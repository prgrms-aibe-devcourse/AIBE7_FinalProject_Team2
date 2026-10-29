package com.team2.project.community.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * 커뮤니티 설정 (BE-115, docs/decisions/BE-115.md)
 *
 * @param homeCacheTtl 커뮤니티 메인(C-6) 응답을 다시 계산하기까지의 시간. 메인은 공개 API라 부를 때마다 글쓴이 전원의
 *                     명함을 계산하면 회원 수만큼 비용이 늘어, 계산한 응답을 이 시간 동안 그대로 준다(랭킹 · 이야기 많은 사건이
 *                     그만큼 늦게 반영된다). 0이면 캐시를 쓰지 않는다(테스트용). 하루(P1D)까지 늘려도 된다
 */
@ConfigurationProperties("community")
public record CommunityProperties(@DefaultValue("PT1H") Duration homeCacheTtl) {

	public CommunityProperties {
		if (homeCacheTtl == null || homeCacheTtl.isNegative()) {
			throw new IllegalStateException("community.home-cache-ttl은 0 이상이어야 한다: " + homeCacheTtl);
		}
	}
}
