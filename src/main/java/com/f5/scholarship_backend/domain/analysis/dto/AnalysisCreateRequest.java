package com.f5.scholarship_backend.domain.analysis.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record AnalysisCreateRequest(
		@Schema(description = "저장 API에서 받은 학생 정보 버전", example = "1")
		@NotNull(message = "프로필 버전은 필수입니다.")
		@Min(value = 1, message = "프로필 버전은 1 이상이어야 합니다.")
		Long profileVersion
) {
}
