package com.f5.scholarship_backend.domain;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "student-profile.department-max-grades.CS=4")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CoreApiIntegrationTest {
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

	@BeforeEach
	void createTestAccounts() {
		jdbc.execute("CREATE TABLE IF NOT EXISTS users (user_id BIGINT PRIMARY KEY, role VARCHAR(16) NOT NULL)");
		jdbc.update("DELETE FROM student_profiles WHERE user_id IN (101,102,103)");
		jdbc.update("MERGE INTO users (user_id,role) KEY(user_id) VALUES (101,'USER'),(102,'USER'),(103,'ADMIN')");
	}

	@Test
	void analysisDoesNotReportAcceptanceBeforeIntegration() throws Exception {
		mockMvc.perform(post("/api/v1/analyses").principal(() -> "101")
						.contentType(MediaType.APPLICATION_JSON).content("{\"profileVersion\":1}"))
				.andExpect(status().isServiceUnavailable())
				.andExpect(jsonPath("$.code").value("SERVICE_UNAVAILABLE"))
				.andExpect(header().doesNotExist("Location"));
	}

	@Test
	void savesReadsAndReplacesOnlyTheAuthenticatedProfile() throws Exception {
		mockMvc.perform(get(PROFILE_URL).principal(() -> "101")).andExpect(status().isNotFound());
		mockMvc.perform(put(PROFILE_URL).principal(() -> "101").contentType(MediaType.APPLICATION_JSON).content(PROFILE))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.data.version").value(1))
				.andExpect(jsonPath("$.data.birthDate").value("2004-06-01"));
		mockMvc.perform(get(PROFILE_URL).principal(() -> "101"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.incomeAssessment.bracket").value("나"))
				.andExpect(jsonPath("$.data.residence.sigunguCode").value("41130"));
		mockMvc.perform(get(PROFILE_URL).principal(() -> "102")).andExpect(status().isNotFound());
		mockMvc.perform(get(PROFILE_URL).principal(() -> "103")).andExpect(status().isForbidden());

		String updatedAt = jdbc.queryForObject("SELECT CAST(updated_at AS VARCHAR) FROM student_profiles WHERE user_id=101",
				String.class);
		mockMvc.perform(put(PROFILE_URL).principal(() -> "101").contentType(MediaType.APPLICATION_JSON)
						.content(PROFILE.replace("3.30", "3.3")))
				.andExpect(status().isOk()).andExpect(jsonPath("$.data.version").value(1));
		org.assertj.core.api.Assertions.assertThat(jdbc.queryForObject(
				"SELECT CAST(updated_at AS VARCHAR) FROM student_profiles WHERE user_id=101", String.class))
				.isEqualTo(updatedAt);
		mockMvc.perform(put(PROFILE_URL).principal(() -> "101").contentType(MediaType.APPLICATION_JSON)
						.content(PROFILE.replace("750", "0")))
				.andExpect(status().isOk()).andExpect(jsonPath("$.data.version").value(2));
		mockMvc.perform(get(PROFILE_URL).principal(() -> "101"))
				.andExpect(status().isOk()).andExpect(jsonPath("$.data.version").value(2))
				.andExpect(jsonPath("$.data.volunteerMinutes").value(0));
	}

	@Test
	void rejectsGradeOutsideConfiguredDepartmentRange() throws Exception {
		mockMvc.perform(put(PROFILE_URL).principal(() -> "101").contentType(MediaType.APPLICATION_JSON)
						.content(PROFILE.replace("\"grade\":3", "\"grade\":5")))
				.andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
	}


	@Test
	void openApiDescribesBothContractsAndCommonErrors() throws Exception {
		mockMvc.perform(get("/v3/api-docs"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.paths['/api/v1/users/me/student-profile'].put.operationId").value("saveStudentProfile"))
				.andExpect(jsonPath("$.paths['/api/v1/users/me/student-profile'].put.responses", allOf(hasKey("200"), hasKey("201"))))
				.andExpect(jsonPath("$.paths['/api/v1/analyses'].post.operationId").value("createAnalysis"))
				.andExpect(jsonPath("$.paths['/api/v1/analyses'].post.responses['202'].headers.Location").exists())
				.andExpect(jsonPath("$.paths['/api/v1/analyses'].post.responses['400'].content['application/problem+json']").exists())
				.andExpect(jsonPath("$.paths['/api/v1/analyses'].post.responses['409']['$ref']").value("#/components/responses/ProfileConflict"))
				.andExpect(jsonPath("$.paths['/api/v1/analyses'].post.security[0].bearerAuth").isArray())
				.andExpect(jsonPath("$.components.securitySchemes.bearerAuth.scheme").value("bearer"))
				.andExpect(jsonPath("$.components.schemas.StudentProfileSaveRequest.required", hasSize(11)))
				.andExpect(jsonPath("$.components.schemas.ApiProblem.required", hasSize(8)));
	}
}
