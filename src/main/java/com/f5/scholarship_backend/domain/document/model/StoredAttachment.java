package com.f5.scholarship_backend.domain.document.model;

import java.net.URI;
import com.f5.scholarship_backend.domain.document.enums.OriginalFileType;

/** 업로드된 불변 원본의 메타데이터이며 파일 바이트나 서명 URL은 포함하지 않는다. */
public record StoredAttachment(String name, URI sourceUrl, OriginalFileType type, String sha256,
        long byteSize, String bucket, String key, String versionId) { }
