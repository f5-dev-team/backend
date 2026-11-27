package com.f5.scholarship_backend.domain.analysis.entity;

import java.io.Serializable;
import java.time.Instant;
import com.f5.scholarship_backend.domain.analysis.enums.AnalysisExclusionReason;
import com.f5.scholarship_backend.domain.analysis.enums.ApplicationPeriodStatus;
import com.f5.scholarship_backend.domain.analysis.enums.EvidenceStatus;
import com.f5.scholarship_backend.domain.matching.enums.EligibilityStatus;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
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
	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(name = "eligibility_status", length = 16) private EligibilityStatus eligibilityStatus;
	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(name = "application_period_status", length = 16) private ApplicationPeriodStatus applicationPeriodStatus;
	@Column(name = "application_period_message", columnDefinition = "TEXT") private String applicationPeriodMessage;
	@JdbcTypeCode(SqlTypes.JSON)
	private String reasons;
	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "unmet_conditions") private String unmetConditions;
	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "unresolved_conditions") private String unresolvedConditions;
	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(name = "evidence_status", length = 16) private EvidenceStatus evidenceStatus;
	@Column(name = "evidence_message", columnDefinition = "TEXT") private String evidenceMessage;
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

	public void complete(EligibilityStatus eligibility, ApplicationPeriodStatus period, String periodMessage,
			String reasons, String unmet, String unresolved, Instant now) {
		if (exclusionReason != null || resultCompletedAt != null) {
			throw new IllegalStateException("Only a pending analysis target can be completed");
		}
		eligibilityStatus = eligibility;
		applicationPeriodStatus = period;
		applicationPeriodMessage = periodMessage;
		this.reasons = reasons;
		unmetConditions = unmet;
		unresolvedConditions = unresolved;
		evidenceStatus = EvidenceStatus.UNAVAILABLE;
		evidenceMessage = "상세 원문 근거는 현재 제공할 수 없습니다.";
		resultCompletedAt = now;
	}

	public record Key(Long analysisId, Long scholarshipId) implements Serializable { }
}
