package com.f5.scholarship_backend.domain.document.service;

import java.util.Objects;
import java.util.Set;
import com.f5.scholarship_backend.domain.document.client.DocumentAnalysisClient;
import com.f5.scholarship_backend.domain.document.client.DocumentException;
import com.f5.scholarship_backend.domain.document.config.DocumentAnalysisProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import static com.f5.scholarship_backend.domain.document.client.DocumentAnalysisClient.INVALID_RESPONSE;

@Component
class DocumentAnalysisWorker {
	private static final Logger LOG = LoggerFactory.getLogger(DocumentAnalysisWorker.class);
	private static final Set<String> IN_PROGRESS = Set.of("QUEUED", "RUNNING", "PROCESSING");
	private final DocumentAnalysisClient client;
	private final DocumentAnalysisState state;
	private final DocumentAnalysisProperties properties;

	DocumentAnalysisWorker(DocumentAnalysisClient client, DocumentAnalysisState state, DocumentAnalysisProperties properties) {
		this.client = client;
		this.state = state;
		this.properties = properties;
	}

	@Scheduled(fixedDelayString = "${document-analysis.poll-delay}")
	public void scheduled() {
		if (properties.enabled()) {
			runOnce();
		}
	}

	void runOnce() {
		var next = state.takeNext();
		if (next.isEmpty()) {
			return;
		}
		var claim = next.orElseThrow();
		try {
			if (claim.request() != null) {
				var accepted = client.submit(claim.request());
				if (!DocumentAnalysisClient.validId(accepted.analysisId()) || accepted.processingStatus() == null
						|| (!IN_PROGRESS.contains(accepted.processingStatus()) && !"COMPLETED".equals(accepted.processingStatus()))) {
					throw new DocumentException(INVALID_RESPONSE);
				}
				state.scheduleNext(claim, accepted.analysisId());
				return;
			}
			var result = client.read(claim.remoteId());
			if (!claim.remoteId().equals(result.analysisId()) || result.processingStatus() == null
					|| (result.scholarshipId() != null && !Long.toString(claim.scholarshipId()).equals(result.scholarshipId()))
					|| (result.scholarshipVersion() != null && !Objects.equals(claim.version(), result.scholarshipVersion()))) {
				throw new DocumentException(INVALID_RESPONSE);
			}
			switch (result.processingStatus()) {
				case "QUEUED", "RUNNING", "PROCESSING" -> state.scheduleNext(claim, claim.remoteId());
				case "FAILED" -> state.reject(claim, "CONDITION_EXTRACTION_FAILED");
				case "COMPLETED" -> {
					if (result.scholarshipId() == null || result.scholarshipVersion() == null) {
						throw new DocumentException(INVALID_RESPONSE);
					}
					state.complete(claim, result);
				}
				default -> throw new DocumentException(INVALID_RESPONSE);
			}
		} catch (RuntimeException failure) {
			String code = failure instanceof DocumentException document ? document.code() : "CONDITION_EXTRACTION_FAILED";
			LOG.warn("공고 문서 분석 실패 scholarshipId={}, version={}, code={}, exceptionType={}",
					claim.scholarshipId(), claim.version(), code, failure.getClass().getSimpleName());
			if (!Thread.currentThread().isInterrupted()) {
				state.reject(claim, code);
			}
		}
	}
}
