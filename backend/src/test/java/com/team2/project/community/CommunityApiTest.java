package com.team2.project.community;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.team2.project.support.ApiIntegrationTest;
import com.team2.project.support.FakeMemberTokens;
import jakarta.servlet.http.Cookie;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** 커뮤니티 API C-1 ~ C-7 (API 명세 12장, BE-115). 메인(C-6) 캐시는 끄고 본다(캐시는 CommunityHomeCacheTest) */
@Import(FakeMemberTokens.class)
@TestPropertySource(properties = "community.home-cache-ttl=0")
class CommunityApiTest extends ApiIntegrationTest {

	private static final String POSTS = "/api/v1/community/posts";
	private static final String HOME = "/api/v1/community/home";
	private static final String SHAREABLE = "/api/v1/community/shareable-verdict";

	/** 시험 회원: 회원 ID + 그 회원에 연결된 익명 ID(쿠키) */
	private record User(long memberId, UUID anonymousId) {

		String bearer() {
			return ApiIntegrationTest.bearer(memberId);
		}

		Cookie cookie() {
			return new Cookie(COOKIE, anonymousId.toString());
		}
	}

	private final List<Long> memberIds = new ArrayList<>();
	private final List<UUID> guestIds = new ArrayList<>();
	private long caseId;
	private int factorOrder;	// 같은 사건에 판결을 여러 개 붙여도 요소 순서가 겹치지 않게

	@BeforeEach
	void setUpCase() {
		caseId = insertCase("MURDER", "PUBLISHED");
	}

	@AfterEach
	void deleteCommunityData() {
		// 부모의 정리(사건 · 체험 · 회원)보다 먼저 돈다. 회원에 연결된 익명 ID와 그 데이터부터 지운다
		for (Long memberId : memberIds) {
			jdbcTemplate.update("DELETE FROM community_like WHERE post_id IN (SELECT id FROM community_post WHERE member_id = ?)",
				memberId);
			jdbcTemplate.update("DELETE FROM community_comment WHERE post_id IN (SELECT id FROM community_post WHERE member_id = ?)",
				memberId);
			jdbcTemplate.update("DELETE FROM community_comment WHERE member_id = ?", memberId);
			jdbcTemplate.update("DELETE FROM community_post WHERE member_id = ?", memberId);
			for (UUID id : jdbcTemplate.queryForList("SELECT id FROM anonymous_user WHERE member_id = ?", UUID.class, memberId)) {
				deleteAnonymous(id);
			}
		}
		for (UUID id : guestIds) {
			deleteAnonymous(id);
		}
		memberIds.clear();
		guestIds.clear();
	}

	/** 익명 ID와 그 데이터(체험 · 판결). 시험이 만든 글은 부모가 회원과 함께 지운다 */
	private void deleteAnonymous(UUID id) {
		jdbcTemplate.update("DELETE FROM community_like WHERE post_id IN (SELECT id FROM community_post WHERE anonymous_user_id = ?)", id);
		jdbcTemplate.update("DELETE FROM community_comment WHERE anonymous_user_id = ?", id);
		jdbcTemplate.update("DELETE FROM community_post WHERE anonymous_user_id = ?", id);
		jdbcTemplate.update("""
			DELETE FROM judgment_factor WHERE judgment_id IN
			    (SELECT j.id FROM judgment j JOIN experience e ON e.id = j.experience_id WHERE e.anonymous_user_id = ?)""", id);
		jdbcTemplate.update(
			"DELETE FROM judgment WHERE experience_id IN (SELECT id FROM experience WHERE anonymous_user_id = ?)", id);
		jdbcTemplate.update("DELETE FROM experience WHERE anonymous_user_id = ?", id);
		jdbcTemplate.update("DELETE FROM anonymous_user WHERE id = ?", id);
	}

	/** 회원 + 그 회원에 연결된 익명 ID(글 · 댓글의 anonymous_user_id 칸용, 명함 · 경험치는 회원 키라 쓰지 않는다) */
	private User newUser(String provider, String nickname) {
		long memberId = registerMember();
		memberIds.add(memberId);
		if (nickname != null) {
			jdbcTemplate.update("UPDATE member SET provider = ?, nickname = ? WHERE id = ?", provider, nickname, memberId);
		}
		UUID anonymousId = UUID.randomUUID();
		jdbcTemplate.update("INSERT INTO anonymous_user (id, member_id) VALUES (?, ?)", anonymousId, memberId);
		return new User(memberId, anonymousId);
	}

	private User newUser(String nickname) {
		return newUser("LOCAL", nickname);
	}

	private MockHttpServletRequestBuilder as(MockHttpServletRequestBuilder request, User user) {
		return user == null ? request : request.header(HttpHeaders.AUTHORIZATION, user.bearer()).cookie(user.cookie());
	}

	private ResultActions write(User user, String json) throws Exception {
		return mockMvc.perform(as(post(POSTS), user).contentType(MediaType.APPLICATION_JSON).content(json));
	}

	private long writePost(User user, String title) throws Exception {
		String body = write(user, """
			{"category":"FREE","caseId":%d,"title":"%s","body":"본문"}""".formatted(caseId, title))
			.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
		return ((Number) JsonPath.read(body, "$.postId")).longValue();
	}

	private ResultActions like(User user, long postId) throws Exception {
		return mockMvc.perform(as(post(POSTS + "/" + postId + "/like"), user));
	}

	private ResultActions comment(User user, long postId, String json) throws Exception {
		return mockMvc.perform(as(post(POSTS + "/" + postId + "/comments"), user)
			.contentType(MediaType.APPLICATION_JSON).content(json));
	}

	private int xp(User user, String source) {
		return jdbcTemplate.queryForObject(
			"SELECT coalesce(sum(amount), 0) FROM member_xp_event WHERE member_id = ? AND source = ?",
			Integer.class, user.memberId(), source);
	}

	private int countPosts(User user) {
		return jdbcTemplate.queryForObject("SELECT count(*) FROM community_post WHERE member_id = ?", Integer.class,
			user.memberId());
	}

	/** 회원이 판결을 확정한 체험 + 내 최종 판결(징역 60개월, 높이는 요소 1 · 낮추는 요소 2) */
	private void confirmVerdict(User user, long targetCaseId) {
		Long experienceId = jdbcTemplate.queryForObject("""
			INSERT INTO experience (member_id, case_id, attempt_no, status, completed_at)
			VALUES (?, ?, 1, 'VERDICT_CONFIRMED', now()) RETURNING id""", Long.class, user.memberId(), targetCaseId);
		attachFinalJudgment(experienceId, targetCaseId);
	}

	/** 손님(쿠키의 익명 ID)이 판결을 확정한 체험 + 최종 판결. 만든 익명 ID는 시험이 끝나면 지운다 */
	private UUID confirmGuestVerdict(long targetCaseId) {
		UUID guestId = UUID.randomUUID();
		guestIds.add(guestId);
		jdbcTemplate.update("INSERT INTO anonymous_user (id) VALUES (?)", guestId);
		Long experienceId = jdbcTemplate.queryForObject("""
			INSERT INTO experience (anonymous_user_id, case_id, attempt_no, status) VALUES (?, ?, 1, 'VERDICT_CONFIRMED')
			RETURNING id""", Long.class, guestId, targetCaseId);
		attachFinalJudgment(experienceId, targetCaseId);
		return guestId;
	}

	private void attachFinalJudgment(long experienceId, long targetCaseId) {
		Long judgmentId = jdbcTemplate.queryForObject("""
			INSERT INTO judgment (case_id, subject_type, timing, experience_id, penalty_type, prison_months, is_published)
			VALUES (?, 'USER', 'FINAL', ?, 'PRISON', 60, true) RETURNING id""", Long.class, targetCaseId, experienceId);
		String[] directions = {"UP", "DOWN", "DOWN"};
		for (int i = 0; i < directions.length; i++) {
			long factorId = insertFactor(targetCaseId, "OVERVIEW", "요소 " + i, null, ++factorOrder);
			jdbcTemplate.update("INSERT INTO judgment_factor (judgment_id, factor_id, direction) VALUES (?, ?, ?)",
				judgmentId, factorId, directions[i]);
		}
	}

	// ---------- 로그인 ----------

	@Test
	@DisplayName("쓰기(글 · 좋아요 · 댓글)는 로그인해야 한다 — 로그인 전이면 401 UNAUTHORIZED이고 저장하지 않는다")
	void write_requiresLogin() throws Exception {
		User author = newUser("글쓴이");
		long postId = writePost(author, "로그인 확인 글");

		write(null, "{\"category\":\"FREE\",\"title\":\"제목\",\"body\":\"본문\"}")
			.andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
		like(null, postId).andExpect(status().isUnauthorized());
		comment(null, postId, "{\"body\":\"댓글\"}").andExpect(status().isUnauthorized());
		assertThat(jdbcTemplate.queryForObject("SELECT like_count + comment_count FROM community_post WHERE id = ?",
			Integer.class, postId)).isZero();
	}

	@Test
	@DisplayName("읽기(목록 · 상세 · 메인)는 로그인 없이 된다 — liked · mine은 false")
	void read_withoutLogin() throws Exception {
		User author = newUser("글쓴이");
		long postId = writePost(author, "누구나 읽는 글");

		mockMvc.perform(get(POSTS).param("caseId", String.valueOf(caseId)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.posts[0].postId").value(postId))
			.andExpect(jsonPath("$.posts[0].liked").value(false))
			.andExpect(jsonPath("$.posts[0].mine").value(false));
		mockMvc.perform(get(POSTS + "/" + postId)).andExpect(status().isOk()).andExpect(jsonPath("$.comments").isArray());
		mockMvc.perform(get(HOME)).andExpect(status().isOk());
	}

	// ---------- 글쓰기 ----------

	@Test
	@DisplayName("C-3: 글을 쓰면 201 + 글쓴이 명함(기본값) · mine true, 글 작성 경험치 30을 준다")
	void create_returnsPostWithAuthorCard() throws Exception {
		User author = newUser("새벽의 배심원");
		write(author, """
			{"category":"QUESTION","caseId":%d,"title":"  반성은 어떻게 보나요?  ","body":"첫 줄\\n둘째 줄","images":[]}
			""".formatted(caseId))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.category").value("QUESTION"))
			.andExpect(jsonPath("$.caseId").value(caseId))
			.andExpect(jsonPath("$.caseTitle").isString())
			.andExpect(jsonPath("$.title").value("반성은 어떻게 보나요?"))	// 앞뒤 공백을 뺀다
			.andExpect(jsonPath("$.body").value("첫 줄\n둘째 줄"))
			.andExpect(jsonPath("$.images", hasSize(0)))
			.andExpect(jsonPath("$.verdict").isEmpty())
			.andExpect(jsonPath("$.likeCount").value(0))
			.andExpect(jsonPath("$.commentCount").value(0))
			.andExpect(jsonPath("$.mine").value(true))
			.andExpect(jsonPath("$.comments").doesNotExist())	// 상세에만 있다
			.andExpect(jsonPath("$.author.nickname").value("새벽의 배심원"))
			.andExpect(jsonPath("$.author.intro").value(""))
			.andExpect(jsonPath("$.author.level").value(1))
			.andExpect(jsonPath("$.author.job.code").isString())
			.andExpect(jsonPath("$.author.cardLine.line").value("type"))
			.andExpect(jsonPath("$.author.cardLine.showLevel").value(false))
			.andExpect(jsonPath("$.author.completedCount").value(0))
			.andExpect(jsonPath("$.author.profileCard.frame").isString())
			.andExpect(jsonPath("$.author.shownType").isEmpty())
			.andExpect(jsonPath("$.createdAt").value(org.hamcrest.Matchers.endsWith("+09:00")));
		assertThat(xp(author, "POST")).isEqualTo(30);
	}

	@Test
	@DisplayName("C-3: 글 작성 경험치는 하루 3건까지 — 네 번째 글은 쓰이지만 경험치는 주지 않는다")
	void create_dailyXpCap() throws Exception {
		User author = newUser("부지런한 판사");
		for (int i = 1; i <= 4; i++) {
			writePost(author, "글 " + i);
		}
		assertThat(countPosts(author)).isEqualTo(4);
		assertThat(xp(author, "POST")).isEqualTo(90);
	}

	@Test
	@DisplayName("C-3: 형식이 틀리면 400 VALIDATION_ERROR — 틀린 칸을 모두 details에 담고 아무것도 저장하지 않는다")
	void create_invalid_rejected() throws Exception {
		User author = newUser("글쓴이");
		write(author, """
			{"category":"NEWS","caseId":-1,"title":"   ","body":"%s","images":["data:image/png;base64,AAAA"]}
			""".formatted("가".repeat(2001)))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
			.andExpect(jsonPath("$.details[*].field", containsInAnyOrder("category", "caseId", "title", "body", "images")));
		write(author, "{\"category\":\"FREE\",\"title\":\"제목\\u0000\",\"body\":\"본문\"}")	// NUL · 제어 문자
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.details[0].field").value("title"));
		write(author, "{\"category\":\"FREE\",\"title\":\"" + "가".repeat(61) + "\",\"body\":\"본문\"}")
			.andExpect(status().isBadRequest());
		assertThat(countPosts(author)).isZero();
		assertThat(xp(author, "POST")).isZero();
	}

	@Test
	@DisplayName("C-3: 닉네임이 없는 회원(카카오)은 회원 번호로 만든 고정 이름 '방청객 {회원 ID}'로 보인다")
	void create_kakaoMember_autoName() throws Exception {
		User kakao = newUser("KAKAO", null);
		write(kakao, "{\"category\":\"FREE\",\"title\":\"제목\",\"body\":\"본문\"}")
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.author.nickname").value("방청객 " + kakao.memberId()));
	}

	@Test
	@DisplayName("명함: 한 줄 소개 · 카드 아래 줄 설정(P-8)이 그대로 보인다")
	void authorCard_reflectsProfileSettings() throws Exception {
		User author = newUser("말없는 판사");
		jdbcTemplate.update("""
			INSERT INTO member_profile_setting (member_id, bio, card_line, card_level) VALUES (?, '말은 적게', 'job', true)""",
			author.memberId());
		write(author, "{\"category\":\"FREE\",\"title\":\"제목\",\"body\":\"본문\"}")
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.author.intro").value("말은 적게"))
			.andExpect(jsonPath("$.author.cardLine.line").value("job"))
			.andExpect(jsonPath("$.author.cardLine.showLevel").value(true));
	}

	@Test
	@DisplayName("글에 남기는 익명 ID: 쿠키가 이 회원 것이 아니면 회원에 연결된 익명 ID를 남긴다. 경험치는 회원이 받는다")
	void writer_cookieOfOtherMember_usesLinkedAnonymousId() throws Exception {
		User author = newUser("글쓴이");
		User other = newUser("다른 사람");
		mockMvc.perform(post(POSTS).header(HttpHeaders.AUTHORIZATION, author.bearer()).cookie(other.cookie())
				.contentType(MediaType.APPLICATION_JSON).content("{\"category\":\"FREE\",\"title\":\"제목\",\"body\":\"본문\"}"))
			.andExpect(status().isCreated());
		assertThat(jdbcTemplate.queryForObject("SELECT anonymous_user_id FROM community_post WHERE member_id = ?",
			UUID.class, author.memberId())).isEqualTo(author.anonymousId());
		assertThat(xp(author, "POST")).isEqualTo(30);
		assertThat(xp(other, "POST")).isZero();
	}

	@Test
	@DisplayName("글 · 댓글에 익명 ID가 없어도(회원만 작성자) 상세 · 목록 · 메인 랭킹의 명함이 나오고 좋아요 경험치도 회원이 받는다")
	void authorWithoutAnonymousId_cardAndLike() throws Exception {
		User author = newUser("익명 없는 글쓴이");
		User reader = newUser("읽는 사람");
		long postId = writePost(author, "익명 ID 없음");
		comment(author, postId, "{\"body\":\"댓글\"}").andExpect(status().isCreated());
		jdbcTemplate.update("UPDATE community_post SET anonymous_user_id = NULL WHERE member_id = ?", author.memberId());
		jdbcTemplate.update("UPDATE community_comment SET anonymous_user_id = NULL WHERE member_id = ?", author.memberId());

		mockMvc.perform(as(get(POSTS + "/" + postId), reader)).andExpect(status().isOk())
			.andExpect(jsonPath("$.author.nickname").value("익명 없는 글쓴이"))
			.andExpect(jsonPath("$.comments[0].author.nickname").value("익명 없는 글쓴이"));
		mockMvc.perform(as(get(POSTS), reader)).andExpect(status().isOk())
			.andExpect(jsonPath("$.posts[?(@.postId == " + postId + ")].author.nickname").value(org.hamcrest.Matchers.contains("익명 없는 글쓴이")));
		like(reader, postId).andExpect(status().isOk());
		assertThat(xp(author, "LIKE_RECEIVED")).isPositive();

		String home = mockMvc.perform(get(HOME)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
		assertThat((List<String>) JsonPath.read(home, "$.ranking[*].nickname")).doesNotHaveDuplicates();
	}

	// ---------- 판결 공유 ----------

	@Test
	@DisplayName("C-3 · C-7: 판결을 확정하기 전이면 붙일 판결이 없다 — C-7 204, shareVerdict 글은 400")
	void shareVerdict_beforeConfirm_rejected() throws Exception {
		User author = newUser("글쓴이");
		mockMvc.perform(as(get(SHAREABLE), author)).andExpect(status().isNoContent());
		write(author, "{\"category\":\"VERDICT\",\"title\":\"제목\",\"body\":\"본문\",\"shareVerdict\":true}")
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.details[0].field").value("shareVerdict"))
			.andExpect(jsonPath("$.details[0].reason").value("VERDICT_NOT_CONFIRMED"));
		assertThat(countPosts(author)).isZero();
	}

	@Test
	@DisplayName("C-3 · C-7: 판결을 확정했으면 서버가 내 판결을 붙이고 글의 사건을 맞춘다 — 공유 보너스 20은 사건당 1회")
	void shareVerdict_attachesMyVerdict() throws Exception {
		User author = newUser("판결한 사람");
		confirmVerdict(author, caseId);

		mockMvc.perform(as(get(SHAREABLE), author))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.caseId").value(caseId))
			.andExpect(jsonPath("$.verdict.penaltyType").value("PRISON"))
			.andExpect(jsonPath("$.verdict.prisonMonths").value(60))
			.andExpect(jsonPath("$.verdict.upCount").value(1))
			.andExpect(jsonPath("$.verdict.downCount").value(2));
		for (int i = 0; i < 2; i++) {
			write(author, "{\"category\":\"VERDICT\",\"title\":\"내 판결\",\"body\":\"본문\",\"shareVerdict\":true}")
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.caseId").value(caseId))	// caseId를 보내지 않아도 판결의 사건
				.andExpect(jsonPath("$.verdict.prisonMonths").value(60))
				.andExpect(jsonPath("$.viewerJudged").value(true));
		}
		assertThat(xp(author, "SHARE_POST")).isEqualTo(20);
	}

	@Test
	@DisplayName("C-3: 다른 사건을 고르고 판결을 붙이면 그 사건의 확정 판결이 없어 400")
	void shareVerdict_otherCase_rejected() throws Exception {
		User author = newUser("판결한 사람");
		confirmVerdict(author, caseId);
		long otherCase = insertCase("FRAUD", "PUBLISHED");
		write(author, """
			{"category":"VERDICT","caseId":%d,"title":"제목","body":"본문","shareVerdict":true}""".formatted(otherCase))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.details[0].field").value("shareVerdict"));
	}

	@Test
	@DisplayName("viewerJudged: 로그인한 보는 사람이 그 사건 판결을 확정했을 때만 true")
	void viewerJudged_followsViewer() throws Exception {
		User author = newUser("글쓴이");
		long postId = writePost(author, "판결 이야기");
		User viewer = newUser("본 사람");
		confirmVerdict(viewer, caseId);

		mockMvc.perform(get(POSTS + "/" + postId)).andExpect(jsonPath("$.viewerJudged").value(false));
		mockMvc.perform(as(get(POSTS + "/" + postId), viewer)).andExpect(jsonPath("$.viewerJudged").value(true));
		mockMvc.perform(as(get(POSTS + "/" + postId), author)).andExpect(jsonPath("$.viewerJudged").value(false));
	}

	@Test
	@DisplayName("viewerJudged: 손님(토큰 없이 쿠키만)은 쿠키의 익명 ID가 가진 체험으로 본다")
	void viewerJudged_guestUsesOwnCookie() throws Exception {
		User author = newUser("글쓴이");
		long postId = writePost(author, "판결 이야기");
		UUID guestId = confirmGuestVerdict(caseId);

		mockMvc.perform(get(POSTS + "/" + postId).cookie(new Cookie(COOKIE, guestId.toString())))
			.andExpect(jsonPath("$.viewerJudged").value(true));
		mockMvc.perform(get(POSTS + "/" + postId).cookie(new Cookie(COOKIE, UUID.randomUUID().toString())))
			.andExpect(jsonPath("$.viewerJudged").value(false));
		mockMvc.perform(get(POSTS + "/" + postId)).andExpect(jsonPath("$.viewerJudged").value(false));
	}

	@Test
	@DisplayName("로그인한 보는 사람의 viewerJudged · 붙일 판결은 회원의 체험에서 온다 — 쿠키의 익명 ID 체험은 보지 않는다")
	void loggedInViewer_usesMemberExperiencesNotCookie() throws Exception {
		User author = newUser("글쓴이");
		long postId = writePost(author, "판결 이야기");
		UUID guestId = confirmGuestVerdict(caseId);
		Cookie guestCookie = new Cookie(COOKIE, guestId.toString());
		User withoutVerdict = newUser("체험 없는 회원");
		User judged = newUser("판결한 회원");
		confirmVerdict(judged, caseId);

		// 쿠키에 판결이 있어도 회원에게 없으면 없다
		mockMvc.perform(get(POSTS + "/" + postId).header(HttpHeaders.AUTHORIZATION, withoutVerdict.bearer()).cookie(guestCookie))
			.andExpect(jsonPath("$.viewerJudged").value(false));
		mockMvc.perform(get(SHAREABLE).header(HttpHeaders.AUTHORIZATION, withoutVerdict.bearer()).cookie(guestCookie))
			.andExpect(status().isNoContent());
		// 쿠키에 판결이 없어도 회원에게 있으면 있다
		mockMvc.perform(get(POSTS + "/" + postId).header(HttpHeaders.AUTHORIZATION, judged.bearer())
				.cookie(new Cookie(COOKIE, UUID.randomUUID().toString())))
			.andExpect(jsonPath("$.viewerJudged").value(true));
		mockMvc.perform(get(SHAREABLE).header(HttpHeaders.AUTHORIZATION, judged.bearer()))
			.andExpect(status().isOk()).andExpect(jsonPath("$.caseId").value(caseId));
	}

	// ---------- 좋아요 ----------

	@Test
	@DisplayName("C-4: 누를 때마다 켜고 끈다. 켤 때 누른 사람 2 · 글쓴이 3, 껐다 다시 켜도 다시 주지 않는다")
	void like_toggle_awardsOnce() throws Exception {
		User author = newUser("글쓴이");
		User liker = newUser("누른 사람");
		long postId = writePost(author, "좋아요 받을 글");

		like(liker, postId).andExpect(status().isOk())
			.andExpect(jsonPath("$.liked").value(true)).andExpect(jsonPath("$.likeCount").value(1));
		like(liker, postId).andExpect(jsonPath("$.liked").value(false)).andExpect(jsonPath("$.likeCount").value(0));
		like(liker, postId).andExpect(jsonPath("$.liked").value(true)).andExpect(jsonPath("$.likeCount").value(1));

		assertThat(xp(liker, "LIKE_GIVEN")).isEqualTo(2);
		assertThat(xp(author, "LIKE_RECEIVED")).isEqualTo(3);
		mockMvc.perform(as(get(POSTS + "/" + postId), liker)).andExpect(jsonPath("$.liked").value(true));
		mockMvc.perform(as(get(POSTS + "/" + postId), author)).andExpect(jsonPath("$.liked").value(false));
	}

	@Test
	@DisplayName("C-4: 내 글에 누른 좋아요는 수만 세고 경험치는 주지 않는다")
	void like_ownPost_noXp() throws Exception {
		User author = newUser("글쓴이");
		long postId = writePost(author, "내 글");
		like(author, postId).andExpect(jsonPath("$.likeCount").value(1));
		assertThat(xp(author, "LIKE_GIVEN") + xp(author, "LIKE_RECEIVED")).isZero();
	}

	@Test
	@DisplayName("없는 글에 좋아요 · 댓글 · 상세는 404 POST_NOT_FOUND")
	void unknownPost_notFound() throws Exception {
		User user = newUser("사용자");
		like(user, Long.MAX_VALUE).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("POST_NOT_FOUND"));
		comment(user, Long.MAX_VALUE, "{\"body\":\"댓글\"}").andExpect(status().isNotFound());
		mockMvc.perform(get(POSTS + "/" + Long.MAX_VALUE)).andExpect(status().isNotFound());
	}

	// ---------- 댓글 ----------

	@Test
	@DisplayName("C-5 · C-2: 댓글은 201, 상세에 오래된 순으로 보이고 mine은 보는 사람 기준. 댓글 경험치 10")
	void comment_shownInDetail() throws Exception {
		User author = newUser("글쓴이");
		User commenter = newUser("댓글 단 사람");
		long postId = writePost(author, "댓글 받을 글");

		comment(commenter, postId, "{\"body\":\"  첫 댓글  \"}").andExpect(status().isCreated())
			.andExpect(jsonPath("$.body").value("첫 댓글"))
			.andExpect(jsonPath("$.mine").value(true))
			.andExpect(jsonPath("$.author.nickname").value("댓글 단 사람"));
		comment(author, postId, "{\"body\":\"답글\"}").andExpect(status().isCreated());

		mockMvc.perform(as(get(POSTS + "/" + postId), commenter))
			.andExpect(jsonPath("$.commentCount").value(2))
			.andExpect(jsonPath("$.comments[0].body").value("첫 댓글"))
			.andExpect(jsonPath("$.comments[0].mine").value(true))
			.andExpect(jsonPath("$.comments[1].author.nickname").value("글쓴이"))
			.andExpect(jsonPath("$.comments[1].mine").value(false))
			.andExpect(jsonPath("$.mine").value(false));
		assertThat(xp(commenter, "COMMENT")).isEqualTo(10);
	}

	@Test
	@DisplayName("C-5: 빈 댓글 · 500자 초과는 400")
	void comment_invalid_rejected() throws Exception {
		User author = newUser("글쓴이");
		long postId = writePost(author, "글");
		for (String json : List.of("{\"body\":\"   \"}", "{}", "{\"body\":1}", "{\"body\":\"" + "가".repeat(501) + "\"}")) {
			comment(author, postId, json).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
		}
		assertThat(xp(author, "COMMENT")).isZero();
	}

	// ---------- 목록 ----------

	@Test
	@DisplayName("C-1: 정렬(최신 · 좋아요 · 댓글) · 말머리 거르기 · 쪽(6개씩 늘어남) · hasMore")
	void list_sortFilterPage() throws Exception {
		User author = newUser("글쓴이");
		User liker = newUser("누른 사람");
		List<Long> ids = new ArrayList<>();
		for (int i = 1; i <= 7; i++) {
			ids.add(writePost(author, "글 " + i));
		}
		like(liker, ids.get(2)).andExpect(status().isOk());
		comment(liker, ids.get(4), "{\"body\":\"댓글\"}").andExpect(status().isCreated());
		String caseParam = String.valueOf(caseId);

		mockMvc.perform(get(POSTS).param("caseId", caseParam))
			.andExpect(jsonPath("$.posts", hasSize(6)))
			.andExpect(jsonPath("$.posts[0].postId").value(ids.get(6)))	// 최신순
			.andExpect(jsonPath("$.total").value(7))
			.andExpect(jsonPath("$.hasMore").value(true))
			.andExpect(jsonPath("$.cases[*].caseId", hasItem((int) caseId)));
		mockMvc.perform(get(POSTS).param("caseId", caseParam).param("page", "2"))
			.andExpect(jsonPath("$.posts", hasSize(7))).andExpect(jsonPath("$.hasMore").value(false));
		mockMvc.perform(get(POSTS).param("caseId", caseParam).param("sort", "LIKES"))
			.andExpect(jsonPath("$.posts[0].postId").value(ids.get(2)));
		mockMvc.perform(get(POSTS).param("caseId", caseParam).param("sort", "COMMENTS"))
			.andExpect(jsonPath("$.posts[0].postId").value(ids.get(4)));
		mockMvc.perform(get(POSTS).param("caseId", caseParam).param("category", "QUESTION"))
			.andExpect(jsonPath("$.posts", hasSize(0)));
		mockMvc.perform(get(POSTS).param("sort", "RANDOM")).andExpect(status().isBadRequest());
		mockMvc.perform(get(POSTS).param("category", "NEWS")).andExpect(status().isBadRequest());
	}

	// ---------- 메인 ----------

	@Test
	@DisplayName("C-6: 오늘 이야기가 있는 사건은 이야기 많은 사건에, 글쓴 회원은 레벨 랭킹에 오른다")
	void home_hotCaseAndRanking() throws Exception {
		User author = newUser("랭킹 후보");
		long postId = writePost(author, "이야기 시작");
		comment(author, postId, "{\"body\":\"댓글\"}").andExpect(status().isCreated());
		insertCasePaper(caseId, "시험 헤드라인");

		String body = mockMvc.perform(get(HOME)).andExpect(status().isOk())
			.andReturn().getResponse().getContentAsString();
		List<Integer> hotIds = JsonPath.read(body, "$.hotCases[*].case.caseId");
		if (hotIds.contains((int) caseId)) {	// 시험 DB에 다른 이야기가 많으면 셋 밖일 수 있다
			assertThat((List<Integer>) JsonPath.read(body, "$.hotCases[?(@.case.caseId == " + caseId + ")].talkCount"))
				.containsExactly(2);
			assertThat((List<String>) JsonPath.read(body, "$.hotCases[?(@.case.caseId == " + caseId + ")].case.headline"))
				.containsExactly("시험 헤드라인");
			// 저장된 사진이 없으면 사건 번호 규칙 주소(BE-141)
			assertThat((List<String>) JsonPath.read(body, "$.hotCases[?(@.case.caseId == " + caseId + ")].case.thumbnailUrl"))
				.containsExactly("/images/cases/case-" + caseId + "-reenact.webp");
		}
		assertThat((List<Object>) JsonPath.read(body, "$.recommendedCases[*].talkCount")).isEmpty();
		List<String> ranked = JsonPath.read(body, "$.ranking[*].nickname");
		assertThat(ranked).hasSizeLessThanOrEqualTo(10);
		assertThat((List<Integer>) JsonPath.read(body, "$.ranking[*].rank")).startsWith(1);
	}
}
