package com.f5.scholarship_backend.domain.document;

import java.net.URI;
import java.time.LocalDate;
import java.util.HexFormat;
import java.security.MessageDigest;
import javax.sql.DataSource;
import com.f5.scholarship_backend.domain.crawl.client.*;
import com.f5.scholarship_backend.domain.crawl.model.BoardPage;
import com.f5.scholarship_backend.domain.crawl.service.*;
import com.f5.scholarship_backend.domain.document.service.AttachmentPipeline;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import static org.assertj.core.api.Assertions.*;

/** 명시적으로 켰을 때만 실제 AWS에 원본을 업로드하고 로컬 테스트 DB에 연결을 보존한다. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, properties = "document-storage.enabled=true")
@ActiveProfiles("mysql-crawl-check")
@EnabledIfEnvironmentVariable(named = "S3_MYSQL_FILE_CHECK", matches = "true")
class S3MysqlFilePipelineLiveTest {
    @Autowired DataSource dataSource;
    @Autowired JdbcTemplate jdbc;
    @Autowired BoardClient board;
    @Autowired GachonBoardParser parser;
    @Autowired AttachmentPipeline pipeline;
    @Autowired ScholarshipCollector collector;
    @Autowired LeaseManager leases;
    @Autowired S3Client s3;
    @Value("${GACHON_FILE_POST_ID:126826}") String postId;

    /** S3 재조회 무결성과 실제 MySQL의 문서·버전 연결 및 재실행 중복 방지를 확인한다. */
    @Test
    @Timeout(value = 10, unit = java.util.concurrent.TimeUnit.MINUTES)
    void realAttachmentIsStoredInS3AndLinkedInLocalMysql() throws Exception {
        try (var connection = dataSource.getConnection()) {
            assertThat(connection.getMetaData().getDatabaseProductName()).isEqualTo("MySQL");
            assertThat(connection.getCatalog()).isEqualTo("scholarship_crawl_test");
            assertThat(connection.getMetaData().getURL()).startsWith("jdbc:mysql://127.0.0.1:3306/scholarship_crawl_test");
        }
        assertThat(postId).matches("[0-9]+");
        String owner = leases.acquire(); assertThat(owner).isNotNull();
        try {
            URI url = URI.create("https://www.gachon.ac.kr/bbs/kor/478/" + postId + "/artclView.do");
            var post = parser.parseDetail(board.get(url), new BoardPage.Entry("478:" + postId, url, LocalDate.now(), false));
            var prepared = pipeline.prepare(post);
            assertThat(prepared.unsupported()).as("지원 형식만 첨부된 공고로 검증합니다.").isFalse();
            assertThat(prepared.files()).isNotEmpty();
            for (var file : prepared.files()) {
                var request = GetObjectRequest.builder().bucket(file.bucket()).key(file.key()).versionId(file.versionId()).build();
                byte[] actual = s3.getObjectAsBytes(request).asByteArray();
                assertThat(actual.length).isEqualTo(file.byteSize());
                assertThat(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(actual))).isEqualTo(file.sha256());
            }
            String result = collector.collect(post, prepared.files(), owner);
            assertThat(result).as("HELD이면 관리자 정정이 없는 공고 번호로 검증해야 합니다.").isIn("COLLECTED", "SKIPPED");
            Long id = jdbc.queryForObject("select scholarship_id from scholarships where source_site='GACHON_KOR' and source_post_key=? and source_item_key='main'", Long.class, post.postKey());
            Integer version = jdbc.queryForObject("select last_imported_version from scholarship_crawl_state where scholarship_id=?", Integer.class, id);
            Long count = jdbc.queryForObject("select count(*) from version_documents where scholarship_id=? and version=?", Long.class, id, version);
            assertThat(count).isEqualTo(prepared.files().stream().map(f -> f.sha256()).distinct().count());
            Long versionsBefore = jdbc.queryForObject("select count(*) from scholarship_versions where scholarship_id=?", Long.class, id);
            assertThat(collector.collect(post, prepared.files(), owner)).isEqualTo("SKIPPED");
            assertThat(jdbc.queryForObject("select count(*) from scholarship_versions where scholarship_id=?", Long.class, id)).isEqualTo(versionsBefore);
        } finally { leases.release(owner); }
    }
}
