package com.f5.scholarship_backend.domain.student.dto;

import java.math.BigDecimal;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.f5.scholarship_backend.domain.student.enums.EnrollmentStatus;
import com.f5.scholarship_backend.domain.student.enums.Gender;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;

@Schema(description = "10개 필드를 모두 전달하는 전체 교체 요청. 미확인 값은 null을 전달합니다.")
public record StudentProfileSaveRequest(
		@JsonProperty(required = true)
		@Schema(description = "학과", example = "컴퓨터공학과", nullable = true)
		@Pattern(regexp = "(?s).*\\S.*", message = "학과는 공백만 입력할 수 없습니다.")
		String department,
		@JsonProperty(required = true)
		@Schema(description = "학년", example = "3", nullable = true)
		@Min(value = 1, message = "학년은 1 이상이어야 합니다.")
		Integer grade,
		@JsonProperty(required = true)
		@Schema(description = "학적 상태", nullable = true)
		EnrollmentStatus enrollmentStatus,
		@JsonProperty(required = true)
		@Schema(description = "전체 학점. 만점 기준과 환산 정책은 미확정입니다.", nullable = true)
		@DecimalMin(value = "0", message = "전체 학점은 0 이상이어야 합니다.")
		BigDecimal cumulativeGpa,
		@JsonProperty(required = true)
		@Schema(description = "직전 학기 학점", nullable = true)
		@DecimalMin(value = "0", message = "직전 학기 학점은 0 이상이어야 합니다.")
		BigDecimal previousSemesterGpa,
		@JsonProperty(required = true)
		@Schema(description = "소득구간. 상한과 특수 구간 표현은 미확정입니다.", nullable = true)
		@Min(value = 0, message = "소득구간은 0 이상이어야 합니다.")
		Integer incomeBracket,
		@JsonProperty(required = true)
		@Schema(description = "봉사시간", nullable = true)
		@DecimalMin(value = "0", message = "봉사시간은 0 이상이어야 합니다.")
		BigDecimal volunteerHours,
		@JsonProperty(required = true)
		@Schema(description = "나이. 나이 산정 기준은 미확정입니다.", nullable = true)
		@Min(value = 0, message = "나이는 0 이상이어야 합니다.")
		Integer age,
		@JsonProperty(required = true)
		@Schema(description = "1년 이상 주민등록된 주소지. 미확인이면 null", nullable = true)
		String residenceAddress,
		@JsonProperty(required = true)
		@Schema(description = "성별. 미제공이면 null", nullable = true)
		Gender gender
) {
}
