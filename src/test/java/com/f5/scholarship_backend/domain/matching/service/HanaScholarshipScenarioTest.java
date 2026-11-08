package com.f5.scholarship_backend.domain.matching.service;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import com.f5.scholarship_backend.domain.matching.enums.*;
import com.f5.scholarship_backend.domain.matching.model.ConditionEvaluation;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 2026-2 하나장학생 게시글 본문을 사람이 읽고 구성한 가상 사례.
 * 출처: https://www.gachon.ac.kr/bbs/kor/478/125712/artclView.do
 * 확인일: 2026-11-04. 첨부파일 내용은 미검증.
 * 공고 자동 추출, 장애 면제 해석, 적용 학기 선택을 구현한 테스트가 아니다.
 * 숫자 테스트는 동일 만점·해당 학기·면제 비대상이라는 전제를 사용한다.
 * 기간 및 최종 선발 여부는 이 테스트 범위 밖이다.
 */
class HanaScholarshipScenarioTest {
    private final NumericConditionMatcher matcher = new NumericConditionMatcher();
    private final EligibilityEvaluator evaluator = new EligibilityEvaluator();

    @DisplayName("면제 비대상이고 동일 만점이면 직전 학기 평점 3.0 경계를 비교한다")
    @ParameterizedTest
    @CsvSource({"3.0, MET, 0", "2.9, NOT_MET, 0.1", "3.1, MET, 0"})
    void gpaBoundary(String actual, ConditionStatus status, String gap) {
        var result = matcher.match(new BigDecimal(actual), NumericOperator.GTE, new BigDecimal("3.0"));
        assertThat(result.status()).isEqualTo(status);
        assertThat(result.gap()).isEqualByComparingTo(gap);
    }

    @DisplayName("적용 학기가 확인된 지원구간의 5 이하 경계를 비교한다")
    @ParameterizedTest
    @CsvSource({"5, MET, 0", "6, NOT_MET, 1", "4, MET, 0"})
    void incomeBoundary(String actual, ConditionStatus status, String gap) {
        var result = matcher.match(new BigDecimal(actual), NumericOperator.LTE, new BigDecimal("5"));
        assertThat(result.status()).isEqualTo(status);
        assertThat(result.gap()).isEqualByComparingTo(gap);
    }

    @DisplayName("이수학점 12는 평점과 별개의 수치로 비교한다 - 프로필 연결은 미구현")
    @ParameterizedTest
    @CsvSource({"12, MET, 0", "11, NOT_MET, 1"})
    void earnedCreditsBoundary(String actual, ConditionStatus status, String gap) {
        var result = matcher.match(new BigDecimal(actual), NumericOperator.GTE, new BigDecimal("12"));
        assertThat(result.status()).isEqualTo(status);
        assertThat(result.gap()).isEqualByComparingTo(gap);
    }

    @DisplayName("평점 2.9여도 면제 여부 미확인을 수동 표시하면 확정 부적격으로 바꾸지 않는다")
    @Test
    void unresolvedExemptionIsPreserved() {
        // 낮은 평점만으로 NOT_MET을 넘기지 않는다. 예외 판단은 호출자의 책임이다.
        var raw = matcher.match(new BigDecimal("2.9"), NumericOperator.GTE, new BigDecimal("3.0"));
        assertThat(raw.status()).isEqualTo(ConditionStatus.NOT_MET);
        var pending = unresolved("gpa-with-disability-exemption");
        var result = evaluator.evaluate(List.of(pending), false);
        assertThat(result.status()).isEqualTo(EligibilityStatus.NEEDS_REVIEW);
        assertThat(result.unmetConditions()).isEmpty();
        assertThat(result.unresolvedConditions()).containsExactly(pending);
    }

    @DisplayName("소득 숫자가 있어도 적용 학기가 불명확하면 수동으로 확인 필요를 전달한다")
    @Test
    void unknownIncomeSemester() {
        var pending = unresolved("income-semester-rule");
        var result = evaluator.evaluate(List.of(pending), false);
        assertThat(result.status()).isEqualTo(EligibilityStatus.NEEDS_REVIEW);
        assertThat(result.unresolvedConditions()).containsExactly(pending);
    }

    @DisplayName("숫자 조건 통과만으로 공고 전체를 적격 판정하지 않는다")
    @Test
    void passingNumbersDoNotProveFullEligibility() {
        var result = evaluator.evaluate(List.of(
                numeric("gpa", "3.0", NumericOperator.GTE, "3.0"),
                numeric("income", "5", NumericOperator.LTE, "5"),
                unresolved("nationality"), unresolved("enrollment"), unresolved("earned-credits")), false);
        assertThat(result.status()).isEqualTo(EligibilityStatus.NEEDS_REVIEW);
        assertThat(result.unmetConditions()).isEmpty();
        assertThat(result.unresolvedConditions()).extracting(ConditionEvaluation::conditionId)
                .containsExactly("nationality", "enrollment", "earned-credits");
        assertThat(result.reviewReasons()).containsExactly(ConditionReason.REQUIREMENTS_INCOMPLETE);
    }

    @DisplayName("적용 학기 확인된 소득구간 6이면 부적격이며 다른 미확정 사유도 남긴다")
    @Test
    void confirmedFailurePreservesPendingConditions() {
        var result = evaluator.evaluate(List.of(
                numeric("income", "6", NumericOperator.LTE, "5"),
                unresolved("gpa-with-disability-exemption"), unresolved("nationality")), false);
        assertThat(result.status()).isEqualTo(EligibilityStatus.INELIGIBLE);
        assertThat(result.unmetConditions()).extracting(ConditionEvaluation::conditionId).containsExactly("income");
        assertThat(result.unmetConditions().get(0).gap()).isEqualByComparingTo("1");
        assertThat(result.unresolvedConditions()).hasSize(2);
        assertThat(result.reviewReasons()).containsExactly(ConditionReason.REQUIREMENTS_INCOMPLETE);
    }

    private ConditionEvaluation numeric(String id, String actual, NumericOperator operator, String expected) {
        return ConditionEvaluation.fromNumeric(id, true,
                matcher.match(new BigDecimal(actual), operator, new BigDecimal(expected)));
    }

    private ConditionEvaluation unresolved(String id) {
        return new ConditionEvaluation(id, true, ConditionStatus.NEEDS_REVIEW, null, ConditionReason.UNSUPPORTED_RULE);
    }
}