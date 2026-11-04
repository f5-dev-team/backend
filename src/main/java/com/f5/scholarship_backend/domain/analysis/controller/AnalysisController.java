package com.f5.scholarship_backend.domain.analysis.controller;

import java.security.Principal;
import com.f5.scholarship_backend.domain.analysis.controller.docs.AnalysisControllerDocs;
import com.f5.scholarship_backend.domain.analysis.dto.AnalysisCreateRequest;
import com.f5.scholarship_backend.domain.analysis.dto.AnalysisCreateResponse;
import com.f5.scholarship_backend.domain.analysis.service.AnalysisService;
import com.f5.scholarship_backend.global.config.RequestIdFilter;
import com.f5.scholarship_backend.global.exception.BusinessException;
import com.f5.scholarship_backend.global.exception.ErrorCode;
import com.f5.scholarship_backend.global.response.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

@RestController
@RequestMapping("/api/v1/analyses")
public class AnalysisController implements AnalysisControllerDocs {
	private final AnalysisService analysisService;

	public AnalysisController(AnalysisService analysisService) {
		this.analysisService = analysisService;
	}

	@Override
	@PostMapping
	public ResponseEntity<ApiResponse<AnalysisCreateResponse>> createAnalysis(
			@Valid @RequestBody AnalysisCreateRequest request,
			Principal principal, HttpServletRequest servletRequest) {
		if (!servletRequest.getParameterMap().isEmpty()) {
			throw new BusinessException(ErrorCode.VALIDATION_ERROR);
		}
		if (principal == null || principal.getName() == null || principal.getName().isBlank()) {
			throw new BusinessException(ErrorCode.TOKEN_INVALID);
		}
		var result = analysisService.create(principal.getName(), request.profileVersion());
		var location = UriComponentsBuilder.fromPath("/api/v1/analyses")
				.pathSegment(result.analysisId()).build().encode().toUri();
		return ResponseEntity.accepted().location(location).cacheControl(CacheControl.noStore())
				.body(ApiResponse.of(result, RequestIdFilter.getRequestId(servletRequest)));
	}
}
