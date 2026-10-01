package com.team2.project.experience;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

import com.team2.project.experience.repository.ExperienceRepository;
import com.team2.project.support.ApiIntegrationTest;

/**
 * 상태 조건부 갱신 SQL을 직접 검증한다. API 수준의 동시성 테스트로는 이 조건을 잡지 못한다:
 * 같은 사용자의 요청은 anonymous_user 행 잠금(last_seen_at 갱신)에서 먼저 직렬화되어, 패자가 상태 확인 단계에서
 * 이미 걸러지기 때문이다. 조건부 갱신은 그 앞단이 바뀌어도 이중 전이를 막는 마지막 방어선이다.
 */
class ExperienceRepositoryTest extends ApiIntegrationTest {

	@Autowired
	private ExperienceRepository experienceRepository;

	@Autowired
	private TransactionTemplate transactionTemplate;

	@Test
	void advanceToPreJudged_fromStarted_updatesOnceAndSecondCallUpdatesNothing() throws Exception {
		long caseId = insertCase("MURDER", "PUBLISHED");
		startAndGetCookie(caseId);
		long experienceId = experienceId(caseId);

		int first = advance(experienceId);
		int second = advance(experienceId);

		assertThat(first).isEqualTo(1);
		assertThat(second).isZero();
		assertThat(jdbcTemplate.queryForMap(
				"SELECT status, last_reviewed_step, pre_judged_at IS NOT NULL AS has_time FROM experience WHERE id = ?",
				experienceId))
				.containsEntry("status", "PRE_JUDGED").containsEntry("last_reviewed_step", 1)
				.containsEntry("has_time", true);
	}

	@Test
	void advanceToPreJudged_fromOtherStatus_updatesNothing() {
		long caseId = insertCase("MURDER", "PUBLISHED");
		insertExperience(UUID.randomUUID(), caseId, 1, "REVIEWED");

		int updated = advance(experienceId(caseId));

		assertThat(updated).isZero();
		assertThat(jdbcTemplate.queryForObject("SELECT status FROM experience WHERE id = ?", String.class,
				experienceId(caseId))).isEqualTo("REVIEWED");
	}

	private int advance(long experienceId) {
		return transactionTemplate.execute(status -> experienceRepository.advanceToPreJudged(experienceId, Instant.now()));
	}

	private long experienceId(long caseId) {
		return jdbcTemplate.queryForObject("SELECT id FROM experience WHERE case_id = ?", Long.class, caseId);
	}
}
