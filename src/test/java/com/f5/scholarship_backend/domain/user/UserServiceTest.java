package com.f5.scholarship_backend.domain.user;

import com.f5.scholarship_backend.domain.user.dto.UserCreateRequest;
import com.f5.scholarship_backend.domain.user.repository.UserRepository;
import com.f5.scholarship_backend.domain.user.service.UserService;
import com.f5.scholarship_backend.global.exception.BusinessException;
import com.f5.scholarship_backend.global.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class UserServiceTest {
	@Test
	void mapsDatabaseEmailUniqueConstraintRaceToConflictWithoutLeakingPassword() {
		UserRepository userRepository = mock(UserRepository.class);
		PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
		when(userRepository.existsByEmail("student@gachon.ac.kr")).thenReturn(false);
		when(passwordEncoder.encode("Secure-Pass1!")).thenReturn("encoded-hash");
		when(userRepository.saveAndFlush(any())).thenThrow(
				new DataIntegrityViolationException("uk_users_email"));

		UserCreateRequest request = new UserCreateRequest();
		request.setEmail("student@gachon.ac.kr");
		request.setPassword("Secure-Pass1!");
		request.setNickname("가천새내기");
		request.setServiceTermsAgreed(true);
		request.setPrivacyPolicyAgreed(true);
		request.setNotificationAgreed(false);

		assertThatThrownBy(() -> new UserService(userRepository, passwordEncoder).create(request))
				.isInstanceOf(BusinessException.class)
				.satisfies(exception -> {
					BusinessException businessException = (BusinessException) exception;
					assertThat(businessException.getErrorCode()).isEqualTo(ErrorCode.EMAIL_ALREADY_EXISTS);
					assertThat(businessException.getMessage()).doesNotContain("Secure-Pass1!", "encoded-hash");
				});
	}

	@Test
	void rethrowsNonEmailIntegrityViolations() {
		UserRepository userRepository = mock(UserRepository.class);
		PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
		when(userRepository.existsByEmail("student@gachon.ac.kr")).thenReturn(false);
		when(passwordEncoder.encode("Secure-Pass1!")).thenReturn("encoded-hash");
		DataIntegrityViolationException violation =
				new DataIntegrityViolationException("uk_users_nickname");
		when(userRepository.saveAndFlush(any())).thenThrow(violation);

		UserCreateRequest request = new UserCreateRequest();
		request.setEmail("student@gachon.ac.kr");
		request.setPassword("Secure-Pass1!");
		request.setNickname("가천새내기");
		request.setServiceTermsAgreed(true);
		request.setPrivacyPolicyAgreed(true);

		assertThatThrownBy(() -> new UserService(userRepository, passwordEncoder).create(request))
				.isSameAs(violation);
	}
}
