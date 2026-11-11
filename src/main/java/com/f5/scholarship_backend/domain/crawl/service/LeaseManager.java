package com.f5.scholarship_backend.domain.crawl.service;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import com.f5.scholarship_backend.domain.crawl.config.CrawlProperties;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class LeaseManager {
	private final JdbcTemplate jdbc;
	private final CrawlProperties properties;
	private final TransactionTemplate transaction;
	private volatile String activeOwner;

	public LeaseManager(JdbcTemplate jdbc, CrawlProperties properties, PlatformTransactionManager manager) {
		this.jdbc = jdbc; this.properties = properties;
		transaction = new TransactionTemplate(manager);
		transaction.setTimeout(10);
		transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
	}

	/** DB 시각과 원자적 갱신으로 다중 서버 중 한 실행만 임대를 얻는다. */
	public synchronized String acquire() {
		if (activeOwner != null) { return null; }
		String owner = UUID.randomUUID().toString();
		Boolean acquired = transaction.execute(status -> {
			Instant now = databaseNow();
			int updated = jdbc.update("update crawler_leases set owner=?, locked_until=? where name='gachon-scholarships' and (owner is null or locked_until<=?)",
					owner, Timestamp.from(now.plus(properties.leaseDuration())), Timestamp.from(now));
			if (updated == 1) { return true; }
			try {
				jdbc.update("insert into crawler_leases(name,owner,locked_until) values('gachon-scholarships',?,?)",
						owner, Timestamp.from(now.plus(properties.leaseDuration())));
				return true;
			} catch (DuplicateKeyException occupied) { return false; }
		});
		if (Boolean.TRUE.equals(acquired)) { activeOwner = owner; return owner; }
		return null;
	}

	/** 실행 트랜잭션에서 임대 행을 잠가 임대 유실 이후의 쓰기를 차단한다. */
	public void assertHeld(String owner) {
		var owners = jdbc.query("select owner from crawler_leases where name='gachon-scholarships' and locked_until>CURRENT_TIMESTAMP for update",
				(rs, row) -> rs.getString(1));
		if (Thread.currentThread().isInterrupted() || owners.isEmpty() || !owner.equals(owners.get(0))) {
			throw new IllegalStateException("수집 실행 임대가 유효하지 않습니다.");
		}
	}

	/** 실행 스레드와 별도로 1분마다 임대를 갱신하며 갱신 실패 시 실행권을 잃는다. */
	@Scheduled(fixedDelay = 60000)
	public void heartbeat() {
		String owner = activeOwner;
		if (owner == null) { return; }
		try {
			transaction.executeWithoutResult(status -> {
				Instant now = databaseNow();
				int changed = jdbc.update("update crawler_leases set locked_until=? where name='gachon-scholarships' and owner=? and locked_until>?",
						Timestamp.from(now.plus(properties.leaseDuration())), owner, Timestamp.from(now));
				if (changed == 0) { activeOwner = null; }
			});
		} catch (RuntimeException failure) { activeOwner = null; }
	}

	/** 자신의 임대만 해제하며 다른 서버의 새 임대를 삭제하지 않는다. */
	public synchronized void release(String owner) {
		try {
			transaction.executeWithoutResult(status -> jdbc.update(
					"update crawler_leases set owner=null where name='gachon-scholarships' and owner=?", owner));
		} finally { if (owner.equals(activeOwner)) { activeOwner = null; } }
	}

	/** 서버 간 시계 오차를 피하기 위해 DB 현재 시각으로 만료를 계산한다. */
	private Instant databaseNow() {
		return jdbc.queryForObject("select CURRENT_TIMESTAMP", Timestamp.class).toInstant();
	}
}