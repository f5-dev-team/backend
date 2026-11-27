package com.f5.scholarship_backend.domain.analysis.service;

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
import com.f5.scholarship_backend.domain.scholarship.entity.Scholarship;
import com.f5.scholarship_backend.domain.scholarship.entity.ScholarshipVersion;
import com.f5.scholarship_backend.domain.scholarship.repository.ScholarshipRepository;
import com.f5.scholarship_backend.domain.scholarship.repository.ScholarshipVersionRepository;
import com.f5.scholarship_backend.domain.student.dto.StudentProfileSaveRequest;
import com.f5.scholarship_backend.domain.student.service.StudentProfileService;
import com.f5.scholarship_backend.domain.user.entity.User;
import com.f5.scholarship_backend.domain.user.enums.UserRole;
import com.f5.scholarship_backend.domain.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import tools.jackson.databind.json.JsonMapper;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
		"spring.datasource.url=jdbc:h2:mem:analysisWorkerTest",
		"analysis.worker.enabled=false",
		"student-profile.department-max-grades.CS=4"
})
@ActiveProfiles("test")
@ExtendWith(OutputCaptureExtension.class)
class AnalysisWorkerIntegrationTest {
	private static final String PROFILE = """
			{"departmentCode":"CS","grade":3,"enrollmentStatus":"ENROLLED","cumulativeGpa":3.30,
			"previousSemesterGpa":null,"incomeAssessment":null,"specialEligibility":null,
			"volunteerMinutes":750,"birthDate":"2004-06-01","residence":null,"gender":null}
			""";

	@Autowired private AnalysisService analyses;
	@Autowired private AnalysisWorker worker;
	@Autowired private AnalysisExecution execution;
	@Autowired private StudentProfileService profiles;
	@Autowired private ScholarshipRepository scholarships;
	@Autowired private ScholarshipVersionRepository versions;
	@Autowired private UserRepository users;
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
		for (String table : List.of("analysis_items", "analyses", "scholarship_conditions", "scholarship_versions",
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

	private void addCondition(long id, int version, String field, String operator, String value,
			String quote, boolean required) {
		jdbc.update("""
				insert into scholarship_conditions
				    (scholarship_id, version, condition_id, field_name, operator, expected_value,
				     description, is_required, source_quote)
				values (?, ?, ?, ?, ?, ? FORMAT JSON, ?, ?, ?)
				""", id, version, UUID.randomUUID().toString(), field, operator, value, "합성 자격조건", required, quote);
	}

	private String queryResult(long id, long scholarshipId, String column) {
		return jdbc.queryForObject("select " + column + " from analysis_items where analysis_id = ? and scholarship_id = ?",
				String.class, id, scholarshipId);
	}

	private static OffsetDateTime utc(Instant at) {
		return at.atOffset(ZoneOffset.UTC);
	}
}
