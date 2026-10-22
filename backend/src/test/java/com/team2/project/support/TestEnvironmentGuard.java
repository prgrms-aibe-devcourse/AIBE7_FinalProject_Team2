package com.team2.project.support;

import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.Profiles;

/** Gradle를 거치지 않는 @SpringBootTest도 빈 생성 전에 전용 저장소 사용을 검사한다 (BE-87). */
public final class TestEnvironmentGuard
		implements ApplicationContextInitializer<ConfigurableApplicationContext>, Ordered {

	private static final Pattern TEST_DB = Pattern.compile(
		"jdbc:postgresql://(127\\.0\\.0\\.1|localhost):55432/lawnambul_test");
	private static final List<String> MIGRATIONS = List.of("classpath:db/migration", "classpath:db/seed");
	private static final String MESSAGE = "BE-87: 테스트는 test 프로필과 전용 DB(:55432/lawnambul_test) · "
		+ "Redis(:56379)만 사용합니다. backend/TESTING.md의 실행 설정을 확인하세요.";

	@Override
	public int getOrder() {
		return Ordered.LOWEST_PRECEDENCE;
	}

	@Override
	public void initialize(ConfigurableApplicationContext context) {
		ConfigurableEnvironment environment = context.getEnvironment();
		// IDE의 환경변수/시스템 속성보다 우선한다. 실제 키를 읽거나 로그에 출력하지 않는다.
		environment.getPropertySources().addFirst(new MapPropertySource("be87ExternalApiDisabled",
			Map.of("rag.embedding.api-key", "")));
		validate(environment);
		// 테스트 속성/customizer가 초기화기 이후 설정을 바꿨는지도 빈 생성 직전에 다시 검사한다.
		context.addBeanFactoryPostProcessor(beanFactory -> validate(environment));
	}

	private static void validate(ConfigurableEnvironment environment) {
		Binder binder = Binder.get(environment);
		String url = value(binder, "spring.datasource.url", "");
		String username = value(binder, "spring.datasource.username", "");
		String flywayUrl = value(binder, "spring.flyway.url", url);
		String flywayUser = value(binder, "spring.flyway.user", username);
		String hikariUrl = value(binder, "spring.datasource.hikari.jdbc-url", url);
		String hikariUser = value(binder, "spring.datasource.hikari.username", username);
		boolean safe = environment.acceptsProfiles(Profiles.of("test"))
			&& TEST_DB.matcher(url).matches() && "lawnambul_test".equals(username)
			&& TEST_DB.matcher(flywayUrl).matches() && "lawnambul_test".equals(flywayUser)
			&& TEST_DB.matcher(hikariUrl).matches() && "lawnambul_test".equals(hikariUser)
			&& List.of("localhost", "127.0.0.1").contains(value(binder, "redis.host", ""))
			&& "56379".equals(value(binder, "redis.port", ""))
			&& "validate".equals(value(binder, "spring.jpa.hibernate.ddl-auto", ""))
			&& "true".equals(value(binder, "spring.flyway.enabled", ""))
			&& "true".equals(value(binder, "spring.flyway.clean-disabled", ""))
			&& MIGRATIONS.equals(binder.bind("spring.flyway.locations", Bindable.listOf(String.class))
				.orElse(List.of()))
			&& value(binder, "admin.bootstrap.email", "").isBlank()
			&& value(binder, "admin.bootstrap.password", "").isBlank()
			&& value(binder, "rag.embedding.api-key", "").isBlank();
		// Hikari/JNDI의 별도 접속 경로로 JDBC URL 검증을 우회하지 못하게 한다.
		safe = safe && value(binder, "spring.datasource.jndi-name", "").isBlank()
			&& value(binder, "spring.datasource.hikari.data-source-class-name", "").isBlank()
			&& !binder.bind("spring.datasource.hikari.data-source-properties", Map.class).isBound()
			&& List.of("", "com.zaxxer.hikari.HikariDataSource")
				.contains(value(binder, "spring.datasource.type", ""));
		if (!safe) {
			// 잘못된 설정값 자체에는 비밀번호나 원격 주소가 있을 수 있어 메시지에 넣지 않는다.
			throw new IllegalStateException(MESSAGE);
		}
	}

	private static String value(Binder binder, String key, String fallback) {
		return binder.bind(key, String.class).orElse(fallback);
	}
}
