package com.f5.scholarship_backend.domain.crawl.entity;

import java.time.Instant;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity @Table(name = "crawl_errors")
@Getter @NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CrawlError {
	@Id @GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "crawl_error_id") private Long id;
	@Column(name = "crawl_run_id", nullable = false) private Long runId;
	@Column(nullable = false, length = 64) private String code;
	@Lob @Column(nullable = false) private String message;
	@Column(name = "source_url", length = 2048) private String sourceUrl;
	@Column(name = "occurred_at", nullable = false) private Instant occurredAt;

	/** 내부 메시지를 저장하지 않고 관리자가 볼 수 있는 고정 요약만 보관한다. */
	public CrawlError(Long runId, String code, String sourceUrl, Instant now) {
		this.runId = runId; this.code = code; this.sourceUrl = sourceUrl; occurredAt = now;
		message = switch (code) {
			case "STORAGE_FAILED" -> "수집 결과 저장을 완료하지 못했습니다.";
			case "DOCUMENT_FETCH_FAILED" -> "첨부파일 수집을 완료하지 못했습니다.";
			default -> "공고 조회 또는 수집 범위 확인을 완료하지 못했습니다.";
		};
	}
}