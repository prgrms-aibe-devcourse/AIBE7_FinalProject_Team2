package com.team2.project.experience;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.team2.project.common.exception.BusinessException;
import com.team2.project.common.exception.ErrorCode;
import com.team2.project.experience.domain.ExperienceStatus;
import com.team2.project.experience.domain.InvalidExperienceStateException;
import com.team2.project.experience.domain.InvalidReviewStepException;
import com.team2.project.experience.domain.ReviewStepOutOfOrderException;
import com.team2.project.experience.dto.ReviewStepRequest;
import com.team2.project.experience.dto.ReviewStepResponse;
import com.team2.project.experience.service.ReviewService;
import jakarta.validation.Validator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** 서비스가 커밋한 뒤 별도 DB 조회로 검증한다. 이 클래스가 만든 데이터만 매 테스트 후 삭제한다. */
@SpringBootTest
class ReviewServiceTest {
	@Autowired JdbcTemplate jdbc;
	@Autowired ReviewService service;
	@Autowired PlatformTransactionManager transactionManager;
	@Autowired Validator validator;
	private Long caseId;
	private Long experienceId;
	private UUID userId;

	@BeforeEach
	void setUp() {
		new TransactionTemplate(transactionManager).executeWithoutResult(tx -> {
			caseId = jdbc.queryForObject("""
				INSERT INTO legal_case (title, crime_type, charge_name, short_intro, overview, applied_law,
				    statutory_penalty_text, recommended_min_months, recommended_max_months, recommended_basis, status)
				VALUES ('BE-8 시험 살인 사건', 'MURDER', '살인', '소개', '지인을 살해한 사건', '형법 제250조 제1항 살인',
				    '사형, 무기 또는 5년 이상의 징역', 84, 144, '권고 산출 근거', 'PUBLISHED') RETURNING id
				""", Long.class);
			jdbc.update("""
				INSERT INTO penalty_rule (case_id, penalty_type, allowed_min, allowed_max, suspension_allowed, display_order)
				VALUES (?, 'PRISON', 30, 360, true, 3), (?, 'DEATH', 240, 600, false, 1), (?, 'LIFE', 120, 600, false, 2)
				""", caseId, caseId, caseId);
			section("DETAIL", "SETTLEMENT", "합의 · 피해 회복", "5,000만 원 공탁, 합의에 이르지 못함", null, 4);
			section("ARGUMENT", "DEFENSE", "피고인 · 변호인", "우발적 범행이라는 주장", null, 2);
			section("DETAIL", "FACTS", "주요 사실관계", "3개월 전부터 변제 문제로 다툼", null, 1);
			section("DETAIL", "DAMAGE", "피해 결과", null,
				"[{\"label\":\"피해자 수\",\"value\":\"1명\"},{\"label\":\"피해 결과\",\"value\":\"사망\"},{\"label\":\"피해자와의 관계\",\"value\":\"지인\"},{\"label\":\"범행 도구\",\"value\":\"흉기\"}]", 2);
			section("DETAIL", "DEFENDANT", "피고인 관련 사실", "형사처벌 전력 없음", null, 3);
			section("ARGUMENT", "PROSECUTOR", "검사", "구호 조치 없이 현장을 떠남", null, 1);
			section("LAW", "LAW_TERM", "용어 설명", null, "[{\"term\":\"공탁\",\"desc\":\"배상금을 법원에 맡기는 것\"}]", 1);
			section("SUMMARY", "SUMMARY", "핵심 사실 요약", null, "[\"지인 1명 살해\",\"흉기 사용\",\"5,000만 원 공탁\",\"형사처벌 전력 없음\"]", 1);
			userId = UUID.randomUUID();
			jdbc.update("INSERT INTO anonymous_user (id) VALUES (?)", userId);
			experienceId = jdbc.queryForObject("""
				INSERT INTO experience (anonymous_user_id, case_id, status, last_reviewed_step)
				VALUES (?, ?, 'PRE_JUDGED', 1) RETURNING id
				""", Long.class, userId, caseId);
		});
	}

	private void section(String stage, String type, String title, String content, String data, int order) {
		jdbc.update("""
			INSERT INTO case_section (case_id, stage, section_type, title, content, data, display_order)
			VALUES (?, ?, ?, ?, ?, CAST(? AS jsonb), ?)
			""", caseId, stage, type, title, content, data, order);
	}

	@AfterEach
	void cleanUp() {
		new TransactionTemplate(transactionManager).executeWithoutResult(tx -> {
			jdbc.update("DELETE FROM experience WHERE id = ?", experienceId);
			jdbc.update("DELETE FROM anonymous_user WHERE id = ?", userId);
			jdbc.update("DELETE FROM case_section WHERE case_id = ?", caseId);
			jdbc.update("DELETE FROM penalty_rule WHERE case_id = ?", caseId);
			jdbc.update("DELETE FROM legal_case WHERE id = ?", caseId);
		});
	}

	private void state(ExperienceStatus status, int step) {
		jdbc.update("UPDATE experience SET status = ?, last_reviewed_step = ? WHERE id = ?", status.name(), step, experienceId);
	}
	private Optional<UUID> me() {
		return Optional.of(userId);
	}
	private ReviewStepRequest request(int step) {
		var request = new ReviewStepRequest(step);
		assertThat(validator.validate(request)).isEmpty();
		return request;
	}
	private void stored(ExperienceStatus status, int step) {
		var row = jdbc.queryForMap("SELECT status, last_reviewed_step FROM experience WHERE id = ?", experienceId);
		assertThat(row).containsEntry("status", status.name()).containsEntry("last_reviewed_step", step);
	}
	private void response(ReviewStepResponse result, ExperienceStatus status, int last) {
		assertThat(result.status()).isEqualTo(status);
		assertThat(result.lastReviewedStep()).isEqualTo(last);
		assertThat(result.openStep()).isEqualTo(last == 4 ? null : Integer.valueOf(last + 1));
	}

	@ParameterizedTest
	@CsvSource({"PRE_JUDGED,1,2", "REVIEWING,2,3", "REVIEWING,3,4", "REVIEWED,4,4"})
	void getReview_progress_matchesContract(ExperienceStatus status, int last, int visible) {
		state(status, last);
		var result = service.getReview(caseId, me());
		assertThat(result.status()).isEqualTo(status);
		assertThat(result.lastReviewedStep()).isEqualTo(last);
		assertThat(result.sections()).hasSize(visible);
		assertThat(result.openStep()).isEqualTo(last == 4 ? null : Integer.valueOf(last + 1));
		assertThat(result.lockedSteps()).hasSize(4 - visible);
		assertThat(result.sections()).allSatisfy(section -> assertThat(section.confirmed()).isEqualTo(section.step() <= last));
		assertThat(result.sections().get(1).items()).hasSize(4);
		assertThat(result.sections().get(1).items().get(1).data()).hasSize(4);
		assertThat(result.law() != null).isEqualTo(visible == 4);
		assertThat(result.summary() != null).isEqualTo(last == 4);
		if (visible == 4) {
			assertThat(result.sections().get(3).items()).isEmpty();
			assertThat(result.law().terms()).hasSize(1);
			assertThat(result.law().allowedRanges()).hasSize(3);
		}
		if (last == 4) assertThat(result.summary()).hasSize(4);
	}

	@ParameterizedTest
	@EnumSource(value = ExperienceStatus.class, names = {"STARTED", "VERDICT_CONFIRMED", "AI_REVEALED", "COMPLETED"})
	void getReview_forbiddenState_rejects(ExperienceStatus status) {
		state(status, status == ExperienceStatus.STARTED ? 0 : 4);
		assertThatThrownBy(() -> service.getReview(caseId, me())).isInstanceOfSatisfying(InvalidExperienceStateException.class,
			exception -> assertThat(exception.getExperienceStatus()).isEqualTo(status));
	}

	@Test
	void confirmStep_sequentialAndRepeated_commitsMonotonicProgress() {
		for (int step = 2; step <= 4; step++) {
			var status = step == 4 ? ExperienceStatus.REVIEWED : ExperienceStatus.REVIEWING;
			response(service.confirmStep(caseId, me(), request(step)), status, step);
			stored(status, step);
			response(service.confirmStep(caseId, me(), request(step)), status, step);
		}
		var reviewedAt = jdbc.queryForObject("SELECT reviewed_at FROM experience WHERE id = ?", java.sql.Timestamp.class, experienceId);
		assertThat(reviewedAt).isNotNull();
		response(service.confirmStep(caseId, me(), request(2)), ExperienceStatus.REVIEWED, 4);
		stored(ExperienceStatus.REVIEWED, 4);
		assertThat(jdbc.queryForObject("SELECT reviewed_at FROM experience WHERE id = ?", java.sql.Timestamp.class, experienceId)).isEqualTo(reviewedAt);
	}

	@ParameterizedTest
	@ValueSource(ints = {1, 5})
	void confirmStep_invalidStep_doesNotChangeState(int step) {
		// HTTP에서는 @Valid가 먼저 400으로 거절한다(ReviewStepRequestValidationTest). 서비스 직접 호출도 엔티티가 막는다.
		assertThatThrownBy(() -> service.confirmStep(caseId, me(), new ReviewStepRequest(step))).isInstanceOf(InvalidReviewStepException.class);
		stored(ExperienceStatus.PRE_JUDGED, 1);
	}

	@Test
	void confirmStep_skippedStep_doesNotChangeState() {
		assertThatThrownBy(() -> service.confirmStep(caseId, me(), request(4))).isInstanceOf(ReviewStepOutOfOrderException.class);
		stored(ExperienceStatus.PRE_JUDGED, 1);
	}
	@Test
	void confirmStep_verdictConfirmed_doesNotChangeState() {
		state(ExperienceStatus.VERDICT_CONFIRMED, 4);
		assertThatThrownBy(() -> service.confirmStep(caseId, me(), request(4))).isInstanceOf(InvalidExperienceStateException.class);
		stored(ExperienceStatus.VERDICT_CONFIRMED, 4);
	}
	@Test
	void getReviewAndConfirmStep_missingExperience_rejects() {
		errorCode(() -> service.getReview(caseId, Optional.of(UUID.randomUUID())), ErrorCode.EXPERIENCE_NOT_FOUND);
		errorCode(() -> service.getReview(caseId, Optional.empty()), ErrorCode.EXPERIENCE_NOT_FOUND);
		errorCode(() -> service.confirmStep(caseId, Optional.empty(), request(2)), ErrorCode.EXPERIENCE_NOT_FOUND);
		stored(ExperienceStatus.PRE_JUDGED, 1);
	}
	@Test
	void getReviewAndConfirmStep_missingCase_rejects() {
		errorCode(() -> service.getReview(-1L, me()), ErrorCode.CASE_NOT_FOUND);
		errorCode(() -> service.confirmStep(-1L, me(), request(2)), ErrorCode.CASE_NOT_FOUND);
	}
	private static void errorCode(org.assertj.core.api.ThrowableAssert.ThrowingCallable call, ErrorCode code) {
		assertThatThrownBy(call).isInstanceOfSatisfying(BusinessException.class,
			exception -> assertThat(exception.getErrorCode()).isEqualTo(code));
	}

	// 강사 리뷰(2026-10-06) 반영 · BE-27: 거절이 연달아 와도 아무것도 바뀌지 않고, 다음 올바른 단계는 진행된다
	@Test
	void confirmStep_rejected_thenNextValidStepStillWorks() {
		var before = snapshot();
		assertThatThrownBy(() -> service.confirmStep(caseId, me(), request(4))).isInstanceOf(ReviewStepOutOfOrderException.class);
		assertThat(snapshot()).isEqualTo(before);
		// 5는 @Valid에서 400으로 막히는 값이라 helper(request)의 Bean Validation을 일부러 거치지 않고 서비스에 직접 보낸다
		assertThatThrownBy(() -> service.confirmStep(caseId, me(), new ReviewStepRequest(5))).isInstanceOf(InvalidReviewStepException.class);
		assertThat(snapshot()).isEqualTo(before);

		response(service.confirmStep(caseId, me(), request(2)), ExperienceStatus.REVIEWING, 2);
		stored(ExperienceStatus.REVIEWING, 2);
	}

	// 강사 리뷰(2026-10-06) 반영 · BE-27: 판결 확정 뒤 섹션 확인은 거절되고 상태 · 단계 · 시각이 그대로다
	@Test
	void confirmStep_afterVerdictConfirmed_rejectedAndUnchanged() {
		// 실제 흐름과 같게 확인 완료 · 판결 확정 시각을 채운 픽스처
		jdbc.update("""
			UPDATE experience SET status = 'VERDICT_CONFIRMED', last_reviewed_step = 4,
			    reviewed_at = now() - interval '10 minutes', verdict_confirmed_at = now() - interval '5 minutes'
			WHERE id = ?
			""", experienceId);
		var before = snapshot();

		assertThatThrownBy(() -> service.confirmStep(caseId, me(), request(4))).isInstanceOf(InvalidExperienceStateException.class);

		assertThat(snapshot()).isEqualTo(before);
		assertThat(before.get("verdict_confirmed_at")).isNotNull();
	}

	/** 거절 전후 비교용. 픽스처 값에 기대지 않고 "바뀌지 않았음"을 확인한다 */
	private Map<String, Object> snapshot() {
		return jdbc.queryForMap(
			"SELECT status, last_reviewed_step, reviewed_at, verdict_confirmed_at FROM experience WHERE id = ?", experienceId);
	}

	@Test
	void confirmStep_sameStepConcurrent_bothSucceed() throws Exception {
		state(ExperienceStatus.REVIEWING, 2);
		var executor = Executors.newFixedThreadPool(2);
		var barrier = new CyclicBarrier(2);
		java.util.concurrent.Callable<ReviewStepResponse> call = () -> {
			barrier.await(10, TimeUnit.SECONDS);
			return service.confirmStep(caseId, me(), request(3));
		};
		try {
			var first = executor.submit(call);
			var second = executor.submit(call);
			for (var result : List.of(first.get(15, TimeUnit.SECONDS), second.get(15, TimeUnit.SECONDS))) {
				response(result, ExperienceStatus.REVIEWING, 3);
			}
			stored(ExperienceStatus.REVIEWING, 3);
		} finally {
			executor.shutdownNow();
			assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
		}
	}

	@Test
	void confirmStep_delayedCommit_blocksDuplicateAndNeverRegresses() throws Exception {
		state(ExperienceStatus.REVIEWING, 2);
		var executor = Executors.newFixedThreadPool(2);
		var locked = new CountDownLatch(1);
		var release = new CountDownLatch(1);
		var attempting = new CountDownLatch(1);
		var secondPid = new AtomicInteger();
		try {
			var first = executor.submit(() -> new TransactionTemplate(transactionManager).execute(tx -> {
				var result = service.confirmStep(caseId, me(), request(3));
				locked.countDown();
				await(release);
				return result;
			}));
			assertThat(locked.await(10, TimeUnit.SECONDS)).isTrue();
			var second = executor.submit(() -> {
				var duplicate = new TransactionTemplate(transactionManager).execute(tx -> {
					secondPid.set(jdbc.queryForObject("SELECT pg_backend_pid()", Integer.class));
					attempting.countDown();
					return service.confirmStep(caseId, me(), request(3));
				});
				response(duplicate, ExperienceStatus.REVIEWING, 3);
				return service.confirmStep(caseId, me(), request(4));
			});
			assertThat(attempting.await(10, TimeUnit.SECONDS)).isTrue();
			// 시간 지연만으로 추측하지 않고 PostgreSQL이 실제로 잠금 대기 중인지 확인한다.
			long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
			boolean blocked = false;
			while (System.nanoTime() < deadline && !blocked) {
				blocked = Boolean.TRUE.equals(jdbc.queryForObject("SELECT cardinality(pg_blocking_pids(?)) > 0", Boolean.class, secondPid.get()));
				if (!blocked) Thread.sleep(20);
			}
			assertThat(blocked).isTrue();
			assertThat(second.isDone()).isFalse();
			release.countDown();
			response(first.get(10, TimeUnit.SECONDS), ExperienceStatus.REVIEWING, 3);
			response(second.get(10, TimeUnit.SECONDS), ExperienceStatus.REVIEWED, 4);
			response(service.confirmStep(caseId, me(), request(3)), ExperienceStatus.REVIEWED, 4);
			stored(ExperienceStatus.REVIEWED, 4);
		} finally {
			release.countDown();
			executor.shutdownNow();
			assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
		}
	}

	private static void await(CountDownLatch latch) {
		try {
			if (!latch.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("잠금 테스트 대기 시간 초과");
		} catch (InterruptedException exception) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException(exception);
		}
	}
}
