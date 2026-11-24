package com.f5.scholarship_backend.domain.crawl;

import java.net.URI;
import java.time.Duration;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import com.sun.net.httpserver.HttpServer;
import com.f5.scholarship_backend.domain.crawl.client.GachonAttachmentDownloader;
import com.f5.scholarship_backend.domain.crawl.model.CrawledPost;
import com.f5.scholarship_backend.domain.document.config.FileProperties;
import com.f5.scholarship_backend.domain.document.client.DocumentException;
import org.junit.jupiter.api.*;
import static org.assertj.core.api.Assertions.*;

@Timeout(10)
class GachonAttachmentDownloaderTest {
    private HttpServer server;
    private URI base;
    private ExecutorService handler;
    private final AtomicInteger calls = new AtomicInteger();
    @BeforeEach void start() throws Exception {
        server = HttpServer.create(new java.net.InetSocketAddress("127.0.0.1", 0), 0);
        handler = Executors.newCachedThreadPool(); server.setExecutor(handler); server.start();
        base = URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/bbs/kor/478/artclList.do");
    }
    @AfterEach void stop() { server.stop(0); handler.shutdownNow(); }
    private GachonAttachmentDownloader downloader(int maxBytes) {
        return new GachonAttachmentDownloader(GachonHttpClientTest.properties(base), new FileProperties(false, "", "",
                "scholarship-originals", maxBytes, 10, maxBytes * 10L, Duration.ofSeconds(2)));
    }
    private CrawledPost.Attachment attachment(String path) { return new CrawledPost.Attachment("a.pdf", base.resolve(path)); }
    @Test void receivesBinaryAndFollowsOnlySameOriginDownloadRedirect() {
        byte[] bytes = "%PDF-1.7\n%%EOF".getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        server.createContext("/bbs/kor/478/1/download.do", e -> {
            calls.incrementAndGet(); e.getResponseHeaders().set("Location", "/bbs/kor/478/2/download.do");
            e.sendResponseHeaders(302, -1); e.close();
        });
        server.createContext("/bbs/kor/478/2/download.do", e -> {
            calls.incrementAndGet(); e.sendResponseHeaders(200, bytes.length); e.getResponseBody().write(bytes); e.close();
        });
        assertThat(downloader(1024).download(attachment("/bbs/kor/478/1/download.do"))).isEqualTo(bytes);
        assertThat(calls.get()).isEqualTo(2);
    }
    @Test void rejectsCrossOriginRedirectWithoutSendingSecondRequest() {
        server.createContext("/bbs/kor/478/1/download.do", e -> {
            calls.incrementAndGet(); e.getResponseHeaders().set("Location", "http://example.invalid/bbs/kor/478/2/download.do");
            e.sendResponseHeaders(302, -1); e.close();
        });
        assertThatThrownBy(() -> downloader(1024).download(attachment("/bbs/kor/478/1/download.do"))).isInstanceOf(DocumentException.class);
        assertThat(calls.get()).isEqualTo(1);
    }
    @Test void rejectsUnknownLengthOversizedBodyWithoutRetry() {
        server.createContext("/bbs/kor/478/1/download.do", e -> {
            calls.incrementAndGet(); e.sendResponseHeaders(200, 0);
            try { e.getResponseBody().write(new byte[200]); } finally { e.close(); }
        });
        assertThatThrownBy(() -> downloader(100).download(attachment("/bbs/kor/478/1/download.do")))
                .isInstanceOfSatisfying(DocumentException.class,
                        failure -> assertThat(failure.isLimitExceeded()).isTrue());
        assertThat(calls.get()).isEqualTo(1);
    }
    @Test void rejectsOversizedContentLengthWithoutRetry() {
        server.createContext("/bbs/kor/478/1/download.do", e -> {
            calls.incrementAndGet(); e.sendResponseHeaders(200, 200); e.close();
        });
        assertThatThrownBy(() -> downloader(100).download(attachment("/bbs/kor/478/1/download.do")))
                .isInstanceOfSatisfying(DocumentException.class,
                        failure -> assertThat(failure.isLimitExceeded()).isTrue());
        assertThat(calls.get()).isEqualTo(1);
    }
    @Test void permanentErrorAndLongRetryAfterDoNotRetry() {
        server.createContext("/bbs/kor/478/1/download.do", e -> {
            calls.incrementAndGet(); e.getResponseHeaders().set("Retry-After", "901"); e.sendResponseHeaders(429, -1); e.close();
        });
        server.createContext("/bbs/kor/478/2/download.do", e -> {
            calls.incrementAndGet(); e.sendResponseHeaders(404, -1); e.close();
        });
        var client = downloader(100);
        assertThatThrownBy(() -> client.download(attachment("/bbs/kor/478/1/download.do"))).isInstanceOf(DocumentException.class);
        assertThatThrownBy(() -> client.download(attachment("/bbs/kor/478/2/download.do"))).isInstanceOf(DocumentException.class);
        assertThat(calls.get()).isEqualTo(2);
    }
    @Test void interruptedBodyReadStopsPromptlyAndRestoresInterrupt() throws Exception {
        var entered = new CountDownLatch(1); var stop = new CountDownLatch(1);
        server.createContext("/bbs/kor/478/1/download.do", e -> {
            e.sendResponseHeaders(200, 100); e.getResponseBody().write(1); e.getResponseBody().flush(); entered.countDown();
            try { stop.await(); } catch (InterruptedException ignored) { } finally { e.close(); }
        });
        var flag = new java.util.concurrent.atomic.AtomicBoolean();
        var failure = new java.util.concurrent.atomic.AtomicReference<Throwable>();
        var client = downloader(100);
        Thread caller = new Thread(() -> {
            try { client.download(attachment("/bbs/kor/478/1/download.do")); }
            catch (DocumentException expected) { flag.set(Thread.currentThread().isInterrupted()); }
            catch (Throwable unexpected) { failure.set(unexpected); }
        });
        try {
            caller.start(); assertThat(entered.await(3, TimeUnit.SECONDS)).isTrue(); caller.interrupt(); caller.join(2000);
            assertThat(caller.isAlive()).isFalse(); assertThat(failure.get()).isNull(); assertThat(flag.get()).isTrue();
        } finally { stop.countDown(); caller.interrupt(); }
    }
}
