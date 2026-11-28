package com.f5.scholarship_backend.domain.document.client;

import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import com.f5.scholarship_backend.domain.document.config.DocumentAnalysisProperties;
import com.f5.scholarship_backend.domain.document.dto.DocumentAnalysisRequest;
import com.f5.scholarship_backend.domain.document.dto.DocumentAnalysisRequest.Document;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import tools.jackson.databind.json.JsonMapper;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DocumentAnalysisClientTest {
	@Test
	void usesTheAgreedSubmissionBodyAndPollsTheReturnedAnalysisId() throws Exception {
		var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		var submitted = new AtomicReference<String>();
		var polled = new AtomicReference<String>();
		server.createContext("/api/ai/document-analyses", exchange -> {
			try (exchange) {
				if ("POST".equals(exchange.getRequestMethod())) {
					submitted.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
					reply(exchange, 202, "{\"analysisId\":\"doc-501\",\"processingStatus\":\"QUEUED\"}");
					return;
				}
				polled.set(exchange.getRequestURI().getPath());
				reply(exchange, 200, """
						{"analysisId":"doc-501","scholarshipId":"1001","scholarshipVersion":2,
						"processingStatus":"COMPLETED","requirementsComplete":true,"requirements":[]}
						""");
			}
		});
		server.start();
		try {
			var json = new JsonMapper();
			var properties = new DocumentAnalysisProperties(true,
					URI.create("http://127.0.0.1:" + server.getAddress().getPort()), "fixture");
			var client = new DocumentAnalysisClient(properties, json);
			var accepted = client.submit(new DocumentAnalysisRequest("1001", 2, null,
					List.of(new Document("3001", "scholarship-originals/example.pdf"))));
			var result = client.read(accepted.analysisId());

			var request = json.readTree(submitted.get());
			assertThat(request.path("scholarshipId").asString()).isEqualTo("1001");
			assertThat(request.path("scholarshipVersion").asInt()).isEqualTo(2);
			assertThat(request.path("sourceText").isNull()).isTrue();
			assertThat(request.path("documents").get(0).path("objectKey").asString())
					.isEqualTo("scholarship-originals/example.pdf");
			assertThat(polled.get()).isEqualTo("/api/ai/document-analyses/doc-501");
			assertThat(result.processingStatus()).isEqualTo("COMPLETED");
			assertThat(result.requirementsComplete()).isTrue();
			assertThatThrownBy(() -> client.read("../invalid")).isInstanceOf(DocumentException.class);
		} finally {
			server.stop(0);
		}
	}

	@ParameterizedTest
	@CsvSource({"400,AI_REQUEST_REJECTED", "422,AI_REQUEST_REJECTED", "201,INVALID_AI_RESPONSE",
			"401,INVALID_AI_RESPONSE", "408,AI_REQUEST_TIMEOUT", "503,DEPENDENCY_UNAVAILABLE"})
	void classifiesUnexpectedStatuses(int status, String code) throws Exception {
		var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		server.createContext("/api/ai/document-analyses", exchange -> {
			try (exchange) {
				reply(exchange, status, "{}");
			}
		});
		server.start();
		try {
			var properties = new DocumentAnalysisProperties(true,
					URI.create("http://127.0.0.1:" + server.getAddress().getPort()), "fixture");
			var client = new DocumentAnalysisClient(properties, new JsonMapper());
			assertThatThrownBy(() -> client.submit(new DocumentAnalysisRequest("1001", 2, "본문", List.of())))
					.isInstanceOfSatisfying(DocumentException.class, error -> assertThat(error.code()).isEqualTo(code));
			assertThatThrownBy(() -> client.read("doc-501"))
					.isInstanceOfSatisfying(DocumentException.class, error -> assertThat(error.code()).isEqualTo(code));
		} finally {
			server.stop(0);
		}
	}

	private static void reply(HttpExchange exchange, int status, String body) throws java.io.IOException {
		var bytes = body.getBytes(StandardCharsets.UTF_8);
		exchange.getResponseHeaders().set("Content-Type", "application/json");
		exchange.sendResponseHeaders(status, bytes.length);
		exchange.getResponseBody().write(bytes);
	}
}
