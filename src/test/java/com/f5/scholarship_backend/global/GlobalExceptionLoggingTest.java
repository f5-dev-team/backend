package com.f5.scholarship_backend.global;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.f5.scholarship_backend.global.config.RequestIdFilter;
import com.f5.scholarship_backend.global.exception.GlobalExceptionHandler;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.LoggerFactory;
import org.springframework.http.ProblemDetail;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.ServletWebRequest;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionLoggingTest {
	/** 애플리케이션 위치 우선 선택과 외부 위치 대체 및 빈 스택의 안전한 로그를 검증한다. */
	@ParameterizedTest
	@ValueSource(ints = {0, 1, 2})
	void logsSafeOriginWithoutThrowableOrSensitiveDetails(int scenario) {
		Logger logger = (Logger) LoggerFactory.getLogger(GlobalExceptionHandler.class);
		ListAppender<ILoggingEvent> appender = new ListAppender<>();
		appender.start();
		logger.addAppender(appender);
		try {
			var original = new IllegalStateException("secret-message", new RuntimeException("secret-cause"));
			original.addSuppressed(new RuntimeException("secret-suppressed"));
			var external = new StackTraceElement("example.Library", "run", "Library.java", 7);
			var application = new StackTraceElement(
					"com.f5.scholarship_backend.domain.analysis.service.AnalysisService", "create", "AnalysisService.java", 42);
			original.setStackTrace(switch (scenario) {
				case 0 -> new StackTraceElement[] {external, application};
				case 1 -> new StackTraceElement[] {external};
				default -> new StackTraceElement[0];
			});
			String expectedOrigin = switch (scenario) {
				case 0 -> application.getClassName() + ".create:42";
				case 1 -> "example.Library.run:7";
				default -> "unknown";
			};
			var request = new MockHttpServletRequest();
			String requestId = RequestIdFilter.getRequestId(request);
			var response = new GlobalExceptionHandler()
					.handleUnexpectedException(original, new ServletWebRequest(request));

			assertThat(response.getStatusCode().value()).isEqualTo(500);
			var problem = (ProblemDetail) response.getBody();
			assertThat(problem.getDetail()).isEqualTo("서버에서 오류가 발생했습니다.");
			assertThat(appender.list).hasSize(1);
			var event = appender.list.get(0);
			assertThat(event.getFormattedMessage())
					.contains(requestId, IllegalStateException.class.getName(), "origin=" + expectedOrigin)
					.doesNotContain("secret-message", "secret-cause", "secret-suppressed");
			assertThat(event.getThrowableProxy()).isNull();
			assertThat(event.getArgumentArray()).allMatch(argument -> !(argument instanceof Throwable));
			assertThat(original.getMessage()).isEqualTo("secret-message");
			assertThat(original.getCause().getMessage()).isEqualTo("secret-cause");
		} finally {
			logger.detachAppender(appender);
			appender.stop();
		}
	}
}