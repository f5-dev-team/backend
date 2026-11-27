package com.f5.scholarship_backend.domain.analysis.entity;

import java.io.Serializable;
import java.time.Instant;
import com.f5.scholarship_backend.domain.analysis.enums.AnalysisExclusionReason;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "analysis_items")
@IdClass(AnalysisItem.Key.class)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AnalysisItem {
	@Id @Column(name = "analysis_id") private Long analysisId;
	@Id @Column(name = "scholarship_id") private Long scholarshipId;
	@Column(name = "scholarship_version", nullable = false) private int scholarshipVersion;
	@Column(name = "processing_status_snapshot", nullable = false, length = 20) private String processingStatus;
	@Enumerated(EnumType.STRING)
	@Column(name = "exclusion_reason", length = 32) private AnalysisExclusionReason exclusionReason;
	@Column(name = "result_completed_at") private Instant resultCompletedAt;

	public AnalysisItem(Long analysisId, Long scholarshipId, int scholarshipVersion,
			String processingStatus, AnalysisExclusionReason exclusionReason) {
		this.analysisId = analysisId;
		this.scholarshipId = scholarshipId;
		this.scholarshipVersion = scholarshipVersion;
		this.processingStatus = processingStatus;
		this.exclusionReason = exclusionReason;
	}

	public record Key(Long analysisId, Long scholarshipId) implements Serializable { }
}
