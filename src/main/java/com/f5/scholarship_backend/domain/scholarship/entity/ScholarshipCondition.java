package com.f5.scholarship_backend.domain.scholarship.entity;

import java.io.Serializable;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "scholarship_conditions")
@IdClass(ScholarshipCondition.Key.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ScholarshipCondition {
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

	public record Key(Long scholarshipId, int version, String conditionId) implements Serializable { }
}
