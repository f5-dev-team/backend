package com.f5.scholarship_backend.domain.crawl.service;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.util.Comparator;
import java.util.List;
import com.f5.scholarship_backend.domain.document.model.StoredAttachment;
import com.f5.scholarship_backend.domain.document.service.DocumentLinker;
import java.util.HexFormat;
import com.f5.scholarship_backend.domain.crawl.entity.CrawlState;
import com.f5.scholarship_backend.domain.crawl.model.CrawledPost;
import com.f5.scholarship_backend.domain.crawl.repository.CrawlStateRepository;
import com.f5.scholarship_backend.domain.scholarship.entity.*;
import com.f5.scholarship_backend.domain.scholarship.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

@Service
public class ScholarshipCollector {
	private final ScholarshipRepository scholarships;
	private final ScholarshipVersionRepository versions;
	private final CrawlStateRepository states;
	private final LeaseManager leases;
	private final JsonMapper json;
	private final Clock clock;
	private final DocumentLinker documents;

	public ScholarshipCollector(ScholarshipRepository scholarships, ScholarshipVersionRepository versions,
			CrawlStateRepository states, LeaseManager leases, JsonMapper json, Clock clock, DocumentLinker documents) {
		this.scholarships = scholarships; this.versions = versions; this.states = states;
		this.leases = leases; this.json = json; this.clock = clock; this.documents = documents;
	}

	/** 원문 지문과 외부 정정 여부를 확인하고 식별 행·버전·후보를 함께 저장한다. */
	@Transactional(timeout = 10)
	public String collect(CrawledPost post, String owner) {
        return collectSource(post, List.of(), owner, false);
    }

    /** 업로드를 마친 파일과 공고 버전·연결을 임대 잠금 아래 함께 확정한다. */
    @Transactional(timeout = 10)
    public String collect(CrawledPost post, List<StoredAttachment> files, String owner) {
        return collectSource(post, files, owner, false);
    }

    /**
     * 첨부 한도를 넘긴 새 공고의 제목·본문·첨부 링크를 PENDING 상태로 보존한다.
     * 이미 저장된 공고는 첨부가 빠진 버전으로 대체하지 않아 기존 문서 연결을 유지한다.
     */
    @Transactional(timeout = 10)
    public void preserveNewSource(CrawledPost post, String owner) {
        collectSource(post, List.of(), owner, true);
    }

    private String collectSource(CrawledPost post, List<StoredAttachment> files, String owner, boolean newOnly) {
        leases.assertWorkAllowed(owner);
		String payload = files.isEmpty() ? json.writeValueAsString(post)
                : json.writeValueAsString(java.util.Map.of("post", post, "files", files));
		String fingerprint = fingerprint(post, files);
		var scholarship = scholarships.findSourceForUpdate(post.postKey()).orElse(null);
		boolean fresh = scholarship == null;
		if (newOnly && !fresh) { return "SKIPPED"; }
		if (fresh) { scholarship = scholarships.saveAndFlush(new Scholarship(post.postKey(), clock.instant())); }
		var state = states.findById(scholarship.getId()).orElse(new CrawlState(scholarship.getId()));
		if (fingerprint.equals(state.getLastSourceFingerprint())) {
			state.observe(fingerprint, payload, false, clock.instant());
			states.save(state);
			return "SKIPPED";
		}
		var latest = versions.findFirstByScholarshipIdOrderByVersionDesc(scholarship.getId()).orElse(null);
		boolean protectedVersion = !fresh && (state.getLastSourceFingerprint() == null
				|| versions.countProtectedSince(scholarship.getId(), state.getLastImportedVersion()) > 0);
		if (protectedVersion) {
			state.observe(fingerprint, payload, true, clock.instant());
			states.save(state);
			scholarship.markPending(clock.instant());
			return "HELD";
		}
		int version = latest == null ? 1 : Math.addExact(latest.getVersion(), 1);
		var savedVersion = versions.save(new ScholarshipVersion(scholarship.getId(), version, post.title(),
				post.sourceUrl().toString(), post.bodyHtml(), null, clock.instant()));
		var attachmentDocuments = documents.link(scholarship.getId(), version, files);
		var sourceAttachments = post.attachments().stream().map(attachment ->
				new SourceAttachment(attachment.name(), attachment.url(), attachmentDocuments.get(attachment))).toList();
		savedVersion.recordSourceAttachments(json.writeValueAsString(sourceAttachments));
		state.observe(fingerprint, payload, false, clock.instant());
		state.imported(fingerprint, version);
		states.save(state);
		scholarship.markPending(clock.instant());
		return "COLLECTED";
	}

	private record SourceAttachment(String name, URI url, Long documentId) { }

	/** 본문·제목·정렬된 첨부 목록과 실제 파일 내용의 JSON 지문을 계산한다. */
	private String fingerprint(CrawledPost post, List<StoredAttachment> files) {
		try {
			var attachments = post.attachments().stream()
					.sorted(Comparator.comparing((CrawledPost.Attachment a) -> a.url().toString()).thenComparing(CrawledPost.Attachment::name))
					.distinct().toList();
			var content = files.stream().map(f -> java.util.List.of(f.name(), f.sourceUrl().toString(), f.type().name(), f.sha256()))
                    .distinct().sorted(Comparator.comparing(Object::toString)).toList();
            String canonical = json.writeValueAsString(files.isEmpty()
                    ? java.util.List.of(post.title(), post.bodyHtml(), attachments)
                    : java.util.List.of(post.title(), post.bodyHtml(), attachments, content));
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
					.digest(canonical.getBytes(StandardCharsets.UTF_8)));
		} catch (java.security.NoSuchAlgorithmException impossible) { throw new IllegalStateException("SHA-256을 사용할 수 없습니다."); }
	}
}