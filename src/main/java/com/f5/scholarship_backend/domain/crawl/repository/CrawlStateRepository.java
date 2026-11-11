package com.f5.scholarship_backend.domain.crawl.repository;
import com.f5.scholarship_backend.domain.crawl.entity.CrawlState;
import org.springframework.data.jpa.repository.JpaRepository;
public interface CrawlStateRepository extends JpaRepository<CrawlState, Long> { }