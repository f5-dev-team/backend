package com.f5.scholarship_backend.domain.user;

import com.f5.scholarship_backend.domain.user.entity.User;
import com.f5.scholarship_backend.domain.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class UserApiIntegrationTest {
	private static final String USERS_URL = "/api/v1/users";
	private static final String RAW_PASSWORD = "Secure-Pass1!";

	@Autowired
	private MockMvc mockMvc;
	@Autowired
	private UserRepository userRepository;
	@Autowired
	private PasswordEncoder passwordEncoder;

	@Test
	void registersNormalizedEmailWithEncodedPasswordAndUtcTermsTime() throws Exception {
		java.time.Instant beforeRequest = java.time.Instant.now();
		mockMvc.perform(post(USERS_URL).contentType(MediaType.APPLICATION_JSON).content("""
						{"email":"  Student@GACHON.AC.KR  ","password":"Secure-Pass1!",
						"nickname":"가천새내기","serviceTermsAgreed":true,"privacyPolicyAgreed":true}
						"""))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.data.userId").isString())
				.andExpect(jsonPath("$.data.email").value("student@gachon.ac.kr"))
				.andExpect(jsonPath("$.data.role").value("USER"))
				.andExpect(jsonPath("$.data.notificationAgreed").value(false))
				.andExpect(jsonPath("$.data.createdAt", endsWith("+09:00")))
				.andExpect(jsonPath("$.data.password").doesNotExist())
				.andExpect(jsonPath("$.data.passwordHash").doesNotExist());

		User saved = userRepository.findByEmail(" STUDENT@gachon.ac.kr ").orElseThrow();
		assertThat(saved.getPasswordHash()).isNotEqualTo(RAW_PASSWORD);
		assertThat(saved.getPasswordHash()).startsWith("$2");
		assertThat(passwordEncoder.matches(RAW_PASSWORD, saved.getPasswordHash())).isTrue();
		assertThat(saved.getTermsAgreedAt()).isBetween(beforeRequest, java.time.Instant.now());
		assertThat(saved.getRole().name()).isEqualTo("USER");
		assertThat(saved.isNotificationAgreed()).isFalse();
	}

	@Test
	void checksNormalizedEmailAvailabilityAgainstStoredAccount() throws Exception {
		userRepository.saveAndFlush(User.create("student@gachon.ac.kr", "encoded", "학생",
				java.time.Instant.now(), false));

		mockMvc.perform(get(USERS_URL + "/email-availability")
						.param("email", "  STUDENT@GACHON.AC.KR "))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.available").value(false));
		mockMvc.perform(get(USERS_URL + "/email-availability")
						.param("email", "new.student@gachon.ac.kr"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.available").value(true));
	}

	@Test
	void mapsPreexistingAndConcurrentUniqueEmailConflictsTo409() throws Exception {
		String body = """
				{"email":"student@gachon.ac.kr","password":"Secure-Pass1!",
				"nickname":"가천새내기","serviceTermsAgreed":true,"privacyPolicyAgreed":true}
				""";
		mockMvc.perform(post(USERS_URL).contentType(MediaType.APPLICATION_JSON).content(body))
				.andExpect(status().isCreated());
		mockMvc.perform(post(USERS_URL).contentType(MediaType.APPLICATION_JSON).content(body))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("EMAIL_ALREADY_EXISTS"));
		mockMvc.perform(post(USERS_URL).contentType(MediaType.APPLICATION_JSON).content(body.replace(
						"student@gachon.ac.kr", "STUDENT@GACHON.AC.KR")))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("EMAIL_ALREADY_EXISTS"));
		assertThat(userRepository.count()).isEqualTo(1);
	}
}
