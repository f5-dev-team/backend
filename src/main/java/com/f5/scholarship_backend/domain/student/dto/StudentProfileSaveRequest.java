package com.f5.scholarship_backend.domain.student.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.f5.scholarship_backend.domain.student.enums.EnrollmentStatus;
import com.f5.scholarship_backend.domain.student.enums.Gender;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

@Schema(description = "본인 학생 정보 전체 교체 요청. 최상위 키는 모두 전달하며 미입력 값은 null을 사용합니다.")
public record StudentProfileSaveRequest(
		@JsonProperty(required = true) @Pattern(regexp = "[A-Za-z0-9_-]{1,100}") String departmentCode,
		@JsonProperty(required = true) @Min(1) @Max(65535) Integer grade,
		@JsonProperty(required = true) EnrollmentStatus enrollmentStatus,
		@JsonProperty(required = true) @DecimalMin("0") @DecimalMax("4.50")
		@Digits(integer = 1, fraction = 2) BigDecimal cumulativeGpa,
		@JsonProperty(required = true) @DecimalMin("0") @DecimalMax("4.50")
		@Digits(integer = 1, fraction = 2) BigDecimal previousSemesterGpa,
		@JsonProperty(required = true) @Valid IncomeAssessment incomeAssessment,
		@JsonProperty(required = true) SpecialEligibility specialEligibility,
		@JsonProperty(required = true) @Min(0) Long volunteerMinutes,
		@JsonProperty(required = true) LocalDate birthDate,
		@JsonProperty(required = true) @Valid Residence residence,
		@JsonProperty(required = true) Gender gender
) {
	public enum IncomeStatus { CALCULATED, NOT_CALCULATED, UNKNOWN }
	public enum IncomeScale { TEN_BRACKETS, FIVE_GROUPS }
	public enum SpecialEligibility { BASIC_LIVELIHOOD, NEAR_POOR, NONE, UNKNOWN }
	public enum ResidenceStatus { REGISTERED, NOT_REGISTERED, UNKNOWN }

	public record IncomeAssessment(
			@NotNull IncomeStatus status,
			@Min(1) Integer year,
			@Min(1) @Max(2) Integer semester,
			IncomeScale scale,
			String bracket
	) {
		@JsonIgnore
		@AssertTrue(message = "산정 학기, 구간 체계와 구간이 서로 일치해야 합니다.")
		public boolean isConsistent() {
			if ((year == null) != (semester == null)) {
				return false;
			}
			if (status != IncomeStatus.CALCULATED) {
				return scale == null && bracket == null;
			}
			if (year == null || scale == null || bracket == null) {
				return false;
			}
			return year < 2027
					? scale == IncomeScale.TEN_BRACKETS && bracket.matches("[1-9]|10")
					: scale == IncomeScale.FIVE_GROUPS && bracket.matches("[가나다라마]");
		}
	}

	public record Residence(
			@NotNull ResidenceStatus status,
			@Pattern(regexp = "[0-9]{2}") String sidoCode,
			@Pattern(regexp = "[0-9]{5}") String sigunguCode
	) {
		@JsonIgnore
		@AssertTrue(message = "주소 있음일 때만 시·도와 해당 시·군·구 코드를 함께 입력해 주세요.")
		public boolean isConsistent() {
			return status == ResidenceStatus.REGISTERED
					? sidoCode != null && sigunguCode != null && sigunguCode.startsWith(sidoCode)
					: sidoCode == null && sigunguCode == null;
		}
	}
}
