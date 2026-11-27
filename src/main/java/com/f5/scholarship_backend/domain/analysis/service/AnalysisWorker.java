package com.f5.scholarship_backend.domain.analysis.service;

import java.util.Arrays;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
class AnalysisWorker {
	private static final Logger LOG = LoggerFactory.getLogger(AnalysisWorker.class);
	private static final int MAX_JOBS_PER_POLL = 10;

	private final AnalysisExecution execution;
	private final boolean enabled;

	AnalysisWorker(AnalysisExecution execution, @Value("${analysis.worker.enabled}") boolean enabled) {
		this.execution = execution;
		this.enabled = enabled;
	}

	@Scheduled(fixedDelayString = "${analysis.worker.poll-delay}")
	public void poll() {
		if (!enabled) {
			return;
		}
		for (int processed = 0; processed < MAX_JOBS_PER_POLL; processed++) {
			if (!runNext()) {
				return;
			}
		}
	}

	@Scheduled(fixedDelay = 60_000)
	public void checkAbandoned() {
		if (enabled) {
			execution.failAbandoned();
		}
	}

	boolean runNext() {
		var claimed = execution.claimNext();
		if (claimed.isEmpty()) {
			return false;
		}
		long id = claimed.orElseThrow();
		try {
			execution.execute(id);
		} catch (RuntimeException failure) {
			var origin = Arrays.stream(failure.getStackTrace())
					.filter(frame -> frame.getClassName().startsWith(AnalysisWorker.class.getPackageName() + "."))
					.findFirst().map(StackTraceElement::toString).orElse("unknown");
			LOG.error("분석 실행 실패 analysisId={}, exceptionType={}, origin={}",
					id, failure.getClass().getName(), origin);
			execution.markFailed(id);
		}
		return true;
	}
}
