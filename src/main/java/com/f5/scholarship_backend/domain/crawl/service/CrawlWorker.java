package com.f5.scholarship_backend.domain.crawl.service;

import java.net.URI;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.HashSet;
import com.f5.scholarship_backend.domain.crawl.client.*;
import com.f5.scholarship_backend.domain.crawl.config.CrawlProperties;
import com.f5.scholarship_backend.domain.crawl.model.BoardPage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;

@Service
public class CrawlWorker {
	private static final Logger log = LoggerFactory.getLogger(CrawlWorker.class);
	private final BoardClient client;
	private final GachonBoardParser parser;
	private final ScholarshipCollector collector;
	private final CrawlJournal journal;
	private final CrawlProperties properties;
	private final Clock clock;

	public CrawlWorker(BoardClient client, GachonBoardParser parser, ScholarshipCollector collector,
			CrawlJournal journal, CrawlProperties properties, Clock clock) {
		this.client = client; this.parser = parser; this.collector = collector;
		this.journal = journal; this.properties = properties; this.clock = clock;
	}

	/** 최근 범위와 고정 공지를 수집하며 중복·부분 실패·순회 한도를 명시적으로 집계한다. */
	public void execute(Long runId, String owner, String requestId) {
		var entries = new LinkedHashMap<String, BoardPage.Entry>();
		var pageSignatures = new HashSet<String>();
		boolean incomplete = false;
		LocalDate cutoff = LocalDate.now(clock.withZone(ZoneId.of(properties.zone()))).minusDays(properties.days() - 1L);
		LocalDate previous = null;
		boolean descending = true;
		try {
			for (int page = 1; page <= properties.maxPages(); page++) {
				checkInterrupted();
				URI uri = UriComponentsBuilder.fromUri(properties.boardUrl())
						.replaceQueryParam("page", page).build().toUri();
				var listing = parser.parseList(client.get(uri), uri, page);
				String signature = listing.entries().stream().filter(e -> !e.pinned()).map(BoardPage.Entry::postKey)
						.reduce("", (a, b) -> a + "," + b);
				if (!pageSignatures.add(signature)) { throw new CrawlException(); }
				boolean allOld = true;
				boolean hasRegular = false;
				for (var entry : listing.entries()) {
					if (!entry.pinned()) {
						hasRegular = true;
						if (previous != null && entry.date().isAfter(previous)) { descending = false; }
						previous = entry.date();
						if (!entry.date().isBefore(cutoff)) { allOld = false; }
					}
					if (entry.pinned() || !entry.date().isBefore(cutoff)) { entries.putIfAbsent(entry.postKey(), entry); }
				}
				journal.discover(runId, entries.size(), owner);
				if (!listing.hasNext() || (hasRegular && allOld && descending)) { break; }
				if (page == properties.maxPages()) { incomplete = true; }
			}
		} catch (RuntimeException failure) {
			checkInterrupted();
			incomplete = true;
			journal.error(runId, "CRAWL_FETCH_FAILED", properties.boardUrl().toString(), owner);
		}
		int processed = 0;
		for (var entry : entries.values()) {
			checkInterrupted();
			if (processed >= properties.maxPosts()) { incomplete = true; break; }
			try {
				var post = parser.parseDetail(client.get(entry.url()), entry);
				try {
					String outcome = collector.collect(post, owner);
					if (outcome.equals("HELD")) {
						log.warn("수집 후보 보류: requestId={}, postKey={}", requestId, entry.postKey());
					}
					journal.outcome(runId, outcome, owner);
				} catch (RuntimeException storageFailure) {
					checkInterrupted();
					journal.error(runId, "STORAGE_FAILED", entry.url().toString(), owner);
					journal.outcome(runId, "FAILED", owner);
				}
			} catch (RuntimeException fetchFailure) {
				checkInterrupted();
				journal.error(runId, "CRAWL_FETCH_FAILED", entry.url().toString(), owner);
				journal.outcome(runId, "FAILED", owner);
			}
			processed++;
		}
		if (incomplete) { journal.error(runId, "CRAWL_FETCH_FAILED", properties.boardUrl().toString(), owner); }
		journal.finish(runId, incomplete, owner);
	}

	/** 실행 취소 후 네트워크·저장 작업을 추가로 수행하지 않는다. */
	private void checkInterrupted() {
		if (Thread.currentThread().isInterrupted()) { throw new CrawlException(); }
	}
}