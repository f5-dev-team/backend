package com.f5.scholarship_backend.domain.crawl;

import java.net.URI;
import com.f5.scholarship_backend.domain.crawl.client.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import static org.assertj.core.api.Assertions.assertThat;

/** 명시적으로 활성화한 개발 검증에서만 학교 목록·상세를 각 한 번 읽는다. DB와 S3는 변경하지 않는다. */
@EnabledIfEnvironmentVariable(named = "CRAWLER_LIVE_CHECK", matches = "true")
class GachonLiveTest {
	@Test void readsOneRealListAndDetail() {
		var base = URI.create("https://www.gachon.ac.kr/bbs/kor/478/artclList.do");
		var client = new GachonHttpClient(GachonHttpClientTest.properties(base));
		var parser = new GachonBoardParser();
		var page = parser.parseList(client.get(base), base, 1);
		assertThat(page.entries()).isNotEmpty();
		var detail = parser.parseDetail(client.get(page.entries().get(0).url()), page.entries().get(0));
		assertThat(detail.title()).isNotBlank();
		assertThat(detail.postKey()).isEqualTo(page.entries().get(0).postKey());
		assertThat(detail.bodyHtml()).isNotBlank();
	}
}