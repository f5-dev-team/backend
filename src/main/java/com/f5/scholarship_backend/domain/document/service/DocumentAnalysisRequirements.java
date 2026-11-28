package com.f5.scholarship_backend.domain.document.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import com.f5.scholarship_backend.domain.document.client.DocumentException;
import com.f5.scholarship_backend.domain.document.dto.DocumentAnalysisResult;
import com.f5.scholarship_backend.domain.document.dto.DocumentAnalysisResult.Requirement;
import com.f5.scholarship_backend.domain.scholarship.entity.ScholarshipCondition;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import static com.f5.scholarship_backend.domain.document.client.DocumentAnalysisClient.INVALID_RESPONSE;

@Component
class DocumentAnalysisRequirements {
	private static final Set<String> NUMERIC_FIELDS = Set.of("grade", "cumulativeGpa", "previousSemesterGpa",
			"age", "volunteerHours");
	private static final Set<String> STRING_FIELDS = Set.of("department", "enrollmentStatus", "residenceAddress", "gender");
	private static final Set<String> OPERATORS = Set.of("GTE", "LTE", "EQ", "IN", "TEXT");
	private final JsonMapper json;

	DocumentAnalysisRequirements(JsonMapper json) {
		this.json = json;
	}

	List<ScholarshipCondition> validate(DocumentAnalysisResult result, long id, int version, Set<String> documentIds) {
		if (result.requirementsComplete() == null || result.requirements() == null) {
			throw new DocumentException(INVALID_RESPONSE);
		}
		var conditions = new ArrayList<ScholarshipCondition>();
		for (var requirement : result.requirements()) {
			validateCondition(requirement);
			var location = requirement.sourceLocation();
			String documentId = location == null ? null : location.documentId();
			Integer page = location == null ? null : location.page();
			String section = location == null ? null : location.section();
			if ((documentId != null && !documentIds.contains(documentId))
					|| (page != null && (page < 1 || documentId == null))
					|| (section != null && section.length() > 255)) {
				throw new DocumentException(INVALID_RESPONSE);
			}
			var expected = requirement.expectedValue();
			conditions.add(new ScholarshipCondition(id, version, requirement.field(), requirement.operator(),
					expected == null || expected.isNull() ? null : json.writeValueAsString(expected),
					requirement.description(), requirement.required(), requirement.sourceQuote(), requirement.gpaScale(),
					documentId == null ? null : Long.valueOf(documentId), page, section));
		}
		return conditions;
	}

	private static void validateCondition(Requirement requirement) {
		if (requirement == null || requirement.field() == null || requirement.operator() == null
				|| !OPERATORS.contains(requirement.operator()) || requirement.required() == null
				|| requirement.description() == null || requirement.description().isBlank()) {
			throw new DocumentException(INVALID_RESPONSE);
		}
		String field = requirement.field();
		boolean numeric = NUMERIC_FIELDS.contains(field);
		boolean text = STRING_FIELDS.contains(field);
		boolean income = "incomeBracket".equals(field);
		if (!numeric && !text && !income && !"other".equals(field)) {
			throw new DocumentException(INVALID_RESPONSE);
		}
		var expected = requirement.expectedValue();
		boolean valid = switch (requirement.operator()) {
			case "TEXT" -> expected == null || expected.isNull();
			case "GTE", "LTE" -> (numeric || income) && expected != null && expected.isNumber();
			case "EQ" -> validScalar(expected, numeric || income, text || income);
			case "IN" -> validList(expected, numeric || income, text || income);
			default -> false;
		};
		boolean gpa = "cumulativeGpa".equals(field) || "previousSemesterGpa".equals(field);
		var scale = requirement.gpaScale();
		if (!valid || (scale != null && (!gpa || "TEXT".equals(requirement.operator()) || scale.signum() <= 0
				|| scale.compareTo(new BigDecimal("99.99")) > 0 || scale.stripTrailingZeros().scale() > 2
				|| exceedsScale(expected, scale)))) {
			throw new DocumentException(INVALID_RESPONSE);
		}
	}

	private static boolean exceedsScale(JsonNode value, BigDecimal scale) {
		if (value.isArray()) {
			return value.valueStream().anyMatch(element -> element.isNumber() && element.decimalValue().compareTo(scale) > 0);
		}
		return value.isNumber() && value.decimalValue().compareTo(scale) > 0;
	}

	private static boolean validScalar(JsonNode value, boolean canBeNumber, boolean canBeText) {
		return value != null && ((canBeNumber && value.isNumber())
				|| (canBeText && value.isString() && !value.asString().isBlank()));
	}

	private static boolean validList(JsonNode value, boolean canBeNumber, boolean canBeText) {
		if (value == null || !value.isArray() || value.isEmpty()) {
			return false;
		}
		boolean numeric = value.get(0).isNumber();
		for (JsonNode element : value) {
			if (element.isNumber() != numeric || !validScalar(element, canBeNumber, canBeText)) {
				return false;
			}
		}
		return true;
	}
}
