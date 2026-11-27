package com.f5.scholarship_backend.domain.analysis.service;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import com.f5.scholarship_backend.domain.analysis.dto.AnalysisCreateResponse;
import com.f5.scholarship_backend.domain.analysis.dto.AnalysisEvidenceResponse;
import com.f5.scholarship_backend.domain.analysis.dto.AnalysisEvidenceResponse.Document;
import com.f5.scholarship_backend.domain.analysis.dto.AnalysisEvidenceResponse.Evidence;
import com.f5.scholarship_backend.domain.analysis.dto.AnalysisRecommendationsResponse;
import com.f5.scholarship_backend.domain.analysis.dto.AnalysisRecommendationsResponse.Recommendation;
import com.f5.scholarship_backend.domain.analysis.dto.ConditionResult;
import com.f5.scholarship_backend.domain.analysis.dto.AnalysisStatusResponse;
import com.f5.scholarship_backend.domain.analysis.dto.AnalysisStatusResponse.Exclusion;
import com.f5.scholarship_backend.domain.analysis.dto.AnalysisStatusResponse.Failure;
import com.f5.scholarship_backend.domain.analysis.entity.AnalysisItem;
import com.f5.scholarship_backend.domain.analysis.entity.AnalysisJob;
import com.f5.scholarship_backend.domain.analysis.enums.AnalysisExclusionReason;
import com.f5.scholarship_backend.domain.analysis.enums.AnalysisStatus;
import com.f5.scholarship_backend.domain.analysis.enums.EvidenceStatus;
import com.f5.scholarship_backend.domain.analysis.repository.AnalysisEvidenceRepository;
import com.f5.scholarship_backend.domain.analysis.repository.AnalysisItemRepository;
import com.f5.scholarship_backend.domain.analysis.repository.AnalysisItemRepository.RecommendationRow;
import com.f5.scholarship_backend.domain.analysis.repository.AnalysisJobRepository;
import com.f5.scholarship_backend.domain.document.repository.VersionDocumentRepository;
import com.f5.scholarship_backend.domain.matching.enums.EligibilityStatus;
import com.f5.scholarship_backend.domain.scholarship.repository.ScholarshipRepository;
import com.f5.scholarship_backend.domain.scholarship.repository.ScholarshipRepository.AnalysisCandidate;
import com.f5.scholarship_backend.domain.student.dto.StudentProfileResponse;
import com.f5.scholarship_backend.domain.student.service.StudentProfileService;
import com.f5.scholarship_backend.global.exception.BusinessException;
import com.f5.scholarship_backend.global.exception.ErrorCode;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

@Service
public class AnalysisService {
	private static final TypeReference<List<String>> REASONS_TYPE = new TypeReference<>() { };
	private static final TypeReference<List<ConditionResult>> CONDITIONS_TYPE = new TypeReference<>() { };
	private final StudentProfileService profiles;
	private final AnalysisJobRepository jobs;
	private final AnalysisItemRepository items;
	private final AnalysisEvidenceRepository evidence;
	private final VersionDocumentRepository versionDocuments;
	private final ScholarshipRepository scholarships;
	private final JsonMapper json;
	private final Clock clock;

	public AnalysisService(StudentProfileService profiles, AnalysisJobRepository jobs,
			AnalysisItemRepository items, AnalysisEvidenceRepository evidence, VersionDocumentRepository versionDocuments,
			ScholarshipRepository scholarships, JsonMapper json, Clock clock) {
		this.profiles = profiles;
		this.jobs = jobs;
		this.items = items;
		this.evidence = evidence;
		this.versionDocuments = versionDocuments;
		this.scholarships = scholarships;
		this.json = json;
		this.clock = clock;
	}

	@Transactional
	public AnalysisCreateResponse create(String userId, long profileVersion) {
		long accountId = positiveId(userId, ErrorCode.TOKEN_INVALID);
		var profile = profiles.findForAnalysisWithAccountLock(accountId)
				.orElseThrow(() -> new BusinessException(ErrorCode.PROFILE_REQUIRED));
		if (profile.version() != profileVersion) {
			throw new BusinessException(ErrorCode.VERSION_CONFLICT);
		}

		Instant acceptedAt = clock.instant().truncatedTo(ChronoUnit.MICROS);
		var candidates = scholarships.findAnalysisCandidates();
		var job = jobs.save(new AnalysisJob(accountId, profile.version(), snapshot(profile), acceptedAt));
		items.saveAll(candidates.stream().map(candidate -> new AnalysisItem(job.getId(),
				candidate.getScholarshipId(), candidate.getVersion(), candidate.getProcessingStatus(),
				exclusionReason(candidate, acceptedAt))).toList());
		return new AnalysisCreateResponse(job.getId().toString(), job.getStatus(), job.getProfileVersion(),
				atUtc(job.getCreatedAt()));
	}

	@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
	public AnalysisStatusResponse findStatus(String userId, String analysisId) {
		var job = findOwnedJob(userId, analysisId);
		long id = job.getId();

		long total = 0;
		long excluded = 0;
		long processed = 0;
		var exclusions = new ArrayList<Exclusion>();
		for (var count : items.countByExclusionReason(id)) {
			var reason = count.getReason();
			if (reason == null) {
				total = count.getItemCount();
				processed = count.getProcessedCount();
			} else {
				excluded += count.getItemCount();
				exclusions.add(new Exclusion(reason, count.getItemCount()));
			}
		}
		exclusions.sort(Comparator.comparing(Exclusion::reason));
		return new AnalysisStatusResponse(job.getId().toString(), job.getStatus(), job.getProfileVersion(),
				total, total + excluded, excluded, exclusions, processed, atUtc(job.getCreatedAt()),
				atUtc(job.getStartedAt()), atUtc(job.getCompletedAt()), publicFailure(job));
	}

	@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
	public AnalysisRecommendationsResponse findRecommendations(String userId, String analysisId,
			int page, int size, EligibilityStatus eligibilityStatus) {
		if (page < 0 || size < 1 || size > 100) {
			throw new BusinessException(ErrorCode.VALIDATION_ERROR);
		}
		var job = findCompletedOwnedJob(userId, analysisId);
		long total = items.countRecommendations(job.getId(), eligibilityStatus);
		long totalPages = total == 0 ? 0 : (total - 1) / size + 1;
		List<Recommendation> recommendations = page >= totalPages ? List.of()
				: items.findRecommendations(job.getId(), eligibilityStatus, PageRequest.of(page, size)).stream()
						.map(this::toRecommendation).toList();
		return new AnalysisRecommendationsResponse(job.getId().toString(), job.getProfileVersion(),
				recommendations, page, size, total, totalPages);
	}

	@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
	public AnalysisEvidenceResponse findEvidence(String userId, String analysisId, String scholarshipId) {
		long id = positiveId(scholarshipId, ErrorCode.RESOURCE_NOT_FOUND);
		var job = findCompletedOwnedJob(userId, analysisId);
		var target = items.findEvidenceTarget(job.getId(), id)
				.orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
		int version = target.getScholarshipVersion();
		var documents = versionDocuments.findDocumentMetadata(id, version).stream()
				.map(document -> new Document(document.getDocumentId().toString(), document.getFileName(),
						document.getFileType()))
				.toList();

		EvidenceStatus status = target.getEvidenceStatus();
		List<Evidence> results = status == EvidenceStatus.UNAVAILABLE ? List.of()
				: evidence.findByAnalysisIdAndScholarshipIdAndScholarshipVersionOrderByIdAsc(job.getId(), id, version)
						.stream().map(item -> new Evidence(item.getConditionId(),
								item.getDocumentId() == null ? null : item.getDocumentId().toString(),
								item.getQuote(), item.getExplanation(), item.getPageNumber(), item.getSectionLabel(),
								item.getSourceUrl()))
						.toList();

		String message = target.getEvidenceMessage();
		if (results.isEmpty() && status != EvidenceStatus.UNAVAILABLE) {
			status = EvidenceStatus.UNAVAILABLE;
			message = null;
		}
		if (message == null || message.isBlank()) {
			message = switch (status) {
				case AVAILABLE -> null;
				case PARTIAL -> "일부 판정 근거만 제공할 수 있습니다.";
				case UNAVAILABLE -> "상세 원문 근거는 현재 제공할 수 없습니다.";
			};
		}
		return new AnalysisEvidenceResponse(job.getId().toString(), Long.toString(id), version, status,
				target.getSourceUrl(), documents, results, message);
	}

	private AnalysisJob findCompletedOwnedJob(String userId, String analysisId) {
		var job = findOwnedJob(userId, analysisId);
		return switch (job.getStatus()) {
			case QUEUED, RUNNING -> throw new BusinessException(ErrorCode.ANALYSIS_NOT_COMPLETED);
			case FAILED -> throw new BusinessException(ErrorCode.ANALYSIS_FAILED);
			case COMPLETED -> job;
		};
	}

	private AnalysisJob findOwnedJob(String userId, String analysisId) {
		long accountId = positiveId(userId, ErrorCode.TOKEN_INVALID);
		long id = positiveId(analysisId, ErrorCode.RESOURCE_NOT_FOUND);
		return jobs.findByIdAndUserId(id, accountId)
				.orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
	}

	private Recommendation toRecommendation(RecommendationRow row) {
		return new Recommendation(row.getScholarshipId().toString(), row.getScholarshipVersion(),
				row.getTitle(), atUtc(row.getApplicationEndAt()), row.getEligibilityStatus(),
				row.getApplicationPeriodStatus(), row.getApplicationPeriodMessage(),
				json.readValue(row.getReasons(), REASONS_TYPE), json.readValue(row.getUnmetConditions(), CONDITIONS_TYPE),
				json.readValue(row.getUnresolvedConditions(), CONDITIONS_TYPE));
	}

	private String snapshot(StudentProfileResponse profile) {
		ObjectNode snapshot = json.valueToTree(profile);
		snapshot.remove("version");
		snapshot.remove("updatedAt");
		return json.writeValueAsString(snapshot);
	}

	private static AnalysisExclusionReason exclusionReason(AnalysisCandidate candidate, Instant acceptedAt) {
		Instant deadline = candidate.getApplicationEndAt();
		if (deadline != null && deadline.isBefore(acceptedAt)) {
			return AnalysisExclusionReason.DEADLINE_PASSED;
		}
		return switch (candidate.getProcessingStatus()) {
			case "READY" -> null;
			case "FAILED" -> AnalysisExclusionReason.PROCESSING_FAILED;
			case "PENDING", "PROCESSING" -> AnalysisExclusionReason.PROCESSING_PENDING;
			default -> throw new IllegalStateException("Unsupported scholarship processing status");
		};
	}

	private static Failure publicFailure(AnalysisJob job) {
		if (job.getStatus() != AnalysisStatus.FAILED) {
			return null;
		}
		if ("DEPENDENCY_UNAVAILABLE".equals(job.getFailureCode())) {
			return new Failure("DEPENDENCY_UNAVAILABLE", ErrorCode.SERVICE_UNAVAILABLE.getMessage());
		}
		return new Failure("ANALYSIS_PROCESSING_FAILED", ErrorCode.ANALYSIS_FAILED.getMessage());
	}


	private static OffsetDateTime atUtc(Instant instant) {
		return instant == null ? null : instant.atOffset(ZoneOffset.UTC);
	}

	private static long positiveId(String value, ErrorCode error) {
		try {
			long id = Long.parseLong(value);
			if (id > 0) {
				return id;
			}
		} catch (NumberFormatException invalidId) {
			throw new BusinessException(error);
		}
		throw new BusinessException(error);
	}
}
