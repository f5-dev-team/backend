package com.f5.scholarship_backend.domain.scholarship.repository;

import java.util.List;
import com.f5.scholarship_backend.domain.scholarship.entity.ScholarshipCondition;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;

public interface ScholarshipConditionRepository extends Repository<ScholarshipCondition, ScholarshipCondition.Key> {
	boolean existsByScholarshipIdAndVersion(long scholarshipId, int version);
	<S extends ScholarshipCondition> List<S> saveAll(Iterable<S> conditions);

	@Query("""
			select c from ScholarshipCondition c join AnalysisItem i
			    on i.scholarshipId = c.scholarshipId and i.scholarshipVersion = c.version
			where i.analysisId = :analysisId and i.exclusionReason is null
			order by c.scholarshipId, c.conditionId
			""")
	List<ScholarshipCondition> findForAnalysis(long analysisId);
}
