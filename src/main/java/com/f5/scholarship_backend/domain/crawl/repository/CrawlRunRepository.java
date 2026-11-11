package com.f5.scholarship_backend.domain.crawl.repository;
import java.time.Instant;
import java.util.List;
import com.f5.scholarship_backend.domain.crawl.entity.CrawlRun;
import org.springframework.data.jpa.repository.JpaRepository;
public interface CrawlRunRepository extends JpaRepository<CrawlRun, Long> {
	List<CrawlRun> findByStatusAndStartedAtBefore(String status, Instant cutoff);
}