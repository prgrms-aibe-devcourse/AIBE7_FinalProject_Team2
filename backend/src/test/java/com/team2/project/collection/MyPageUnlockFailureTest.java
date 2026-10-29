package com.team2.project.collection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.team2.project.profile.service.AchievementUnlockRecorder;
import com.team2.project.common.metrics.CaseImportMetrics;
import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.HikariPoolMXBean;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import com.team2.project.support.ApiIntegrationTest;
import jakarta.servlet.http.Cookie;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
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

	/** 60초 지표 작업(@Scheduled)이 연결을 잠깐 써 연결 수 검사가 흔들리지 않게, 이 클래스에서는 빈 동작으로 둔다 */
	@MockitoBean
	private CaseImportMetrics caseImportMetrics;

	@Autowired
	private DataSource dataSource;

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
	@DisplayName("해금 기록은 조회 트랜잭션 밖에서 열린다 — 연결을 잡은 채 두 번째 연결을 기다리지 않는다 (PR #335 리뷰)")
	void unlockIsRecordedOutsideReadTransaction() throws Exception {
		UUID userId = UUID.randomUUID();
		Cookie cookie = completedUserCookie(userId);
		HikariPoolMXBean pool = dataSource.unwrap(HikariDataSource.class).getHikariPoolMXBean();
		AtomicInteger activeWhileRecording = new AtomicInteger(-1);
		// 기록 트랜잭션 안에서 사용 중인 연결 수: 조회 트랜잭션이 연결을 잡은 채면 2(조회 + 기록), 밖에서 열면 1(기록만).
		// (기록 Bean을 감싼 지점은 트랜잭션 프록시 안쪽이라 "트랜잭션이 있는지"로는 둘을 구분할 수 없다)
		doAnswer(invocation -> {
			activeWhileRecording.set(pool.getActiveConnections());
			return invocation.callRealMethod();
		}).when(unlockRecorder).recordSeparately(any(), any());

		mockMvc.perform(get(MY_PAGE).cookie(cookie))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.achievements[0].id").value("first-case"))
			.andExpect(jsonPath("$.achievements[0].unlockedAt").isNotEmpty());	// 기록한 해금이 같은 응답에 반영

		assertThat(activeWhileRecording.get()).as("해금 기록 중 사용 중인 연결 수").isEqualTo(1);
		assertThat(countUnlocks(userId)).isPositive();
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
