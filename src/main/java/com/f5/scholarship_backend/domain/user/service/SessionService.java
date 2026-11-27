package com.f5.scholarship_backend.domain.user.service;

import java.nio.charset.StandardCharsets;
import java.util.Optional;

import com.f5.scholarship_backend.domain.user.dto.SessionLoginRequest;
import com.f5.scholarship_backend.domain.user.dto.SessionLoginResponse;
import com.f5.scholarship_backend.domain.user.entity.User;
import com.f5.scholarship_backend.domain.user.enums.UserRole;
import com.f5.scholarship_backend.domain.user.repository.UserRepository;
import com.f5.scholarship_backend.global.exception.BusinessException;
import com.f5.scholarship_backend.global.exception.ErrorCode;
import com.f5.scholarship_backend.global.security.JwtTokenProvider;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SessionService {
	private static final String DUMMY_PASSWORD = "not-a-real-user-password";

	private final UserRepository userRepository;
	private final PasswordEncoder passwordEncoder;
	private final JwtTokenProvider tokenProvider;
	private final String dummyPasswordHash;

	public SessionService(UserRepository userRepository, PasswordEncoder passwordEncoder,
			JwtTokenProvider tokenProvider) {
		this.userRepository = userRepository;
		this.passwordEncoder = passwordEncoder;
		this.tokenProvider = tokenProvider;
		this.dummyPasswordHash = passwordEncoder.encode(DUMMY_PASSWORD);
	}

	@Transactional(readOnly = true)
	public SessionLoginResponse login(SessionLoginRequest request, boolean adminLogin) {
		String normalizedEmail = User.normalizeEmail(request.email());
		Optional<User> user = userRepository.findByNormalizedEmail(normalizedEmail);
		boolean passwordTooLong = request.password().getBytes(StandardCharsets.UTF_8).length > 72;
		String passwordToCheck = passwordTooLong ? DUMMY_PASSWORD : request.password();
		String passwordHash = user.map(User::getPasswordHash).orElse(dummyPasswordHash);
		boolean passwordMatches = passwordEncoder.matches(passwordToCheck, passwordHash);

		if (passwordTooLong || user.isEmpty() || !passwordMatches) {
			throw new BusinessException(ErrorCode.AUTHENTICATION_FAILED);
		}

		User authenticatedUser = user.orElseThrow();
		if (adminLogin && authenticatedUser.getRole() != UserRole.ADMIN) {
			throw new BusinessException(ErrorCode.FORBIDDEN);
		}

		String userId = authenticatedUser.getId().toString();
		String accessToken = tokenProvider.createAccessToken(userId, authenticatedUser.getRole());
		return new SessionLoginResponse(accessToken, "Bearer",
				tokenProvider.getAccessTokenExpirationSeconds(), userId,
				authenticatedUser.getNickname(), authenticatedUser.getRole());
	}
}
