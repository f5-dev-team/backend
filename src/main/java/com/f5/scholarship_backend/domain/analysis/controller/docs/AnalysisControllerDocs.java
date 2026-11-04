package com.f5.scholarship_backend.domain.analysis.controller.docs;

import java.security.Principal;
import com.f5.scholarship_backend.domain.analysis.dto.AnalysisCreateRequest;
import com.f5.scholarship_backend.domain.analysis.dto.AnalysisCreateResponse;
import com.f5.scholarship_backend.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;

@Tag(name = "분석", description = "장학금 자격 분석 작업")
public interface AnalysisControllerDocs {
	@Operation(operationId = "createAnalysis", summary = "장학금 분석 작업 접수",
			description = "저장된 본인 프로필의 버전으로 분석을 요청합니다. 202는 접수만 의미합니다. "
					+ "재호출은 새 작업을 생성하는 계약입니다. 현재 DB·작업 실행 및 인증 연동 전 뼈대 단계입니다.")
	@SecurityRequirement(name = "bearerAuth")
	@ApiResponses({
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "202", description = "분석 작업 접수",
					useReturnTypeSchema = true, headers = @Header(name = "Location", description = "분석 상태 조회 주소",
					schema = @Schema(type = "string", example = "/api/v1/analyses/5001"))),
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", ref = "#/components/responses/ProfileConflict")
	})
	ResponseEntity<ApiResponse<AnalysisCreateResponse>> createAnalysis(
			AnalysisCreateRequest request, @Parameter(hidden = true) Principal principal,
			@Parameter(hidden = true) HttpServletRequest servletRequest);
}
