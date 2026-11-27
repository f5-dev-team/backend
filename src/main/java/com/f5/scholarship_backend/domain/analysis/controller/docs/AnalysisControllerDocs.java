package com.f5.scholarship_backend.domain.analysis.controller.docs;

import java.security.Principal;
import com.f5.scholarship_backend.domain.analysis.dto.AnalysisCreateRequest;
import com.f5.scholarship_backend.domain.analysis.dto.AnalysisCreateResponse;
import com.f5.scholarship_backend.domain.analysis.dto.AnalysisRecommendationsResponse;
import com.f5.scholarship_backend.domain.analysis.dto.AnalysisStatusResponse;
import com.f5.scholarship_backend.domain.matching.enums.EligibilityStatus;
import com.f5.scholarship_backend.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;

@Tag(name = "분석", description = "장학금 자격 분석 작업")
public interface AnalysisControllerDocs {
	/** 분석 접수 API의 요청·응답과 상태 조회 주소를 문서화한다. */
	@Operation(operationId = "createAnalysis", summary = "장학금 분석 작업 접수",
			description = "저장된 본인 프로필의 버전으로 분석을 요청합니다. 202는 접수만 의미합니다. "
					+ "재호출할 때마다 새 작업을 생성합니다.")
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

	@Operation(operationId = "getAnalysisStatus", summary = "장학금 분석 작업 상태 조회",
			description = "본인 작업의 상태, 진행 건수, 접수 시점의 제외 사유별 건수를 조회합니다.")
	@SecurityRequirement(name = "bearerAuth")
	@ApiResponses({
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "분석 상태 조회",
					useReturnTypeSchema = true),
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404",
					description = "RESOURCE_NOT_FOUND",
					content = @Content(mediaType = "application/problem+json",
							schema = @Schema(ref = "#/components/schemas/ApiProblem")))
	})
	ResponseEntity<ApiResponse<AnalysisStatusResponse>> getAnalysisStatus(
			@Parameter(description = "본인이 접수한 분석 ID", example = "5001") String analysisId,
			@Parameter(hidden = true) Principal principal, @Parameter(hidden = true) HttpServletRequest servletRequest);

	@Operation(operationId = "getRecommendations", summary = "장학금 추천 결과 조회",
			description = "완료된 본인 분석의 공고별 판정 결과를 장학금 ID 내림차순으로 조회합니다. "
					+ "판정 상태를 생략하면 적격·부적격·확인 필요를 모두 반환합니다.")
	@SecurityRequirement(name = "bearerAuth")
	@ApiResponses({
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "추천 결과 조회",
					useReturnTypeSchema = true),
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "FORBIDDEN",
					content = @Content(mediaType = "application/problem+json",
							schema = @Schema(ref = "#/components/schemas/ApiProblem"))),
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "RESOURCE_NOT_FOUND",
					content = @Content(mediaType = "application/problem+json",
							schema = @Schema(ref = "#/components/schemas/ApiProblem"))),
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409",
					description = "ANALYSIS_NOT_COMPLETED: 분석 대기·진행 중 또는 ANALYSIS_FAILED: 분석 실패",
					content = @Content(mediaType = "application/problem+json",
							schema = @Schema(ref = "#/components/schemas/ApiProblem")))
	})
	ResponseEntity<ApiResponse<AnalysisRecommendationsResponse>> getRecommendations(
			@Parameter(description = "본인의 분석 ID", example = "5001") String analysisId,
			@Parameter(description = "0부터 시작하는 페이지 번호", schema = @Schema(defaultValue = "0", minimum = "0"))
			int page,
			@Parameter(description = "페이지당 결과 수", schema = @Schema(defaultValue = "20", minimum = "1", maximum = "100"))
			int size,
			@Parameter(description = "판정 상태 필터. 생략하면 전체 조회") EligibilityStatus eligibilityStatus,
			@Parameter(hidden = true) Principal principal, @Parameter(hidden = true) HttpServletRequest servletRequest);
}
