package com.f5.scholarship_backend.domain.user;

import java.time.Instant;
import java.util.List;

import com.f5.scholarship_backend.domain.user.dto.SessionLoginRequest;
import com.f5.scholarship_backend.domain.user.entity.User;
import com.f5.scholarship_backend.domain.user.enums.UserRole;
import com.f5.scholarship_backend.domain.user.repository.UserRepository;
import com.f5.scholarship_backend.global.security.JwtTokenProvider;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class SessionApiIntegrationTest {
	private static final String USER_URL = "/api/v1/sessions";
	private static final String ADMIN_URL = "/api/v1/admin/sessions";
	private static final String PASSWORD = "Secure-Pass1!";

	@Autowired
	private MockMvc mockMvc;
	@Autowired
	private UserRepository userRepository;
	@Autowired
	private PasswordEncoder passwordEncoder;
	@Autowired
	private JwtTokenProvider tokenProvider;

	@Test
	void logsInUserAndIssuesTokenAcceptedByProtectedUserApi() throws Exception {
		User user = createUser("student@gachon.ac.kr", UserRole.USER, "가천새내기");
		var result = mockMvc.perform(post(USER_URL)
						.contentType(MediaType.APPLICATION_JSON)
						.content(body("  STUDENT@GACHON.AC.KR  ", PASSWORD)))
				.andExpect(status().isOk())
				.andExpect(header().string("Cache-Control", "no-store"))
				.andExpect(jsonPath("$.data.tokenType").value("Bearer"))
				.andExpect(jsonPath("$.data.expiresIn").value(tokenProvider.getAccessTokenExpirationSeconds()))
				.andExpect(jsonPath("$.data.userId").value(user.getId().toString()))
				.andExpect(jsonPath("$.data.nickname").value("가천새내기"))
				.andExpect(jsonPath("$.data.role").value("USER"))
				.andReturn();

		String token = JsonPath.read(result.getResponse().getContentAsString(), "$.data.accessToken");
		assertThat(tokenProvider.parseToken(token).role()).isEqualTo(UserRole.USER);
		mockMvc.perform(get("/api/v1/users/me/student-profile").header("Authorization", "Bearer " + token))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
	}

	@Test
	void logsInAdminAndIssuesAdminTokenAcceptedByAdminApi() throws Exception {
		User admin = createUser("admin@gachon.ac.kr", UserRole.ADMIN, "관리자");
		var result = mockMvc.perform(post(ADMIN_URL)
						.contentType(MediaType.APPLICATION_JSON)
						.content(body(admin.getEmail(), PASSWORD)))
				.andExpect(status().isOk())
				.andExpect(header().string("Cache-Control", "no-store"))
				.andExpect(jsonPath("$.data.role").value("ADMIN"))
				.andExpect(jsonPath("$.data.nickname").value("관리자"))
				.andExpect(jsonPath("$.data.tokenType").value("Bearer"))
				.andExpect(jsonPath("$.data.expiresIn").value(tokenProvider.getAccessTokenExpirationSeconds()))
				.andReturn();

		String token = JsonPath.read(result.getResponse().getContentAsString(), "$.data.accessToken");
		assertThat(tokenProvider.parseToken(token).role()).isEqualTo(UserRole.ADMIN);
		mockMvc.perform(get("/api/v1/admin/scholarships").header("Authorization", "Bearer " + token))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
	}

	@Test
	void returnsForbiddenWhenUserCredentialsAreValidForAdminLogin() throws Exception {
		createUser("student@gachon.ac.kr", UserRole.USER, "가천새내기");

		mockMvc.perform(post(ADMIN_URL).contentType(MediaType.APPLICATION_JSON)
						.content(body("student@gachon.ac.kr", PASSWORD)))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("FORBIDDEN"));
	}

	@Test
	void usesSameAuthenticationFailureForUnknownEmailWrongPasswordAndOversizedPassword() throws Exception {
		createUser("student@gachon.ac.kr", UserRole.USER, "가천새내기");
		String oversizedPassword = "A".repeat(73);

		String userUnknownEmail = assertAuthenticationFailure(USER_URL, "missing@gachon.ac.kr", PASSWORD);
		String userWrongPassword = assertAuthenticationFailure(USER_URL, "student@gachon.ac.kr", "Wrong-Pass1!");
		String userOversized = assertAuthenticationFailure(USER_URL, "student@gachon.ac.kr", oversizedPassword);
		String adminUnknownEmail = assertAuthenticationFailure(ADMIN_URL, "missing@gachon.ac.kr", PASSWORD);
		String adminWrongPassword = assertAuthenticationFailure(ADMIN_URL, "student@gachon.ac.kr", "Wrong-Pass1!");
		String adminOversized = assertAuthenticationFailure(ADMIN_URL, "student@gachon.ac.kr", oversizedPassword);

		assertThat(userUnknownEmail).isEqualTo(userWrongPassword).isEqualTo(userOversized)
				.isEqualTo(adminUnknownEmail).isEqualTo(adminWrongPassword).isEqualTo(adminOversized);
		assertThat(oversizedPassword.getBytes(java.nio.charset.StandardCharsets.UTF_8)).hasSize(73);
	}

	@Test
	void rejectsUnknownFieldsMissingFieldsAndQueryParametersWithoutExposingPassword() throws Exception {
		String secret = "Distinct-Secret-123!";
		String validBody = body("student@gachon.ac.kr", secret);
		for (String endpoint : List.of(USER_URL, ADMIN_URL)) {
			for (String invalidBody : List.of(
					validBody.replace("\"password\":\"" + secret + "\"",
							"\"password\":\"" + secret + "\",\"unexpected\":\"extra\""),
					"{\"email\":42,\"password\":\"" + secret + "\"}",
					"{\"email\":\"student@gachon.ac.kr\",\"password\":42}",
					"{\"email\":\"student@gachon.ac.kr\"}",
					"{\"password\":\"" + secret + "\"}")) {
				var response = mockMvc.perform(post(endpoint).contentType(MediaType.APPLICATION_JSON)
								.content(invalidBody))
						.andExpect(status().isBadRequest())
						.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
						.andReturn();
				assertThat(response.getResponse().getContentAsString()).doesNotContain(secret);
			}

			var queryResponse = mockMvc.perform(post(endpoint).param("unexpected", "value")
							.contentType(MediaType.APPLICATION_JSON).content(validBody))
					.andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
					.andReturn();
			assertThat(queryResponse.getResponse().getContentAsString()).doesNotContain(secret);
		}

		SessionLoginRequest request = new SessionLoginRequest();
		request.setEmail("student@gachon.ac.kr");
		request.setPassword(secret);
		assertThat(request.toString()).doesNotContain(secret);
	}

	private User createUser(String email, UserRole role, String nickname) {
		return userRepository.saveAndFlush(User.create(email, passwordEncoder.encode(PASSWORD), role,
				nickname, Instant.now(), false));
	}

	private String assertAuthenticationFailure(String endpoint, String email, String password) throws Exception {
		MvcResult result = mockMvc.perform(post(endpoint).contentType(MediaType.APPLICATION_JSON)
						.content(body(email, password)))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("AUTHENTICATION_FAILED"))
				.andReturn();
		String response = result.getResponse().getContentAsString();
		assertThat(response).doesNotContain(password);
		return JsonPath.read(response, "$.detail");
	}

	private String body(String email, String password) {
		return "{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}";
	}
}
