package com.team2.project.personality;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.lessThan;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.team2.project.support.ApiIntegrationTest;
import com.team2.project.support.FakeMemberTokens;
import jakarta.servlet.http.Cookie;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * 판결 성향 API (API 명세 9장 P-1 ~ P-5, BE-72). 문항 · 유형은 콘텐츠 시드(R__personality_content)를 그대로 쓴다.
 * 응답에 4글자 코드 · 축 글자가 섞이지 않는지(#119)도 함께 본다.
 */
@Import(FakeMemberTokens.class)
class PersonalityApiTest extends ApiIntegrationTest {

	private static final String RESULTS = "/api/v1/personality/results";
	private static final String MY_RESULT = "/api/v1/personality/results/me";
	private static final String MY_PAGE = "/api/v1/my-page";
	private static final List<String> CODES = List.of("ESTJ", "ISTJ", "ESTP", "ENTJ", "ESFJ", "ISFJ", "INTJ", "ISTP",
		"ENTP", "ESFP", "ENFJ", "INFJ", "INTP", "ISFP", "ENFP", "INFP");

	private final Set<UUID> users = new HashSet<>();

	@AfterEach
	void deletePersonalityData() {
		// 부모의 정리(체험 · 사용자 삭제)보다 먼저 돈다. 성향 기록을 지우고, 체험이 없는 사용자(P-2로만 생긴 사용자)는 여기서 지운다
		for (UUID user : users) {
			jdbcTemplate.update("""
				DELETE FROM experience_personality
				WHERE experience_id IN (SELECT id FROM experience WHERE anonymous_user_id = ?)""", user);
			jdbcTemplate.update("DELETE FROM personality_result WHERE anonymous_user_id = ?", user);
			jdbcTemplate.update("DELETE FROM attendance WHERE anonymous_user_id = ?", user);	// P-2 발급 시 출석(BE-98)
			jdbcTemplate.update("""
				DELETE FROM anonymous_user a WHERE a.id = ?
				AND NOT EXISTS (SELECT 1 FROM experience e WHERE e.anonymous_user_id = a.id)""", user);
		}
		users.clear();
	}

	@Test
	@DisplayName("P-1: 문항 16개를 출제 순서대로, 상황 · 두 행동 문장만 준다 (축 · 글자 · 점수 없음)")
	void questions_listedWithoutAxisOrLetters() throws Exception {
		String body = mockMvc.perform(get("/api/v1/personality/questions"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.questions.length()").value(16))
			.andExpect(jsonPath("$.questions[0].scenario").value("FRIEND"))
			.andExpect(jsonPath("$.questions[0].content").value(startsWith("약속을 세 번이나")))
			.andExpect(jsonPath("$.questions[0].optionA").exists())
			.andExpect(jsonPath("$.questions[0].optionB").exists())
			.andReturn().getResponse().getContentAsString();
		assertThat(body).doesNotContain("\"axis\"", "\"letter\"", "\"score\"", "APOLOGY_SINCERITY");
	}

	@Test
	@DisplayName("P-2: 쿠키 없이 보통만 고르면 201 · 쿠키 발급 · 모든 축 동점이라 관대 유형(그럴 수도 있지형), 응답에 코드 없음")
	void submit_allNeutral_issuesCookieAndReturnsLenientType() throws Exception {
		MockHttpServletResponse response = mockMvc.perform(post(RESULTS).contentType(MediaType.APPLICATION_JSON)
				.content(answersJson(questionIds(), 2)))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.type.name").value("그럴 수도 있지형"))
			.andExpect(jsonPath("$.type.character").value("NAERO"))
			.andExpect(jsonPath("$.type.tolerance").value(4))
			.andExpect(jsonPath("$.type.shareId").exists())
			.andExpect(jsonPath("$.axisScores.APOLOGY_SINCERITY").value(0.5))
			.andExpect(jsonPath("$.axisScores.ORDER_OPPORTUNITY").value(0.5))
			.andExpect(jsonPath("$.testedAt").value(endsWith("+09:00")))
			.andReturn().getResponse();
		Cookie cookie = cookieFrom(response);
		assertNoTypeCode(response.getContentAsString());
		assertThat(response.getContentAsString()).doesNotContain("\"code\"", "\"typeCode\"");
		assertThat(jdbcTemplate.queryForObject(
			"SELECT type_code FROM personality_result WHERE anonymous_user_id = ?::uuid AND is_current", String.class,
			cookie.getValue())).isEqualTo("INFP");
	}

	@Test
	@DisplayName("P-2 · P-3: 엄격한 쪽만 매우로 고르면 국룰 수호자형, 내 결과 조회도 같은 결과")
	void submit_allStrict_thenGetMine() throws Exception {
		Cookie cookie = submitNew(strictValues());
		mockMvc.perform(get(MY_RESULT).cookie(cookie))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.type.name").value("국룰 수호자형"))
			.andExpect(jsonPath("$.type.tolerance").value(0))
			.andExpect(jsonPath("$.axisScores.FAULT_STANDARD").value(0.0));
	}

	@Test
	@DisplayName("P-2: 재응시하면 새 결과가 현재 결과가 되고 이전 결과는 이력으로 남는다. 쿠키는 다시 발급하지 않는다")
	void submit_retake_keepsHistory() throws Exception {
		Cookie cookie = submitNew(strictValues());
		MockHttpServletResponse second = mockMvc.perform(post(RESULTS).cookie(cookie)
				.contentType(MediaType.APPLICATION_JSON).content(answersJson(questionIds(), 2)))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.type.name").value("그럴 수도 있지형"))
			.andReturn().getResponse();
		assertThat(second.getHeader(HttpHeaders.SET_COOKIE)).isNull();
		assertThat(jdbcTemplate.queryForList(
			"SELECT type_code FROM personality_result WHERE anonymous_user_id = ?::uuid ORDER BY is_current, id",
			String.class, cookie.getValue())).containsExactly("ESTJ", "INFP");	// 이전 결과(false) → 현재(true)
	}

	@Test
	@DisplayName("P-2: 같은 사용자가 동시에 두 번 제출해도 둘 다 201이고 현재 결과는 하나다 (사용자 행 잠금)")
	void submit_concurrentRetake_oneCurrent() throws Exception {
		Cookie cookie = submitNew(questionValues(2));
		String body = answersJson(strictValues());
		int threads = 2;
		ExecutorService pool = Executors.newFixedThreadPool(threads);
		CountDownLatch ready = new CountDownLatch(threads);
		List<Future<Integer>> futures = new ArrayList<>();
		try {
			for (int i = 0; i < threads; i++) {
				Callable<Integer> call = () -> {
					ready.countDown();
					ready.await();
					return mockMvc.perform(post(RESULTS).cookie(cookie).contentType(MediaType.APPLICATION_JSON).content(body))
						.andReturn().getResponse().getStatus();
				};
				futures.add(pool.submit(call));
			}
			for (Future<Integer> future : futures) {
				assertThat(future.get()).isEqualTo(201);
			}
		} finally {
			pool.shutdownNow();
		}
		assertThat(countResults(cookie, true)).isEqualTo(1);
		assertThat(countResults(cookie, false)).isEqualTo(3);
	}

	@Test
	@DisplayName("P-2: 빠진 문항 · 겹친 문항 · 모르는 문항 · 0 ~ 4 밖 · 소수 값 · answers 없음은 400이고 저장하지 않는다")
	void submit_invalidAnswers_rejected() throws Exception {
		Cookie cookie = submitNew(questionValues(2));
		List<Long> ids = questionIds();
		List<Long> duplicated = new ArrayList<>(ids);
		duplicated.set(15, ids.get(0));
		List<Long> unknown = new ArrayList<>(ids);
		unknown.set(15, -1L);
		List<String> bodies = List.of(
			answersJson(ids.subList(1, ids.size()), 2),		// 빠짐
			answersJson(duplicated, 2),						// 겹침
			answersJson(unknown, 2),						// 모르는 문항
			answersJson(ids, 5),							// 범위 밖
			answersJson(ids, 2).replaceFirst("\"value\":2", "\"value\":1.5"),	// 소수
			"{}");											// answers 없음
		for (String body : bodies) {
			mockMvc.perform(post(RESULTS).cookie(cookie).contentType(MediaType.APPLICATION_JSON).content(body))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
		}
		assertThat(countResults(cookie, false)).isEqualTo(1);	// 처음 낸 결과 그대로
		assertThat(countResults(cookie, true)).isEqualTo(1);
	}

	@Test
	@DisplayName("P-2: 쿠키 없이 낸 요청이 400이면 익명 사용자를 만들지 않고 쿠키도 내려 주지 않는다 (#128 리뷰)")
	void submit_invalidWithoutCookie_issuesNothing() throws Exception {
		int usersBefore = jdbcTemplate.queryForObject("SELECT count(*) FROM anonymous_user", Integer.class);
		MockHttpServletResponse response = mockMvc.perform(post(RESULTS).contentType(MediaType.APPLICATION_JSON)
				.content(answersJson(questionIds().subList(1, 16), 2)))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
			.andReturn().getResponse();
		assertThat(response.getHeader(HttpHeaders.SET_COOKIE)).isNull();
		assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM anonymous_user", Integer.class)).isEqualTo(usersBefore);
	}

	@Test
	@DisplayName("P-3: 쿠키가 없거나 테스트한 적 없으면 404 PERSONALITY_RESULT_NOT_FOUND")
	void getMine_noResult_notFound() throws Exception {
		mockMvc.perform(get(MY_RESULT))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("PERSONALITY_RESULT_NOT_FOUND"));
		mockMvc.perform(get(MY_RESULT).cookie(new Cookie(COOKIE, UUID.randomUUID().toString())))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("PERSONALITY_RESULT_NOT_FOUND"));
	}

	@Test
	@DisplayName("P-4: 공유 주소로 유형 설명만 준다. 없는 값 · 형식이 다른 값 · 대소문자가 다른 값은 모두 404")
	void getType_byShareId() throws Exception {
		String shareId = jdbcTemplate.queryForObject("SELECT share_id FROM personality_type WHERE code = 'INTP'",
			String.class);
		String body = mockMvc.perform(get("/api/v1/personality/types/" + shareId))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.shareId").value(shareId))
			.andExpect(jsonPath("$.name").value("사연 분석러형"))
			.andExpect(jsonPath("$.dailyDescription").exists())
			.andExpect(jsonPath("$.axisScores").doesNotExist())
			.andReturn().getResponse().getContentAsString();
		assertNoTypeCode(body);
		for (String wrong : List.of("zzzzzzzz", "a-b", swapCase(shareId))) {
			mockMvc.perform(get("/api/v1/personality/types/" + wrong))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("PERSONALITY_TYPE_NOT_FOUND"));
		}
	}

	@Test
	@DisplayName("P-5: 로그인하지 않으면 401 UNAUTHORIZED — 쿠키만으로는 마이페이지를 열 수 없다 (BE-140 3b)")
	void myPage_withoutToken_unauthorized() throws Exception {
		mockMvc.perform(get(MY_PAGE))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
		// 손님이 성향 테스트 · 체험으로 쿠키와 기록을 가졌어도 마찬가지다
		Cookie guest = submitNew(strictValues());
		mockMvc.perform(get(MY_PAGE).cookie(guest))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
	}

	@Test
	@DisplayName("P-5: 회원은 다른 회원 · 같은 브라우저 손님의 성향 · 체험을 보지 못하고, 기록이 없으면 404가 아니라 빈 마이페이지")
	void myPage_member_neverSeesOthers() throws Exception {
		long owner = registerMember();
		submitAs(owner, strictValues());
		completedWithContribution(owner, axes(1, 0, 1, 0, 1, 0, 1, 0));
		Cookie guest = submitNew(strictValues());
		UUID guestId = UUID.fromString(guest.getValue());
		completedWithContribution(guestId, axes(1, 0, 1, 0, 1, 0, 1, 0));

		long other = registerMember();
		mockMvc.perform(get(MY_PAGE).header(HttpHeaders.AUTHORIZATION, bearer(other)).cookie(guest))	// 쿠키는 무시한다
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.personality").isEmpty())
			.andExpect(jsonPath("$.judgmentPersonality").isEmpty())
			.andExpect(jsonPath("$.initialPersonality").isEmpty())	// v0.50 (BE-104)
			.andExpect(jsonPath("$.baselineRetake.available").value(false))
			.andExpect(jsonPath("$.baselineRetake.result").isEmpty())
			.andExpect(jsonPath("$.history.length()").value(0))
			.andExpect(jsonPath("$.experiences.length()").value(0));

		mockMvc.perform(get(MY_PAGE).header(HttpHeaders.AUTHORIZATION, bearer(owner)).cookie(guest))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.personality.type.name").value("국룰 수호자형"))
			.andExpect(jsonPath("$.history.length()").value(1))
			.andExpect(jsonPath("$.experiences.length()").value(1));
	}

	@Test
	@DisplayName("P-5: 테스트 결과 · 첫 회차 체험 기여로 종합(ISFJ)과 흐름, 사건마다 최근 회차 목록")
	void myPage_withResultAndContributions() throws Exception {
		long member = registerMember();
		String token = submitAs(member, strictValues());
		long firstCase = insertCase("MURDER", "PUBLISHED");
		long secondCase = insertCase("FRAUD", "PUBLISHED");
		insertMemberExperience(member, firstCase, 1, "COMPLETED");
		insertMemberExperience(member, firstCase, 2, "STARTED");	// 다시 하기 — 목록은 최근 회차, 종합 · 흐름은 첫 회차만
		insertMemberExperience(member, secondCase, 1, "COMPLETED");
		contribution(member, firstCase, 1, """
			{"APOLOGY_SINCERITY":{"down":1,"up":0},"FAULT_STANDARD":{"down":0,"up":2},
			 "PRINCIPLE_RELATION":{"down":2,"up":0},"ORDER_OPPORTUNITY":{"down":0,"up":1}}""");
		contribution(member, secondCase, 1, """
			{"APOLOGY_SINCERITY":{"down":0,"up":0},"FAULT_STANDARD":{"down":0,"up":0},
			 "PRINCIPLE_RELATION":{"down":1,"up":1},"ORDER_OPPORTUNITY":{"down":0,"up":0}}""");

		mockMvc.perform(get(MY_PAGE).header(HttpHeaders.AUTHORIZATION, token))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.personality.type.name").value("국룰 수호자형"))
			.andExpect(jsonPath("$.judgmentPersonality.type.name").value("새끼손가락 도장형"))	// ISFJ
			.andExpect(jsonPath("$.judgmentPersonality.experienceCount").value(2))
			.andExpect(jsonPath("$.judgmentPersonality.axisScores.APOLOGY_SINCERITY").value(1.0))
			.andExpect(jsonPath("$.judgmentPersonality.axisScores.PRINCIPLE_RELATION").value(0.75))
			.andExpect(jsonPath("$.history.length()").value(2))
			.andExpect(jsonPath("$.history[0].caseId").value(firstCase))
			.andExpect(jsonPath("$.history[0].type.name").value("새끼손가락 도장형"))	// 이 체험 한 건도 ISFJ (BE-100)
			.andExpect(jsonPath("$.history[0].type.shareId").isNotEmpty())
			.andExpect(jsonPath("$.history[0].type.code").doesNotExist())	// 4글자 코드는 내보내지 않는다(#119)
			.andExpect(jsonPath("$.history[1].axisScores.PRINCIPLE_RELATION").value(0.5))
			.andExpect(jsonPath("$.history[1].axisScores.APOLOGY_SINCERITY").isEmpty())
			.andExpect(jsonPath("$.history[1].type").isEmpty())	// 축이 빠진 체험은 유형 없음
			.andExpect(jsonPath("$.experiences.length()").value(2))
			.andExpect(jsonPath("$.experiences[?(@.caseId == " + firstCase + ")].attemptNo").value(2))
			.andExpect(jsonPath("$.experiences[?(@.caseId == " + firstCase + ")].status").value("STARTED"));
	}

	@Test
	@DisplayName("P-5: 체험이 4건 이상이면 현재 판결 성향은 최근 3건 평균, 흐름은 전체 (BE-99)")
	void myPage_judgmentUsesRecentThree() throws Exception {
		long member = registerMember();
		String token = submitAs(member, strictValues());
		// 가장 오래된 체험은 네 축 모두 가중(0.0). 전체 평균이면 0.75, 최근 3건이면 1.0
		completedWithContribution(member, axes(0, 5, 0, 5, 0, 5, 0, 5));
		for (int i = 0; i < 3; i++) {
			completedWithContribution(member, axes(1, 0, 1, 0, 1, 0, 1, 0));
		}

		mockMvc.perform(get(MY_PAGE).header(HttpHeaders.AUTHORIZATION, token))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.judgmentPersonality.experienceCount").value(3))	// 평균에 쓴 체험 수
			.andExpect(jsonPath("$.judgmentPersonality.axisScores.APOLOGY_SINCERITY").value(1.0))
			.andExpect(jsonPath("$.judgmentPersonality.axisScores.FAULT_STANDARD").value(1.0))
			.andExpect(jsonPath("$.judgmentPersonality.axisScores.PRINCIPLE_RELATION").value(1.0))
			.andExpect(jsonPath("$.judgmentPersonality.axisScores.ORDER_OPPORTUNITY").value(1.0))
			.andExpect(jsonPath("$.judgmentPersonality.type").isNotEmpty())	// 네 축 모두 값이 있어 유형이 있다
			.andExpect(jsonPath("$.history.length()").value(4))	// 흐름은 자르지 않는다
			.andExpect(jsonPath("$.history[0].axisScores.APOLOGY_SINCERITY").value(0.0));
	}

	@Test
	@DisplayName("P-5: 최근 3건은 기여 저장 순서가 아니라 완료 시각 순서로 고른다 (BE-99, #238 리뷰)")
	void myPage_recentThreeByCompletedAt_notContributionOrder() throws Exception {
		long member = registerMember();
		String token = submitAs(member, strictValues());
		// 동시 비교 공개로 가장 먼저 완료된 체험(가중)의 기여가 가장 늦게 저장된 상황
		long earliest = insertCase("MURDER", "PUBLISHED");
		insertMemberExperience(member, earliest, 1, "COMPLETED");
		completedAt(member, earliest, "2026-10-21T10:00:00+09:00");
		for (int i = 1; i <= 3; i++) {
			long caseId = insertCase("MURDER", "PUBLISHED");
			insertMemberExperience(member, caseId, 1, "COMPLETED");
			completedAt(member, caseId, "2026-10-21T10:0" + i + ":00+09:00");
			contribution(member, caseId, 1, axes(1, 0, 1, 0, 1, 0, 1, 0));
		}
		contribution(member, earliest, 1, axes(0, 5, 0, 5, 0, 5, 0, 5));	// 저장은 마지막

		mockMvc.perform(get(MY_PAGE).header(HttpHeaders.AUTHORIZATION, token))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.judgmentPersonality.experienceCount").value(3))
			.andExpect(jsonPath("$.judgmentPersonality.axisScores.APOLOGY_SINCERITY").value(1.0))	// 저장 순서면 0.667
			.andExpect(jsonPath("$.history.length()").value(4))
			.andExpect(jsonPath("$.history[0].caseId").value(earliest));	// 흐름도 완료 순서
	}

	@Test
	@DisplayName("P-5: 최근 3건에 자료가 없는 축은 오래된 체험에 있어도 null이고 유형도 null (BE-99)")
	void myPage_recentThreeMissingAxis_typeNull() throws Exception {
		long member = registerMember();
		String token = submitAs(member, strictValues());
		completedWithContribution(member, axes(1, 0, 1, 0, 1, 0, 1, 0));	// 질서 · 기회 축은 이 체험에만 있다
		for (int i = 0; i < 3; i++) {
			completedWithContribution(member, axes(1, 1, 0, 2, 2, 0, 0, 0));
		}

		mockMvc.perform(get(MY_PAGE).header(HttpHeaders.AUTHORIZATION, token))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.judgmentPersonality.experienceCount").value(3))
			.andExpect(jsonPath("$.judgmentPersonality.axisScores.APOLOGY_SINCERITY").value(0.5))
			.andExpect(jsonPath("$.judgmentPersonality.axisScores.FAULT_STANDARD").value(0.0))
			.andExpect(jsonPath("$.judgmentPersonality.axisScores.ORDER_OPPORTUNITY").isEmpty())
			.andExpect(jsonPath("$.judgmentPersonality.type").isEmpty());
	}

	@Test
	@DisplayName("P-5: 최근 3건이 네 축 모두 자료가 없으면 현재 판결 성향은 null, 흐름은 남는다 (BE-99)")
	void myPage_recentThreeAllEmpty_judgmentNull() throws Exception {
		long member = registerMember();
		String token = submitAs(member, strictValues());
		completedWithContribution(member, axes(1, 0, 1, 0, 1, 0, 1, 0));
		for (int i = 0; i < 3; i++) {
			completedWithContribution(member, axes(0, 0, 0, 0, 0, 0, 0, 0));
		}

		mockMvc.perform(get(MY_PAGE).header(HttpHeaders.AUTHORIZATION, token))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.judgmentPersonality").isEmpty())
			.andExpect(jsonPath("$.history.length()").value(4));
	}

	@Test
	@DisplayName("P-5: history[].type은 그 체험 하나만으로 낸 종합과 같은 유형이다 — 동점은 관대 글자 (BE-100)")
	void myPage_historyType_sameRuleAsJudgment() throws Exception {
		long member = registerMember();
		String token = submitAs(member, strictValues());
		completedWithContribution(member, axes(1, 1, 2, 2, 1, 1, 3, 3));	// 네 축 모두 동점(0.5) → 네 글자 모두 관대

		String body = mockMvc.perform(get(MY_PAGE).header(HttpHeaders.AUTHORIZATION, token))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.history.length()").value(1))
			.andExpect(jsonPath("$.history[0].type.tolerance").value(4))	// 관대 글자 4개
			.andReturn().getResponse().getContentAsString();

		// 체험이 하나뿐이면 현재 판결 성향도 같은 한 건이다 — 두 유형이 같아야 한다
		String historyShareId = JsonPath.read(body, "$.history[0].type.shareId");
		String judgmentShareId = JsonPath.read(body, "$.judgmentPersonality.type.shareId");
		assertThat(historyShareId).isEqualTo(judgmentShareId);
	}

	@Test
	@DisplayName("P-5: 사건별 유형은 체험마다 따로 정한다 — 종합에 유형이 없어도 네 축이 다 있는 체험은 유형이 있다 (BE-100)")
	void myPage_historyType_perExperience() throws Exception {
		long member = registerMember();
		String token = submitAs(member, strictValues());
		completedWithContribution(member, axes(1, 0, 1, 0, 1, 0, 1, 0));	// 네 축 모두 있음
		for (int i = 0; i < 3; i++) {
			completedWithContribution(member, axes(1, 1, 0, 2, 2, 0, 0, 0));	// 질서 · 기회 축 없음
		}

		mockMvc.perform(get(MY_PAGE).header(HttpHeaders.AUTHORIZATION, token))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.judgmentPersonality.type").isEmpty())	// 최근 3건에 질서 · 기회 축이 없다
			.andExpect(jsonPath("$.history[0].type.tolerance").value(4))	// 가장 오래된 체험은 네 축 모두 관대
			.andExpect(jsonPath("$.history[1].type").isEmpty())
			.andExpect(jsonPath("$.history[3].type").isEmpty());
	}

	@Test
	@DisplayName("P-5: 쿠키는 있지만 체험 기여가 없으면 현재 판결 성향은 null")
	void myPage_noContributions_judgmentNull() throws Exception {
		long member = registerMember();
		String token = submitAs(member, strictValues());

		mockMvc.perform(get(MY_PAGE).header(HttpHeaders.AUTHORIZATION, token))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.personality").isNotEmpty())
			.andExpect(jsonPath("$.judgmentPersonality").isEmpty())
			.andExpect(jsonPath("$.history.length()").value(0));
	}

	// --- 성향카드 재심 · 처음 성향 (BE-104, API 명세 v0.50) ---

	@Test
	@DisplayName("P-2: 응시마다 그 시점에 완료한 사건 수를 남긴다 (BE-104)")
	void submit_recordsCompletedExperienceCount() throws Exception {
		Cookie cookie = submitNew(strictValues());
		UUID user = UUID.fromString(cookie.getValue());
		mockMvc.perform(get(MY_RESULT).cookie(cookie))
			.andExpect(jsonPath("$.completedExperienceCount").value(0));
		completedWithContribution(user, axes(1, 0, 1, 0, 1, 0, 1, 0));
		completedWithContribution(user, axes(1, 0, 1, 0, 1, 0, 1, 0));

		mockMvc.perform(post(RESULTS).cookie(cookie).contentType(MediaType.APPLICATION_JSON).content(answersJson(questionIds(), 2)))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.completedExperienceCount").value(2));
	}

	@Test
	@DisplayName("P-2: 테스트한 적 없이 재심을 보내면 첫 테스트로 처리하고 재심 횟수는 쓰지 않는다 (BE-104)")
	void baseline_withoutTest_countsAsFirstTest() throws Exception {
		long member = registerMember();
		mockMvc.perform(post(RESULTS).header(HttpHeaders.AUTHORIZATION, bearer(member)).contentType(MediaType.APPLICATION_JSON)
				.content(withBaseline(answersJson(strictValues()), "true")))
			.andExpect(status().isCreated());

		mockMvc.perform(get(MY_PAGE).header(HttpHeaders.AUTHORIZATION, bearer(member)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.initialPersonality.source").value("TEST"))
			.andExpect(jsonPath("$.initialPersonality.type.name").value("국룰 수호자형"))
			.andExpect(jsonPath("$.baselineRetake.available").value(true))
			.andExpect(jsonPath("$.baselineRetake.result").isEmpty());
		assertThat(countRetakes(member)).isZero();
	}

	@Test
	@DisplayName("P-2 · P-5: 재심은 처음 성향을 덮어쓰지 않고 한 건 더 남는다. 이후 일반 응시도 처음 성향 · 재심을 바꾸지 않는다 (BE-104)")
	void baseline_afterTest_keepsInitial() throws Exception {
		long member = registerMember();
		String token = submitAs(member, strictValues());	// 초심: 국룰 수호자형, 완료 0건
		completedWithContribution(member, axes(1, 0, 1, 0, 1, 0, 1, 0));

		mockMvc.perform(post(RESULTS).header(HttpHeaders.AUTHORIZATION, token).contentType(MediaType.APPLICATION_JSON)
				.content(withBaseline(answersJson(questionIds(), 2), "true")))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.type.name").value("그럴 수도 있지형"))
			.andExpect(jsonPath("$.completedExperienceCount").value(1));

		mockMvc.perform(get(MY_PAGE).header(HttpHeaders.AUTHORIZATION, token))
			.andExpect(jsonPath("$.personality.type.name").value("그럴 수도 있지형"))	// 재심 결과가 현재 결과
			.andExpect(jsonPath("$.initialPersonality.source").value("TEST"))
			.andExpect(jsonPath("$.initialPersonality.type.name").value("국룰 수호자형"))
			.andExpect(jsonPath("$.initialPersonality.completedExperienceCount").value(0))
			.andExpect(jsonPath("$.initialPersonality.experienceCount").doesNotExist())
			.andExpect(jsonPath("$.baselineRetake.available").value(false))
			.andExpect(jsonPath("$.baselineRetake.result.type.name").value("그럴 수도 있지형"))
			.andExpect(jsonPath("$.baselineRetake.result.completedExperienceCount").value(1));

		// 일반 응시는 횟수 제한 없이 되고, 처음 성향 · 재심 결과는 그대로다
		mockMvc.perform(post(RESULTS).header(HttpHeaders.AUTHORIZATION, token).contentType(MediaType.APPLICATION_JSON).content(answersJson(strictValues())))
			.andExpect(status().isCreated());
		mockMvc.perform(get(MY_PAGE).header(HttpHeaders.AUTHORIZATION, token))
			.andExpect(jsonPath("$.personality.type.name").value("국룰 수호자형"))
			.andExpect(jsonPath("$.initialPersonality.type.name").value("국룰 수호자형"))
			.andExpect(jsonPath("$.baselineRetake.result.type.name").value("그럴 수도 있지형"));
		assertThat(countResults(member, false)).isEqualTo(3);
		assertThat(countRetakes(member)).isEqualTo(1);
	}

	@Test
	@DisplayName("P-2: 재심은 사용자당 한 번 — 두 번째는 409 BASELINE_RETAKE_USED이고 결과를 저장하지 않는다 (BE-104)")
	void baseline_secondTime_conflict() throws Exception {
		Cookie cookie = submitNew(strictValues());
		mockMvc.perform(post(RESULTS).cookie(cookie).contentType(MediaType.APPLICATION_JSON)
				.content(withBaseline(answersJson(questionIds(), 2), "true")))
			.andExpect(status().isCreated());

		mockMvc.perform(post(RESULTS).cookie(cookie).contentType(MediaType.APPLICATION_JSON)
				.content(withBaseline(answersJson(strictValues()), "true")))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("BASELINE_RETAKE_USED"));
		assertThat(countResults(cookie, false)).isEqualTo(2);
		mockMvc.perform(get(MY_RESULT).cookie(cookie))
			.andExpect(jsonPath("$.type.name").value("그럴 수도 있지형"));	// 현재 결과도 바뀌지 않는다
	}

	@Test
	@DisplayName("P-2: baseline이 불리언이 아니면 400 — 문자열 \"true\" · 숫자 1도 받지 않는다. null · 생략은 일반 응시 (BE-104)")
	void baseline_notBoolean_rejected() throws Exception {
		Cookie cookie = submitNew(strictValues());
		for (String value : List.of("\"true\"", "1", "\"yes\"", "{}")) {
			mockMvc.perform(post(RESULTS).cookie(cookie).contentType(MediaType.APPLICATION_JSON)
					.content(withBaseline(answersJson(strictValues()), value)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
		}
		assertThat(countResults(cookie, false)).isEqualTo(1);

		mockMvc.perform(post(RESULTS).cookie(cookie).contentType(MediaType.APPLICATION_JSON)
				.content(withBaseline(answersJson(strictValues()), "null")))
			.andExpect(status().isCreated());
		assertThat(countRetakes(cookie)).isZero();

		// 쿠키 없이 낸 요청이 baseline 형식으로 400이면 익명 ID를 발급하지 않는다(P-2 검증 먼저)
		MockHttpServletResponse response = mockMvc.perform(post(RESULTS).contentType(MediaType.APPLICATION_JSON)
				.content(withBaseline(answersJson(strictValues()), "1")))
			.andExpect(status().isBadRequest())
			.andReturn().getResponse();
		assertThat(response.getHeader(HttpHeaders.SET_COOKIE)).isNull();
	}

	@Test
	@DisplayName("P-2: 같은 사용자가 재심을 동시에 두 번 보내도 한 건만 저장되고 나머지는 409 (BE-104)")
	void baseline_concurrent_oneSaved() throws Exception {
		Cookie cookie = submitNew(strictValues());
		String body = withBaseline(answersJson(questionIds(), 2), "true");
		int threads = 2;
		ExecutorService pool = Executors.newFixedThreadPool(threads);
		CountDownLatch ready = new CountDownLatch(threads);
		List<Future<Integer>> futures = new ArrayList<>();
		try {
			for (int i = 0; i < threads; i++) {
				Callable<Integer> call = () -> {
					ready.countDown();
					ready.await();
					return mockMvc.perform(post(RESULTS).cookie(cookie).contentType(MediaType.APPLICATION_JSON).content(body))
						.andReturn().getResponse().getStatus();
				};
				futures.add(pool.submit(call));
			}
			List<Integer> statuses = new ArrayList<>();
			for (Future<Integer> future : futures) {
				statuses.add(future.get());
			}
			assertThat(statuses).containsExactlyInAnyOrder(201, 409);
		} finally {
			pool.shutdownNow();
		}
		assertThat(countRetakes(cookie)).isEqualTo(1);
		assertThat(countResults(cookie, true)).isEqualTo(1);
	}

	@Test
	@DisplayName("P-5: 테스트 전 처음 성향은 첫 판결(완료 순서로 처음 3건 평균) — judgmentPersonality 모양 + source (BE-104)")
	void myPage_initialJudgment_beforeTest() throws Exception {
		long member = registerMember();
		String token = bearer(member);
		for (int i = 0; i < 3; i++) {
			completedWithContribution(member, axes(1, 0, 1, 0, 1, 0, 1, 0));	// 처음 3건: 네 축 모두 관대
		}
		completedWithContribution(member, axes(0, 5, 0, 5, 0, 5, 0, 5));	// 4번째: 처음 성향에는 들어가지 않는다

		mockMvc.perform(get(MY_PAGE).header(HttpHeaders.AUTHORIZATION, token))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.personality").isEmpty())
			.andExpect(jsonPath("$.initialPersonality.source").value("JUDGMENT"))
			.andExpect(jsonPath("$.initialPersonality.type.tolerance").value(4))
			.andExpect(jsonPath("$.initialPersonality.axisScores.APOLOGY_SINCERITY").value(1.0))
			.andExpect(jsonPath("$.initialPersonality.experienceCount").value(3))
			.andExpect(jsonPath("$.initialPersonality.testedAt").doesNotExist())
			.andExpect(jsonPath("$.initialPersonality.completedExperienceCount").doesNotExist())
			.andExpect(jsonPath("$.judgmentPersonality.axisScores.APOLOGY_SINCERITY").value(lessThan(1.0)))	// 최근 3건에는 4번째가 들어간다
			.andExpect(jsonPath("$.baselineRetake.available").value(false))	// 첫 테스트가 없어 재심 불가
			.andExpect(jsonPath("$.baselineRetake.result").isEmpty());
	}

	@Test
	@DisplayName("P-5: 테스트 전 처음 3건에 빠진 축이 있으면 처음 성향은 null (유형이 있을 때만, BE-104)")
	void myPage_initialJudgment_missingAxis_null() throws Exception {
		long member = registerMember();
		String token = bearer(member);
		for (int i = 0; i < 3; i++) {
			completedWithContribution(member, axes(1, 0, 1, 0, 1, 0, 0, 0));	// 질서 · 기회 축 없음
		}
		completedWithContribution(member, axes(1, 0, 1, 0, 1, 0, 1, 0));

		mockMvc.perform(get(MY_PAGE).header(HttpHeaders.AUTHORIZATION, token))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.initialPersonality").isEmpty());
	}

	@Test
	@DisplayName("P-5: 첫 판결이 있어도 테스트를 하면 처음 성향은 그 첫 테스트가 대신한다 (BE-104)")
	void myPage_initialTest_replacesJudgment() throws Exception {
		long member = registerMember();
		String token = bearer(member);
		for (int i = 0; i < 3; i++) {
			completedWithContribution(member, axes(1, 0, 1, 0, 1, 0, 1, 0));
		}
		mockMvc.perform(post(RESULTS).header(HttpHeaders.AUTHORIZATION, token).contentType(MediaType.APPLICATION_JSON).content(answersJson(strictValues())))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.completedExperienceCount").value(3));

		mockMvc.perform(get(MY_PAGE).header(HttpHeaders.AUTHORIZATION, token))
			.andExpect(jsonPath("$.initialPersonality.source").value("TEST"))
			.andExpect(jsonPath("$.initialPersonality.type.name").value("국룰 수호자형"))
			.andExpect(jsonPath("$.initialPersonality.completedExperienceCount").value(3))
			.andExpect(jsonPath("$.baselineRetake.available").value(true));
	}

	// --- 도움 함수 ---

	/** 새 사건 하나를 첫 회차로 완료하고 기여를 저장한다. 호출 순서가 완료 순서다 */
	private void completedWithContribution(UUID user, String axisCounts) {
		long caseId = insertCase("MURDER", "PUBLISHED");
		insertExperience(user, caseId, 1, "COMPLETED");
		contribution(user, caseId, 1, axisCounts);
	}

	/** 회원의 새 사건 하나를 첫 회차로 완료하고 기여를 저장한다 */
	private void completedWithContribution(long member, String axisCounts) {
		long caseId = insertCase("MURDER", "PUBLISHED");
		insertMemberExperience(member, caseId, 1, "COMPLETED");
		contribution(member, caseId, 1, axisCounts);
	}

	/** 회원이 주인인 체험(anonymous_user_id는 비운다) */
	private void insertMemberExperience(long member, long caseId, int attemptNo, String status) {
		jdbcTemplate.update("INSERT INTO experience (member_id, case_id, attempt_no, status, completed_at) "
			+ "VALUES (?, ?, ?, ?, CASE WHEN ? = 'COMPLETED' THEN now() END)", member, caseId, attemptNo, status, status);
	}

	private void completedAt(UUID user, long caseId, String completedAt) {
		jdbcTemplate.update("UPDATE experience SET completed_at = ?::timestamptz WHERE anonymous_user_id = ? AND case_id = ? AND attempt_no = 1",
			completedAt, user, caseId);
	}

	private void completedAt(long member, long caseId, String completedAt) {
		jdbcTemplate.update("UPDATE experience SET completed_at = ?::timestamptz WHERE member_id = ? AND case_id = ? AND attempt_no = 1",
			completedAt, member, caseId);
	}

	private void contribution(long member, long caseId, int attemptNo, String axisCounts) {
		jdbcTemplate.update("""
			INSERT INTO experience_personality (experience_id, axis_counts)
			SELECT id, ?::jsonb FROM experience WHERE member_id = ? AND case_id = ? AND attempt_no = ?""",
			axisCounts, member, caseId, attemptNo);
	}

	/** 회원으로 답을 제출하고(쿠키는 내려 주지 않는다) Authorization 헤더 값을 돌려준다 */
	private String submitAs(long member, List<Integer> values) throws Exception {
		String token = bearer(member);
		MockHttpServletResponse response = mockMvc.perform(post(RESULTS).header(HttpHeaders.AUTHORIZATION, token)
				.contentType(MediaType.APPLICATION_JSON).content(answersJson(values)))
			.andExpect(status().isCreated()).andReturn().getResponse();
		assertThat(response.getHeader(HttpHeaders.SET_COOKIE)).isNull();
		return token;
	}

	private int countRetakes(long member) {
		return jdbcTemplate.queryForObject(
			"SELECT count(*) FROM personality_result WHERE member_id = ? AND is_baseline_retake", Integer.class, member);
	}

	private int countResults(long member, boolean currentOnly) {
		return jdbcTemplate.queryForObject("SELECT count(*) FROM personality_result WHERE member_id = ?"
			+ (currentOnly ? " AND is_current" : ""), Integer.class, member);
	}

	/** 축 순서(사과 · 과실 · 원칙 · 질서)대로 감경 · 가중 횟수를 받아 axis_counts JSON을 만든다 */
	private static String axes(int apologyDown, int apologyUp, int faultDown, int faultUp,
		int principleDown, int principleUp, int orderDown, int orderUp) {
		return """
			{"APOLOGY_SINCERITY":{"down":%d,"up":%d},"FAULT_STANDARD":{"down":%d,"up":%d},
			 "PRINCIPLE_RELATION":{"down":%d,"up":%d},"ORDER_OPPORTUNITY":{"down":%d,"up":%d}}"""
			.formatted(apologyDown, apologyUp, faultDown, faultUp, principleDown, principleUp, orderDown, orderUp);
	}

	/** 쿠키 없이 답을 제출하고 발급된 쿠키를 돌려준다 */
	private Cookie submitNew(List<Integer> values) throws Exception {
		MockHttpServletRequestBuilder request = post(RESULTS).contentType(MediaType.APPLICATION_JSON)
			.content(answersJson(values));
		return cookieFrom(mockMvc.perform(request).andExpect(status().isCreated()).andReturn().getResponse());
	}

	private Cookie cookieFrom(MockHttpServletResponse response) {
		String value = response.getHeader(HttpHeaders.SET_COOKIE).split(";")[0].substring((COOKIE + "=").length());
		users.add(UUID.fromString(value));
		return new Cookie(COOKIE, value);
	}

	/** answers JSON 끝에 "baseline": value를 붙인다(value는 JSON 리터럴 그대로) */
	private static String withBaseline(String answersJson, String value) {
		return answersJson.substring(0, answersJson.length() - 1) + ",\"baseline\":" + value + "}";
	}

	private int countRetakes(Cookie cookie) {
		return jdbcTemplate.queryForObject(
			"SELECT count(*) FROM personality_result WHERE anonymous_user_id = ?::uuid AND is_baseline_retake",
			Integer.class, cookie.getValue());
	}

	private int countResults(Cookie cookie, boolean currentOnly) {
		return jdbcTemplate.queryForObject("SELECT count(*) FROM personality_result WHERE anonymous_user_id = ?::uuid"
			+ (currentOnly ? " AND is_current" : ""), Integer.class, cookie.getValue());
	}

	private List<Long> questionIds() {
		return jdbcTemplate.queryForList(
			"SELECT id FROM personality_question WHERE is_active ORDER BY display_order", Long.class);
	}

	private List<Integer> questionValues(int value) {
		return questionIds().stream().map(id -> value).toList();
	}

	/** 문항마다 엄격 글자 쪽 "매우" (A가 엄격이면 0, B가 엄격이면 4) */
	private List<Integer> strictValues() {
		return jdbcTemplate.queryForList("""
			SELECT CASE WHEN c.letter IN ('E', 'S', 'T', 'J') THEN 0 ELSE 4 END
			FROM personality_question q JOIN personality_choice c ON c.question_id = q.id AND c.display_order = 1
			WHERE q.is_active ORDER BY q.display_order""", Integer.class);
	}

	private String answersJson(List<Integer> values) {
		List<Long> ids = questionIds();
		StringBuilder json = new StringBuilder("{\"answers\":[");
		for (int i = 0; i < ids.size(); i++) {
			json.append(i == 0 ? "" : ",").append("{\"questionId\":").append(ids.get(i))
				.append(",\"value\":").append(values.get(i)).append('}');
		}
		return json.append("]}").toString();
	}

	private static String answersJson(List<Long> ids, int value) {
		StringBuilder json = new StringBuilder("{\"answers\":[");
		for (int i = 0; i < ids.size(); i++) {
			json.append(i == 0 ? "" : ",").append("{\"questionId\":").append(ids.get(i))
				.append(",\"value\":").append(value).append('}');
		}
		return json.append("]}").toString();
	}

	private void contribution(UUID user, long caseId, int attemptNo, String axisCounts) {
		jdbcTemplate.update("""
			INSERT INTO experience_personality (experience_id, axis_counts)
			SELECT id, ?::jsonb FROM experience WHERE anonymous_user_id = ? AND case_id = ? AND attempt_no = ?""",
			axisCounts, user, caseId, attemptNo);
	}

	private static String swapCase(String value) {
		StringBuilder swapped = new StringBuilder(value.length());
		for (char c : value.toCharArray()) {
			swapped.append(Character.isUpperCase(c) ? Character.toLowerCase(c) : Character.toUpperCase(c));
		}
		return swapped.toString();
	}

	private static void assertNoTypeCode(String body) {
		assertThat(CODES).allSatisfy(code -> assertThat(body).doesNotContain("\"" + code + "\""));
	}
}
