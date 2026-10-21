package com.team2.project.auth.oauth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizedClientRepository;

/**
 * 카카오 액세스 토큰을 보관하지 않는다 (BE-62). 로그인 확인에만 쓰고 카카오 API를 다시 부르지 않기 때문이다.
 * 기본 저장소(메모리 서비스)를 쓰면 로그인할 때마다 서버 메모리에 쌓이고, 세션 저장소는 무상태 원칙에 어긋난다
 */
class NoStoreAuthorizedClientRepository implements OAuth2AuthorizedClientRepository {

	@Override
	public <T extends OAuth2AuthorizedClient> T loadAuthorizedClient(String clientRegistrationId,
		Authentication principal, HttpServletRequest request) {
		return null;
	}

	@Override
	public void saveAuthorizedClient(OAuth2AuthorizedClient authorizedClient, Authentication principal,
		HttpServletRequest request, HttpServletResponse response) {
		// 보관하지 않는다
	}

	@Override
	public void removeAuthorizedClient(String clientRegistrationId, Authentication principal,
		HttpServletRequest request, HttpServletResponse response) {
		// 보관한 것이 없다
	}
}
