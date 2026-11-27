package com.f5.scholarship_backend.domain.document;

import java.net.URI;
import java.time.Instant;
import com.f5.scholarship_backend.domain.document.client.DocumentException;
import com.f5.scholarship_backend.domain.document.entity.ScholarshipDocument;
import com.f5.scholarship_backend.domain.document.entity.VersionDocument;
import com.f5.scholarship_backend.domain.document.enums.OriginalFileType;
import com.f5.scholarship_backend.domain.document.model.StoredAttachment;
import com.f5.scholarship_backend.domain.document.repository.ScholarshipDocumentRepository;
import com.f5.scholarship_backend.domain.document.repository.VersionDocumentRepository;
import com.f5.scholarship_backend.domain.document.storage.OriginalDocumentAccess;
import com.f5.scholarship_backend.domain.scholarship.entity.Scholarship;
import com.f5.scholarship_backend.domain.scholarship.entity.ScholarshipVersion;
import com.f5.scholarship_backend.domain.scholarship.repository.ScholarshipRepository;
import com.f5.scholarship_backend.domain.scholarship.repository.ScholarshipVersionRepository;
import com.f5.scholarship_backend.domain.user.enums.UserRole;
import com.f5.scholarship_backend.global.security.JwtTokenProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:documentAccessTest")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DocumentAccessApiIntegrationTest {
	private static final Instant EXPIRES_AT = Instant.parse("2026-11-28T09:05:00Z");

	@Autowired private MockMvc mockMvc;
	@Autowired private JwtTokenProvider tokens;
	@Autowired private ScholarshipRepository scholarships;
	@Autowired private ScholarshipVersionRepository versions;
	@Autowired private ScholarshipDocumentRepository documents;
	@Autowired private VersionDocumentRepository links;
	@Autowired private JdbcTemplate jdbc;
	@MockitoBean private OriginalDocumentAccess access;

	@AfterEach
	void cleanFixtures() {
		jdbc.update("delete from version_documents");
		jdbc.update("delete from scholarship_documents");
		jdbc.update("delete from scholarship_versions");
		jdbc.update("delete from scholarships");
	}

	@Test
	void issuesAccessForCurrentAndRetainedDocumentVersions() throws Exception {
		long scholarship = scholarship("document-access");
		linkVersion(scholarship, 1, "old-notice");
		linkVersion(scholarship, 2, "new-notice");
		long historical = addDocument(scholarship, "기존 공고.pdf", OriginalFileType.PDF, "a", "version-a");
		long current = addDocument(scholarship, "최신 공고.hwp", OriginalFileType.HWP, "b", null);
		links.save(new VersionDocument(scholarship, 1, historical));
		links.save(new VersionDocument(scholarship, 1, current));
		links.save(new VersionDocument(scholarship, 2, current));
		when(access.issue("fixture-bucket", "originals/a", "version-a"))
				.thenReturn(new OriginalDocumentAccess.AccessUrl("https://example.test/signed/old", EXPIRES_AT));
		when(access.issue("fixture-bucket", "originals/b", null))
				.thenReturn(new OriginalDocumentAccess.AccessUrl("https://example.test/signed/current", EXPIRES_AT));

		mockMvc.perform(get(url(scholarship, historical)).header("Authorization", token(UserRole.USER)))
				.andExpect(status().isOk())
				.andExpect(header().string("Cache-Control", "no-store"))
				.andExpect(jsonPath("$.meta.requestId").isString())
				.andExpect(jsonPath("$.data.documentId").value(Long.toString(historical)))
				.andExpect(jsonPath("$.data.fileName").value("기존 공고.pdf"))
				.andExpect(jsonPath("$.data.fileType").value("PDF"))
				.andExpect(jsonPath("$.data.sourceUrl").value("https://example.test/old-notice"))
				.andExpect(jsonPath("$.data.downloadUrl").value("https://example.test/signed/old"))
				.andExpect(jsonPath("$.data.expiresAt").value(EXPIRES_AT.toString()))
				.andExpect(jsonPath("$.data.bucket").doesNotExist())
				.andExpect(jsonPath("$.data.key").doesNotExist());
		mockMvc.perform(get(url(scholarship, current)).header("Authorization", token(UserRole.USER)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.fileType").value("HWP"))
				.andExpect(jsonPath("$.data.sourceUrl").value("https://example.test/new-notice"))
				.andExpect(jsonPath("$.data.downloadUrl").value("https://example.test/signed/current"));
	}

	@Test
	void rejectsRequestsForUnlinkedOrMismatchedDocumentsBeforeCallingStorage() throws Exception {
		long scholarship = scholarship("valid-document");
		long other = scholarship("other-scholarship");
		linkVersion(scholarship, 1, "notice");
		long document = addDocument(scholarship, "연결.pdf", OriginalFileType.PDF, "c", null);
		long unlinked = addDocument(scholarship, "미연결.pdf", OriginalFileType.PDF, "d", null);
		links.save(new VersionDocument(scholarship, 1, document));

		mockMvc.perform(get(url(scholarship, document))).andExpect(status().isUnauthorized());
		mockMvc.perform(get(url(scholarship, document)).header("Authorization", token(UserRole.ADMIN)))
				.andExpect(status().isForbidden());
		for (var invalid : new String[] {url(other, document), url(scholarship, unlinked),
				url(scholarship, Long.MAX_VALUE), "/api/v1/scholarships/" + scholarship + "/documents/invalid"}) {
			mockMvc.perform(get(invalid).header("Authorization", token(UserRole.USER)))
					.andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
		}
		verifyNoInteractions(access);
	}

	@Test
	void returnsServiceUnavailableWithoutStorageDetails() throws Exception {
		long scholarship = scholarship("storage-unavailable");
		linkVersion(scholarship, 1, "notice");
		long document = addDocument(scholarship, "원본.pdf", OriginalFileType.PDF, "e", null);
		links.save(new VersionDocument(scholarship, 1, document));
		when(access.issue("fixture-bucket", "originals/e", null)).thenThrow(DocumentException.storage());

		mockMvc.perform(get(url(scholarship, document)).header("Authorization", token(UserRole.USER)))
				.andExpect(status().isServiceUnavailable())
				.andExpect(header().string("Cache-Control", "no-store"))
				.andExpect(jsonPath("$.code").value("SERVICE_UNAVAILABLE"))
				.andExpect(jsonPath("$.data.downloadUrl").doesNotExist());
	}

	@Test
	void documentsTheAccessEndpointAndStorageFailureInOpenApi() throws Exception {
		String path = "$.paths['/api/v1/scholarships/{scholarshipId}/documents/{documentId}'].get";
		mockMvc.perform(get("/v3/api-docs"))
				.andExpect(status().isOk())
				.andExpect(jsonPath(path + ".operationId").value("getDocumentAccess"))
				.andExpect(jsonPath(path + ".responses['503'].content['application/problem+json']").exists());
	}

	private long scholarship(String key) {
		return scholarships.save(new Scholarship(key, Instant.now())).getId();
	}

	private void linkVersion(long scholarship, int version, String sourcePath) {
		versions.save(new ScholarshipVersion(scholarship, version, "합성 공고", "https://example.test/" + sourcePath,
				"", "[]", Instant.now()));
	}

	private long addDocument(long scholarship, String name, OriginalFileType type, String suffix, String version) {
		var attachment = new StoredAttachment(name, URI.create("https://example.test/file"), type,
				suffix.repeat(64), 128, "fixture-bucket", "originals/" + suffix, version);
		return documents.save(new ScholarshipDocument(scholarship, attachment, Instant.now())).getId();
	}

	private String token(UserRole role) {
		return "Bearer " + tokens.createAccessToken("1", role);
	}

	private static String url(long scholarship, long document) {
		return "/api/v1/scholarships/" + scholarship + "/documents/" + document;
	}
}
