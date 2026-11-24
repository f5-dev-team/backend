package com.f5.scholarship_backend.domain.document.storage;

import com.f5.scholarship_backend.domain.document.enums.OriginalFileType;

public interface OriginalFileStorage {
    /** 같은 키를 덮어쓰지 않으며 기존 객체의 지문과 크기를 검증한 뒤 재사용한다. */
    Location store(String key, byte[] bytes, String sha256, OriginalFileType type);
    record Location(String bucket, String key, String versionId) { }
}
