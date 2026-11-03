package com.f5.scholarship_backend.domain.matching.model;

import java.math.BigDecimal;

import com.f5.scholarship_backend.domain.matching.enums.ConditionReason;
import com.f5.scholarship_backend.domain.matching.enums.ConditionStatus;

/**
 * 조건 하나의 내부 비교 결과. 장학금 최종 판정이나 API 응답 DTO와 구분한다.
 * gap은 충족 시 0, 미충족 시 기준까지의 차이, 계산 불가 시 null이다.
 * reason은 충족 시 null이다.
 */
public record NumericConditionResult(
		ConditionStatus status,
		BigDecimal gap,
		ConditionReason reason
) {
}
