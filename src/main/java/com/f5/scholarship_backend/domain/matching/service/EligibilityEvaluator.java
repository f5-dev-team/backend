package com.f5.scholarship_backend.domain.matching.service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import org.springframework.stereotype.Service;

import com.f5.scholarship_backend.domain.matching.enums.ConditionReason;
import com.f5.scholarship_backend.domain.matching.enums.ConditionStatus;
import com.f5.scholarship_backend.domain.matching.enums.EligibilityStatus;
import com.f5.scholarship_backend.domain.matching.model.ConditionEvaluation;
import com.f5.scholarship_backend.domain.matching.model.EligibilityResult;

@Service
public class EligibilityEvaluator {

	/**
	 * 하나의 장학금 버전에 속하는 단순 AND 조건 전체의 결과를 종합한다.
	 * requirementsComplete는 호출자가 조건 추출의 완전성을 확인해 전달한다.
	 * OR·예외 조건은 사전 해석 없이 단순 조건들로 나누어 전달하지 않는다.
	 */
	public EligibilityResult evaluate(List<ConditionEvaluation> conditions, boolean requirementsComplete) {
		Objects.requireNonNull(conditions, "조건별 판정 결과 목록은 null일 수 없습니다.");
		List<ConditionEvaluation> unmet = new ArrayList<>();
		List<ConditionEvaluation> unresolved = new ArrayList<>();
		Set<String> conditionIds = new HashSet<>();
		boolean requiredNotMet = false;
		boolean requiredUnresolved = false;

		for (ConditionEvaluation condition : conditions) {
			Objects.requireNonNull(condition, "조건별 판정 결과는 null일 수 없습니다.");
			if (!conditionIds.add(condition.conditionId())) {
				throw new IllegalArgumentException("조건 ID가 중복되었습니다: " + condition.conditionId());
			}
			if (condition.status() == ConditionStatus.NOT_MET) {
				unmet.add(condition);
				requiredNotMet |= condition.required();
			} else if (condition.status() == ConditionStatus.NEEDS_REVIEW) {
				unresolved.add(condition);
				requiredUnresolved |= condition.required();
			}
		}

		boolean incomplete = !requirementsComplete || conditions.isEmpty();
		EligibilityStatus status;
		if (requiredNotMet) {
			status = EligibilityStatus.INELIGIBLE;
		} else if (requiredUnresolved || incomplete) {
			status = EligibilityStatus.NEEDS_REVIEW;
		} else {
			status = EligibilityStatus.ELIGIBLE;
		}

		return new EligibilityResult(status, unmet, unresolved,
				incomplete ? List.of(ConditionReason.REQUIREMENTS_INCOMPLETE) : List.of());
	}
}
