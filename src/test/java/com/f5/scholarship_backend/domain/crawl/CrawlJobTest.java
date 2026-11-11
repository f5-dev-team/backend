package com.f5.scholarship_backend.domain.crawl;

import java.time.Duration;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import com.f5.scholarship_backend.domain.crawl.config.CrawlProperties;
import com.f5.scholarship_backend.domain.crawl.service.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@Timeout(10)
class CrawlJobTest {
	private final LeaseManager leases = mock(LeaseManager.class);
	private final CrawlWorker worker = mock(CrawlWorker.class);
	private final CrawlJournal journal = mock(CrawlJournal.class);
	private final CountDownLatch released = new CountDownLatch(1);

	private CrawlJob job(Duration timeout) {
		var p = GachonHttpClientTest.properties(java.net.URI.create("https://www.gachon.ac.kr/bbs/kor/478/artclList.do"));
		var settings = new CrawlProperties(false, p.boardUrl(), p.cron(), p.zone(), p.days(), p.maxPages(),
				p.maxPosts(), p.interval(), p.connectTimeout(), p.requestTimeout(), p.maxHtmlBytes(), timeout, p.leaseDuration());
		when(leases.acquire()).thenReturn("owner");
		doAnswer(invocation -> { released.countDown(); return null; }).when(leases).release("owner");
		when(journal.start(anyString(), anyString(), eq("owner"))).thenReturn(1L);
		return new CrawlJob(settings, leases, worker, journal);
	}

	@Test void normalCompletionReleasesOwnership() throws Exception {
		var job = job(Duration.ofSeconds(2));
		try {
			assertThat(job.runOnce()).isEqualTo(1L);
			assertThat(released.await(2, TimeUnit.SECONDS)).isTrue();
			verify(leases).release("owner");
			verify(journal, never()).failIfRunning(anyLong(), anyString());
		} finally { job.close(); }
	}

	@Test void timedOutUncooperativeWorkerKeepsLeaseAndCannotQueueAnotherRun() throws Exception {
		var job = job(Duration.ofMillis(300));
		var entered = new CountDownLatch(1);
		var stop = new CountDownLatch(1);
		doAnswer(invocation -> {
			entered.countDown();
			while (stop.getCount() > 0) {
				try { stop.await(); }
				catch (InterruptedException ignored) { /* 종료 신호를 무시하는 외부 작업을 모사한다. */ }
			}
			return null;
		}).when(worker).execute(anyLong(), anyString(), anyString());
		try {
			assertThat(job.runOnce()).isEqualTo(1L);
			assertThat(entered.await(1, TimeUnit.SECONDS)).isTrue();
			verify(journal).failIfRunning(1L, "owner");
			verify(leases).cancelWrites("owner");
			verify(leases, never()).release(anyString());
			assertThat(job.runOnce()).isNull();
			verify(leases, times(1)).acquire();
			stop.countDown();
			assertThat(released.await(2, TimeUnit.SECONDS)).isTrue();
			verify(leases).release("owner");
		} finally { stop.countDown(); job.close(); }
	}

	@ParameterizedTest
	@ValueSource(booleans = {false, true})
	void interruptedCallerRecordsFailureBeforeRestoringFlagAndKeepsLeaseUntilExit(boolean cancellationFails) throws Exception {
		var job = job(Duration.ofSeconds(5));
		if (cancellationFails) { doThrow(new IllegalStateException("합성 취소 실패")).when(leases).cancelWrites("owner"); }
		var entered = new CountDownLatch(1);
		var stop = new CountDownLatch(1);
		var restored = new AtomicBoolean();
		var failure = new AtomicReference<Throwable>();
		doAnswer(invocation -> {
			entered.countDown();
			while (stop.getCount() > 0) {
				try { stop.await(); } catch (InterruptedException ignored) { }
			}
			return null;
		}).when(worker).execute(anyLong(), anyString(), anyString());
		doAnswer(invocation -> {
			assertThat(Thread.currentThread().isInterrupted()).isFalse();
			return null;
		}).when(journal).failIfRunning(1L, "owner");
		Thread caller = new Thread(() -> {
			try { job.runOnce(); restored.set(Thread.currentThread().isInterrupted()); }
			catch (Throwable exception) { failure.set(exception); }
		});
		try {
			caller.start();
			assertThat(entered.await(2, TimeUnit.SECONDS)).isTrue();
			caller.interrupt();
			caller.join(2000);
			assertThat(caller.isAlive()).isFalse();
			assertThat(failure.get()).isNull();
			assertThat(restored.get()).isTrue();
			verify(journal).failIfRunning(1L, "owner");
			verify(leases, never()).release(anyString());
			assertThat(job.runOnce()).isNull();
			stop.countDown();
			assertThat(released.await(2, TimeUnit.SECONDS)).isTrue();
			verify(leases).release("owner");
		} finally { stop.countDown(); caller.interrupt(); job.close(); }
	}

	@Test void workerFailureIsRecordedAndLeaseIsReleased() throws Exception {
		var job = job(Duration.ofSeconds(2));
		doThrow(new IllegalStateException("합성 실패")).when(worker).execute(anyLong(), anyString(), anyString());
		try {
			assertThat(job.runOnce()).isEqualTo(1L);
			verify(journal).failIfRunning(1L, "owner");
			assertThat(released.await(2, TimeUnit.SECONDS)).isTrue();
			verify(leases).release("owner");
		} finally { job.close(); }
	}

	/** 취소 DB 오류가 있어도 실패 종료를 시도하고 실행 중인 작업에 인터럽트를 전달한다. */
	@ParameterizedTest
	@ValueSource(booleans = {false, true})
	void cancellationFailureStillRecordsTimeoutOrWorkerFailure(boolean workerFails) throws Exception {
		var job = job(Duration.ofMillis(300));
		var interrupted = new CountDownLatch(1);
		doThrow(new IllegalStateException("합성 취소 실패")).when(leases).cancelWrites("owner");
		doAnswer(invocation -> {
			if (workerFails) { throw new IllegalStateException("합성 작업 실패"); }
			try { new CountDownLatch(1).await(); }
			catch (InterruptedException expected) { interrupted.countDown(); }
			return null;
		}).when(worker).execute(anyLong(), anyString(), anyString());
		try {
			assertThat(job.runOnce()).isEqualTo(1L);
			verify(leases).cancelWrites("owner");
			verify(journal).failIfRunning(1L, "owner");
			if (!workerFails) { assertThat(interrupted.await(2, TimeUnit.SECONDS)).isTrue(); }
			assertThat(released.await(2, TimeUnit.SECONDS)).isTrue();
			verify(leases).release("owner");
		} finally { job.close(); }
	}

}
