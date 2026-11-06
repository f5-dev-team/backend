package com.f5.scholarship_backend.domain.matching.service;

import java.math.BigDecimal;
import java.util.Objects;

import org.springframework.stereotype.Service;

import com.f5.scholarship_backend.domain.matching.enums.ConditionReason;
import com.f5.scholarship_backend.domain.matching.enums.ConditionStatus;
import com.f5.scholarship_backend.domain.matching.enums.NumericOperator;
import com.f5.scholarship_backend.domain.matching.model.NumericConditionResult;

@Service
public class NumericConditionMatcher {

	/**
	 * 같은 만점·단위·기간 기준으로 비교 가능한 숫자만 전달한다.
	 * 환산이나 반올림은 수행하지 않는다. actualValue의 null은 학생 정보 미확인이다.
	 * operator와 expectedValue는 호출 전에 확인된 유효한 조건이어야 한다.
	 */
	public NumericConditionResult match(
			BigDecimal actualValue,
			NumericOperator operator,
			BigDecimal expectedValue
	) {
		Objects.requireNonNull(operator, "비교 연산자는 null일 수 없습니다.");
		Objects.requireNonNull(expectedValue, "비교 기준값은 null일 수 없습니다.");

		if (actualValue == null) {
			return new NumericConditionResult(
					ConditionStatus.NEEDS_REVIEW, null, ConditionReason.PROFILE_VALUE_MISSING);
		}

		BigDecimal difference = switch (operator) {
			case GTE -> expectedValue.subtract(actualValue);
			case LTE -> actualValue.subtract(expectedValue);
		};

		if (difference.signum() <= 0) {
			return new NumericConditionResult(ConditionStatus.MET, BigDecimal.ZERO, null);
		}

		return new NumericConditionResult(
				ConditionStatus.NOT_MET, difference, ConditionReason.CONDITION_NOT_MET);
	}
}
