package com.f5.scholarship_backend.domain.scholarship.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import com.f5.scholarship_backend.domain.scholarship.entity.Scholarship;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface ScholarshipRepository extends JpaRepository<Scholarship, Long> {
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select s from Scholarship s where s.sourceSite='GACHON_KOR' and s.sourcePostKey=:key and s.sourceItemKey='main'")
	Optional<Scholarship> findSourceForUpdate(@Param("key") String key);

	@Query("""
			select s.id as scholarshipId, s.processingStatus as processingStatus,
			       v.version as version, v.applicationEndAt as applicationEndAt
			from Scholarship s
			join ScholarshipVersion v on v.scholarshipId = s.id
			where v.version = (select max(latest.version) from ScholarshipVersion latest
			                   where latest.scholarshipId = s.id)
			""")
	List<AnalysisCandidate> findAnalysisCandidates();

	interface AnalysisCandidate {
		Long getScholarshipId();
		int getVersion();
		String getProcessingStatus();
		Instant getApplicationEndAt();
	}
}
