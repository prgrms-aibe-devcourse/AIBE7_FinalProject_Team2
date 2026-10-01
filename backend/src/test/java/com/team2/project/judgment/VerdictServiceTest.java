package com.team2.project.judgment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.team2.project.experience.domain.ExperienceStatus;
import com.team2.project.experience.domain.InvalidExperienceStateException;
import com.team2.project.experience.repository.ExperienceRepository;
import com.team2.project.judgment.domain.InvalidJudgmentException;
import com.team2.project.judgment.dto.VerdictRequest;
import com.team2.project.judgment.dto.VerdictRequest.FactorItem;
import com.team2.project.judgment.service.VerdictFormService;
import com.team2.project.judgment.service.VerdictService;
import com.team2.project.legalcase.domain.PenaltyType;
import jakarta.validation.Validator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@Transactional
class VerdictServiceTest {
	@Autowired JdbcTemplate jdbc;
	@Autowired VerdictService service;
	@Autowired VerdictFormService forms;
	@Autowired ExperienceRepository experiences;
	@Autowired PlatformTransactionManager transactionManager;
	@Autowired Validator beanValidator;
	private Long caseId;
	private Long experienceId;
	private Long factorId;
	private UUID userId;

	@BeforeEach
	void setUp() {
		caseId = jdbc.queryForObject("""
			INSERT INTO legal_case (title, crime_type, charge_name, short_intro, overview, applied_law, statutory_penalty_text,
			    recommended_min_months, recommended_max_months, recommended_basis, status)
			VALUES ('BE-9 시험 사건', 'MURDER', '살인', '소개', '개요', '형법 제250조 제1항', '사형, 무기 또는 5년 이상의 징역',
			    84, 144, '시험 산출 근거', 'PUBLISHED') RETURNING id
			""", Long.class);
		jdbc.update("""
			INSERT INTO penalty_rule (case_id, penalty_type, allowed_min, allowed_max, suspension_allowed, display_order)
			VALUES (?, 'PRISON', 30, 360, true, 3), (?, 'DEATH', 240, 600, false, 1), (?, 'LIFE', 120, 600, false, 2)
			""", caseId, caseId, caseId);
		factorId = jdbc.queryForObject("""
			INSERT INTO factor (case_id, label, reveal_stage, summary_tag, display_order)
			VALUES (?, '상세 요소', 'DETAIL', '상세', 2) RETURNING id
			""", Long.class, caseId);
		jdbc.update("INSERT INTO factor (case_id, label, reveal_stage, summary_tag, display_order) VALUES (?, '개요 요소', 'OVERVIEW', '개요', 1)", caseId);
		userId = UUID.randomUUID();
		jdbc.update("INSERT INTO anonymous_user (id) VALUES (?)", userId);
		experienceId = jdbc.queryForObject("""
			INSERT INTO experience (anonymous_user_id, case_id, status, last_reviewed_step)
			VALUES (?, ?, 'REVIEWED', 4) RETURNING id
			""", Long.class, userId, caseId);
	}

	@AfterEach
	void cleanCommittedFixtures() {
		if (TransactionSynchronizationManager.isActualTransactionActive()) return; // 일반 테스트는 자동 롤백
		new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
			jdbc.update("DELETE FROM judgment_factor WHERE judgment_id IN (SELECT id FROM judgment WHERE experience_id = ?)", experienceId);
			jdbc.update("DELETE FROM judgment WHERE experience_id = ?", experienceId);
			jdbc.update("DELETE FROM experience WHERE id = ?", experienceId);
			jdbc.update("DELETE FROM anonymous_user WHERE id = ?", userId);
			jdbc.update("DELETE FROM factor WHERE case_id = ?", caseId);
			jdbc.update("DELETE FROM penalty_rule WHERE case_id = ?", caseId);
			jdbc.update("DELETE FROM legal_case WHERE id = ?", caseId);
		});
	}

	private VerdictRequest validRequest() {
		var request = new VerdictRequest("LIFE", "PRISON", 480, null, null, List.of(new FactorItem(factorId, "DOWN")), null);
		assertThat(beanValidator.validate(request)).isEmpty();
		return request;
	}

	@Test
	@Transactional(propagation = Propagation.NOT_SUPPORTED)
	void getForm_reviewed_returnsOrderedPublicFields() {
		var result = forms.getForm(experienceId);
		assertThat(result.penaltyOptions()).extracting(option -> option.penaltyType())
			.containsExactly(PenaltyType.DEATH, PenaltyType.LIFE, PenaltyType.PRISON);
		assertThat(result.penaltyOptions().get(0).reducibleTo()).containsExactly(PenaltyType.LIFE, PenaltyType.PRISON);
		assertThat(result.penaltyOptions().get(1).reducibleTo()).containsExactly(PenaltyType.PRISON);
		assertThat(result.penaltyOptions().get(2).reducibleTo()).isEmpty();
		assertThat(result.penaltyOptions().get(0).text()).isEqualTo("사형 (감경하면 무기징역 또는 징역 20년 ~ 50년)");
		assertThat(result.penaltyOptions().get(2).allowedMin()).isEqualTo(30);
		assertThat(result.penaltyOptions().get(2).allowedMax()).isEqualTo(360);
		assertThat(result.penaltyOptions().get(2).suspensionAllowed()).isTrue();
		assertThat(result.statutoryPenaltyText()).isEqualTo("사형, 무기 또는 5년 이상의 징역");
		assertThat(result.recommended().minMonths()).isEqualTo(84);
		assertThat(result.recommended().maxMonths()).isEqualTo(144);
		assertThat(result.recommended().basis()).isEqualTo("시험 산출 근거");
		assertThat(result.factors()).extracting(factor -> factor.label()).containsExactly("개요 요소", "상세 요소");
		assertThat(result.suspensionRule().maxPrisonMonths()).isEqualTo(36);
		assertThat(result.suspensionRule().maxFineAmount()).isEqualTo(5000000);
		assertThat(result.suspensionRule().minMonths()).isEqualTo(12);
		assertThat(result.suspensionRule().maxMonths()).isEqualTo(60);
		String json = tools.jackson.databind.json.JsonMapper.builder().build().writeValueAsString(result);
		assertThat(json).doesNotContain("allowedBasis").contains("\"penaltyOptions\"", "\"reducibleTo\"", "\"factors\"");
	}

	@Test
	void getForm_preJudged_rejectsState() {
		jdbc.update("UPDATE experience SET status = 'PRE_JUDGED' WHERE id = ?", experienceId);
		assertThatThrownBy(() -> forms.getForm(experienceId)).isInstanceOfSatisfying(InvalidExperienceStateException.class,
			exception -> assertThat(exception.getCurrentStatus()).isEqualTo(ExperienceStatus.PRE_JUDGED));
	}

	@Test
	@Transactional(propagation = Propagation.NOT_SUPPORTED)
	void submit_validRequest_commitsJudgmentFactorsAndState() {
		// 서비스 자체 트랜잭션이 끝난 뒤 새 트랜잭션에서 DB 저장 결과를 확인한다.
		assertThat(service.submit(experienceId, validRequest()).status()).isEqualTo(ExperienceStatus.VERDICT_CONFIRMED);
		new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
			var rows = jdbc.queryForList("SELECT subject_type, timing, penalty_type, reduced_to, prison_months FROM judgment WHERE experience_id = ?", experienceId);
			assertThat(rows).hasSize(1);
			assertThat(rows.get(0)).containsEntry("subject_type", "USER").containsEntry("timing", "FINAL")
				.containsEntry("penalty_type", "LIFE").containsEntry("reduced_to", "PRISON").containsEntry("prison_months", 480);
			assertThat(jdbc.queryForObject("SELECT direction FROM judgment_factor WHERE judgment_id IN (SELECT id FROM judgment WHERE experience_id = ?)", String.class, experienceId)).isEqualTo("DOWN");
			var found = experiences.findById(experienceId).orElseThrow();
			assertThat(found.getStatus()).isEqualTo(ExperienceStatus.VERDICT_CONFIRMED);
			assertThat(found.getVerdictConfirmedAt()).isNotNull();
		});
		assertThatThrownBy(() -> service.submit(experienceId, validRequest())).isInstanceOf(InvalidExperienceStateException.class);
	}

	@Test
	@Transactional(propagation = Propagation.NOT_SUPPORTED)
	void submit_wrongState_doesNotSave() {
		jdbc.update("UPDATE experience SET status = 'PRE_JUDGED' WHERE id = ?", experienceId);
		assertThatThrownBy(() -> service.submit(experienceId, validRequest())).isInstanceOf(InvalidExperienceStateException.class);
		assertUnchanged("PRE_JUDGED");
	}

	@Test
	@Transactional(propagation = Propagation.NOT_SUPPORTED)
	void submit_invalidRange_doesNotSave() {
		var request = new VerdictRequest("LIFE", "PRISON", 119, null, null, null, null);
		assertThat(beanValidator.validate(request)).isEmpty();
		assertThatThrownBy(() -> service.submit(experienceId, request)).isInstanceOf(InvalidJudgmentException.class);
		assertUnchanged("REVIEWED");
	}

	private void assertUnchanged(String status) {
		assertThat(jdbc.queryForObject("SELECT count(*) FROM judgment WHERE experience_id = ?", Integer.class, experienceId)).isZero();
		assertThat(jdbc.queryForObject("SELECT status FROM experience WHERE id = ?", String.class, experienceId)).isEqualTo(status);
	}

	@Test
	@Transactional(propagation = Propagation.NOT_SUPPORTED)
	void submit_concurrentRequests_onlyOneCommits() throws Exception {
		var executor = Executors.newFixedThreadPool(2);
		var barrier = new CyclicBarrier(2);
		var request = validRequest();
		Callable<String> submit = () -> {
			try {
				return new TransactionTemplate(transactionManager).execute(status -> {
					// 두 트랜잭션 모두 REVIEWED를 읽은 뒤 제출해 INSERT 경합을 강제한다.
					experiences.findById(experienceId).orElseThrow();
					try { barrier.await(10, TimeUnit.SECONDS); }
					catch (Exception exception) { throw new IllegalStateException(exception); }
					service.submit(experienceId, request);
					return "SUCCESS";
				});
			} catch (InvalidExperienceStateException exception) {
				assertThat(exception.getCurrentStatus()).isEqualTo(ExperienceStatus.VERDICT_CONFIRMED);
				return "INVALID_STATE";
			}
		};
		try {
			var first = executor.submit(submit);
			var second = executor.submit(submit);
			assertThat(List.of(first.get(20, TimeUnit.SECONDS), second.get(20, TimeUnit.SECONDS)))
				.containsExactlyInAnyOrder("SUCCESS", "INVALID_STATE");
			assertThat(jdbc.queryForObject("SELECT count(*) FROM judgment WHERE experience_id = ? AND timing = 'FINAL'", Integer.class, experienceId)).isEqualTo(1);
			assertThat(jdbc.queryForObject("SELECT status FROM experience WHERE id = ?", String.class, experienceId)).isEqualTo("VERDICT_CONFIRMED");
		} finally {
			executor.shutdownNow();
			assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
		}
	}
}
