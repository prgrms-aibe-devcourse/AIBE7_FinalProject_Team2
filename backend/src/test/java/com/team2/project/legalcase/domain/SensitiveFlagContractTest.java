package com.team2.project.legalcase.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@link SensitiveFlag}가 파이프라인의 플래그 목록과 같은지 확인한다 (BE-125).
 *
 * <p>기준은 {@code tools/ai-judgment/script_assembler.py}의 {@code SENSITIVE_FLAGS}다(두 번째 값이 None이 아니면
 * 사용자 안내 대상). 어긋나면 사용자 안내와 대본 가드가 따로 놀기 때문에, 파이프라인에 플래그를 더하고 여기를
 * 빼먹으면 이 테스트가 깨진다. 저장소 밖에서 돌려 파일이 없으면 건너뛴다.
 */
class SensitiveFlagContractTest {

	private static final Path SCRIPT_ASSEMBLER = Path.of("..", "tools", "ai-judgment", "script_assembler.py");
	private static final Pattern ENTRY = Pattern.compile("^\\s+\"([A-Z_]+)\": \\(\"[a-z_]+\", (None|\"[^\"]*\")\\),?\\s*$");

	@Test
	@DisplayName("플래그 이름과 안내 대상이 파이프라인의 SENSITIVE_FLAGS와 같다")
	void matchesPipelineFlags() throws IOException {
		assumeTrue(Files.exists(SCRIPT_ASSEMBLER), "tools/ai-judgment가 없어 건너뜁니다");

		Set<String> all = new LinkedHashSet<>();
		Set<String> notice = new LinkedHashSet<>();
		boolean inFlags = false;
		for (String line : Files.readAllLines(SCRIPT_ASSEMBLER)) {
			if (line.startsWith("SENSITIVE_FLAGS = {")) {
				inFlags = true;
				continue;
			}
			if (inFlags && line.startsWith("}")) {
				break;
			}
			Matcher entry = ENTRY.matcher(line);
			if (inFlags && entry.matches()) {
				all.add(entry.group(1));
				if (!entry.group(2).equals("None")) {
					notice.add(entry.group(1));
				}
			}
		}

		assertThat(all).as("script_assembler.py에서 읽은 플래그").isNotEmpty();
		assertThat(Arrays.stream(SensitiveFlag.values()).map(Enum::name)).containsExactlyInAnyOrderElementsOf(all);
		assertThat(Arrays.stream(SensitiveFlag.values()).filter(SensitiveFlag::isNotice).map(Enum::name))
			.containsExactlyInAnyOrderElementsOf(notice);
	}
}
