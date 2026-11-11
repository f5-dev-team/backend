package com.f5.scholarship_backend.domain.crawl.service;

import java.time.Clock;
import com.f5.scholarship_backend.domain.crawl.entity.*;
import com.f5.scholarship_backend.domain.crawl.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CrawlJournal {
	private final CrawlRunRepository runs;
	private final CrawlErrorRepository errors;
	private final LeaseManager leases;
	private final Clock clock;

	public CrawlJournal(CrawlRunRepository runs, CrawlErrorRepository errors, LeaseManager leases, Clock clock) {
		this.runs = runs; this.errors = errors; this.leases = leases; this.clock = clock;
	}

	/** 새 임대를 획득한 실행이 오래된 RUNNING을 복구한 뒤 자신의 실행을 시작한다. */
	@Transactional(timeout = 10)
	public Long start(String url, String requestId, String owner) {
		leases.assertHeld(owner);
		for (var old : runs.findByStatusAndStartedAtBefore("RUNNING", clock.instant().minusSeconds(3600))) {
			old.finish(true, clock.instant());
			errors.save(new CrawlError(old.getId(), "CRAWL_FETCH_FAILED", null, clock.instant()));
		}
		return runs.save(new CrawlRun(url, requestId, clock.instant())).getId();
	}

	/** 네트워크 작업과 독립적으로 발견한 고유 공고 수를 영속화한다. */
	@Transactional(timeout = 10)
	public void discover(Long id, int count, String owner) {
		leases.assertHeld(owner);
		var run = runs.findById(id).orElseThrow();
		if (!run.getStatus().equals("RUNNING")) { throw new IllegalStateException("수집 실행이 종료되었습니다."); }
		run.discover(count);
	}
	/** 유실된 임대나 종료된 실행에 늦게 도착한 결과는 기록하지 않는다. */
	@Transactional(timeout = 10)
	public void outcome(Long id, String result, String owner) {
		leases.assertHeld(owner);
		var run = runs.findById(id).orElseThrow();
		if (!run.getStatus().equals("RUNNING")) { throw new IllegalStateException("수집 실행이 종료되었습니다."); }
		run.record(result.equals("HELD") ? "SKIPPED" : result);
	}

	/** 임대 소유자만 안전한 고정 오류 요약을 저장한다. */
	@Transactional(timeout = 10)
	public void error(Long id, String code, String url, String owner) {
		leases.assertHeld(owner);
		errors.save(new CrawlError(id, code, url, clock.instant()));
	}

	/** 종료 집계와 상태를 저장한다. 이미 종료된 실행은 변경하지 않는다. */
	@Transactional(timeout = 10)
	public void finish(Long id, boolean incomplete, String owner) {
		leases.assertHeld(owner);
		var run = runs.findById(id).orElseThrow();
		if (run.getStatus().equals("RUNNING")) { run.finish(incomplete, clock.instant()); }
	}
}