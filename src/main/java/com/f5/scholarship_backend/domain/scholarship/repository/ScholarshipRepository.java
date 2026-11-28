package com.f5.scholarship_backend.domain.scholarship.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import com.f5.scholarship_backend.domain.scholarship.entity.Scholarship;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
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

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select s from Scholarship s where s.id = :id")
	Optional<Scholarship> findForDocumentAnalysis(long id);

	@Query("""
			select s.id from Scholarship s
			join ScholarshipVersion v on v.scholarshipId = s.id
			where v.origin = 'CRAWLER' and v.changedByUserId is null
			    and v.version = (select max(latest.version) from ScholarshipVersion latest where latest.scholarshipId = s.id)
			    and not exists (select 1 from CrawlState c where c.scholarshipId = s.id and c.requiresReview = true)
			    and (s.processingStatus = 'PENDING' or
			         (s.processingStatus = 'PROCESSING' and s.documentAnalysisNextAt <= :now
			          and (s.documentAnalysisId is not null or s.statusUpdatedAt <= :cutoff)))
			order by case when s.processingStatus = 'PROCESSING' then 0 else 1 end, s.statusUpdatedAt, s.id
			""")
	List<Long> findDocumentAnalysisCandidates(Instant now, Instant cutoff, Pageable limit);

	interface AnalysisCandidate {
		Long getScholarshipId();
		int getVersion();
		String getProcessingStatus();
		Instant getApplicationEndAt();
	}
}
