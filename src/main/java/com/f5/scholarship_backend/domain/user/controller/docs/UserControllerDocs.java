package com.f5.scholarship_backend.domain.user.controller.docs;

import com.f5.scholarship_backend.domain.user.dto.EmailAvailabilityResponse;
import com.f5.scholarship_backend.domain.user.dto.UserCreateRequest;
import com.f5.scholarship_backend.domain.user.dto.UserCreateResponse;
import com.f5.scholarship_backend.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.Parameters;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;

@Tag(name = "사용자", description = "회원가입 및 이메일 확인")
public interface UserControllerDocs {
	@Operation(operationId = "checkEmailAvailability", summary = "이메일 중복 확인",
			description = "가천대학교 이메일을 정규화하여 가입 가능 여부를 조회합니다.")
	@Parameters({
			@Parameter(name = "email", description = "가천대학교 이메일", required = true,
					in = ParameterIn.QUERY, schema = @Schema(type = "string", example = "student@gachon.ac.kr"))
	})
	@ApiResponses({
			@io.swagger.v3.oas.annotations.responses.ApiResponse(
					responseCode = "200", description = "이메일 확인 완료", useReturnTypeSchema = true)
	})
	ResponseEntity<ApiResponse<EmailAvailabilityResponse>> checkEmailAvailability(
			@Parameter(hidden = true) HttpServletRequest servletRequest);

	@Operation(operationId = "createUser", summary = "회원가입",
			description = "필수 약관에 동의한 USER 계정을 생성합니다.")
	@ApiResponses({
			@io.swagger.v3.oas.annotations.responses.ApiResponse(
					responseCode = "201", description = "회원가입 완료", useReturnTypeSchema = true),
			@io.swagger.v3.oas.annotations.responses.ApiResponse(
					responseCode = "409", description = "이미 사용 중인 이메일")
	})
	ResponseEntity<ApiResponse<UserCreateResponse>> createUser(
			UserCreateRequest request, @Parameter(hidden = true) HttpServletRequest servletRequest);
}
