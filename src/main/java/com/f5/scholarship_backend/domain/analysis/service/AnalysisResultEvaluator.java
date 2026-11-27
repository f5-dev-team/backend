package com.f5.scholarship_backend.domain.analysis.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import com.f5.scholarship_backend.domain.analysis.enums.ApplicationPeriodStatus;
import com.f5.scholarship_backend.domain.matching.enums.ConditionReason;
import com.f5.scholarship_backend.domain.matching.enums.ConditionStatus;
import com.f5.scholarship_backend.domain.matching.enums.EligibilityStatus;
import com.f5.scholarship_backend.domain.matching.enums.NumericOperator;
import com.f5.scholarship_backend.domain.matching.model.ConditionEvaluation;
import com.f5.scholarship_backend.domain.matching.service.EligibilityEvaluator;
import com.f5.scholarship_backend.domain.matching.service.NumericConditionMatcher;
import com.f5.scholarship_backend.domain.scholarship.entity.ScholarshipCondition;
import com.f5.scholarship_backend.domain.student.enums.EnrollmentStatus;
import com.f5.scholarship_backend.domain.student.enums.Gender;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@Component
class AnalysisResultEvaluator {
	private static final BigDecimal GPA_MAX = new BigDecimal("4.5");
	private static final Pattern GPA_45 = Pattern.compile("(?<![0-9.])4\\.5\\s*(?:점\\s*)?(?:만점|기준)");
	private static final Pattern GPA_43 = Pattern.compile("(?<![0-9.])4\\.3(?![0-9])");

	private final NumericConditionMatcher matcher;
	private final EligibilityEvaluator evaluator;
	private final JsonMapper json;

	AnalysisResultEvaluator(NumericConditionMatcher matcher, EligibilityEvaluator evaluator, JsonMapper json) {
		this.matcher = matcher;
		this.evaluator = evaluator;
		this.json = json;
	}

	Result evaluate(JsonNode profile, List<ScholarshipCondition> conditions, Instant acceptedAt,
			Instant startAt, Instant endAt) {
		var evaluations = new ArrayList<ConditionEvaluation>();
		var unmet = new ArrayList<ConditionResult>();
		var unresolved = new ArrayList<ConditionResult>();
		for (var condition : conditions) {
			JsonNode actual = profile.path(condition.getFieldName());
			var evaluation = evaluateCondition(condition, actual);
			evaluations.add(evaluation);
			switch (evaluation.status()) {
				case MET -> { }
				case NOT_MET -> unmet.add(conditionResult(condition, actual, evaluation));
				case NEEDS_REVIEW -> unresolved.add(conditionResult(condition, actual, evaluation));
			}
		}

		// Only READY versions reach the worker. The shared evaluator handles an empty condition list.
		var finalResult = evaluator.evaluate(evaluations, true);
		for (ConditionReason reason : finalResult.reviewReasons()) {
			unresolved.add(new ConditionResult(null, "other", true, null, "공고 자격조건 확인",
					null, null, reason, "저장된 자격조건을 확인할 수 없습니다."));
		}
		return new Result(finalResult.status(), periodAt(acceptedAt, startAt, endAt),
				List.of(summary(finalResult.status())), unmet, unresolved);
	}

	private ConditionEvaluation evaluateCondition(ScholarshipCondition condition, JsonNode actual) {
		if ("TEXT".equals(condition.getOperator()) || condition.getExpectedValue() == null) {
			return review(condition, ConditionReason.UNSUPPORTED_RULE);
		}
		JsonNode expected = json.readTree(condition.getExpectedValue());
		return switch (condition.getFieldName()) {
			case "grade" -> compareNumber(condition, actual, expected, AnalysisResultEvaluator::isGrade);
			case "cumulativeGpa", "previousSemesterGpa" -> hasConfirmedGpaScale(condition)
					? compareNumber(condition, actual, expected, AnalysisResultEvaluator::isGpa)
					: review(condition, ConditionReason.UNSUPPORTED_RULE);
			case "enrollmentStatus" -> compareExact(condition, actual, expected,
					value -> isEnumName(value, EnrollmentStatus.values()));
			case "gender" -> compareExact(condition, actual, expected, value -> isEnumName(value, Gender.values()));
			default -> review(condition, ConditionReason.UNSUPPORTED_RULE);
		};
	}

	private ConditionEvaluation compareNumber(ScholarshipCondition condition, JsonNode actual, JsonNode expected,
			Predicate<JsonNode> validNumber) {
		String operator = condition.getOperator();
		if (!"GTE".equals(operator) && !"LTE".equals(operator)) {
			return compareExact(condition, actual, expected, validNumber);
		}
		if (!validNumber.test(expected)) {
			return review(condition, ConditionReason.UNSUPPORTED_RULE);
		}

		BigDecimal actualValue = validNumber.test(actual) ? actual.decimalValue() : null;
		var result = matcher.match(actualValue, NumericOperator.valueOf(operator), expected.decimalValue());
		return ConditionEvaluation.fromNumeric(condition.getConditionId(), condition.isRequired(), result);
	}

	private static ConditionEvaluation compareExact(ScholarshipCondition condition, JsonNode actual, JsonNode expected,
			Predicate<JsonNode> validValue) {
		List<JsonNode> options = new ArrayList<>();
		switch (condition.getOperator()) {
			case "EQ" -> options.add(expected);
			case "IN" -> {
				if (expected.isArray()) {
					expected.forEach(options::add);
				}
			}
			default -> { }
		}
		if (options.isEmpty() || options.stream().anyMatch(value -> !validValue.test(value))) {
			return review(condition, ConditionReason.UNSUPPORTED_RULE);
		}
		if (!validValue.test(actual)) {
			return review(condition, ConditionReason.PROFILE_VALUE_MISSING);
		}
		boolean matched = options.stream().anyMatch(option -> actual.isNumber()
				? actual.decimalValue().compareTo(option.decimalValue()) == 0 : actual.equals(option));
		return new ConditionEvaluation(condition.getConditionId(), condition.isRequired(),
				matched ? ConditionStatus.MET : ConditionStatus.NOT_MET, null,
				matched ? null : ConditionReason.CONDITION_NOT_MET);
	}

	private static ConditionEvaluation review(ScholarshipCondition condition, ConditionReason reason) {
		return new ConditionEvaluation(condition.getConditionId(), condition.isRequired(),
				ConditionStatus.NEEDS_REVIEW, null, reason);
	}

	private static boolean isGrade(JsonNode value) {
		return value.isIntegralNumber() && value.decimalValue().compareTo(BigDecimal.ONE) >= 0
				&& value.decimalValue().compareTo(BigDecimal.valueOf(65535)) <= 0;
	}

	private static boolean isGpa(JsonNode value) {
		return value.isNumber() && value.decimalValue().signum() >= 0 && value.decimalValue().compareTo(GPA_MAX) <= 0;
	}

	private static boolean hasConfirmedGpaScale(ScholarshipCondition condition) {
		String quote = condition.getSourceQuote();
		return quote != null && GPA_45.matcher(quote).find() && !GPA_43.matcher(quote).find();
	}

	private static boolean isEnumName(JsonNode value, Enum<?>[] options) {
		if (!value.isString()) {
			return false;
		}
		for (Enum<?> option : options) {
			if (option.name().equals(value.asString())) {
				return true;
			}
		}
		return false;
	}

	private static ConditionResult conditionResult(ScholarshipCondition condition, JsonNode actual,
			ConditionEvaluation evaluation) {
		String field = condition.getFieldName();
		Object actualValue = actual.isNumber() ? actual.decimalValue() : actual.isString() ? actual.asString() : null;
		String unit = evaluation.gap() == null ? null : "grade".equals(field) ? "학년" : "학점";
		String message = switch (evaluation.reason()) {
			case CONDITION_NOT_MET -> "저장된 학생 정보가 공고의 조건을 충족하지 않습니다.";
			case PROFILE_VALUE_MISSING -> "조건을 비교할 학생 정보가 필요합니다.";
			default -> "공고 원문에서 조건을 직접 확인해 주세요.";
		};
		return new ConditionResult(condition.getConditionId(), field, condition.isRequired(), actualValue,
				condition.getDescription(), evaluation.gap(), unit, evaluation.reason(), message);
	}

	private static ApplicationPeriodStatus periodAt(Instant at, Instant start, Instant end) {
		if (start != null && end != null && start.isAfter(end)) {
			return ApplicationPeriodStatus.UNKNOWN;
		}
		if (start != null && at.isBefore(start)) {
			return ApplicationPeriodStatus.NOT_STARTED;
		}
		if (end != null && at.isAfter(end)) {
			return ApplicationPeriodStatus.CLOSED;
		}
		return start != null && end != null ? ApplicationPeriodStatus.OPEN : ApplicationPeriodStatus.UNKNOWN;
	}

	private static String summary(EligibilityStatus status) {
		return switch (status) {
			case ELIGIBLE -> "필수 자격조건을 충족했습니다.";
			case INELIGIBLE -> "충족하지 못한 필수 자격조건이 있습니다.";
			case NEEDS_REVIEW -> "확인이 필요한 자격조건이 있습니다.";
		};
	}

	record Result(EligibilityStatus status, ApplicationPeriodStatus period, List<String> reasons,
			List<ConditionResult> unmet, List<ConditionResult> unresolved) { }

	record ConditionResult(String conditionId, String field, boolean required, Object actualValue,
			String expectedDescription, BigDecimal gap, String unit, ConditionReason reasonCode, String message) { }
}
