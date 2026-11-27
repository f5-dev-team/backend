package com.f5.scholarship_backend.domain.analysis.repository;

import java.time.Instant;
import java.util.List;
import com.f5.scholarship_backend.domain.analysis.entity.AnalysisItem;
import com.f5.scholarship_backend.domain.analysis.enums.AnalysisExclusionReason;
import com.f5.scholarship_backend.domain.analysis.enums.ApplicationPeriodStatus;
import com.f5.scholarship_backend.domain.matching.enums.EligibilityStatus;
import org.springframework.data.domain.Pageable;
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

	String COMPLETED_RECOMMENDATIONS = """
			from AnalysisItem i join ScholarshipVersion v
			    on v.scholarshipId = i.scholarshipId and v.version = i.scholarshipVersion
			where i.analysisId = :analysisId and i.exclusionReason is null and i.resultCompletedAt is not null
			    and (:eligibilityStatus is null or i.eligibilityStatus = :eligibilityStatus)
			""";

	@Query("select count(i) " + COMPLETED_RECOMMENDATIONS)
	long countRecommendations(long analysisId, EligibilityStatus eligibilityStatus);

	@Query("""
			select i.scholarshipId as scholarshipId, i.scholarshipVersion as scholarshipVersion,
			       v.title as title, v.applicationEndAt as applicationEndAt,
			       i.eligibilityStatus as eligibilityStatus, i.applicationPeriodStatus as applicationPeriodStatus,
			       i.applicationPeriodMessage as applicationPeriodMessage, i.reasons as reasons,
			       i.unmetConditions as unmetConditions, i.unresolvedConditions as unresolvedConditions
			""" + COMPLETED_RECOMMENDATIONS + "order by i.scholarshipId desc")
	List<RecommendationRow> findRecommendations(long analysisId, EligibilityStatus eligibilityStatus, Pageable page);

	interface RecommendationRow {
		Long getScholarshipId();
		int getScholarshipVersion();
		String getTitle();
		Instant getApplicationEndAt();
		EligibilityStatus getEligibilityStatus();
		ApplicationPeriodStatus getApplicationPeriodStatus();
		String getApplicationPeriodMessage();
		String getReasons();
		String getUnmetConditions();
		String getUnresolvedConditions();
	}

	interface ItemCounts {
		AnalysisExclusionReason getReason();
		long getItemCount();
		long getProcessedCount();
	}
}
