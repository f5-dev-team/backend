package com.f5.scholarship_backend.domain.crawl.client;

import com.f5.scholarship_backend.domain.crawl.config.CrawlProperties;
import org.springframework.stereotype.Component;

/** 목록·상세·파일 요청이 같은 시작 간격을 공유한다. */
@Component
public class CrawlRequestThrottle {
    private final long intervalNanos;
    private long lastStart;
    public CrawlRequestThrottle(CrawlProperties properties) { intervalNanos = properties.interval().toNanos(); }
    public synchronized void await() throws InterruptedException {
        long wait = intervalNanos - (System.nanoTime() - lastStart);
        if (lastStart != 0 && wait > 0) { java.util.concurrent.TimeUnit.NANOSECONDS.sleep(wait); }
        if (Thread.currentThread().isInterrupted()) { throw new InterruptedException(); }
        lastStart = System.nanoTime();
    }
}
