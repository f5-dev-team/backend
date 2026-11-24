package com.f5.scholarship_backend.domain.crawl;

import java.net.URI;
import java.time.LocalDate;
import com.f5.scholarship_backend.domain.crawl.client.*;
import com.f5.scholarship_backend.domain.crawl.model.BoardPage;
import com.f5.scholarship_backend.domain.document.client.AttachmentDownloader;
import com.f5.scholarship_backend.domain.document.enums.OriginalFileType;
import com.f5.scholarship_backend.domain.document.service.FileValidator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import static org.assertj.core.api.Assertions.*;

/** 실제 학교 사이트 한 공고의 한 첨부만 확인하며 DB나 S3에는 저장하지 않는다. */
@SpringBootTest
@ActiveProfiles("test")
@EnabledIfEnvironmentVariable(named = "GACHON_FILE_CHECK", matches = "true")
class GachonFileDownloadLiveTest {
    @Autowired BoardClient board;
    @Autowired GachonBoardParser parser;
    @Autowired AttachmentDownloader downloader;
    @Autowired FileValidator validator;
    @Test void downloadsOneRealPdfOrHwpAndValidatesItsSignature() {
        URI url = URI.create("https://www.gachon.ac.kr/bbs/kor/478/126826/artclView.do");
        var entry = new BoardPage.Entry("478:126826", url, LocalDate.now(), false);
        var post = parser.parseDetail(board.get(url), entry);
        var attachment = post.attachments().stream().filter(a -> OriginalFileType.fromName(a.name()).isPresent()).findFirst().orElseThrow();
        byte[] bytes = downloader.download(attachment);
        assertThat(validator.validateAndHash(bytes, OriginalFileType.fromName(attachment.name()).orElseThrow())).hasSize(64);
        assertThat(bytes.length).isPositive();
    }
}
