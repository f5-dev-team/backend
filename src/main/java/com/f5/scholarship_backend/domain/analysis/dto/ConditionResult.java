package com.f5.scholarship_backend.domain.analysis.dto;

import java.math.BigDecimal;
import com.f5.scholarship_backend.domain.matching.enums.ConditionReason;
import io.swagger.v3.oas.annotations.media.Schema;
import tools.jackson.databind.JsonNode;

/** 저장된 조건별 판정 사유의 JSON 형식이며 추천 결과 API에서도 사용한다. */
public record ConditionResult(
		@Schema(nullable = true) String conditionId,
		String field,
		boolean required,
		@Schema(nullable = true, oneOf = {BigDecimal.class, String.class}) JsonNode actualValue,
		String expectedDescription,
		@Schema(nullable = true) BigDecimal gap,
		@Schema(nullable = true) String unit,
		ConditionReason reasonCode,
		String message
) { }
