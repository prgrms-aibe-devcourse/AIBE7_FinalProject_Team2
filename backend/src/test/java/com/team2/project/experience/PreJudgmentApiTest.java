package com.team2.project.experience;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import jakarta.servlet.http.Cookie;

import com.team2.project.support.ApiIntegrationTest;

class PreJudgmentApiTest extends ApiIntegrationTest {

	// ---------- API 4. 사건 개요 · 사전 판단 선택지 ----------

	@Test
	void getOverview_returnsCaseAndRangeOptionsOfItsCrimeType() throws Exception {
		long caseId = insertCase("MURDER", "PUBLISHED");
		Cookie cookie = startAndGetCookie(caseId);
		List<Long> murderOptions = rangeOptionIds("MURDER");

		mockMvc.perform(get(overviewUrl(caseId)).cookie(cookie))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.case.caseId").value(caseId))
				.andExpect(jsonPath("$.case.crimeType").value("MURDER"))
				.andExpect(jsonPath("$.case.crimeCategoryLabel").value("생명범죄"))
				.andExpect(jsonPath("$.case.chargeName").value("테스트죄"))
				.andExpect(jsonPath("$.case.overview").value("테스트 개요"))
				.andExpect(jsonPath("$.rangeOptions.length()").value(murderOptions.size()))
				.andExpect(jsonPath("$.rangeOptions[0].rangeOptionId").value(murderOptions.get(0)))
				.andExpect(jsonPath("$.rangeOptions[?(@.label == '무기징역')]").isNotEmpty())
				.andExpect(jsonPath("$.rangeOptions[?(@.label == '사형')]").isNotEmpty())
				// 살인은 법정형에 벌금이 없어 벌금형 구간이 없다
				.andExpect(jsonPath("$.rangeOptions[?(@.label == '벌금형')]").isEmpty());
	}

	@Test
	void getOverview_rangeOptionsDifferByCrimeType() throws Exception {
		long caseId = insertCase("FRAUD", "PUBLISHED");
		Cookie cookie = startAndGetCookie(caseId);

		mockMvc.perform(get(overviewUrl(caseId)).cookie(cookie))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.rangeOptions.length()").value(rangeOptionIds("FRAUD").size()))
				.andExpect(jsonPath("$.rangeOptions[?(@.label == '벌금형')]").isNotEmpty())
				.andExpect(jsonPath("$.rangeOptions[?(@.label == '사형')]").isEmpty());
	}

	@Test
	void getOverview_doesNotExposeLawOrPenaltyInformation() throws Exception {
		long caseId = insertCase("MURDER", "PUBLISHED");
		Cookie cookie = startAndGetCookie(caseId);

		// 사건 정보는 6개 필드로 고정: 법정형 · 적용 법조문 · 권고 범위 · 실제 판결이 새어 나가면 이 검사가 깨진다 (FR-2-8)
		mockMvc.perform(get(overviewUrl(caseId)).cookie(cookie))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(3))
				.andExpect(jsonPath("$.case.length()").value(6))
				.andExpect(jsonPath("$.case.appliedLaw").doesNotExist())
				.andExpect(jsonPath("$.case.statutoryPenaltyText").doesNotExist())
				.andExpect(jsonPath("$.case.recommendedMinMonths").doesNotExist());
	}

	@Test
	void getOverview_preFactors_onlyOverviewStageWithPreLabel() throws Exception {
		long caseId = insertCase("MURDER", "PUBLISHED");
		long second = insertFactor(caseId, "OVERVIEW", "두 번째 요소 긴 문구", "두 번째 짧은 문구", 2);
		long first = insertFactor(caseId, "OVERVIEW", "첫 번째 요소 긴 문구", "첫 번째 짧은 문구", 1);
		insertFactor(caseId, "DETAIL", "상세 단계 요소", null, 3);
		insertFactor(caseId, "ARGUMENT", "주장 단계 요소", null, 4);
		Cookie cookie = startAndGetCookie(caseId);

		mockMvc.perform(get(overviewUrl(caseId)).cookie(cookie))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.preFactors.length()").value(2))
				.andExpect(jsonPath("$.preFactors[0].factorId").value(first))
				.andExpect(jsonPath("$.preFactors[0].label").value("첫 번째 짧은 문구"))
				.andExpect(jsonPath("$.preFactors[1].factorId").value(second));
	}

	@Test
	void getOverview_afterPreJudgment_returnsInvalidStateWithCurrentStatus() throws Exception {
		long caseId = insertCase("MURDER", "PUBLISHED");
		Cookie cookie = startAndGetCookie(caseId);
		submit(caseId, cookie, "{\"rangeOptionId\": %d}".formatted(rangeOptionIds("MURDER").get(0)))
				.andExpect(status().isOk());

		mockMvc.perform(get(overviewUrl(caseId)).cookie(cookie))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("INVALID_STATE"))
				.andExpect(jsonPath("$.currentStatus").value("PRE_JUDGED"));
	}

	@Test
	void getOverview_noCookie_returnsExperienceNotFound() throws Exception {
		long caseId = insertCase("MURDER", "PUBLISHED");

		mockMvc.perform(get(overviewUrl(caseId)))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("EXPERIENCE_NOT_FOUND"));
	}

	@Test
	void getOverview_unpublishedCase_returnsCaseNotFound() throws Exception {
		long caseId = insertCase("MURDER", "DRAFT");

		mockMvc.perform(get(overviewUrl(caseId)))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("CASE_NOT_FOUND"));
	}

	// ---------- API 5. 사전 판단 제출 ----------

	@Test
	void submit_success_movesToPreJudgedAndStoresJudgment() throws Exception {
		long caseId = insertCase("MURDER", "PUBLISHED");
		Cookie cookie = startAndGetCookie(caseId);
		long rangeOptionId = rangeOptionIds("MURDER").get(3);

		submit(caseId, cookie, "{\"rangeOptionId\": %d, \"factorIds\": []}".formatted(rangeOptionId))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("PRE_JUDGED"))
				.andExpect(jsonPath("$.lastReviewedStep").value(1))
				// 사전 판단 내용은 되돌려주지 않는다 (S-09 전까지 다시 보여 주지 않음)
				.andExpect(jsonPath("$.length()").value(2))
				.andExpect(jsonPath("$.rangeOptionId").doesNotExist())
				.andExpect(jsonPath("$.factorIds").doesNotExist());

		assertThat(experienceStatus(caseId)).isEqualTo("PRE_JUDGED");
		assertThat(jdbcTemplate.queryForObject(
				"SELECT last_reviewed_step FROM experience WHERE case_id = ?", Integer.class, caseId)).isEqualTo(1);
		assertThat(jdbcTemplate.queryForObject(
				"SELECT pre_judged_at IS NOT NULL FROM experience WHERE case_id = ?", Boolean.class, caseId)).isTrue();

		var judgment = jdbcTemplate.queryForMap(
				"SELECT subject_type, timing, range_option_id, is_published, experience_id IS NOT NULL AS has_experience,"
						+ " penalty_type, prison_months FROM judgment WHERE case_id = ?", caseId);
		assertThat(judgment).containsEntry("subject_type", "USER").containsEntry("timing", "PRE")
				.containsEntry("range_option_id", rangeOptionId).containsEntry("is_published", true)
				.containsEntry("has_experience", true).containsEntry("penalty_type", null)
				.containsEntry("prison_months", null);
		assertThat(countJudgmentFactors(caseId)).isZero();
	}

	@Test
	void submit_factorIdsOmitted_isAccepted() throws Exception {
		long caseId = insertCase("MURDER", "PUBLISHED");
		Cookie cookie = startAndGetCookie(caseId);

		submit(caseId, cookie, "{\"rangeOptionId\": %d}".formatted(rangeOptionIds("MURDER").get(0)))
				.andExpect(status().isOk());

		assertThat(countJudgments(caseId)).isEqualTo(1);
	}

	@Test
	void submit_withFactors_storesFactorsWithoutDirection() throws Exception {
		long caseId = insertCase("MURDER", "PUBLISHED");
		long first = insertFactor(caseId, "OVERVIEW", "요소1", "요소1", 1);
		long second = insertFactor(caseId, "OVERVIEW", "요소2", "요소2", 2);
		Cookie cookie = startAndGetCookie(caseId);

		submit(caseId, cookie, "{\"rangeOptionId\": %d, \"factorIds\": [%d, %d]}"
				.formatted(rangeOptionIds("MURDER").get(0), first, second)).andExpect(status().isOk());

		List<Long> stored = jdbcTemplate.queryForList("""
				SELECT jf.factor_id FROM judgment_factor jf JOIN judgment j ON j.id = jf.judgment_id
				WHERE j.case_id = ? AND jf.direction IS NULL ORDER BY jf.factor_id
				""", Long.class, caseId);
		assertThat(stored).containsExactly(first, second);
	}

	@Test
	void submit_twice_secondReturnsInvalidStateAndKeepsOneJudgment() throws Exception {
		long caseId = insertCase("MURDER", "PUBLISHED");
		Cookie cookie = startAndGetCookie(caseId);
		String body = "{\"rangeOptionId\": %d}".formatted(rangeOptionIds("MURDER").get(0));
		submit(caseId, cookie, body).andExpect(status().isOk());

		submit(caseId, cookie, body)
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("INVALID_STATE"))
				.andExpect(jsonPath("$.currentStatus").value("PRE_JUDGED"));

		assertThat(countJudgments(caseId)).isEqualTo(1);
	}

	@Test
	void submit_experienceAlreadyFurtherAlong_returnsInvalidStateBeforeValidatingBody() throws Exception {
		long caseId = insertCase("MURDER", "PUBLISHED");
		UUID userId = UUID.randomUUID();
		insertExperience(userId, caseId, 1, "REVIEWED");

		// 잘못된 구간이어도 상태 검사가 먼저다 (409 > 422)
		submit(caseId, new Cookie(COOKIE, userId.toString()), "{\"rangeOptionId\": -1}")
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("INVALID_STATE"))
				.andExpect(jsonPath("$.currentStatus").value("REVIEWED"));
	}

	@Test
	void submit_rangeOptionOfOtherCrimeType_returnsInvalidRangeOptionAndChangesNothing() throws Exception {
		long caseId = insertCase("MURDER", "PUBLISHED");
		Cookie cookie = startAndGetCookie(caseId);
		// 벌금형은 사기 · 상해 구간이고 살인에는 없다
		long fraudFine = jdbcTemplate.queryForObject(
				"SELECT id FROM sentence_range_option WHERE crime_type = 'FRAUD' AND label = '벌금형'", Long.class);

		submit(caseId, cookie, "{\"rangeOptionId\": %d}".formatted(fraudFine))
				.andExpect(status().isUnprocessableContent())
				.andExpect(jsonPath("$.code").value("INVALID_RANGE_OPTION"));

		assertNothingSaved(caseId);
	}

	@Test
	void submit_unknownRangeOption_returnsInvalidRangeOption() throws Exception {
		long caseId = insertCase("MURDER", "PUBLISHED");
		Cookie cookie = startAndGetCookie(caseId);

		submit(caseId, cookie, "{\"rangeOptionId\": 999999999}")
				.andExpect(status().isUnprocessableContent())
				.andExpect(jsonPath("$.code").value("INVALID_RANGE_OPTION"));
		assertNothingSaved(caseId);
	}

	@Test
	void submit_missingOrMalformedBody_returnsValidationError() throws Exception {
		long caseId = insertCase("MURDER", "PUBLISHED");
		Cookie cookie = startAndGetCookie(caseId);

		for (String body : List.of("{}", "{\"rangeOptionId\": null}", "{\"rangeOptionId\": \"abc\"}", "not json",
				"{\"rangeOptionId\": %d, \"factorIds\": [null]}".formatted(rangeOptionIds("MURDER").get(0)))) {
			submit(caseId, cookie, body)
					.andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
		}
		assertNothingSaved(caseId);
	}

	@Test
	void submit_invalidFactors_returnsInvalidFactorAndChangesNothing() throws Exception {
		long caseId = insertCase("MURDER", "PUBLISHED");
		long overview = insertFactor(caseId, "OVERVIEW", "개요 요소", "개요 요소", 1);
		long detail = insertFactor(caseId, "DETAIL", "상세 요소", null, 2);
		long otherCase = insertCase("MURDER", "PUBLISHED");
		long otherCaseOverview = insertFactor(otherCase, "OVERVIEW", "남의 사건 요소", "남의 사건 요소", 1);
		Cookie cookie = startAndGetCookie(caseId);
		long range = rangeOptionIds("MURDER").get(0);

		List<String> invalidFactorIds = List.of(
				String.valueOf(detail),                     // OVERVIEW가 아닌 요소
				String.valueOf(otherCaseOverview),          // 다른 사건의 요소
				"999999999",                                // 없는 요소
				overview + ", " + overview,                 // 중복
				overview + ", " + otherCaseOverview);       // 하나라도 잘못되면 거절
		for (String factorIds : invalidFactorIds) {
			submit(caseId, cookie, "{\"rangeOptionId\": %d, \"factorIds\": [%s]}".formatted(range, factorIds))
					.andExpect(status().isUnprocessableContent())
					.andExpect(jsonPath("$.code").value("INVALID_FACTOR"));
		}
		assertNothingSaved(caseId);
	}

	@Test
	void submit_moreThanTwoFactors_returnsTooManyFactors() throws Exception {
		long caseId = insertCase("MURDER", "PUBLISHED");
		long a = insertFactor(caseId, "OVERVIEW", "a", "a", 1);
		long b = insertFactor(caseId, "OVERVIEW", "b", "b", 2);
		long c = insertFactor(caseId, "OVERVIEW", "c", "c", 3);
		Cookie cookie = startAndGetCookie(caseId);

		submit(caseId, cookie, "{\"rangeOptionId\": %d, \"factorIds\": [%d, %d, %d]}"
				.formatted(rangeOptionIds("MURDER").get(0), a, b, c))
				.andExpect(status().isUnprocessableContent())
				.andExpect(jsonPath("$.code").value("TOO_MANY_FACTORS"));
		assertNothingSaved(caseId);
	}

	@Test
	void submit_noCookie_returnsExperienceNotFound() throws Exception {
		long caseId = insertCase("MURDER", "PUBLISHED");

		mockMvc.perform(post(preJudgmentUrl(caseId)).contentType(MediaType.APPLICATION_JSON)
				.content("{\"rangeOptionId\": %d}".formatted(rangeOptionIds("MURDER").get(0))))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("EXPERIENCE_NOT_FOUND"));
	}

	@Test
	void submit_otherUsersCookie_returnsExperienceNotFound() throws Exception {
		long caseId = insertCase("MURDER", "PUBLISHED");
		startAndGetCookie(caseId);
		Cookie stranger = startAndGetCookie(insertCase("FRAUD", "PUBLISHED"));

		submit(caseId, stranger, "{\"rangeOptionId\": %d}".formatted(rangeOptionIds("MURDER").get(0)))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("EXPERIENCE_NOT_FOUND"));
	}

	@Test
	void submit_unpublishedCase_returnsCaseNotFound() throws Exception {
		long caseId = insertCase("MURDER", "DRAFT");

		submit(caseId, new Cookie(COOKIE, UUID.randomUUID().toString()), "{\"rangeOptionId\": 1}")
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("CASE_NOT_FOUND"));
	}

	/**
	 * 종단 동작 확인용. 같은 사용자의 동시 요청은 anonymous_user 행 잠금에서 먼저 직렬화되므로
	 * 조건부 갱신 SQL 자체는 ExperienceRepositoryTest가 따로 검증한다.
	 */
	@Test
	void submit_concurrentRequests_onlyOneSucceedsAndOneJudgmentSaved() throws Exception {
		long caseId = insertCase("MURDER", "PUBLISHED");
		Cookie cookie = startAndGetCookie(caseId);
		String body = "{\"rangeOptionId\": %d}".formatted(rangeOptionIds("MURDER").get(0));

		int threads = 8;
		CountDownLatch ready = new CountDownLatch(threads);
		CountDownLatch go = new CountDownLatch(1);
		ExecutorService pool = Executors.newFixedThreadPool(threads);
		try {
			Callable<Integer> request = () -> {
				ready.countDown();
				go.await();
				return submit(caseId, cookie, body).andReturn().getResponse().getStatus();
			};
			List<Future<Integer>> futures = IntStream.range(0, threads).mapToObj(i -> pool.submit(request)).toList();
			ready.await();
			go.countDown();

			int succeeded = 0;
			for (Future<Integer> future : futures) {
				int httpStatus = future.get();
				assertThat(httpStatus).isIn(200, 409);
				if (httpStatus == 200) {
					succeeded++;
				}
			}
			assertThat(succeeded).isEqualTo(1);
		} finally {
			pool.shutdownNow();
		}
		assertThat(countJudgments(caseId)).isEqualTo(1);
		assertThat(experienceStatus(caseId)).isEqualTo("PRE_JUDGED");
	}

	// ---------- 헬퍼 ----------

	private ResultActions submit(long caseId, Cookie cookie, String body) throws Exception {
		return mockMvc.perform(post(preJudgmentUrl(caseId)).cookie(cookie)
				.contentType(MediaType.APPLICATION_JSON).content(body));
	}

	/** 422 · 400으로 거절되면 상태도, 판단도, 판단 요소도 바뀌지 않아야 한다. */
	private void assertNothingSaved(long caseId) {
		assertThat(experienceStatus(caseId)).isEqualTo("STARTED");
		assertThat(countJudgments(caseId)).isZero();
		assertThat(countJudgmentFactors(caseId)).isZero();
	}

	private String experienceStatus(long caseId) {
		return jdbcTemplate.queryForObject("SELECT status FROM experience WHERE case_id = ?", String.class, caseId);
	}

	private int countJudgments(long caseId) {
		return jdbcTemplate.queryForObject("SELECT count(*) FROM judgment WHERE case_id = ?", Integer.class, caseId);
	}

	private int countJudgmentFactors(long caseId) {
		return jdbcTemplate.queryForObject("""
				SELECT count(*) FROM judgment_factor jf JOIN judgment j ON j.id = jf.judgment_id WHERE j.case_id = ?
				""", Integer.class, caseId);
	}

	private static String overviewUrl(long caseId) {
		return experienceUrl(caseId) + "/overview";
	}

	private static String preJudgmentUrl(long caseId) {
		return experienceUrl(caseId) + "/pre-judgment";
	}
}
