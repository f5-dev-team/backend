package com.f5.scholarship_backend.global.exception;

import org.springframework.http.HttpStatus;

public enum ErrorCode {
	VALIDATION_ERROR(HttpStatus.BAD_REQUEST, "입력값을 확인해 주세요."),
	AUTHENTICATION_FAILED(HttpStatus.UNAUTHORIZED, "로그인 정보를 확인해 주세요."),
	TOKEN_INVALID(HttpStatus.UNAUTHORIZED, "인증 정보가 없거나 유효하지 않습니다."),
	FORBIDDEN(HttpStatus.FORBIDDEN, "접근 권한이 없습니다."),
	RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND, "요청한 대상을 찾을 수 없습니다."),
	EMAIL_ALREADY_EXISTS(HttpStatus.CONFLICT, "이미 사용 중인 이메일입니다."),
	PROFILE_REQUIRED(HttpStatus.CONFLICT, "학생 정보를 먼저 저장해 주세요."),
	VERSION_CONFLICT(HttpStatus.CONFLICT, "정보가 변경되었습니다. 최신 정보를 확인해 주세요."),
	ANALYSIS_NOT_COMPLETED(HttpStatus.CONFLICT, "분석이 아직 완료되지 않았습니다."),
	ANALYSIS_FAILED(HttpStatus.CONFLICT, "분석에 실패했습니다. 새 분석을 요청해 주세요."),
	RATE_LIMIT_EXCEEDED(HttpStatus.TOO_MANY_REQUESTS, "요청이 너무 많습니다. 잠시 후 다시 시도해 주세요."),
	INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "서버에서 오류가 발생했습니다."),
	SERVICE_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "일시적으로 서비스를 이용할 수 없습니다.");

	private final HttpStatus status;
	private final String message;

	ErrorCode(HttpStatus status, String message) {
		this.status = status;
		this.message = message;
	}

	public HttpStatus getStatus() {
		return status;
	}

	public String getMessage() {
		return message;
	}
}