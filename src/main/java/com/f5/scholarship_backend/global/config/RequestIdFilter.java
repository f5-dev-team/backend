package com.f5.scholarship_backend.global.config;

import java.io.IOException;
import java.util.UUID;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestIdFilter extends OncePerRequestFilter {
	public static final String ATTRIBUTE = RequestIdFilter.class.getName() + ".requestId";
	public static final String HEADER = "X-Request-Id";

	/** 요청 속성의 추적 ID를 반환하고, 없으면 서버에서 생성하여 저장한다. */
	public static String getRequestId(HttpServletRequest request) {
		if (request.getAttribute(ATTRIBUTE) instanceof String requestId) {
			return requestId;
		}
		String requestId = "req-" + UUID.randomUUID();
		request.setAttribute(ATTRIBUTE, requestId);
		return requestId;
	}

	/** 요청 ID를 응답 헤더와 MDC에 설정하고 처리가 끝나면 기존 MDC 상태를 복원한다. */
	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
			FilterChain filterChain) throws ServletException, IOException {
		String previous = MDC.get("requestId");
		String requestId = getRequestId(request);
		response.setHeader(HEADER, requestId);
		MDC.put("requestId", requestId);
		try {
			filterChain.doFilter(request, response);
		} finally {
			if (previous == null) {
				MDC.remove("requestId");
			} else {
				MDC.put("requestId", previous);
			}
		}
	}
}