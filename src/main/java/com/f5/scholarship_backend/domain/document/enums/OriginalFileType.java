package com.f5.scholarship_backend.domain.document.enums;

import java.util.Locale;
import java.util.Optional;

public enum OriginalFileType {
    PDF("application/pdf"), HWP("application/x-hwp");
    private final String contentType;
    OriginalFileType(String contentType) { this.contentType = contentType; }
    public String contentType() { return contentType; }
    /** 게시판이 표시한 실제 확장자만 해석하고 다른 형식은 지원 제외로 남긴다. */
    public static Optional<OriginalFileType> fromName(String name) {
        String value = name.strip().toLowerCase(Locale.ROOT);
        if (value.endsWith(".pdf")) { return Optional.of(PDF); }
        if (value.endsWith(".hwp")) { return Optional.of(HWP); }
        return Optional.empty();
    }
}
