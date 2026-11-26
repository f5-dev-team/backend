package com.f5.scholarship_backend.global.security;

import java.io.IOException;

import com.f5.scholarship_backend.global.exception.ErrorCode;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

@Component
public class JwtAuthenticationEntryPoint implements AuthenticationEntryPoint {
	private final SecurityProblemWriter problemWriter;

	public JwtAuthenticationEntryPoint(SecurityProblemWriter problemWriter) {
		this.problemWriter = problemWriter;
	}

	@Override
	public void commence(HttpServletRequest request, HttpServletResponse response,
			AuthenticationException exception) throws IOException, ServletException {
		response.setHeader("WWW-Authenticate", "Bearer");
		problemWriter.write(request, response, ErrorCode.TOKEN_INVALID);
	}
}
