package com.f5.scholarship_backend.domain.analysis.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "analysis_evidence")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AnalysisEvidence {
	@Id @GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "evidence_id") private Long id;
	@Column(name = "analysis_id", nullable = false) private Long analysisId;
	@Column(name = "scholarship_id", nullable = false) private Long scholarshipId;
	@Column(name = "scholarship_version", nullable = false) private int scholarshipVersion;
	@JdbcTypeCode(SqlTypes.CHAR)
	@Column(name = "condition_id", nullable = false, length = 36) private String conditionId;
	@Column(name = "document_id") private Long documentId;
	@Column(nullable = false, columnDefinition = "TEXT") private String quote;
	@Column(nullable = false, columnDefinition = "TEXT") private String explanation;
	@Column(name = "page_number") private Integer pageNumber;
	@Column(name = "section_label", length = 255) private String sectionLabel;
	@Column(name = "source_url", nullable = false, length = 2048) private String sourceUrl;
}
