package com.team2.project.auth.oauth;

import com.team2.project.common.redis.RedisConfig;
import com.team2.project.common.security.SecurityConfig;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Arrays;
import java.util.Base64;
import java.util.Optional;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Conditional;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

/**
 * 카카오 첫 가입의 "가입 대기" (확장 단계, BE-145 · API 명세 M-4 · M-12 · M-13)
 *
 * 처음 온 카카오 계정은 약관에 동의해야 회원이 만들어진다. 동의 전에는 DB에 아무것도 저장하지 않고,
 * 카카오 회원번호만 Redis 토큰 DB에 잠깐 둔다. OAuth state({@link OAuthAuthorizationRequestRepository})와 같은 방식이다:
 * - 값은 Redis에 TTL로 두고, 브라우저에는 그 값을 찾는 무작위 문자열만 짧은 HttpOnly 쿠키로 준다. 회원번호는 브라우저에 내리지 않는다
 * - 꺼낼 때는 Redis GETDEL로 한 번만 쓴다(같은 요청 재사용 방지). 동의하면 쓰이고, 닫으면 지워지고, 아니면 TTL로 사라진다
 * - 쿠키는 SameSite=Lax: 카카오에서 돌아오는 콜백(다른 사이트에서 온 이동)의 응답에서 심기 때문이다. 경로는 M-12 · M-13 하나로 좁힌다
 */
@Component
@Conditional(KakaoOAuthEnabled.class)
public class KakaoSignupPending {

	public static final String COOKIE = "NLNB_SIGNUP";
	/** M-12 · M-13 경로. 이 경로의 요청에만 쿠키가 따라간다 */
	public static final String PATH = SecurityConfig.KAKAO_SIGNUP_PATH;
	/** 동의 화면을 읽고 누르는 시간. 지나면 카카오 로그인부터 다시 한다 */
	static final Duration TTL = Duration.ofMinutes(10);
	private static final String KEY_PREFIX = "kakao-signup:pending:";
	/** 32바이트 무작위 값의 Base64URL(패딩 없음). 쿠키 값을 Redis 키에 붙이기 전에 형식을 확인한다 */
	private static final Pattern TOKEN = Pattern.compile("[A-Za-z0-9_-]{43}");
	private static final SecureRandom RANDOM = new SecureRandom();

	private final StringRedisTemplate redis;

	public KakaoSignupPending(@Qualifier(RedisConfig.TOKEN) StringRedisTemplate tokenRedisTemplate) {
		this.redis = tokenRedisTemplate;
	}

	/** 가입 대기를 만들고 쿠키를 심는다. Redis에 먼저 쓰고, 성공한 뒤에만 쿠키를 심는다 */
	public void begin(HttpServletResponse response, String kakaoUserId) {
		byte[] bytes = new byte[32];
		RANDOM.nextBytes(bytes);
		String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
		redis.opsForValue().set(key(token), kakaoUserId, TTL);
		response.addHeader(HttpHeaders.SET_COOKIE, cookie(token, TTL).toString());
	}

	/** 쿠키가 가리키는 가입 대기를 꺼내 쓴다(한 번만). 없거나 지났거나 이미 썼으면 비어 있다. 쿠키는 항상 지운다 */
	public Optional<String> consume(HttpServletRequest request, HttpServletResponse response) {
		Optional<String> kakaoUserId = token(request).map(token -> redis.opsForValue().getAndDelete(key(token)));
		clearCookie(response);
		return kakaoUserId;
	}

	/** 동의 없이 닫았을 때: 가입 대기를 지우고 쿠키를 지운다. 이미 없어도 같다 */
	public void discard(HttpServletRequest request, HttpServletResponse response) {
		token(request).ifPresent(token -> redis.delete(key(token)));
		clearCookie(response);
	}

	private static Optional<String> token(HttpServletRequest request) {
		Cookie[] cookies = request.getCookies();
		if (cookies == null) {
			return Optional.empty();
		}
		return Arrays.stream(cookies)
			.filter(cookie -> COOKIE.equals(cookie.getName()))
			.map(Cookie::getValue)
			.filter(value -> value != null && TOKEN.matcher(value).matches())
			.findFirst();
	}

	private static void clearCookie(HttpServletResponse response) {
		response.addHeader(HttpHeaders.SET_COOKIE, cookie("", Duration.ZERO).toString());
	}

	private static ResponseCookie cookie(String value, Duration maxAge) {
		return ResponseCookie.from(COOKIE, value)
			.httpOnly(true)
			.secure(true)
			.sameSite("Lax")
			.path(PATH)
			.maxAge(maxAge)
			.build();
	}

	private static String key(String token) {
		return KEY_PREFIX + token;
	}
}
