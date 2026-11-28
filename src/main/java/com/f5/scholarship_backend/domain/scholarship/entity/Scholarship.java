package com.f5.scholarship_backend.domain.scholarship.entity;

import java.time.Instant;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "scholarships", uniqueConstraints = @UniqueConstraint(
		name = "uk_scholarship_source", columnNames = {"source_site", "source_post_key", "source_item_key"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Scholarship {
	@Id @GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "scholarship_id") private Long id;
	@Column(name = "source_site", nullable = false, length = 64) private String sourceSite;
	@Column(name = "source_post_key", nullable = false, length = 191) private String sourcePostKey;
	@Column(name = "source_item_key", nullable = false, length = 64) private String sourceItemKey;
	@Column(name = "processing_status", nullable = false, length = 20) private String processingStatus;
	@Column(name = "processing_error_code", length = 64) private String processingErrorCode;
	@Lob @Column(name = "processing_error_message") private String processingErrorMessage;
	@Column(name = "collected_at", nullable = false) private Instant collectedAt;
	@Column(name = "status_updated_at", nullable = false) private Instant statusUpdatedAt;
	@Column(name = "document_analysis_id", length = 128) private String documentAnalysisId;
	@Column(name = "document_analysis_version") private Integer documentAnalysisVersion;
	@Column(name = "document_analysis_next_at") private Instant documentAnalysisNextAt;

	/** 원문 수집용 main 식별 행을 생성하며 적격 분석 대상으로 즉시 전환하지 않는다. */
	public Scholarship(String postKey, Instant now) {
		this.sourceSite = "GACHON_KOR";
		this.sourcePostKey = postKey;
		this.sourceItemKey = "main";
		this.collectedAt = now;
		markPending(now);
	}

	/** 기존 오류 상태를 해제하고 문서 처리 대기 상태로 전환한다. */
	public void markPending(Instant now) {
		changeStatus("PENDING", null, null, now);
	}

	public void startDocumentAnalysis(int version, Instant now, Instant reserveUntil) {
		changeStatus("PROCESSING", null, null, now);
		documentAnalysisVersion = version;
		documentAnalysisNextAt = reserveUntil;
	}

	public void scheduleDocumentAnalysis(String analysisId, Instant nextAt) {
		documentAnalysisId = analysisId;
		documentAnalysisNextAt = nextAt;
	}

	public void finishDocumentAnalysis(Instant now) {
		changeStatus("READY", null, null, now);
	}

	public void failDocumentAnalysis(String code, String message, Instant now) {
		changeStatus("FAILED", code, message, now);
	}

	private void changeStatus(String status, String code, String message, Instant now) {
		processingStatus = status;
		processingErrorCode = code;
		processingErrorMessage = message;
		statusUpdatedAt = now;
		documentAnalysisId = null;
		documentAnalysisVersion = null;
		documentAnalysisNextAt = null;
	}
}
