package com.f5.scholarship_backend.domain.document.client;

/** 외부 원문이나 인증 정보를 포함하지 않는 파일 처리 오류다. */
public class DocumentException extends RuntimeException {
    private final String code;
    private final boolean limitExceeded;

    public DocumentException(String code) { this(code, false); }

    private DocumentException(String code, boolean limitExceeded) {
        super("첨부파일 처리를 완료하지 못했습니다.");
        this.code = code;
        this.limitExceeded = limitExceeded;
    }

    public String code() { return code; }
    public boolean isLimitExceeded() { return limitExceeded; }
    public static DocumentException limitExceeded() { return new DocumentException("DOCUMENT_FETCH_FAILED", true); }
    public static DocumentException download() { return new DocumentException("DOCUMENT_FETCH_FAILED"); }
    public static DocumentException storage() { return new DocumentException("STORAGE_FAILED"); }
}
