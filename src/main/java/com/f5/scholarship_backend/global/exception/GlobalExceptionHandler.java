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

	/** 업무 오류의 상태와 안전한 안내 문구를 Problem Details 응답으로 변환한다. */
	@ExceptionHandler(BusinessException.class)
	public ResponseEntity<Object> handleBusinessException(BusinessException exception, WebRequest request) {
		ErrorCode code = exception.getErrorCode();
		HttpHeaders headers = new HttpHeaders();
		if (code == ErrorCode.TOKEN_INVALID) {
			headers.set(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
		}
		return problem(code.getStatus(), code.name(), code.getMessage(), List.of(), headers, request);
	}

	/** 내부 오류의 안전한 진단 정보를 기록하고 상세 내용을 숨긴 500 응답을 반환한다. */
	@ExceptionHandler(Exception.class)
	public ResponseEntity<Object> handleUnexpectedException(Exception exception, WebRequest request) {
		// 원본 메시지·원인·suppressed 예외는 복사하지 않고 클래스 이름과 발생 위치만 기록한다.
		Throwable sanitized = new Throwable(exception.getClass().getName(), null);
		sanitized.setStackTrace(exception.getStackTrace());
		log.error("요청 처리 실패: requestId={}, exceptionType={}",
				requestId(request), exception.getClass().getName(), sanitized);
		ErrorCode code = ErrorCode.INTERNAL_SERVER_ERROR;
		return problem(code.getStatus(), code.name(), code.getMessage(), List.of(), new HttpHeaders(), request);
	}

	/** Spring MVC 오류의 HTTP 상태와 헤더를 보존하여 공통 오류 형식으로 변환한다. */
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

	/** 표준 필드와 오류 코드·필드 오류·요청 ID를 포함한 캐시 금지 오류 응답을 생성한다. */
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

	/** 현재 HTTP 요청에 부여된 서버 생성 추적 ID를 조회한다. */
	private String requestId(WebRequest request) {
		return RequestIdFilter.getRequestId(((ServletWebRequest) request).getRequest());
	}
}