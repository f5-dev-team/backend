package com.f5.scholarship_backend.domain.student;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import com.f5.scholarship_backend.domain.user.entity.User;
import com.f5.scholarship_backend.domain.user.enums.UserRole;
import com.f5.scholarship_backend.domain.user.repository.UserRepository;
import com.f5.scholarship_backend.global.security.JwtTokenProvider;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.aMapWithSize;
import static org.hamcrest.Matchers.endsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "student-profile.department-max-grades.CS=4")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class StudentProfileApiIntegrationTest {
	private static final String PROFILE_URL = "/api/v1/users/me/student-profile";
	private static final String USERS_URL = "/api/v1/users";
	private static final String PASSWORD = "Secure-Pass1!";

	@Autowired
	private MockMvc mockMvc;
	@Autowired
	private JdbcTemplate jdbc;
	@Autowired
	private UserRepository users;
	@Autowired
	private PasswordEncoder passwordEncoder;
	@Autowired
	private JwtTokenProvider tokenProvider;

	private final List<Long> createdUserIds = new ArrayList<>();

	@AfterEach
	void removeTestAccounts() {
		for (Long userId : createdUserIds) {
			jdbc.update("DELETE FROM student_profiles WHERE user_id = ?", userId);
		}
		users.deleteAllById(createdUserIds);
		createdUserIds.clear();
	}

	@Test
	void registersAndPersistsIsolatedProfilesWithExpectedVersionsAndResponses() throws Exception {
		AuthenticatedUser first = registerAndLogin("first");
		AuthenticatedUser second = registerAndLogin("second");

		mockMvc.perform(get(PROFILE_URL).header("Authorization", first.authorization()))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));

		mockMvc.perform(put(PROFILE_URL).header("Authorization", first.authorization())
						.contentType(MediaType.APPLICATION_JSON).content(profile(750)))
				.andExpect(status().isCreated())
				.andExpect(header().string("Cache-Control", "no-store"))
				.andExpect(jsonPath("$.data.version").value(1));
		assertThat(jdbc.queryForObject("SELECT version FROM student_profiles WHERE user_id = ?",
				Long.class, Long.valueOf(first.userId()))).isEqualTo(1L);
		assertThat(jdbc.queryForObject("SELECT volunteer_minutes FROM student_profiles WHERE user_id = ?",
				Long.class, Long.valueOf(first.userId()))).isEqualTo(750L);

		mockMvc.perform(get(PROFILE_URL).header("Authorization", first.authorization()))
				.andExpect(status().isOk())
				.andExpect(header().string("Cache-Control", "no-store"))
				.andExpect(jsonPath("$.data", aMapWithSize(13)))
				.andExpect(jsonPath("$.data.departmentCode").value("CS"))
				.andExpect(jsonPath("$.data.previousSemesterGpa").value(org.hamcrest.Matchers.nullValue()))
				.andExpect(jsonPath("$.data.specialEligibility").value(org.hamcrest.Matchers.nullValue()))
				.andExpect(jsonPath("$.data.gender").value(org.hamcrest.Matchers.nullValue()))
				.andExpect(jsonPath("$.data.updatedAt", endsWith("+09:00")));

		mockMvc.perform(get(PROFILE_URL).header("Authorization", second.authorization()))
				.andExpect(status().isNotFound());
		mockMvc.perform(put(PROFILE_URL).header("Authorization", second.authorization())
						.contentType(MediaType.APPLICATION_JSON).content(profile(333)))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.data.version").value(1));
		mockMvc.perform(get(PROFILE_URL).header("Authorization", second.authorization()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.volunteerMinutes").value(333));
		mockMvc.perform(get(PROFILE_URL).header("Authorization", first.authorization()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.volunteerMinutes").value(750));

		mockMvc.perform(put(PROFILE_URL).header("Authorization", first.authorization())
						.contentType(MediaType.APPLICATION_JSON).content(profile(0)))
				.andExpect(status().isOk())
				.andExpect(header().string("Cache-Control", "no-store"))
				.andExpect(jsonPath("$.data.version").value(2));
		assertThat(jdbc.queryForObject("SELECT version FROM student_profiles WHERE user_id = ?",
				Long.class, Long.valueOf(first.userId()))).isEqualTo(2L);
		assertThat(jdbc.queryForObject("SELECT volunteer_minutes FROM student_profiles WHERE user_id = ?",
				Long.class, Long.valueOf(first.userId()))).isZero();
		mockMvc.perform(get(PROFILE_URL).header("Authorization", first.authorization()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.version").value(2))
				.andExpect(jsonPath("$.data.volunteerMinutes").value(0));
	}

	@Test
	void enforcesAuthenticationRoleAndQueryContract() throws Exception {
		AuthenticatedUser user = registerAndLogin("contract");
		User admin = users.saveAndFlush(User.create("profile.admin@gachon.ac.kr",
				passwordEncoder.encode(PASSWORD), UserRole.ADMIN, "관리자", Instant.now(), false));
		createdUserIds.add(admin.getId());
		String adminToken = login("/api/v1/admin/sessions", admin.getEmail());

		mockMvc.perform(get(PROFILE_URL).param("unexpected", "value")
						.header("Authorization", user.authorization()))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
		mockMvc.perform(get(PROFILE_URL))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("TOKEN_INVALID"));
		mockMvc.perform(get(PROFILE_URL).header("Authorization", "Bearer " + adminToken))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("FORBIDDEN"));

		mockMvc.perform(get(PROFILE_URL).header("Authorization",
						"Bearer " + tokenProvider.createAccessToken("not-a-number", UserRole.USER)))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("TOKEN_INVALID"));
		mockMvc.perform(get(PROFILE_URL).header("Authorization",
						"Bearer " + tokenProvider.createAccessToken("9223372036854775807", UserRole.USER)))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("TOKEN_INVALID"));
	}

	private AuthenticatedUser registerAndLogin(String suffix) throws Exception {
		String email = "profile." + suffix + "@gachon.ac.kr";
		mockMvc.perform(post(USERS_URL).contentType(MediaType.APPLICATION_JSON).content("""
						{"email":"%s","password":"%s","nickname":"학생%s",
						"serviceTermsAgreed":true,"privacyPolicyAgreed":true}
						""".formatted(email, PASSWORD, suffix)))
				.andExpect(status().isCreated());
		String token = login("/api/v1/sessions", email);
		String userId = users.findByEmail(email).orElseThrow().getId().toString();
		createdUserIds.add(Long.valueOf(userId));
		return new AuthenticatedUser(userId, token);
	}

	private String login(String path, String email) throws Exception {
		var result = mockMvc.perform(post(path).contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"email":"%s","password":"%s"}
								""".formatted(email, PASSWORD)))
				.andExpect(status().isOk())
				.andReturn();
		return JsonPath.read(result.getResponse().getContentAsString(), "$.data.accessToken");
	}

	private String profile(long volunteerMinutes) {
		return """
				{"departmentCode":"CS","grade":3,"enrollmentStatus":"ENROLLED",
				"cumulativeGpa":3.30,"previousSemesterGpa":null,
				"incomeAssessment":{"status":"CALCULATED","year":2026,"semester":2,
				"scale":"TEN_BRACKETS","bracket":"4"},
				"specialEligibility":null,"volunteerMinutes":%d,"birthDate":"2004-06-01",
				"residence":{"status":"REGISTERED","sidoCode":"41","sigunguCode":"41130"},
				"gender":null}
				""".formatted(volunteerMinutes);
	}

	private record AuthenticatedUser(String userId, String token) {
		private String authorization() {
			return "Bearer " + token;
		}
	}
}
