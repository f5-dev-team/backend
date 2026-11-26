package com.f5.scholarship_backend.domain.student.config;

import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "student-profile")
public record StudentProfileProperties(Map<String, Integer> departmentMaxGrades) {
	public StudentProfileProperties {
		departmentMaxGrades = departmentMaxGrades == null ? Map.of() : Map.copyOf(departmentMaxGrades);
		if (departmentMaxGrades.values().stream().anyMatch(maxGrade -> maxGrade < 1)) {
			throw new IllegalArgumentException("학과별 최대 학년은 1 이상이어야 합니다.");
		}
	}
}
