package com.f5.scholarship_backend.domain.user.service;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import com.f5.scholarship_backend.domain.user.dto.UserCreateRequest;
import com.f5.scholarship_backend.domain.user.dto.UserCreateResponse;
import com.f5.scholarship_backend.domain.user.entity.User;
import com.f5.scholarship_backend.domain.user.repository.UserRepository;
import com.f5.scholarship_backend.global.exception.BusinessException;
import com.f5.scholarship_backend.global.exception.ErrorCode;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.hibernate.exception.ConstraintViolationException;

@Service
public class UserService {
	private final UserRepository userRepository;
	private final PasswordEncoder passwordEncoder;

	public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
		this.userRepository = userRepository;
		this.passwordEncoder = passwordEncoder;
	}

	@Transactional(readOnly = true)
	public boolean isEmailAvailable(String email) {
		String normalizedEmail = GachonEmailPolicy.normalizeAndValidate(email);
		return !userRepository.existsByEmail(normalizedEmail);
	}

	@Transactional
	public UserCreateResponse create(UserCreateRequest request) {
		if (request.password().getBytes(StandardCharsets.UTF_8).length > 72) {
			throw new BusinessException(ErrorCode.VALIDATION_ERROR);
		}
		String normalizedEmail = GachonEmailPolicy.normalizeAndValidate(request.email());
		if (userRepository.existsByEmail(normalizedEmail)) {
			throw new BusinessException(ErrorCode.EMAIL_ALREADY_EXISTS);
		}

		Instant termsAgreedAt = Instant.now();
		User user = User.create(normalizedEmail, passwordEncoder.encode(request.password()),
				request.nickname(), termsAgreedAt, Boolean.TRUE.equals(request.notificationAgreed()));
		try {
			User saved = userRepository.saveAndFlush(user);
			return new UserCreateResponse(Long.toString(saved.getId()), saved.getEmail(),
					saved.getNickname(), saved.getRole(), saved.isNotificationAgreed(),
					OffsetDateTime.ofInstant(saved.getCreatedAt(), ZoneOffset.ofHours(9)));
		} catch (DataIntegrityViolationException exception) {
			if (isEmailUniqueConstraintViolation(exception)) {
				throw new BusinessException(ErrorCode.EMAIL_ALREADY_EXISTS);
			}
			throw exception;
		}
	}

	private boolean isEmailUniqueConstraintViolation(Throwable exception) {
		for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
			if (cause instanceof ConstraintViolationException constraintViolation
					&& isEmailConstraint(constraintViolation.getConstraintName())) {
				return true;
			}
			if (isEmailConstraint(cause.getMessage())) {
				return true;
			}
		}
		return false;
	}

	private boolean isEmailConstraint(String constraint) {
		if (constraint == null) {
			return false;
		}
		String normalized = constraint.toLowerCase(Locale.ROOT);
		return normalized.contains("uk_users_email") || normalized.contains("users.email");
	}
}
