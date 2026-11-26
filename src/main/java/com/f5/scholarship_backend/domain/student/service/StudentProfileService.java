package com.f5.scholarship_backend.domain.student.service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;
import com.f5.scholarship_backend.domain.student.config.ResidenceCodeCatalog;
import com.f5.scholarship_backend.domain.student.config.StudentProfileProperties;
import com.f5.scholarship_backend.domain.student.dto.StudentProfileResponse;
import com.f5.scholarship_backend.domain.student.dto.StudentProfileSaveRequest;
import com.f5.scholarship_backend.domain.student.dto.StudentProfileSaveRequest.ResidenceStatus;
import com.f5.scholarship_backend.domain.student.entity.StudentProfile;
import com.f5.scholarship_backend.domain.student.entity.StudentProfileData;
import com.f5.scholarship_backend.domain.student.repository.StudentProfileRepository;
import com.f5.scholarship_backend.global.exception.BusinessException;
import com.f5.scholarship_backend.global.exception.ErrorCode;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@EnableConfigurationProperties(StudentProfileProperties.class)
public class StudentProfileService {
	private static final ZoneId KOREA = ZoneId.of("Asia/Seoul");

	private final StudentProfileRepository profiles;
	private final StudentProfileProperties properties;
	private final ResidenceCodeCatalog residenceCodes;
	private final Clock clock;

	public StudentProfileService(StudentProfileRepository profiles, StudentProfileProperties properties,
			ResidenceCodeCatalog residenceCodes, Clock clock) {
		this.profiles = profiles;
		this.properties = properties;
		this.residenceCodes = residenceCodes;
		this.clock = clock;
	}

	@Transactional
	public StudentProfileSaveResult save(String userId, StudentProfileSaveRequest request) {
		long accountId = accountId(userId);
		requireStudent(profiles.lockAccountRole(accountId));
		validateProfile(request);

		StudentProfileData data = StudentProfileData.from(request);
		var savedAt = clock.instant();
		var existing = profiles.findById(accountId);
		if (existing.isEmpty()) {
			var created = profiles.save(new StudentProfile(accountId, data, savedAt));
			return new StudentProfileSaveResult(created.toResponse(), true);
		}

		var profile = existing.orElseThrow();
		profile.replace(data, savedAt);
		return new StudentProfileSaveResult(profile.toResponse(), false);
	}

	@Transactional(readOnly = true)
	public Optional<StudentProfileResponse> findForAnalysis(String userId) {
		long accountId = accountId(userId);
		requireStudent(profiles.findAccountRole(accountId));
		return profiles.findById(accountId).map(StudentProfile::toResponse);
	}

	private void validateProfile(StudentProfileSaveRequest request) {
		if (request.birthDate() != null && request.birthDate().isAfter(LocalDate.now(clock.withZone(KOREA)))) {
			throw new BusinessException(ErrorCode.VALIDATION_ERROR);
		}

		validateDepartmentAndGrade(request.departmentCode(), request.grade());
		var residence = request.residence();
		if (residence != null && residence.status() == ResidenceStatus.REGISTERED
				&& !residenceCodes.contains(residence.sidoCode(), residence.sigunguCode())) {
			throw new BusinessException(ErrorCode.VALIDATION_ERROR);
		}
	}

	private void validateDepartmentAndGrade(String departmentCode, Integer grade) {
		if (departmentCode == null && grade == null) {
			return;
		}

		var catalog = properties.departmentMaxGrades();
		if (catalog.isEmpty()) {
			throw new BusinessException(ErrorCode.SERVICE_UNAVAILABLE);
		}

		Integer maxGrade = departmentCode == null
				? java.util.Collections.max(catalog.values())
				: catalog.get(departmentCode);
		if (maxGrade == null || (grade != null && grade > maxGrade)) {
			throw new BusinessException(ErrorCode.VALIDATION_ERROR);
		}
	}

	private static long accountId(String userId) {
		try {
			long accountId = Long.parseLong(userId);
			if (accountId > 0) {
				return accountId;
			}
		} catch (NumberFormatException invalidId) {
			throw new BusinessException(ErrorCode.TOKEN_INVALID);
		}
		throw new BusinessException(ErrorCode.TOKEN_INVALID);
	}

	private static void requireStudent(Optional<String> accountRole) {
		String role = accountRole.orElseThrow(() -> new BusinessException(ErrorCode.TOKEN_INVALID));
		if (!"USER".equals(role)) {
			throw new BusinessException(ErrorCode.FORBIDDEN);
		}
	}
}
