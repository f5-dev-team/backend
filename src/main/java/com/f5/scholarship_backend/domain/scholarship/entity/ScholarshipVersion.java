package com.f5.scholarship_backend.domain.scholarship.entity;

import java.io.Serializable;
import java.time.Instant;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "scholarship_versions")
@IdClass(ScholarshipVersion.Key.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ScholarshipVersion {
	@Id @Column(name = "scholarship_id") private Long scholarshipId;
	@Id @Column(name = "version") private int version;
	@Column(nullable = false, length = 255) private String title;
	private String organization;
	@Lob private String description;
	@Column(name = "application_start_at") private Instant applicationStartAt;
	@Column(name = "application_end_at") private Instant applicationEndAt;
	@Lob @Column(name = "application_period_text") private String applicationPeriodText;
	@Column(name = "source_url", nullable = false, length = 2048) private String sourceUrl;
	@Lob @Column(name = "source_text", columnDefinition = "LONGTEXT") private String sourceText;
	@Lob @Column(name = "source_attachments", columnDefinition = "LONGTEXT") private String sourceAttachments;
	@Column(name = "changed_by_user_id") private Long changedByUserId;
	@Column(name = "created_at", nullable = false) private Instant createdAt;
	@Column(nullable = false, length = 16) private String origin;

	/** 확정 날짜를 추측하지 않고 새 크롤링 원문 버전을 추가한다. */
	public ScholarshipVersion(Long scholarshipId, int version, String title, String sourceUrl,
			String sourceText, String sourceAttachments, Instant now) {
		this.scholarshipId = scholarshipId;
		this.version = version;
		this.title = title;
		this.sourceUrl = sourceUrl;
		this.sourceText = sourceText;
		this.sourceAttachments = sourceAttachments;
		this.createdAt = now;
		this.origin = "CRAWLER";
	}

	/** 복합 기본키는 JPA 직렬화와 값 동등성을 지원한다. */
	public record Key(Long scholarshipId, int version) implements Serializable { }
}