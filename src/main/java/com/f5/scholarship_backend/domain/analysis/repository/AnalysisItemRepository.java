package com.f5.scholarship_backend.domain.analysis.repository;

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

	interface ItemCounts {
		AnalysisExclusionReason getReason();
		long getItemCount();
		long getProcessedCount();
	}
}
