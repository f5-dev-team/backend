package com.f5.scholarship_backend.domain.user.dto;

import com.f5.scholarship_backend.domain.user.enums.UserRole;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "로그인 응답")
public record SessionLoginResponse(
		@Schema(description = "Bearer 액세스 토큰") String accessToken,
		@Schema(description = "토큰 유형", example = "Bearer") String tokenType,
		@Schema(description = "토큰 유효기간(초)", example = "3600") long expiresIn,
		@Schema(description = "인증된 사용자 ID", example = "101") String userId,
		@Schema(description = "사용자 닉네임", example = "가천새내기") String nickname,
		@Schema(description = "사용자 권한", example = "USER") UserRole role) {
}
