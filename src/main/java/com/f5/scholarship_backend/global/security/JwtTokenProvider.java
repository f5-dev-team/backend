package com.f5.scholarship_backend.global.security;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;

import com.f5.scholarship_backend.domain.user.enums.UserRole;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class JwtTokenProvider {
	private final JwtProperties properties;
	private final javax.crypto.SecretKey signingKey;
	private final Clock clock;

	@Autowired
	public JwtTokenProvider(JwtProperties properties) {
		this(properties, Clock.systemUTC());
	}

	JwtTokenProvider(JwtProperties properties, Clock clock) {
		this.properties = properties;
		this.clock = clock;
		this.signingKey = Keys.hmacShaKeyFor(Decoders.BASE64.decode(properties.secret()));
	}

	public String createAccessToken(String userId, UserRole role) {
		if (userId == null || userId.isBlank()) {
			throw new IllegalArgumentException("userId must not be blank");
		}
		if (role == null) {
			throw new IllegalArgumentException("role must not be null");
		}
		Instant issuedAt = clock.instant();
		return Jwts.builder()
				.subject(userId)
				.claim("role", role.name())
				.issuedAt(Date.from(issuedAt))
				.expiration(Date.from(issuedAt.plusSeconds(properties.accessTokenExpirationSeconds())))
				.signWith(signingKey)
				.compact();
	}

	public TokenClaims parseToken(String token) {
		Claims claims = Jwts.parser()
				.verifyWith(signingKey)
				.clock(() -> Date.from(clock.instant()))
				.build()
				.parseSignedClaims(token)
				.getPayload();
		String userId = claims.getSubject();
		if (userId == null || userId.isBlank()) {
			throw new IllegalArgumentException("JWT subject must not be blank");
		}
		String role = claims.get("role", String.class);
		if (role == null || role.isBlank()) {
			throw new IllegalArgumentException("JWT role must not be blank");
		}
		Date expiration = claims.getExpiration();
		if (expiration == null) {
			throw new IllegalArgumentException("JWT expiration must not be null");
		}
		return new TokenClaims(userId, UserRole.valueOf(role), expiration.toInstant());
	}

	public long getAccessTokenExpirationSeconds() {
		return properties.accessTokenExpirationSeconds();
	}

	public long getRemainingExpirationSeconds(String token) {
		return Math.max(0, Duration.between(clock.instant(), parseToken(token).expiresAt()).getSeconds());
	}

	public record TokenClaims(String userId, UserRole role, Instant expiresAt) {
	}
}
