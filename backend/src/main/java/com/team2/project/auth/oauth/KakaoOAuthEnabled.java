package com.team2.project.auth.oauth;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.util.StringUtils;

/**
 * kakao.oauth.client-id가 있을 때만 카카오 로그인을 켠다 (BE-62).
 * 없으면 카카오 로그인 경로만 꺼지고 서버는 그대로 뜬다(로컬 · 테스트 · CI, 다른 기능과 같은 규칙)
 */
public class KakaoOAuthEnabled implements Condition {

	@Override
	public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
		return StringUtils.hasText(context.getEnvironment().getProperty("kakao.oauth.client-id"));
	}
}
