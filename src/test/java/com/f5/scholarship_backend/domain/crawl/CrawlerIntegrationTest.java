package com.f5.scholarship_backend.domain.crawl;

import java.net.URI;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import com.f5.scholarship_backend.domain.crawl.client.BoardClient;
import com.f5.scholarship_backend.domain.crawl.model.CrawledPost;
import com.f5.scholarship_backend.domain.crawl.repository.*;
import com.f5.scholarship_backend.domain.crawl.service.*;
import com.f5.scholarship_backend.domain.scholarship.repository.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@SpringBootTest(properties = {"crawler.interval=0ms", "crawler.max-pages=2", "crawler.max-posts=2"})
@ActiveProfiles("test")
class CrawlerIntegrationTest {
	@Autowired ScholarshipCollector collector;
	@Autowired ScholarshipRepository scholarships;
	@Autowired ScholarshipVersionRepository versions;
	@Autowired CrawlStateRepository states;
	@Autowired CrawlRunRepository runs;
	@Autowired CrawlErrorRepository errors;
	@Autowired LeaseManager leases;
	@Autowired CrawlJob job;
	@Autowired CrawlJournal journal;
	@Autowired JdbcTemplate jdbc;
	@Autowired org.springframework.transaction.PlatformTransactionManager manager;
	@Autowired com.f5.scholarship_backend.domain.crawl.config.CrawlProperties properties;
	@MockitoBean BoardClient client;
	private String owner;

	@BeforeEach void clean() {
		errors.deleteAll(); runs.deleteAll(); states.deleteAll(); versions.deleteAll(); scholarships.deleteAll();
		jdbc.update("delete from crawler_leases");
		reset(client);
	}
	@AfterEach void release() { if (owner != null) { leases.release(owner); owner = null; } }

	@Test void createsAtomicVersionSkipsSameAndPreservesOldBody() {
		owner = leases.acquire();
		assertThat(collector.collect(post("제목", "<p>원문</p>"), owner)).isEqualTo("COLLECTED");
		assertThat(collector.collect(post("제목", "<p>원문</p>"), owner)).isEqualTo("SKIPPED");
		assertThat(collector.collect(post("변경 제목", "<p>변경</p>"), owner)).isEqualTo("COLLECTED");
		assertThat(scholarships.count()).isEqualTo(1);
		assertThat(versions.count()).isEqualTo(2);
		var id = scholarships.findAll().get(0).getId();
		assertThat(versions.findById(new com.f5.scholarship_backend.domain.scholarship.entity.ScholarshipVersion.Key(id, 1))
				.orElseThrow().getSourceText()).contains("원문");
		assertThat(scholarships.findById(id).orElseThrow().getProcessingStatus()).isEqualTo("PENDING");
	}
	@Test void rollsBackIdentityWhenVersionSaveFails() {
		owner = leases.acquire();
		assertThatThrownBy(() -> collector.collect(post("x".repeat(256), "본문"), owner)).isInstanceOf(RuntimeException.class);
		assertThat(scholarships.count()).isZero();
		assertThat(versions.count()).isZero();
		assertThat(states.count()).isZero();
	}
	@Test void preservesAdminCorrectionAndStoresChangedSourceAsCandidate() {
		owner = leases.acquire();
		var source = post("원문", "내용");
		collector.collect(source, owner);
		Long id = scholarships.findAll().get(0).getId();
		jdbc.update("insert into scholarship_versions(scholarship_id,version,title,source_url,source_text,created_at,origin,changed_by_user_id) values(?,2,?,?,?,?,'ADMIN',99)",
				id, "관리자 정정", source.sourceUrl().toString(), "관리자 본문", java.sql.Timestamp.from(Instant.now()));
		assertThat(collector.collect(source, owner)).isEqualTo("SKIPPED");
		assertThat(collector.collect(post("바뀐 원문", "새 본문"), owner)).isEqualTo("HELD");
		assertThat(versions.count()).isEqualTo(2);
		assertThat(versions.findFirstByScholarshipIdOrderByVersionDesc(id).orElseThrow().getTitle()).isEqualTo("관리자 정정");
		assertThat(states.findById(id).orElseThrow().isRequiresReview()).isTrue();
		assertThat(states.findById(id).orElseThrow().getCandidatePayload()).contains("바뀐 원문");
	}
	@Test void exclusiveLeaseRejectsStaleWriterAfterTakeover() {
		owner = leases.acquire();
		assertThat(leases.acquire()).isNull();
		String expired = owner;
		leases.release(owner); owner = leases.acquire();
		assertThatThrownBy(() -> collector.collect(post("제목", "내용"), expired)).isInstanceOf(IllegalStateException.class);
		assertThat(collector.collect(post("제목", "내용"), owner)).isEqualTo("COLLECTED");
	}
	@Test void jobDeduplicatesPinnedRowsAndContinuesAfterOneDetailFailure() {
		String date = LocalDate.now(java.time.ZoneId.of("Asia/Seoul")).toString().replace('-', '.');
		String rows = "<tr class='notice'><td class='td-subject'><a href='/bbs/kor/478/1001/artclView.do'>A</a></td><td class='td-date'>2020.01.01</td></tr>"
				+ row(1001, date) + row(1002, date);
		when(client.get(any())).thenAnswer(invocation -> {
			URI uri = invocation.getArgument(0);
			if (uri.getPath().endsWith("artclList.do")) { return "<table>" + rows + "</table>"; }
			if (uri.getPath().contains("/1002/")) { throw new com.f5.scholarship_backend.domain.crawl.client.CrawlException(); }
			return detail(1001);
		});
		Long id = job.runOnce();
		var run = runs.findById(id).orElseThrow();
		assertThat(run.getDiscoveredCount()).isEqualTo(2);
		assertThat(run.getCollectedCount()).isEqualTo(1);
		assertThat(run.getFailedCount()).isEqualTo(1);
		assertThat(run.getStatus()).isEqualTo("PARTIAL_FAILED");
		assertThat(run.getRequestId()).startsWith("req-");
		verify(client, times(1)).get(URI.create("https://www.gachon.ac.kr/bbs/kor/478/1001/artclView.do"));
		assertThat(errors.findAll()).allMatch(error -> !error.getMessage().contains("Exception"));
	}
	@Test void listFailureIsNotZeroItemSuccess() {
		when(client.get(any())).thenReturn("<html>로그인 오류</html>");
		var run = runs.findById(job.runOnce()).orElseThrow();
		assertThat(run.getStatus()).isEqualTo("FAILED");
		assertThat(run.getDiscoveredCount()).isZero();
		assertThat(errors.count()).isPositive();
	}
	@Test void scanLimitIsRecordedAsIncomplete() {
		String date = LocalDate.now(java.time.ZoneId.of("Asia/Seoul")).toString().replace('-', '.');
		when(client.get(any())).thenAnswer(invocation -> {
			URI uri = invocation.getArgument(0);
			if (uri.getPath().endsWith("artclList.do")) {
				boolean second = uri.getQuery().contains("page=2");
				return "<table>" + row(second ? 1002 : 1001, date) + "</table><a href=\"javascript:page_link('3')\">3</a>";
			}
			return detail(uri.getPath().contains("1001") ? 1001 : 1002);
		});
		var run = runs.findById(job.runOnce()).orElseThrow();
		assertThat(run.getCollectedCount()).isEqualTo(2);
		assertThat(run.getStatus()).isEqualTo("PARTIAL_FAILED");
		assertThat(errors.count()).isPositive();
	}
	@Test void competingManagerCannotAcquireLiveLeaseButCanTakeExpiredLease() {
		owner = leases.acquire();
		var peer = new LeaseManager(jdbc, properties, manager);
		assertThat(peer.acquire()).isNull();
		jdbc.update("update crawler_leases set locked_until=?", java.sql.Timestamp.from(Instant.now().minusSeconds(5)));
		String next = peer.acquire();
		assertThat(next).isNotNull();
		try {
			assertThatThrownBy(() -> collector.collect(post("늦은 쓰기", "본문"), owner))
					.isInstanceOf(IllegalStateException.class);
			assertThat(collector.collect(post("새 실행", "본문"), next)).isEqualTo("COLLECTED");
		} finally { peer.release(next); }
	}
	@Test void attachmentOrderDoesNotChangeVersionButChangedNameDoes() {
		owner = leases.acquire();
		var first = new CrawledPost.Attachment("첫.hwp", URI.create("https://www.gachon.ac.kr/bbs/kor/478/7001/download.do"));
		var second = new CrawledPost.Attachment("둘.pdf", URI.create("https://www.gachon.ac.kr/bbs/kor/478/7002/download.do"));
		var source = post("제목", "본문");
		var initial = new CrawledPost(source.postKey(), source.sourceUrl(), source.title(), source.bodyHtml(),
				source.publishedDate(), List.of(first, second));
		var reordered = new CrawledPost(source.postKey(), source.sourceUrl(), source.title(), source.bodyHtml(),
				source.publishedDate(), List.of(second, first));
		assertThat(collector.collect(initial, owner)).isEqualTo("COLLECTED");
		assertThat(collector.collect(reordered, owner)).isEqualTo("SKIPPED");
		var changed = new CrawledPost(source.postKey(), source.sourceUrl(), source.title(), source.bodyHtml(),
				source.publishedDate(), List.of(new CrawledPost.Attachment("수정.hwp", first.url())));
		assertThat(collector.collect(changed, owner)).isEqualTo("COLLECTED");
		var id = scholarships.findAll().get(0).getId();
		assertThat(versions.findById(new com.f5.scholarship_backend.domain.scholarship.entity.ScholarshipVersion.Key(id, 1))
				.orElseThrow().getSourceAttachments()).contains("첫.hwp", "둘.pdf");
	}
	@Test void postLimitCountsUnprocessedItemsAsFailures() {
		String date = LocalDate.now(java.time.ZoneId.of("Asia/Seoul")).toString().replace('-', '.');
		when(client.get(any())).thenAnswer(invocation -> {
			URI uri = invocation.getArgument(0);
			if (uri.getPath().endsWith("artclList.do")) {
				return "<table>" + row(1001, date) + row(1002, date) + row(1003, date) + "</table>";
			}
			return detail(uri.getPath().contains("1001") ? 1001 : 1002);
		});
		var run = runs.findById(job.runOnce()).orElseThrow();
		assertThat(run.getDiscoveredCount()).isEqualTo(3);
		assertThat(run.getCollectedCount()).isEqualTo(2);
		assertThat(run.getFailedCount()).isEqualTo(1);
		assertThat(run.getStatus()).isEqualTo("PARTIAL_FAILED");
	}
	@Test void staleRunIsRecoveredAfterExpiredLeaseAndNoUnprocessedItemsDisappear() {
		owner = leases.acquire();
		var old = runs.save(new com.f5.scholarship_backend.domain.crawl.entity.CrawlRun(
				properties.boardUrl().toString(), "req-old", Instant.now().minusSeconds(7200)));
		old.discover(3); old.record("COLLECTED"); runs.save(old);
		leases.release(owner); owner = null;
		when(client.get(any())).thenReturn("<html>구조 오류</html>");
		job.runOnce();
		var recovered = runs.findById(old.getId()).orElseThrow();
		assertThat(recovered.getStatus()).isEqualTo("PARTIAL_FAILED");
		assertThat(recovered.getDiscoveredCount()).isEqualTo(3);
		assertThat(recovered.getFailedCount()).isEqualTo(2);
		assertThat(recovered.getFinishedAt()).isNotNull();
	}
	@Test void reversionClearsHeldCandidateWithoutChangingAdminVersion() {
		owner = leases.acquire();
		var source = post("원문", "원래 본문");
		collector.collect(source, owner);
		Long id = scholarships.findAll().get(0).getId();
		jdbc.update("insert into scholarship_versions(scholarship_id,version,title,source_url,source_text,created_at,origin,changed_by_user_id) values(?,2,?,?,?,?,'ADMIN',99)",
				id, "관리자 정정", source.sourceUrl().toString(), "관리자 본문", java.sql.Timestamp.from(Instant.now()));
		assertThat(collector.collect(post("변경 원문", "변경 본문"), owner)).isEqualTo("HELD");
		assertThat(collector.collect(source, owner)).isEqualTo("SKIPPED");
		var state = states.findById(id).orElseThrow();
		assertThat(state.isRequiresReview()).isFalse();
		assertThat(state.getCandidateFingerprint()).isEqualTo(state.getLastSourceFingerprint());
		assertThat(state.getCandidatePayload()).contains("원래 본문").doesNotContain("변경 본문");
		assertThat(versions.count()).isEqualTo(2);
		assertThat(versions.findFirstByScholarshipIdOrderByVersionDesc(id).orElseThrow().getTitle()).isEqualTo("관리자 정정");
	}
	@Test void rejectsErrorAfterRunFinished() {
		owner = leases.acquire();
		Long id = journal.start(properties.boardUrl().toString(), "req-finish", owner);
		journal.finish(id, false, owner);
		assertThatThrownBy(() -> journal.error(id, "CRAWL_FETCH_FAILED", null, owner))
				.isInstanceOf(IllegalStateException.class);
		assertThat(errors.count()).isZero();
		assertThat(runs.findById(id).orElseThrow().getStatus()).isEqualTo("SUCCEEDED");
		journal.failIfRunning(id, owner);
		assertThat(runs.findById(id).orElseThrow().getStatus()).isEqualTo("SUCCEEDED");
	}
	@Test void cancelledWorkerCannotStoreButCallerCanFinishWithSameLease() {
		owner = leases.acquire();
		Long id = journal.start(properties.boardUrl().toString(), "req-cancel", owner);
		journal.discover(id, 1, owner);
		leases.cancelWrites(owner);
		assertThatThrownBy(() -> collector.collect(post("취소 이후", "본문"), owner))
				.isInstanceOf(IllegalStateException.class);
		journal.failIfRunning(id, owner);
		assertThat(runs.findById(id).orElseThrow().getStatus()).isEqualTo("FAILED");
		assertThat(runs.findById(id).orElseThrow().getFailedCount()).isEqualTo(1);
		assertThat(errors.count()).isEqualTo(1);
		assertThat(leases.acquire()).isNull();
	}
	@org.junit.jupiter.params.ParameterizedTest
	@org.junit.jupiter.params.provider.ValueSource(booleans = {true, false})
	void pinnedOnlyPagesAreComparedByAllPostKeys(boolean distinct) {
		when(client.get(any())).thenAnswer(invocation -> {
			URI uri = invocation.getArgument(0);
			if (uri.getPath().endsWith("artclList.do")) {
				boolean second = uri.getQuery().contains("page=2");
				return "<table>" + row(second && distinct ? 1002 : 1001, "2020.01.01")
						.replace("<tr>", "<tr class='notice'>") + "</table>"
						+ (second ? "" : "<a href=\"javascript:page_link('2')\">2</a>");
			}
			return detail(uri.getPath().contains("1002") ? 1002 : 1001);
		});
		var run = runs.findById(job.runOnce()).orElseThrow();
		assertThat(run.getStatus()).isEqualTo(distinct ? "SUCCEEDED" : "PARTIAL_FAILED");
		assertThat(run.getCollectedCount()).isEqualTo(distinct ? 2 : 1);
		if (distinct) { assertThat(errors.count()).isZero(); } else { assertThat(errors.count()).isPositive(); }
	}
	private CrawledPost post(String title, String body) {
		return new CrawledPost("478:1001", URI.create("https://www.gachon.ac.kr/bbs/kor/478/1001/artclView.do"),
				title, body, LocalDate.now(), List.of());
	}
	private String row(int id, String date) {
		return "<tr><td class='td-subject'><a href='/bbs/kor/478/" + id + "/artclView.do'>제목</a></td><td class='td-date'>" + date + "</td></tr>";
	}
	private String detail(int id) {
		return "<dl class='view-num'><dd>" + id + "</dd></dl><h2 class='view-title'>합성 제목</h2><div class='view-con'><p>합성 본문</p></div>";
	}
}