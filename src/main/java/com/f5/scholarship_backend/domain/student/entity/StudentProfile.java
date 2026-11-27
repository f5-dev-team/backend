package com.f5.scholarship_backend.domain.student.entity;

import java.time.Instant;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import com.f5.scholarship_backend.domain.student.dto.StudentProfileResponse;
import com.f5.scholarship_backend.domain.user.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "student_profiles")
public class StudentProfile {
	private static final ZoneId KOREA = ZoneId.of("Asia/Seoul");

	@Id
	@Column(name = "user_id", nullable = false)
	private Long userId;

	@MapsId
	@OneToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "user_id", nullable = false)
	private User user;

	@Embedded
	private StudentProfileData data;

	@Column(name = "version", nullable = false)
	private int version;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	protected StudentProfile() {
	}

	public static StudentProfile create(User user, StudentProfileData data, Instant savedAt) {
		StudentProfile profile = new StudentProfile();
		profile.user = user;
		profile.data = data;
		profile.version = 1;
		profile.updatedAt = savedAt.truncatedTo(ChronoUnit.MICROS);
		return profile;
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
		return storedData().toResponse(version, updatedAt.atZone(KOREA).toOffsetDateTime());
	}

	private StudentProfileData storedData() {
		// Hibernate materializes an entirely null embeddable as null.
		return data == null ? StudentProfileData.EMPTY : data;
	}
}
