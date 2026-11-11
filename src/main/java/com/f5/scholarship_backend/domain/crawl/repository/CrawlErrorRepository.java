package com.f5.scholarship_backend.domain.crawl.repository;
import com.f5.scholarship_backend.domain.crawl.entity.CrawlError;
import org.springframework.data.jpa.repository.JpaRepository;
public interface CrawlErrorRepository extends JpaRepository<CrawlError, Long> { }