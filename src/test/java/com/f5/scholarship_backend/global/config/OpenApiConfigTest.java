package com.f5.scholarship_backend.global.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.Paths;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class OpenApiConfigTest {
	/** 개별 오류 응답 참조를 보존하고 누락 상태만 공통 응답으로 채우며 반복 적용에도 유지한다. */
	@ParameterizedTest
	@ValueSource(strings = {"400", "401", "500", "503"})
	void preservesOperationSpecificResponseAndAddsMissingFallbacks(String existingStatus) {
		var specific = new ApiResponse().$ref("#/components/responses/SpecificError");
		var operation = new Operation().responses(new ApiResponses().addApiResponse(existingStatus, specific));
		var api = new OpenAPI().paths(new Paths().addPathItem("/api/v1/example", new PathItem().post(operation)));

		var customizer = new OpenApiConfig().commonErrorResponses();
		customizer.customise(api);
		customizer.customise(api);

		assertThat(operation.getResponses().get(existingStatus)).isSameAs(specific);
		assertThat(specific.get$ref()).isEqualTo("#/components/responses/SpecificError");
		assertThat(operation.getResponses()).hasSize(4);
		for (String status : new String[] {"400", "401", "500", "503"}) {
			if (!status.equals(existingStatus)) {
				var fallback = operation.getResponses().get(status);
				assertThat(fallback.getDescription()).isNotBlank();
				assertThat(fallback.getContent().get("application/problem+json").getSchema().get$ref())
						.isEqualTo("#/components/schemas/ApiProblem");
			}
		}
	}
}