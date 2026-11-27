package com.f5.scholarship_backend.domain.user.dto;

import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "로그인 요청")
public class SessionLoginRequest {
	@Schema(description = "가입 이메일", example = "student@gachon.ac.kr", requiredMode = Schema.RequiredMode.REQUIRED)
	@NotBlank(message = "이메일은 필수입니다.")
	private String email;

	@Schema(description = "비밀번호", requiredMode = Schema.RequiredMode.REQUIRED)
	@NotBlank(message = "비밀번호는 필수입니다.")
	private String password;

	public SessionLoginRequest() {
	}

	public String email() {
		return email;
	}

	public String password() {
		return password;
	}

	public void setEmail(String email) {
		this.email = email;
	}

	@JsonSetter(nulls = Nulls.FAIL)
	public void setPassword(String password) {
		this.password = password;
	}

	@Override
	public String toString() {
		return "SessionLoginRequest[email=" + email + ", password=[REDACTED]]";
	}
}
