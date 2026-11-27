package com.f5.scholarship_backend.domain.analysis.repository;

import java.time.Instant;
import java.util.List;
import com.f5.scholarship_backend.domain.analysis.entity.AnalysisItem;
import com.f5.scholarship_backend.domain.analysis.enums.AnalysisExclusionReason;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface AnalysisItemRepository extends JpaRepository<AnalysisItem, AnalysisItem.Key> {
	@Query("""
			select i.exclusionReason as reason, count(i) as itemCount,
			       count(i.resultCompletedAt) as processedCount
			from AnalysisItem i
			where i.analysisId = :analysisId
			group by i.exclusionReason
			""")
	List<ItemCounts> countByExclusionReason(long analysisId);

	@Query("""
			select i as item, v.version as version, v.applicationStartAt as startAt, v.applicationEndAt as endAt
			from AnalysisItem i left join ScholarshipVersion v
			    on v.scholarshipId = i.scholarshipId and v.version = i.scholarshipVersion
			where i.analysisId = :analysisId and i.exclusionReason is null
			order by i.scholarshipId
			""")
	List<AnalysisTarget> findTargets(long analysisId);

	interface AnalysisTarget {
		AnalysisItem getItem();
		Integer getVersion();
		Instant getStartAt();
		Instant getEndAt();
	}

	interface ItemCounts {
		AnalysisExclusionReason getReason();
		long getItemCount();
		long getProcessedCount();
	}
}
