package com.f5.scholarship_backend.global.exception;

import java.util.Objects;

public class BusinessException extends RuntimeException {
	private final ErrorCode errorCode;

	/** 필수 오류 코드와 해당 코드의 안전한 안내 문구로 업무 예외를 생성한다. */
	public BusinessException(ErrorCode errorCode) {
		super(Objects.requireNonNull(errorCode, "오류 코드는 필수입니다.").getMessage());
		this.errorCode = errorCode;
	}

	/** HTTP 상태와 클라이언트 오류 코드를 결정할 업무 오류 정의를 반환한다. */
	public ErrorCode getErrorCode() {
		return errorCode;
	}
}