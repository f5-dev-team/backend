package com.f5.scholarship_backend.domain.user;

import java.time.Instant;
import com.f5.scholarship_backend.domain.user.entity.User;
import com.f5.scholarship_backend.domain.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@Transactional
@ActiveProfiles("test")
class UserRepositoryTest {
	@Autowired UserRepository users;

	@Test
	void savesUserWithNormalizedEmailAndUtcInstants() {
		Instant termsAgreedAt = Instant.parse("2026-01-02T03:04:05Z");
		User saved = users.saveAndFlush(User.create(
				"  Example.User@GACHON.AC.KR  ", "encoded-password", "학생", termsAgreedAt, true));

		assertThat(saved.getId()).isNotNull();
		assertThat(saved.getEmail()).isEqualTo("example.user@gachon.ac.kr");
		assertThat(saved.getCreatedAt()).isNotNull();
		assertThat(saved.getTermsAgreedAt()).isEqualTo(termsAgreedAt);
		assertThat(saved.isNotificationAgreed()).isTrue();
	}

	@Test
	void findsAndChecksEmailAfterNormalizingLookupValue() {
		users.saveAndFlush(User.create(
				"student@gachon.ac.kr", "encoded-password", "학생",
				Instant.parse("2026-01-02T03:04:05Z"), false));

		assertThat(users.existsByEmail("  STUDENT@GACHON.AC.KR ")).isTrue();
		assertThat(users.findByEmail("  STUDENT@GACHON.AC.KR "))
				.isPresent()
				.get()
				.extracting(User::getEmail)
				.isEqualTo("student@gachon.ac.kr");
		assertThat(users.existsByEmail("missing@gachon.ac.kr")).isFalse();
	}

	@Test
	void rejectsDuplicateNormalizedEmailAtDatabaseConstraint() {
		Instant termsAgreedAt = Instant.parse("2026-01-02T03:04:05Z");
		users.saveAndFlush(User.create(
				"student@gachon.ac.kr", "encoded-password", "첫 사용자", termsAgreedAt, false));

		assertThatThrownBy(() -> users.saveAndFlush(User.create(
				" STUDENT@GACHON.AC.KR ", "another-encoded-password", "중복 사용자", termsAgreedAt, false)))
				.isInstanceOf(DataIntegrityViolationException.class);
	}
}
