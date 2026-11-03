package com.f5.scholarship_backend.domain.matching.model;

import java.util.List;
import java.util.Objects;

import com.f5.scholarship_backend.domain.matching.enums.ConditionReason;
import com.f5.scholarship_backend.domain.matching.enums.EligibilityStatus;

/** reviewReasons는 특정 조건 ID가 없는 공고 전체의 확인 필요 사유다. */
public record EligibilityResult(
		EligibilityStatus status,
		List<ConditionEvaluation> unmetConditions,
		List<ConditionEvaluation> unresolvedConditions,
		List<ConditionReason> reviewReasons
) {

	public EligibilityResult {
		Objects.requireNonNull(status, "판정 상태는 null일 수 없습니다.");
		unmetConditions = List.copyOf(unmetConditions);
		unresolvedConditions = List.copyOf(unresolvedConditions);
		reviewReasons = List.copyOf(reviewReasons);
	}
}
