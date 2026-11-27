package com.f5.scholarship_backend.domain.analysis.service;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import com.f5.scholarship_backend.domain.analysis.dto.AnalysisCreateResponse;
import com.f5.scholarship_backend.domain.analysis.dto.AnalysisStatusResponse;
import com.f5.scholarship_backend.domain.analysis.dto.AnalysisStatusResponse.Exclusion;
import com.f5.scholarship_backend.domain.analysis.dto.AnalysisStatusResponse.Failure;
import com.f5.scholarship_backend.domain.analysis.entity.AnalysisItem;
import com.f5.scholarship_backend.domain.analysis.entity.AnalysisJob;
import com.f5.scholarship_backend.domain.analysis.enums.AnalysisExclusionReason;
import com.f5.scholarship_backend.domain.analysis.enums.AnalysisStatus;
import com.f5.scholarship_backend.domain.analysis.repository.AnalysisItemRepository;
import com.f5.scholarship_backend.domain.analysis.repository.AnalysisJobRepository;
import com.f5.scholarship_backend.domain.scholarship.repository.ScholarshipRepository;
import com.f5.scholarship_backend.domain.scholarship.repository.ScholarshipRepository.AnalysisCandidate;
import com.f5.scholarship_backend.domain.student.dto.StudentProfileResponse;
import com.f5.scholarship_backend.domain.student.service.StudentProfileService;
import com.f5.scholarship_backend.global.exception.BusinessException;
import com.f5.scholarship_backend.global.exception.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

@Service
public class AnalysisService {
	private final StudentProfileService profiles;
	private final AnalysisJobRepository jobs;
	private final AnalysisItemRepository items;
	private final ScholarshipRepository scholarships;
	private final JsonMapper json;
	private final Clock clock;

	public AnalysisService(StudentProfileService profiles, AnalysisJobRepository jobs,
			AnalysisItemRepository items, ScholarshipRepository scholarships, JsonMapper json, Clock clock) {
		this.profiles = profiles;
		this.jobs = jobs;
		this.items = items;
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
		long accountId = positiveId(userId, ErrorCode.TOKEN_INVALID);
		long id = positiveId(analysisId, ErrorCode.RESOURCE_NOT_FOUND);
		var job = jobs.findByIdAndUserId(id, accountId)
				.orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));

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
