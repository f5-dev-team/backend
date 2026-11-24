package com.f5.scholarship_backend.domain.document;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import com.f5.scholarship_backend.domain.crawl.model.CrawledPost;
import com.f5.scholarship_backend.domain.crawl.repository.*;
import com.f5.scholarship_backend.domain.crawl.service.*;
import com.f5.scholarship_backend.domain.document.client.*;
import com.f5.scholarship_backend.domain.document.repository.*;
import com.f5.scholarship_backend.domain.document.service.AttachmentPipeline;
import com.f5.scholarship_backend.domain.document.storage.OriginalFileStorage;
import com.f5.scholarship_backend.domain.scholarship.repository.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@SpringBootTest
@ActiveProfiles("test")
class FilePipelineIntegrationTest {
    @Autowired AttachmentPipeline pipeline;
    @Autowired ScholarshipCollector collector;
    @Autowired LeaseManager leases;
    @Autowired ScholarshipRepository scholarships;
    @Autowired ScholarshipVersionRepository versions;
    @Autowired ScholarshipDocumentRepository documents;
    @Autowired VersionDocumentRepository links;
    @Autowired CrawlStateRepository states;
    @Autowired CrawlErrorRepository errors;
    @Autowired CrawlRunRepository runs;
    @Autowired JdbcTemplate jdbc;
    @MockitoBean com.f5.scholarship_backend.domain.crawl.client.BoardClient board;
    @Autowired CrawlJob job;
    @MockitoBean AttachmentDownloader downloader;
    @MockitoBean OriginalFileStorage storage;
    private final Map<String, byte[]> objects = new ConcurrentHashMap<>();
    private String owner;
    @BeforeEach void setup() {
        links.deleteAll(); documents.deleteAll(); errors.deleteAll(); runs.deleteAll(); states.deleteAll(); versions.deleteAll(); scholarships.deleteAll();
        jdbc.update("delete from crawler_leases");
        owner = leases.acquire();
        when(downloader.download(any())).thenReturn(pdf("원본"));
        when(storage.store(anyString(), any(), anyString(), any())).thenAnswer(invocation -> {
            String key = invocation.getArgument(0); byte[] bytes = invocation.getArgument(1);
            objects.compute(key, (k, existing) -> {
                if (existing != null && !Arrays.equals(existing, bytes)) { throw new AssertionError("원본 덮어쓰기"); }
                return bytes;
            });
            return new OriginalFileStorage.Location("test-bucket", key, "v1");
        });
    }
    @AfterEach void release() { if (owner != null) { leases.release(owner); } }
    @Test void sameUrlChangedBytesCreatesVersionAndPreservesOriginalConnections() {
        var post = post("제목", List.of(file("a.pdf", 1)));
        assertThat(collect(post)).isEqualTo("COLLECTED");
        assertThat(collect(post)).isEqualTo("SKIPPED");
        assertThat(documents.count()).isEqualTo(1);
        when(downloader.download(any())).thenReturn(pdf("변경"));
        assertThat(collect(post)).isEqualTo("COLLECTED");
        assertThat(versions.count()).isEqualTo(2);
        assertThat(documents.count()).isEqualTo(2);
        assertThat(links.findAll()).extracting(l -> l.getVersion()).containsExactlyInAnyOrder(1, 2);
        assertThat(objects).hasSize(2);
    }
    @Test void reorderedAttachmentsReuseVersionAndRenamedFileReusesDocument() {
        var a = file("a.pdf", 1); var b = file("b.pdf", 2);
        collect(post("제목", List.of(a, b)));
        assertThat(collect(post("제목", List.of(b, a)))).isEqualTo("SKIPPED");
        assertThat(collect(post("제목", List.of(file("rename.pdf", 1))))).isEqualTo("COLLECTED");
        assertThat(documents.count()).isEqualTo(1);
        assertThat(versions.count()).isEqualTo(2);
        assertThat(links.count()).isEqualTo(2);
        assertThat(documents.findAll().get(0).getFileName()).isEqualTo("a.pdf");
    }
    @Test void uploadedObjectIsReusedAfterDatabaseRollback() {
        assertThatThrownBy(() -> collect(post("x".repeat(256), List.of(file("a.pdf", 1))))).isInstanceOf(RuntimeException.class);
        assertThat(scholarships.count()).isZero(); assertThat(documents.count()).isZero(); assertThat(links.count()).isZero();
        assertThat(objects).hasSize(1);
        assertThat(collect(post("정상 제목", List.of(file("a.pdf", 1))))).isEqualTo("COLLECTED");
        assertThat(objects).hasSize(1); assertThat(documents.count()).isEqualTo(1); assertThat(links.count()).isEqualTo(1);
    }
    @Test void storageFailurePreservesExistingVersionAndConnections() {
        var post = post("제목", List.of(file("a.pdf", 1))); collect(post);
        when(storage.store(anyString(), any(), anyString(), any())).thenThrow(DocumentException.storage());
        assertThatThrownBy(() -> collect(post)).isInstanceOf(DocumentException.class);
        assertThat(versions.count()).isEqualTo(1); assertThat(links.count()).isEqualTo(1);
    }
    @Test void cancellationAfterUploadBlocksDatabaseCommit() {
        var prepared = pipeline.prepare(post("제목", List.of(file("a.pdf", 1))));
        leases.cancelWrites(owner);
        assertThatThrownBy(() -> collector.collect(post("제목", List.of(file("a.pdf", 1))), prepared.files(), owner))
                .isInstanceOf(IllegalStateException.class);
        assertThat(scholarships.count()).isZero(); assertThat(documents.count()).isZero(); assertThat(objects).hasSize(1);
    }
    @Test void unsupportedFileKeepsRawSourceAndDoesNotPretendDocumentComplete() {
        var post = post("제목", List.of(file("안내.hwpx", 1)));
        var prepared = pipeline.prepare(post);
        assertThat(prepared.unsupported()).isTrue(); assertThat(prepared.files()).isEmpty();
        collector.collect(post, prepared.files(), owner);
        assertThat(versions.findAll().get(0).getSourceAttachments()).contains("안내.hwpx");
        assertThat(scholarships.findAll().get(0).getProcessingStatus()).isEqualTo("PENDING");
        verifyNoInteractions(downloader, storage);
    }
    @Test void adminCorrectionIsNotOverwrittenWhenOnlyFileBytesChange() {
        var post = post("제목", List.of(file("a.pdf", 1))); collect(post);
        Long id = scholarships.findAll().get(0).getId();
        jdbc.update("insert into scholarship_versions(scholarship_id,version,title,source_url,created_at,origin) values(?,2,?,?,CURRENT_TIMESTAMP,'ADMIN')",
                id, "관리자 수정", post.sourceUrl().toString());
        when(downloader.download(any())).thenReturn(pdf("변경"));
        assertThat(collect(post)).isEqualTo("HELD");
        assertThat(versions.count()).isEqualTo(2); assertThat(links.count()).isEqualTo(1);
        assertThat(states.findById(id).orElseThrow().getCandidatePayload()).contains("sha256", "files");
    }
    @Test void tooManyFilesIsRejectedBeforeAnyNetworkCall() {
        var attachments = java.util.stream.IntStream.range(1, 12).mapToObj(i -> file(i + ".pdf", i)).toList();
        assertThatThrownBy(() -> pipeline.prepare(post("제목", attachments))).isInstanceOf(DocumentException.class);
        verifyNoInteractions(downloader, storage);
    }
    @Test void workerRecordsUnsupportedFileAsFailureWhilePreservingRawSource() {
        leases.release(owner); owner = null;
        String today = LocalDate.now(java.time.ZoneId.of("Asia/Seoul")).toString().replace('-', '.');
        when(board.get(any())).thenAnswer(invocation -> {
            URI uri = invocation.getArgument(0);
            if (uri.getPath().endsWith("artclList.do")) {
                return "<table><tr><td class='td-subject'><a href='/bbs/kor/478/1001/artclView.do'>공고</a></td><td class='td-date'>" + today + "</td></tr></table>";
            }
            return "<dl class='view-num'><dd>1001</dd></dl><h2 class='view-title'>제목</h2><div class='view-con'>본문</div>"
                    + "<div class='view-file'><a href='/bbs/kor/478/1/download.do'>안내.hwpx</a></div>";
        });
        Long id = job.runOnce();
        assertThat(runs.findById(id).orElseThrow().getStatus()).isEqualTo("FAILED");
        assertThat(errors.findAll()).extracting(e -> e.getCode()).containsExactly("DOCUMENT_FETCH_FAILED");
        assertThat(versions.count()).isEqualTo(1);
        assertThat(documents.count()).isZero();
        verifyNoInteractions(downloader, storage);
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(booleans = {false, true})
    void attachmentLimitPreservesNewSourceAndRecordsFailedRun(boolean tooManyFiles) {
        int count = tooManyFiles ? 11 : 1;
        if (!tooManyFiles) {
            when(downloader.download(any())).thenThrow(DocumentException.limitExceeded());
        }
        leases.release(owner);
        owner = null;
        String date = LocalDate.now(java.time.ZoneId.of("Asia/Seoul")).toString().replace('-', '.');
        String attachments = java.util.stream.IntStream.rangeClosed(1, count)
                .mapToObj(i -> "<a href='/bbs/kor/478/" + i + "/download.do'>" + i + ".pdf</a>")
                .reduce("", String::concat);
        when(board.get(any())).thenAnswer(invocation -> {
            URI uri = invocation.getArgument(0);
            if (uri.getPath().endsWith("artclList.do")) {
                return "<table><tr><td class='td-subject'><a href='/bbs/kor/478/1001/artclView.do'>공고</a>"
                        + "</td><td class='td-date'>" + date + "</td></tr></table>";
            }
            return "<dl class='view-num'><dd>1001</dd></dl><h2 class='view-title'>제목</h2>"
                    + "<div class='view-con'>본문</div><div class='view-file'>" + attachments + "</div>";
        });

        Long runId = job.runOnce();

        assertThat(runs.findById(runId).orElseThrow().getStatus()).isEqualTo("FAILED");
        assertThat(errors.findAll()).extracting(e -> e.getCode()).containsExactly("DOCUMENT_FETCH_FAILED");
        assertThat(scholarships.findAll()).singleElement()
                .satisfies(s -> assertThat(s.getProcessingStatus()).isEqualTo("PENDING"));
        assertThat(versions.findAll()).singleElement().satisfies(version -> {
            assertThat(version.getTitle()).isEqualTo("제목");
            assertThat(version.getSourceText()).contains("본문");
            assertThat(version.getSourceAttachments()).contains("1.pdf", count + ".pdf");
        });
        assertThat(documents.count()).isZero();
        assertThat(links.count()).isZero();
        verifyNoInteractions(storage);
        if (tooManyFiles) {
            verifyNoInteractions(downloader);
        }
    }

    private String collect(CrawledPost post) { return collector.collect(post, pipeline.prepare(post).files(), owner); }
    static byte[] pdf(String text) { return ("%PDF-1.7\n" + text + "\n%%EOF").getBytes(StandardCharsets.UTF_8); }
    static CrawledPost.Attachment file(String name, int id) {
        return new CrawledPost.Attachment(name, URI.create("https://www.gachon.ac.kr/bbs/kor/478/" + id + "/download.do"));
    }
    static CrawledPost post(String title, List<CrawledPost.Attachment> files) {
        return new CrawledPost("478:1001", URI.create("https://www.gachon.ac.kr/bbs/kor/478/1001/artclView.do"),
                title, "<p>본문</p>", LocalDate.now(), files);
    }
}
