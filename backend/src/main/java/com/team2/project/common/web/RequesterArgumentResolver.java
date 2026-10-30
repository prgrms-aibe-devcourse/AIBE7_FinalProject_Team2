package com.team2.project.common.web;

import com.team2.project.common.security.token.MemberPrincipal;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.core.MethodParameter;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;
import jakarta.servlet.http.HttpServletRequest;

/**
 * 컨트롤러 인자 {@link Requester}를 만든다 (BE-140): 회원은 사용자 체인의 Bearer 필터가 SecurityContext에 넣은 값, 익명 ID는 쿠키.
 * 잘못된 토큰은 필터가 먼저 401로 끝내므로 여기서는 보지 않는다
 */
@Component
@RequiredArgsConstructor
public class RequesterArgumentResolver implements HandlerMethodArgumentResolver {

	private final AnonymousIdCookie anonymousIdCookie;

	@Override
	public boolean supportsParameter(MethodParameter parameter) {
		return parameter.getParameterType() == Requester.class;
	}

	@Override
	public Requester resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
		NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
		HttpServletRequest request = webRequest.getNativeRequest(HttpServletRequest.class);
		return new Requester(currentMemberId(), anonymousIdCookie.read(request));
	}

	private static Optional<Long> currentMemberId() {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		return authentication != null && authentication.getPrincipal() instanceof MemberPrincipal member
			? Optional.of(member.memberId())
			: Optional.empty();
	}
}
