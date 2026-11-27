package com.f5.scholarship_backend.domain.analysis.service;

import java.time.Clock;
import java.time.Instant;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import com.f5.scholarship_backend.domain.analysis.enums.AnalysisStatus;
import com.f5.scholarship_backend.domain.analysis.enums.ApplicationPeriodStatus;
import com.f5.scholarship_backend.domain.analysis.repository.AnalysisItemRepository;
import com.f5.scholarship_backend.domain.analysis.repository.AnalysisJobRepository;
import com.f5.scholarship_backend.domain.scholarship.entity.ScholarshipCondition;
import com.f5.scholarship_backend.domain.scholarship.repository.ScholarshipConditionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

@Service
class AnalysisExecution {
	private static final int EXECUTION_TIMEOUT_SECONDS = 60;
	private static final Duration ABANDONED_AFTER = Duration.ofMinutes(5);
	private static final String FAILURE_CODE = "ANALYSIS_PROCESSING_FAILED";

	private final AnalysisJobRepository jobs;
	private final AnalysisItemRepository items;
	private final ScholarshipConditionRepository conditions;
	private final AnalysisResultEvaluator evaluator;
	private final JsonMapper json;
	private final Clock clock;

	AnalysisExecution(AnalysisJobRepository jobs, AnalysisItemRepository items,
			ScholarshipConditionRepository conditions, AnalysisResultEvaluator evaluator, JsonMapper json, Clock clock) {
		this.jobs = jobs;
		this.items = items;
		this.conditions = conditions;
		this.evaluator = evaluator;
		this.json = json;
		this.clock = clock;
	}

	/** Commit the claim so status polling can see RUNNING before result processing starts. */
	@Transactional(isolation = Isolation.READ_COMMITTED)
	public Optional<Long> claimNext() {
		return jobs.findFirstByStatusOrderByCreatedAtAscIdAsc(AnalysisStatus.QUEUED)
				.filter(job -> jobs.claimQueued(job.getId(), laterOf(now(), job.getCreatedAt())) == 1)
				.map(AnalysisJobRepository.QueuedJob::getId);
	}

	/** Hold the job lock while writing results and publish COMPLETED in the same commit. */
	@Transactional(timeout = EXECUTION_TIMEOUT_SECONDS)
	public void execute(long id) {
		var job = jobs.findForExecution(id).orElseThrow();
		if (job.getStatus() != AnalysisStatus.RUNNING) {
			return;
		}
		var profile = json.readTree(job.getProfileSnapshot());
		if (!profile.isObject()) {
			throw new IllegalStateException("Invalid analysis profile snapshot");
		}
		var targets = items.findTargets(id);
		var byScholarship = conditions.findForAnalysis(id).stream()
				.collect(Collectors.groupingBy(ScholarshipCondition::getScholarshipId));
		for (var target : targets) {
			if (target.getVersion() == null) {
				throw new IllegalStateException("Analysis target version is missing");
			}
			var item = target.getItem();
			var requirements = byScholarship.getOrDefault(item.getId().scholarshipId(), List.of());
			var result = evaluator.evaluate(profile, requirements, job.getCreatedAt(), target.getStartAt(), target.getEndAt());
			String periodMessage = result.period() == ApplicationPeriodStatus.UNKNOWN ? "공고에서 신청 기간을 확인해 주세요." : null;
			item.complete(result.status(), result.period(), periodMessage, json.writeValueAsString(result.reasons()),
					json.writeValueAsString(result.unmet()), json.writeValueAsString(result.unresolved()),
					laterOf(now(), job.getStartedAt()));
		}
		job.complete(now());
	}

	@Transactional
	public void markFailed(long id) {
		jobs.failRunning(id, now(), FAILURE_CODE, "분석을 완료하지 못했습니다. 새 분석을 요청해 주세요.");
	}

	@Transactional(isolation = Isolation.READ_COMMITTED)
	public void failAbandoned() {
		Instant now = now();
		jobs.failAbandoned(now.minus(ABANDONED_AFTER), now, FAILURE_CODE,
				"중단된 분석을 완료하지 못했습니다. 새 분석을 요청해 주세요.");
	}

	private Instant now() {
		return clock.instant().truncatedTo(ChronoUnit.MICROS);
	}

	private static Instant laterOf(Instant left, Instant right) {
		return left.isBefore(right) ? right : left;
	}
}
