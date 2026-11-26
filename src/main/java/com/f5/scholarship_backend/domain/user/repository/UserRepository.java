package com.f5.scholarship_backend.domain.user.repository;

import java.util.Optional;
import com.f5.scholarship_backend.domain.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<User, Long> {
	@Query("select u from User u where u.email = :email")
	Optional<User> findByNormalizedEmail(@Param("email") String email);

	default Optional<User> findByEmail(String email) {
		return findByNormalizedEmail(User.normalizeEmail(email));
	}

	@Query("select case when count(u) > 0 then true else false end from User u where u.email = :email")
	boolean existsByNormalizedEmail(@Param("email") String email);

	default boolean existsByEmail(String email) {
		return existsByNormalizedEmail(User.normalizeEmail(email));
	}
}
