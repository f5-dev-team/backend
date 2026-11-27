package com.f5.scholarship_backend.domain.document.dto;

import java.time.OffsetDateTime;
import io.swagger.v3.oas.annotations.media.Schema;

public record DocumentAccessResponse(
		String documentId,
		String fileName,
		@Schema(allowableValues = {"PDF", "HWP"}) String fileType,
		String sourceUrl,
		String downloadUrl,
		OffsetDateTime expiresAt
) { }
