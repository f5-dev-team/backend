package com.f5.scholarship_backend.domain.user.dto;

import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import com.f5.scholarship_backend.domain.user.validation.MaxUtf8Bytes;

@Schema(description = "회원가입 요청")
public class UserCreateRequest {
	@Schema(description = "가천대학교 이메일", example = "student@gachon.ac.kr")
	@NotBlank(message = "이메일은 필수입니다.")
	private String email;

	@Schema(description = "영문, 숫자, 특수문자를 포함한 8자 이상, UTF-8 기준 72바이트 이하 비밀번호")
	@NotBlank(message = "비밀번호는 필수입니다.")
	@Pattern(regexp = "(?s)(?=.*[A-Za-z])(?=.*[0-9])(?=.*[^A-Za-z0-9\\s]).{8,}",
			message = "비밀번호는 영문, 숫자, 특수문자를 포함해 8자 이상이어야 합니다.")
	@MaxUtf8Bytes(max = 72, message = "비밀번호는 UTF-8 기준 72바이트 이하여야 합니다.")
	private String password;

	@Schema(description = "공백 없는 2~20자 닉네임", example = "가천새내기")
	@NotBlank(message = "닉네임은 필수입니다.")
	@Pattern(regexp = "(?U)\\S{2,20}", message = "닉네임은 공백 없이 2~20자로 입력해 주세요.")
	private String nickname;

	@Schema(description = "서비스 이용 약관 동의", example = "true")
	@NotNull(message = "서비스 이용 약관 동의는 필수입니다.")
	@AssertTrue(message = "서비스 이용 약관에 동의해야 합니다.")
	private Boolean serviceTermsAgreed;

	@Schema(description = "개인정보 처리방침 동의", example = "true")
	@NotNull(message = "개인정보 처리방침 동의는 필수입니다.")
	@AssertTrue(message = "개인정보 처리방침에 동의해야 합니다.")
	private Boolean privacyPolicyAgreed;

	@Schema(description = "장학금 알림 수신 동의 여부", defaultValue = "false")
	private Boolean notificationAgreed = false;

	public UserCreateRequest() {
	}

	public String email() {
		return email;
	}

	public String password() {
		return password;
	}

	public String nickname() {
		return nickname;
	}

	public Boolean serviceTermsAgreed() {
		return serviceTermsAgreed;
	}

	public Boolean privacyPolicyAgreed() {
		return privacyPolicyAgreed;
	}

	public Boolean notificationAgreed() {
		return notificationAgreed;
	}

	public void setEmail(String email) {
		this.email = email;
	}

	public void setPassword(String password) {
		this.password = password;
	}

	public void setNickname(String nickname) {
		this.nickname = nickname;
	}

	public void setServiceTermsAgreed(Boolean serviceTermsAgreed) {
		this.serviceTermsAgreed = serviceTermsAgreed;
	}

	public void setPrivacyPolicyAgreed(Boolean privacyPolicyAgreed) {
		this.privacyPolicyAgreed = privacyPolicyAgreed;
	}

	@JsonSetter(nulls = Nulls.FAIL)
	public void setNotificationAgreed(Boolean notificationAgreed) {
		this.notificationAgreed = notificationAgreed;
	}

	@Override
	public String toString() {
		return "UserCreateRequest[email=" + email + ", password=[REDACTED], nickname=" + nickname
				+ ", serviceTermsAgreed=" + serviceTermsAgreed
				+ ", privacyPolicyAgreed=" + privacyPolicyAgreed
				+ ", notificationAgreed=" + notificationAgreed + "]";
	}
}
