package com.f5.scholarship_backend.domain.crawl.client;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;
import com.f5.scholarship_backend.domain.crawl.config.CrawlProperties;
import org.springframework.stereotype.Component;

@Component
public class GachonHttpClient implements BoardClient {
	private final CrawlProperties properties;
	private final HttpClient client;
	private long lastStart;

	public GachonHttpClient(CrawlProperties properties) {
		this.properties = properties;
		this.client = HttpClient.newBuilder().connectTimeout(properties.connectTimeout())
				.followRedirects(HttpClient.Redirect.NEVER).build();
	}

	/** 단일 연결·속도 제한·유한 재시도로 HTML을 수집하며 응답 크기를 제한한다. */
	@Override
	public synchronized String get(URI uri) {
		if (!sameSource(uri)) { throw new CrawlException(); }
		int redirects = 0;
		for (int attempt = 0; attempt < 3; attempt++) {
			try {
				long wait = properties.interval().toNanos() - (System.nanoTime() - lastStart);
				if (lastStart != 0 && wait > 0) { sleep(Duration.ofNanos(wait)); }
				lastStart = System.nanoTime();
				var request = HttpRequest.newBuilder(uri).timeout(properties.requestTimeout())
						.header("User-Agent", "F5-ScholarshipCrawler/1.0")
						.header("Accept", "text/html").GET().build();
				var response = client.send(request, info -> new LimitedBody(properties.maxHtmlBytes()));
				int status = response.statusCode();
				if (status == 301 || status == 302 || status == 303 || status == 307 || status == 308) {
					if (++redirects > 3) { throw new CrawlException(); }
					URI target = uri.resolve(response.headers().firstValue("Location").orElseThrow(CrawlException::new));
					if (!sameSource(target)) { throw new CrawlException(); }
					uri = target;
					attempt--;
					continue;
				}
				if (status == 200) {
					String type = response.headers().firstValue("Content-Type").orElse("");
					if (!type.toLowerCase().contains("text/html")) { throw new CrawlException(); }
					return new String(response.body(), StandardCharsets.UTF_8);
				}
				if (status != 408 && status != 429 && (status < 500 || status > 599)) { throw new CrawlException(); }
				if (attempt == 2) { throw new CrawlException(); }
				long delay = attempt == 0 ? 5 : 15;
				if (status == 429 || status == 503) {
					delay = Math.max(delay, retryAfter(response.headers().firstValue("Retry-After").orElse("")));
				}
				if (delay > 900) { throw new CrawlException(); }
				sleep(Duration.ofSeconds(delay));
			} catch (InterruptedException exception) {
				Thread.currentThread().interrupt();
				throw new CrawlException();
			} catch (java.io.IOException exception) {
				if (Thread.currentThread().isInterrupted() || attempt == 2) { throw new CrawlException(); }
				try { sleep(Duration.ofSeconds(attempt == 0 ? 5 : 15)); }
				catch (InterruptedException interrupted) {
					Thread.currentThread().interrupt();
					throw new CrawlException();
				}
			}
		}
		throw new CrawlException();
	}

	/** 외부 호스트를 차단하고 게시판 및 확인된 상세 표시 경로만 허용한다. */
	private boolean sameSource(URI uri) {
		URI base = properties.boardUrl();
		return base.getScheme().equalsIgnoreCase(uri.getScheme())
				&& base.getHost().equalsIgnoreCase(uri.getHost()) && base.getPort() == uri.getPort()
				&& uri.getUserInfo() == null && (uri.getPath().startsWith("/bbs/kor/478/") || uri.getPath().equals("/kor/1146/subview.do"));
	}

	/** 초 또는 HTTP 날짜의 재시도 지시를 해석하며 무효한 값은 기본 대기로 처리한다. */
	private long retryAfter(String value) {
		try { return Math.max(0, Long.parseLong(value.strip())); }
		catch (NumberFormatException ignored) {
			try { return Math.max(0, Duration.between(java.time.Instant.now(),
					ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant()).getSeconds()); }
			catch (java.time.DateTimeException invalid) { return 0; }
		}
	}

	/** 인터럽트 가능한 대기로 실행 시간 제한 시 종료를 지원한다. */
	private void sleep(Duration delay) throws InterruptedException {
		Thread.sleep(delay.toMillis(), (int) (delay.toNanosPart() % 1_000_000));
	}

	/** 수신 중 크기를 검사하여 과도한 HTML 응답의 메모리 적재를 중단한다. */
	private static final class LimitedBody implements HttpResponse.BodySubscriber<byte[]> {
		private final HttpResponse.BodySubscriber<byte[]> delegate = HttpResponse.BodySubscribers.ofByteArray();
		private final int limit;
		private int received;
		private Flow.Subscription subscription;
		LimitedBody(int limit) { this.limit = limit; }
		@Override public CompletionStage<byte[]> getBody() { return delegate.getBody(); }
		@Override public void onSubscribe(Flow.Subscription value) { subscription = value; delegate.onSubscribe(value); }
		@Override public void onNext(List<ByteBuffer> buffers) {
			for (ByteBuffer buffer : buffers) {
				if (buffer.remaining() > limit - received) {
					subscription.cancel();
					delegate.onError(new java.io.IOException("HTML 응답 크기 제한 초과"));
					return;
				}
				received += buffer.remaining();
			}
			delegate.onNext(buffers);
		}
		@Override public void onError(Throwable error) { delegate.onError(error); }
		@Override public void onComplete() { delegate.onComplete(); }
	}
}