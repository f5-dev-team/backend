package com.f5.scholarship_backend.domain.analysis.dto;

import java.time.OffsetDateTime;
import java.util.List;
import com.f5.scholarship_backend.domain.analysis.enums.ApplicationPeriodStatus;
import com.f5.scholarship_backend.domain.matching.enums.EligibilityStatus;
import io.swagger.v3.oas.annotations.media.Schema;

public record AnalysisRecommendationsResponse(
		String analysisId,
		long profileVersion,
		List<Recommendation> items,
		int page,
		int size,
		long totalElements,
		long totalPages
) {
	public record Recommendation(
			String scholarshipId,
			int scholarshipVersion,
			String title,
			@Schema(nullable = true) OffsetDateTime applicationEndAt,
			EligibilityStatus eligibilityStatus,
			ApplicationPeriodStatus applicationPeriodStatus,
			@Schema(nullable = true) String applicationPeriodMessage,
			List<String> reasons,
			List<ConditionResult> unmetConditions,
			List<ConditionResult> unresolvedConditions
	) { }
}
