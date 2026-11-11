package com.f5.scholarship_backend.domain.crawl.config;

import java.net.URI;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("crawler")
public record CrawlProperties(boolean enabled, URI boardUrl, String cron, String zone,
		int days, int maxPages, int maxPosts, Duration interval, Duration connectTimeout,
		Duration requestTimeout, int maxHtmlBytes, Duration runTimeout, Duration leaseDuration) {
}