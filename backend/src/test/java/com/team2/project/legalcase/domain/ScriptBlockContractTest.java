package com.team2.project.legalcase.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * 대본 블록 계약 (BE-126). 파이프라인(파이썬)과 같은 계약 파일을 읽어 연동 표 · 신문 답 규칙이 같은지 본다 — 파이썬 쪽은
 * `tools/ai-judgment/tests/test_script_assembler.py`의 {@code BlockContractTest}가 같은 파일을 읽는다. 정본은 파이썬이라,
 * 이 테스트가 깨지면 계약 파일이 바뀐 것이다: {@link ScriptUnit} · {@link ScriptAnswers}를 파일에 맞춘다.
 */
class ScriptBlockContractTest {

	private static final Path CONTRACT = Path.of("..", "tools", "ai-judgment", "contracts", "script_blocks.json");

	private static JsonNode contract;

	@BeforeAll
	static void readContract() throws Exception {
		assertThat(CONTRACT).as("tools/ai-judgment/contracts/script_blocks.json (backend 폴더 기준 상대 경로)").isRegularFile();
		contract = JsonMapper.builder().build().readTree(Files.readString(CONTRACT, StandardCharsets.UTF_8));
	}

	@Test
	@DisplayName("연동 표: 블록마다 만들 때 본 값(basis)과 바뀌면 낡는 뒤 블록(affects)이 파이썬과 같다")
	void basisAndAffects_matchContract() {
		Map<String, List<String>> basis = new LinkedHashMap<>();
		Map<String, List<String>> affects = new LinkedHashMap<>();
		for (ScriptUnit unit : ScriptUnit.values()) {
			basis.put(unit.name(), unit.basis());
			affects.put(unit.name(), unit.affects().stream().map(ScriptUnit::name).toList());
		}

		assertThat(basis).isEqualTo(stringLists(contract.get("basis")));
		assertThat(affects).isEqualTo(stringLists(contract.get("affects")));
		assertThat(ScriptUnit.DB_BASIS).isEqualTo(strings(contract.get("dbBasis")));
	}

	@Test
	@DisplayName("신문 답 정규화: 계약 사례가 모두 같은 값이 된다 (말줄임표 · 마침표 섞임, 전각 공백, 접두만 같은 낱말)")
	void normalizeAnswer_matchesContract() {
		List<String> mismatches = new ArrayList<>();
		for (JsonNode example : contract.get("normalizeAnswer")) {
			String answer = example.get("answer").isNull() ? null : example.get("answer").asText();
			String actual = ScriptAnswers.normalize(answer);
			if (!actual.equals(example.get("expected").asText())) {
				mismatches.add("%s → %s (기대 %s)".formatted(answer, actual, example.get("expected").asText()));
			}
		}
		assertThat(mismatches).isEmpty();
		assertThat(contract.get("normalizeAnswer").size()).isGreaterThanOrEqualTo(20);	// 파일을 제대로 읽었는지
	}

	@Test
	@DisplayName("인정 여부(admits): 계약 사례가 모두 같은 값이 된다")
	void admits_matchesContract() {
		List<String> mismatches = new ArrayList<>();
		for (JsonNode example : contract.get("admits")) {
			String answer = example.get("answer").isNull() ? null : example.get("answer").asText();
			if (ScriptAnswers.admits(answer) != example.get("expected").asBoolean()) {
				mismatches.add(String.valueOf(answer));
			}
		}
		assertThat(mismatches).isEmpty();
		assertThat(contract.get("admits").size()).isGreaterThanOrEqualTo(10);
	}

	@Test
	@DisplayName("계약 파일의 단위는 enum과 같다 (U0 ~ U4)")
	void units_matchContract() {
		List<String> units = new ArrayList<>();
		contract.get("basis").properties().forEach(entry -> units.add(entry.getKey()));
		assertThat(units).isEqualTo(Arrays.stream(ScriptUnit.values()).map(ScriptUnit::name).toList());
	}

	private static Map<String, List<String>> stringLists(JsonNode node) {
		Map<String, List<String>> map = new LinkedHashMap<>();
		node.properties().forEach(entry -> map.put(entry.getKey(), strings(entry.getValue())));
		return map;
	}

	private static List<String> strings(JsonNode array) {
		List<String> values = new ArrayList<>();
		array.forEach(item -> values.add(item.asText()));
		return values;
	}
}
