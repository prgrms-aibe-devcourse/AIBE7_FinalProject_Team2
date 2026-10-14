package com.team2.project.common.redis;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Redis 접속 설정 (확장 단계, BE-61 · WBS 7.1.4). 정책: docs/tech-stack.md 2장 "Redis 운영 구성"
 *
 * Redis 한 인스턴스를 논리 DB 두 개로 나눠 쓴다. 캐시(cacheDatabase)는 지워져도 PostgreSQL에서 다시 만들고,
 * 토큰(tokenDatabase)은 지워지면 로그인이 풀리므로 원본처럼 다룬다. 서버 설정(AOF · noeviction)은 Compose 파일에 있다.
 * Spring Boot 기본 설정(spring.data.redis)은 DB 번호를 하나만 받아서 쓰지 않는다.
 *
 * @param host 접속 호스트. 운영은 Compose 서비스 이름(redis)
 * @param port 접속 포트
 * @param password 비밀번호. 환경변수 REDIS_PASSWORD로만 받는다 (코드 · 저장소에 두지 않는다). 비어 있으면 인증 없이 접속
 * @param timeout 명령 응답 대기 상한. Redis 장애 때 요청이 오래 매달리지 않게 짧게 둔다
 * @param cacheDatabase 캐시용 논리 DB 번호
 * @param tokenDatabase 토큰 저장소용 논리 DB 번호 (7.4.3)
 */
@ConfigurationProperties("redis")
public record AppRedisProperties(
	@DefaultValue("localhost") String host,
	@DefaultValue("6379") int port,
	String password,
	@DefaultValue("2s") Duration timeout,
	@DefaultValue("0") int cacheDatabase,
	@DefaultValue("1") int tokenDatabase
) {

	public AppRedisProperties {
		// 두 용도가 같은 DB를 쓰면 캐시를 비울 때 토큰까지 지워진다. 설정 실수를 기동 시점에 막는다
		if (cacheDatabase == tokenDatabase) {
			throw new IllegalArgumentException(
				"redis.cache-database와 redis.token-database는 달라야 한다 (현재 둘 다 " + cacheDatabase + ")");
		}
	}

	public boolean hasPassword() {
		return password != null && !password.isBlank();
	}

	/** 기본 toString은 모든 값을 찍으므로 비밀번호를 가린다 (설정 객체가 로그에 남아도 노출되지 않게) */
	@Override
	public String toString() {
		return "AppRedisProperties[host=" + host + ", port=" + port + ", password=" + (hasPassword() ? "****" : "(없음)")
			+ ", timeout=" + timeout + ", cacheDatabase=" + cacheDatabase + ", tokenDatabase=" + tokenDatabase + "]";
	}
}
