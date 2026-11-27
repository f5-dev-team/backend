package com.f5.scholarship_backend.global.security;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "cors")
public record CorsProperties(List<String> allowedOrigins) {
	public CorsProperties {
		allowedOrigins = allowedOrigins == null ? List.of() : allowedOrigins.stream()
				.filter(origin -> origin != null && !origin.isBlank())
				.map(String::trim)
				.toList();
		if (allowedOrigins.stream().anyMatch(origin -> origin.contains("*"))) {
			throw new IllegalArgumentException("CORS 와일드카드 출처는 허용되지 않습니다.");
		}
	}
}
