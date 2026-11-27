package com.f5.scholarship_backend.global.security;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import com.f5.scholarship_backend.domain.user.enums.UserRole;
import io.jsonwebtoken.ExpiredJwtException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SecurityInfrastructureTest {
	private static final String PROTECTED_ADMIN_URL = "/api/v1/admin/scholarships";
	private static final String STUDENT_PROFILE_URL = "/api/v1/users/me/student-profile";
	private static final String ALLOWED_ORIGIN = "http://localhost:3000";

	@Autowired
	private MockMvc mockMvc;
	@Autowired
	private JwtTokenProvider tokenProvider;

	@Test
	void issuesAndParsesUserIdRoleAndConfiguredExpiration() {
		String token = tokenProvider.createAccessToken("101", UserRole.USER);
		var claims = tokenProvider.parseToken(token);

		assertThat(claims.userId()).isEqualTo("101");
		assertThat(claims.role()).isEqualTo(UserRole.USER);
		long expirationSeconds = tokenProvider.getAccessTokenExpirationSeconds();
		assertThat(expirationSeconds).isPositive();
		assertThat(tokenProvider.getRemainingExpirationSeconds(token)).isBetween(0L, expirationSeconds);
	}

	@Test
	void parsesTokenAndCalculatesRemainingExpirationWithFixedClock() {
		Instant issuedAt = Instant.parse("2026-01-01T00:00:00Z");
		var fixedProvider = new JwtTokenProvider(
				new JwtProperties("MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=", 120),
				Clock.fixed(issuedAt, ZoneOffset.UTC));
		String token = fixedProvider.createAccessToken("101", UserRole.USER);

		assertThat(fixedProvider.parseToken(token).userId()).isEqualTo("101");
		assertThat(fixedProvider.getRemainingExpirationSeconds(token)).isEqualTo(120);
	}

	@Test
	void rejectsExpiredTokenWithFixedClock() {
		Instant issuedAt = Instant.parse("2026-01-01T00:00:00Z");
		var properties = new JwtProperties("MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=", 1);
		var issuer = new JwtTokenProvider(properties, Clock.fixed(issuedAt, ZoneOffset.UTC));
		String token = issuer.createAccessToken("101", UserRole.USER);
		var verifierAfterExpiration = new JwtTokenProvider(properties,
				Clock.fixed(issuedAt.plusSeconds(2), ZoneOffset.UTC));

		assertThatThrownBy(() -> verifierAfterExpiration.parseToken(token))
				.isInstanceOf(ExpiredJwtException.class);
	}

	@Test
	void rejectsMissingOrTamperedBearerTokenWithProblemDetailsAndRequestId() throws Exception {
		assertUnauthorized(mockMvc.perform(get(PROTECTED_ADMIN_URL)));

		String token = tokenProvider.createAccessToken("101", UserRole.USER) + "tampered";
		assertUnauthorized(mockMvc.perform(get(PROTECTED_ADMIN_URL).header("Authorization", "Bearer " + token)));
	}

	@Test
	void acceptsBearerTokenWithSingleOrMultipleAsciiSpaces() throws Exception {
		String token = tokenProvider.createAccessToken("201", UserRole.ADMIN);

		mockMvc.perform(get(PROTECTED_ADMIN_URL).header("Authorization", "Bearer " + token))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
		mockMvc.perform(get(PROTECTED_ADMIN_URL).header("Authorization", "Bearer  " + token))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
	}

	@Test
	void rejectsWhitespaceInsideBearerToken() throws Exception {
		String token = tokenProvider.createAccessToken("201", UserRole.ADMIN);
		int midpoint = token.length() / 2;
		String tokenWithWhitespace = token.substring(0, midpoint) + " " + token.substring(midpoint);

		assertUnauthorized(mockMvc.perform(get(PROTECTED_ADMIN_URL)
				.header("Authorization", "Bearer " + tokenWithWhitespace)));
	}

	@Test
	void publicSessionEndpointPassesTamperedBearerTokenToRequestValidation() throws Exception {
		String token = tokenProvider.createAccessToken("101", UserRole.USER) + "tampered";
		mockMvc.perform(post("/api/v1/sessions").header("Authorization", "Bearer " + token))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
	}

	@Test
	void authenticatedUserWithoutAdminRoleGetsForbiddenProblemDetails() throws Exception {
		var result = mockMvc.perform(get(PROTECTED_ADMIN_URL)
						.header("Authorization", "Bearer " + tokenProvider.createAccessToken("101", UserRole.USER)))
				.andExpect(status().isForbidden())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
				.andExpect(header().string("Cache-Control", "no-store"))
				.andExpect(jsonPath("$.type").value("about:blank"))
				.andExpect(jsonPath("$.title").value("Forbidden"))
				.andExpect(jsonPath("$.status").value(403))
				.andExpect(jsonPath("$.code").value("FORBIDDEN"))
				.andExpect(jsonPath("$.errors").isEmpty())
				.andExpect(jsonPath("$.requestId").value(startsWith("req-")))
				.andReturn();

		assertThat(result.getResponse().getContentAsString())
				.contains(result.getResponse().getHeader("X-Request-Id"));
	}

	@Test
	void adminRolePassesAdminAuthorization() throws Exception {
		mockMvc.perform(get(PROTECTED_ADMIN_URL)
						.header("Authorization", "Bearer " + tokenProvider.createAccessToken("201", UserRole.ADMIN)))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
	}

	@Test
	void allowsPreflightForProtectedApiWithoutJwtAuthentication() throws Exception {
		mockMvc.perform(options(STUDENT_PROFILE_URL)
						.header("Origin", ALLOWED_ORIGIN)
						.header("Access-Control-Request-Method", "GET")
						.header("Access-Control-Request-Headers", "Authorization,Content-Type"))
				.andExpect(status().isOk())
				.andExpect(header().string("Access-Control-Allow-Origin", ALLOWED_ORIGIN))
				.andExpect(header().string("Access-Control-Allow-Methods", containsString("GET")));
	}

	@Test
	void rejectsPreflightFromUnconfiguredOrigin() throws Exception {
		mockMvc.perform(options(STUDENT_PROFILE_URL)
						.header("Origin", "https://untrusted.example")
						.header("Access-Control-Request-Method", "GET"))
				.andExpect(status().isForbidden())
				.andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
	}

	@Test
	void exposesCorsHeadersOnAuthenticatedApiResponse() throws Exception {
		mockMvc.perform(get(STUDENT_PROFILE_URL)
						.header("Origin", ALLOWED_ORIGIN)
						.header("Authorization", "Bearer " + tokenProvider.createAccessToken("101", UserRole.USER)))
				.andExpect(header().string("Access-Control-Allow-Origin", ALLOWED_ORIGIN))
				.andExpect(header().string("Access-Control-Expose-Headers", containsString("X-Request-Id")))
				.andExpect(header().string("Access-Control-Expose-Headers", containsString("Location")));
	}

	private void assertUnauthorized(ResultActions result) throws Exception {
		var response = result
				.andExpect(status().isUnauthorized())
				.andExpect(header().string("WWW-Authenticate", "Bearer"))
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
				.andExpect(header().string("Cache-Control", "no-store"))
				.andExpect(jsonPath("$.type").value("about:blank"))
				.andExpect(jsonPath("$.title").value("Unauthorized"))
				.andExpect(jsonPath("$.status").value(401))
				.andExpect(jsonPath("$.code").value("TOKEN_INVALID"))
				.andExpect(jsonPath("$.errors").isEmpty())
				.andExpect(jsonPath("$.requestId").value(startsWith("req-")))
				.andReturn();
		assertThat(response.getResponse().getContentAsString())
				.contains(response.getResponse().getHeader("X-Request-Id"));
	}
}
