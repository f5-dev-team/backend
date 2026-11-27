package com.f5.scholarship_backend.domain;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import com.f5.scholarship_backend.domain.user.entity.User;
import com.f5.scholarship_backend.domain.user.enums.UserRole;
import com.f5.scholarship_backend.domain.user.repository.UserRepository;
import com.f5.scholarship_backend.global.security.JwtProperties;
import com.f5.scholarship_backend.global.security.JwtTokenProvider;
import com.jayway.jsonpath.JsonPath;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.aMapWithSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "student-profile.department-max-grades.CS=4")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthAndProfileE2ETest {
	private static final String USERS_URL = "/api/v1/users";
	private static final String SESSIONS_URL = "/api/v1/sessions";
	private static final String PROFILE_URL = USERS_URL + "/me/student-profile";
	private static final String ANALYSIS_URL = "/api/v1/analyses";
	private static final String PASSWORD = "Secure-Pass1!";
	private static final String PROFILE = """
			{"departmentCode":"CS","grade":3,"enrollmentStatus":"ENROLLED",
			"cumulativeGpa":3.30,"previousSemesterGpa":3.60,
			"incomeAssessment":{"status":"CALCULATED","year":2027,"semester":1,"scale":"FIVE_GROUPS","bracket":"나"},
			"specialEligibility":"NONE","volunteerMinutes":750,"birthDate":"2004-06-01",
			"residence":{"status":"REGISTERED","sidoCode":"41","sigunguCode":"41130"},"gender":null}
			""";
	private static final String NULL_PROFILE = """
			{"departmentCode":null,"grade":null,"enrollmentStatus":null,"cumulativeGpa":null,
			"previousSemesterGpa":null,"incomeAssessment":null,"specialEligibility":null,
			"volunteerMinutes":null,"birthDate":null,"residence":null,"gender":null}
			""";

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
	@Autowired
	private JwtProperties jwtProperties;

	private final List<String> testEmails = new ArrayList<>();

	@AfterEach
	void removeTestData() {
		if (testEmails.isEmpty()) {
			return;
		}
		String placeholders = String.join(",", java.util.Collections.nCopies(testEmails.size(), "?"));
		Object[] parameters = testEmails.toArray();
		String userIds = "SELECT user_id FROM users WHERE email IN (" + placeholders + ")";
		jdbc.update("DELETE FROM analysis_items WHERE analysis_id IN "
				+ "(SELECT analysis_id FROM analyses WHERE user_id IN (" + userIds + "))", parameters);
		jdbc.update("DELETE FROM analyses WHERE user_id IN (" + userIds + ")", parameters);
		jdbc.update("DELETE FROM student_profiles WHERE user_id IN (" + userIds + ")", parameters);
		jdbc.update("DELETE FROM users WHERE email IN (" + placeholders + ")", parameters);
	}

	@Test
	void fullUserJourneyFromSignupToProfileAndAnalysisVersionCheck() throws Exception {
		String email = track("journey@gachon.ac.kr");
		mockMvc.perform(get(USERS_URL + "/email-availability").param("email", email))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.available").value(true));

		mockMvc.perform(post(USERS_URL).contentType(MediaType.APPLICATION_JSON)
						.content(signupBody(email, PASSWORD, "여정학생")))
				.andExpect(status().isCreated());

		mockMvc.perform(get(USERS_URL + "/email-availability")
						.param("email", "  JOURNEY@GACHON.AC.KR  "))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.available").value(false));

		String token = login(email, PASSWORD);
		String authorization = bearer(token);

		mockMvc.perform(get(PROFILE_URL).header("Authorization", authorization))
				.andExpect(status().isNotFound());
		mockMvc.perform(put(PROFILE_URL).header("Authorization", authorization)
						.contentType(MediaType.APPLICATION_JSON).content(PROFILE))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.data.version").value(1));
		mockMvc.perform(put(PROFILE_URL).header("Authorization", authorization)
						.contentType(MediaType.APPLICATION_JSON)
						.content(PROFILE.replace("\"volunteerMinutes\":750", "\"volunteerMinutes\":751")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.version").value(2));

		mockMvc.perform(post(ANALYSIS_URL).header("Authorization", authorization)
						.contentType(MediaType.APPLICATION_JSON).content("{\"profileVersion\":1}"))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("VERSION_CONFLICT"));

		MvcResult analysis = mockMvc.perform(post(ANALYSIS_URL).header("Authorization", authorization)
						.contentType(MediaType.APPLICATION_JSON).content("{\"profileVersion\":2}"))
				.andExpect(status().isAccepted())
				.andReturn();
		assertThat(analysis.getResponse().getHeader("Location"))
				.matches(".*/api/v1/analyses/[^/]+");
	}

	@Test
	void idempotentProfilePutPreservesVersionAndAllNullReplacesWithNextVersion() throws Exception {
		String email = track("profile-idempotency@gachon.ac.kr");
		register(email, PASSWORD);
		String authorization = bearer(login(email, PASSWORD));

		MvcResult firstSave = mockMvc.perform(put(PROFILE_URL).header("Authorization", authorization)
						.contentType(MediaType.APPLICATION_JSON).content(PROFILE))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.data.version").value(1))
				.andReturn();
		String originalUpdatedAt = JsonPath.read(firstSave.getResponse().getContentAsString(), "$.data.updatedAt");

		MvcResult unchangedSave = mockMvc.perform(put(PROFILE_URL).header("Authorization", authorization)
						.contentType(MediaType.APPLICATION_JSON).content(PROFILE))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.version").value(1))
				.andReturn();
		String unchangedUpdatedAt = JsonPath.<String>read(unchangedSave.getResponse().getContentAsString(),
				"$.data.updatedAt");
		assertThat(unchangedUpdatedAt).isEqualTo(originalUpdatedAt);

		mockMvc.perform(put(PROFILE_URL).header("Authorization", authorization)
						.contentType(MediaType.APPLICATION_JSON).content(NULL_PROFILE))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.version").value(2));
		MvcResult allNullProfile = mockMvc.perform(get(PROFILE_URL).header("Authorization", authorization))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data", aMapWithSize(13)))
				.andReturn();
		Map<String, Object> allNullData = JsonPath.<Map<String, Object>>read(
				allNullProfile.getResponse().getContentAsString(), "$.data");
		assertThat(allNullData).containsKeys("departmentCode", "grade", "enrollmentStatus",
				"cumulativeGpa", "previousSemesterGpa", "incomeAssessment", "specialEligibility",
				"volunteerMinutes", "birthDate", "residence", "gender", "version", "updatedAt");
		for (String field : List.of("departmentCode", "grade", "enrollmentStatus", "cumulativeGpa",
				"previousSemesterGpa", "incomeAssessment", "specialEligibility", "volunteerMinutes",
				"birthDate", "residence", "gender")) {
			assertThat(allNullData).containsEntry(field, null);
		}
	}

	@Test
	void crossRoleAndInvalidTokenResponsesFollowProblemDetailsAndRequestIdContract() throws Exception {
		String userEmail = track("role-user@gachon.ac.kr");
		register(userEmail, PASSWORD);
		String userToken = login(userEmail, PASSWORD);
		User admin = createAdmin(track("role-admin@gachon.ac.kr"));
		String adminToken = loginAdmin(admin.getEmail(), PASSWORD);

		assertProblem(mockMvc.perform(get("/api/v1/admin/scholarships")
						.header("Authorization", bearer(userToken))).andReturn(), 403, "FORBIDDEN");
		assertProblem(mockMvc.perform(get(PROFILE_URL)
						.header("Authorization", bearer(adminToken))).andReturn(), 403, "FORBIDDEN");

		String invalidToken = tamperToken(userToken);
		assertInvalidToken(mockMvc.perform(get(PROFILE_URL)
				.header("Authorization", bearer(invalidToken))).andReturn());
		String userId = users.findByEmail(userEmail).orElseThrow().getId().toString();
		String expiredToken = expiredToken(userId);
		assertInvalidToken(mockMvc.perform(get(PROFILE_URL)
				.header("Authorization", bearer(expiredToken))).andReturn());
	}

	@Test
	void seventyTwoUtf8BytePasswordWorksAcrossSignupAndLogin() throws Exception {
		String email = track("password-bytes@gachon.ac.kr");
		String password72 = "A1!" + "x".repeat(69);
		assertThat(password72.getBytes(java.nio.charset.StandardCharsets.UTF_8)).hasSize(72);
		register(email, password72);
		login(email, password72);

		MvcResult tooLongPasswordLogin = mockMvc.perform(post(SESSIONS_URL)
						.contentType(MediaType.APPLICATION_JSON)
						.content(loginBody(email, password72 + "x")))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("AUTHENTICATION_FAILED"))
				.andReturn();
		assertThat(tooLongPasswordLogin.getResponse().getStatus()).isEqualTo(401);
	}

	@Test
	void concurrentSignupAndConcurrentInitialProfileSaveHandleRacesWithoutServerError() throws Exception {
		String email = track("signup-race@gachon.ac.kr");
		List<MvcResult> signupResults = performAtOnce(
				() -> mockMvc.perform(post(USERS_URL).contentType(MediaType.APPLICATION_JSON)
						.content(signupBody(email, PASSWORD, "동시학생"))).andReturn(),
				() -> mockMvc.perform(post(USERS_URL).contentType(MediaType.APPLICATION_JSON)
						.content(signupBody(email, PASSWORD, "동시학생"))).andReturn());
		assertThat(signupResults).extracting(result -> result.getResponse().getStatus())
				.containsExactlyInAnyOrder(201, 409);
		assertThat(signupResults.stream().filter(result -> result.getResponse().getStatus() == 409)
				.findFirst().orElseThrow().getResponse().getContentAsString())
				.contains("EMAIL_ALREADY_EXISTS");
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM users WHERE email = ?", Integer.class, email))
				.isEqualTo(1);

		String authorization = bearer(login(email, PASSWORD));
		List<MvcResult> profileResults = performAtOnce(
				() -> mockMvc.perform(put(PROFILE_URL).header("Authorization", authorization)
						.contentType(MediaType.APPLICATION_JSON).content(PROFILE)).andReturn(),
				() -> mockMvc.perform(put(PROFILE_URL).header("Authorization", authorization)
						.contentType(MediaType.APPLICATION_JSON).content(PROFILE)).andReturn());
		assertThat(profileResults.stream().filter(result -> result.getResponse().getStatus() == 201))
				.hasSize(1);
		assertThat(profileResults.stream().filter(result -> result.getResponse().getStatus() == 200
						|| result.getResponse().getStatus() == 409))
				.hasSize(1);
		assertThat(profileResults).allSatisfy(result ->
				assertThat(result.getResponse().getStatus()).isIn(200, 201, 409));
		var conflictResult = profileResults.stream()
				.filter(result -> result.getResponse().getStatus() == 409)
				.findFirst();
		if (conflictResult.isPresent()) {
			assertThat(conflictResult.orElseThrow().getResponse().getContentAsString())
					.contains("VERSION_CONFLICT");
		}
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM student_profiles WHERE user_id = "
						+ "(SELECT user_id FROM users WHERE email = ?)", Integer.class, email))
				.isEqualTo(1);
	}

	private String track(String email) {
		testEmails.add(email);
		return email;
	}

	private void register(String email, String password) throws Exception {
		mockMvc.perform(post(USERS_URL).contentType(MediaType.APPLICATION_JSON)
						.content(signupBody(email, password, "통합테스트")))
				.andExpect(status().isCreated());
	}

	private String login(String email, String password) throws Exception {
		MvcResult result = mockMvc.perform(post(SESSIONS_URL).contentType(MediaType.APPLICATION_JSON)
						.content(loginBody(email, password)))
				.andExpect(status().isOk())
				.andReturn();
		return JsonPath.read(result.getResponse().getContentAsString(), "$.data.accessToken");
	}

	private String loginAdmin(String email, String password) throws Exception {
		MvcResult result = mockMvc.perform(post("/api/v1/admin/sessions")
						.contentType(MediaType.APPLICATION_JSON).content(loginBody(email, password)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.role").value("ADMIN"))
				.andReturn();
		return JsonPath.read(result.getResponse().getContentAsString(), "$.data.accessToken");
	}

	private User createAdmin(String email) {
		return users.saveAndFlush(User.create(email, passwordEncoder.encode(PASSWORD), UserRole.ADMIN,
				"통합테스트 관리자", Instant.now(), false));
	}

	private List<MvcResult> performAtOnce(java.util.concurrent.Callable<MvcResult> first,
			java.util.concurrent.Callable<MvcResult> second) throws Exception {
		ExecutorService executor = Executors.newFixedThreadPool(2);
		CountDownLatch ready = new CountDownLatch(2);
		CountDownLatch start = new CountDownLatch(1);
		try {
			Future<MvcResult> firstResult = executor.submit(() -> {
				ready.countDown();
				start.await();
				return first.call();
			});
			Future<MvcResult> secondResult = executor.submit(() -> {
				ready.countDown();
				start.await();
				return second.call();
			});
			assertThat(ready.await(10, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
			start.countDown();
			return List.of(firstResult.get(), secondResult.get());
		} finally {
			executor.shutdownNow();
		}
	}

	private void assertInvalidToken(MvcResult result) throws Exception {
		assertProblem(result, 401, "TOKEN_INVALID");
		assertThat(result.getResponse().getHeader("WWW-Authenticate")).startsWith("Bearer");
	}

	private void assertProblem(MvcResult result, int statusCode, String code) throws Exception {
		assertThat(result.getResponse().getStatus()).isEqualTo(statusCode);
		assertThat(result.getResponse().getContentType())
				.isEqualTo(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
		assertThat(result.getResponse().getHeader("Cache-Control")).contains("no-store");
		String requestId = result.getResponse().getHeader("X-Request-Id");
		assertThat(requestId).isNotBlank();
		String actualCode = JsonPath.<String>read(result.getResponse().getContentAsString(), "$.code");
		String actualRequestId = JsonPath.<String>read(result.getResponse().getContentAsString(), "$.requestId");
		assertThat(actualCode).isEqualTo(code);
		assertThat(actualRequestId).isEqualTo(requestId);
	}

	private String expiredToken(String userId) {
		Clock fixedClock = Clock.fixed(Instant.parse("2020-01-01T00:00:00Z"), ZoneOffset.UTC);
		Instant issuedAt = fixedClock.instant();
		return Jwts.builder()
				.subject(userId)
				.claim("role", UserRole.USER.name())
				.issuedAt(java.util.Date.from(issuedAt))
				.expiration(java.util.Date.from(issuedAt.plusSeconds(tokenProvider.getAccessTokenExpirationSeconds())))
				.signWith(Keys.hmacShaKeyFor(Decoders.BASE64.decode(jwtProperties.secret())))
				.compact();
	}

	private String tamperToken(String token) {
		String[] segments = token.split("\\.");
		char replacement = segments[2].charAt(0) == 'a' ? 'b' : 'a';
		segments[2] = replacement + segments[2].substring(1);
		return String.join(".", segments);
	}

	private static String bearer(String token) {
		return "Bearer " + token;
	}

	private static String signupBody(String email, String password, String nickname) {
		return """
				{"email":"%s","password":"%s","nickname":"%s","serviceTermsAgreed":true,
				"privacyPolicyAgreed":true,"notificationAgreed":true}
				""".formatted(email, password, nickname);
	}

	private static String loginBody(String email, String password) {
		return """
				{"email":"%s","password":"%s"}
				""".formatted(email, password);
	}
}
