package com.f5.scholarship_backend.domain.crawl.entity;

import java.time.Instant;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@Entity @Table(name = "crawler_leases")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CrawlerLease {
	@Id @Column(length = 64) private String name;
	@Column(length = 64) private String owner;
	@Column(name = "locked_until", nullable = false) private Instant lockedUntil;
	@Column(nullable = false) private boolean cancelled;
}