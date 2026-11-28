package com.f5.scholarship_backend.domain.document.dto;

import java.util.List;

public record DocumentAnalysisRequest(String scholarshipId, int scholarshipVersion, String sourceText,
		List<Document> documents) {
	public record Document(String documentId, String objectKey) { }
}
