package com.team2.project.common.logging;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.joran.JoranConfigurator;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.LoggingEvent;
import ch.qos.logback.core.ConsoleAppender;
import ch.qos.logback.core.encoder.Encoder;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import net.logstash.logback.argument.StructuredArguments;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.MDC;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * 실제 운영 설정 파일(logback-spring.xml)을 그대로 읽어서 검증한다 (BE-71, WBS 7.2.3).
 * 전역 LoggerContext를 건드리지 않도록 테스트마다 별도 LoggerContext를 만들어 그 안에서만 로그를 찍고 끈다.
 */
class StructuredLoggingConfigTest {

	private static final JsonMapper JSON = JsonMapper.builder().build();

	private LoggerContext context;
	@TempDir java.nio.file.Path tempDir;

	@BeforeEach
	void setUp() throws Exception {
		System.setProperty("LOG_PATH", tempDir.toString());
		context = new LoggerContext();
		context.setName("test");
		// 애플리케이션이 쓰는 LoggerContext(SLF4J가 부팅 때 바인딩)와 달리, 직접 만든 LoggerContext는
		// MDCAdapter가 비어 있다. new LogbackMDCAdapter()로 새로 만들면 org.slf4j.MDC.put(...)이 쓰는
		// ThreadLocal과 다른 인스턴스가 되어(필드가 인스턴스별) 값이 안 보이므로, 전역에 이미 바인딩된 어댑터를 그대로 쓴다
		context.setMDCAdapter(MDC.getMDCAdapter());
		JoranConfigurator configurator = new JoranConfigurator();
		configurator.setContext(context);
		configurator.doConfigure(getClass().getClassLoader().getResource("logback-spring.xml"));
	}

	@AfterEach
	void tearDown() {
		context.stop();
		System.clearProperty("LOG_PATH");
	}

	@Test
	void traceIdInMdc_appearsAsJsonField() throws IOException {
		Logger logger = context.getLogger("test.trace");
		MDC.put("traceId", "trace-abc-123");
		try {
			logger.info("평범한 로그");
		} finally {
			MDC.remove("traceId");
		}
		context.stop();

		var entry = onlyLogEntry();
		assertThat(entry.get("traceId").asString()).isEqualTo("trace-abc-123");
		assertThat(entry.get("message").asString()).isEqualTo("평범한 로그");
	}

	@Test
	void bearerTokenAndEmailInMessage_areMasked() throws IOException {
		Logger logger = context.getLogger("test.masking");
		logger.warn("인증 실패: Authorization=Bearer abcDEF123.xyz-789_token, 문의: user@example.com");
		context.stop();

		var entry = onlyLogEntry();
		String message = entry.get("message").asString();
		assertThat(message).doesNotContain("abcDEF123.xyz-789_token").doesNotContain("user@example.com")
			.contains("****");
	}

	@Test
	void structuredArgumentWithSensitiveKey_isMaskedByFieldName() throws IOException {
		Logger logger = context.getLogger("test.masking.structured");
		logger.info("로그인 시도 {}", StructuredArguments.kv("password", "hunter2"));
		context.stop();

		var entry = onlyLogEntry();
		assertThat(entry.get("password").asString()).isEqualTo("****");
	}

	@Test
	void basicAuthAndRawJwtAndQuotedSecret_areMaskedInFile() throws IOException {
		Logger logger = context.getLogger("test.masking.more");
		logger.warn("Authorization=Basic dXNlcjpwYXNz, jwt=eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxMjM0NTY3ODkwIn0.dQw4w9WgXcQ, "
			+ "body={\"password\":\"hunter2\"}");
		context.stop();

		var entry = onlyLogEntry();
		String message = entry.get("message").asString();
		assertThat(message).doesNotContain("dXNlcjpwYXNz", "eyJhbGciOiJIUzI1NiJ9", "hunter2").contains("****");
	}

	@Test
	void consoleAppender_masksSameAsFileAppender() throws Exception {
		Logger root = context.getLogger(Logger.ROOT_LOGGER_NAME);
		@SuppressWarnings("unchecked")
		ConsoleAppender<ILoggingEvent> console = (ConsoleAppender<ILoggingEvent>) root.getAppender("CONSOLE");
		Encoder<ILoggingEvent> encoder = console.getEncoder();
		Logger logger = context.getLogger("test.masking.console");
		ILoggingEvent event = new LoggingEvent(Logger.class.getName(), logger, Level.WARN,
			"Authorization=Bearer abcDEF123.xyz-789_token, Basic dXNlcjpwYXNz, "
				+ "jwt=eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxMjM0NTY3ODkwIn0.dQw4w9WgXcQ, mail=user@example.com, "
				+ "body={\"password\":\"hunter2\"}",
			null, null);

		String rendered = new String(encoder.encode(event), StandardCharsets.UTF_8);

		assertThat(rendered).doesNotContain("abcDEF123.xyz-789_token", "dXNlcjpwYXNz", "eyJhbGciOiJIUzI1NiJ9",
			"user@example.com", "hunter2");
	}

	private JsonNode onlyLogEntry() throws IOException {
		List<String> lines = Files.readAllLines(tempDir.resolve("app.log"));
		assertThat(lines).hasSize(1);
		return JSON.readTree(lines.get(0));
	}
}
