package com.f5.scholarship_backend.domain;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.stream.Stream;

import com.f5.scholarship_backend.domain.analysis.controller.AnalysisController;
import com.f5.scholarship_backend.domain.analysis.dto.AnalysisCreateResponse;
import com.f5.scholarship_backend.domain.analysis.enums.AnalysisStatus;
import com.f5.scholarship_backend.domain.analysis.service.AnalysisService;
import com.f5.scholarship_backend.domain.student.controller.StudentProfileController;
import com.f5.scholarship_backend.domain.student.dto.StudentProfileResponse;
import com.f5.scholarship_backend.domain.student.dto.StudentProfileSaveRequest;
import com.f5.scholarship_backend.domain.student.enums.EnrollmentStatus;
import com.f5.scholarship_backend.domain.student.service.StudentProfileSaveResult;
import com.f5.scholarship_backend.domain.student.service.StudentProfileService;
import com.f5.scholarship_backend.global.config.JsonConfig;
import com.f5.scholarship_backend.global.exception.BusinessException;
import com.f5.scholarship_backend.global.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest({StudentProfileController.class, AnalysisController.class})
@Import(JsonConfig.class)
class CoreApiControllerTest {
	private static final String PROFILE_URL = "/api/v1/users/me/student-profile";
	private static final String ANALYSIS_URL = "/api/v1/analyses";
	private static final String PROFILE = """
			{"department":"컴퓨터공학과","grade":3,"enrollmentStatus":"ENROLLED",
			"cumulativeGpa":3.3,"previousSemesterGpa":3.6,"incomeBracket":4,
			"volunteerHours":20,"age":22,"residenceAddress":"테스트 주소","gender":null}
			""";
	private static final OffsetDateTime TIME = OffsetDateTime.parse("2026-10-14T09:00:00+09:00");

	@Autowired
	private MockMvc mockMvc;
	@Autowired
	private JsonMapper mapper;
	@MockitoBean
	private StudentProfileService studentProfileService;
	@MockitoBean
	private AnalysisService analysisService;

	@ParameterizedTest
	@ValueSource(booleans = {true, false})
	void saveUsesServiceCreationFlagAndAuthenticatedUser(boolean created) throws Exception {
		var response = profileResponse();
		when(studentProfileService.save(eq("101"), any())).thenReturn(new StudentProfileSaveResult(response, created));
		var result = mockMvc.perform(put(PROFILE_URL).principal(() -> "101")
						.contentType(MediaType.APPLICATION_JSON).content(PROFILE))
				.andExpect(status().is(created ? 201 : 200))
				.andExpect(header().string("Cache-Control", "no-store"))
				.andExpect(jsonPath("$.data.department").value("컴퓨터공학과"))
				.andExpect(jsonPath("$.data.version").value(7))
				.andExpect(jsonPath("$.data.updatedAt").isString())
				.andExpect(jsonPath("$.data", hasKey("gender")))
				.andExpect(jsonPath("$.data.gender").value(nullValue()))
				.andExpect(jsonPath("$.meta.requestId").value(startsWith("req-")))
				.andReturn();
		assertThat(result.getResponse().getContentAsString())
				.contains(result.getResponse().getHeader("X-Request-Id"));
		var captor = ArgumentCaptor.forClass(StudentProfileSaveRequest.class);
		verify(studentProfileService).save(eq("101"), captor.capture());
		assertThat(captor.getValue().cumulativeGpa()).isEqualByComparingTo("3.3");
		assertThat(captor.getValue().gender()).isNull();
	}

	@Test
	void explicitlyNullFieldsAreAccepted() throws Exception {
		ObjectNode body = (ObjectNode) mapper.readTree(PROFILE);
		body.propertyNames().forEach(name -> body.putNull(name));
		var response = new StudentProfileResponse(null, null, null, null, null, null, null, null, null, null, 1, TIME);
		when(studentProfileService.save(eq("101"), any())).thenReturn(new StudentProfileSaveResult(response, true));
		mockMvc.perform(put(PROFILE_URL).principal(() -> "101").contentType(MediaType.APPLICATION_JSON)
						.content(body.toString()))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.data", aMapWithSize(12)))
				.andExpect(jsonPath("$.data.grade").value(nullValue()));
	}

	@ParameterizedTest
	@ValueSource(strings = {"department", "grade", "enrollmentStatus", "cumulativeGpa",
			"previousSemesterGpa", "incomeBracket", "volunteerHours", "age", "residenceAddress", "gender"})
	void everyProfileFieldMustBePresent(String field) throws Exception {
		ObjectNode body = (ObjectNode) mapper.readTree(PROFILE);
		body.remove(field);
		assertProfileBadRequest(body.toString());
	}

	@ParameterizedTest
	@MethodSource("invalidProfileFields")
	void rejectsInvalidProfileField(String field, String jsonValue) throws Exception {
		ObjectNode body = (ObjectNode) mapper.readTree(PROFILE);
		body.set(field, mapper.readTree(jsonValue));
		assertProfileBadRequest(body.toString());
	}

	static Stream<Arguments> invalidProfileFields() {
		return Stream.of(
				Arguments.of("department", "\"\""), Arguments.of("department", "\"   \""),
				Arguments.of("department", "123"), Arguments.of("residenceAddress", "true"),
				Arguments.of("grade", "0"), Arguments.of("grade", "1.5"), Arguments.of("grade", "\"3\""),
				Arguments.of("age", "-1"), Arguments.of("incomeBracket", "-1"),
				Arguments.of("cumulativeGpa", "-0.1"), Arguments.of("cumulativeGpa", "\"3.3\""),
				Arguments.of("previousSemesterGpa", "-1"), Arguments.of("volunteerHours", "-0.1"),
				Arguments.of("enrollmentStatus", "\"UNKNOWN\""), Arguments.of("enrollmentStatus", "0"),
				Arguments.of("enrollmentStatus", "\"\""), Arguments.of("gender", "\"UNKNOWN\""),
				Arguments.of("gender", "0"));
	}

	@ParameterizedTest
	@ValueSource(strings = {"userId", "version", "updatedAt", "unknownField"})
	void rejectsUndeclaredProfileFields(String field) throws Exception {
		ObjectNode body = (ObjectNode) mapper.readTree(PROFILE);
		body.put(field, "client-supplied");
		assertProfileBadRequest(body.toString());
	}

	@Test
	void zeroBoundaryAndUnconfirmedUpperLimitsAreNotInvented() throws Exception {
		ObjectNode body = (ObjectNode) mapper.readTree(PROFILE);
		body.put("grade", 1);
		body.put("cumulativeGpa", 5);
		body.put("previousSemesterGpa", 0);
		body.put("incomeBracket", 0);
		body.put("volunteerHours", 0);
		body.put("age", 0);
		when(studentProfileService.save(eq("101"), any()))
				.thenReturn(new StudentProfileSaveResult(profileResponse(), false));
		mockMvc.perform(put(PROFILE_URL).principal(() -> "101").contentType(MediaType.APPLICATION_JSON)
				.content(body.toString())).andExpect(status().isOk());
	}

	@Test
	void analysisReturnsAcceptedLocationAndServiceResponse() throws Exception {
		when(analysisService.create("101", 7)).thenReturn(
				new AnalysisCreateResponse("5001", AnalysisStatus.QUEUED, 7, TIME));
		mockMvc.perform(post(ANALYSIS_URL).principal(() -> "101").contentType(MediaType.APPLICATION_JSON)
						.content("{\"profileVersion\":7}"))
				.andExpect(status().isAccepted())
				.andExpect(header().string("Location", "/api/v1/analyses/5001"))
				.andExpect(header().string("Cache-Control", "no-store"))
				.andExpect(jsonPath("$.data.analysisId").value("5001"))
				.andExpect(jsonPath("$.data.status").value("QUEUED"))
				.andExpect(jsonPath("$.data.profileVersion").value(7))
				.andExpect(jsonPath("$.data.createdAt").isString())
				.andExpect(jsonPath("$.meta.requestId").value(startsWith("req-")));
		verify(analysisService).create("101", 7);
	}

	@ParameterizedTest
	@ValueSource(strings = {"{}", "null", "", "{", "{\"profileVersion\":null}",
			"{\"profileVersion\":0}", "{\"profileVersion\":-1}", "{\"profileVersion\":1.5}",
			"{\"profileVersion\":\"1\"}", "{\"profileVersion\":true}",
			"{\"profileVersion\":1,\"userId\":\"999\"}", "{\"profileVersion\":1} {}"})
	void invalidAnalysisBodyNeverCallsService(String body) throws Exception {
		mockMvc.perform(post(ANALYSIS_URL).principal(() -> "101").contentType(MediaType.APPLICATION_JSON).content(body))
				.andExpect(status().isBadRequest())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
				.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
		verifyNoInteractions(analysisService);
	}

	@ParameterizedTest
	@ValueSource(strings = {"PROFILE_REQUIRED", "VERSION_CONFLICT"})
	void analysisBusinessConflictIsNotAccepted(String codeName) throws Exception {
		when(analysisService.create("101", 1)).thenThrow(new BusinessException(ErrorCode.valueOf(codeName)));
		mockMvc.perform(post(ANALYSIS_URL).principal(() -> "101").contentType(MediaType.APPLICATION_JSON)
						.content("{\"profileVersion\":1}"))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value(codeName))
				.andExpect(header().doesNotExist("Location"));
	}

	@Test
	void noPrincipalIsNotReplacedWithFakeUser() throws Exception {
		mockMvc.perform(put(PROFILE_URL).contentType(MediaType.APPLICATION_JSON).content(PROFILE))
				.andExpect(status().isUnauthorized())
				.andExpect(header().string("WWW-Authenticate", "Bearer"));
		mockMvc.perform(post(ANALYSIS_URL).contentType(MediaType.APPLICATION_JSON).content("{\"profileVersion\":1}"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("TOKEN_INVALID"));
		verifyNoInteractions(studentProfileService, analysisService);
	}

	/** 본문이 유효할 때 인증 오류는 쿼리 파라미터 오류보다 우선하며 Service를 호출하지 않는다. */
	@ParameterizedTest
	@ValueSource(ints = {0, 1, 2, 3})
	void invalidPrincipalTakesPriorityOverUnknownQuery(int scenario) throws Exception {
		String name = switch (scenario) {
			case 1 -> null;
			case 2 -> "";
			default -> "   ";
		};
		var profile = put(PROFILE_URL).param("unexpected", "value")
				.contentType(MediaType.APPLICATION_JSON).content(PROFILE);
		var analysis = post(ANALYSIS_URL).param("unexpected", "value")
				.contentType(MediaType.APPLICATION_JSON).content("{\"profileVersion\":1}");
		if (scenario != 0) {
			profile.principal(() -> name);
			analysis.principal(() -> name);
		}
		for (var request : java.util.List.of(profile, analysis)) {
			mockMvc.perform(request)
					.andExpect(status().isUnauthorized())
					.andExpect(header().string("WWW-Authenticate", "Bearer"))
					.andExpect(jsonPath("$.code").value("TOKEN_INVALID"));
		}
		verifyNoInteractions(studentProfileService, analysisService);
	}

	@Test
	void rejectsUnknownQueryParameters() throws Exception {
		mockMvc.perform(put(PROFILE_URL).principal(() -> "101").param("userId", "999")
						.contentType(MediaType.APPLICATION_JSON).content(PROFILE))
				.andExpect(status().isBadRequest());
		mockMvc.perform(post(ANALYSIS_URL).principal(() -> "101").param("unexpected", "value")
						.contentType(MediaType.APPLICATION_JSON).content("{\"profileVersion\":1}"))
				.andExpect(status().isBadRequest());
		verifyNoInteractions(studentProfileService, analysisService);
	}

	private void assertProfileBadRequest(String body) throws Exception {
		mockMvc.perform(put(PROFILE_URL).principal(() -> "101").contentType(MediaType.APPLICATION_JSON).content(body))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
		verifyNoInteractions(studentProfileService);
	}

	private StudentProfileResponse profileResponse() {
		return new StudentProfileResponse("컴퓨터공학과", 3, EnrollmentStatus.ENROLLED,
				new BigDecimal("3.3"), new BigDecimal("3.6"), 4, new BigDecimal("20"),
				22, "테스트 주소", null, 7, TIME);
	}
}
