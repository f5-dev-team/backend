package com.f5.scholarship_backend.domain.crawl;

import java.net.URI;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import com.sun.net.httpserver.HttpServer;
import com.f5.scholarship_backend.domain.crawl.client.*;
import com.f5.scholarship_backend.domain.crawl.config.CrawlProperties;
import org.junit.jupiter.api.*;
import static org.assertj.core.api.Assertions.*;

class GachonHttpClientTest {
	private HttpServer server;
	private URI base;
	@BeforeEach void start() throws Exception {
		server = HttpServer.create(new java.net.InetSocketAddress("127.0.0.1", 0), 0);
		server.start();
		base = URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/bbs/kor/478/artclList.do");
	}
	@AfterEach void stop() { server.stop(0); }

	@Test void receivesUtf8HtmlWithAnIdentifiableUserAgent() {
		server.createContext("/bbs/kor/478/", exchange -> {
			assertThat(exchange.getRequestHeaders().getFirst("User-Agent")).contains("ScholarshipCrawler");
			byte[] body = "<html>합성 공고</html>".getBytes(java.nio.charset.StandardCharsets.UTF_8);
			exchange.getResponseHeaders().set("Content-Type", "text/html;charset=UTF-8");
			exchange.sendResponseHeaders(200, body.length);
			exchange.getResponseBody().write(body); exchange.close();
		});
		assertThat(new GachonHttpClient(properties(base)).get(base)).contains("합성 공고");
	}
	@Test void doesNotFollowRedirectOrRetryPermanentErrors() {
		var count = new AtomicInteger();
		server.createContext("/bbs/kor/478/", exchange -> {
			count.incrementAndGet();
			exchange.getResponseHeaders().set("Location", "https://example.invalid/");
			exchange.sendResponseHeaders(302, -1); exchange.close();
		});
		assertThatThrownBy(() -> new GachonHttpClient(properties(base)).get(base)).isInstanceOf(CrawlException.class);
		assertThat(count.get()).isEqualTo(1);
	}
	@Test void rejectsExternalOriginBeforeSendingRequest() {
		assertThatThrownBy(() -> new GachonHttpClient(properties(base))
				.get(URI.create("https://example.invalid/bbs/kor/478/artclList.do"))).isInstanceOf(CrawlException.class);
	}
	@Test void respectsLongRetryAfterByStoppingThisRun() {
		var count = new AtomicInteger();
		server.createContext("/bbs/kor/478/", exchange -> {
			count.incrementAndGet();
			exchange.getResponseHeaders().set("Retry-After", "901");
			exchange.sendResponseHeaders(429, -1); exchange.close();
		});
		assertThatThrownBy(() -> new GachonHttpClient(properties(base)).get(base)).isInstanceOf(CrawlException.class);
		assertThat(count.get()).isEqualTo(1);
	}
	@Test void retriesTemporaryStatusBeforeReturningSuccess() {
		var count = new AtomicInteger();
		server.createContext("/bbs/kor/478/", exchange -> {
			boolean fail = count.incrementAndGet() == 1;
			exchange.getResponseHeaders().set("Content-Type", "text/html");
			exchange.sendResponseHeaders(fail ? 503 : 200, 1);
			exchange.getResponseBody().write('x'); exchange.close();
		});
		assertThat(new GachonHttpClient(properties(base)).get(base)).isEqualTo("x");
		assertThat(count.get()).isEqualTo(2);
	}
	@Test void followsOnlyKnownSameOriginDisplayRedirect() {
		server.createContext("/bbs/kor/478/", exchange -> {
			exchange.getResponseHeaders().set("Location", "/kor/1146/subview.do?enc=synthetic");
			exchange.sendResponseHeaders(302, -1); exchange.close();
		});
		server.createContext("/kor/1146/subview.do", exchange -> {
			exchange.getResponseHeaders().set("Content-Type", "text/html");
			exchange.sendResponseHeaders(200, 1);
			exchange.getResponseBody().write('x'); exchange.close();
		});
		assertThat(new GachonHttpClient(properties(base)).get(base)).isEqualTo("x");
	}
	@Test void limitsSameOriginRedirectLoops() {
		var count = new AtomicInteger();
		server.createContext("/bbs/kor/478/", exchange -> {
			count.incrementAndGet();
			exchange.getResponseHeaders().set("Location", base.toString());
			exchange.sendResponseHeaders(302, -1); exchange.close();
		});
		assertThatThrownBy(() -> new GachonHttpClient(properties(base)).get(base)).isInstanceOf(CrawlException.class);
		assertThat(count.get()).isEqualTo(4);
	}
	static CrawlProperties properties(URI base) {
		return new CrawlProperties(false, base, "0 0 9,18 * * *", "Asia/Seoul",
				90, 20, 500, Duration.ofSeconds(2), Duration.ofSeconds(5), Duration.ofSeconds(20),
				5 * 1024 * 1024, Duration.ofMinutes(45), Duration.ofMinutes(60));
	}
}