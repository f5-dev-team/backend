package com.f5.scholarship_backend.domain;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import com.f5.scholarship_backend.domain.scholarship.entity.Scholarship;
import com.f5.scholarship_backend.domain.scholarship.entity.ScholarshipVersion;
import com.f5.scholarship_backend.domain.scholarship.repository.ScholarshipRepository;
import com.f5.scholarship_backend.domain.scholarship.repository.ScholarshipVersionRepository;
import com.f5.scholarship_backend.domain.user.entity.User;
import com.f5.scholarship_backend.domain.user.enums.UserRole;
import com.f5.scholarship_backend.domain.user.repository.UserRepository;
import com.f5.scholarship_backend.global.security.JwtTokenProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.json.JsonMapper;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "student-profile.department-max-grades.CS=4")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CoreApiIntegrationTest {
	private static final String ANALYSIS_URL = "/api/v1/analyses";
	private static final String PROFILE_URL = "/api/v1/users/me/student-profile";
	private static final String PROFILE = """
			{"departmentCode":"CS","grade":3,"enrollmentStatus":"ENROLLED",
			"cumulativeGpa":3.30,"previousSemesterGpa":null,
			"incomeAssessment":{"status":"CALCULATED","year":2027,"semester":1,"scale":"FIVE_GROUPS","bracket":"나"},
			"specialEligibility":"NONE","volunteerMinutes":750,"birthDate":"2004-06-01",
			"residence":{"status":"REGISTERED","sidoCode":"41","sigunguCode":"41130"},"gender":null}
			""";

	@Autowired
	private MockMvc mockMvc;
	@Autowired
	private JdbcTemplate jdbc;
	@Autowired
	private UserRepository users;
	@Autowired
	private JwtTokenProvider jwtTokenProvider;
	@Autowired
	private ScholarshipRepository scholarships;
	@Autowired
	private ScholarshipVersionRepository versions;
	@Autowired
	private JsonMapper json;

	private User student;
	private User otherStudent;
	private User admin;

	@BeforeEach
	void createTestAccounts() {
		student = createAccount("student@gachon.ac.kr", UserRole.USER);
		otherStudent = createAccount("other@gachon.ac.kr", UserRole.USER);
		admin = createAccount("admin@gachon.ac.kr", UserRole.ADMIN);
	}

	@AfterEach
	void removeTestAccounts() {
		jdbc.update("DELETE FROM analysis_items WHERE analysis_id IN (SELECT analysis_id FROM analyses WHERE user_id = ?)", student.getId());
		jdbc.update("DELETE FROM analyses WHERE user_id = ?", student.getId());
		jdbc.update("""
				DELETE FROM scholarship_versions WHERE scholarship_id IN
				(SELECT scholarship_id FROM scholarships WHERE source_post_key LIKE 'analysis-job-test-%')
				""");
		jdbc.update("DELETE FROM scholarships WHERE source_post_key LIKE 'analysis-job-test-%'");
		jdbc.update("DELETE FROM student_profiles WHERE user_id = ?", student.getId());
		users.deleteAll(List.of(student, otherStudent, admin));
	}

	private User createAccount(String email, UserRole role) {
		return users.save(User.create(email, "test-hash", role, "테스트", Instant.now(), false));
	}

	@Test
	void rejectsAnalysisWithoutMatchingProfileVersion() throws Exception {
		mockMvc.perform(post(ANALYSIS_URL).header("Authorization", bearerToken(student))
						.contentType(MediaType.APPLICATION_JSON).content("{\"profileVersion\":1}"))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("PROFILE_REQUIRED"))
				.andExpect(header().doesNotExist("Location"));
		saveAnalysisProfile(PROFILE);
		mockMvc.perform(post(ANALYSIS_URL).header("Authorization", bearerToken(student))
						.contentType(MediaType.APPLICATION_JSON).content("{\"profileVersion\":2}"))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("VERSION_CONFLICT"))
				.andExpect(header().doesNotExist("Location"));
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM analyses WHERE user_id = ?", Long.class, student.getId()))
				.isZero();
	}

	@Test
	void acceptsAnalysisAndReadsOriginalCandidateAndProfileSnapshots() throws Exception {
		long ready = createCandidate("ready", "READY", null);
		addCandidateVersion(ready, 2);
		createCandidate("pending", "PENDING", null);
		createCandidate("processing", "PROCESSING", null);
		createCandidate("failed", "FAILED", null);
		createCandidate("expired", "FAILED", Instant.now().minusSeconds(3600));
		saveAnalysisProfile(PROFILE);
		String location = mockMvc.perform(post(ANALYSIS_URL).header("Authorization", bearerToken(student))
						.contentType(MediaType.APPLICATION_JSON).content("{\"profileVersion\":1}"))
				.andExpect(status().isAccepted())
				.andExpect(jsonPath("$.data.status").value("QUEUED"))
				.andExpect(jsonPath("$.data.profileVersion").value(1))
				.andExpect(jsonPath("$.data.createdAt").isString())
				.andReturn().getResponse().getHeader("Location");

		assertThat(location).startsWith(ANALYSIS_URL + "/");
		long id = Long.parseLong(location.substring((ANALYSIS_URL + "/").length()));
		mockMvc.perform(get(location)).andExpect(status().isUnauthorized());
		mockMvc.perform(get(location).header("Authorization", bearerToken(admin))).andExpect(status().isForbidden());
		mockMvc.perform(get(location).header("Authorization", bearerToken(otherStudent)))
				.andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
		mockMvc.perform(get(ANALYSIS_URL + "/not-an-id").header("Authorization", bearerToken(student)))
				.andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));

		saveAnalysisProfile(PROFILE.replace("750", "0"));
		addCandidateVersion(ready, 3);
		setProcessingStatus(ready, "FAILED");

		mockMvc.perform(get(location).header("Authorization", bearerToken(student)))
				.andExpect(status().isOk())
				.andExpect(header().string("Cache-Control", "no-store"))
				.andExpect(jsonPath("$.data.analysisId").value(Long.toString(id)))
				.andExpect(jsonPath("$.data.status").value("QUEUED"))
				.andExpect(jsonPath("$.data.profileVersion").value(1))
				.andExpect(jsonPath("$.data.totalCount").value(1))
				.andExpect(jsonPath("$.data.candidateCount").value(5))
				.andExpect(jsonPath("$.data.excludedCount").value(4))
				.andExpect(jsonPath("$.data.processedCount").value(0))
				.andExpect(jsonPath("$.data.exclusions[?(@.reason == 'DEADLINE_PASSED')].count").value(contains(1)))
				.andExpect(jsonPath("$.data.exclusions[?(@.reason == 'PROCESSING_FAILED')].count").value(contains(1)))
				.andExpect(jsonPath("$.data.exclusions[?(@.reason == 'PROCESSING_PENDING')].count").value(contains(2)))
				.andExpect(jsonPath("$.data.startedAt").value(nullValue()))
				.andExpect(jsonPath("$.data.completedAt").value(nullValue()))
				.andExpect(jsonPath("$.data.failure").value(nullValue()));

		var snapshot = json.readTree(jdbc.queryForObject("SELECT profile_snapshot FROM analyses WHERE analysis_id = ?",
				String.class, id));
		assertThat(snapshot.isObject()).isTrue();
		assertThat(snapshot.size()).isEqualTo(11);
		assertThat(snapshot.get("volunteerMinutes").longValue()).isEqualTo(750L);
		assertThat(snapshot.get("previousSemesterGpa").isNull()).isTrue();
		assertThat(snapshot.get("birthDate").asString()).isEqualTo("2004-06-01");
		assertThat(jdbc.queryForObject("""
				SELECT scholarship_version FROM analysis_items WHERE analysis_id = ? AND scholarship_id = ?
				""", Integer.class, id, ready)).isEqualTo(2);
	}

	@Test
	void repeatedPostCreatesAnotherQueuedJobEvenWithoutCandidates() throws Exception {
		saveAnalysisProfile(PROFILE);
		String first = mockMvc.perform(post(ANALYSIS_URL).header("Authorization", bearerToken(student))
						.contentType(MediaType.APPLICATION_JSON).content("{\"profileVersion\":1}"))
				.andExpect(status().isAccepted()).andReturn().getResponse().getHeader("Location");
		String second = mockMvc.perform(post(ANALYSIS_URL).header("Authorization", bearerToken(student))
						.contentType(MediaType.APPLICATION_JSON).content("{\"profileVersion\":1}"))
				.andExpect(status().isAccepted()).andReturn().getResponse().getHeader("Location");
		assertThat(second).isNotEqualTo(first);
		mockMvc.perform(get(second).header("Authorization", bearerToken(student)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.totalCount").value(0))
				.andExpect(jsonPath("$.data.candidateCount").value(0))
				.andExpect(jsonPath("$.data.excludedCount").value(0))
				.andExpect(jsonPath("$.data.exclusions").isEmpty());
	}

	private void saveAnalysisProfile(String profile) throws Exception {
		mockMvc.perform(put(PROFILE_URL).header("Authorization", bearerToken(student))
						.contentType(MediaType.APPLICATION_JSON).content(profile))
				.andExpect(status().is2xxSuccessful());
	}

	private long createCandidate(String key, String processingStatus, Instant deadline) {
		var scholarship = scholarships.save(new Scholarship("analysis-job-test-" + key, Instant.now()));
		long id = scholarship.getId();
		addCandidateVersion(id, 1);
		setProcessingStatus(id, processingStatus);
		if (deadline != null) {
			jdbc.update("UPDATE scholarship_versions SET application_end_at = ? WHERE scholarship_id = ?",
					Timestamp.from(deadline), id);
		}
		return id;
	}

	private void setProcessingStatus(long id, String processingStatus) {
		boolean failed = "FAILED".equals(processingStatus);
		jdbc.update("""
				UPDATE scholarships SET processing_status = ?, processing_error_code = ?,
					processing_error_message = ? WHERE scholarship_id = ?
				""", processingStatus, failed ? "TEST_FAILURE" : null, failed ? "합성 오류" : null, id);
	}

	private void addCandidateVersion(long id, int version) {
		versions.save(new ScholarshipVersion(id, version, "합성 장학금", "https://example.test/notice",
				"합성 공고 원문", "[]", Instant.now()));
	}

	private String bearerToken(User user) {
		return "Bearer " + jwtTokenProvider.createAccessToken(user.getId().toString(), user.getRole());
	}

	@Test
	void savesReadsAndReplacesOnlyTheAuthenticatedProfile() throws Exception {
		mockMvc.perform(get(PROFILE_URL)).andExpect(status().isUnauthorized());
		mockMvc.perform(get(PROFILE_URL).header("Authorization", bearerToken(student))).andExpect(status().isNotFound());
		mockMvc.perform(put(PROFILE_URL).header("Authorization", bearerToken(student)).contentType(MediaType.APPLICATION_JSON).content(PROFILE))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.data.version").value(1))
				.andExpect(jsonPath("$.data.birthDate").value("2004-06-01"));
		mockMvc.perform(get(PROFILE_URL).header("Authorization", bearerToken(student)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.incomeAssessment.bracket").value("나"))
				.andExpect(jsonPath("$.data.residence.sigunguCode").value("41130"));
		mockMvc.perform(get(PROFILE_URL).header("Authorization", bearerToken(otherStudent))).andExpect(status().isNotFound());
		mockMvc.perform(get(PROFILE_URL).header("Authorization", bearerToken(admin))).andExpect(status().isForbidden());

		String updatedAt = jdbc.queryForObject("SELECT CAST(updated_at AS VARCHAR) FROM student_profiles WHERE user_id = ?",
				String.class, student.getId());
		mockMvc.perform(put(PROFILE_URL).header("Authorization", bearerToken(student)).contentType(MediaType.APPLICATION_JSON)
						.content(PROFILE.replace("3.30", "3.3")))
				.andExpect(status().isOk()).andExpect(jsonPath("$.data.version").value(1));
		assertThat(jdbc.queryForObject(
				"SELECT CAST(updated_at AS VARCHAR) FROM student_profiles WHERE user_id = ?", String.class, student.getId()))
				.isEqualTo(updatedAt);
		mockMvc.perform(put(PROFILE_URL).header("Authorization", bearerToken(student)).contentType(MediaType.APPLICATION_JSON)
						.content(PROFILE.replace("750", "0")))
				.andExpect(status().isOk()).andExpect(jsonPath("$.data.version").value(2));
		mockMvc.perform(get(PROFILE_URL).header("Authorization", bearerToken(student)))
				.andExpect(status().isOk()).andExpect(jsonPath("$.data.version").value(2))
				.andExpect(jsonPath("$.data.volunteerMinutes").value(0));
	}

	@ParameterizedTest
	@CsvSource({
			"'\"grade\":3', '\"grade\":5'",
			"'\"grade\":3', '\"grade\":65536'",
			"CS, INVALID_DEPARTMENT",
			"41130, 41999"
	})
	void rejectsInvalidGradeDepartmentOrRegion(String original, String invalid) throws Exception {
		mockMvc.perform(put(PROFILE_URL).header("Authorization", bearerToken(student))
						.contentType(MediaType.APPLICATION_JSON).content(PROFILE.replace(original, invalid)))
				.andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
		mockMvc.perform(get(PROFILE_URL).header("Authorization", bearerToken(student)))
				.andExpect(status().isNotFound());
	}

	@Test
	void openApiDescribesBothContractsAndCommonErrors() throws Exception {
		mockMvc.perform(get("/v3/api-docs"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.paths['/api/v1/users/me/student-profile'].put.operationId").value("saveStudentProfile"))
				.andExpect(jsonPath("$.paths['/api/v1/users/me/student-profile'].put.responses", allOf(hasKey("200"), hasKey("201"))))
				.andExpect(jsonPath("$.paths['/api/v1/analyses'].post.operationId").value("createAnalysis"))
				.andExpect(jsonPath("$.paths['/api/v1/analyses/{analysisId}'].get.operationId").value("getAnalysisStatus"))
				.andExpect(jsonPath("$.paths['/api/v1/analyses/{analysisId}'].get.responses['404'].content['application/problem+json']").exists())
				.andExpect(jsonPath("$.paths['/api/v1/analyses'].post.responses['202'].headers.Location").exists())
				.andExpect(jsonPath("$.paths['/api/v1/analyses'].post.responses['400'].content['application/problem+json']").exists())
				.andExpect(jsonPath("$.paths['/api/v1/analyses'].post.responses['409']['$ref']").value("#/components/responses/ProfileConflict"))
				.andExpect(jsonPath("$.paths['/api/v1/analyses'].post.security[0].bearerAuth").isArray())
				.andExpect(jsonPath("$.components.securitySchemes.bearerAuth.scheme").value("bearer"))
				.andExpect(jsonPath("$.components.schemas.StudentProfileSaveRequest.required", hasSize(11)))
				.andExpect(jsonPath("$.components.schemas.ApiProblem.required", hasSize(8)));
	}
}
