package com.f5.scholarship_backend.global.security;

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
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SecurityInfrastructureTest {
	private static final String PROTECTED_ADMIN_URL = "/api/v1/admin/scholarships";

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
	void rejectsExpiredToken() throws InterruptedException {
		var shortLivedProvider = new JwtTokenProvider(
				new JwtProperties("MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=", 1));
		String token = shortLivedProvider.createAccessToken("101", UserRole.USER);
		Thread.sleep(1100);

		assertThatThrownBy(() -> shortLivedProvider.parseToken(token))
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
	void publicSessionEndpointIsNotRejectedForTamperedBearerToken() throws Exception {
		mockMvc.perform(post("/api/v1/sessions")
						.header("Authorization", "Bearer malformed-token"))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
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
