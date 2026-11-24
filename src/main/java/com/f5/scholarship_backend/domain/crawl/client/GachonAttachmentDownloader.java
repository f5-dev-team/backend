package com.f5.scholarship_backend.domain.crawl.client;

import java.net.URI;
import java.net.http.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import org.springframework.stereotype.Component;
import com.f5.scholarship_backend.domain.crawl.config.CrawlProperties;
import com.f5.scholarship_backend.domain.crawl.model.CrawledPost;
import com.f5.scholarship_backend.domain.document.client.*;
import com.f5.scholarship_backend.domain.document.config.FileProperties;

@Component
public class GachonAttachmentDownloader implements AttachmentDownloader {
    private final CrawlProperties crawl;
    private final FileProperties files;
    private final HttpClient client;
    private final CrawlRequestThrottle throttle;
    public GachonAttachmentDownloader(CrawlProperties crawl, FileProperties files) {
        this(crawl, files, new CrawlRequestThrottle(crawl));
    }
    @org.springframework.beans.factory.annotation.Autowired
    public GachonAttachmentDownloader(CrawlProperties crawl, FileProperties files, CrawlRequestThrottle throttle) {
        this.crawl = crawl; this.files = files; this.throttle = throttle;
        client = HttpClient.newBuilder().connectTimeout(crawl.connectTimeout()).followRedirects(HttpClient.Redirect.NEVER).build();
    }
    /** 같은 출처의 다운로드 경로만 요청하며 리다이렉트·크기·시간·재시도를 제한한다. */
    @Override
    public synchronized byte[] download(CrawledPost.Attachment attachment) {
        URI uri = attachment.url();
        int redirects = 0;
        for (int attempt = 0; attempt < 3; attempt++) {
            validate(uri);
            try {
                if (Thread.currentThread().isInterrupted()) { throw DocumentException.download(); }
                throttle.await();
                var request = HttpRequest.newBuilder(uri).timeout(files.downloadTimeout())
                        .header("User-Agent", "F5-ScholarshipCrawler/1.0").header("Accept", "application/pdf, application/x-hwp, application/octet-stream")
                        .GET().build();
                var response = receive(request);
                /* 응답 전체 수신은 receive에서 시간·크기를 제한한다. */
                int status = response.statusCode();
                if (status == 301 || status == 302 || status == 303 || status == 307 || status == 308) {
                    if (++redirects > 3) { throw DocumentException.download(); }
                    uri = uri.resolve(response.headers().firstValue("Location").orElseThrow(DocumentException::download));
                    validate(uri); attempt--; continue;
                }
                if (status == 200) {
                    if (response.body().length == 0) { throw DocumentException.download(); }
                    return response.body();
                }
                if (attempt == 2 || (status != 408 && status != 429 && (status < 500 || status > 599))) {
                    throw DocumentException.download();
                }
                long delay = attempt == 0 ? 5 : 15;
                if (status == 429 || status == 503) {
                    delay = Math.max(delay, retryAfter(response.headers().firstValue("Retry-After").orElse("")));
                }
                if (delay > 900) { throw DocumentException.download(); }
                sleep(Duration.ofSeconds(delay));
            } catch (InterruptedException failure) {
                Thread.currentThread().interrupt(); throw DocumentException.download();
            } catch (java.io.IOException failure) {
                if (attempt == 2 || Thread.currentThread().isInterrupted()) { throw DocumentException.download(); }
                try { sleep(Duration.ofSeconds(attempt == 0 ? 5 : 15)); }
                catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); throw DocumentException.download(); }
            }
        }
        throw DocumentException.download();
    }

    /** 헤더 이후 본문 수신에도 제한 시간을 적용하고 중단 시 HTTP 요청을 취소한다. */
    private HttpResponse<byte[]> receive(HttpRequest request) throws java.io.IOException, InterruptedException {
        var pending = client.sendAsync(request, info -> {
            if (info.statusCode() != 200) { return HttpResponse.BodySubscribers.replacing(new byte[0]); }
            if (info.headers().firstValueAsLong("Content-Length").orElse(0) > files.maxFileBytes()) {
                throw DocumentException.limitExceeded();
            }
            return new GachonHttpClient.LimitedBody(files.maxFileBytes(), DocumentException::limitExceeded);
        });
        try { return pending.get(files.downloadTimeout().toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS); }
        catch (java.util.concurrent.ExecutionException failure) {
            Throwable cause = failure;
            for (int depth = 0; cause != null && depth < 16; depth++, cause = cause.getCause()) {
                // 비동기 래퍼를 풀어 크기 한도 초과 사유를 호출자까지 전달한다.
                if (cause instanceof DocumentException documentFailure) { throw documentFailure; }
            }
            throw new java.io.IOException("파일 응답 수신 실패");
        } catch (java.util.concurrent.TimeoutException failure) { throw new java.io.IOException("파일 응답 시간 초과"); }
        finally { if (!pending.isDone()) { pending.cancel(true); } }
    }
    private void validate(URI uri) {
        URI base = crawl.boardUrl();
        if (uri.getHost() == null || uri.getUserInfo() != null || uri.getFragment() != null
                || !base.getScheme().equalsIgnoreCase(uri.getScheme()) || !base.getHost().equalsIgnoreCase(uri.getHost())
                || port(base) != port(uri) || uri.getPath() == null || !uri.getPath().matches("/bbs/kor/478/[0-9]+/download\\.do")) {
            throw DocumentException.download();
        }
    }
    private int port(URI uri) { return uri.getPort() >= 0 ? uri.getPort() : ("https".equalsIgnoreCase(uri.getScheme()) ? 443 : 80); }
    private long retryAfter(String value) {
        try { return Math.max(0, Long.parseLong(value.strip())); }
        catch (NumberFormatException ignored) {
            try { return Math.max(0, Duration.between(Instant.now(), ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant()).getSeconds()); }
            catch (DateTimeException invalid) { return 0; }
        }
    }
    private void sleep(Duration duration) throws InterruptedException { Thread.sleep(duration.toMillis(), duration.toNanosPart() % 1_000_000); }
}
