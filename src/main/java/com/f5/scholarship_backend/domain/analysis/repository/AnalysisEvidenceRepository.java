package com.f5.scholarship_backend.domain.analysis.repository;

import java.util.List;
import com.f5.scholarship_backend.domain.analysis.entity.AnalysisEvidence;
import org.springframework.data.repository.Repository;

public interface AnalysisEvidenceRepository extends Repository<AnalysisEvidence, Long> {
	List<AnalysisEvidence> findByAnalysisIdAndScholarshipIdAndScholarshipVersionOrderByIdAsc(
			long analysisId, long scholarshipId, int scholarshipVersion);
}
