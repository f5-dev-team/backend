package com.f5.scholarship_backend.global.exception;

import java.net.URI;
import java.util.List;
import java.util.UUID;

import com.f5.scholarship_backend.global.config.RequestIdFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {
	private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

	@ExceptionHandler(BusinessException.class)
	public ResponseEntity<Object> handleBusinessException(BusinessException exception, WebRequest request) {
		ErrorCode code = exception.getErrorCode();
		HttpHeaders headers = new HttpHeaders();
		if (code == ErrorCode.TOKEN_INVALID) {
			headers.set(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
		}
		return problem(code.getStatus(), code.name(), code.getMessage(), List.of(), headers, request);
	}

	@ExceptionHandler(Exception.class)
	public ResponseEntity<Object> handleUnexpectedException(Exception exception, WebRequest request) {
		// 예외 메시지와 입력값에는 개인정보가 포함될 수 있으므로 기록하지 않는다.
		log.error("요청 처리 실패: requestId={}, exceptionType={}",
				requestId(request), exception.getClass().getName());
		ErrorCode code = ErrorCode.INTERNAL_SERVER_ERROR;
		return problem(code.getStatus(), code.name(), code.getMessage(), List.of(), new HttpHeaders(), request);
	}

	@Override
	protected ResponseEntity<Object> handleExceptionInternal(Exception exception, Object body,
			HttpHeaders headers, HttpStatusCode status, WebRequest request) {
		if (request instanceof ServletWebRequest servletRequest
				&& servletRequest.getResponse() != null && servletRequest.getResponse().isCommitted()) {
			return null;
		}
		List<FieldErrorResponse> errors = List.of();
		if (exception instanceof MethodArgumentNotValidException validationException) {
			errors = validationException.getBindingResult().getFieldErrors().stream()
					.map(error -> new FieldErrorResponse(error.getField(), "입력값을 확인해 주세요."))
					.distinct().toList();
		}
		ErrorCode code = switch (status.value()) {
			case 400 -> ErrorCode.VALIDATION_ERROR;
			case 404 -> ErrorCode.RESOURCE_NOT_FOUND;
			case 503 -> ErrorCode.SERVICE_UNAVAILABLE;
			default -> null;
		};
		// 명세에 없는 HTTP 오류도 원래 상태와 Allow 등의 헤더를 보존한다.
		HttpStatus httpStatus = HttpStatus.resolve(status.value());
		String codeName = code != null ? code.name()
				: (httpStatus != null ? httpStatus.name() : "HTTP_ERROR");
		String detail = code != null ? code.getMessage()
				: (status.is5xxServerError() ? "서버에서 오류가 발생했습니다." : "요청 방식이나 형식을 확인해 주세요.");
		return problem(status, codeName, detail, errors, headers, request);
	}

	private ResponseEntity<Object> problem(HttpStatusCode status, String code, String detail,
			List<FieldErrorResponse> errors, HttpHeaders originalHeaders, WebRequest request) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
		problem.setType(URI.create("about:blank"));
		problem.setInstance(URI.create("urn:uuid:" + UUID.randomUUID()));
		problem.setProperty("code", code);
		problem.setProperty("errors", errors);
		problem.setProperty("requestId", requestId(request));
		HttpHeaders headers = new HttpHeaders();
		headers.putAll(originalHeaders);
		headers.setContentType(MediaType.APPLICATION_PROBLEM_JSON);
		headers.setCacheControl("no-store");
		return new ResponseEntity<>(problem, headers, status);
	}

	private String requestId(WebRequest request) {
		return RequestIdFilter.getRequestId(((ServletWebRequest) request).getRequest());
	}
}