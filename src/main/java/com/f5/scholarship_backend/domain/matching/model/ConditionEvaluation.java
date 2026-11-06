package com.f5.scholarship_backend.domain.matching.model;

import java.math.BigDecimal;
import java.util.Objects;

import com.f5.scholarship_backend.domain.matching.enums.ConditionReason;
import com.f5.scholarship_backend.domain.matching.enums.ConditionStatus;

/** 조건 ID와 필수 여부를 포함하는 내부 판정 결과. API 응답 DTO는 아니다. */
public record ConditionEvaluation(
		String conditionId,
		boolean required,
		ConditionStatus status,
		BigDecimal gap,
		ConditionReason reason
) {

	public ConditionEvaluation {
		if (conditionId == null || conditionId.isBlank()) {
			throw new IllegalArgumentException("조건 ID는 null이거나 공백일 수 없습니다.");
		}
		Objects.requireNonNull(status, "판정 상태는 null일 수 없습니다.");
		if (gap != null && gap.signum() < 0) {
			throw new IllegalArgumentException("부족량 또는 초과량은 음수일 수 없습니다.");
		}
		if (status == ConditionStatus.MET && (reason != null || (gap != null && gap.signum() != 0))) {
			throw new IllegalArgumentException("충족한 조건에는 실패 사유나 0이 아닌 부족량이 있을 수 없습니다.");
		}
		if (status == ConditionStatus.NOT_MET && reason != ConditionReason.CONDITION_NOT_MET) {
			throw new IllegalArgumentException("미충족 조건의 사유는 CONDITION_NOT_MET이어야 합니다.");
		}
		if (status == ConditionStatus.NEEDS_REVIEW
				&& (gap != null || (reason != ConditionReason.PROFILE_VALUE_MISSING
				&& reason != ConditionReason.UNSUPPORTED_RULE))) {
			throw new IllegalArgumentException("확인 필요 조건에는 정보 누락 또는 미지원 규칙 사유가 필요하며, 부족량은 null이어야 합니다.");
		}
	}

	public static ConditionEvaluation fromNumeric(
			String conditionId, boolean required, NumericConditionResult result
	) {
		Objects.requireNonNull(result, "비교 결과는 null일 수 없습니다.");
		return new ConditionEvaluation(conditionId, required, result.status(), result.gap(), result.reason());
	}
}
