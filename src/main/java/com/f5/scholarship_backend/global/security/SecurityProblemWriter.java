package com.f5.scholarship_backend.global.security;

import java.io.IOException;
import java.net.URI;
import java.util.List;
import java.util.UUID;

import com.f5.scholarship_backend.global.config.RequestIdFilter;
import com.f5.scholarship_backend.global.exception.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

@Component
public class SecurityProblemWriter {
	private final JsonMapper jsonMapper;

	public SecurityProblemWriter(JsonMapper jsonMapper) {
		this.jsonMapper = jsonMapper;
	}

	public void write(HttpServletRequest request, HttpServletResponse response, ErrorCode errorCode)
			throws IOException {
		HttpStatusCode status = errorCode.getStatus();
		String requestId = RequestIdFilter.getRequestId(request);
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, errorCode.getMessage());
		problem.setType(URI.create("about:blank"));
		problem.setInstance(URI.create("urn:uuid:" + UUID.randomUUID()));
		problem.setProperty("code", errorCode.name());
		problem.setProperty("errors", List.of());
		problem.setProperty("requestId", requestId);

		response.setStatus(status.value());
		response.setHeader(RequestIdFilter.HEADER, requestId);
		response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
		response.setHeader("Cache-Control", "no-store");
		jsonMapper.writeValue(response.getOutputStream(), problem);
	}
}
