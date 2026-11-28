package com.f5.scholarship_backend.domain.scholarship.entity;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.UUID;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.domain.Persistable;

@Entity
@Table(name = "scholarship_conditions")
@IdClass(ScholarshipCondition.Key.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ScholarshipCondition implements Persistable<ScholarshipCondition.Key> {
	@Id @Column(name = "scholarship_id") private Long scholarshipId;
	@Id @Column(name = "version") private int version;
	@Id @JdbcTypeCode(SqlTypes.CHAR)
	@Column(name = "condition_id", length = 36) private String conditionId;
	@Column(name = "field_name", nullable = false, length = 40) private String fieldName;
	@Column(nullable = false, length = 8) private String operator;
	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "expected_value") private String expectedValue;
	@Column(nullable = false, columnDefinition = "TEXT") private String description;
	@JdbcTypeCode(SqlTypes.TINYINT)
	@Column(name = "is_required", nullable = false) private boolean required;
	@Column(name = "source_quote", columnDefinition = "TEXT") private String sourceQuote;

	@Column(name = "gpa_scale", precision = 4, scale = 2) private BigDecimal gpaScale;
	@Column(name = "source_document_id") private Long sourceDocumentId;
	@Column(name = "source_page") private Integer sourcePage;
	@Column(name = "source_section", length = 255) private String sourceSection;
	@Transient private boolean newCondition = true;

	public ScholarshipCondition(long scholarshipId, int version, String fieldName, String operator,
			String expectedValue, String description, boolean required, String sourceQuote, BigDecimal gpaScale,
			Long sourceDocumentId, Integer sourcePage, String sourceSection) {
		this.scholarshipId = scholarshipId;
		this.version = version;
		this.conditionId = UUID.randomUUID().toString();
		this.fieldName = fieldName;
		this.operator = operator;
		this.expectedValue = expectedValue;
		this.description = description;
		this.required = required;
		this.sourceQuote = sourceQuote;
		this.gpaScale = gpaScale;
		this.sourceDocumentId = sourceDocumentId;
		this.sourcePage = sourcePage;
		this.sourceSection = sourceSection;
	}

	@Override
	public Key getId() {
		return new Key(scholarshipId, version, conditionId);
	}

	@Override
	public boolean isNew() {
		return newCondition;
	}

	@PostPersist
	@PostLoad
	private void markPersisted() {
		newCondition = false;
	}

	public record Key(Long scholarshipId, int version, String conditionId) implements Serializable { }
}
