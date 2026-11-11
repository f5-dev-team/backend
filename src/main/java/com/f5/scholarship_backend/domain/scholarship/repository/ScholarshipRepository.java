package com.f5.scholarship_backend.domain.scholarship.repository;

import java.util.Optional;
import com.f5.scholarship_backend.domain.scholarship.entity.Scholarship;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface ScholarshipRepository extends JpaRepository<Scholarship, Long> {
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select s from Scholarship s where s.sourceSite='GACHON_KOR' and s.sourcePostKey=:key and s.sourceItemKey='main'")
	Optional<Scholarship> findSourceForUpdate(@Param("key") String key);
}