package com.f5.scholarship_backend.domain.document.client;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.regex.Pattern;
import com.f5.scholarship_backend.domain.document.config.DocumentAnalysisProperties;
import com.f5.scholarship_backend.domain.document.dto.DocumentAnalysisRequest;
import com.f5.scholarship_backend.domain.document.dto.DocumentAnalysisResult;
import com.f5.scholarship_backend.domain.document.dto.DocumentAnalysisResult.Accepted;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

@Component
public class DocumentAnalysisClient {
	public static final String INVALID_RESPONSE = "INVALID_AI_RESPONSE";
	public static final String EXTERNAL_FAILURE = "DEPENDENCY_UNAVAILABLE";
	public static final String TIMEOUT = "AI_REQUEST_TIMEOUT";
	private static final String ANALYSES_PATH = "/api/ai/document-analyses";
	private static final Pattern ANALYSIS_ID = Pattern.compile("[a-zA-Z0-9_-]{1,128}");
	private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
	private final DocumentAnalysisProperties properties;
	private final JsonMapper json;

	public DocumentAnalysisClient(DocumentAnalysisProperties properties, JsonMapper json) {
		this.properties = properties;
		this.json = json;
	}

	public Accepted submit(DocumentAnalysisRequest request) {
		return exchange(builder(ANALYSES_PATH).header("Content-Type", "application/json")
				.POST(HttpRequest.BodyPublishers.ofByteArray(json.writeValueAsBytes(request))).build(),
				202, Accepted.class);
	}

	public DocumentAnalysisResult read(String analysisId) {
		if (!validId(analysisId)) {
			throw new DocumentException(INVALID_RESPONSE);
		}
		return exchange(builder(ANALYSES_PATH + "/" + analysisId).GET().build(), 200, DocumentAnalysisResult.class);
	}

	public static boolean validId(String analysisId) {
		return analysisId != null && ANALYSIS_ID.matcher(analysisId).matches();
	}

	private HttpRequest.Builder builder(String path) {
		return HttpRequest.newBuilder(properties.baseUrl().resolve(path)).timeout(Duration.ofSeconds(5))
				.header("Accept", "application/json");
	}

	private <T> T exchange(HttpRequest request, int expectedStatus, Class<T> responseType) {
		try {
			var response = http.send(request, HttpResponse.BodyHandlers.ofByteArray());
			if (response.statusCode() != expectedStatus) {
				throw new DocumentException(EXTERNAL_FAILURE);
			}
			var result = json.readValue(response.body(), responseType);
			if (result == null) {
				throw new DocumentException(INVALID_RESPONSE);
			}
			return result;
		} catch (HttpTimeoutException timeout) {
			throw new DocumentException(TIMEOUT);
		} catch (InterruptedException interrupted) {
			Thread.currentThread().interrupt();
			throw new DocumentException(EXTERNAL_FAILURE);
		} catch (IOException failure) {
			throw new DocumentException(EXTERNAL_FAILURE);
		} catch (JacksonException malformed) {
			throw new DocumentException(INVALID_RESPONSE);
		}
	}
}
