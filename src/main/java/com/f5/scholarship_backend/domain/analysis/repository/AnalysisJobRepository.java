package com.f5.scholarship_backend.domain.analysis.repository;

import java.util.Optional;
import com.f5.scholarship_backend.domain.analysis.entity.AnalysisJob;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AnalysisJobRepository extends JpaRepository<AnalysisJob, Long> {
	Optional<AnalysisJob> findByIdAndUserId(Long id, Long userId);
}
