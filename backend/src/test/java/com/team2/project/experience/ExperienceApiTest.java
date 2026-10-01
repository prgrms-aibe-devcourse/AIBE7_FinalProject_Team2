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

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletResponse;

import jakarta.servlet.http.Cookie;

import com.team2.project.support.ApiIntegrationTest;

class ExperienceApiTest extends ApiIntegrationTest {

	private static final String COOKIE = "NLNB_AID";

	@Test
	void start_firstTime_createsExperienceAndIssuesCookie() throws Exception {
		long caseId = insertCase("MURDER", "PUBLISHED");

		MockHttpServletResponse response = mockMvc.perform(post(url(caseId)))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.caseId").value(caseId))
				.andExpect(jsonPath("$.attemptNo").value(1))
				.andExpect(jsonPath("$.status").value("STARTED"))
				.andExpect(jsonPath("$.lastReviewedStep").value(0))
				.andExpect(jsonPath("$.startedAt").value(org.hamcrest.Matchers.endsWith("+09:00")))
				.andReturn().getResponse();

		String setCookie = response.getHeader(HttpHeaders.SET_COOKIE);
		assertThat(setCookie).startsWith(COOKIE + "=")
				.contains("HttpOnly", "Secure", "SameSite=Lax", "Path=/", "Max-Age=31536000");
		assertThat(countExperiences(caseId)).isEqualTo(1);
	}

	@Test
	void start_withExistingCookie_returnsSameExperienceWithoutNewCookie() throws Exception {
		long caseId = insertCase("MURDER", "PUBLISHED");
		Cookie cookie = startAndGetCookie(caseId);

		MockHttpServletResponse second = mockMvc.perform(post(url(caseId)).cookie(cookie))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("STARTED"))
				.andReturn().getResponse();

		assertThat(second.getHeader(HttpHeaders.SET_COOKIE)).isNull();
		assertThat(countExperiences(caseId)).isEqualTo(1);
	}

	@Test
	void start_existingProgressedExperience_returnsCurrentStatus() throws Exception {
		long caseId = insertCase("MURDER", "PUBLISHED");
		UUID userId = UUID.randomUUID();
		insertExperience(userId, caseId, 1, "COMPLETED");

		mockMvc.perform(post(url(caseId)).cookie(new Cookie(COOKIE, userId.toString())))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("COMPLETED"));
		assertThat(countExperiences(caseId)).isEqualTo(1);
	}

	@Test
	void start_cookieNotInDb_issuesNewCookieInsteadOfAdoptingIt() throws Exception {
		long caseId = insertCase("MURDER", "PUBLISHED");
		UUID unknown = UUID.randomUUID();

		MockHttpServletResponse response = mockMvc.perform(post(url(caseId)).cookie(new Cookie(COOKIE, unknown.toString())))
				.andExpect(status().isCreated())
				.andReturn().getResponse();

		assertThat(response.getHeader(HttpHeaders.SET_COOKIE)).startsWith(COOKIE + "=").doesNotContain(unknown.toString());
	}

	@Test
	void start_malformedCookie_treatedAsNoCookie() throws Exception {
		long caseId = insertCase("MURDER", "PUBLISHED");

		mockMvc.perform(post(url(caseId)).cookie(new Cookie(COOKIE, "not-a-uuid")))
				.andExpect(status().isCreated());
	}

	@Test
	void start_sameUserDifferentCases_createsOneExperienceEach() throws Exception {
		long first = insertCase("MURDER", "PUBLISHED");
		long second = insertCase("FRAUD", "PUBLISHED");
		Cookie cookie = startAndGetCookie(first);

		mockMvc.perform(post(url(second)).cookie(cookie)).andExpect(status().isCreated());

		assertThat(countExperiences(first)).isEqualTo(1);
		assertThat(countExperiences(second)).isEqualTo(1);
	}

	@Test
	void start_unknownCase_returnsCaseNotFound() throws Exception {
		mockMvc.perform(post(url(Long.MAX_VALUE)))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("CASE_NOT_FOUND"));
	}

	@Test
	void start_unpublishedCase_returnsCaseNotFound() throws Exception {
		long caseId = insertCase("MURDER", "DRAFT");

		mockMvc.perform(post(url(caseId)))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("CASE_NOT_FOUND"));
		assertThat(countExperiences(caseId)).isZero();
	}

	@Test
	void start_concurrentRequestsFromSameUser_createsOnlyOneExperience() throws Exception {
		long caseId = insertCase("MURDER", "PUBLISHED");
		// 이미 익명 사용자가 있는 상태에서 같은 쿠키로 동시에 시작한다
		Cookie cookie = startAndGetCookie(caseId);
		jdbcTemplate.update("DELETE FROM experience WHERE case_id = ?", caseId);

		int threads = 8;
		CountDownLatch ready = new CountDownLatch(threads);
		CountDownLatch go = new CountDownLatch(1);
		ExecutorService pool = Executors.newFixedThreadPool(threads);
		try {
			Callable<Integer> request = () -> {
				ready.countDown();
				go.await();
				return mockMvc.perform(post(url(caseId)).cookie(cookie)).andReturn().getResponse().getStatus();
			};
			List<Future<Integer>> futures = java.util.stream.IntStream.range(0, threads)
					.mapToObj(i -> pool.submit(request)).toList();
			ready.await();
			go.countDown();

			int created = 0;
			for (Future<Integer> future : futures) {
				int httpStatus = future.get();
				assertThat(httpStatus).isIn(200, 201);
				if (httpStatus == 201) {
					created++;
				}
			}
			assertThat(created).isEqualTo(1);
		} finally {
			pool.shutdownNow();
		}
		assertThat(countExperiences(caseId)).isEqualTo(1);
	}

	@Test
	void getMyExperience_afterStart_returnsStatus() throws Exception {
		long caseId = insertCase("MURDER", "PUBLISHED");
		Cookie cookie = startAndGetCookie(caseId);

		mockMvc.perform(get(url(caseId)).cookie(cookie))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.caseId").value(caseId))
				.andExpect(jsonPath("$.status").value("STARTED"))
				.andExpect(jsonPath("$.attemptNo").value(1));
	}

	@Test
	void getMyExperience_noCookie_returnsExperienceNotFound() throws Exception {
		long caseId = insertCase("MURDER", "PUBLISHED");

		mockMvc.perform(get(url(caseId)))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("EXPERIENCE_NOT_FOUND"));
	}

	@Test
	void getMyExperience_otherUsersCookie_returnsExperienceNotFound() throws Exception {
		long caseId = insertCase("MURDER", "PUBLISHED");
		startAndGetCookie(caseId);
		Cookie stranger = startAndGetCookie(insertCase("FRAUD", "PUBLISHED"));

		mockMvc.perform(get(url(caseId)).cookie(stranger))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("EXPERIENCE_NOT_FOUND"));
	}

	@Test
	void getMyExperience_unknownCase_returnsCaseNotFound() throws Exception {
		mockMvc.perform(get(url(Long.MAX_VALUE)))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("CASE_NOT_FOUND"));
	}

	@Test
	void getMyExperience_updatesLastSeenAt() throws Exception {
		long caseId = insertCase("MURDER", "PUBLISHED");
		Cookie cookie = startAndGetCookie(caseId);
		jdbcTemplate.update("UPDATE anonymous_user SET last_seen_at = now() - interval '1 day' WHERE id = ?",
				UUID.fromString(cookie.getValue()));

		mockMvc.perform(get(url(caseId)).cookie(cookie)).andExpect(status().isOk());

		Boolean refreshed = jdbcTemplate.queryForObject(
				"SELECT last_seen_at > now() - interval '1 minute' FROM anonymous_user WHERE id = ?",
				Boolean.class, UUID.fromString(cookie.getValue()));
		assertThat(refreshed).isTrue();
	}

	private Cookie startAndGetCookie(long caseId) throws Exception {
		MockHttpServletResponse response = mockMvc.perform(post(url(caseId))).andExpect(status().isCreated())
				.andReturn().getResponse();
		String value = response.getHeader(HttpHeaders.SET_COOKIE).split(";")[0].substring((COOKIE + "=").length());
		return new Cookie(COOKIE, value);
	}

	private static String url(long caseId) {
		return "/api/v1/cases/" + caseId + "/experience";
	}
}
