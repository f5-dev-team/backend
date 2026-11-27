package com.f5.scholarship_backend.domain.analysis.dto;

import java.util.List;
import com.f5.scholarship_backend.domain.analysis.enums.EvidenceStatus;
import io.swagger.v3.oas.annotations.media.Schema;

public record AnalysisEvidenceResponse(
		String analysisId,
		String scholarshipId,
		int scholarshipVersion,
		EvidenceStatus evidenceStatus,
		String sourceUrl,
		List<Document> documents,
		List<Evidence> items,
		@Schema(nullable = true) String message
) {
	public record Document(String documentId, String fileName, String fileType) { }

	public record Evidence(
			String conditionId,
			@Schema(nullable = true) String documentId,
			String quote,
			String explanation,
			@Schema(nullable = true) Integer page,
			@Schema(nullable = true) String section,
			String sourceUrl
	) { }
}
