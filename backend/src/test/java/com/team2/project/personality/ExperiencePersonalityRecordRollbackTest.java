package com.team2.project.personality;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.team2.project.personality.domain.ExperiencePersonality;
import com.team2.project.personality.repository.ExperiencePersonalityRepository;
import com.team2.project.support.ApiIntegrationTest;
import jakarta.servlet.http.Cookie;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

/**
 * 성향 기여 저장이 실패하면 COMPLETED 전이도 함께 롤백되는지 (BE-64, 성향 산식 3-4 "같은 트랜잭션")
 * 스파이 빈 때문에 스프링 컨텍스트가 따로 뜨므로 다른 테스트와 클래스를 나눴다 (컨텍스트 캐시를 깨지 않게)
 */
class ExperiencePersonalityRecordRollbackTest extends ApiIntegrationTest {

	// 기록기(MANDATORY 트랜잭션 프록시)를 직접 스텁하면 스텁 설정 호출 자체가 트랜잭션 밖이라 실패한다.
	// 그래서 기록기 안에서 마지막에 부르는 저장소를 실패시킨다
	@MockitoSpyBean
	private ExperiencePersonalityRepository repository;

	@Test
	void revealComparison_whenRecordFails_staysAiRevealed() throws Exception {
		UUID anonymousId = UUID.randomUUID();
		long caseId = insertCase("MURDER", "PUBLISHED");
		for (String subjectType : new String[] {"AI", "COURT"}) {
			jdbcTemplate.update("""
					INSERT INTO judgment (case_id, subject_type, timing, penalty_type, prison_months, summary, reasoning, is_published)
					VALUES (?, ?, 'FINAL', 'PRISON', 120, '한 줄 요약', '판결 이유', true)
					""", caseId, subjectType);
		}
		insertExperience(anonymousId, caseId, 1, "AI_REVEALED");
		long experienceId = jdbcTemplate.queryForObject(
			"SELECT id FROM experience WHERE case_id = ? AND anonymous_user_id = ?", Long.class, caseId, anonymousId);
		doThrow(new IllegalStateException("저장 실패 재현")).when(repository).save(any(ExperiencePersonality.class));

		mockMvc.perform(post(experienceUrl(caseId) + "/comparison-reveal").cookie(new Cookie(COOKIE, anonymousId.toString())))
			.andExpect(status().isInternalServerError());

		// 상태가 COMPLETED로 남으면 "완료했는데 성향 기여가 없는" 체험이 되어 다시 공개해도 저장할 기회가 없다
		assertThat(jdbcTemplate.queryForObject("SELECT status FROM experience WHERE id = ?", String.class, experienceId))
			.isEqualTo("AI_REVEALED");
		assertThat(jdbcTemplate.queryForObject(
			"SELECT count(*) FROM experience_personality WHERE experience_id = ?", Integer.class, experienceId)).isZero();
	}
}
