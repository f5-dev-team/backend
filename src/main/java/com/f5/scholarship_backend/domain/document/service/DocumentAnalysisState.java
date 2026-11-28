package com.f5.scholarship_backend.domain.document.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import com.f5.scholarship_backend.domain.crawl.repository.CrawlStateRepository;
import com.f5.scholarship_backend.domain.document.client.DocumentException;
import com.f5.scholarship_backend.domain.document.config.DocumentAnalysisProperties;
import com.f5.scholarship_backend.domain.document.dto.DocumentAnalysisRequest;
import com.f5.scholarship_backend.domain.document.dto.DocumentAnalysisResult;
import com.f5.scholarship_backend.domain.document.repository.VersionDocumentRepository;
import com.f5.scholarship_backend.domain.scholarship.entity.Scholarship;
import com.f5.scholarship_backend.domain.scholarship.entity.ScholarshipVersion;
import com.f5.scholarship_backend.domain.scholarship.repository.ScholarshipConditionRepository;
import com.f5.scholarship_backend.domain.scholarship.repository.ScholarshipRepository;
import com.f5.scholarship_backend.domain.scholarship.repository.ScholarshipVersionRepository;
import org.jsoup.Jsoup;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;
import static com.f5.scholarship_backend.domain.document.client.DocumentAnalysisClient.TIMEOUT;

@Service
class DocumentAnalysisState {
	private static final Duration JOB_TIMEOUT = Duration.ofMinutes(2);
	private static final Duration CLAIM_DURATION = Duration.ofSeconds(15);
	private static final Duration RETRY_POLL_AFTER = Duration.ofSeconds(2);
	private final ScholarshipRepository scholarships;
	private final ScholarshipVersionRepository versions;
	private final ScholarshipConditionRepository conditions;
	private final CrawlStateRepository crawlStates;
	private final VersionDocumentRepository documents;
	private final DocumentAnalysisRequirements requirements;
	private final DocumentAnalysisProperties properties;
	private final JsonMapper json;
	private final Clock clock;

	DocumentAnalysisState(ScholarshipRepository scholarships, ScholarshipVersionRepository versions,
			ScholarshipConditionRepository conditions, CrawlStateRepository crawlStates, VersionDocumentRepository documents,
			DocumentAnalysisRequirements requirements, DocumentAnalysisProperties properties, JsonMapper json, Clock clock) {
		this.scholarships = scholarships;
		this.versions = versions;
		this.conditions = conditions;
		this.crawlStates = crawlStates;
		this.documents = documents;
		this.requirements = requirements;
		this.properties = properties;
		this.json = json;
		this.clock = clock;
	}

	/** Reserve one HTTP exchange, then commit before touching FastAPI. */
	@Transactional(isolation = Isolation.READ_COMMITTED)
	public Optional<Claim> takeNext() {
		Instant now = now();
		var candidates = scholarships.findDocumentAnalysisCandidates(now, now.minus(JOB_TIMEOUT), PageRequest.of(0, 1));
		if (candidates.isEmpty()) {
			return Optional.empty();
		}
		var scholarship = scholarships.findForDocumentAnalysis(candidates.get(0)).orElseThrow();
		var current = latestCrawlerVersion(scholarship.getId());
		if (current.isEmpty()) {
			return Optional.empty();
		}
		int version = current.orElseThrow();
		Instant reserveUntil = now.plus(CLAIM_DURATION);
		if ("PENDING".equals(scholarship.getProcessingStatus())) {
			var source = versions.findById(new ScholarshipVersion.Key(scholarship.getId(), version)).orElseThrow();
			if (source.getRequirementsComplete() != null || conditions.existsByScholarshipIdAndVersion(scholarship.getId(), version)) {
				fail(scholarship, "CONDITION_EXTRACTION_FAILED", now);
				return Optional.empty();
			}
			try {
				var request = buildRequest(source);
				scholarship.startDocumentAnalysis(version, now, reserveUntil);
				return Optional.of(new Claim(scholarship.getId(), version, reserveUntil, null, request));
			} catch (DocumentException incompleteSource) {
				fail(scholarship, incompleteSource.code(), now);
				return Optional.empty();
			}
		}
		if (!"PROCESSING".equals(scholarship.getProcessingStatus()) || scholarship.getDocumentAnalysisNextAt() == null
				|| scholarship.getDocumentAnalysisNextAt().isAfter(now)) {
			return Optional.empty();
		}
		if (!Objects.equals(scholarship.getDocumentAnalysisVersion(), version)) {
			scholarship.markPending(now);
			return Optional.empty();
		}
		if (!scholarship.getStatusUpdatedAt().plus(JOB_TIMEOUT).isAfter(now)) {
			fail(scholarship, TIMEOUT, now);
			return Optional.empty();
		}
		String remoteId = scholarship.getDocumentAnalysisId();
		if (remoteId == null) {
			return Optional.empty();
		}
		scholarship.scheduleDocumentAnalysis(remoteId, reserveUntil);
		return Optional.of(new Claim(scholarship.getId(), version, reserveUntil, remoteId, null));
	}

	@Transactional
	public void scheduleNext(Claim claim, String remoteId) {
		currentClaim(claim).ifPresent(scholarship -> scholarship.scheduleDocumentAnalysis(remoteId, now().plus(RETRY_POLL_AFTER)));
	}

	@Transactional
	public void complete(Claim claim, DocumentAnalysisResult result) {
		var current = currentClaim(claim);
		if (current.isEmpty()) {
			return;
		}
		var scholarship = current.orElseThrow();
		var version = versions.findById(new ScholarshipVersion.Key(claim.scholarshipId(), claim.version())).orElseThrow();
		if (version.getRequirementsComplete() != null || conditions.existsByScholarshipIdAndVersion(claim.scholarshipId(), claim.version())) {
			fail(scholarship, "CONDITION_EXTRACTION_FAILED", now());
			return;
		}
		var ids = documents.findDocumentAnalysisSources(claim.scholarshipId(), claim.version()).stream()
				.map(document -> document.getDocumentId().toString()).collect(Collectors.toSet());
		var validated = requirements.validate(result, claim.scholarshipId(), claim.version(), ids);
		conditions.saveAll(validated);
		version.recordConditionExtraction(result.requirementsComplete());
		scholarship.finishDocumentAnalysis(now());
	}

	@Transactional
	public void reject(Claim claim, String code) {
		currentClaim(claim).ifPresent(scholarship -> fail(scholarship, code, now()));
	}

	private Optional<Integer> latestCrawlerVersion(long id) {
		if (crawlStates.existsByScholarshipIdAndRequiresReviewTrue(id)) {
			return Optional.empty();
		}
		return versions.findTopByScholarshipIdOrderByVersionDesc(id)
				.filter(version -> "CRAWLER".equals(version.getOrigin()) && version.getChangedByUserId() == null)
				.map(ScholarshipVersionRepository.DocumentVersion::getVersion);
	}

	private Optional<Scholarship> currentClaim(Claim claim) {
		return scholarships.findForDocumentAnalysis(claim.scholarshipId()).filter(scholarship ->
				"PROCESSING".equals(scholarship.getProcessingStatus())
				&& Objects.equals(scholarship.getDocumentAnalysisVersion(), claim.version())
				&& claim.reservedUntil().equals(scholarship.getDocumentAnalysisNextAt())
				&& latestCrawlerVersion(claim.scholarshipId()).filter(version -> version == claim.version()).isPresent());
	}

	private DocumentAnalysisRequest buildRequest(ScholarshipVersion version) {
		long id = version.getScholarshipId();
		var files = documents.findDocumentAnalysisSources(id, version.getVersion());
		if (version.getSourceAttachments() != null) {
			try {
				var sourceLinks = json.readTree(version.getSourceAttachments());
				if (!sourceLinks.isArray() || sourceLinks.valueStream().distinct().count() > files.size()) {
					throw DocumentException.download();
				}
			} catch (JacksonException invalidLinks) {
				throw DocumentException.download();
			}
		}
		if (files.stream().anyMatch(file -> !Objects.equals(properties.sourceBucket(), file.getBucket()))) {
			throw DocumentException.storage();
		}
		String text = version.getSourceText() == null ? "" : Jsoup.parse(version.getSourceText()).text();
		if (text.isBlank() && files.isEmpty()) {
			throw DocumentException.download();
		}
		List<DocumentAnalysisRequest.Document> inputs = files.stream()
				.map(file -> new DocumentAnalysisRequest.Document(file.getDocumentId().toString(), file.getObjectKey()))
				.toList();
		return new DocumentAnalysisRequest(Long.toString(id), version.getVersion(), text.isBlank() ? null : text, inputs);
	}

	private void fail(Scholarship scholarship, String code, Instant now) {
		String message = switch (code) {
			case TIMEOUT -> "문서 분석 응답 시간을 초과했습니다.";
			case "DEPENDENCY_UNAVAILABLE" -> "문서 분석 서버에 연결할 수 없습니다.";
			case "DOCUMENT_FETCH_FAILED", "STORAGE_FAILED" -> "문서 분석에 필요한 공고 원본을 확인해 주세요.";
			default -> "공고 자격조건을 확인할 수 없습니다.";
		};
		scholarship.failDocumentAnalysis(code, message, now);
	}

	private Instant now() {
		return clock.instant().truncatedTo(ChronoUnit.MICROS);
	}

	record Claim(long scholarshipId, int version, Instant reservedUntil, String remoteId, DocumentAnalysisRequest request) { }
}
