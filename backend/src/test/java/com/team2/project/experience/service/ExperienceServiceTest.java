package com.team2.project.experience.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.team2.project.common.exception.BusinessException;
import com.team2.project.common.exception.ErrorCode;
import com.team2.project.experience.domain.AnonymousUser;
import com.team2.project.experience.domain.Experience;
import com.team2.project.experience.domain.ExperienceStatus;
import com.team2.project.experience.domain.InvalidExperienceStateException;
import com.team2.project.experience.repository.AnonymousUserRepository;
import com.team2.project.experience.repository.ExperienceRepository;
import com.team2.project.judgment.domain.Judgment;
import com.team2.project.judgment.repository.JudgmentRepository;
import com.team2.project.legalcase.domain.PenaltyType;
import com.team2.project.legalcase.repository.LegalCaseRepository;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 내 체험 찾기 · 상태 전이(조건부 갱신) · 익명 사용자 발급 검증 (실제 PostgreSQL, 테스트마다 롤백)
 * 다른 요청이 먼저 처리한 상황은 같은 트랜잭션에서 JDBC로 상태를 바꿔 재현한다.
 * 최근 접속 시각 갱신은 별도 트랜잭션이라 MyExperienceReadOnlyTransactionTest에서 확인한다.
 */
@SpringBootTest
@Transactional
class ExperienceServiceTest {

	@Autowired JdbcTemplate jdbc;
	@Autowired EntityManager entityManager;
	@Autowired MyExperienceService myExperienceService;
	@Autowired ExperienceTransitionService transitionService;
	@Autowired AnonymousUserService anonymousUserService;
	@Autowired AnonymousUserRepository anonymousUserRepository;
	@Autowired ExperienceRepository experienceRepository;
	@Autowired LegalCaseRepository legalCaseRepository;
	@Autowired JudgmentRepository judgmentRepository;

	private Long caseId;
	private Long draftCaseId;
	private UUID anonymousId;

	@BeforeEach
	void setUp() {
		String sql = """
			INSERT INTO legal_case (title, crime_type, charge_name, short_intro, overview, applied_law,
			    statutory_penalty_text, status)
			VALUES ('시험 사건', 'MURDER', '살인', '소개', '개요', '형법 제250조', '사형, 무기 또는 5년 이상의 징역', ?)
			RETURNING id""";
		caseId = jdbc.queryForObject(sql, Long.class, "PUBLISHED");
		draftCaseId = jdbc.queryForObject(sql, Long.class, "DRAFT");
		jdbc.update("""
			INSERT INTO penalty_rule (case_id, penalty_type, allowed_min, allowed_max, suspension_allowed, display_order)
			VALUES (?, 'PRISON', 60, 360, true, 1)""", caseId);
		AnonymousUser user = anonymousUserRepository.save(AnonymousUser.issue(Instant.now()));
		anonymousId = user.getId();
		experienceRepository.save(Experience.start(user, legalCaseRepository.getReferenceById(caseId)));
	}

	/** 자동 롤백되지 않는 테스트(NOT_SUPPORTED)를 위한 수동 정리. 일반 테스트는 클래스 레벨 @Transactional이 롤백한다 */
	@AfterEach
	void cleanCommittedFixtures() {
		if (TransactionSynchronizationManager.isActualTransactionActive()) {
			return;
		}
		jdbc.update("DELETE FROM judgment_factor WHERE judgment_id IN (SELECT id FROM judgment WHERE case_id IN (?, ?))",
			caseId, draftCaseId);
		jdbc.update("DELETE FROM judgment WHERE case_id IN (?, ?)", caseId, draftCaseId);
		jdbc.update("DELETE FROM experience WHERE case_id IN (?, ?)", caseId, draftCaseId);
		jdbc.update("DELETE FROM anonymous_user WHERE id = ?", anonymousId);
		jdbc.update("DELETE FROM penalty_rule WHERE case_id IN (?, ?)", caseId, draftCaseId);
		jdbc.update("DELETE FROM legal_case WHERE id IN (?, ?)", caseId, draftCaseId);
	}

	@Test
	@DisplayName("비공개 사건은 CASE_NOT_FOUND, 쿠키 없음 · 모르는 ID · 체험 없음은 EXPERIENCE_NOT_FOUND")
	void getMyExperience_notFound_throwsByOrder() {
		assertErrorCode(() -> myExperienceService.getMyExperience(draftCaseId, Optional.of(anonymousId)),
			ErrorCode.CASE_NOT_FOUND);
		assertErrorCode(() -> myExperienceService.getMyExperience(caseId, Optional.empty()),
			ErrorCode.EXPERIENCE_NOT_FOUND);
		assertErrorCode(() -> myExperienceService.getMyExperience(caseId, Optional.of(UUID.randomUUID())),
			ErrorCode.EXPERIENCE_NOT_FOUND);
		UUID otherUser = anonymousUserRepository.save(AnonymousUser.issue(Instant.now())).getId();
		assertErrorCode(() -> myExperienceService.getMyExperience(caseId, Optional.of(otherUser)),
			ErrorCode.EXPERIENCE_NOT_FOUND);
	}

	@Test
	@DisplayName("상태 조건을 만족하지 않으면 INVALID_STATE와 현재 상태를 돌려준다")
	void getMyExperienceAtLeast_beforeRequired_throwsWithCurrentStatus() {
		assertThat(myExperienceService.getMyExperience(caseId, Optional.of(anonymousId)).getStatus())
			.isEqualTo(ExperienceStatus.STARTED);

		assertThatThrownBy(() -> myExperienceService.getMyExperienceAtLeast(caseId, Optional.of(anonymousId),
			ExperienceStatus.VERDICT_CONFIRMED))
			.isInstanceOfSatisfying(InvalidExperienceStateException.class,
				e -> assertThat(e.getExperienceStatus()).isEqualTo(ExperienceStatus.STARTED));
		assertThatThrownBy(() -> myExperienceService.getMyExperienceBetween(caseId, Optional.of(anonymousId),
			ExperienceStatus.PRE_JUDGED, ExperienceStatus.REVIEWED))
			.isInstanceOf(InvalidExperienceStateException.class);
	}

	@Test
	@DisplayName("상태 이동은 조건부 갱신으로 DB에 반영된다")
	void apply_unchangedByOthers_updatesState() {
		Experience experience = myExperienceService.getMyExperience(caseId, Optional.of(anonymousId));
		Instant now = Instant.now();

		transitionService.apply(experience, e -> e.markPreJudged(now));
		transitionService.apply(experience, e -> e.confirmReviewStep(2, now));

		Experience reloaded = experienceRepository.findById(experience.getId()).orElseThrow();
		assertThat(reloaded.getStatus()).isEqualTo(ExperienceStatus.REVIEWING);
		assertThat(reloaded.getLastReviewedStep()).isEqualTo(2);
		assertThat(reloaded.getPreJudgedAt()).isNotNull();
	}

	@Test
	@DisplayName("다른 요청이 먼저 상태를 바꿨으면 반영하지 않고 INVALID_STATE (동시 사전 판단 제출)")
	void apply_changedByOtherRequest_throwsInvalidState() {
		Experience experience = myExperienceService.getMyExperience(caseId, Optional.of(anonymousId));
		// 같은 체험을 읽은 다른 요청이 먼저 사전 판단을 제출한 상황
		jdbc.update("UPDATE experience SET status = 'PRE_JUDGED', last_reviewed_step = 1 WHERE id = ?", experience.getId());

		assertThatThrownBy(() -> transitionService.apply(experience, e -> e.markPreJudged(Instant.now())))
			.isInstanceOfSatisfying(InvalidExperienceStateException.class,
				e -> assertThat(e.getExperienceStatus()).isEqualTo(ExperienceStatus.PRE_JUDGED));
	}

	@Test
	@DisplayName("섹션 확인(API 7) 동시 클릭: 다른 요청이 이미 그 섹션 이상으로 진행시켰으면 성공으로 보고, 실제 현재 값을 돌려준다 (리뷰 반영)")
	void applyIdempotent_reviewStepAlreadyReachedByOther_succeedsWithActualState() {
		Experience experience = myExperienceService.getMyExperience(caseId, Optional.of(anonymousId));
		Instant now = Instant.now();
		transitionService.apply(experience, e -> e.markPreJudged(now));			// STARTED -> PRE_JUDGED, step 1
		transitionService.apply(experience, e -> e.confirmReviewStep(2, now));	// -> REVIEWING, step 2
		// 두 요청이 동시에 step 3을 확인했고, 다른 요청(승자)이 step 4까지 먼저 끝낸 상황.
		// 패자의 체험 엔티티에는 "step 3"(이번 요청이 만들려던 값)이 들어 있어, 그걸 그대로 응답에 쓰면
		// 명세(API 7 "현재 상태를 그대로 돌려준다")와 다르게 step 3으로 잘못 응답하게 된다.
		jdbc.update("UPDATE experience SET status = 'REVIEWED', last_reviewed_step = 4 WHERE id = ?", experience.getId());

		var result = transitionService.applyIdempotent(experience, e -> e.confirmReviewStep(3, now));

		assertThat(result.changed()).isFalse();	// 실패가 아니라 "이미 그 지점" 성공으로 처리됨
		// 호출 쪽은 이 값으로 응답을 만들어야 한다. 엔티티의 getLastReviewedStep()(=3, 이번 요청이 만들려던 값)이 아니라
		// 실제 현재 값(승자가 반영한 4)이어야 한다.
		assertThat(result.status()).isEqualTo(ExperienceStatus.REVIEWED);
		assertThat(result.lastReviewedStep()).isEqualTo(4);
	}

	@Test
	@DisplayName("판단 저장 + 상태 전이를 한 번에 묶으면, 정상 제출은 둘 다 반영된다")
	void apply_withRelatedWrites_savesJudgmentAndTransitionsTogether() {
		Experience experience = myExperienceService.getMyExperience(caseId, Optional.of(anonymousId));
		jdbc.update("UPDATE experience SET status = 'REVIEWED', last_reviewed_step = 4 WHERE id = ?", experience.getId());
		entityManager.clear();
		Experience reloaded = myExperienceService.getMyExperience(caseId, Optional.of(anonymousId));

		transitionService.apply(reloaded,
			() -> judgmentRepository.saveAndFlush(Judgment.userFinal(reloaded, PenaltyType.PRISON, null, 120, null, null, null)),
			e -> e.markVerdictConfirmed(Instant.now()));

		assertThat(jdbc.queryForObject("SELECT count(*) FROM judgment WHERE experience_id = ?", Integer.class,
			reloaded.getId())).isEqualTo(1);
		assertThat(experienceRepository.findById(reloaded.getId()).orElseThrow().getStatus())
			.isEqualTo(ExperienceStatus.VERDICT_CONFIRMED);
	}

	@Test
	@Transactional(propagation = Propagation.NOT_SUPPORTED)
	@DisplayName("판단 저장 + 상태 전이를 한 번에 묶으면, 동시 중복 제출의 패자도 유니크 위반 없이 currentStatus 포함 INVALID_STATE를 받는다 (리뷰 2번 반영)")
	void apply_withRelatedWrites_duplicateSubmission_losesCleanlyWithCurrentStatus() {
		Experience experience = myExperienceService.getMyExperience(caseId, Optional.of(anonymousId));
		jdbc.update("UPDATE experience SET status = 'REVIEWED', last_reviewed_step = 4 WHERE id = ?", experience.getId());
		entityManager.clear();
		Experience reloaded = myExperienceService.getMyExperience(caseId, Optional.of(anonymousId));
		// 다른 요청(승자)이 이미 판단을 저장하고 상태까지 확정한 상황
		judgmentRepository.saveAndFlush(Judgment.userFinal(reloaded, PenaltyType.PRISON, null, 150, null, null, null));
		jdbc.update("UPDATE experience SET status = 'VERDICT_CONFIRMED' WHERE id = ?", reloaded.getId());

		// 패자: relatedWrites(판단 저장)에서 유니크 위반이 나도, apply 한 메서드가 알아서 깨끗하게 바꿔 던진다
		assertThatThrownBy(() -> transitionService.apply(reloaded,
			() -> judgmentRepository.saveAndFlush(Judgment.userFinal(reloaded, PenaltyType.PRISON, null, 120, null, null, null)),
			e -> e.markVerdictConfirmed(Instant.now())))
			.isInstanceOfSatisfying(InvalidExperienceStateException.class,
				e -> assertThat(e.getExperienceStatus()).isEqualTo(ExperienceStatus.VERDICT_CONFIRMED));

		// 승자의 판단 1건만 남아 있다 (패자의 판단은 저장되지 않음)
		assertThat(jdbc.queryForObject("SELECT count(*) FROM judgment WHERE experience_id = ?", Integer.class,
			reloaded.getId())).isEqualTo(1);
	}

	@Test
	@DisplayName("공개 요청은 다른 요청이 먼저 공개했어도 성공으로 본다 (멱등)")
	void applyIdempotent_alreadyRevealedByOther_succeeds() {
		Long experienceId = experienceRepository.findLatest(anonymousId, caseId).orElseThrow().getId();
		jdbc.update("UPDATE experience SET status = 'VERDICT_CONFIRMED', last_reviewed_step = 4 WHERE id = ?", experienceId);
		entityManager.clear();	// 새 요청처럼 DB에서 다시 읽는다
		Experience experience = myExperienceService.getMyExperience(caseId, Optional.of(anonymousId));
		jdbc.update("UPDATE experience SET status = 'AI_REVEALED' WHERE id = ?", experienceId);

		var result = transitionService.applyIdempotent(experience, e -> e.revealCourt(Instant.now()));

		assertThat(result.changed()).isFalse();
		assertThat(result.status()).isEqualTo(ExperienceStatus.AI_REVEALED);
		assertThat(experienceRepository.findStatusById(experienceId)).contains(ExperienceStatus.AI_REVEALED);
	}

	@Test
	@DisplayName("체험 시작용 조회는 쿠키가 없거나 모르는 ID면 새로 발급한다")
	void getOrIssue_unknownId_issuesNewUser() {
		assertThat(anonymousUserService.getOrIssue(Optional.of(anonymousId)).issued()).isFalse();
		var issued = anonymousUserService.getOrIssue(Optional.of(UUID.randomUUID()));
		assertThat(issued.issued()).isTrue();
		assertThat(anonymousUserRepository.existsById(issued.user().getId())).isTrue();
	}

	private void assertErrorCode(org.assertj.core.api.ThrowableAssert.ThrowingCallable call, ErrorCode expected) {
		assertThatThrownBy(call).isInstanceOfSatisfying(BusinessException.class,
			e -> assertThat(e.getErrorCode()).isEqualTo(expected));
	}
}
