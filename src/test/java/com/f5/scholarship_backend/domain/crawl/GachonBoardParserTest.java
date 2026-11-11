package com.f5.scholarship_backend.domain.crawl;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import com.f5.scholarship_backend.domain.crawl.client.*;
import com.f5.scholarship_backend.domain.crawl.model.BoardPage;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class GachonBoardParserTest {
	private final GachonBoardParser parser = new GachonBoardParser();
	private final URI base = URI.create("https://www.gachon.ac.kr/bbs/kor/478/artclList.do");

	@Test void parsesPinnedAndNormalEntriesWithRealBoardSelectors() throws Exception {
		var page = parser.parseList(fixture("list"), base, 1);
		assertThat(page.hasNext()).isTrue();
		assertThat(page.entries()).hasSize(3);
		assertThat(page.entries().get(0).pinned()).isTrue();
		assertThat(page.entries().get(0).postKey()).isEqualTo(page.entries().get(1).postKey());
		assertThat(page.entries().get(2).date()).isEqualTo(LocalDate.of(2026, 11, 8));
	}
	@Test void preservesTableAndOnlyDownloadLinks() throws Exception {
		var post = parser.parseDetail(fixture("detail"), entry("478:1001"));
		assertThat(post.title()).isEqualTo("합성 장학금 공고");
		assertThat(post.bodyHtml()).contains("<table>", "학점 3.5", "소득 4");
		assertThat(post.attachments()).hasSize(2);
		assertThat(post.attachments().get(0).url().toString()).endsWith("/7001/download.do");
	}
	@Test void changedOrWrongPageIsFailureRatherThanEmptySuccess() throws Exception {
		assertThatThrownBy(() -> parser.parseList("<html>로그인 오류</html>", base, 1)).isInstanceOf(CrawlException.class);
		assertThatThrownBy(() -> parser.parseDetail(fixture("detail"), entry("478:9999"))).isInstanceOf(CrawlException.class);
		assertThatThrownBy(() -> parser.parseDetail("<h2 class='view-title'>제목</h2>", entry("478:1001"))).isInstanceOf(CrawlException.class);
	}
	private BoardPage.Entry entry(String key) {
		return new BoardPage.Entry(key, base.resolve("/bbs/kor/478/1001/artclView.do"), LocalDate.of(2026, 11, 9), false);
	}
	private String fixture(String name) throws Exception {
		try (var input = getClass().getResourceAsStream("/crawler/" + name + ".html")) {
			return new String(input.readAllBytes(), StandardCharsets.UTF_8);
		}
	}
}