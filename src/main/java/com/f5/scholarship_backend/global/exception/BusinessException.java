package com.f5.scholarship_backend.global.exception;

import java.util.Objects;

public class BusinessException extends RuntimeException {
	private final ErrorCode errorCode;

	public BusinessException(ErrorCode errorCode) {
		super(Objects.requireNonNull(errorCode, "오류 코드는 필수입니다.").getMessage());
		this.errorCode = errorCode;
	}

	public ErrorCode getErrorCode() {
		return errorCode;
	}
}