package com.f5.scholarship_backend.domain.document.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("document-storage")
public record FileProperties(boolean enabled, String bucket, String region, String prefix,
        int maxFileBytes, int maxFiles, long maxPostBytes, Duration downloadTimeout) {
    public FileProperties {
        if (maxFileBytes <= 0 || maxFiles <= 0 || maxPostBytes < maxFileBytes
                || downloadTimeout == null || downloadTimeout.isNegative() || downloadTimeout.isZero()) {
            throw new IllegalArgumentException("파일 제한 설정이 유효하지 않습니다.");
        }
        if (prefix == null || !prefix.matches("[a-zA-Z0-9/_-]+") || prefix.startsWith("/")) {
            throw new IllegalArgumentException("S3 원본 경로가 유효하지 않습니다.");
        }
        if (enabled && (bucket == null || bucket.isBlank() || region == null || region.isBlank())) {
            throw new IllegalArgumentException("S3 활성화 시 버킷과 리전을 설정해야 합니다.");
        }
    }
}
