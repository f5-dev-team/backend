package com.f5.scholarship_backend.domain.document.dto;

import java.math.BigDecimal;
import java.util.List;
import tools.jackson.databind.JsonNode;

public record DocumentAnalysisResult(String analysisId, String scholarshipId, Integer scholarshipVersion,
		String processingStatus, Boolean requirementsComplete, List<Requirement> requirements) {
	public record Accepted(String analysisId, String processingStatus) { }

	public record Requirement(String field, String operator, JsonNode expectedValue, BigDecimal gpaScale,
			Boolean required, String description, String sourceQuote, SourceLocation sourceLocation) { }

	public record SourceLocation(String documentId, Integer page, String section) { }
}
