package com.f5.scholarship_backend.domain.analysis.entity;

import java.time.Instant;
import com.f5.scholarship_backend.domain.analysis.enums.AnalysisStatus;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "analyses")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AnalysisJob {
	@Id @GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "analysis_id") private Long id;
	@Column(name = "user_id", nullable = false) private Long userId;
	@Column(name = "profile_version", nullable = false) private long profileVersion;
	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "profile_snapshot", nullable = false) private String profileSnapshot;
	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 16) private AnalysisStatus status;
	@Column(name = "failure_code", length = 64) private String failureCode;
	@Column(name = "created_at", nullable = false) private Instant createdAt;
	@Column(name = "started_at") private Instant startedAt;
	@Column(name = "completed_at") private Instant completedAt;

	public AnalysisJob(Long userId, long profileVersion, String profileSnapshot, Instant createdAt) {
		this.userId = userId;
		this.profileVersion = profileVersion;
		this.profileSnapshot = profileSnapshot;
		this.createdAt = createdAt;
		this.status = AnalysisStatus.QUEUED;
	}
}
