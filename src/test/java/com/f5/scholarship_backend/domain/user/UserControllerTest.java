package com.f5.scholarship_backend.domain.user;

import com.f5.scholarship_backend.domain.user.controller.UserController;
import com.f5.scholarship_backend.domain.user.dto.UserCreateRequest;
import com.f5.scholarship_backend.domain.user.dto.UserCreateResponse;
import com.f5.scholarship_backend.domain.user.enums.UserRole;
import com.f5.scholarship_backend.domain.user.service.UserService;
import com.f5.scholarship_backend.global.config.JsonConfig;
import com.f5.scholarship_backend.global.config.RequestIdFilter;
import java.time.OffsetDateTime;
import java.util.stream.Stream;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.MockMvcBuilderCustomizer;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.mockito.ArgumentCaptor;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(UserController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({JsonConfig.class, UserControllerTest.TestMockMvcConfiguration.class})
@ActiveProfiles("test")
class UserControllerTest {
	private static final String USERS_URL = "/api/v1/users";
	private static final Pattern PASSWORD_FIELD = Pattern.compile("\"password\"\\s*:\\s*\"([^\"]*)\"");
	private static final String VALID_BODY = """
			{"email":"student@gachon.ac.kr","password":"Secure-Pass1!",
			"nickname":"가천새내기","serviceTermsAgreed":true,"privacyPolicyAgreed":true,
			"notificationAgreed":true}
			""";

	@TestConfiguration(proxyBeanMethods = false)
	static class TestMockMvcConfiguration {
		@Bean
		MockMvcBuilderCustomizer requestIdFilterForControllerTest() {
			return builder -> builder.addFilters(new RequestIdFilter());
		}
	}

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private UserService userService;

	@Test
	void checksEmailAvailabilityAndWrapsTheResult() throws Exception {
		when(userService.isEmailAvailable("student@gachon.ac.kr")).thenReturn(true);
		mockMvc.perform(get(USERS_URL + "/email-availability").param("email", " Student@Gachon.ac.kr "))
				.andExpect(status().isOk())
				.andExpect(header().string("Cache-Control", "no-store"))
				.andExpect(jsonPath("$.data.available").value(true))
				.andExpect(jsonPath("$.meta.requestId", org.hamcrest.Matchers.startsWith("req-")));
		verify(userService).isEmailAvailable("student@gachon.ac.kr");
	}

	@ParameterizedTest
	@MethodSource("invalidEmails")
	void rejectsMalformedOrUnapprovedEmailDomains(String email) throws Exception {
		mockMvc.perform(get(USERS_URL + "/email-availability").param("email", email))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
		verifyNoInteractions(userService);
	}

	static Stream<Arguments> invalidEmails() {
		return Stream.of(
				Arguments.of("student@gachon.ac.kr.attacker.example"),
				Arguments.of("student@sub.gachon.ac.kr"),
				Arguments.of("not-an-email"),
				Arguments.of("student name@gachon.ac.kr"),
				Arguments.of("a".repeat(241) + "@gachon.ac.kr"));
	}

	@Test
	void requiresExactlyOneEmailQueryParameter() throws Exception {
		mockMvc.perform(get(USERS_URL + "/email-availability"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
		mockMvc.perform(get(USERS_URL + "/email-availability").param("email", ""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
		mockMvc.perform(get(USERS_URL + "/email-availability")
						.param("email", "a@gachon.ac.kr", "b@gachon.ac.kr"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
		mockMvc.perform(get(USERS_URL + "/email-availability")
						.param("email", "a@gachon.ac.kr").param("extra", "value"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
		verifyNoInteractions(userService);
	}

	@Test
	void createsAccountResponseWithoutExposingPassword() throws Exception {
		when(userService.create(any())).thenReturn(response(false));
		var result = mockMvc.perform(post(USERS_URL).contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
				.andExpect(status().isCreated())
				.andExpect(header().string("Cache-Control", "no-store"))
				.andExpect(jsonPath("$.data.userId").value("42"))
				.andExpect(jsonPath("$.data.email").value("student@gachon.ac.kr"))
				.andExpect(jsonPath("$.data.nickname").value("가천새내기"))
				.andExpect(jsonPath("$.data.role").value("USER"))
				.andExpect(jsonPath("$.data.notificationAgreed").value(false))
				.andExpect(jsonPath("$.data.createdAt").value("2026-10-14T09:00:00+09:00"))
				.andExpect(jsonPath("$.data.password").doesNotExist())
				.andExpect(jsonPath("$.data.passwordHash").doesNotExist())
				.andReturn();
		assertThat(result.getResponse().getContentAsString()).doesNotContain("Secure-Pass1!");
		var captor = ArgumentCaptor.forClass(UserCreateRequest.class);
		verify(userService).create(captor.capture());
		assertThat(captor.getValue().notificationAgreed()).isTrue();
	}

	@Test
	void omittedNotificationAgreementDefaultsToFalse() throws Exception {
		when(userService.create(any())).thenReturn(response(false));
		String body = VALID_BODY.replace(",\n\"notificationAgreed\":true", "");
		mockMvc.perform(post(USERS_URL).contentType(MediaType.APPLICATION_JSON).content(body))
				.andExpect(status().isCreated());
		var captor = ArgumentCaptor.forClass(UserCreateRequest.class);
		verify(userService).create(captor.capture());
		assertThat(captor.getValue().notificationAgreed()).isFalse();
	}

	@ParameterizedTest
	@ValueSource(strings = {
			"{\"email\":\"student@gachon.ac.kr\",\"password\":\"Secure-Pass1!\",\"nickname\":\"가천새내기\",\"serviceTermsAgreed\":true,\"privacyPolicyAgreed\":true,\"unknown\":1}",
			"{\"email\":\"student@gachon.ac.kr\",\"password\":\"Secure-Pass1!\",\"nickname\":\"가천새내기\",\"serviceTermsAgreed\":true,\"privacyPolicyAgreed\":true,\"notificationAgreed\":null}",
			"{\"email\":\"student@gachon.ac.kr\",\"password\":\"Secure-Pass1!\",\"nickname\":\"가천새내기\",\"serviceTermsAgreed\":true,\"privacyPolicyAgreed\":true,\"notificationAgreed\":\"true\"}",
			"{\"email\":\"student@gachon.ac.kr\",\"password\":\"Secure-Pass1!\",\"nickname\":\"가천새내기\",\"serviceTermsAgreed\":\"true\",\"privacyPolicyAgreed\":true}",
			"{\"email\":\"student@gachon.ac.kr\",\"password\":\"Secure-Pass1!\",\"nickname\":\"가천새내기\",\"serviceTermsAgreed\":true}",
			"{\"email\":\"student@gachon.ac.kr\",\"password\":\"Secure-Pass1!\",\"nickname\":\"가천새내기\",\"privacyPolicyAgreed\":true}",
			"{\"email\":\"student@gachon.ac.kr\",\"password\":\"Secure-Pass1!\",\"nickname\":\"가천새내기\",\"serviceTermsAgreed\":null,\"privacyPolicyAgreed\":true}",
			"{\"email\":\"student@gachon.ac.kr\",\"password\":\"Secure-Pass1!\",\"nickname\":\"가천새내기\",\"serviceTermsAgreed\":true,\"privacyPolicyAgreed\":null}",
			"{\"email\":\"student@gachon.ac.kr\",\"password\":\"Secure-Pass1!\",\"nickname\":\"가천새내기\",\"serviceTermsAgreed\":false,\"privacyPolicyAgreed\":true}",
			"{\"email\":\"student@gachon.ac.kr\",\"password\":\"Secure-Pass1!\",\"nickname\":\"가천새내기\",\"serviceTermsAgreed\":true,\"privacyPolicyAgreed\":false}",
			"{\"email\":\"student@gachon.ac.kr.attacker.example\",\"password\":\"Secure-Pass1!\",\"nickname\":\"가천새내기\",\"serviceTermsAgreed\":true,\"privacyPolicyAgreed\":true}",
			"{\"email\":\"student@sub.gachon.ac.kr\",\"password\":\"Secure-Pass1!\",\"nickname\":\"가천새내기\",\"serviceTermsAgreed\":true,\"privacyPolicyAgreed\":true}",
			"{\"email\":\"student@gachon.ac.kr\",\"password\":\"weak\",\"nickname\":\"가천새내기\",\"serviceTermsAgreed\":true,\"privacyPolicyAgreed\":true}",
			"{\"email\":\"student@gachon.ac.kr\",\"password\":\"12345678!\",\"nickname\":\"가천새내기\",\"serviceTermsAgreed\":true,\"privacyPolicyAgreed\":true}",
			"{\"email\":\"student@gachon.ac.kr\",\"password\":\"SecurePass1\",\"nickname\":\"가천새내기\",\"serviceTermsAgreed\":true,\"privacyPolicyAgreed\":true}",
			"{\"email\":\"student@gachon.ac.kr\",\"password\":\"Secure-Pass1!\",\"nickname\":\"두 글 자\",\"serviceTermsAgreed\":true,\"privacyPolicyAgreed\":true}",
			"{\"email\":\"student@gachon.ac.kr\",\"password\":\"Secure-Pass1!\",\"nickname\":\"두\\t글\",\"serviceTermsAgreed\":true,\"privacyPolicyAgreed\":true}",
			"{\"email\":\"student@gachon.ac.kr\",\"password\":\"Secure-Pass1!\",\"nickname\":\"가\",\"serviceTermsAgreed\":true,\"privacyPolicyAgreed\":true}",
			"{\"email\":\"student@gachon.ac.kr\",\"password\":\"Secure-Pass1!\",\"nickname\":\"가가가가가가가가가가가가가가가가가가가가가\",\"serviceTermsAgreed\":true,\"privacyPolicyAgreed\":true}"
	})
	void rejectsInvalidSignupBodyWithoutCallingService(String body) throws Exception {
		var result = mockMvc.perform(post(USERS_URL).contentType(MediaType.APPLICATION_JSON).content(body))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
				.andReturn();
		Matcher passwordField = PASSWORD_FIELD.matcher(body);
		assertThat(passwordField.find()).isTrue();
		assertThat(result.getResponse().getContentAsString()).doesNotContain(passwordField.group(1));
		verifyNoInteractions(userService);
	}

	@Test
	void rejectsPasswordsOver72Utf8BytesWithoutCallingServiceOrExposingPassword() throws Exception {
		for (String password : new String[] {"A1!" + "a".repeat(70), "A1!" + "가".repeat(24)}) {
			String body = VALID_BODY.replace("Secure-Pass1!", password);
			var result = mockMvc.perform(post(USERS_URL).contentType(MediaType.APPLICATION_JSON).content(body))
					.andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
					.andExpect(jsonPath("$.errors[0].field").value("password"))
					.andReturn();
			assertThat(result.getResponse().getContentAsString()).doesNotContain(password);
		}
		verifyNoInteractions(userService);
	}

	@Test
	void rejectsUnknownSignupQueryParameter() throws Exception {
		mockMvc.perform(post(USERS_URL).param("unexpected", "value")
						.contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
		verifyNoInteractions(userService);
	}

	private UserCreateResponse response(boolean notificationAgreed) {
		return new UserCreateResponse("42", "student@gachon.ac.kr", "가천새내기", UserRole.USER,
				notificationAgreed, OffsetDateTime.parse("2026-10-14T09:00:00+09:00"));
	}
}
