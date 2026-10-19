package com.team2.project.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.SystemEnvironmentPropertySource;
import org.springframework.mock.env.MockEnvironment;

/** 실제 DB/Redis/외부 API에 접속하지 않고 테스트 시작 차단 시점과 자동 등록을 검증한다. */
class TestEnvironmentGuardTest {

	@Test
	void acceptsDedicatedStoresAndDisablesEmbeddingKey() {
		MockEnvironment environment = safeEnvironment().withProperty("rag.embedding.api-key", "fake-inherited-key");
		try (GenericApplicationContext context = context(environment)) {
			new TestEnvironmentGuard().initialize(context);
			context.refresh();
			assertThat(environment.getProperty("rag.embedding.api-key")).isEmpty();
		}
	}

	@Test
	void rejectsMissingTestProfile() {
		MockEnvironment environment = safeEnvironment();
		environment.setActiveProfiles();
		assertBlocked(environment);
	}

	@Test
	void rejectsDevelopmentRemoteAndModifiedJdbcUrls() {
		for (String url : new String[] {
			"jdbc:postgresql://localhost:5432/lawnambul",
			"jdbc:postgresql://localhost:55432/lawnambul",
			"jdbc:postgresql://example.invalid:55432/lawnambul_test",
			"jdbc:postgresql://localhost:55432/lawnambul_test?currentSchema=public"}) {
			assertBlocked(safeEnvironment().withProperty("spring.datasource.url", url));
		}
	}

	@Test
	void rejectsFlywayAndHikariConnectionOverrides() {
		for (String key : new String[] {"spring.flyway.url", "spring.datasource.hikari.jdbc-url"}) {
			assertBlocked(safeEnvironment().withProperty(key, "jdbc:postgresql://localhost:5432/lawnambul"));
		}
		for (String key : new String[] {"spring.datasource.username", "spring.flyway.user",
			"spring.datasource.hikari.username"}) {
			assertBlocked(safeEnvironment().withProperty(key, "lawnambul"));
		}
		assertBlocked(safeEnvironment().withProperty("spring.datasource.jndi-name", "java:comp/env/jdbc/development"));
		assertBlocked(safeEnvironment().withProperty("spring.datasource.hikari.data-source-class-name", "other.Source"));
		assertBlocked(safeEnvironment().withProperty("spring.datasource.hikari.data-source-properties.databaseName", "lawnambul"));
	}

	@Test
	void rejectsRelaxedEnvironmentVariableOverrides() {
		for (String key : new String[] {"SPRING_DATASOURCE_URL", "SPRING_FLYWAY_URL",
			"SPRING_DATASOURCE_HIKARI_JDBCURL"}) {
			MockEnvironment environment = safeEnvironment();
			// Boot는 실제 시스템 환경 소스 이름일 때 환경변수 전용 relaxed mapping을 적용한다.
			environment.getPropertySources().addFirst(new SystemEnvironmentPropertySource("systemEnvironment",
				Map.of(key, "jdbc:postgresql://localhost:5432/lawnambul")));
			assertBlocked(environment);
		}
	}

	@Test
	void rejectsDevelopmentRedis() {
		assertBlocked(safeEnvironment().withProperty("redis.host", "example.invalid"));
		assertBlocked(safeEnvironment().withProperty("redis.port", "6379"));
	}

	@Test
	void rejectsUnsafeMigrationDdlAndBootstrapSettings() {
		assertBlocked(safeEnvironment().withProperty("spring.flyway.locations", "filesystem:/not-a-real-seed"));
		assertBlocked(safeEnvironment().withProperty("spring.flyway.clean-disabled", "false"));
		assertBlocked(safeEnvironment().withProperty("spring.jpa.hibernate.ddl-auto", "create-drop"));
		assertBlocked(safeEnvironment().withProperty("admin.bootstrap.email", "fake@example.invalid"));
	}

	@Test
	void detectsLatePropertyOverridesBeforeBeanCreation() {
		MockEnvironment environment = safeEnvironment();
		AtomicBoolean beanCreated = new AtomicBoolean();
		try (GenericApplicationContext context = context(environment)) {
			context.registerBean("wouldConnect", Object.class, () -> {
				beanCreated.set(true);
				return new Object();
			});
			new TestEnvironmentGuard().initialize(context);
			environment.getPropertySources().addFirst(new MapPropertySource("lateTestOverride",
				Map.of("spring.datasource.url", "jdbc:postgresql://localhost:5432/lawnambul")));
			assertThatThrownBy(context::refresh).hasMessageContaining("BE-87");
			assertThat(beanCreated).isFalse();
		}
	}

	@Test
	void springApplicationAutomaticallyLoadsGuardWithoutGradleOrAnnotation() {
		// 데이터소스 자동 설정이 없는 빈 컨텍스트다. 잘못된 주소로 실제 연결하지 않는다.
		SpringApplication application = new SpringApplication(EmptyTestApplication.class);
		application.setWebApplicationType(WebApplicationType.NONE);
		application.setLogStartupInfo(false);
		application.setRegisterShutdownHook(false);
		assertThat(application.getInitializers()).anyMatch(TestEnvironmentGuard.class::isInstance);
		assertThatThrownBy(() -> application.run(
			"--spring.config.location=optional:classpath:/be87-no-config.yml",
			"--spring.profiles.active=test",
			"--spring.datasource.url=jdbc:postgresql://localhost:5432/lawnambul"))
			.hasMessageContaining("BE-87");
	}

	private void assertBlocked(MockEnvironment environment) {
		try (GenericApplicationContext context = context(environment)) {
			assertThatThrownBy(() -> new TestEnvironmentGuard().initialize(context))
				.isInstanceOf(IllegalStateException.class).hasMessageContaining("BE-87")
				.hasMessageNotContaining("example.invalid").hasMessageNotContaining("fake@example.invalid");
		}
	}

	private GenericApplicationContext context(MockEnvironment environment) {
		GenericApplicationContext context = new GenericApplicationContext();
		context.setEnvironment(environment);
		return context;
	}

	private MockEnvironment safeEnvironment() {
		MockEnvironment environment = new MockEnvironment()
			.withProperty("spring.datasource.url", "jdbc:postgresql://127.0.0.1:55432/lawnambul_test")
			.withProperty("spring.datasource.username", "lawnambul_test")
			.withProperty("redis.host", "127.0.0.1").withProperty("redis.port", "56379")
			.withProperty("spring.jpa.hibernate.ddl-auto", "validate")
			.withProperty("spring.flyway.clean-disabled", "true")
			.withProperty("spring.flyway.locations", "classpath:db/migration,classpath:db/seed");
		environment.setActiveProfiles("test");
		return environment;
	}

	@TestConfiguration(proxyBeanMethods = false)
	static class EmptyTestApplication {
		@Bean
		String mustNotBeCreated() {
			throw new AssertionError("Guard must stop startup before bean creation");
		}
	}
}
