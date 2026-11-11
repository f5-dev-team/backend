package com.f5.scholarship_backend.domain.crawl.service;

import java.util.UUID;
import java.util.concurrent.*;
import com.f5.scholarship_backend.domain.crawl.config.CrawlProperties;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class CrawlJob {
	private static final Logger log = LoggerFactory.getLogger(CrawlJob.class);
	private final CrawlProperties properties;
	private final LeaseManager leases;
	private final CrawlWorker worker;
	private final CrawlJournal journal;
	private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
		var thread = new Thread(r, "gachon-crawl-worker"); thread.setDaemon(true); return thread;
	});

	public CrawlJob(CrawlProperties properties, LeaseManager leases, CrawlWorker worker, CrawlJournal journal) {
		this.properties = properties; this.leases = leases; this.worker = worker; this.journal = journal;
	}

	/** 운영에서 명시적으로 활성화한 경우만 한국 시간 정규 수집을 실행한다. */
	@Scheduled(cron = "${crawler.cron}", zone = "${crawler.zone}")
	public void scheduled() { if (properties.enabled()) { runOnce(); } }

	/** 테스트나 운영 스케줄에서 공유 임대를 얻고 제한 시간 내 한 실행을 수행한다. */
	public Long runOnce() {
		String owner = leases.acquire();
		if (owner == null) { log.info("수집 실행 건너뜀: 기존 실행 진행 중"); return null; }
		String requestId = "req-" + UUID.randomUUID();
		Long id = null;
		Future<?> task = null;
		try {
			id = journal.start(properties.boardUrl().toString(), requestId, owner);
			Long runId = id;
			task = executor.submit(() -> worker.execute(runId, owner, requestId));
			task.get(properties.runTimeout().toMillis(), TimeUnit.MILLISECONDS);
			return id;
		} catch (InterruptedException interrupted) {
			if (task != null) { task.cancel(true); }
			Thread.currentThread().interrupt();
			log.error("수집 실행 중단: requestId={}", requestId);
			return id;
		} catch (ExecutionException | TimeoutException failure) {
			if (task != null) { task.cancel(true); }
			log.error("수집 실행 실패: requestId={}", requestId);
			if (id != null) {
				journal.error(id, "CRAWL_FETCH_FAILED", null, owner);
				journal.finish(id, true, owner);
			}
			return id;
		} finally { leases.release(owner); }
	}

	/** 애플리케이션 종료 시 실행 중인 수집 작업도 중단한다. */
	@PreDestroy
	public void close() { executor.shutdownNow(); }
}