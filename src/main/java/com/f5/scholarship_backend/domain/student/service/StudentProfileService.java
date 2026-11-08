package com.f5.scholarship_backend.domain.student.service;

import com.f5.scholarship_backend.domain.student.dto.StudentProfileSaveRequest;
import com.f5.scholarship_backend.global.exception.BusinessException;
import com.f5.scholarship_backend.global.exception.ErrorCode;
import org.springframework.stereotype.Service;

@Service
public class StudentProfileService {
	/** 저장 기능 연결 전까지 SERVICE_UNAVAILABLE을 발생시키는 Service 진입점이다. */
	public StudentProfileSaveResult save(String userId, StudentProfileSaveRequest request) {
		// DB 연동 시 최초 생성 여부 및 동일 값 재저장에 대한 버전 유지 규칙을 구현한다.
		throw new BusinessException(ErrorCode.SERVICE_UNAVAILABLE);
	}
}
