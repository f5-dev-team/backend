package com.f5.scholarship_backend.domain.student.entity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import com.f5.scholarship_backend.domain.student.dto.StudentProfileResponse;
import com.f5.scholarship_backend.domain.student.dto.StudentProfileSaveRequest;
import com.f5.scholarship_backend.domain.student.dto.StudentProfileSaveRequest.IncomeAssessment;
import com.f5.scholarship_backend.domain.student.dto.StudentProfileSaveRequest.IncomeScale;
import com.f5.scholarship_backend.domain.student.dto.StudentProfileSaveRequest.IncomeStatus;
import com.f5.scholarship_backend.domain.student.dto.StudentProfileSaveRequest.Residence;
import com.f5.scholarship_backend.domain.student.dto.StudentProfileSaveRequest.ResidenceStatus;
import com.f5.scholarship_backend.domain.student.dto.StudentProfileSaveRequest.SpecialEligibility;
import com.f5.scholarship_backend.domain.student.enums.EnrollmentStatus;
import com.f5.scholarship_backend.domain.student.enums.Gender;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;

@Embeddable
public record StudentProfileData(
		@Column(name = "department_code", length = 100) String departmentCode,
		Integer grade,
		@Enumerated(EnumType.STRING) @Column(length = 20) EnrollmentStatus enrollmentStatus,
		@Column(precision = 5, scale = 2) BigDecimal cumulativeGpa,
		@Column(precision = 5, scale = 2) BigDecimal previousSemesterGpa,
		@Enumerated(EnumType.STRING) @Column(length = 20) IncomeStatus incomeStatus,
		Integer incomeYear,
		Integer incomeSemester,
		@Enumerated(EnumType.STRING) @Column(length = 20) IncomeScale incomeScale,
		@Column(length = 2) String incomeBracketCode,
		@Enumerated(EnumType.STRING) @Column(length = 20) SpecialEligibility specialEligibility,
		Long volunteerMinutes,
		LocalDate birthDate,
		@Enumerated(EnumType.STRING) @Column(length = 20) ResidenceStatus residenceStatus,
		@Column(length = 2) String sidoCode,
		@Column(length = 5) String sigunguCode,
		@Enumerated(EnumType.STRING) @Column(length = 16) Gender gender
) {
	static final StudentProfileData EMPTY = from(new StudentProfileSaveRequest(
			null, null, null, null, null, null, null, null, null, null, null));

	public static StudentProfileData from(StudentProfileSaveRequest request) {
		IncomeAssessment income = request.incomeAssessment();
		Residence residence = request.residence();
		return new StudentProfileData(
				request.departmentCode(), request.grade(), request.enrollmentStatus(),
				normalizeGpa(request.cumulativeGpa()), normalizeGpa(request.previousSemesterGpa()),
				income == null ? null : income.status(), income == null ? null : income.year(),
				income == null ? null : income.semester(), income == null ? null : income.scale(),
				income == null ? null : income.bracket(), request.specialEligibility(), request.volunteerMinutes(),
				request.birthDate(), residence == null ? null : residence.status(),
				residence == null ? null : residence.sidoCode(), residence == null ? null : residence.sigunguCode(),
				request.gender());
	}

	StudentProfileResponse toResponse(long version, OffsetDateTime updatedAt) {
		IncomeAssessment income = incomeStatus == null ? null
				: new IncomeAssessment(incomeStatus, incomeYear, incomeSemester, incomeScale, incomeBracketCode);
		Residence residence = residenceStatus == null ? null : new Residence(residenceStatus, sidoCode, sigunguCode);
		return new StudentProfileResponse(departmentCode, grade, enrollmentStatus, cumulativeGpa,
				previousSemesterGpa, income, specialEligibility, volunteerMinutes, birthDate, residence, gender,
				version, updatedAt);
	}

	private static BigDecimal normalizeGpa(BigDecimal value) {
		return value == null ? null : value.setScale(2);
	}
}
