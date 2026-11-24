package com.f5.scholarship_backend.domain.document;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import com.f5.scholarship_backend.domain.document.config.FileProperties;
import com.f5.scholarship_backend.domain.document.client.DocumentException;
import com.f5.scholarship_backend.domain.document.enums.OriginalFileType;
import com.f5.scholarship_backend.domain.document.service.FileValidator;
import org.apache.poi.poifs.filesystem.POIFSFileSystem;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class FileValidatorTest {
    static FileProperties properties() { return new FileProperties(false, "test-bucket", "ap-northeast-2",
            "scholarship-originals", 1024 * 1024, 10, 2 * 1024 * 1024, Duration.ofSeconds(2)); }
    @Test void rejectsEmptyOversizedHtmlAndWrongExtension() {
        var validator = new FileValidator(properties());
        for (byte[] bytes : new byte[][] {new byte[0], "<html>오류</html>".getBytes(StandardCharsets.UTF_8), new byte[1024 * 1024 + 1]}) {
            assertThatThrownBy(() -> validator.validateAndHash(bytes, OriginalFileType.PDF)).isInstanceOf(DocumentException.class);
        }
        assertThatThrownBy(() -> validator.validateAndHash("%PDF-1.7".getBytes(StandardCharsets.US_ASCII), OriginalFileType.HWP))
                .isInstanceOf(DocumentException.class);
    }
    @Test void distinguishesHwpFromOtherOleFiles() throws Exception {
        var validator = new FileValidator(properties());
        byte[] hwp = ole(true);
        assertThat(validator.validateAndHash(hwp, OriginalFileType.HWP)).hasSize(64);
        assertThatThrownBy(() -> validator.validateAndHash(ole(false), OriginalFileType.HWP)).isInstanceOf(DocumentException.class);
    }
    static byte[] ole(boolean hwp) throws Exception {
        try (var fs = new POIFSFileSystem(); var out = new ByteArrayOutputStream()) {
            byte[] header = new byte[256];
            byte[] signature = (hwp ? "HWP Document File" : "다른 문서").getBytes(StandardCharsets.UTF_8);
            System.arraycopy(signature, 0, header, 0, signature.length);
            fs.createDocument(new ByteArrayInputStream(header), "FileHeader");
            fs.writeFilesystem(out); return out.toByteArray();
        }
    }
}
