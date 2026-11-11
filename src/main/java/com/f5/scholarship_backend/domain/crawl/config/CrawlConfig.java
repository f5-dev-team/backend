package com.f5.scholarship_backend.domain.crawl.config;

import java.time.Clock;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
@EnableConfigurationProperties(CrawlProperties.class)
public class CrawlConfig {
	/** 시간 기준을 주입하여 테스트에서 실행일을 고정할 수 있게 한다. */
	@Bean
	Clock crawlClock() {
		return Clock.systemUTC();
	}
}