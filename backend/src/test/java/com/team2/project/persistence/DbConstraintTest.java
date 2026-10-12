package com.team2.project.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/**
 * DB 제약(부분 유니크 인덱스 · 유니크 · CHECK)이 실제 PostgreSQL에서 잘못된 데이터를 거절하는지 검증한다 (BE-5).
 *
 * 엔티티 · 서비스는 잘못된 값을 먼저 막지만, AI · 재판부 판결과 사건 콘텐츠는 팀이 SQL로 직접 넣는다.
 * 그래서 서비스를 거치지 않는 SQL로 규칙을 어기는 행을 넣어 보고, 어느 제약이 막았는지 이름까지 확인한다.
 * 부분 유니크 인덱스는 PostgreSQL 전용이라 H2 같은 인메모리 DB로는 검증할 수 없고, CI의 PostgreSQL 컨테이너에서 돌린다.
 *
 * 테스트마다 롤백된다. 거절 확인은 항상 각 테스트의 마지막 문장이다 (제약 위반이 나면 그 트랜잭션은 더 진행할 수 없다).
 */
@SpringBootTest
@Transactional
class DbConstraintTest {

	/** 행을 만들 때 쓰는 자리표시자. 실제 ID는 setUp에서 만든 값으로 바꾼다 */
	private enum Ref { CASE, OTHER_CASE, EXPERIENCE, RANGE_OPTION }

	@Autowired JdbcTemplate jdbc;

	private Long caseId;
	private Long otherCaseId;
	private Long experienceId;
	private Long factorId;
	private Long rangeOptionId;
	private UUID anonymousId;

	@BeforeEach
	void setUp() {
		caseId = insertCase("제약 시험 사건 A");
		otherCaseId = insertCase("제약 시험 사건 B");
		anonymousId = UUID.randomUUID();
		jdbc.update("INSERT INTO anonymous_user (id) VALUES (?)", anonymousId);
		experienceId = jdbc.queryForObject(
			"INSERT INTO experience (anonymous_user_id, case_id) VALUES (?, ?) RETURNING id", Long.class, anonymousId, caseId);
		factorId = jdbc.queryForObject("""
			INSERT INTO factor (case_id, label, reveal_stage, summary_tag, display_order)
			VALUES (?, '시험 요소', 'OVERVIEW', '시험', 1) RETURNING id""", Long.class, caseId);
		rangeOptionId = jdbc.queryForObject(
			"SELECT id FROM sentence_range_option WHERE crime_type = 'MURDER' ORDER BY id LIMIT 1", Long.class);
	}

	// ---------- judgment: 부분 유니크 인덱스 · 유니크 ----------

	@Test
	@DisplayName("사건마다 공개된 AI · 재판부 판결은 각각 1개만 넣을 수 있다 (부분 유니크 인덱스)")
	void uniquePublished_secondPublishedJudgment_isRejected() {
		// 허용: 비공개 AI 판결은 여러 개, 공개 AI 1개 + 공개 재판부 1개(주체가 다름), 다른 사건의 공개 AI 1개
		insertJudgment(row("subject_type", "AI", "timing", "FINAL", "penalty_type", "PRISON", "prison_months", 100, "is_published", false));
		insertJudgment(row("subject_type", "AI", "timing", "FINAL", "penalty_type", "PRISON", "prison_months", 110, "is_published", false));
		insertJudgment(row("subject_type", "AI", "timing", "FINAL", "penalty_type", "PRISON", "prison_months", 144));
		insertJudgment(row("subject_type", "COURT", "timing", "FINAL", "penalty_type", "PRISON", "prison_months", 120));
		insertJudgment(row("case_id", Ref.OTHER_CASE, "subject_type", "AI", "timing", "FINAL", "penalty_type", "PRISON", "prison_months", 144));
		assertThat(jdbc.queryForObject(
			"SELECT count(*) FROM judgment WHERE case_id = ? AND subject_type = 'AI' AND is_published", Long.class, caseId)).isEqualTo(1L);

		// 거절: 같은 사건에 공개 AI 판결을 하나 더
		assertThatThrownBy(() -> insertJudgment(
			row("subject_type", "AI", "timing", "FINAL", "penalty_type", "PRISON", "prison_months", 90)))
			.isInstanceOf(DataIntegrityViolationException.class)
			.hasMessageContaining("uk_judgment_published");
	}

	@Test
	@DisplayName("한 체험에 사전 판단 · 최종 판결은 각각 한 번만 저장된다")
	void uniqueExperienceTiming_secondJudgment_isRejected() {
		// 허용: 같은 체험에 사전 판단 1개 + 최종 판결 1개
		insertJudgment(row("subject_type", "USER", "timing", "PRE", "experience_id", Ref.EXPERIENCE, "range_option_id", Ref.RANGE_OPTION));
		insertJudgment(row("subject_type", "USER", "timing", "FINAL", "experience_id", Ref.EXPERIENCE,
			"penalty_type", "PRISON", "prison_months", 30));

		// 거절: 사전 판단을 다시 제출 (수정 · 재제출 불가)
		assertThatThrownBy(() -> insertJudgment(
			row("subject_type", "USER", "timing", "PRE", "experience_id", Ref.EXPERIENCE, "range_option_id", Ref.RANGE_OPTION)))
			.isInstanceOf(DataIntegrityViolationException.class)
			.hasMessageContaining("uk_judgment_experience_timing");
	}

	// ---------- judgment: CHECK ----------

	/** 제약 하나만 어기는 행. 한 행이 여러 제약을 어기면 이름순으로 먼저 걸리는 제약이 보고되므로 하나씩만 어기게 만든다 */
	static Stream<Arguments> judgmentViolations() {
		return Stream.of(
			Arguments.of("사전 판단에 형벌 값이 있음", "chk_judgment_pre",
				row("subject_type", "USER", "timing", "PRE", "experience_id", Ref.EXPERIENCE, "range_option_id", Ref.RANGE_OPTION, "prison_months", 12)),
			Arguments.of("최종 판결에 형벌 종류가 없음", "chk_judgment_final",
				row("subject_type", "USER", "timing", "FINAL", "experience_id", Ref.EXPERIENCE)),
			Arguments.of("징역인데 개월이 없음", "chk_judgment_penalty_values",
				row("subject_type", "AI", "timing", "FINAL", "penalty_type", "PRISON")),
			Arguments.of("사형 · 무기를 고른 뒤 감경해도 집행유예", "chk_judgment_death_life_no_suspension",
				row("subject_type", "AI", "timing", "FINAL", "penalty_type", "LIFE", "reduced_to", "PRISON", "prison_months", 120, "suspension_months", 12)),
			Arguments.of("허용되지 않은 감경(무기 → 무기)", "chk_judgment_reduced_to",
				row("subject_type", "AI", "timing", "FINAL", "penalty_type", "LIFE", "reduced_to", "LIFE")),
			Arguments.of("징역 0개월", "chk_judgment_positive_values",
				row("subject_type", "AI", "timing", "FINAL", "penalty_type", "PRISON", "prison_months", 0)),
			Arguments.of("AI 판결이 체험에 속함", "chk_judgment_user_experience",
				row("subject_type", "AI", "timing", "FINAL", "penalty_type", "PRISON", "prison_months", 12, "experience_id", Ref.EXPERIENCE)),
			Arguments.of("사용자 판단이 비공개", "chk_judgment_user_published",
				row("subject_type", "USER", "timing", "PRE", "experience_id", Ref.EXPERIENCE, "range_option_id", Ref.RANGE_OPTION, "is_published", false)),
			Arguments.of("없는 판단 주체", "chk_judgment_subject_type",
				row("subject_type", "JURY", "timing", "FINAL", "penalty_type", "PRISON", "prison_months", 12)),
			Arguments.of("없는 판단 시점", "chk_judgment_timing",
				row("subject_type", "AI", "timing", "MID")));
	}

	@ParameterizedTest(name = "{0} → {1}")
	@MethodSource("judgmentViolations")
	@DisplayName("판단(judgment)의 CHECK 제약이 규칙을 어긴 행을 거절한다")
	void judgmentCheck_violation_isRejected(String description, String constraint, Map<String, Object> row) {
		assertThatThrownBy(() -> insertJudgment(row))
			.isInstanceOf(DataIntegrityViolationException.class)
			.hasMessageContaining(constraint);
	}

	@Test
	@DisplayName("사형 · 무기를 감경한 판결은 최종 선고 형벌 기준으로 값을 받는다")
	void judgmentCheck_reducedSentence_isAccepted() {
		insertJudgment(row("subject_type", "AI", "timing", "FINAL", "penalty_type", "DEATH", "is_published", false));
		insertJudgment(row("subject_type", "AI", "timing", "FINAL", "penalty_type", "DEATH", "reduced_to", "LIFE", "is_published", false));
		insertJudgment(row("subject_type", "AI", "timing", "FINAL", "penalty_type", "LIFE", "reduced_to", "PRISON", "prison_months", 180, "is_published", false));
		insertJudgment(row("subject_type", "AI", "timing", "FINAL", "penalty_type", "PRISON", "prison_months", 36, "suspension_months", 24, "is_published", false));
		assertThat(jdbc.queryForObject("SELECT count(*) FROM judgment WHERE case_id = ?", Long.class, caseId)).isEqualTo(4L);
	}

	// ---------- judgment_factor ----------

	@Test
	@DisplayName("같은 판단에 같은 판단 요소를 두 번 기록할 수 없다")
	void uniqueJudgmentFactor_duplicate_isRejected() {
		Long judgmentId = insertJudgment(row("subject_type", "USER", "timing", "PRE", "experience_id", Ref.EXPERIENCE, "range_option_id", Ref.RANGE_OPTION));
		jdbc.update("INSERT INTO judgment_factor (judgment_id, factor_id) VALUES (?, ?)", judgmentId, factorId);

		assertThatThrownBy(() -> jdbc.update("INSERT INTO judgment_factor (judgment_id, factor_id) VALUES (?, ?)", judgmentId, factorId))
			.isInstanceOf(DataIntegrityViolationException.class)
			.hasMessageContaining("uk_judgment_factor_judgment_factor");
	}

	@Test
	@DisplayName("판단 요소의 방향은 UP · DOWN만 허용한다")
	void checkDirection_unknownValue_isRejected() {
		Long judgmentId = insertJudgment(row("subject_type", "USER", "timing", "PRE", "experience_id", Ref.EXPERIENCE, "range_option_id", Ref.RANGE_OPTION));

		assertThatThrownBy(() -> jdbc.update(
			"INSERT INTO judgment_factor (judgment_id, factor_id, direction) VALUES (?, ?, 'SIDEWAYS')", judgmentId, factorId))
			.isInstanceOf(DataIntegrityViolationException.class)
			.hasMessageContaining("chk_judgment_factor_direction");
	}

	// ---------- factor ----------

	@Test
	@DisplayName("판단 요소의 가치관 축은 네 축이거나 NULL만 허용한다 (BE-47)")
	void checkValueAxis_unknownValue_isRejected() {
		// 허용: NULL(어느 축에도 맞지 않음) · 네 축
		assertThat(jdbc.queryForObject("SELECT value_axis FROM factor WHERE id = ?", String.class, factorId)).isNull();
		for (String axis : List.of("APOLOGY_SINCERITY", "FAULT_STANDARD", "PRINCIPLE_RELATION", "ORDER_OPPORTUNITY")) {
			jdbc.update("UPDATE factor SET value_axis = ? WHERE id = ?", axis, factorId);
		}
		jdbc.update("UPDATE factor SET value_axis = NULL WHERE id = ?", factorId);

		assertThatThrownBy(() -> jdbc.update("UPDATE factor SET value_axis = 'EMBEDDING' WHERE id = ?", factorId))
			.isInstanceOf(DataIntegrityViolationException.class)
			.hasMessageContaining("chk_factor_value_axis");
	}

	@Test
	@DisplayName("판단 요소의 가치관 축 후검수 상태는 기본 AUTO이고 AUTO · CONFIRMED만 허용한다 (BE-48)")
	void checkValueAxisStatus_unknownValue_isRejected() {
		// 허용: 기본값 AUTO, 관리자 확정 CONFIRMED
		assertThat(jdbc.queryForObject("SELECT value_axis_status FROM factor WHERE id = ?", String.class, factorId)).isEqualTo("AUTO");
		jdbc.update("UPDATE factor SET value_axis_status = 'CONFIRMED' WHERE id = ?", factorId);

		assertThatThrownBy(() -> jdbc.update("UPDATE factor SET value_axis_status = 'REVIEWED' WHERE id = ?", factorId))
			.isInstanceOf(DataIntegrityViolationException.class)
			.hasMessageContaining("chk_factor_value_axis_status");
	}

	// ---------- penalty_rule ----------

	@Test
	@DisplayName("사건마다 형벌 종류별 규칙은 1개만 둘 수 있다")
	void uniquePenaltyRule_duplicateType_isRejected() {
		String sql = "INSERT INTO penalty_rule (case_id, penalty_type, allowed_min, allowed_max, suspension_allowed, display_order) "
			+ "VALUES (?, ?, 1, 360, true, ?)";
		jdbc.update(sql, caseId, "PRISON", 1);
		jdbc.update(sql, otherCaseId, "PRISON", 1);	// 다른 사건은 같은 형벌 규칙을 둘 수 있다

		assertThatThrownBy(() -> jdbc.update(sql, caseId, "PRISON", 2))
			.isInstanceOf(DataIntegrityViolationException.class)
			.hasMessageContaining("uk_penalty_rule_case_penalty_type");
	}

	@ParameterizedTest(name = "{0}")
	@MethodSource("deathLifeRuleViolations")
	@DisplayName("사형 · 무기 규칙은 법정형 범위가 없고 집행유예를 허용하지 않는다")
	void checkPenaltyRule_deathLifeViolation_isRejected(String description, String sql) {
		assertThatThrownBy(() -> jdbc.update(sql.replace("{case}", String.valueOf(caseId))))
			.isInstanceOf(DataIntegrityViolationException.class)
			.hasMessageContaining("chk_penalty_rule_death_life");
	}

	static Stream<Arguments> deathLifeRuleViolations() {
		String head = "INSERT INTO penalty_rule (case_id, penalty_type, statutory_max, allowed_min, allowed_max, suspension_allowed, display_order) VALUES ";
		return Stream.of(
			Arguments.of("사형 규칙에 법정형 상한이 있음", head + "({case}, 'DEATH', 600, 240, 600, false, 1)"),
			Arguments.of("무기 규칙에 집행유예를 허용함", head + "({case}, 'LIFE', NULL, 120, 600, true, 1)"));
	}

	// ---------- experience ----------

	@Test
	@DisplayName("같은 사용자가 같은 사건을 같은 회차로 두 번 시작할 수 없다 (동시 요청 방지)")
	void uniqueExperience_sameUserCaseAttempt_isRejected() {
		jdbc.update("INSERT INTO experience (anonymous_user_id, case_id, attempt_no) VALUES (?, ?, 2)", anonymousId, caseId);
		jdbc.update("INSERT INTO experience (anonymous_user_id, case_id) VALUES (?, ?)", anonymousId, otherCaseId);

		assertThatThrownBy(() -> jdbc.update("INSERT INTO experience (anonymous_user_id, case_id) VALUES (?, ?)", anonymousId, caseId))
			.isInstanceOf(DataIntegrityViolationException.class)
			.hasMessageContaining("uk_experience_user_case_attempt");
	}

	@ParameterizedTest(name = "{0} → {1}")
	@MethodSource("experienceViolations")
	@DisplayName("체험의 섹션 확인 번호 · 회차 · 상태 범위를 벗어난 값은 거절한다")
	void checkExperience_outOfRange_isRejected(String description, String constraint, String values) {
		assertThatThrownBy(() -> jdbc.update(
			"INSERT INTO experience (anonymous_user_id, case_id, attempt_no, last_reviewed_step, status) VALUES (?, ?, " + values + ")",
			anonymousId, otherCaseId))
			.isInstanceOf(DataIntegrityViolationException.class)
			.hasMessageContaining(constraint);
	}

	static Stream<Arguments> experienceViolations() {
		return Stream.of(
			Arguments.of("확인한 섹션 5", "chk_experience_last_reviewed_step", "1, 5, 'STARTED'"),
			Arguments.of("회차 0", "chk_experience_attempt_no", "0, 0, 'STARTED'"),
			Arguments.of("없는 상태", "chk_experience_status", "1, 0, 'FINISHED'"));
	}

	// ---------- 도우미 ----------

	private Long insertCase(String title) {
		return jdbc.queryForObject("""
			INSERT INTO legal_case (title, crime_type, charge_name, short_intro, overview, applied_law,
			    statutory_penalty_text, status)
			VALUES (?, 'MURDER', '살인', '소개', '개요', '형법 제250조 제1항', '사형, 무기 또는 5년 이상의 징역', 'PUBLISHED')
			RETURNING id""", Long.class, title);
	}

	/** 판단 행. 기본값은 시험 사건의 공개 판단이고, 넘긴 열 이름 · 값으로 덮어쓴다. 값이 없는 열은 넣지 않는다 (NULL) */
	private static Map<String, Object> row(Object... columnValues) {
		Map<String, Object> row = new LinkedHashMap<>();
		row.put("case_id", Ref.CASE);
		row.put("is_published", true);
		for (int i = 0; i < columnValues.length; i += 2) {
			row.put((String) columnValues[i], columnValues[i + 1]);
		}
		return row;
	}

	private Long insertJudgment(Map<String, Object> row) {
		List<String> columns = new ArrayList<>(row.keySet());
		String sql = "INSERT INTO judgment (" + String.join(", ", columns) + ") VALUES ("
			+ columns.stream().map(column -> "?").collect(Collectors.joining(", ")) + ") RETURNING id";
		Object[] args = columns.stream().map(column -> resolve(row.get(column))).toArray();
		return jdbc.queryForObject(sql, Long.class, args);
	}

	private Object resolve(Object value) {
		if (value instanceof Ref ref) {
			return switch (ref) {
				case CASE -> caseId;
				case OTHER_CASE -> otherCaseId;
				case EXPERIENCE -> experienceId;
				case RANGE_OPTION -> rangeOptionId;
			};
		}
		return value;
	}
}
