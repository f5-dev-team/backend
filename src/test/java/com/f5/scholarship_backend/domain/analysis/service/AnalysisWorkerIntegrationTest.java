package com.f5.scholarship_backend.domain.analysis.service;

import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import com.f5.scholarship_backend.domain.analysis.enums.AnalysisStatus;
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
import com.f5.scholarship_backend.domain.student.dto.StudentProfileSaveRequest;
import com.f5.scholarship_backend.domain.student.service.StudentProfileService;
import com.f5.scholarship_backend.domain.user.entity.User;
import com.f5.scholarship_backend.domain.user.enums.UserRole;
import com.f5.scholarship_backend.domain.user.repository.UserRepository;
import com.f5.scholarship_backend.global.security.JwtTokenProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.json.JsonMapper;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
		"spring.datasource.url=jdbc:h2:mem:analysisWorkerTest",
		"analysis.worker.enabled=false",
		"student-profile.department-max-grades.CS=4"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@ExtendWith(OutputCaptureExtension.class)
class AnalysisWorkerIntegrationTest {
	private static final String PROFILE = """
			{"departmentCode":"CS","grade":3,"enrollmentStatus":"ENROLLED","cumulativeGpa":3.30,
			"previousSemesterGpa":null,"incomeAssessment":null,"specialEligibility":null,
			"volunteerMinutes":750,"birthDate":"2004-06-01","residence":null,"gender":null}
			""";

	@Autowired private MockMvc mockMvc;
	@Autowired private JwtTokenProvider tokens;
	@Autowired private AnalysisService analyses;
	@Autowired private AnalysisWorker worker;
	@Autowired private AnalysisExecution execution;
	@Autowired private StudentProfileService profiles;
	@Autowired private ScholarshipRepository scholarships;
	@Autowired private ScholarshipVersionRepository versions;
	@Autowired private UserRepository users;
	@Autowired private ScholarshipDocumentRepository documents;
	@Autowired private VersionDocumentRepository versionDocuments;
	@Autowired private JdbcTemplate jdbc;
	@Autowired private JsonMapper json;
	@Autowired private Clock clock;
	private String userId;

	@BeforeEach
	void saveProfile() {
		var account = users.save(User.create("test-" + UUID.randomUUID() + "@example.test", "test-hash",
				UserRole.USER, "테스트", clock.instant(), false));
		userId = account.getId().toString();
		profiles.save(userId, json.readValue(PROFILE, StudentProfileSaveRequest.class));
	}

	@AfterEach
	void cleanFixtures() {
		for (String table : List.of("analysis_evidence", "analysis_items", "analyses", "version_documents",
				"scholarship_documents", "scholarship_conditions", "scholarship_versions",
				"scholarships", "student_profiles", "users")) {
			jdbc.update("delete from " + table);
		}
	}

	@Test
	void completesFromPinnedVersionsAndPersistsAllOutcomeTypes() {
		long eligible = addScholarship("eligible", "READY");
		addCondition(eligible, 1, "grade", "IN", "[2,3]", "", true);
		addCondition(eligible, 1, "enrollmentStatus", "EQ", "\"ENROLLED\"", "", true);
		addCondition(eligible, 1, "cumulativeGpa", "GTE", "3.30", "전체평점 3.30 이상(4.5 만점)", true);
		long ineligible = addScholarship("ineligible", "READY");
		addCondition(ineligible, 1, "cumulativeGpa", "GTE", "3.50", "전체평점 3.50 이상(4.5 만점)", true);
		addCondition(ineligible, 1, "other", "TEXT", null, "학과장 추천 필요", true);
		long needsReview = addScholarship("needs-review", "READY");
		addCondition(needsReview, 1, "previousSemesterGpa", "GTE", "3.00", "직전학기 3.00 이상(4.5 기준)", true);
		addCondition(needsReview, 1, "cumulativeGpa", "GTE", "4.00", "4.00 이상(4.3 만점)", true);
		long empty = addScholarship("empty", "READY");
		long pending = addScholarship("pending", "PENDING");

		long id = startAnalysis();
		profiles.save(userId, json.readValue(PROFILE.replace("\"grade\":3", "\"grade\":1"), StudentProfileSaveRequest.class));
		addVersion(eligible, 2);
		addCondition(eligible, 2, "grade", "GTE", "4", "", true);
		jdbc.update("update scholarships set processing_status = 'FAILED' where scholarship_id = ?", eligible);

		assertThat(worker.runNext()).isTrue();
		var status = analyses.findStatus(userId, Long.toString(id));
		assertThat(status.status()).isEqualTo(AnalysisStatus.COMPLETED);
		assertThat(status.processedCount()).isEqualTo(4);
		assertThat(status.excludedCount()).isEqualTo(1);
		assertThat(status.startedAt()).isNotNull();
		assertThat(status.completedAt()).isAfterOrEqualTo(status.startedAt());
		assertThat(queryResult(id, eligible, "eligibility_status")).isEqualTo("ELIGIBLE");
		assertThat(queryResult(id, ineligible, "eligibility_status")).isEqualTo("INELIGIBLE");
		assertThat(queryResult(id, needsReview, "eligibility_status")).isEqualTo("NEEDS_REVIEW");
		assertThat(queryResult(id, pending, "eligibility_status")).isNull();
		assertThat(queryResult(id, eligible, "application_period_status")).isEqualTo("OPEN");
		assertThat(queryResult(id, eligible, "evidence_status")).isEqualTo("UNAVAILABLE");

		var unmet = json.readTree(queryResult(id, ineligible, "unmet_conditions")).get(0);
		assertThat(unmet.path("gap").decimalValue()).isEqualByComparingTo("0.20");
		assertThat(unmet.path("reasonCode").asString()).isEqualTo("CONDITION_NOT_MET");
		var unresolved = json.readTree(queryResult(id, ineligible, "unresolved_conditions")).get(0);
		assertThat(unresolved.path("reasonCode").asString()).isEqualTo("UNSUPPORTED_RULE");
		var uncertain = json.readTree(queryResult(id, empty, "unresolved_conditions")).get(0);
		assertThat(uncertain.path("conditionId").isNull()).isTrue();
		assertThat(uncertain.path("reasonCode").asString()).isEqualTo("REQUIREMENTS_INCOMPLETE");
	}

	@Test
	void onlyOneWorkerClaimsAJobAndZeroTargetsCanComplete() throws Exception {
		long id = startAnalysis();
		var start = new CountDownLatch(1);
		var pool = Executors.newFixedThreadPool(2);
		try {
			var first = pool.submit(() -> { start.await(); return execution.claimNext(); });
			var second = pool.submit(() -> { start.await(); return execution.claimNext(); });
			start.countDown();
			var claims = List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS));
			assertThat(claims.stream().flatMap(java.util.Optional::stream)).containsExactly(id);
		} finally {
			pool.shutdownNow();
		}
		assertThat(analyses.findStatus(userId, Long.toString(id)).status()).isEqualTo(AnalysisStatus.RUNNING);
		execution.execute(id);
		execution.execute(id);
		assertThat(worker.runNext()).isFalse();
		var status = analyses.findStatus(userId, Long.toString(id));
		assertThat(status.status()).isEqualTo(AnalysisStatus.COMPLETED);
		assertThat(status.processedCount()).isZero();
	}

	@Test
	void rollsBackAllItemsAndPublishesFailureWhenARequiredVersionIsMissing() {
		long first = addScholarship("valid", "READY");
		addCondition(first, 1, "grade", "EQ", "3", "", true);
		long missing = addScholarship("missing", "READY");
		long id = startAnalysis();

		// Simulate a persistence failure after the first item has been evaluated.
		jdbc.update("delete from scholarship_versions where scholarship_id = ?", missing);
		assertThat(worker.runNext()).isTrue();
		var status = analyses.findStatus(userId, Long.toString(id));
		assertThat(status.status()).isEqualTo(AnalysisStatus.FAILED);
		assertThat(status.processedCount()).isZero();
		assertThat(status.failure().code()).isEqualTo("ANALYSIS_PROCESSING_FAILED");
		assertThat(status.completedAt()).isNotNull();
		assertThat(queryResult(id, first, "eligibility_status")).isNull();
		assertThat(jdbc.queryForObject("select failure_message from analyses where analysis_id = ?", String.class, id))
				.isNotBlank();
	}

	@Test
	void logsFailureOriginWithoutIncludingSnapshotOrExceptionMessages(CapturedOutput output) {
		long scholarship = addScholarship("logs", "READY");
		long invalid = startAnalysis();
		long missing = startAnalysis();
		jdbc.update("update analyses set profile_snapshot = ? FORMAT JSON where analysis_id = ?",
				"[\"SYNTHETIC_PROFILE_CONTENT\"]", invalid);

		assertThat(worker.runNext()).isTrue();
		jdbc.update("delete from scholarship_versions where scholarship_id = ?", scholarship);
		assertThat(worker.runNext()).isTrue();

		var invalidStatus = analyses.findStatus(userId, Long.toString(invalid));
		var missingStatus = analyses.findStatus(userId, Long.toString(missing));
		assertThat(invalidStatus.status()).isEqualTo(AnalysisStatus.FAILED);
		assertThat(missingStatus.status()).isEqualTo(AnalysisStatus.FAILED);
		assertThat(invalidStatus.failure()).isEqualTo(missingStatus.failure());
		assertThat(output.getAll()).doesNotContain("SYNTHETIC_PROFILE_CONTENT",
				"Invalid analysis profile snapshot", "Analysis target version is missing");
		var origins = Pattern.compile("exceptionType=java\\.lang\\.IllegalStateException, origin="
				+ "[^\\r\\n]*AnalysisExecution\\.execute\\(AnalysisExecution\\.java:(\\d+)\\)")
				.matcher(output.getAll()).results().map(match -> match.group(1)).toList();
		assertThat(origins).hasSize(2).doesNotHaveDuplicates();
	}

	@Test
	void requiresAnUnambiguousScaleLinkedToTheExpectedGpaAndOperator() {
		List<GpaCase> cases = List.of(
				new GpaCase("conflicting", "GTE", "3.0",
						"4.0점 만점 기준 3.0 이상. 성적표에 4.5점 만점 환산 점수도 기재.", "NEEDS_REVIEW"),
				new GpaCase("four-point-threshold", "GTE", "4.0", "4.0 이상(4.5 만점)", "INELIGIBLE"),
				new GpaCase("four-three-threshold", "GTE", "4.3", "4.3 이상(4.5 만점)", "INELIGIBLE"),
				new GpaCase("prefix", "GTE", "3.30", "4.5점 만점 기준 평점 3.3 이상", "ELIGIBLE"),
				new GpaCase("upper-bound", "LTE", "3.30", "3.3 이하(4.5 만점)", "ELIGIBLE"),
				new GpaCase("unrelated-note", "GTE", "3.0", "평점 3.0 이상. 성적표에 4.5점 만점 환산 점수 기재.", "NEEDS_REVIEW"),
				new GpaCase("parenthesized-note", "GTE", "3.0",
						"평점 3.0 이상(4.5점 만점 환산 점수도 기재)", "NEEDS_REVIEW"),
				new GpaCase("mismatched-value", "GTE", "3.0", "3.5 이상(4.5 만점)", "NEEDS_REVIEW"),
				new GpaCase("mismatched-operator", "GTE", "3.0", "3.0 이하(4.5 만점)", "NEEDS_REVIEW"),
				new GpaCase("other-scale", "GTE", "3.0", "3.0 이상(5점 만점)", "NEEDS_REVIEW"));
		var targets = new LinkedHashMap<Long, GpaCase>();
		for (var testCase : cases) {
			long scholarship = addScholarship(testCase.key(), "READY");
			addCondition(scholarship, 1, "cumulativeGpa", testCase.operator(), testCase.threshold(), testCase.quote(), true);
			targets.put(scholarship, testCase);
		}

		long id = startAnalysis();
		assertThat(worker.runNext()).isTrue();
		assertThat(analyses.findStatus(userId, Long.toString(id)).processedCount()).isEqualTo(cases.size());
		targets.forEach((scholarship, testCase) -> assertThat(queryResult(id, scholarship, "eligibility_status"))
				.as(testCase.key()).isEqualTo(testCase.expectedStatus()));
	}

	private record GpaCase(String key, String operator, String threshold, String quote, String expectedStatus) { }

	@Test
	void marksAbandonedClaimsFailedAndLeavesRecentClaimsRunning() {
		long abandoned = startAnalysis();
		long current = startAnalysis();
		assertThat(execution.claimNext()).contains(abandoned);
		assertThat(execution.claimNext()).contains(current);
		Instant created = clock.instant().minusSeconds(600).truncatedTo(ChronoUnit.MICROS);
		jdbc.update("update analyses set created_at = ?, started_at = ? where analysis_id = ?",
				utc(created), utc(created), abandoned);
		execution.failAbandoned();
		assertThat(analyses.findStatus(userId, Long.toString(abandoned)).status()).isEqualTo(AnalysisStatus.FAILED);
		assertThat(analyses.findStatus(userId, Long.toString(current)).status()).isEqualTo(AnalysisStatus.RUNNING);
	}

	@Test
	void returnsPagedRecommendationsFromPinnedVersionsAndStoredResults() throws Exception {
		long eligible = addScholarship("recommend-eligible", "READY");
		addCondition(eligible, 1, "grade", "EQ", "3", "", true);
		var deadline = clock.instant().plusSeconds(7200).truncatedTo(ChronoUnit.SECONDS);
		jdbc.update("update scholarship_versions set title = ?, application_end_at = ? where scholarship_id = ?",
				"분석 시점 공고", utc(deadline), eligible);

		long excluded = addScholarship("recommend-excluded", "PENDING");
		long ineligible = addScholarship("recommend-ineligible", "READY");
		addCondition(ineligible, 1, "cumulativeGpa", "GTE", "3.50", "전체평점 3.50 이상(4.5 만점)", true);
		addCondition(ineligible, 1, "other", "TEXT", null, "학과장 추천 필요", true);
		long needsReview = addScholarship("recommend-needs-review", "READY");
		long analysisId = startAnalysis();
		addVersion(eligible, 2);
		assertThat(worker.runNext()).isTrue();

		var url = recommendationsUrl(analysisId);
		var bearer = authorization(userId, UserRole.USER);
		mockMvc.perform(get(url).header("Authorization", bearer))
				.andExpect(status().isOk())
				.andExpect(header().string("Cache-Control", "no-store"))
				.andExpect(jsonPath("$.meta.requestId").isString())
				.andExpect(jsonPath("$.data.analysisId").value(Long.toString(analysisId)))
				.andExpect(jsonPath("$.data.profileVersion").value(1))
				.andExpect(jsonPath("$.data.page").value(0))
				.andExpect(jsonPath("$.data.size").value(20))
				.andExpect(jsonPath("$.data.totalPages").value(1))
				.andExpect(jsonPath("$.data.totalElements").value(3))
				.andExpect(jsonPath("$.data.items[*].scholarshipId", contains(
						Long.toString(needsReview), Long.toString(ineligible), Long.toString(eligible))))
				.andExpect(jsonPath("$.data.items[*].eligibilityStatus", contains("NEEDS_REVIEW", "INELIGIBLE", "ELIGIBLE")))
				.andExpect(jsonPath("$.data.items[0].unresolvedConditions[0].conditionId").value(nullValue()))
				.andExpect(jsonPath("$.data.items[1].reasons[0]").isString())
				.andExpect(jsonPath("$.data.items[1].unmetConditions[0].actualValue").value(3.3))
				.andExpect(jsonPath("$.data.items[1].unmetConditions[0].gap").value(0.2))
				.andExpect(jsonPath("$.data.items[1].unresolvedConditions[0].reasonCode").value("UNSUPPORTED_RULE"))
				.andExpect(jsonPath("$.data.items[2].title").value("분석 시점 공고"))
				.andExpect(jsonPath("$.data.items[2].scholarshipVersion").value(1))
				.andExpect(jsonPath("$.data.items[2].applicationEndAt").value(deadline.toString()))
				.andExpect(jsonPath("$.data.items[2].applicationPeriodStatus").value("OPEN"))
				.andExpect(jsonPath("$.data.items[2].applicationPeriodMessage").value(nullValue()))
				.andExpect(jsonPath("$.data.items[2].unmetConditions").isEmpty());
		assertThat(queryResult(analysisId, excluded, "eligibility_status")).isNull();

		mockMvc.perform(get(url).header("Authorization", bearer).param("page", "1").param("size", "1"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.items[*].scholarshipId", contains(Long.toString(ineligible))))
				.andExpect(jsonPath("$.data.totalPages").value(3));
		mockMvc.perform(get(url).header("Authorization", bearer).param("eligibilityStatus", "ELIGIBLE").param("size", "1"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.items[*].scholarshipId", contains(Long.toString(eligible))))
				.andExpect(jsonPath("$.data.totalPages").value(1))
				.andExpect(jsonPath("$.data.totalElements").value(1));
		mockMvc.perform(get(url).header("Authorization", bearer).param("eligibilityStatus", "INELIGIBLE")
						.param("page", Integer.toString(Integer.MAX_VALUE)).param("size", "100"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.items").isEmpty())
				.andExpect(jsonPath("$.data.totalPages").value(1))
				.andExpect(jsonPath("$.data.totalElements").value(1));
	}

	@Test
	void onlyAllowsValidRequestsForOwnedCompletedAnalyses() throws Exception {
		long analysisId = startAnalysis();
		var url = recommendationsUrl(analysisId);
		var bearer = authorization(userId, UserRole.USER);
		var other = users.save(User.create("other-" + UUID.randomUUID() + "@example.test", "test-hash",
				UserRole.USER, "테스트", clock.instant(), false));
		mockMvc.perform(get(url)).andExpect(status().isUnauthorized());
		mockMvc.perform(get(url).header("Authorization", authorization(userId, UserRole.ADMIN)))
				.andExpect(status().isForbidden());
		mockMvc.perform(get(url).header("Authorization", authorization(other.getId().toString(), UserRole.USER)))
				.andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
		mockMvc.perform(get(recommendationsUrl(Long.MAX_VALUE)).header("Authorization", bearer))
				.andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
		mockMvc.perform(get(url).header("Authorization", bearer).param("page", "-1"))
				.andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
		mockMvc.perform(get(url).header("Authorization", bearer).param("size", "101"))
				.andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
		mockMvc.perform(get(url).header("Authorization", bearer).param("eligibilityStatus", "UNKNOWN"))
				.andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
		mockMvc.perform(get(url).header("Authorization", bearer))
				.andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("ANALYSIS_NOT_COMPLETED"));

		assertThat(execution.claimNext()).contains(analysisId);
		mockMvc.perform(get(url).header("Authorization", bearer))
				.andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("ANALYSIS_NOT_COMPLETED"));
		execution.markFailed(analysisId);
		mockMvc.perform(get(url).header("Authorization", bearer))
				.andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("ANALYSIS_FAILED"));

		long emptyAnalysis = startAnalysis();
		assertThat(worker.runNext()).isTrue();
		mockMvc.perform(get(recommendationsUrl(emptyAnalysis)).header("Authorization", bearer))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.items").isEmpty())
				.andExpect(jsonPath("$.data.totalPages").value(0))
				.andExpect(jsonPath("$.data.totalElements").value(0));
	}

	@Test
	void returnsPinnedSourcesAndDocumentsWhenEvidenceIsUnavailable() throws Exception {
		long scholarship = addScholarship("no-evidence", "READY");
		long originalDocument = addDocument(scholarship, 1, "기존 원본.pdf", OriginalFileType.PDF, "a");
		long id = startAnalysis();
		addVersion(scholarship, 2);
		addDocument(scholarship, 2, "수정 원본.hwp", OriginalFileType.HWP, "b");
		jdbc.update("update scholarship_versions set source_url = ? where scholarship_id = ? and version = 2",
				"https://example.test/new-notice", scholarship);
		assertThat(worker.runNext()).isTrue();

		mockMvc.perform(get(evidenceUrl(id, scholarship)).header("Authorization", authorization(userId, UserRole.USER)))
				.andExpect(status().isOk())
				.andExpect(header().string("Cache-Control", "no-store"))
				.andExpect(jsonPath("$.meta.requestId").isString())
				.andExpect(jsonPath("$.data.analysisId").value(Long.toString(id)))
				.andExpect(jsonPath("$.data.scholarshipId").value(Long.toString(scholarship)))
				.andExpect(jsonPath("$.data.scholarshipVersion").value(1))
				.andExpect(jsonPath("$.data.sourceUrl").value("https://example.test/notice"))
				.andExpect(jsonPath("$.data.evidenceStatus").value("UNAVAILABLE"))
				.andExpect(jsonPath("$.data.message").isNotEmpty())
				.andExpect(jsonPath("$.data.items").isEmpty())
				.andExpect(jsonPath("$.data.documents[*].documentId", contains(Long.toString(originalDocument))))
				.andExpect(jsonPath("$.data.documents[0].fileName").value("기존 원본.pdf"))
				.andExpect(jsonPath("$.data.documents[0].fileType").value("PDF"))
				.andExpect(jsonPath("$.data.documents[0].bucket").doesNotExist())
				.andExpect(jsonPath("$.data.documents[0].key").doesNotExist());
	}

	@Test
	void returnsOnlyStoredEvidenceForTheRequestedAnalysisAndScholarship() throws Exception {
		long scholarship = addScholarship("stored-evidence", "READY");
		long document = addDocument(scholarship, 1, "지원안내.pdf", OriginalFileType.PDF, "a");
		String condition = addCondition(scholarship, 1, "grade", "EQ", "3", "", true);
		long partial = addScholarship("partial-evidence", "READY");
		String partialCondition = addCondition(partial, 1, "other", "TEXT", null, "", true);
		long id = startAnalysis();
		long otherAnalysis = startAnalysis();
		assertThat(worker.runNext()).isTrue();
		assertThat(worker.runNext()).isTrue();

		addEvidence(id, scholarship, condition, document, 2, "지원 자격", "3학년 학생");
		addEvidence(id, scholarship, condition, null, null, null, "게시글의 지원 대상");
		addEvidence(otherAnalysis, scholarship, condition, document, 5, null, "다른 분석 인용");
		addEvidence(id, partial, partialCondition, null, null, null, "추가 서류 안내");
		setEvidenceStatus(id, scholarship, "AVAILABLE", null);
		setEvidenceStatus(id, partial, "PARTIAL", "추천서 조건의 근거는 확인되지 않았습니다.");
		var bearer = authorization(userId, UserRole.USER);
		mockMvc.perform(get(evidenceUrl(id, scholarship)).header("Authorization", bearer))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.evidenceStatus").value("AVAILABLE"))
				.andExpect(jsonPath("$.data.message").value(nullValue()))
				.andExpect(jsonPath("$.data.items[*].quote", contains("3학년 학생", "게시글의 지원 대상")))
				.andExpect(jsonPath("$.data.items[0].conditionId").value(condition))
				.andExpect(jsonPath("$.data.items[0].documentId").value(Long.toString(document)))
				.andExpect(jsonPath("$.data.items[0].page").value(2))
				.andExpect(jsonPath("$.data.items[0].section").value("지원 자격"))
				.andExpect(jsonPath("$.data.items[0].explanation").value("판정에 사용한 조건입니다."))
				.andExpect(jsonPath("$.data.items[0].sourceUrl").value("https://example.test/evidence"))
				.andExpect(jsonPath("$.data.items[1].documentId").value(nullValue()))
				.andExpect(jsonPath("$.data.items[1].page").value(nullValue()));
		mockMvc.perform(get(evidenceUrl(id, partial)).header("Authorization", bearer))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.evidenceStatus").value("PARTIAL"))
				.andExpect(jsonPath("$.data.message").value("추천서 조건의 근거는 확인되지 않았습니다."))
				.andExpect(jsonPath("$.data.items[*].quote", contains("추가 서류 안내")))
				.andExpect(jsonPath("$.data.documents").isEmpty());

		setEvidenceStatus(otherAnalysis, partial, "AVAILABLE", null);
		mockMvc.perform(get(evidenceUrl(otherAnalysis, partial)).header("Authorization", bearer))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.evidenceStatus").value("UNAVAILABLE"))
				.andExpect(jsonPath("$.data.items").isEmpty())
				.andExpect(jsonPath("$.data.message").isNotEmpty());
	}

	@Test
	void onlyAllowsEvidenceForOwnedCompletedIncludedScholarships() throws Exception {
		long scholarship = addScholarship("authorized-evidence", "READY");
		long excluded = addScholarship("excluded-evidence", "PENDING");
		long id = startAnalysis();
		var url = evidenceUrl(id, scholarship);
		var bearer = authorization(userId, UserRole.USER);
		var other = users.save(User.create("other-" + UUID.randomUUID() + "@example.test", "test-hash",
				UserRole.USER, "테스트", clock.instant(), false));

		mockMvc.perform(get(url)).andExpect(status().isUnauthorized());
		mockMvc.perform(get(url).header("Authorization", authorization(userId, UserRole.ADMIN)))
				.andExpect(status().isForbidden());
		mockMvc.perform(get(url).header("Authorization", authorization(other.getId().toString(), UserRole.USER)))
				.andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
		mockMvc.perform(get(url).header("Authorization", bearer))
				.andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("ANALYSIS_NOT_COMPLETED"));

		assertThat(execution.claimNext()).contains(id);
		mockMvc.perform(get(url).header("Authorization", bearer))
				.andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("ANALYSIS_NOT_COMPLETED"));
		execution.markFailed(id);
		mockMvc.perform(get(url).header("Authorization", bearer))
				.andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("ANALYSIS_FAILED"));

		long completed = startAnalysis();
		assertThat(worker.runNext()).isTrue();
		mockMvc.perform(get(evidenceUrl(completed, excluded)).header("Authorization", bearer))
				.andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
		mockMvc.perform(get(evidenceUrl(completed, Long.MAX_VALUE)).header("Authorization", bearer))
				.andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
		mockMvc.perform(get(evidenceUrl(Long.MAX_VALUE, scholarship)).header("Authorization", bearer))
				.andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
	}

	private long addDocument(long scholarship, int version, String name, OriginalFileType type, String digestChar) {
		var attachment = new StoredAttachment(name, URI.create("https://example.test/file"), type, digestChar.repeat(64),
				128, "test-bucket", "private/" + digestChar, null);
		long id = documents.save(new ScholarshipDocument(scholarship, attachment, clock.instant())).getId();
		versionDocuments.save(new VersionDocument(scholarship, version, id));
		return id;
	}

	private void addEvidence(long analysis, long scholarship, String condition, Long document, Integer page,
			String section, String quote) {
		jdbc.update("""
				insert into analysis_evidence
				    (analysis_id, scholarship_id, scholarship_version, condition_id, document_id, quote,
				     explanation, page_number, section_label, source_url)
				values (?, ?, 1, ?, ?, ?, ?, ?, ?, ?)
				""", analysis, scholarship, condition, document, quote, "판정에 사용한 조건입니다.", page, section,
				"https://example.test/evidence");
	}

	private void setEvidenceStatus(long analysis, long scholarship, String status, String message) {
		jdbc.update("update analysis_items set evidence_status = ?, evidence_message = ? where analysis_id = ? and scholarship_id = ?",
				status, message, analysis, scholarship);
	}

	private static String evidenceUrl(long analysis, long scholarship) {
		return recommendationsUrl(analysis) + "/" + scholarship + "/evidence";
	}

	private static String recommendationsUrl(long analysisId) {
		return "/api/v1/analyses/" + analysisId + "/recommendations";
	}

	private String authorization(String id, UserRole role) {
		return "Bearer " + tokens.createAccessToken(id, role);
	}

	private long startAnalysis() {
		return Long.parseLong(analyses.create(userId, 1).analysisId());
	}

	private long addScholarship(String key, String status) {
		long id = scholarships.save(new Scholarship(key, clock.instant())).getId();
		addVersion(id, 1);
		jdbc.update("update scholarships set processing_status = ? where scholarship_id = ?", status, id);
		return id;
	}

	private void addVersion(long id, int version) {
		Instant now = clock.instant();
		versions.save(new ScholarshipVersion(id, version, "합성 공고", "https://example.test/notice", "", "[]", now));
		jdbc.update("""
				update scholarship_versions set application_start_at = ?, application_end_at = ?
				where scholarship_id = ? and version = ?
				""", utc(now.minusSeconds(60)), utc(now.plusSeconds(3600)), id, version);
	}

	private String addCondition(long id, int version, String field, String operator, String value,
			String quote, boolean required) {
		String conditionId = UUID.randomUUID().toString();
		jdbc.update("""
				insert into scholarship_conditions
				    (scholarship_id, version, condition_id, field_name, operator, expected_value,
				     description, is_required, source_quote)
				values (?, ?, ?, ?, ?, ? FORMAT JSON, ?, ?, ?)
				""", id, version, conditionId, field, operator, value, "합성 자격조건", required, quote);
		return conditionId;
	}

	private String queryResult(long id, long scholarshipId, String column) {
		return jdbc.queryForObject("select " + column + " from analysis_items where analysis_id = ? and scholarship_id = ?",
				String.class, id, scholarshipId);
	}

	private static OffsetDateTime utc(Instant at) {
		return at.atOffset(ZoneOffset.UTC);
	}
}
