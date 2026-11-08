package com.f5.scholarship_backend.global;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.f5.scholarship_backend.global.config.RequestIdFilter;
import com.f5.scholarship_backend.global.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.ServletWebRequest;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionLoggingTest {
	@Test
	void logsOriginalFramesAndClassWithoutSensitiveExceptionDetails() {
		Logger logger = (Logger) LoggerFactory.getLogger(GlobalExceptionHandler.class);
		ListAppender<ILoggingEvent> appender = new ListAppender<>();
		appender.start();
		logger.addAppender(appender);
		try {
			var original = new IllegalStateException("secret-message", new RuntimeException("secret-cause"));
			original.addSuppressed(new RuntimeException("secret-suppressed"));
			var frame = new StackTraceElement("example.AnalysisService", "create", "AnalysisService.java", 42);
			original.setStackTrace(new StackTraceElement[] {frame});
			var request = new MockHttpServletRequest();
			String requestId = RequestIdFilter.getRequestId(request);

			var response = new GlobalExceptionHandler()
					.handleUnexpectedException(original, new ServletWebRequest(request));

			assertThat(response.getStatusCode().value()).isEqualTo(500);
			assertThat(appender.list).hasSize(1);
			var event = appender.list.get(0);
			assertThat(event.getFormattedMessage()).contains(requestId, IllegalStateException.class.getName())
					.doesNotContain("secret-message", "secret-cause", "secret-suppressed");
			var logged = event.getThrowableProxy();
			assertThat(logged).isNotNull();
			assertThat(logged.getMessage()).isEqualTo(IllegalStateException.class.getName());
			assertThat(logged.getCause()).isNull();
			assertThat(logged.getSuppressed()).isNullOrEmpty();
			assertThat(logged.getStackTraceElementProxyArray()).hasSize(1);
			assertThat(logged.getStackTraceElementProxyArray()[0].getStackTraceElement()).isEqualTo(frame);
			assertThat(original.getMessage()).isEqualTo("secret-message");
			assertThat(original.getCause().getMessage()).isEqualTo("secret-cause");
		} finally {
			logger.detachAppender(appender);
			appender.stop();
		}
	}
}