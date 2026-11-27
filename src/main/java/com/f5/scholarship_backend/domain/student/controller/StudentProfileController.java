package com.f5.scholarship_backend.domain.student.controller;

import java.security.Principal;
import com.f5.scholarship_backend.domain.student.controller.docs.StudentProfileControllerDocs;
import com.f5.scholarship_backend.domain.student.dto.StudentProfileSaveRequest;
import com.f5.scholarship_backend.domain.student.dto.StudentProfileResponse;
import com.f5.scholarship_backend.domain.student.service.StudentProfileService;
import com.f5.scholarship_backend.global.config.RequestIdFilter;
import com.f5.scholarship_backend.global.exception.BusinessException;
import com.f5.scholarship_backend.global.exception.ErrorCode;
import com.f5.scholarship_backend.global.response.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users/me/student-profile")
public class StudentProfileController implements StudentProfileControllerDocs {
	private final StudentProfileService studentProfileService;

	/** 학생 정보 저장 Service를 주입한다. */
	public StudentProfileController(StudentProfileService studentProfileService) {
		this.studentProfileService = studentProfileService;
	}

	/** 본인 학생 정보를 전체 저장하고 최초 생성 여부에 따라 201 또는 200 응답을 구성한다. */
	@Override
	@PutMapping
	public ResponseEntity<ApiResponse<StudentProfileResponse>> saveStudentProfile(
			@Valid @RequestBody StudentProfileSaveRequest request,
			Principal principal, HttpServletRequest servletRequest) {
		String userId = authenticatedUserId(principal, servletRequest);
		var result = studentProfileService.save(userId, request);
		return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK)
				.cacheControl(CacheControl.noStore())
				.body(ApiResponse.of(result.profile(), RequestIdFilter.getRequestId(servletRequest)));
	}

	@Override
	@GetMapping
	public ResponseEntity<ApiResponse<StudentProfileResponse>> getStudentProfile(
			Principal principal, HttpServletRequest servletRequest) {
		String userId = authenticatedUserId(principal, servletRequest);
		var profile = studentProfileService.findForUser(userId)
				.orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
		return ResponseEntity.ok().cacheControl(CacheControl.noStore())
				.body(ApiResponse.of(profile, RequestIdFilter.getRequestId(servletRequest)));
	}

	private String authenticatedUserId(Principal principal, HttpServletRequest servletRequest) {
		if (principal == null || principal.getName() == null || principal.getName().isBlank()) {
			throw new BusinessException(ErrorCode.TOKEN_INVALID);
		}
		if (!servletRequest.getParameterMap().isEmpty()) {
			throw new BusinessException(ErrorCode.VALIDATION_ERROR);
		}
		return principal.getName();
	}
}
