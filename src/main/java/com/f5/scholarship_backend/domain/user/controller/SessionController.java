package com.f5.scholarship_backend.domain.user.controller;

import com.f5.scholarship_backend.domain.user.controller.docs.SessionControllerDocs;
import com.f5.scholarship_backend.domain.user.dto.SessionLoginRequest;
import com.f5.scholarship_backend.domain.user.dto.SessionLoginResponse;
import com.f5.scholarship_backend.domain.user.service.SessionService;
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

@RestController
@RequestMapping("/api/v1")
public class SessionController implements SessionControllerDocs {
	private final SessionService sessionService;

	public SessionController(SessionService sessionService) {
		this.sessionService = sessionService;
	}

	@Override
	@PostMapping("/sessions")
	public ResponseEntity<ApiResponse<SessionLoginResponse>> login(
			@Valid @RequestBody SessionLoginRequest request, HttpServletRequest servletRequest) {
		validateNoQueryParameters(servletRequest);
		SessionLoginResponse result = sessionService.login(request, false);
		return ResponseEntity.ok().cacheControl(CacheControl.noStore())
				.body(ApiResponse.of(result, RequestIdFilter.getRequestId(servletRequest)));
	}

	@Override
	@PostMapping("/admin/sessions")
	public ResponseEntity<ApiResponse<SessionLoginResponse>> loginAdmin(
			@Valid @RequestBody SessionLoginRequest request, HttpServletRequest servletRequest) {
		validateNoQueryParameters(servletRequest);
		SessionLoginResponse result = sessionService.login(request, true);
		return ResponseEntity.ok().cacheControl(CacheControl.noStore())
				.body(ApiResponse.of(result, RequestIdFilter.getRequestId(servletRequest)));
	}

	private void validateNoQueryParameters(HttpServletRequest request) {
		if (!request.getParameterMap().isEmpty()) {
			throw new BusinessException(ErrorCode.VALIDATION_ERROR);
		}
	}
}
