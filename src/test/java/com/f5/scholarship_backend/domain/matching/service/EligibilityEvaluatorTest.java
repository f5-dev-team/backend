package com.f5.scholarship_backend.domain.matching.service;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import com.f5.scholarship_backend.domain.matching.enums.ConditionReason;
import com.f5.scholarship_backend.domain.matching.enums.ConditionStatus;
import com.f5.scholarship_backend.domain.matching.enums.EligibilityStatus;
import com.f5.scholarship_backend.domain.matching.enums.NumericOperator;
import com.f5.scholarship_backend.domain.matching.model.ConditionEvaluation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

class EligibilityEvaluatorTest {

	private final EligibilityEvaluator evaluator = new EligibilityEvaluator();
	private final NumericConditionMatcher matcher = new NumericConditionMatcher();

	@DisplayName("필수 조건의 모든 두 상태 조합에서 미충족, 확인 필요, 충족 순으로 판정한다")
	@ParameterizedTest
	@CsvSource({
			"MET, MET, ELIGIBLE",
			"MET, NOT_MET, INELIGIBLE",
			"MET, NEEDS_REVIEW, NEEDS_REVIEW",
			"NOT_MET, MET, INELIGIBLE",
			"NOT_MET, NOT_MET, INELIGIBLE",
			"NOT_MET, NEEDS_REVIEW, INELIGIBLE",
			"NEEDS_REVIEW, MET, NEEDS_REVIEW",
			"NEEDS_REVIEW, NOT_MET, INELIGIBLE",
			"NEEDS_REVIEW, NEEDS_REVIEW, NEEDS_REVIEW"
	})
	void combinesRequiredConditions(ConditionStatus first, ConditionStatus second, EligibilityStatus expected) {
		var conditions = List.of(condition("first", true, first), condition("second", true, second));
		var result = evaluator.evaluate(conditions, true);

		assertThat(result.status()).isEqualTo(expected);
		assertThat(result.unmetConditions()).containsExactlyElementsOf(conditions.stream()
				.filter(c -> c.status() == ConditionStatus.NOT_MET).toList());
		assertThat(result.unresolvedConditions()).containsExactlyElementsOf(conditions.stream()
				.filter(c -> c.status() == ConditionStatus.NEEDS_REVIEW).toList());
		assertThat(result.reviewReasons()).isEmpty();
	}

	@DisplayName("우대 조건의 미충족과 미확정은 보존하되 최종 판정을 악화시키지 않는다")
	@ParameterizedTest
	@CsvSource({
			"MET, ELIGIBLE",
			"NOT_MET, INELIGIBLE",
			"NEEDS_REVIEW, NEEDS_REVIEW"
	})
	void optionalConditionsDoNotChangeEligibility(ConditionStatus requiredStatus, EligibilityStatus expected) {
		var optionalUnmet = condition("optional-unmet", false, ConditionStatus.NOT_MET);
		var optionalUnresolved = condition("optional-unresolved", false, ConditionStatus.NEEDS_REVIEW);
		var result = evaluator.evaluate(List.of(
				condition("required", true, requiredStatus), optionalUnmet, optionalUnresolved), true);

		assertThat(result.status()).isEqualTo(expected);
		assertThat(result.unmetConditions()).contains(optionalUnmet);
		assertThat(result.unresolvedConditions()).contains(optionalUnresolved);
	}

	@DisplayName("완전한 조건 목록이 우대 조건만 포함하면 우대 미충족으로 탈락시키지 않는다")
	@Test
	void optionalOnlyConditions() {
		var result = evaluator.evaluate(List.of(condition("optional", false, ConditionStatus.NOT_MET)), true);
		assertThat(result.status()).isEqualTo(EligibilityStatus.ELIGIBLE);
		assertThat(result.unmetConditions()).hasSize(1);
	}

	@DisplayName("빈 조건 목록은 완료 표시와 무관하게 자동 적격 처리하지 않는다")
	@ParameterizedTest
	@ValueSource(booleans = {true, false})
	void emptyRequirementsNeedReview(boolean complete) {
		var result = evaluator.evaluate(List.of(), complete);
		assertThat(result.status()).isEqualTo(EligibilityStatus.NEEDS_REVIEW);
		assertThat(result.reviewReasons()).containsExactly(ConditionReason.REQUIREMENTS_INCOMPLETE);
		assertThat(result.unmetConditions()).isEmpty();
		assertThat(result.unresolvedConditions()).isEmpty();
	}

	@DisplayName("추출 미완료는 사유를 보존하며 이미 확정된 부적격을 덮어쓰지 않는다")
	@ParameterizedTest
	@CsvSource({"MET, NEEDS_REVIEW", "NOT_MET, INELIGIBLE", "NEEDS_REVIEW, NEEDS_REVIEW"})
	void incompleteRequirements(ConditionStatus conditionStatus, EligibilityStatus expected) {
		var result = evaluator.evaluate(List.of(condition("known", true, conditionStatus)), false);
		assertThat(result.status()).isEqualTo(expected);
		assertThat(result.reviewReasons()).containsExactly(ConditionReason.REQUIREMENTS_INCOMPLETE);
	}

	@DisplayName("미지원 필수 조건은 확인 필요로 보존한다")
	@Test
	void unsupportedRuleNeedsReview() {
		var unsupported = new ConditionEvaluation("address", true, ConditionStatus.NEEDS_REVIEW,
				null, ConditionReason.UNSUPPORTED_RULE);
		var result = evaluator.evaluate(List.of(unsupported), true);
		assertThat(result.status()).isEqualTo(EligibilityStatus.NEEDS_REVIEW);
		assertThat(result.unresolvedConditions()).containsExactly(unsupported);
	}

	@DisplayName("실제 숫자 비교 결과의 ID·필수 여부·부족량·누락 사유를 최종 결과까지 보존한다")
	@Test
	void combinesNumericMatchingResults() {
		var gpa = ConditionEvaluation.fromNumeric("gpa", true,
				matcher.match(new BigDecimal("3.3"), NumericOperator.GTE, new BigDecimal("3.5")));
		var income = ConditionEvaluation.fromNumeric("income", true,
				matcher.match(new BigDecimal("6"), NumericOperator.LTE, new BigDecimal("5")));
		var volunteer = ConditionEvaluation.fromNumeric("volunteer", true,
				matcher.match(null, NumericOperator.GTE, new BigDecimal("30")));

		var result = evaluator.evaluate(List.of(gpa, income, volunteer), true);

		assertThat(result.status()).isEqualTo(EligibilityStatus.INELIGIBLE);
		assertThat(result.unmetConditions()).containsExactly(gpa, income);
		assertThat(result.unmetConditions().get(0).gap()).isEqualByComparingTo("0.2");
		assertThat(result.unmetConditions().get(1).gap()).isEqualByComparingTo("1");
		assertThat(result.unresolvedConditions()).containsExactly(volunteer);
		assertThat(volunteer.reason()).isEqualTo(ConditionReason.PROFILE_VALUE_MISSING);
	}

	@DisplayName("중복 조건 ID나 누락된 결과를 정상 판정하지 않는다")
	@Test
	void rejectsInvalidConditionCollection() {
		assertThatIllegalArgumentException().isThrownBy(() -> evaluator.evaluate(List.of(
				condition("same", true, ConditionStatus.MET),
				condition("same", true, ConditionStatus.NOT_MET)), true));
		assertThatNullPointerException().isThrownBy(() -> evaluator.evaluate(null, true));
	}

	@DisplayName("상태와 사유 또는 부족량이 모순되는 조건 결과를 거절한다")
	@Test
	void rejectsContradictoryEvaluation() {
		assertThatIllegalArgumentException().isThrownBy(() -> new ConditionEvaluation(
				"gpa", true, ConditionStatus.MET, BigDecimal.ONE, null));
		assertThatIllegalArgumentException().isThrownBy(() -> new ConditionEvaluation(
				"gpa", true, ConditionStatus.NOT_MET, BigDecimal.ONE, null));
		assertThatIllegalArgumentException().isThrownBy(() -> new ConditionEvaluation(
				"gpa", true, ConditionStatus.NEEDS_REVIEW, BigDecimal.ZERO,
				ConditionReason.PROFILE_VALUE_MISSING));
	}

	private ConditionEvaluation condition(String id, boolean required, ConditionStatus status) {
		return switch (status) {
			case MET -> new ConditionEvaluation(id, required, status, BigDecimal.ZERO, null);
			case NOT_MET -> new ConditionEvaluation(id, required, status, BigDecimal.ONE,
					ConditionReason.CONDITION_NOT_MET);
			case NEEDS_REVIEW -> new ConditionEvaluation(id, required, status, null,
					ConditionReason.PROFILE_VALUE_MISSING);
		};
	}
}
