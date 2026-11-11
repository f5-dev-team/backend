package com.f5.scholarship_backend.domain.crawl;

import java.net.URI;
import java.util.concurrent.TimeUnit;
import java.util.UUID;
import javax.sql.DataSource;

import com.f5.scholarship_backend.domain.crawl.client.BoardClient;
import com.f5.scholarship_backend.domain.crawl.client.GachonBoardParser;
import com.f5.scholarship_backend.domain.crawl.config.CrawlProperties;
import com.f5.scholarship_backend.domain.crawl.service.CrawlJournal;
import com.f5.scholarship_backend.domain.crawl.service.LeaseManager;
import com.f5.scholarship_backend.domain.crawl.service.ScholarshipCollector;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 실행자가 명시적으로 켰을 때만 학교 공고 한 건을 로컬 MySQL 테스트 DB에 보존한다.
 * 스키마나 기존 데이터는 삭제하지 않는다. CI의 일반 테스트에서는 실행하지 않는다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE,
		properties = {"crawler.enabled=false", "spring.jpa.hibernate.ddl-auto=none"})
@ActiveProfiles("mysql-crawl-check")
@EnabledIfEnvironmentVariable(named = "MYSQL_CRAWL_CHECK", matches = "true")
class GachonMysqlPersistenceTest {
	private static final Logger log = LoggerFactory.getLogger(GachonMysqlPersistenceTest.class);

	@Autowired private DataSource dataSource;
	@Autowired private JdbcTemplate jdbc;
	@Autowired private BoardClient client;
	@Autowired private GachonBoardParser parser;
	@Autowired private CrawlProperties properties;
	@Autowired private ScholarshipCollector collector;
	@Autowired private LeaseManager leases;
	@Autowired private CrawlJournal journal;

	/** 실제 공고 저장과 같은 수집본의 재저장 시 식별자·버전 불변을 검증한다. */
	@Test
	@Timeout(value = 3, unit = TimeUnit.MINUTES)
	void storesOneRealPostAndDoesNotDuplicateTheSameSnapshot() throws Exception {
		// 접속 대상이 다르면 임대 획득 등 최초 쓰기를 하기 전에 중단한다.
		try (var connection = dataSource.getConnection()) {
			assertThat(connection.getMetaData().getDatabaseProductName()).isEqualTo("MySQL");
			assertThat(connection.getCatalog()).isEqualTo("scholarship_crawl_test");
			assertThat(connection.getMetaData().getURL())
					.startsWith("jdbc:mysql://127.0.0.1:3306/scholarship_crawl_test");
		}
		String owner = leases.acquire();
		assertThat(owner).as("다른 수집 실행이 없어야 합니다.").isNotNull();
		Long runId = null;
		boolean completed = false;
		try {
			String requestId = "req-" + UUID.randomUUID();
			URI base = properties.boardUrl();
			runId = journal.start(base.toString(), requestId, owner);
			var page = parser.parseList(client.get(base), base, 1);
			assertThat(page.entries()).isNotEmpty();
			var entry = page.entries().get(0);
			journal.discover(runId, 1, owner);
			var post = parser.parseDetail(client.get(entry.url()), entry);

			var versionsBeforeCollect = jdbc.queryForList("""
					select v.version, v.title, v.source_text, v.origin from scholarship_versions v
					join scholarships s on s.scholarship_id=v.scholarship_id
					where s.source_site='GACHON_KOR' and s.source_post_key=? and s.source_item_key='main'
					order by v.version
					""", post.postKey());
			String first = collector.collect(post, owner);
			assertThat(first).isIn("COLLECTED", "SKIPPED", "HELD");
			Long id = jdbc.queryForObject("""
					select scholarship_id from scholarships
					where source_site='GACHON_KOR' and source_post_key=? and source_item_key='main'
					""", Long.class, post.postKey());
			Long versionsBefore = jdbc.queryForObject(
					"select count(*) from scholarship_versions where scholarship_id=?", Long.class, id);

			assertThat(collector.collect(post, owner)).isEqualTo(first.equals("HELD") ? "HELD" : "SKIPPED");
			assertThat(jdbc.queryForObject("""
					select count(*) from scholarships
					where source_site='GACHON_KOR' and source_post_key=? and source_item_key='main'
					""", Long.class, post.postKey())).isEqualTo(1L);
			assertThat(jdbc.queryForObject("select count(*) from scholarship_versions where scholarship_id=?",
					Long.class, id)).isEqualTo(versionsBefore);
			if (first.equals("HELD")) {
				assertThat(jdbc.queryForList("""
						select version, title, source_text, origin from scholarship_versions
						where scholarship_id=? order by version
						""", id)).isEqualTo(versionsBeforeCollect);
				assertThat(jdbc.queryForObject(
						"select requires_review from scholarship_crawl_state where scholarship_id=?",
						Boolean.class, id)).isTrue();
				log.info("MySQL 관리자 정정 보존·원문 보류 확인: scholarshipId={}", id);
			} else {
				// 최신 ADMIN/AI 버전이 아니라 마지막으로 반영한 CRAWLER 버전을 비교한다.
				String storedBody = jdbc.queryForObject("""
						select v.source_text from scholarship_versions v
						join scholarship_crawl_state c on c.scholarship_id=v.scholarship_id
						  and c.last_imported_version=v.version
						where v.scholarship_id=?
						""", String.class, id);
				assertThat(storedBody).isEqualTo(post.bodyHtml());
			}

			journal.outcome(runId, first, owner);
			journal.finish(runId, false, owner);
			completed = true;
			log.info("MySQL 한 건 검증 성공: runId={}, scholarshipId={}, postKey={}, versionCount={}, firstOutcome={}",
					runId, id, post.postKey(), versionsBefore, first);
		} finally {
			try {
				if (runId != null && !completed) {
					journal.error(runId, "CRAWL_FETCH_FAILED", null, owner);
					journal.finish(runId, true, owner);
				}
			} finally {
				leases.release(owner);
			}
		}
	}
}