package com.f5.scholarship_backend.domain.document.controller.docs;

import com.f5.scholarship_backend.domain.document.dto.DocumentAccessResponse;
import com.f5.scholarship_backend.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;

@Tag(name = "장학 공고 원본")
public interface DocumentAccessControllerDocs {
	@Operation(operationId = "getDocumentAccess", summary = "장학 공고 원본 접근 정보 조회",
			description = "장학금에 연결된 현재 또는 과거 원본의 임시 URL을 반환합니다. URL 만료 시 다시 조회할 수 있습니다.")
	@SecurityRequirement(name = "bearerAuth")
	@ApiResponses({
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "원본 접근 정보 조회",
					useReturnTypeSchema = true),
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "FORBIDDEN",
					content = @Content(mediaType = "application/problem+json",
							schema = @Schema(ref = "#/components/schemas/ApiProblem"))),
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404",
					description = "RESOURCE_NOT_FOUND: 해당 장학금에 연결된 문서가 없음",
					content = @Content(mediaType = "application/problem+json",
							schema = @Schema(ref = "#/components/schemas/ApiProblem"))),
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "503",
					description = "SERVICE_UNAVAILABLE: 원본 접근 URL 발급 불가",
					content = @Content(mediaType = "application/problem+json",
							schema = @Schema(ref = "#/components/schemas/ApiProblem")))
	})
	ResponseEntity<ApiResponse<DocumentAccessResponse>> getDocumentAccess(
			@Parameter(description = "장학금 ID", example = "1001") String scholarshipId,
			@Parameter(description = "장학금에 연결된 문서 ID", example = "2001") String documentId,
			@Parameter(hidden = true) HttpServletRequest servletRequest);
}
