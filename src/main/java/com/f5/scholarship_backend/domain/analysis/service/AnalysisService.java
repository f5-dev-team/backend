package com.f5.scholarship_backend.domain.analysis.service;

import com.f5.scholarship_backend.domain.analysis.dto.AnalysisCreateResponse;
import com.f5.scholarship_backend.global.exception.BusinessException;
import com.f5.scholarship_backend.global.exception.ErrorCode;
import org.springframework.stereotype.Service;

@Service
public class AnalysisService {
	public AnalysisCreateResponse create(String userId, long profileVersion) {
		// 프로필 버전 확인, 스냅샷 확보 및 실제 작업 접수를 연결하기 전에는 성공을 반환하지 않는다.
		throw new BusinessException(ErrorCode.SERVICE_UNAVAILABLE);
	}
}
