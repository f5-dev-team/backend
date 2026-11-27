package com.f5.scholarship_backend.domain.user.dto;

import java.time.OffsetDateTime;
import com.f5.scholarship_backend.domain.user.enums.UserRole;
import io.swagger.v3.oas.annotations.media.Schema;

public record UserCreateResponse(
		@Schema(description = "생성된 사용자 ID", example = "101") String userId,
		@Schema(description = "정규화된 이메일", example = "student@gachon.ac.kr") String email,
		@Schema(description = "닉네임", example = "가천새내기") String nickname,
		@Schema(description = "사용자 권한", example = "USER") UserRole role,
		@Schema(description = "장학금 알림 수신 동의 여부") boolean notificationAgreed,
		@Schema(description = "가입 일시") OffsetDateTime createdAt
) {
}
