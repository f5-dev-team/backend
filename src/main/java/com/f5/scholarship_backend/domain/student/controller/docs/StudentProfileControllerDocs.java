package com.f5.scholarship_backend.domain.student.controller.docs;

import java.security.Principal;
import com.f5.scholarship_backend.domain.student.dto.StudentProfileSaveRequest;
import com.f5.scholarship_backend.domain.student.dto.StudentProfileResponse;
import com.f5.scholarship_backend.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;

@Tag(name = "학생 정보", description = "본인의 학생 정보 관리")
public interface StudentProfileControllerDocs {
	/** 학생 정보 전체 저장 API의 요청·응답 및 인증 요구를 문서화한다. */
	@Operation(operationId = "saveStudentProfile", summary = "학생 정보 저장·전체 교체",
			description = "10개 필드를 모두 전달하며 미확인 값은 null입니다. 최초 저장은 201, 기존 정보 교체는 200입니다. "
					+ "동일 값 재저장 시 버전과 갱신 시각을 유지해야 합니다. 현재 DB 및 인증 연동 전 뼈대 단계입니다.")
	@SecurityRequirement(name = "bearerAuth")
	@ApiResponses({
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "기존 정보 교체", useReturnTypeSchema = true),
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "최초 저장", useReturnTypeSchema = true)
	})
	ResponseEntity<ApiResponse<StudentProfileResponse>> saveStudentProfile(
			StudentProfileSaveRequest request, @Parameter(hidden = true) Principal principal,
			@Parameter(hidden = true) HttpServletRequest servletRequest);
}
