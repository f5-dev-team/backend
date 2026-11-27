package com.f5.scholarship_backend.global.security;

import java.util.List;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableConfigurationProperties({JwtProperties.class, CorsProperties.class})
public class SecurityConfig {
	@Bean
	CorsConfigurationSource corsConfigurationSource(CorsProperties corsProperties) {
		CorsConfiguration configuration = new CorsConfiguration();
		configuration.setAllowedOrigins(corsProperties.allowedOrigins());
		configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
		configuration.setAllowedHeaders(List.of(
				"Authorization", "Content-Type", "Accept", "X-Request-Id"));
		configuration.setExposedHeaders(List.of("X-Request-Id", "Location", "WWW-Authenticate"));
		configuration.setAllowCredentials(false);

		UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
		source.registerCorsConfiguration("/**", configuration);
		return source;
	}

	@Bean
	PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder();
	}

	@Bean
	SecurityFilterChain securityFilterChain(HttpSecurity http,
			JwtTokenProvider tokenProvider,
			JwtAuthenticationEntryPoint authenticationEntryPoint,
			JwtAccessDeniedHandler accessDeniedHandler,
			CorsConfigurationSource corsConfigurationSource) throws Exception {
		http
				.csrf(csrf -> csrf.disable())
				.cors(cors -> cors.configurationSource(corsConfigurationSource))
				.formLogin(form -> form.disable())
				.httpBasic(basic -> basic.disable())
				.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
				.exceptionHandling(exceptions -> exceptions
						.authenticationEntryPoint(authenticationEntryPoint)
						.accessDeniedHandler(accessDeniedHandler))
				.authorizeHttpRequests(authorize -> authorize
						.requestMatchers(HttpMethod.POST, "/api/v1/users", "/api/v1/sessions",
								"/api/v1/admin/sessions").permitAll()
						.requestMatchers(HttpMethod.GET, "/api/v1/users/email-availability").permitAll()
						.requestMatchers("/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**",
								"/v3/api-docs.yaml").permitAll()
						.requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
						.requestMatchers("/api/v1/**").hasRole("USER")
						.anyRequest().permitAll())
				.addFilterBefore(new JwtAuthenticationFilter(tokenProvider),
						UsernamePasswordAuthenticationFilter.class);
		return http.build();
	}
}
