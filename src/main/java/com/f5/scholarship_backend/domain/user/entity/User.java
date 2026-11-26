package com.f5.scholarship_backend.domain.user.entity;

import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import com.f5.scholarship_backend.domain.user.enums.UserRole;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;

@Entity
@Table(name = "users", uniqueConstraints = @UniqueConstraint(
		name = "uk_users_email", columnNames = "email"))
@Getter
public class User {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "user_id")
	private Long id;

	@Column(nullable = false, length = 254)
	private String email;

	@Column(name = "password_hash", nullable = false, length = 255)
	private String passwordHash;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 16)
	private UserRole role;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	@Column(nullable = false, length = 30)
	private String nickname;

	@Column(name = "terms_agreed_at", nullable = false)
	private Instant termsAgreedAt;

	@Column(name = "notification_agreed", nullable = false)
	private boolean notificationAgreed;

	protected User() {}

	private User(String email, String passwordHash, UserRole role, Instant createdAt,
			String nickname, Instant termsAgreedAt, boolean notificationAgreed) {
		this.email = normalizeEmail(email);
		this.passwordHash = Objects.requireNonNull(passwordHash, "passwordHash");
		this.role = Objects.requireNonNull(role, "role");
		this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
		this.nickname = Objects.requireNonNull(nickname, "nickname");
		this.termsAgreedAt = Objects.requireNonNull(termsAgreedAt, "termsAgreedAt");
		this.notificationAgreed = notificationAgreed;
	}

	public static User create(String email, String passwordHash, String nickname,
			Instant termsAgreedAt, boolean notificationAgreed) {
		return create(email, passwordHash, UserRole.USER, nickname, termsAgreedAt, notificationAgreed);
	}

	public static User create(String email, String passwordHash, UserRole role, String nickname,
			Instant termsAgreedAt, boolean notificationAgreed) {
		return new User(email, passwordHash, role, Instant.now(), nickname, termsAgreedAt, notificationAgreed);
	}

	public static String normalizeEmail(String email) {
		return Objects.requireNonNull(email, "email").trim().toLowerCase(Locale.ROOT);
	}

	@PrePersist
	private void normalizeEmailBeforePersist() {
		email = normalizeEmail(email);
	}
}
