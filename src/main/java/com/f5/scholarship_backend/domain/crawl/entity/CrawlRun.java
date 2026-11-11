package com.f5.scholarship_backend.domain.crawl.entity;

import java.time.Instant;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity @Table(name = "crawl_runs")
@Getter @NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CrawlRun {
	@Id @GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "crawl_run_id") private Long id;
	@Column(name = "source_url", nullable = false, length = 2048) private String sourceUrl;
	@Column(nullable = false, length = 20) private String status;
	@Column(name = "started_at", nullable = false) private Instant startedAt;
	@Column(name = "finished_at") private Instant finishedAt;
	@Column(name = "discovered_count", nullable = false) private int discoveredCount;
	@Column(name = "collected_count", nullable = false) private int collectedCount;
	@Column(name = "skipped_count", nullable = false) private int skippedCount;
	@Column(name = "failed_count", nullable = false) private int failedCount;
	@Column(name = "request_id", nullable = false, length = 64) private String requestId;

	public CrawlRun(String sourceUrl, String requestId, Instant now) {
		this.sourceUrl = sourceUrl; this.requestId = requestId; startedAt = now; status = "RUNNING";
	}
	/** 발견한 고유 게시글 수를 별도 저장하여 중단 시 미처리 실패를 복구한다. */
	public void discover(int count) { discoveredCount = Math.max(discoveredCount, count); }
	/** 처리 결과는 게시글당 한 번 반영하며 발견 수와 합계를 일치시킨다. */
	public void record(String outcome) {
		switch (outcome) {
			case "COLLECTED" -> collectedCount++;
			case "SKIPPED" -> skippedCount++;
			case "FAILED" -> failedCount++;
			default -> throw new IllegalArgumentException("지원하지 않는 수집 결과입니다.");
		}
	}
	/** 발견 후 중단된 글도 실패로 집계하여 종료 상태를 결정한다. */
	public void finish(boolean incomplete, Instant now) {
		failedCount += Math.max(0, discoveredCount - collectedCount - skippedCount - failedCount);
		status = incomplete || failedCount > 0
				? (collectedCount + skippedCount > 0 ? "PARTIAL_FAILED" : "FAILED") : "SUCCEEDED";
		finishedAt = now;
	}
}