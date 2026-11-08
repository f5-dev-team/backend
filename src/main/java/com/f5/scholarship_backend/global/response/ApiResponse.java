package com.f5.scholarship_backend.global.response;

import java.util.Objects;

public record ApiResponse<T>(T data, Meta meta) {
	/** 응답 메타 정보의 존재를 검증한다. 응답 데이터는 null을 허용한다. */
	public ApiResponse {
		Objects.requireNonNull(meta, "응답 메타 정보는 필수입니다.");
	}

	/** 응답 데이터와 서버 생성 요청 ID를 공통 성공 응답으로 묶는다. */
	public static <T> ApiResponse<T> of(T data, String requestId) {
		return new ApiResponse<>(data, new Meta(requestId));
	}

	public record Meta(String requestId) {
		/** 응답과 로그를 연결할 요청 ID가 null이 아닌지 검증한다. */
		public Meta {
			Objects.requireNonNull(requestId, "요청 식별자는 필수입니다.");
		}
	}
}