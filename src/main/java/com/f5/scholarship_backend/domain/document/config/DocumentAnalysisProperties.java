package com.f5.scholarship_backend.domain.document.config;

import java.net.URI;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("document-analysis")
public record DocumentAnalysisProperties(boolean enabled, URI baseUrl, String sourceBucket) {
	public DocumentAnalysisProperties {
		if (baseUrl == null || baseUrl.getHost() == null || baseUrl.getUserInfo() != null
				|| baseUrl.getQuery() != null || baseUrl.getFragment() != null
				|| !("http".equalsIgnoreCase(baseUrl.getScheme()) || "https".equalsIgnoreCase(baseUrl.getScheme()))) {
			throw new IllegalArgumentException("FastAPI base URL must be an HTTP(S) server address");
		}
	}
}
