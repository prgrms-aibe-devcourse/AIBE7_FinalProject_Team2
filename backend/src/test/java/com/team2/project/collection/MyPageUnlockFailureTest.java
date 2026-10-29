package com.team2.project.collection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.team2.project.profile.service.AchievementUnlockRecorder;
import com.team2.project.support.ApiIntegrationTest;
import jakarta.servlet.http.Cookie;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.CannotCreateTransactionException;

/**
 * 마이페이지(P-5)의 해금 기록은 부가 기능이다 (BE-83, PR #335 리뷰).
 * 별도 트랜잭션을 못 열거나(커넥션 풀 고갈) DB 오류가 나도 조회는 200으로 내려 주고, 판정은 계산값 그대로다
 */
class MyPageUnlockFailureTest extends ApiIntegrationTest {

	private static final String MY_PAGE = "/api/v1/my-page";

	@MockitoSpyBean
	private AchievementUnlockRecorder unlockRecorder;

	private Cookie completedUserCookie(UUID userId) {
		long caseId = insertCase("MURDER", "PUBLISHED");
		insertExperience(userId, caseId, 1, "COMPLETED");
		return new Cookie(COOKIE, userId.toString());
	}

	private int countUnlocks(UUID userId) {
		return jdbcTemplate.queryForObject("SELECT count(*) FROM achievement_unlock WHERE anonymous_user_id = ?",
			Integer.class, userId);
	}

	@Test
	@DisplayName("새 트랜잭션을 못 열어도(CannotCreateTransactionException) 마이페이지는 200, first-case는 계산값으로 CLEARED")
	void transactionFailureKeepsPage() throws Exception {
		UUID userId = UUID.randomUUID();
		Cookie cookie = completedUserCookie(userId);
		doThrow(new CannotCreateTransactionException("풀 고갈"))
			.when(unlockRecorder).recordSeparately(any(), any());

		mockMvc.perform(get(MY_PAGE).cookie(cookie))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.achievements[0].id").value("first-case"))
			.andExpect(jsonPath("$.achievements[0].status").value("CLEARED"))
			.andExpect(jsonPath("$.achievements[0].unlockedAt").isEmpty());	// 기록 전이라 시각 없음
		assertThat(countUnlocks(userId)).isZero();
	}

	@Test
	@DisplayName("DB 오류(DataAccessException)여도 마이페이지는 200")
	void dataAccessFailureKeepsPage() throws Exception {
		UUID userId = UUID.randomUUID();
		Cookie cookie = completedUserCookie(userId);
		doThrow(new DataAccessResourceFailureException("DB 오류"))
			.when(unlockRecorder).recordSeparately(any(), any());

		mockMvc.perform(get(MY_PAGE).cookie(cookie))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.achievements[0].status").value("CLEARED"));
	}
}
