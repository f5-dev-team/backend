package com.f5.scholarship_backend.domain.analysis.dto;

import java.time.OffsetDateTime;
import java.util.List;
import com.f5.scholarship_backend.domain.analysis.enums.AnalysisExclusionReason;
import com.f5.scholarship_backend.domain.analysis.enums.AnalysisStatus;
import io.swagger.v3.oas.annotations.media.Schema;

public record AnalysisStatusResponse(
		String analysisId,
		AnalysisStatus status,
		long profileVersion,
		long totalCount,
		long candidateCount,
		long excludedCount,
		List<Exclusion> exclusions,
		long processedCount,
		OffsetDateTime createdAt,
		@Schema(nullable = true) OffsetDateTime startedAt,
		@Schema(nullable = true) OffsetDateTime completedAt,
		@Schema(nullable = true) Failure failure
) {
	public record Exclusion(AnalysisExclusionReason reason, long count) { }

	public record Failure(String code, String message) { }
}
