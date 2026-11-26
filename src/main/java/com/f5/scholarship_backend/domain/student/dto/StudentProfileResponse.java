package com.f5.scholarship_backend.domain.student.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import com.f5.scholarship_backend.domain.student.dto.StudentProfileSaveRequest.IncomeAssessment;
import com.f5.scholarship_backend.domain.student.dto.StudentProfileSaveRequest.Residence;
import com.f5.scholarship_backend.domain.student.dto.StudentProfileSaveRequest.SpecialEligibility;
import com.f5.scholarship_backend.domain.student.enums.EnrollmentStatus;
import com.f5.scholarship_backend.domain.student.enums.Gender;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "저장된 학생 정보와 서버에서 생성한 버전 및 갱신 시각")
public record StudentProfileResponse(
		@Schema(nullable = true) String departmentCode,
		@Schema(nullable = true) Integer grade,
		@Schema(nullable = true) EnrollmentStatus enrollmentStatus,
		@Schema(nullable = true) BigDecimal cumulativeGpa,
		@Schema(nullable = true) BigDecimal previousSemesterGpa,
		@Schema(nullable = true) IncomeAssessment incomeAssessment,
		@Schema(nullable = true) SpecialEligibility specialEligibility,
		@Schema(nullable = true) Long volunteerMinutes,
		@Schema(nullable = true) LocalDate birthDate,
		@Schema(nullable = true) Residence residence,
		@Schema(nullable = true) Gender gender,
		@Schema(description = "최초 1, 실제 저장 내용 변경 시 증가", example = "1") long version,
		@Schema(description = "최종 저장 일시") OffsetDateTime updatedAt
) {
}
