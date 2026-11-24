package com.f5.scholarship_backend.domain.document.service;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import org.apache.poi.poifs.filesystem.POIFSFileSystem;
import org.springframework.stereotype.Component;
import com.f5.scholarship_backend.domain.document.client.DocumentException;
import com.f5.scholarship_backend.domain.document.config.FileProperties;
import com.f5.scholarship_backend.domain.document.enums.OriginalFileType;

@Component
public class FileValidator {
    private final FileProperties properties;
    public FileValidator(FileProperties properties) { this.properties = properties; }
    /** PDF 헤더 또는 HWP의 OLE FileHeader 스트림을 확인한 뒤 바이트 지문을 계산한다. */
    public String validateAndHash(byte[] bytes, OriginalFileType type) {
        if (bytes.length > properties.maxFileBytes()) { throw DocumentException.limitExceeded(); }
        if (bytes.length == 0) { throw DocumentException.download(); }
        if (type == OriginalFileType.PDF) {
            if (bytes.length < 8 || !new String(bytes, 0, 5, StandardCharsets.US_ASCII).equals("%PDF-")) {
                throw DocumentException.download();
            }
        } else {
            try (var fs = new POIFSFileSystem(new ByteArrayInputStream(bytes));
                    var header = fs.createDocumentInputStream("FileHeader")) {
                byte[] signature = header.readNBytes(32);
                byte[] expected = new byte[32];
                byte[] text = "HWP Document File".getBytes(StandardCharsets.US_ASCII);
                System.arraycopy(text, 0, expected, 0, text.length);
                if (!java.util.Arrays.equals(signature, expected)) { throw DocumentException.download(); }
            } catch (java.io.IOException | IllegalArgumentException failure) { throw DocumentException.download(); }
        }
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (java.security.NoSuchAlgorithmException impossible) { throw new IllegalStateException("SHA-256을 사용할 수 없습니다."); }
    }
}
