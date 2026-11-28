package com.f5.scholarship_backend.domain.document.service;

import java.net.URI;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import com.f5.scholarship_backend.domain.crawl.model.CrawledPost;
import com.f5.scholarship_backend.domain.crawl.model.CrawledPost.Attachment;
import com.f5.scholarship_backend.domain.crawl.service.LeaseManager;
import com.f5.scholarship_backend.domain.crawl.service.ScholarshipCollector;
import com.f5.scholarship_backend.domain.document.client.DocumentAnalysisClient;
import com.f5.scholarship_backend.domain.document.client.DocumentException;
import com.f5.scholarship_backend.domain.document.dto.DocumentAnalysisRequest;
import com.f5.scholarship_backend.domain.document.dto.DocumentAnalysisResult;
import com.f5.scholarship_backend.domain.document.dto.DocumentAnalysisResult.Accepted;
import com.f5.scholarship_backend.domain.document.entity.ScholarshipDocument;
import com.f5.scholarship_backend.domain.document.entity.VersionDocument;
import com.f5.scholarship_backend.domain.document.enums.OriginalFileType;
import com.f5.scholarship_backend.domain.document.model.StoredAttachment;
import com.f5.scholarship_backend.domain.document.repository.ScholarshipDocumentRepository;
import com.f5.scholarship_backend.domain.document.repository.VersionDocumentRepository;
import com.f5.scholarship_backend.domain.scholarship.entity.Scholarship;
import com.f5.scholarship_backend.domain.scholarship.entity.ScholarshipVersion;
import com.f5.scholarship_backend.domain.scholarship.repository.ScholarshipRepository;
import com.f5.scholarship_backend.domain.scholarship.repository.ScholarshipVersionRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import tools.jackson.databind.json.JsonMapper;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@SpringBootTest(properties = {"spring.datasource.url=jdbc:h2:mem:documentAnalysisTest",
		"document-analysis.enabled=false", "document-analysis.source-bucket=fixture-bucket"})
@ActiveProfiles("test")
class DocumentAnalysisIntegrationTest {
	@Autowired private DocumentAnalysisWorker worker;
	@Autowired private ScholarshipCollector collector;
	@Autowired private LeaseManager leases;
	@Autowired private ScholarshipRepository scholarships;
	@Autowired private ScholarshipVersionRepository versions;
	@Autowired private ScholarshipDocumentRepository documents;
	@Autowired private VersionDocumentRepository documentLinks;
	@Autowired private JdbcTemplate jdbc;
	@Autowired private JsonMapper json;
	@MockitoBean private DocumentAnalysisClient client;

	@AfterEach
	void clean() {
		for (String table : List.of("scholarship_conditions", "version_documents", "scholarship_documents",
				"scholarship_versions", "scholarship_crawl_state", "scholarships")) {
			jdbc.update("delete from " + table);
		}
	}

	@Test
	void savesTheContractResultOnceWithItsOriginalVersionAndDocumentLocation() {
		long scholarship = createScholarship("document-success", """
				[{"name":"원본.pdf","url":"https://example.test/file"}]
				""");
		long document = addDocument(scholarship);
		when(client.submit(any())).thenAnswer(call -> {
			DocumentAnalysisRequest request = call.getArgument(0);
			assertThat(request.scholarshipId()).isEqualTo(Long.toString(scholarship));
			assertThat(request.scholarshipVersion()).isEqualTo(1);
			assertThat(request.sourceText()).isEqualTo("공고 본문");
			assertThat(request.documents()).singleElement().satisfies(file -> {
				assertThat(file.documentId()).isEqualTo(Long.toString(document));
				assertThat(file.objectKey()).isEqualTo("scholarship-originals/notice.pdf");
			});
			return new Accepted("doc-501", "QUEUED");
		});
		when(client.read("doc-501")).thenReturn(result(scholarship, 1, document), result(scholarship, 1, document));

		worker.runOnce();
		assertThat(status(scholarship)).isEqualTo("PROCESSING");
		assertThat(jdbc.queryForObject("select document_analysis_id from scholarships where scholarship_id = ?",
				String.class, scholarship)).isEqualTo("doc-501");

		pollDue(scholarship);
		worker.runOnce();
		worker.runOnce();
		assertThat(status(scholarship)).isEqualTo("READY");
		assertThat(conditionCount(scholarship)).isEqualTo(1);
		assertThat(jdbc.queryForObject("select gpa_scale from scholarship_conditions where scholarship_id = ?",
				java.math.BigDecimal.class, scholarship)).isEqualByComparingTo("4.5");
		assertThat(jdbc.queryForObject("select source_document_id from scholarship_conditions where scholarship_id = ?",
				Long.class, scholarship)).isEqualTo(document);
		assertThat(jdbc.queryForObject("select source_page from scholarship_conditions where scholarship_id = ?",
				Integer.class, scholarship)).isEqualTo(2);
		assertThat(jdbc.queryForObject("select source_quote from scholarship_conditions where scholarship_id = ?",
				String.class, scholarship)).contains("4.3 만점");
		assertThat(versions.findById(new ScholarshipVersion.Key(scholarship, 1)).orElseThrow()
				.getRequirementsComplete()).isTrue();
		verify(client, times(1)).read("doc-501");
	}

	@Test
	void rejectsInvalidResultIdentityAndPreservesAnUntouchedConditionTable() {
		long scholarship = createScholarship("wrong-result", "[]");
		when(client.submit(any())).thenReturn(new Accepted("doc-501", "QUEUED"));
		when(client.read("doc-501")).thenReturn(result(scholarship, 2, 200L));
		worker.runOnce();
		pollDue(scholarship);
		worker.runOnce();

		assertThat(status(scholarship)).isEqualTo("FAILED");
		assertThat(conditionCount(scholarship)).isZero();
		assertThat(jdbc.queryForObject("select processing_error_code from scholarships where scholarship_id = ?",
				String.class, scholarship)).isEqualTo("INVALID_AI_RESPONSE");
	}

	@Test
	void ignoresResponseThatArrivesAfterAnAdminVersionWasCreated() {
		long scholarship = createScholarship("stale-result", "[]");
		when(client.submit(any())).thenReturn(new Accepted("doc-501", "PROCESSING"));
		when(client.read("doc-501")).thenAnswer(call -> {
			versions.save(new ScholarshipVersion(scholarship, 2, "관리자 수정본", "https://example.test/new",
					"정정 본문", "[]", Instant.now()));
			jdbc.update("update scholarship_versions set origin = 'ADMIN' where scholarship_id = ? and version = 2", scholarship);
			return result(scholarship, 1, 200L);
		});
		worker.runOnce();
		pollDue(scholarship);
		worker.runOnce();
		worker.runOnce();

		assertThat(status(scholarship)).isEqualTo("PROCESSING");
		assertThat(conditionCount(scholarship)).isZero();
		assertThat(versions.findById(new ScholarshipVersion.Key(scholarship, 2)).orElseThrow().getTitle())
				.isEqualTo("관리자 수정본");
		verify(client, times(1)).read("doc-501");
	}

	@Test
	void refusesPartialOriginalsWithoutSendingThemToFastApi() {
		long damaged = createScholarship("invalid-attachment-metadata", "{");
		long scholarship = createScholarship("missing-original", """
				[{"name":"누락.pdf","url":"https://example.test/missing"}]
				""");
		addDocument(scholarship);
		worker.runOnce();
		assertThat(status(damaged)).isEqualTo("FAILED");
		worker.runOnce();
		assertThat(status(scholarship)).isEqualTo("FAILED");
		assertThat(jdbc.queryForObject("select processing_error_code from scholarships where scholarship_id = ?",
				String.class, scholarship)).isEqualTo("DOCUMENT_FETCH_FAILED");
		verifyNoInteractions(client);
	}

	@Test
	void rejectsSourceLocationsOutsideTheVersion() {
		long scholarship = createScholarship("wrong-location", "[]");
		when(client.submit(any())).thenReturn(new Accepted("doc-501", "QUEUED"));
		when(client.read("doc-501")).thenReturn(result(scholarship, 1, 200L));
		worker.runOnce();
		pollDue(scholarship);
		worker.runOnce();

		assertThat(status(scholarship)).isEqualTo("FAILED");
		assertThat(conditionCount(scholarship)).isZero();
	}

	@Test
	void expiresAbandonedSubmissionsWithoutSubmittingTwice() {
		long scholarship = createScholarship("expired-claim", "[]");
		when(client.submit(any())).thenReturn(new Accepted("doc-501", "QUEUED"));
		worker.runOnce();
		jdbc.update("""
				update scholarships set document_analysis_id = null, status_updated_at = ?
				where scholarship_id = ?
				""", OffsetDateTime.now().minusMinutes(5), scholarship);
		pollDue(scholarship);
		worker.runOnce();

		assertThat(status(scholarship)).isEqualTo("FAILED");
		assertThat(jdbc.queryForObject("select processing_error_code from scholarships where scholarship_id = ?",
				String.class, scholarship)).isEqualTo("AI_REQUEST_TIMEOUT");
		verify(client, times(1)).submit(any());
	}

	@ParameterizedTest
	@CsvSource({"true,false", "true,true", "false,false"})
	void validatesEveryAttachmentEvenWhenSeveralUrlsShareOneDocument(boolean allStored, boolean legacy) {
		var first = new Attachment("원본.pdf", URI.create("https://example.test/file"));
		var second = new Attachment("사본.pdf", URI.create("https://example.test/alias"));
		var post = new CrawledPost("478:9001", URI.create("https://example.test/notice"),
				"합성 공고", "공고 본문", null, List.of(first, second));
		var originals = allStored ? List.of(storedFile(first), storedFile(second)) : List.of(storedFile(first));
		String owner = leases.acquire();
		try {
			assertThat(collector.collect(post, originals, owner)).isEqualTo("COLLECTED");
		} finally {
			leases.release(owner);
		}
		long id = scholarships.findAll().get(0).getId();
		long document = documents.findAll().get(0).getId();
		assertThat(documentLinks.count()).isEqualTo(1);
		var version = versions.findById(new ScholarshipVersion.Key(id, 1)).orElseThrow();
		var attachments = json.readTree(version.getSourceAttachments());
		assertThat(attachments.get(0).path("documentId").asLong()).isEqualTo(document);
		assertThat(attachments.get(1).path("url").asString()).isEqualTo(second.url().toString());
		if (allStored) {
			assertThat(attachments.get(1).path("documentId").asLong()).isEqualTo(document);
			if (legacy) {
				jdbc.update("update scholarship_versions set source_attachments = ? where scholarship_id = ?",
						json.writeValueAsString(post.attachments()), id);
			}
			when(client.submit(any())).thenReturn(new Accepted("doc-501", "QUEUED"));
			worker.runOnce();
			verify(client).submit(new DocumentAnalysisRequest(Long.toString(id), 1, "공고 본문",
					List.of(new DocumentAnalysisRequest.Document(Long.toString(document), "scholarship-originals/notice.pdf"))));
		} else {
			assertThat(attachments.get(1).path("documentId").isNull()).isTrue();
			worker.runOnce();
			assertThat(status(id)).isEqualTo("FAILED");
			assertThat(failureCode(id)).isEqualTo("DOCUMENT_FETCH_FAILED");
			verifyNoInteractions(client);
		}
	}

	@ParameterizedTest
	@CsvSource(delimiter = '|', value = {"[3.0,3.5]|READY", "[3.0,4.51]|FAILED"})
	void checksEveryGpaListValueAgainstTheScaleBeforeSaving(String values, String expectedStatus) {
		long scholarship = createScholarship("gpa-list", "[]");
		when(client.submit(any())).thenReturn(new Accepted("doc-501", "QUEUED"));
		when(client.read("doc-501")).thenReturn(json.readValue("""
				{"analysisId":"doc-501","scholarshipId":"%d","scholarshipVersion":1,
				"processingStatus":"COMPLETED","requirementsComplete":true,"requirements":[
				{"field":"previousSemesterGpa","operator":"IN","expectedValue":%s,"gpaScale":4.5,
				"required":true,"description":"직전 학기 평점"}]}
				""".formatted(scholarship, values), DocumentAnalysisResult.class));
		worker.runOnce();
		pollDue(scholarship);
		worker.runOnce();

		assertThat(status(scholarship)).isEqualTo(expectedStatus);
		assertThat(conditionCount(scholarship)).isEqualTo("READY".equals(expectedStatus) ? 1 : 0);
	}

	@ParameterizedTest
	@ValueSource(strings = {"AI_REQUEST_REJECTED", "INVALID_AI_RESPONSE"})
	void preservesTheSpecificFastApiFailureWhenUpdatingTheScholarship(String code) {
		long scholarship = createScholarship("fastapi-error", "[]");
		when(client.submit(any())).thenThrow(new DocumentException(code));
		worker.runOnce();
		assertThat(status(scholarship)).isEqualTo("FAILED");
		assertThat(failureCode(scholarship)).isEqualTo(code);
	}

	private String failureCode(long scholarship) {
		return jdbc.queryForObject("select processing_error_code from scholarships where scholarship_id = ?",
				String.class, scholarship);
	}

	private static StoredAttachment storedFile(Attachment attachment) {
		return new StoredAttachment(attachment.name(), attachment.url(), OriginalFileType.PDF,
				"a".repeat(64), 128, "fixture-bucket", "scholarship-originals/notice.pdf", null);
	}

	private long createScholarship(String key, String attachments) {
		long id = scholarships.save(new Scholarship(key, Instant.now())).getId();
		versions.save(new ScholarshipVersion(id, 1, "합성 공고", "https://example.test/notice",
				"<p>공고 본문</p>", attachments, Instant.now()));
		return id;
	}

	private long addDocument(long scholarship) {
		var file = storedFile(new Attachment("원본.pdf", URI.create("https://example.test/file")));
		long id = documents.save(new ScholarshipDocument(scholarship, file, Instant.now())).getId();
		documentLinks.save(new VersionDocument(scholarship, 1, id));
		return id;
	}

	private DocumentAnalysisResult result(long scholarship, int version, long document) {
		return json.readValue("""
				{"analysisId":"doc-501","scholarshipId":"%d","scholarshipVersion":%d,
				"processingStatus":"COMPLETED","requirementsComplete":true,"requirements":[
				{"field":"previousSemesterGpa","operator":"GTE","expectedValue":3.5,"gpaScale":4.5,"required":true,
				"description":"직전 학기 평점 3.5 이상(4.5 만점 기준)",
				"sourceQuote":"직전 학기 평점 4.5 만점 기준 3.5 이상(4.3 만점 기준 3.3 이상)",
				"sourceLocation":{"documentId":"%d","page":2,"section":"지원 자격"}}]}
				""".formatted(scholarship, version, document), DocumentAnalysisResult.class);
	}

	private void pollDue(long scholarship) {
		jdbc.update("update scholarships set document_analysis_next_at = ? where scholarship_id = ?",
				OffsetDateTime.now().minusSeconds(5), scholarship);
	}

	private String status(long scholarship) {
		return jdbc.queryForObject("select processing_status from scholarships where scholarship_id = ?",
				String.class, scholarship);
	}

	private long conditionCount(long scholarship) {
		return jdbc.queryForObject("select count(*) from scholarship_conditions where scholarship_id = ?",
				Long.class, scholarship);
	}
}
