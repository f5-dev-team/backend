package com.f5.scholarship_backend.domain.analysis.repository;

import java.time.Instant;
import java.util.Optional;
import com.f5.scholarship_backend.domain.analysis.entity.AnalysisJob;
import com.f5.scholarship_backend.domain.analysis.enums.AnalysisStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface AnalysisJobRepository extends JpaRepository<AnalysisJob, Long> {
	Optional<AnalysisJob> findByIdAndUserId(Long id, Long userId);

	Optional<QueuedJob> findFirstByStatusOrderByCreatedAtAscIdAsc(AnalysisStatus status);

	interface QueuedJob {
		Long getId();
		Instant getCreatedAt();
	}

	@Modifying
	@Query("""
			update AnalysisJob j set j.status = com.f5.scholarship_backend.domain.analysis.enums.AnalysisStatus.RUNNING, j.startedAt = :startedAt
			where j.id = :id and j.status = com.f5.scholarship_backend.domain.analysis.enums.AnalysisStatus.QUEUED
			""")
	int claimQueued(long id, Instant startedAt);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select j from AnalysisJob j where j.id = :id")
	Optional<AnalysisJob> findForExecution(long id);

	@Modifying
	@Query("""
			update AnalysisJob j
			set j.status = com.f5.scholarship_backend.domain.analysis.enums.AnalysisStatus.FAILED, j.failureCode = :code, j.failureMessage = :message,
			    j.completedAt = case when j.startedAt > :now then j.startedAt else :now end
			where j.id = :id and j.status = com.f5.scholarship_backend.domain.analysis.enums.AnalysisStatus.RUNNING
			""")
	int failRunning(long id, Instant now, String code, String message);

	@Modifying
	@Query("""
			update AnalysisJob j
			set j.status = com.f5.scholarship_backend.domain.analysis.enums.AnalysisStatus.FAILED, j.failureCode = :code, j.failureMessage = :message, j.completedAt = :now
			where j.status = com.f5.scholarship_backend.domain.analysis.enums.AnalysisStatus.RUNNING and j.startedAt <= :cutoff
			""")
	int failAbandoned(Instant cutoff, Instant now, String code, String message);
}
