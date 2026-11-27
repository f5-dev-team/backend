package com.f5.scholarship_backend.domain.analysis.entity;

import java.io.Serializable;
import java.time.Instant;
import com.f5.scholarship_backend.domain.analysis.enums.AnalysisExclusionReason;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.springframework.data.domain.Persistable;

@Entity
@Table(name = "analysis_items")
@IdClass(AnalysisItem.Key.class)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AnalysisItem implements Persistable<AnalysisItem.Key> {
	@Id @Column(name = "analysis_id") private Long analysisId;
	@Id @Column(name = "scholarship_id") private Long scholarshipId;
	@Column(name = "scholarship_version", nullable = false) private int scholarshipVersion;
	@Column(name = "processing_status_snapshot", nullable = false, length = 20) private String processingStatus;
	@Enumerated(EnumType.STRING)
	@Column(name = "exclusion_reason", length = 32) private AnalysisExclusionReason exclusionReason;
	@Column(name = "result_completed_at") private Instant resultCompletedAt;

	// The composite ID is assigned before saving, so Spring Data needs an explicit new-state check.
	@Transient
	private boolean newItem = true;

	public AnalysisItem(Long analysisId, Long scholarshipId, int scholarshipVersion,
			String processingStatus, AnalysisExclusionReason exclusionReason) {
		this.analysisId = analysisId;
		this.scholarshipId = scholarshipId;
		this.scholarshipVersion = scholarshipVersion;
		this.processingStatus = processingStatus;
		this.exclusionReason = exclusionReason;
	}

	@Override
	public Key getId() {
		return new Key(analysisId, scholarshipId);
	}

	@Override
	public boolean isNew() {
		return newItem;
	}

	@PostPersist
	@PostLoad
	private void markPersisted() {
		newItem = false;
	}

	public record Key(Long analysisId, Long scholarshipId) implements Serializable { }
}
