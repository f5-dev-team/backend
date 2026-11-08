package com.f5.scholarship_backend.global.config;

import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.MapperFeature;
import tools.jackson.databind.cfg.CoercionAction;
import tools.jackson.databind.cfg.CoercionInputShape;
import tools.jackson.databind.cfg.EnumFeature;
import tools.jackson.databind.type.LogicalType;

@Configuration
public class JsonConfig {
	/** 정의되지 않은 JSON 필드와 명세에 없는 자료형 자동 변환을 거부한다. */
	@Bean
	JsonMapperBuilderCustomizer strictRequestJson() {
		return builder -> builder
				.enable(EnumFeature.FAIL_ON_NUMBERS_FOR_ENUMS)
				.enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES,
						DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
				.disable(DeserializationFeature.ACCEPT_FLOAT_AS_INT)
				.disable(MapperFeature.ALLOW_COERCION_OF_SCALARS)
				.withCoercionConfig(LogicalType.Textual, config -> config
						.setCoercion(CoercionInputShape.Integer, CoercionAction.Fail)
						.setCoercion(CoercionInputShape.Float, CoercionAction.Fail)
						.setCoercion(CoercionInputShape.Boolean, CoercionAction.Fail))
				.withCoercionConfig(LogicalType.Enum, config -> config
						.setCoercion(CoercionInputShape.Integer, CoercionAction.Fail)
						.setCoercion(CoercionInputShape.EmptyString, CoercionAction.Fail));
	}
}
