package com.f5.scholarship_backend.domain.user.service;

import java.util.regex.Pattern;
import com.f5.scholarship_backend.domain.user.entity.User;
import com.f5.scholarship_backend.global.exception.BusinessException;
import com.f5.scholarship_backend.global.exception.ErrorCode;

public final class GachonEmailPolicy {
	private static final Pattern VALID_EMAIL = Pattern.compile(
			"(?=.{1,64}@)[A-Za-z0-9!#$%&'*+/=?^_`{|}~-]+(?:\\.[A-Za-z0-9!#$%&'*+/=?^_`{|}~-]+)*@gachon\\.ac\\.kr");

	private GachonEmailPolicy() {
	}

	public static String normalizeAndValidate(String email) {
		if (email == null) {
			throw new BusinessException(ErrorCode.VALIDATION_ERROR);
		}
		String normalized = User.normalizeEmail(email);
		if (normalized.length() > 254 || !VALID_EMAIL.matcher(normalized).matches()) {
			throw new BusinessException(ErrorCode.VALIDATION_ERROR);
		}
		return normalized;
	}
}
