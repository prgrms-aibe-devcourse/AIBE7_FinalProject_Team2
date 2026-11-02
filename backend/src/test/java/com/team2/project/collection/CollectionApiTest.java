package com.team2.project.collection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.team2.project.support.ApiIntegrationTest;
import com.team2.project.support.FakeMemberTokens;
import jakarta.servlet.http.Cookie;
import java.util.UUID;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

/**
 * 수집 기록 P-9 · 컬렉션 봤음 P-10 · 도전과제 봤음 P-11 · 마이페이지 collection · achievements 해금 (BE-83)
 * 로그인한 회원만 쓸 수 있고 손님은 401이다 (BE-140). 항목 id는 모을 수 있는 목록(BE-136)에 있어야 한다: 일러스트 case-{공개 사건 id}-{origin|conclusion|reenact}, 의상은 역할 의상 그림({who}:{role})
 */
@Import(FakeMemberTokens.class)
class CollectionApiTest extends ApiIntegrationTest {

	private static final String COLLECTION = "/api/v1/collection";
	private static final String COLLECTION_SEEN = "/api/v1/collection/seen";
	private static final String ACHIEVEMENTS_SEEN = "/api/v1/achievements/seen";
	private static final String MY_PAGE = "/api/v1/my-page";

	/** 사건 하나를 완료한 회원 (회원이 주인인 체험) */
	private long completedMember(long caseId) {
		long member = registerMember();
		completeCase(member, caseId);
		return member;
	}

	private void completeCase(long member, long caseId) {
		jdbcTemplate.update("""
			INSERT INTO experience (member_id, case_id, attempt_no, status, completed_at)
			VALUES (?, ?, 1, 'COMPLETED', now())
			""", member, caseId);
	}

	private static String item(String kind, String itemId) {
		return "{\"kind\":\"" + kind + "\",\"itemId\":\"" + itemId + "\"}";
	}

	private static String body(long caseId, boolean backfill, String... items) {
		return "{\"caseId\":" + caseId + ",\"backfill\":" + backfill + ",\"items\":[" + String.join(",", items) + "]}";
	}

	private ResultActions record(Long member, String json) throws Exception {
		var request = post(COLLECTION).contentType(MediaType.APPLICATION_JSON).content(json);
		return mockMvc.perform(member == null ? request : request.header("Authorization", bearer(member)));
	}

	private ResultActions myPage(long member) throws Exception {
		return mockMvc.perform(get(MY_PAGE).header("Authorization", bearer(member)));
	}

	private int countItems(long member) {
		return jdbcTemplate.queryForObject("SELECT count(*) FROM member_collection_item WHERE member_id = ?",
			Integer.class, member);
	}

	/** 사건 일러스트 id (BE-136 목록 규칙) */
	private static String illustration(long caseId, String slot) {
		return "case-" + caseId + "-" + slot;
	}

	/** 그 사건의 일러스트 3장 전부(발단 · 결론 · 재연) */
	private static String[] illustrations(long caseId) {
		return Stream.of("origin", "conclusion", "reenact").map(slot -> item("illustration", illustration(caseId, slot)))
			.toArray(String[]::new);
	}

	private static String[] concat(String[]... groups) {
		return Stream.of(groups).flatMap(Stream::of).toArray(String[]::new);
	}

	@Test
	@DisplayName("완료한 사건의 항목을 저장하고(200) 마이페이지 collection에 \"새로 얻음\"으로 나온다")
	void recordAndReadBack() throws Exception {
		long caseId = insertCase("MURDER", "PUBLISHED");
		long member = completedMember(caseId);
		
		record(member, body(caseId, false, item("illustration", illustration(caseId, "origin")), item("cast", "naero:judge")))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.added.length()").value(2))
			.andExpect(jsonPath("$.added[0].kind").value("illustration"))
			.andExpect(jsonPath("$.added[0].itemId").value(illustration(caseId, "origin")))
			.andExpect(jsonPath("$.added[0].caseId").value(caseId))
			.andExpect(jsonPath("$.added[0].isNew").value(true))
			.andExpect(jsonPath("$.collected").value(2));

		myPage(member)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.collection.collected").value(2))
			.andExpect(jsonPath("$.collection.items.length()").value(2))
			.andExpect(jsonPath("$.collection.items[0].isNew").value(true))
			.andExpect(jsonPath("$.collection.items[0].acquiredAt").isNotEmpty())
			.andExpect(jsonPath("$.achievements[3].id").value("collect-5"))
			.andExpect(jsonPath("$.achievements[3].value").value(2));
	}

	@Test
	@DisplayName("멱등: 같은 요청을 다시 보내면 added는 비고 모은 수는 그대로")
	void idempotent() throws Exception {
		long caseId = insertCase("MURDER", "PUBLISHED");
		long member = completedMember(caseId);
		String json = body(caseId, false, item("illustration", illustration(caseId, "origin")),
			item("illustration", illustration(caseId, "origin")));

		record(member, json).andExpect(status().isOk()).andExpect(jsonPath("$.added.length()").value(1));
		record(member, json)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.added.length()").value(0))
			.andExpect(jsonPath("$.collected").value(1));
	}

	@Test
	@DisplayName("같은 그림을 다른 사건에서 다시 얻어도 처음 얻은 사건 · 시각이 남는다 (그림별 key)")
	void sameIllustrationKeepsFirstCase() throws Exception {
		long firstCase = insertCase("MURDER", "PUBLISHED");
		long secondCase = insertCase("FRAUD", "PUBLISHED");
		long member = completedMember(firstCase);
		completeCase(member, secondCase);
		
		// 두 번째 사건이 첫 사건의 그림을 다시 쓴다(그림별 key) — 목록에 있는 다른 공개 사건의 그림이라 받는다 (BE-136)
		String shared = item("illustration", illustration(firstCase, "origin"));
		record(member, body(firstCase, false, shared)).andExpect(status().isOk());
		Instant firstAcquired = jdbcTemplate.queryForObject(
			"SELECT acquired_at FROM member_collection_item WHERE member_id = ?", Timestamp.class, member).toInstant();

		record(member, body(secondCase, false, shared))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.added.length()").value(0));

		assertThat(jdbcTemplate.queryForObject("SELECT case_id FROM member_collection_item WHERE member_id = ?",
			Long.class, member)).isEqualTo(firstCase);
		assertThat(jdbcTemplate.queryForObject("SELECT acquired_at FROM member_collection_item WHERE member_id = ?",
			Timestamp.class, member).toInstant()).isEqualTo(firstAcquired);
	}

	@Test
	@DisplayName("체험 기록이 없는 회원은 404 EXPERIENCE_NOT_FOUND, 없는 사건이면 404 CASE_NOT_FOUND")
	void notFound() throws Exception {
		long caseId = insertCase("MURDER", "PUBLISHED");
		String json = body(caseId, false, item("illustration", illustration(caseId, "origin")));
		long member = registerMember();

		record(member, json).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("EXPERIENCE_NOT_FOUND"));
		record(member, body(Long.MAX_VALUE, false, item("illustration", illustration(caseId, "origin"))))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("CASE_NOT_FOUND"));
	}

	@Test
	@DisplayName("손님(토큰 없음)은 401 UNAUTHORIZED, 익명 쿠키는 무시하고 아무것도 저장하지 않는다")
	void guestUnauthorized() throws Exception {
		long caseId = insertCase("MURDER", "PUBLISHED");
		UUID anonymous = UUID.randomUUID();
		insertExperience(anonymous, caseId, 1, "COMPLETED");
		String json = body(caseId, false, item("illustration", illustration(caseId, "origin")));

		record(null, json).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
		var response = mockMvc.perform(post(COLLECTION).cookie(new Cookie(COOKIE, anonymous.toString()))
				.contentType(MediaType.APPLICATION_JSON).content(json))
			.andExpect(status().isUnauthorized()).andReturn().getResponse();
		assertThat(response.getHeader("Set-Cookie")).isNull();
		mockMvc.perform(post(COLLECTION_SEEN).contentType(MediaType.APPLICATION_JSON).content("{\"kind\":\"cast\"}"))
			.andExpect(status().isUnauthorized());
		mockMvc.perform(post(ACHIEVEMENTS_SEEN).contentType(MediaType.APPLICATION_JSON).content("{\"achievementIds\":[\"first-case\"]}"))
			.andExpect(status().isUnauthorized());
		mockMvc.perform(get(MY_PAGE).cookie(new Cookie(COOKIE, anonymous.toString())))
			.andExpect(status().isUnauthorized());
	}

	@Test
	@DisplayName("다른 회원이 완료한 사건으로는 저장하지 못하고(404), 다른 회원의 수집 · 해금은 내 마이페이지에 섞이지 않는다")
	void otherMemberIsolated() throws Exception {
		long caseId = insertCase("MURDER", "PUBLISHED");
		long owner = completedMember(caseId);
		long other = registerMember();
		String json = body(caseId, false, item("illustration", illustration(caseId, "origin")));

		record(other, json).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("EXPERIENCE_NOT_FOUND"));
		record(owner, json).andExpect(status().isOk());
		myPage(owner).andExpect(jsonPath("$.collection.collected").value(1));

		myPage(other)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.collection.collected").value(0))
			.andExpect(jsonPath("$.achievements[0].status").value(not("CLEARED")));
		// 남의 항목 봤음 처리도 내 것만 건드린다
		mockMvc.perform(post(COLLECTION_SEEN).header("Authorization", bearer(other)).contentType(MediaType.APPLICATION_JSON)
				.content("{\"kind\":\"illustration\"}"))
			.andExpect(status().isNoContent());
		myPage(owner).andExpect(jsonPath("$.collection.items[0].isNew").value(true));
		assertThat(countItems(other)).isZero();
	}

	@Test
	@DisplayName("체험을 아직 끝내지 않았으면 409 INVALID_STATE + currentStatus, 아무것도 저장하지 않는다")
	void notCompleted() throws Exception {
		long caseId = insertCase("MURDER", "PUBLISHED");
		long member = registerMember();
		jdbcTemplate.update("INSERT INTO experience (member_id, case_id, attempt_no, status) VALUES (?, ?, 1, 'REVIEWING')",
			member, caseId);

		record(member, body(caseId, false, item("illustration", illustration(caseId, "origin"))))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("INVALID_STATE"))
			.andExpect(jsonPath("$.currentStatus").value("REVIEWING"));
		assertThat(countItems(member)).isZero();
	}

	@Test
	@DisplayName("형식이 틀리면 400 VALIDATION_ERROR (모르는 종류 · 대문자 id · 빈 목록 · 21개 이상)")
	void validation() throws Exception {
		long caseId = insertCase("MURDER", "PUBLISHED");
		long member = completedMember(caseId);

		record(member, body(caseId, false, item("background", "bg-1")))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
			.andExpect(jsonPath("$.details[0].reason").value("Pattern"));
		record(member, body(caseId, false, item("illustration", "Case-1"))).andExpect(status().isBadRequest());
		record(member, body(caseId, false)).andExpect(status().isBadRequest());
		String[] many = IntStream.rangeClosed(1, 21).mapToObj(i -> item("cast", "naero:role" + i)).toArray(String[]::new);
		record(member, body(caseId, false, many)).andExpect(status().isBadRequest());
	}

	@Test
	@DisplayName("일러스트가 사건당 상한(5)을 넘으면 409 COLLECTION_LIMIT_EXCEEDED, 아무것도 저장하지 않는다. 이미 가진 것은 세지 않는다")
	void illustrationLimit() throws Exception {
		long caseId = insertCase("MURDER", "PUBLISHED");
		long otherCase = insertCase("FRAUD", "PUBLISHED");
		long member = completedMember(caseId);
				// 목록에 있는 그림 6장: 이 사건 3장 + 다른 공개 사건 3장(그림별 key라 받지만 사건당 5장까지)
		String[] six = concat(illustrations(caseId), illustrations(otherCase));

		record(member, body(caseId, false, six))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("COLLECTION_LIMIT_EXCEEDED"))
			.andExpect(jsonPath("$.details[0].field").value("items.kind"))
			.andExpect(jsonPath("$.details[0].reason").value("illustration"));
		assertThat(countItems(member)).isZero();

		String[] five = Arrays.copyOf(six, 5);
		record(member, body(caseId, false, five)).andExpect(status().isOk());
		record(member, body(caseId, false, five)).andExpect(status().isOk());	// 이미 가진 5장은 세지 않는다
		record(member, body(caseId, false, six[5])).andExpect(status().isConflict());
	}

	@Test
	@DisplayName("목록에 없는 항목은 400 VALIDATION_ERROR(items[i].itemId · NOT_COLLECTIBLE), 섞여 있으면 하나도 저장하지 않는다 (BE-136)")
	void notCollectible() throws Exception {
		long caseId = insertCase("MURDER", "PUBLISHED");
		long reviewCase = insertCase("FRAUD", "REVIEW");
		long member = completedMember(caseId);
		
		record(member, body(caseId, false,
			item("illustration", illustration(caseId, "origin")),		// 0 — 목록에 있다
			item("illustration", illustration(caseId, "verdict")),	// 1 — 없는 칸 이름
			item("illustration", "shared-courtroom"),					// 2 — 사건 그림 규칙이 아니다
			item("illustration", illustration(reviewCase, "origin")),	// 3 — 공개 전 사건
			item("illustration", "case-999999999999-origin"),			// 4 — 없는 사건
			item("cast", "naero:judge"),								// 5 — 목록에 있다
			item("cast", "naero:role1"),								// 6 — 그림이 없는 역할
			item("cast", "never:owned")))								// 7 — 모르는 인물
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
			.andExpect(jsonPath("$.details[*].field").value(contains(
				"items[1].itemId", "items[2].itemId", "items[3].itemId", "items[4].itemId", "items[6].itemId",
				"items[7].itemId")))
			.andExpect(jsonPath("$.details[*].reason", everyItem(is("NOT_COLLECTIBLE"))));
		assertThat(countItems(member)).isZero();	// 목록에 있는 0 · 5번도 저장하지 않는다
	}

	@Test
	@DisplayName("5장을 모으면 collect-5가 해금되고 \"새로 열림\", 도전과제 봤음(P-11) 뒤에는 isNew false")
	void collectAchievementUnlocks() throws Exception {
		long caseId = insertCase("MURDER", "PUBLISHED");
		long member = completedMember(caseId);

		record(member, body(caseId, false, concat(illustrations(caseId), new String[] {item("cast", "naero:judge")})))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.newlyUnlocked", not(hasItem("collect-5"))));
		record(member, body(caseId, false, item("cast", "nambul:judge")))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.collected").value(5))
			.andExpect(jsonPath("$.newlyUnlocked", hasItem("collect-5")));

		myPage(member)
			.andExpect(jsonPath("$.achievements[3].id").value("collect-5"))
			.andExpect(jsonPath("$.achievements[3].status").value("CLEARED"))
			.andExpect(jsonPath("$.achievements[3].isNew").value(true))
			.andExpect(jsonPath("$.achievements[3].unlockedAt").isNotEmpty())
			.andExpect(jsonPath("$.unlocked.backgrounds", hasItem("news")));	// 수집 보상이 열린다

		mockMvc.perform(post(ACHIEVEMENTS_SEEN).header("Authorization", bearer(member)).contentType(MediaType.APPLICATION_JSON)
				.content("{\"achievementIds\":[\"collect-5\",\"unknown-id\"]}"))
			.andExpect(status().isNoContent());
		myPage(member)
			.andExpect(jsonPath("$.achievements[3].isNew").value(false))
			.andExpect(jsonPath("$.achievements[3].status").value("CLEARED"));
	}

	@Test
	@DisplayName("마이페이지 조회가 막 달성한 도전과제를 기록한다: first-case가 \"새로 열림\"으로 나온다")
	void myPageRecordsUnlock() throws Exception {
		long caseId = insertCase("MURDER", "PUBLISHED");
		long member = completedMember(caseId);

		myPage(member)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.achievements[0].id").value("first-case"))
			.andExpect(jsonPath("$.achievements[0].status").value("CLEARED"))
			.andExpect(jsonPath("$.achievements[0].isNew").value(true))
			.andExpect(jsonPath("$.achievements[0].unlockedAt").isNotEmpty());

		assertThat(jdbcTemplate.queryForObject(
			"SELECT count(*) FROM member_achievement_unlock WHERE member_id = ? AND achievement_id = 'first-case'",
			Integer.class, member)).isOne();
		myPage(member);	// 다시 조회해도 한 줄
		assertThat(jdbcTemplate.queryForObject(
			"SELECT count(*) FROM member_achievement_unlock WHERE member_id = ?", Integer.class, member)).isOne();
	}

	@Test
	@DisplayName("컬렉션 봤음(P-10): 항목 지정 · 종류 전부 모두 204, 본 항목은 isNew false")
	void markSeen() throws Exception {
		long caseId = insertCase("MURDER", "PUBLISHED");
		long member = completedMember(caseId);
		String origin = illustration(caseId, "origin");
		record(member, body(caseId, false, item("illustration", origin), item("cast", "naero:judge"),
			item("cast", "nambul:judge"))).andExpect(status().isOk());

		mockMvc.perform(post(COLLECTION_SEEN).header("Authorization", bearer(member)).contentType(MediaType.APPLICATION_JSON)
				.content("{\"items\":[" + item("illustration", origin) + "]}"))
			.andExpect(status().isNoContent());
		myPage(member)
			.andExpect(jsonPath("$.collection.items[?(@.itemId == '" + origin + "')].isNew", contains(false)))
			.andExpect(jsonPath("$.collection.items[?(@.kind == 'cast')].isNew", contains(true, true)));

		mockMvc.perform(post(COLLECTION_SEEN).header("Authorization", bearer(member)).contentType(MediaType.APPLICATION_JSON)
				.content("{\"kind\":\"cast\"}"))
			.andExpect(status().isNoContent());
		myPage(member)
			.andExpect(jsonPath("$.collection.items[?(@.kind == 'cast')].isNew", contains(false, false)));

		mockMvc.perform(post(COLLECTION_SEEN).header("Authorization", bearer(member)).contentType(MediaType.APPLICATION_JSON)
				.content("{\"kind\":\"cast\",\"items\":[" + item("cast", "naero:judge") + "]}"))
			.andExpect(status().isBadRequest());	// 둘 중 하나만
	}

	@Test
	@DisplayName("컬렉션 봤음(P-10): 종류가 섞인 항목 지정도 지정한 항목만 본 것으로 (종류별 일괄 UPDATE, 중복 · 없는 항목 무시, PR #335 리뷰)")
	void markSeen_mixedKinds_marksOnlyListedItems() throws Exception {
		long caseId = insertCase("MURDER", "PUBLISHED");
		long member = completedMember(caseId);
				String origin = illustration(caseId, "origin");
		String conclusion = illustration(caseId, "conclusion");
		record(member, body(caseId, false, item("illustration", origin), item("illustration", conclusion),
			item("cast", "naero:judge"), item("cast", "nambul:judge"))).andExpect(status().isOk());

		// 일러스트 1 · 의상 1을 한 요청에, 같은 항목 중복 · 얻지 않은 항목도 섞어 보낸다
		mockMvc.perform(post(COLLECTION_SEEN).header("Authorization", bearer(member)).contentType(MediaType.APPLICATION_JSON)
				.content("{\"items\":[" + String.join(",", item("illustration", origin),
					item("cast", "naero:judge"), item("cast", "naero:judge"), item("cast", "never:owned")) + "]}"))
			.andExpect(status().isNoContent());

		myPage(member)
			.andExpect(jsonPath("$.collection.items[?(@.itemId == '" + origin + "')].isNew", contains(false)))
			.andExpect(jsonPath("$.collection.items[?(@.itemId == 'naero:judge')].isNew", contains(false)))
			// 지정하지 않은 항목은 그대로 새 항목
			.andExpect(jsonPath("$.collection.items[?(@.itemId == '" + conclusion + "')].isNew", contains(true)))
			.andExpect(jsonPath("$.collection.items[?(@.itemId == 'nambul:judge')].isNew", contains(true)));
		assertThat(countItems(member)).isEqualTo(4);	// 얻지 않은 항목은 새로 생기지 않는다
	}

	@Test
	@DisplayName("백필: 획득 시각은 그 사건의 첫 완료 시각, \"본 것\"으로 저장하고 새로 열린 도전과제도 \"본 것\"")
	void backfill() throws Exception {
		long caseId = insertCase("MURDER", "PUBLISHED");
		long member = completedMember(caseId);
		Instant completedAt = Instant.parse("2026-10-01T03:00:00Z");
		jdbcTemplate.update("UPDATE experience SET completed_at = ? WHERE member_id = ?",
			Timestamp.from(completedAt), member);
		
		record(member, body(caseId, true, concat(illustrations(caseId),
			new String[] {item("cast", "naero:judge"), item("cast", "nambul:judge")})))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.added[0].isNew").value(false))
			.andExpect(jsonPath("$.added[0].acquiredAt").value("2026-10-01T12:00:00+09:00"))
			.andExpect(jsonPath("$.newlyUnlocked", hasItem("collect-5")));

		List<Instant> acquired = jdbcTemplate.queryForList(
			"SELECT acquired_at FROM member_collection_item WHERE member_id = ?", Timestamp.class, member)
			.stream().map(Timestamp::toInstant).collect(Collectors.toList());
		assertThat(acquired).hasSize(5).allSatisfy(at -> assertThat(at).isEqualTo(completedAt));
		myPage(member)
			.andExpect(jsonPath("$.collection.items[0].isNew").value(false))
			.andExpect(jsonPath("$.achievements[3].status").value("CLEARED"))
			.andExpect(jsonPath("$.achievements[3].isNew").value(false));
	}
}
