package com.team2.project.auth.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.team2.project.common.security.token.AccessTokenVerifier;
import com.team2.project.common.security.token.MemberPrincipal;
import com.team2.project.common.security.token.jwt.RefreshTokenStore;
import com.team2.project.common.web.AnonymousIdCookie;
import com.team2.project.common.web.RefreshTokenCookies;
import com.team2.project.support.ApiIntegrationTest;
import jakarta.servlet.http.Cookie;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * 아이디 회원가입(M-5) · 로그인(M-6) (확장 단계, BE-113 · docs/decisions/BE-113.md)
 * 실제 PostgreSQL · Redis와 보안 체인(Origin 검증)을 거쳐 확인한다. 테스트가 만든 회원 · 익명 기록 · 토큰만 지운다
 */
class LocalAuthControllerTest extends ApiIntegrationTest {

	private static final String SIGNUP = "/api/v1/auth/signup";
	private static final String LOGIN = "/api/v1/auth/login";
	private static final String REISSUE = "/api/v1/auth/token/reissue";
	private static final String SAME_ORIGIN = "http://localhost";
	private static final String OTHER_ORIGIN = "https://evil.example.com";
	private static final String PASSWORD = "password123";

	@Autowired
	private RefreshTokenStore refreshTokens;

	@Autowired
	private AccessTokenVerifier accessTokenVerifier;

	private final List<String> loginIds = new ArrayList<>();
	private final List<UUID> anonymousIds = new ArrayList<>();
	private final List<String> issuedRefreshTokens = new ArrayList<>();

	@AfterEach
	void cleanUp() {
		issuedRefreshTokens.forEach(refreshTokens::delete);
		for (UUID id : anonymousIds) {
			jdbcTemplate.update("DELETE FROM anonymous_user WHERE id = ?", id);
		}
		for (String loginId : loginIds) {
			List<Long> memberIds = jdbcTemplate.queryForList(
				"SELECT member_id FROM member_password WHERE login_id = ?", Long.class, loginId);
			for (Long memberId : memberIds) {
				jdbcTemplate.update("DELETE FROM anonymous_user WHERE member_id = ?", memberId);
				jdbcTemplate.update("DELETE FROM member_password WHERE member_id = ?", memberId);
				jdbcTemplate.update("DELETE FROM member WHERE id = ?", memberId);
			}
		}
	}

	/** 테스트마다 겹치지 않는 아이디(소문자, 4~20자) */
	private String newLoginId() {
		String id = "t" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
		loginIds.add(id);
		return id;
	}

	private static String signupBody(String id, String password, String nickname) {
		return """
			{"id":"%s","password":"%s","nickname":"%s"}""".formatted(id, password, nickname);
	}

	private static String loginBody(String id, String password) {
		return """
			{"id":"%s","password":"%s"}""".formatted(id, password);
	}

	private static MockHttpServletRequestBuilder json(String path, String body) {
		return post(path).header(HttpHeaders.ORIGIN, SAME_ORIGIN).contentType(MediaType.APPLICATION_JSON).content(body);
	}

	private MvcResult signup(String id, String nickname) throws Exception {
		MvcResult result = mockMvc.perform(json(SIGNUP, signupBody(id, PASSWORD, nickname)))
			.andExpect(status().isOk())
			.andReturn();
		remember(result);
		return result;
	}

	/** 응답이 심은 쿠키 값 (없으면 empty) */
	private static Optional<String> cookie(MvcResult result, String name) {
		return result.getResponse().getHeaders(HttpHeaders.SET_COOKIE).stream()
			.filter(header -> header.startsWith(name + "="))
			.map(header -> header.split(";")[0].substring((name + "=").length()))
			.findFirst();
	}

	/** 정리할 토큰 · 익명 ID를 기록한다 */
	private void remember(MvcResult result) {
		cookie(result, RefreshTokenCookies.NAME).ifPresent(issuedRefreshTokens::add);
		cookie(result, AnonymousIdCookie.NAME).map(UUID::fromString).ifPresent(anonymousIds::add);
	}

	private Long memberIdOf(String loginId) {
		return jdbcTemplate.queryForObject("SELECT member_id FROM member_password WHERE login_id = ?", Long.class,
			loginId);
	}

	/** 리프레시 쿠키로 재발급(M-1)을 받아 액세스 토큰의 주인을 확인한다 — 화면과 같은 흐름 */
	private MemberPrincipal principalFrom(MvcResult loginResult) throws Exception {
		String refreshToken = cookie(loginResult, RefreshTokenCookies.NAME).orElseThrow();
		MvcResult reissued = mockMvc.perform(post(REISSUE).header(HttpHeaders.ORIGIN, SAME_ORIGIN)
				.cookie(new Cookie(RefreshTokenCookies.NAME, refreshToken)))
			.andExpect(status().isOk())
			.andReturn();
		remember(reissued);
		String accessToken = JsonPath.read(reissued.getResponse().getContentAsString(), "$.accessToken");
		return accessTokenVerifier.verify(accessToken);
	}

	// ---- 회원가입 ----

	@Test
	void 가입하면_바로_로그인되고_리프레시_쿠키와_닉네임을_준다() throws Exception {
		String id = newLoginId();

		MvcResult result = mockMvc.perform(json(SIGNUP, signupBody(id, PASSWORD, "판사님")))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.nickname").value("판사님"))
			.andReturn();
		remember(result);

		assertThat(cookie(result, RefreshTokenCookies.NAME)).isPresent();
		// 쿠키 없이 가입했으니 이 회원에 연결된 새 익명 ID를 받는다(BE-62 결정 6)
		assertThat(cookie(result, AnonymousIdCookie.NAME)).isPresent();

		long memberId = memberIdOf(id);
		assertThat(jdbcTemplate.queryForObject("SELECT provider FROM member WHERE id = ?", String.class, memberId))
			.isEqualTo("LOCAL");
		String providerUserId = jdbcTemplate.queryForObject(
			"SELECT provider_user_id FROM member WHERE id = ?", String.class, memberId);
		assertThat(UUID.fromString(providerUserId)).isNotNull();	// 공급자 회원번호 자리는 UUID (결정 1)
		assertThat(jdbcTemplate.queryForObject("SELECT password_hash FROM member_password WHERE member_id = ?",
			String.class, memberId)).startsWith("{argon2}").doesNotContain(PASSWORD);
		assertThat(jdbcTemplate.queryForObject("SELECT last_login_at IS NOT NULL FROM member WHERE id = ?",
			Boolean.class, memberId)).isTrue();

		MemberPrincipal principal = principalFrom(result);
		assertThat(principal.memberId()).isEqualTo(memberId);
		assertThat(principal.roles()).isEqualTo(Set.of(MemberPrincipal.USER));
	}

	@Test
	void 아이디는_소문자로_저장하고_대소문자만_다른_아이디는_중복이다() throws Exception {
		String id = newLoginId();
		MvcResult result = mockMvc.perform(json(SIGNUP, signupBody(id.toUpperCase(), PASSWORD, "닉네임")))
			.andExpect(status().isOk())
			.andReturn();
		remember(result);
		assertThat(memberIdOf(id)).isNotNull();

		mockMvc.perform(json(SIGNUP, signupBody(id, PASSWORD, "다른사람")))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("DUPLICATE_ID"));
	}

	@Test
	void 같은_아이디로_동시에_가입하면_한_명만_만들어진다() throws Exception {
		String id = newLoginId();
		int threads = 4;
		CountDownLatch start = new CountDownLatch(1);
		ExecutorService pool = Executors.newFixedThreadPool(threads);
		try {
			List<Future<MvcResult>> futures = new ArrayList<>();
			for (int i = 0; i < threads; i++) {
				Callable<MvcResult> call = () -> {
					start.await();
					return mockMvc.perform(json(SIGNUP, signupBody(id, PASSWORD, uniqueNickname()))).andReturn();
				};
				futures.add(pool.submit(call));
			}
			start.countDown();
			int ok = 0;
			int conflict = 0;
			for (Future<MvcResult> future : futures) {
				MvcResult result = future.get();
				remember(result);
				int status = result.getResponse().getStatus();
				if (status == 200) {
					ok++;
				} else if (status == 409) {
					assertThat(result.getResponse().getContentAsString()).contains("DUPLICATE_ID");
					conflict++;
				}
			}
			assertThat(ok).isEqualTo(1);
			assertThat(conflict).isEqualTo(threads - 1);
		} finally {
			pool.shutdownNow();
		}
		// 진 쪽의 회원 행은 되돌려졌다 — 비밀번호 행 없는 LOCAL 회원이 남지 않는다
		assertThat(jdbcTemplate.queryForObject("""
			SELECT count(*) FROM member m
			WHERE m.provider = 'LOCAL' AND m.nickname LIKE 'cc%'
			  AND NOT EXISTS (SELECT 1 FROM member_password p WHERE p.member_id = m.id)
			""", Integer.class)).isZero();
	}

	@Test
	void 가입_입력이_규칙에_맞지_않으면_400이다() throws Exception {
		String id = newLoginId();
		// 요청 본문 → 서버가 details[].field로 알려 줄 칸 이름. 화면(signupPage.js)은 이 이름으로 그 칸에 안내를 붙인다 (PR #306 리뷰)
		// DTO 검사(@Valid)와 서비스 검사(한글 8자, BusinessException.ofField)가 같은 모양 [{ field, reason }]으로 내려가는지 함께 본다
		Map<String, String> invalidBodies = new LinkedHashMap<>();
		invalidBodies.put(signupBody("ab", PASSWORD, "닉네임"), "id");							// 아이디 4자 미만
		invalidBodies.put(signupBody("admin@example.com", PASSWORD, "닉네임"), "id");				// 아이디에 @ 불가 (화면 분기 기준)
		invalidBodies.put(signupBody(id, "short1", "닉네임"), "password");						// 비밀번호 8자 미만
		invalidBodies.put(signupBody(id, "onlyletters", "닉네임"), "password");					// 숫자 없음
		invalidBodies.put(signupBody(id, "12345678", "닉네임"), "password");						// 영문 없음
		invalidBodies.put(signupBody(id, "a1" + "x".repeat(63), "닉네임"), "password");			// 64자 초과
		invalidBodies.put(signupBody(id, PASSWORD, "가"), "nickname");							// 닉네임 2자 미만
		invalidBodies.put(signupBody(id, PASSWORD, "닉 네임"), "nickname");						// 공백 불가
		invalidBodies.put(signupBody(id, PASSWORD, "가나다라마바사아자"), "nickname");				// 한글이 있으면 8자까지 (FE-114, 서비스 검사)
		invalidBodies.put(signupBody(id, PASSWORD, "abc가나다라마바"), "nickname");				// 한글이 섞이면 영문이 있어도 8자까지 (서비스 검사)
		invalidBodies.put(signupBody(id, PASSWORD, "abcdefghijklm"), "nickname");				// 13자
		for (Map.Entry<String, String> invalid : invalidBodies.entrySet()) {
			mockMvc.perform(json(SIGNUP, invalid.getKey()))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
				.andExpect(jsonPath("$.details").isArray())
				.andExpect(jsonPath("$.details[*].field", everyItem(is(invalid.getValue()))))
				.andExpect(jsonPath("$.details[0].reason").isNotEmpty());
		}
		assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM member_password WHERE login_id = ?",
			Integer.class, id)).isZero();
	}

	/** 시험마다 겹치지 않는 닉네임(영문 소문자 · 숫자 12자) — 닉네임은 중복 금지(BE-139) */
	private static String uniqueNickname() {
		return "cc" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
	}

	@Test
	void 이미_쓰는_닉네임으로_가입하면_409이고_대소문자만_달라도_같다() throws Exception {
		String nickname = uniqueNickname();
		signup(newLoginId(), nickname);

		String other = newLoginId();
		mockMvc.perform(json(SIGNUP, signupBody(other, PASSWORD, nickname.toUpperCase())))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("DUPLICATE_NICKNAME"));
		// 거절된 가입은 회원도 비밀번호도 남기지 않는다
		assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM member_password WHERE login_id = ?", Integer.class,
			other)).isZero();
	}

	@Test
	void 닉네임_경계값은_통과한다() throws Exception {
		signup(newLoginId(), "가나다라마바사아");		// 한글 8자
		signup(newLoginId(), "abcdefghijkl");		// 영문 12자
	}

	// ---- 로그인 ----

	@Test
	void 닉네임이_비워진_아이디_회원이_로그인하면_표시_이름_방청객_회원ID를_준다() throws Exception {
		String id = newLoginId();
		signup(id, "비워진회원");
		long memberId = memberIdOf(id);
		jdbcTemplate.update("UPDATE member SET nickname = NULL WHERE id = ?", memberId);	// V39가 겹치던 닉네임을 비운 회원

		MvcResult result = mockMvc.perform(json(LOGIN, loginBody(id, PASSWORD)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.nickname").value("방청객 " + memberId))	// null이 아니라 항상 문자열
			.andReturn();
		remember(result);
	}

	@Test
	void 방청객으로_시작하는_닉네임으로는_가입할_수_없다() throws Exception {
		String id = newLoginId();
		mockMvc.perform(json(SIGNUP, signupBody(id, PASSWORD, "방청객99")))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
			.andExpect(jsonPath("$.details[0].field").value("nickname"));
		assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM member_password WHERE login_id = ?", Integer.class, id)).isZero();
	}

	@Test
	void 로그인하면_리프레시_쿠키와_닉네임을_주고_대문자로_입력해도_된다() throws Exception {
		String id = newLoginId();
		signup(id, "로그인회원");
		long memberId = memberIdOf(id);

		MvcResult result = mockMvc.perform(json(LOGIN, loginBody(id.toUpperCase(), PASSWORD)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.nickname").value("로그인회원"))
			.andReturn();
		remember(result);

		assertThat(principalFrom(result).memberId()).isEqualTo(memberId);
	}

	@Test
	void 비밀번호가_틀리거나_없는_아이디면_같은_401이다() throws Exception {
		String id = newLoginId();
		signup(id, "회원");

		for (String body : List.of(
			loginBody(id, "wrong12345"),				// 비밀번호 틀림
			loginBody("nosuchuser9", PASSWORD),			// 없는 아이디
			loginBody("bad-format!", PASSWORD),			// 형식이 틀린 아이디도 같은 응답
			loginBody("admin@lawnambul", PASSWORD))) {	// 관리자 이메일 형식 — 회원 로그인으로는 401
			MvcResult result = mockMvc.perform(json(LOGIN, body))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"))
				.andReturn();
			assertThat(result.getResponse().getHeaders(HttpHeaders.SET_COOKIE)).isEmpty();	// 실패하면 쿠키를 건드리지 않는다
		}
	}

	@Test
	void 카카오_회원은_아이디_로그인으로_들어올_수_없다() throws Exception {
		// 카카오 회원에게는 member_password 행이 없다 → 공급자 회원번호를 아이디로 넣어도 401
		String kakaoId = "9" + System.nanoTime() % 1_000_000_000L;
		jdbcTemplate.update("INSERT INTO member (provider, provider_user_id) VALUES ('KAKAO', ?)", kakaoId);
		try {
			mockMvc.perform(json(LOGIN, loginBody(kakaoId, PASSWORD)))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
		} finally {
			jdbcTemplate.update("DELETE FROM member WHERE provider = 'KAKAO' AND provider_user_id = ?", kakaoId);
		}
	}

	// ---- 익명 기록 연결 (BE-62 결정 6 규칙을 그대로 쓴다) ----

	@Test
	void 연결_안_된_익명_ID로_가입하면_그_ID를_이_회원에_연결하고_쿠키는_그대로다() throws Exception {
		UUID anonymousId = UUID.randomUUID();
		jdbcTemplate.update("INSERT INTO anonymous_user (id) VALUES (?)", anonymousId);
		anonymousIds.add(anonymousId);
		String id = newLoginId();

		MvcResult result = mockMvc.perform(json(SIGNUP, signupBody(id, PASSWORD, "익명연결"))
				.cookie(new Cookie(AnonymousIdCookie.NAME, anonymousId.toString())))
			.andExpect(status().isOk())
			.andReturn();
		remember(result);

		assertThat(cookie(result, AnonymousIdCookie.NAME)).isEmpty();
		assertThat(jdbcTemplate.queryForObject("SELECT member_id FROM anonymous_user WHERE id = ?", Long.class,
			anonymousId)).isEqualTo(memberIdOf(id));
	}

	@Test
	void 다른_회원에_연결된_익명_ID로_로그인하면_덮어쓰지_않고_새_익명_ID를_준다() throws Exception {
		String first = newLoginId();
		String second = newLoginId();
		MvcResult firstSignup = signup(first, "첫회원");
		signup(second, "둘째회원");
		UUID firstAnonymous = UUID.fromString(cookie(firstSignup, AnonymousIdCookie.NAME).orElseThrow());

		MvcResult result = mockMvc.perform(json(LOGIN, loginBody(second, PASSWORD))
				.cookie(new Cookie(AnonymousIdCookie.NAME, firstAnonymous.toString())))
			.andExpect(status().isOk())
			.andReturn();
		remember(result);

		assertThat(jdbcTemplate.queryForObject("SELECT member_id FROM anonymous_user WHERE id = ?", Long.class,
			firstAnonymous)).isEqualTo(memberIdOf(first));
		UUID issued = UUID.fromString(cookie(result, AnonymousIdCookie.NAME).orElseThrow());
		assertThat(jdbcTemplate.queryForObject("SELECT member_id FROM anonymous_user WHERE id = ?", Long.class,
			issued)).isEqualTo(memberIdOf(second));
	}

	// ---- Origin 검증 (로그인 CSRF, 결정 5) ----

	@Test
	void Origin이_없거나_다르면_가입_로그인_모두_403이다() throws Exception {
		String id = newLoginId();
		signup(id, "오리진");

		for (String origin : new String[] {null, OTHER_ORIGIN}) {
			MockHttpServletRequestBuilder signupRequest = post(SIGNUP).contentType(MediaType.APPLICATION_JSON)
				.content(signupBody(newLoginId(), PASSWORD, "막힘"));
			MockHttpServletRequestBuilder loginRequest = post(LOGIN).contentType(MediaType.APPLICATION_JSON)
				.content(loginBody(id, PASSWORD));
			if (origin != null) {
				signupRequest.header(HttpHeaders.ORIGIN, origin);
				loginRequest.header(HttpHeaders.ORIGIN, origin);
			}
			for (MockHttpServletRequestBuilder request : List.of(signupRequest, loginRequest)) {
				MvcResult result = mockMvc.perform(request)
					.andExpect(status().isForbidden())
					.andExpect(jsonPath("$.code").value("FORBIDDEN"))
					.andReturn();
				assertThat(result.getResponse().getHeaders(HttpHeaders.SET_COOKIE)).isEmpty();
			}
		}
		assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM member WHERE nickname = '막힘'",
			Integer.class)).isZero();
	}
}
