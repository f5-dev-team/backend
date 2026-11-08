package com.f5.scholarship_backend.domain.student.service;

import com.f5.scholarship_backend.domain.student.dto.StudentProfileResponse;

public record StudentProfileSaveResult(StudentProfileResponse profile, boolean created) {
}
