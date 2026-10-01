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
import com.team2.project.legalcase.repository.LegalCaseRepository;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
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
