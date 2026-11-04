package com.f5.scholarship_backend.domain.student.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import com.f5.scholarship_backend.domain.student.enums.EnrollmentStatus;
import com.f5.scholarship_backend.domain.student.enums.Gender;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "저장된 학생 정보와 서버에서 생성한 버전 및 갱신 시각")
public record StudentProfileResponse(
		@Schema(nullable = true) String department,
		@Schema(nullable = true) Integer grade,
		@Schema(nullable = true) EnrollmentStatus enrollmentStatus,
		@Schema(nullable = true) BigDecimal cumulativeGpa,
		@Schema(nullable = true) BigDecimal previousSemesterGpa,
		@Schema(nullable = true) Integer incomeBracket,
		@Schema(nullable = true) BigDecimal volunteerHours,
		@Schema(nullable = true) Integer age,
		@Schema(nullable = true) String residenceAddress,
		@Schema(nullable = true) Gender gender,
		@Schema(description = "최초 1, 실제 저장 내용 변경 시 증가", example = "1") long version,
		@Schema(description = "최종 저장 일시") OffsetDateTime updatedAt
) {
}
