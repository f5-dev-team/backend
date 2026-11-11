package com.f5.scholarship_backend.domain.crawl.service;

import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
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
	private final AtomicBoolean running = new AtomicBoolean();
	private volatile Execution active;
	private volatile boolean closed;

	public CrawlJob(CrawlProperties properties, LeaseManager leases, CrawlWorker worker, CrawlJournal journal) {
		this.properties = properties; this.leases = leases; this.worker = worker; this.journal = journal;
	}

	/** 운영에서 명시적으로 활성화한 경우만 한국 시간 정규 수집을 실행한다. */
	@Scheduled(cron = "${crawler.cron}", zone = "${crawler.zone}")
	public void scheduled() { if (properties.enabled()) { runOnce(); } }

	/** 실제 worker 종료와 호출자 정리가 모두 끝날 때까지 다음 실행을 차단한다. */
	public Long runOnce() {
		if (closed || !running.compareAndSet(false, true)) { return null; }
		String owner;
		try { owner = leases.acquire(); }
		catch (RuntimeException failure) { running.set(false); throw failure; }
		if (owner == null) { running.set(false); return null; }
		Execution execution = new Execution(owner);
		active = execution;
		String requestId = "req-" + UUID.randomUUID();
		Long id = null;
		boolean restoreInterrupt = false;
		try {
			id = journal.start(properties.boardUrl().toString(), requestId, owner);
			Long runId = id;
			execution.task = new FutureTask<>(() -> {
				worker.execute(runId, owner, requestId);
				return null;
			});
			Thread thread = new Thread(() -> {
				try { execution.task.run(); }
				finally { execution.workerDone(); }
			}, "gachon-crawl-worker");
			thread.setDaemon(true);
			execution.workerStarted = true;
			try { thread.start(); }
			catch (RuntimeException failure) { execution.workerDone(); throw failure; }
			if (closed) { execution.cancel(); }
			execution.task.get(properties.runTimeout().toMillis(), TimeUnit.MILLISECONDS);
			return id;
		} catch (InterruptedException interrupted) {
			execution.cancel();
			// JDBC 실패 종료 기록이 인터럽트로 생략되지 않도록 기록 후 상태를 복원한다.
			Thread.interrupted();
			restoreInterrupt = true;
			if (id != null) { journal.failIfRunning(id, owner); }
			log.error("수집 실행 중단: requestId={}", requestId);
			return id;
		} catch (ExecutionException | TimeoutException | CancellationException failure) {
			execution.cancel();
			if (id != null) { journal.failIfRunning(id, owner); }
			log.error("수집 실행 실패: requestId={}", requestId);
			return id;
		} finally {
			try { execution.callerDone(); }
			finally { if (restoreInterrupt) { Thread.currentThread().interrupt(); } }
		}
	}

	/** 종료를 요청해도 worker가 살아 있으면 실행권은 즉시 반환하지 않는다. */
	@PreDestroy
	public void close() {
		closed = true;
		Execution execution = active;
		if (execution != null) { execution.cancel(); }
	}

	/** Future의 cancelled 상태와 실제 실행 종료를 구분하여 임대 수명을 관리한다. */
	private final class Execution {
		private final String owner;
		private volatile FutureTask<Void> task;
		private volatile boolean workerStarted;
		private boolean workerFinished;
		private boolean callerFinished;
		private boolean released;

		private Execution(String owner) { this.owner = owner; }

		private void cancel() {
			try { leases.cancelWrites(owner); }
			finally {
				FutureTask<Void> current = task;
				if (current != null) { current.cancel(true); }
			}
		}

		private synchronized void workerDone() {
			workerFinished = true;
			releaseIfDone();
		}

		private synchronized void callerDone() {
			callerFinished = true;
			if (!workerStarted) { workerFinished = true; }
			releaseIfDone();
		}

		private void releaseIfDone() {
			if (!released && workerFinished && callerFinished) {
				released = true;
				try { leases.release(owner); }
				finally { active = null; running.set(false); }
			}
		}
	}
}