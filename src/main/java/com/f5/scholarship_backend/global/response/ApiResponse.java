package com.f5.scholarship_backend.global.response;

import java.util.Objects;

public record ApiResponse<T>(T data, Meta meta) {
	public ApiResponse {
		Objects.requireNonNull(meta, "응답 메타 정보는 필수입니다.");
	}

	public static <T> ApiResponse<T> of(T data, String requestId) {
		return new ApiResponse<>(data, new Meta(requestId));
	}

	public record Meta(String requestId) {
		public Meta {
			Objects.requireNonNull(requestId, "요청 식별자는 필수입니다.");
		}
	}
}