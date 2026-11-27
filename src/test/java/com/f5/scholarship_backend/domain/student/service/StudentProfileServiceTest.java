package com.f5.scholarship_backend.domain.student.service;

import java.time.Clock;
import java.util.Map;
import java.util.Optional;
import com.f5.scholarship_backend.domain.student.config.ResidenceCodeCatalog;
import com.f5.scholarship_backend.domain.student.config.StudentProfileProperties;
import com.f5.scholarship_backend.domain.student.dto.StudentProfileSaveRequest;
import com.f5.scholarship_backend.domain.student.repository.StudentProfileRepository;
import com.f5.scholarship_backend.domain.user.repository.UserRepository;
import com.f5.scholarship_backend.global.exception.BusinessException;
import com.f5.scholarship_backend.global.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class StudentProfileServiceTest {
	@Test
	void missingDepartmentCatalogDoesNotAllowUnvalidatedDepartment() throws Exception {
		var repository = mock(StudentProfileRepository.class);
		var users = mock(UserRepository.class);
		when(repository.lockAccountRole(101L)).thenReturn(Optional.of("USER"));
		var service = new StudentProfileService(repository, users, new StudentProfileProperties(Map.of()),
				new ResidenceCodeCatalog(), Clock.systemUTC());
		var request = new StudentProfileSaveRequest("UNKNOWN_DEPARTMENT", 3, null, null, null, null,
				null, null, null, null, null);

		assertThatThrownBy(() -> service.save("101", request))
				.isInstanceOfSatisfying(BusinessException.class, exception ->
						org.assertj.core.api.Assertions.assertThat(exception.getErrorCode())
								.isEqualTo(ErrorCode.SERVICE_UNAVAILABLE));
		verify(repository).lockAccountRole(101L);
		verifyNoMoreInteractions(repository);
	}
}
