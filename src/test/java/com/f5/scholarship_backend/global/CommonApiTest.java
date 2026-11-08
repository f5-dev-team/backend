package com.f5.scholarship_backend.global;

import java.util.Map;
import com.f5.scholarship_backend.global.config.RequestIdFilter;
import com.f5.scholarship_backend.global.exception.BusinessException;
import com.f5.scholarship_backend.global.exception.ErrorCode;
import com.f5.scholarship_backend.global.response.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(CommonApiTest.TestController.class)
class CommonApiTest {
	@Autowired
	private MockMvc mockMvc;

	@Test
	void successUsesDataAndServerGeneratedRequestId() throws Exception {
		var result = mockMvc.perform(get("/test/common/success").header("X-Request-Id", "untrusted"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.name").value("테스트"))
				.andExpect(jsonPath("$.meta.requestId").value(startsWith("req-")))
				.andExpect(jsonPath("$.statusCode").doesNotExist())
				.andReturn();
		String requestId = result.getResponse().getHeader("X-Request-Id");
		assertThat(requestId).isNotEqualTo("untrusted");
		assertThat(result.getResponse().getContentAsString()).contains(requestId);
		mockMvc.perform(get("/test/common/success"))
				.andExpect(header().string("X-Request-Id", not(requestId)));
		assertThat(MDC.get("requestId")).isNull();
	}

	@Test
	void emptySuccessIncludesNullData() throws Exception {
		mockMvc.perform(get("/test/common/empty"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$", hasKey("data")))
				.andExpect(jsonPath("$.data").value(nullValue()));
	}

	@ParameterizedTest
	@EnumSource(ErrorCode.class)
	void businessErrorsFollowContract(ErrorCode code) throws Exception {
		var result = mockMvc.perform(get("/test/common/business").param("code", code.name()))
				.andExpect(status().is(code.getStatus().value()))
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
				.andExpect(header().string("Cache-Control", "no-store"))
				.andExpect(jsonPath("$.type").value("about:blank"))
				.andExpect(jsonPath("$.title").value(code.getStatus().getReasonPhrase()))
				.andExpect(jsonPath("$.status").value(code.getStatus().value()))
				.andExpect(jsonPath("$.detail").value(code.getMessage()))
				.andExpect(jsonPath("$.instance").value(startsWith("urn:uuid:")))
				.andExpect(jsonPath("$.code").value(code.name()))
				.andExpect(jsonPath("$.errors").isEmpty())
				.andExpect(jsonPath("$.data").doesNotExist())
				.andReturn();
		assertThat(result.getResponse().getContentAsString())
				.contains(result.getResponse().getHeader("X-Request-Id"));
		if (code == ErrorCode.TOKEN_INVALID) {
			assertThat(result.getResponse().getHeader("WWW-Authenticate")).isEqualTo("Bearer");
		}
	}

	@Test
	void validationReturnsFieldWithoutRejectedValue() throws Exception {
		mockMvc.perform(post("/test/common/input").contentType(MediaType.APPLICATION_JSON)
						.content("{\"name\":\"\"}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
				.andExpect(jsonPath("$.errors[0].field").value("name"))
				.andExpect(jsonPath("$.errors[0].reason").isString())
				.andExpect(jsonPath("$.errors[0].rejectedValue").doesNotExist());
	}

	@Test
	void malformedJsonDoesNotExposeInput() throws Exception {
		mockMvc.perform(post("/test/common/input").contentType(MediaType.APPLICATION_JSON)
						.content("{\"name\":secret-value"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
				.andExpect(content().string(not(containsString("secret-value"))));
	}

	@Test
	void internalInvariantFailureIs500WithoutInternalMessage() throws Exception {
		mockMvc.perform(get("/test/common/internal"))
				.andExpect(status().isInternalServerError())
				.andExpect(jsonPath("$.code").value("INTERNAL_SERVER_ERROR"))
				.andExpect(content().string(not(containsString("private-database-password"))));
		assertThat(MDC.get("requestId")).isNull();
	}

	@Test
	void frameworkErrorsPreserveStatusAndHeaders() throws Exception {
		mockMvc.perform(get("/test/common/input"))
				.andExpect(status().isMethodNotAllowed())
				.andExpect(header().string("Allow", containsString("POST")))
				.andExpect(jsonPath("$.status").value(405))
				.andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"));
		mockMvc.perform(post("/test/common/input").contentType(MediaType.TEXT_PLAIN).content("text"))
				.andExpect(status().isUnsupportedMediaType())
				.andExpect(jsonPath("$.status").value(415));
		mockMvc.perform(get("/test/common/missing"))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
	}

	@Test
	void restoresExistingMdcAfterFailure() throws Exception {
		MDC.put("requestId", "outer-request");
		try {
			mockMvc.perform(get("/test/common/internal")).andExpect(status().isInternalServerError());
			assertThat(MDC.get("requestId")).isEqualTo("outer-request");
		} finally {
			MDC.remove("requestId");
		}
	}

	@RestController
	@RequestMapping("/test/common")
	static class TestController {
		@GetMapping("/success")
		ApiResponse<Map<String, String>> success(HttpServletRequest request) {
			return ApiResponse.of(Map.of("name", "테스트"), RequestIdFilter.getRequestId(request));
		}
		@GetMapping("/empty")
		ApiResponse<Void> empty(HttpServletRequest request) {
			return ApiResponse.of(null, RequestIdFilter.getRequestId(request));
		}
		@GetMapping("/business")
		void business(@RequestParam ErrorCode code) {
			throw new BusinessException(code);
		}
		@PostMapping("/input")
		ApiResponse<Input> input(@Valid @RequestBody Input input, HttpServletRequest request) {
			return ApiResponse.of(input, RequestIdFilter.getRequestId(request));
		}
		@GetMapping("/internal")
		void internal() {
			throw new IllegalArgumentException("private-database-password");
		}
	}
	record Input(@NotBlank String name) {
	}
}