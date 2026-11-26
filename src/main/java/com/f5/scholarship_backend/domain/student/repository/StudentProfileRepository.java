package com.f5.scholarship_backend.domain.student.repository;

import java.util.Optional;
import com.f5.scholarship_backend.domain.student.entity.StudentProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface StudentProfileRepository extends JpaRepository<StudentProfile, Long> {
	@Query(value = "SELECT role FROM users WHERE user_id = :userId", nativeQuery = true)
	Optional<String> findAccountRole(long userId);

	// Lock the account row even when there is no profile yet, to serialize concurrent first saves.
	@Query(value = "SELECT role FROM users WHERE user_id = :userId FOR UPDATE", nativeQuery = true)
	Optional<String> lockAccountRole(long userId);
}
