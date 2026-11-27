package com.f5.scholarship_backend.domain.user.controller;

import com.f5.scholarship_backend.domain.user.controller.docs.UserControllerDocs;
import com.f5.scholarship_backend.domain.user.dto.EmailAvailabilityResponse;
import com.f5.scholarship_backend.domain.user.dto.UserCreateRequest;
import com.f5.scholarship_backend.domain.user.dto.UserCreateResponse;
import com.f5.scholarship_backend.domain.user.service.GachonEmailPolicy;
import com.f5.scholarship_backend.domain.user.service.UserService;
import com.f5.scholarship_backend.global.config.RequestIdFilter;
import com.f5.scholarship_backend.global.exception.BusinessException;
import com.f5.scholarship_backend.global.exception.ErrorCode;
import com.f5.scholarship_backend.global.response.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users")
public class UserController implements UserControllerDocs {
	private final UserService userService;

	public UserController(UserService userService) {
		this.userService = userService;
	}

	@Override
	@GetMapping("/email-availability")
	public ResponseEntity<ApiResponse<EmailAvailabilityResponse>> checkEmailAvailability(
			HttpServletRequest servletRequest) {
		var parameters = servletRequest.getParameterMap();
		String[] emails = parameters.get("email");
		if (parameters.size() != 1 || emails == null || emails.length != 1
				|| emails[0] == null || emails[0].isBlank()) {
			throw new BusinessException(ErrorCode.VALIDATION_ERROR);
		}
		String email = GachonEmailPolicy.normalizeAndValidate(emails[0]);
		var result = new EmailAvailabilityResponse(userService.isEmailAvailable(email));
		return ResponseEntity.ok().cacheControl(CacheControl.noStore())
				.body(ApiResponse.of(result, RequestIdFilter.getRequestId(servletRequest)));
	}

	@Override
	@PostMapping
	public ResponseEntity<ApiResponse<UserCreateResponse>> createUser(
			@Valid @RequestBody UserCreateRequest request, HttpServletRequest servletRequest) {
		if (!servletRequest.getParameterMap().isEmpty()) {
			throw new BusinessException(ErrorCode.VALIDATION_ERROR);
		}
		GachonEmailPolicy.normalizeAndValidate(request.email());
		var result = userService.create(request);
		return ResponseEntity.status(HttpStatus.CREATED).cacheControl(CacheControl.noStore())
				.body(ApiResponse.of(result, RequestIdFilter.getRequestId(servletRequest)));
	}
}
