package com.team2.project.common.security.token.jwt;

import com.team2.project.common.redis.RedisConfig;
import java.time.Duration;
import java.util.Optional;
import java.util.Set;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * 리프레시 토큰 저장소 (확장 단계, BE-94 · WBS 7.4.3). Redis 토큰 DB(DB 1)에 JSON 문자열로 저장한다
 * (RedisConfig 규칙 — JDK 직렬화 금지). 키 자체가 토큰 값이라 재사용 · 추측 공격은 토큰의 무작위성에 기댄다
 * (JwtTokenIssuer가 SecureRandom으로 생성).
 *
 * 모든 키에 TTL을 단다(Redis noeviction 정책, RedisConfig). 토큰 값은 로그에 남기지 않는다.
 */
@Component
public class RefreshTokenStore {

	private static final String KEY_PREFIX = "refresh:";

	private final StringRedisTemplate tokenRedisTemplate;
	private final JsonMapper jsonMapper;

	public RefreshTokenStore(@Qualifier(RedisConfig.TOKEN) StringRedisTemplate tokenRedisTemplate, JsonMapper jsonMapper) {
		this.tokenRedisTemplate = tokenRedisTemplate;
		this.jsonMapper = jsonMapper;
	}

	public void save(String refreshToken, long memberId, Set<String> roles, Duration ttl) {
		String value = jsonMapper.writeValueAsString(new StoredRefreshToken(memberId, roles));
		tokenRedisTemplate.opsForValue().set(key(refreshToken), value, ttl);
	}

	public Optional<StoredRefreshToken> find(String refreshToken) {
		String value = tokenRedisTemplate.opsForValue().get(key(refreshToken));
		if (value == null) {
			return Optional.empty();
		}
		return Optional.of(jsonMapper.readValue(value, StoredRefreshToken.class));
	}

	/** 없는 토큰을 지워도 예외를 던지지 않는다(로그아웃 · 재발급 둘 다 멱등해야 한다) */
	public void delete(String refreshToken) {
		tokenRedisTemplate.delete(key(refreshToken));
	}

	private static String key(String refreshToken) {
		return KEY_PREFIX + refreshToken;
	}
}
