package com.f5.scholarship_backend.domain.matching.service;

import java.math.BigDecimal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

import com.f5.scholarship_backend.domain.matching.enums.ConditionReason;
import com.f5.scholarship_backend.domain.matching.enums.ConditionStatus;
import com.f5.scholarship_backend.domain.matching.enums.NumericOperator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

class NumericConditionMatcherTest {

	private final NumericConditionMatcher matcher = new NumericConditionMatcher();

	@DisplayName("이상·이하 조건은 경계값과 소수 자릿수가 다른 같은 값을 포함한다")
	@ParameterizedTest
	@CsvSource({
			"3.7, GTE, 3.5",
			"3.5, GTE, 3.5",
			"3.50, GTE, 3.5",
			"4, LTE, 5",
			"5, LTE, 5",
			"5.00, LTE, 5",
			"0, GTE, 0",
			"0, LTE, 0"
	})
	void matchesInclusiveThreshold(String actual, NumericOperator operator, String expected) {
		var result = matcher.match(new BigDecimal(actual), operator, new BigDecimal(expected));

		assertThat(result.status()).isEqualTo(ConditionStatus.MET);
		assertThat(result.gap()).isEqualByComparingTo(BigDecimal.ZERO);
		assertThat(result.reason()).isNull();
	}

	@DisplayName("미충족이면 최소 기준 부족량 또는 최대 기준 초과량을 정확히 계산한다")
	@ParameterizedTest
	@CsvSource({
			"3.3, GTE, 3.5, 0.2",
			"20, GTE, 30, 10",
			"6, LTE, 5, 1",
			"0, GTE, 30, 30",
			"3.499, GTE, 3.5, 0.001",
			"5.001, LTE, 5, 0.001"
	})
	void reportsExactGap(String actual, NumericOperator operator, String expected, String gap) {
		var result = matcher.match(new BigDecimal(actual), operator, new BigDecimal(expected));

		assertThat(result.status()).isEqualTo(ConditionStatus.NOT_MET);
		assertThat(result.gap()).isEqualByComparingTo(new BigDecimal(gap));
		assertThat(result.reason()).isEqualTo(ConditionReason.CONDITION_NOT_MET);
	}

	@DisplayName("학생 정보 누락은 0이나 미충족으로 취급하지 않는다")
	@ParameterizedTest
	@EnumSource(NumericOperator.class)
	void missingValueNeedsReview(NumericOperator operator) {
		var result = matcher.match(null, operator, new BigDecimal("3.5"));

		assertThat(result.status()).isEqualTo(ConditionStatus.NEEDS_REVIEW);
		assertThat(result.gap()).isNull();
		assertThat(result.reason()).isEqualTo(ConditionReason.PROFILE_VALUE_MISSING);
	}

	@DisplayName("기준값 없는 잘못된 내부 호출은 정상 판정으로 처리하지 않는다")
	@ParameterizedTest
	@EnumSource(NumericOperator.class)
	void rejectsMissingThreshold(NumericOperator operator) {
		assertThatNullPointerException()
				.isThrownBy(() -> matcher.match(BigDecimal.ONE, operator, null));
	}
}
