package com.f5.scholarship_backend.domain.student.service;

import java.sql.SQLException;
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
import com.f5.scholarship_backend.domain.user.repository.UserRepository;
import com.f5.scholarship_backend.global.exception.BusinessException;
import com.f5.scholarship_backend.global.exception.ErrorCode;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@EnableConfigurationProperties(StudentProfileProperties.class)
public class StudentProfileService {
	private static final ZoneId KOREA = ZoneId.of("Asia/Seoul");

	private final StudentProfileRepository profiles;
	private final UserRepository users;
	private final StudentProfileProperties properties;
	private final ResidenceCodeCatalog residenceCodes;
	private final Clock clock;

	public StudentProfileService(StudentProfileRepository profiles, UserRepository users,
			StudentProfileProperties properties,
			ResidenceCodeCatalog residenceCodes, Clock clock) {
		this.profiles = profiles;
		this.users = users;
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
			try {
				var created = profiles.saveAndFlush(StudentProfile.create(
						users.getReferenceById(accountId), data, savedAt));
				return new StudentProfileSaveResult(created.toResponse(), true);
			} catch (DataIntegrityViolationException conflict) {
				if (isDuplicateKeyViolation(conflict)) {
					throw new BusinessException(ErrorCode.VERSION_CONFLICT);
				}
				throw conflict;
			}
		}

		var profile = existing.orElseThrow();
		profile.replace(data, savedAt);
		return new StudentProfileSaveResult(profile.toResponse(), false);
	}

	@Transactional(readOnly = true)
	public Optional<StudentProfileResponse> findForUser(String userId) {
		long accountId = accountId(userId);
		requireStudent(profiles.findAccountRole(accountId));
		return profiles.findById(accountId).map(StudentProfile::toResponse);
	}

	/** 프로필 저장과 같은 계정 행 잠금을 접수 트랜잭션 종료까지 유지한다. */
	@Transactional(propagation = Propagation.MANDATORY)
	public Optional<StudentProfileResponse> findForAnalysisWithAccountLock(long accountId) {
		requireStudent(profiles.lockAccountRole(accountId));
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

	private static boolean isDuplicateKeyViolation(Throwable exception) {
		for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
			if (cause instanceof SQLException sqlException
					&& (sqlException.getErrorCode() == 1062 || "23505".equals(sqlException.getSQLState()))) {
				return true;
			}
		}
		return false;
	}
}
