package com.f5.scholarship_backend.domain.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public record EmailAvailabilityResponse(
		@Schema(description = "가입 가능 여부", example = "true") boolean available
) {
}
