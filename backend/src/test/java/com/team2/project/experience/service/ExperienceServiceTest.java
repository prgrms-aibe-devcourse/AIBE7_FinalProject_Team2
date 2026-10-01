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
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

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
	@DisplayName("섹션 확인(API 7) 동시 클릭: 다른 요청이 이미 그 섹션 이상으로 진행시켰으면 성공으로 본다 (리뷰 반영)")
	void applyIdempotent_reviewStepAlreadyReachedByOther_succeeds() {
		Experience experience = myExperienceService.getMyExperience(caseId, Optional.of(anonymousId));
		Instant now = Instant.now();
		transitionService.apply(experience, e -> e.markPreJudged(now));			// STARTED -> PRE_JUDGED, step 1
		transitionService.apply(experience, e -> e.confirmReviewStep(2, now));	// -> REVIEWING, step 2
		// 두 요청이 동시에 step 3을 확인했고, 다른 요청(승자)이 먼저 반영된 상황.
		// status는 그대로(REVIEWING)인 채 lastReviewedStep만 앞서 있어서, status만 비교하면 "바뀌지 않았다"고 오판하기 쉽다.
		jdbc.update("UPDATE experience SET last_reviewed_step = 3 WHERE id = ?", experience.getId());

		boolean changed = transitionService.applyIdempotent(experience, e -> e.confirmReviewStep(3, now));

		assertThat(changed).isFalse();	// 실패가 아니라 "이미 그 지점" 성공으로 처리됨
		Experience reloaded = experienceRepository.findById(experience.getId()).orElseThrow();
		assertThat(reloaded.getStatus()).isEqualTo(ExperienceStatus.REVIEWING);
		assertThat(reloaded.getLastReviewedStep()).isEqualTo(3);	// 승자가 반영한 값 그대로, 패자가 덮어쓰지 않음
	}

	@Test
	@DisplayName("권장 순서(상태 전이 먼저, 판단 저장은 그 다음)면 동시 판결 확정의 패자가 유니크 위반 없이 깨끗하게 거절된다 (리뷰 반영)")
	void apply_beforeSavingJudgment_losesCleanlyWithoutUniqueViolation() {
		Experience experience = myExperienceService.getMyExperience(caseId, Optional.of(anonymousId));
		jdbc.update("UPDATE experience SET status = 'REVIEWED', last_reviewed_step = 4 WHERE id = ?", experience.getId());
		entityManager.clear();
		Experience reloaded = myExperienceService.getMyExperience(caseId, Optional.of(anonymousId));
		// 다른 요청이 먼저 판결을 확정한 상황
		jdbc.update("UPDATE experience SET status = 'VERDICT_CONFIRMED' WHERE id = ?", reloaded.getId());

		// 권장 순서: 판단을 저장하기 전에 상태 전이부터 시도한다
		assertThatThrownBy(() -> transitionService.apply(reloaded, e -> e.markVerdictConfirmed(Instant.now())))
			.isInstanceOfSatisfying(InvalidExperienceStateException.class,
				e -> assertThat(e.getExperienceStatus()).isEqualTo(ExperienceStatus.VERDICT_CONFIRMED));

		// 패자는 여기서 멈추므로 judgment를 저장하지 않는다 → 유니크 제약 위반 자체가 발생하지 않는다
		assertThat(jdbc.queryForObject("SELECT count(*) FROM judgment WHERE experience_id = ?", Integer.class,
			reloaded.getId())).isZero();
	}

	@Test
	@DisplayName("반대 순서(판단 저장을 먼저)면 패자가 currentStatus 없는 날 DataIntegrityViolationException으로 실패한다 (문제 재현)")
	void apply_afterSavingJudgment_losesWithRawUniqueViolationInstead() {
		Experience experience = myExperienceService.getMyExperience(caseId, Optional.of(anonymousId));
		jdbc.update("UPDATE experience SET status = 'REVIEWED', last_reviewed_step = 4 WHERE id = ?", experience.getId());
		entityManager.clear();
		Experience reloaded = myExperienceService.getMyExperience(caseId, Optional.of(anonymousId));
		Long experienceId = reloaded.getId();
		judgmentRepository.saveAndFlush(Judgment.userFinal(reloaded, PenaltyType.PRISON, null, 120, null, null, null));
		// 다른 요청(승자)이 이미 FINAL 판단을 저장한 상황. 패자가 잘못된 순서로 판단을 먼저 저장하면 유니크 제약에 걸린다

		assertThatThrownBy(() -> judgmentRepository.saveAndFlush(
			Judgment.userFinal(experienceRepository.findById(experienceId).orElseThrow(),
				PenaltyType.PRISON, null, 150, null, null, null)))
			.isInstanceOfSatisfying(DataIntegrityViolationException.class, e -> {
				var cause = e.getCause();
				assertThat(cause).isInstanceOf(ConstraintViolationException.class);
				assertThat(((ConstraintViolationException) cause).getConstraintName())
					.isEqualTo("uk_judgment_experience_timing");
			});
		// 이 경로로 오면 ApiExceptionAdvice.handleDataIntegrity가 currentStatus 없는 일반 INVALID_STATE로 응답한다.
		// (서비스가 직접 잡아 InvalidExperienceStateException으로 바꾸지 않는 한) — 그래서 호출 순서를 뒤집어 피한다.
	}

	@Test
	@DisplayName("공개 요청은 다른 요청이 먼저 공개했어도 성공으로 본다 (멱등)")
	void applyIdempotent_alreadyRevealedByOther_succeeds() {
		Long experienceId = experienceRepository.findLatest(anonymousId, caseId).orElseThrow().getId();
		jdbc.update("UPDATE experience SET status = 'VERDICT_CONFIRMED', last_reviewed_step = 4 WHERE id = ?", experienceId);
		entityManager.clear();	// 새 요청처럼 DB에서 다시 읽는다
		Experience experience = myExperienceService.getMyExperience(caseId, Optional.of(anonymousId));
		jdbc.update("UPDATE experience SET status = 'AI_REVEALED' WHERE id = ?", experienceId);

		boolean changed = transitionService.applyIdempotent(experience, e -> e.revealCourt(Instant.now()));

		assertThat(changed).isFalse();
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
