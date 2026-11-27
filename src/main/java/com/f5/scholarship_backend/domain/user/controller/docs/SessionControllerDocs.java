package com.f5.scholarship_backend.domain.user.controller.docs;

import com.f5.scholarship_backend.domain.user.dto.SessionLoginRequest;
import com.f5.scholarship_backend.domain.user.dto.SessionLoginResponse;
import com.f5.scholarship_backend.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;

@Tag(name = "인증", description = "일반 사용자 및 관리자 로그인")
public interface SessionControllerDocs {
	@Operation(operationId = "login", summary = "일반 사용자 로그인")
	@ApiResponses({
			@io.swagger.v3.oas.annotations.responses.ApiResponse(
					responseCode = "200", description = "로그인 성공", useReturnTypeSchema = true),
			@io.swagger.v3.oas.annotations.responses.ApiResponse(
					responseCode = "401", description = "이메일 또는 비밀번호 불일치", content = @Content)
	})
	ResponseEntity<ApiResponse<SessionLoginResponse>> login(
			SessionLoginRequest request, @Parameter(hidden = true) HttpServletRequest servletRequest);

	@Operation(operationId = "loginAdmin", summary = "관리자 로그인")
	@ApiResponses({
			@io.swagger.v3.oas.annotations.responses.ApiResponse(
					responseCode = "200", description = "로그인 성공", useReturnTypeSchema = true),
			@io.swagger.v3.oas.annotations.responses.ApiResponse(
					responseCode = "401", description = "이메일 또는 비밀번호 불일치", content = @Content),
			@io.swagger.v3.oas.annotations.responses.ApiResponse(
					responseCode = "403", description = "관리자 권한 없음", content = @Content)
	})
	ResponseEntity<ApiResponse<SessionLoginResponse>> loginAdmin(
			SessionLoginRequest request, @Parameter(hidden = true) HttpServletRequest servletRequest);
}
