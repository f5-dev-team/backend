package com.f5.scholarship_backend.global.config;

import java.util.List;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.ArraySchema;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {
	@Bean
	OpenAPI scholarshipOpenApi() {
		var fieldError = new ObjectSchema()
				.addProperty("field", new StringSchema())
				.addProperty("reason", new StringSchema());
		fieldError.setRequired(List.of("field", "reason"));
		var problem = new ObjectSchema()
				.addProperty("type", new StringSchema().example("about:blank"))
				.addProperty("title", new StringSchema().example("Bad Request"))
				.addProperty("status", new IntegerSchema().example(400))
				.addProperty("detail", new StringSchema().example("입력값을 확인해 주세요."))
				.addProperty("instance", new StringSchema().example("urn:uuid:550e8400-e29b-41d4-a716-446655440000"))
				.addProperty("code", new StringSchema().example("VALIDATION_ERROR"))
				.addProperty("errors", new ArraySchema().items(fieldError))
				.addProperty("requestId", new StringSchema().example("req-example"));
		problem.setRequired(List.of("type", "title", "status", "detail", "instance", "code", "errors", "requestId"));
		return new OpenAPI().info(new Info().title("장학금 백엔드 API").version("v1"))
				.components(new Components()
						.addSecuritySchemes("bearerAuth", new SecurityScheme().type(SecurityScheme.Type.HTTP)
								.scheme("bearer").bearerFormat("JWT"))
						.addSchemas("ApiProblem", problem)
						.addResponses("ProfileConflict", errorResponse("PROFILE_REQUIRED: 학생 정보 미등록 / VERSION_CONFLICT: 프로필 버전 불일치")));
	}

	@Bean
	OpenApiCustomizer commonErrorResponses() {
		return api -> api.getPaths().forEach((path, item) -> {
			if (path.startsWith("/api/v1/")) {
				item.readOperations().forEach(operation -> {
					operation.getResponses().addApiResponse("400", errorResponse("VALIDATION_ERROR: 입력 오류"));
					operation.getResponses().addApiResponse("401", errorResponse("TOKEN_INVALID: 인증 정보 없음 또는 유효하지 않음"));
					operation.getResponses().addApiResponse("500", errorResponse("INTERNAL_SERVER_ERROR: 내부 오류"));
					operation.getResponses().addApiResponse("503", errorResponse("SERVICE_UNAVAILABLE: 서비스 미연결 또는 일시적 이용 불가"));
				});
			}
		});
	}

	private ApiResponse errorResponse(String description) {
		return new ApiResponse().description(description).content(new Content()
				.addMediaType("application/problem+json", new MediaType()
						.schema(new ObjectSchema().$ref("#/components/schemas/ApiProblem"))));
	}
}
