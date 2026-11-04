package com.f5.scholarship_backend.domain.analysis.dto;

import java.time.OffsetDateTime;
import com.f5.scholarship_backend.domain.analysis.enums.AnalysisStatus;
import io.swagger.v3.oas.annotations.media.Schema;

public record AnalysisCreateResponse(
		@Schema(description = "분석 작업 ID", example = "5001") String analysisId,
		@Schema(description = "접수 시 QUEUED. 분석 완료를 의미하지 않습니다.", allowableValues = {"QUEUED"})
		AnalysisStatus status,
		@Schema(description = "분석에 고정한 프로필 버전", example = "1") long profileVersion,
		@Schema(description = "작업 접수 시각") OffsetDateTime createdAt
) {
}
