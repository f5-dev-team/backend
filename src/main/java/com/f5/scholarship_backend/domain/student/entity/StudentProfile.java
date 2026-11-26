package com.f5.scholarship_backend.domain.student.entity;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import com.f5.scholarship_backend.domain.student.dto.StudentProfileResponse;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "student_profiles")
public class StudentProfile {
	@Id
	@Column(name = "user_id")
	private Long userId;

	@Embedded
	private StudentProfileData data;

	@Column(nullable = false)
	private long version;

	@Column(nullable = false)
	private Instant updatedAt;

	protected StudentProfile() {
	}

	public StudentProfile(long userId, StudentProfileData data, Instant savedAt) {
		this.userId = userId;
		this.data = data;
		this.version = 1;
		this.updatedAt = savedAt.truncatedTo(ChronoUnit.MICROS);
	}

	public void replace(StudentProfileData newData, Instant savedAt) {
		if (storedData().equals(newData)) {
			return;
		}
		this.data = newData;
		this.version++;
		this.updatedAt = savedAt.truncatedTo(ChronoUnit.MICROS);
	}

	public StudentProfileResponse toResponse() {
		return storedData().toResponse(version, updatedAt.atOffset(ZoneOffset.UTC));
	}

	private StudentProfileData storedData() {
		// Hibernate materializes an entirely null embeddable as null.
		return data == null ? StudentProfileData.EMPTY : data;
	}
}
