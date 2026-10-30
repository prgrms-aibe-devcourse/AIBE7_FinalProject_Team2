package com.team2.project.common.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.team2.project.common.exception.BusinessException;
import com.team2.project.common.exception.ErrorCode;
import com.team2.project.common.security.token.MemberPrincipal;
import jakarta.servlet.http.Cookie;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.context.request.ServletWebRequest;

/** 컨트롤러 인자 Requester (BE-140) */
class RequesterArgumentResolverTest {

	private final RequesterArgumentResolver resolver = new RequesterArgumentResolver(new AnonymousIdCookie());

	@AfterEach
	void clearContext() {
		SecurityContextHolder.clearContext();
	}

	private Requester resolve(MockHttpServletRequest request) {
		return resolver.resolveArgument(null, null, new ServletWebRequest(request), null);
	}

	@Test
	@DisplayName("회원 토큰이면 회원 ID, 쿠키가 있으면 익명 ID — 둘 다 담는다")
	void member_andCookie() {
		UUID anonymousId = UUID.randomUUID();
		MemberPrincipal member = MemberPrincipal.user(7L);
		SecurityContextHolder.getContext().setAuthentication(
			new UsernamePasswordAuthenticationToken(member, null, member.authorities()));
		MockHttpServletRequest request = new MockHttpServletRequest();
		request.setCookies(new Cookie(AnonymousIdCookie.NAME, anonymousId.toString()));

		Requester requester = resolve(request);

		assertThat(requester.memberId()).contains(7L);
		assertThat(requester.anonymousId()).contains(anonymousId);
		assertThat(requester.requireMember()).isEqualTo(7L);
	}

	@Test
	@DisplayName("토큰 · 쿠키가 없으면 둘 다 없고, 회원 전용은 401")
	void guest_withoutCookie() {
		Requester requester = resolve(new MockHttpServletRequest());

		assertThat(requester.memberId()).isEmpty();
		assertThat(requester.anonymousId()).isEmpty();
		assertThatThrownBy(requester::requireMember)
			.isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED));
	}

	@Test
	@DisplayName("Spring의 익명 인증(비로그인 표시)은 회원이 아니다")
	void springAnonymous_isNotMember() {
		SecurityContextHolder.getContext().setAuthentication(new AnonymousAuthenticationToken(
			"key", "anonymousUser", AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS")));

		assertThat(resolve(new MockHttpServletRequest()).memberId()).isEmpty();
	}

	@Test
	@DisplayName("Requester 타입 인자만 맡는다")
	void supportsOnlyRequester() throws Exception {
		var handler = Fixture.class.getDeclaredMethod("handler", Requester.class, String.class);

		assertThat(resolver.supportsParameter(new MethodParameter(handler, 0))).isTrue();
		assertThat(resolver.supportsParameter(new MethodParameter(handler, 1))).isFalse();
	}

	private static final class Fixture {
		@SuppressWarnings("unused")
		void handler(Requester requester, String other) {
		}
	}
}
