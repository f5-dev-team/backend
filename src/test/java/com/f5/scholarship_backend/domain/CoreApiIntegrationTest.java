package com.f5.scholarship_backend.domain;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CoreApiIntegrationTest {
	@Autowired
	private MockMvc mockMvc;

	@Test
	void unimplementedServicesNeverReportSuccessfulPersistenceOrAcceptance() throws Exception {
		mockMvc.perform(put("/api/v1/users/me/student-profile").principal(() -> "101")
						.contentType(MediaType.APPLICATION_JSON).content("""
								{"department":null,"grade":null,"enrollmentStatus":null,
								"cumulativeGpa":null,"previousSemesterGpa":null,"incomeBracket":null,
								"volunteerHours":null,"age":null,"residenceAddress":null,"gender":null}
								"""))
				.andExpect(status().isServiceUnavailable())
				.andExpect(jsonPath("$.code").value("SERVICE_UNAVAILABLE"))
				.andExpect(jsonPath("$.data").doesNotExist());
		mockMvc.perform(post("/api/v1/analyses").principal(() -> "101")
						.contentType(MediaType.APPLICATION_JSON).content("{\"profileVersion\":1}"))
				.andExpect(status().isServiceUnavailable())
				.andExpect(jsonPath("$.code").value("SERVICE_UNAVAILABLE"))
				.andExpect(header().doesNotExist("Location"));
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
				.andExpect(jsonPath("$.components.schemas.StudentProfileSaveRequest.required", hasSize(10)))
				.andExpect(jsonPath("$.components.schemas.ApiProblem.required", hasSize(8)));
	}
}
