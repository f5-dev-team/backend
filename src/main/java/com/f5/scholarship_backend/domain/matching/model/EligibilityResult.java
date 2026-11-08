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

	/**
	 * 최종 판정과 조건별 사유를 보관하며, 전달된 목록을 수정 불가능한 복사본으로 저장한다.
	 *
	 * @param status 장학금 최종 자격 판정
	 * @param unmetConditions 미충족 조건 목록. 우대 조건도 포함될 수 있음
	 * @param unresolvedConditions 확인 필요 조건 목록. 우대 조건도 포함될 수 있음
	 * @param reviewReasons 특정 조건에 속하지 않는 공고 전체의 확인 필요 사유
	 * @throws NullPointerException 판정 상태나 목록이 null이거나 목록에 null 항목이 있는 경우
	 */
	public EligibilityResult {
		Objects.requireNonNull(status, "판정 상태는 null일 수 없습니다.");
		unmetConditions = List.copyOf(unmetConditions);
		unresolvedConditions = List.copyOf(unresolvedConditions);
		reviewReasons = List.copyOf(reviewReasons);
	}
}
