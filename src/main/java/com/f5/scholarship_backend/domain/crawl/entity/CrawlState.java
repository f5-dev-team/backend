package com.f5.scholarship_backend.domain.crawl.entity;

import java.time.Instant;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "scholarship_crawl_state")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CrawlState {
	@Id @Column(name = "scholarship_id") private Long scholarshipId;
	@Column(name = "last_source_fingerprint", length = 64) private String lastSourceFingerprint;
	@Column(name = "last_imported_version", nullable = false) private int lastImportedVersion;
	@Lob @Column(name = "candidate_payload", columnDefinition = "LONGTEXT") private String candidatePayload;
	@Column(name = "candidate_fingerprint", length = 64) private String candidateFingerprint;
	@Column(name = "requires_review", nullable = false) private boolean requiresReview;
	@Column(name = "observed_at", nullable = false) private Instant observedAt;

	public CrawlState(Long scholarshipId) { this.scholarshipId = scholarshipId; }

	/** 최신 수집 후보를 보존하고 관리자 정정과 충돌하면 적용 없이 보류한다. */
	public void observe(String fingerprint, String payload, boolean hold, Instant now) {
		candidateFingerprint = fingerprint;
		candidatePayload = payload;
		requiresReview = hold;
		observedAt = now;
	}

	/** 실제 반영된 원문 지문과 버전을 기억해 동일 원문이 정정본을 덮어쓰지 않게 한다. */
	public void imported(String fingerprint, int version) {
		lastSourceFingerprint = fingerprint;
		lastImportedVersion = version;
		requiresReview = false;
	}
}