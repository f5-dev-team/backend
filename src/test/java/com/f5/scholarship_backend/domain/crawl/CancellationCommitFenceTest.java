package com.f5.scholarship_backend.domain.crawl;

import java.net.URI;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.*;
import com.f5.scholarship_backend.domain.crawl.config.CrawlProperties;
import com.f5.scholarship_backend.domain.crawl.model.CrawledPost;
import com.f5.scholarship_backend.domain.crawl.repository.*;
import com.f5.scholarship_backend.domain.crawl.service.*;
import com.f5.scholarship_backend.domain.scholarship.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;

@SpringBootTest
@ActiveProfiles("test")
@Timeout(20)
class CancellationCommitFenceTest {
	@Autowired private ScholarshipCollector collector;
	@Autowired private ScholarshipRepository scholarships;
	@Autowired private ScholarshipVersionRepository versions;
	@Autowired private CrawlStateRepository states;
	@Autowired private CrawlRunRepository runs;
	@Autowired private CrawlErrorRepository errors;
	@Autowired private JdbcTemplate jdbc;
	@Autowired private PlatformTransactionManager manager;
	@Autowired private CrawlProperties properties;
	@MockitoSpyBean private LeaseManager leases;

	@BeforeEach
	void clearTestDatabase() {
		errors.deleteAll(); runs.deleteAll(); states.deleteAll(); versions.deleteAll(); scholarships.deleteAll();
		jdbc.update("delete from crawler_leases");
	}

	/** 허가 직후 취소를 경합시켜 commit/rollback 이후에만 영속 취소가 완료되는지 확인한다. */
	@ParameterizedTest
	@ValueSource(booleans = {false, true})
	void cancellationCannotCompleteBetweenPermissionCheckAndTransactionCompletion(boolean rollback) throws Exception {
		String owner = leases.acquire();
		assertThat(owner).isNotNull();
		var checked = new CountDownLatch(1);
		var continueSaving = new CountDownLatch(1);
		var cancelling = new CountDownLatch(1);
		var executor = Executors.newFixedThreadPool(2);
		doAnswer(invocation -> {
			invocation.callRealMethod();
			checked.countDown();
			if (!continueSaving.await(5, TimeUnit.SECONDS)) { throw new IllegalStateException("테스트 저장 대기 시간 초과"); }
			return null;
		}).when(leases).assertWorkAllowed(anyString());
		try {
			var post = new CrawledPost("478:2001", URI.create("https://www.gachon.ac.kr/bbs/kor/478/2001/artclView.do"),
					rollback ? "x".repeat(256) : "합성 공고", "<p>합성 본문</p>", LocalDate.now(), List.of());
			Future<String> saving = executor.submit(() -> collector.collect(post, owner));
			assertThat(checked.await(3, TimeUnit.SECONDS)).isTrue();
			Future<?> cancel = executor.submit(() -> {
				cancelling.countDown();
				leases.cancelWrites(owner);
			});
			assertThat(cancelling.await(2, TimeUnit.SECONDS)).isTrue();
			assertThatThrownBy(() -> cancel.get(200, TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);

			continueSaving.countDown();
			if (rollback) {
				assertThatThrownBy(() -> saving.get(5, TimeUnit.SECONDS)).isInstanceOf(ExecutionException.class);
			} else {
				assertThat(saving.get(5, TimeUnit.SECONDS)).isEqualTo("COLLECTED");
			}
			cancel.get(5, TimeUnit.SECONDS);
			assertThat(jdbc.queryForObject("select cancelled from crawler_leases where owner=?", Boolean.class, owner)).isTrue();
			assertThat(scholarships.count()).isEqualTo(rollback ? 0 : 1);
			assertThat(versions.count()).isEqualTo(rollback ? 0 : 1);
			assertThat(states.count()).isEqualTo(rollback ? 0 : 1);

			// 로컬 플래그를 모르는 별도 manager도 DB의 취소 상태로 저장을 거절해야 한다.
			var peer = new LeaseManager(jdbc, properties, manager);
			assertThatThrownBy(() -> new TransactionTemplate(manager).execute(status -> {
				peer.assertWorkAllowed(owner);
				return null;
			})).isInstanceOf(IllegalStateException.class);
			assertThatThrownBy(() -> collector.collect(post, owner)).isInstanceOf(IllegalStateException.class);
		} finally {
			continueSaving.countDown();
			executor.shutdownNow();
			executor.awaitTermination(5, TimeUnit.SECONDS);
			leases.release(owner);
		}
	}
}