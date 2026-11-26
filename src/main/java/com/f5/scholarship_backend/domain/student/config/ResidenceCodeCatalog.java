package com.f5.scholarship_backend.domain.student.config;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

@Component
public class ResidenceCodeCatalog {
	private final Set<String> sigunguCodes;

	public ResidenceCodeCatalog() throws IOException {
		var resource = new ClassPathResource("student/residence-codes.tsv");
		try (var reader = new BufferedReader(new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
			sigunguCodes = reader.lines()
					.filter(line -> !line.isBlank() && !line.startsWith("#"))
					.map(line -> line.split("\t", 2)[0])
					.collect(Collectors.toUnmodifiableSet());
		}
		if (sigunguCodes.isEmpty()) {
			throw new IllegalStateException("시·군·구 코드 목록이 비어 있습니다.");
		}
	}

	public boolean contains(String sidoCode, String sigunguCode) {
		return sidoCode != null && sigunguCode != null
				&& sigunguCode.startsWith(sidoCode) && sigunguCodes.contains(sigunguCode);
	}
}
