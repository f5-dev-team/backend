package com.f5.scholarship_backend.domain.scholarship.repository;

import java.util.Optional;
import com.f5.scholarship_backend.domain.scholarship.entity.ScholarshipVersion;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface ScholarshipVersionRepository extends JpaRepository<ScholarshipVersion, ScholarshipVersion.Key> {
	Optional<ScholarshipVersion> findFirstByScholarshipIdOrderByVersionDesc(Long scholarshipId);
	@Query("select count(v) from ScholarshipVersion v where v.scholarshipId=:id and v.version>:baseline and (v.changedByUserId is not null or v.origin not in ('CRAWLER','AI'))")
	long countProtectedSince(@Param("id") Long id, @Param("baseline") int baseline);
}