package com.f5.scholarship_backend.domain.crawl.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.util.Comparator;
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

	public ScholarshipCollector(ScholarshipRepository scholarships, ScholarshipVersionRepository versions,
			CrawlStateRepository states, LeaseManager leases, JsonMapper json, Clock clock) {
		this.scholarships = scholarships; this.versions = versions; this.states = states;
		this.leases = leases; this.json = json; this.clock = clock;
	}

	/** 원문 지문과 외부 정정 여부를 확인하고 식별 행·버전·후보를 함께 저장한다. */
	@Transactional(timeout = 10)
	public String collect(CrawledPost post, String owner) {
		leases.assertWorkAllowed(owner);
		String payload = json.writeValueAsString(post);
		// 링크 집합의 순서 변화로 새 버전을 만들지 않는다. S3 파일 바이트 지문은 다음 이슈에서 추가한다.
		String fingerprint = fingerprint(post);
		var scholarship = scholarships.findSourceForUpdate(post.postKey()).orElse(null);
		boolean fresh = scholarship == null;
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
		versions.save(new ScholarshipVersion(scholarship.getId(), version, post.title(),
				post.sourceUrl().toString(), post.bodyHtml(), json.writeValueAsString(post.attachments()), clock.instant()));
		state.observe(fingerprint, payload, false, clock.instant());
		state.imported(fingerprint, version);
		states.save(state);
		scholarship.markPending(clock.instant());
		return "COLLECTED";
	}

	/** 본문·제목·정렬된 첨부 링크의 JSON 지문을 계산한다. */
	private String fingerprint(CrawledPost post) {
		try {
			var attachments = post.attachments().stream()
					.sorted(Comparator.comparing((CrawledPost.Attachment a) -> a.url().toString()).thenComparing(CrawledPost.Attachment::name))
					.distinct().toList();
			String canonical = json.writeValueAsString(java.util.List.of(post.title(), post.bodyHtml(), attachments));
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
					.digest(canonical.getBytes(StandardCharsets.UTF_8)));
		} catch (java.security.NoSuchAlgorithmException impossible) { throw new IllegalStateException("SHA-256을 사용할 수 없습니다."); }
	}
}