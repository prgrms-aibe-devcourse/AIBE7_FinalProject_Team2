package com.team2.project.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/**
 * 성향 콘텐츠 시드(R__personality_content) 검증 (BE-72 · WBS 7.6.4).
 * DB 제약이 한 행씩 막는 것(글자 · 점수 · 칸 순서 · 관용 지수)은 V15가 맡고, 여기서는 "전체가 빠짐없이 들어왔는가"를 본다.
 * 기준은 docs/personality-test-content.md 3장 · 4장, 채점에 필요한 조건은 docs/personality-scoring.md 2-5.
 */
@SpringBootTest
@Transactional
class PersonalityContentSeedTest {

	private static final Map<String, Set<String>> AXIS_LETTERS = Map.of(
		"APOLOGY_SINCERITY", Set.of("E", "I"), "FAULT_STANDARD", Set.of("S", "N"),
		"PRINCIPLE_RELATION", Set.of("T", "F"), "ORDER_OPPORTUNITY", Set.of("J", "P"));
	private static final Set<String> STRICT_LETTERS = Set.of("E", "S", "T", "J");

	@Autowired JdbcTemplate jdbc;

	@Test
	@DisplayName("유형 16개가 모두 있고, 캐릭터마다 8개, 공유 주소는 서로 다르다")
	void types_allSixteen() {
		List<Map<String, Object>> types = jdbc.queryForList("SELECT code, share_id, character_code FROM personality_type");
		assertThat(types).hasSize(16);
		assertThat(types).extracting(row -> (String) row.get("code")).containsExactlyInAnyOrderElementsOf(allCodes());
		assertThat(types).extracting(row -> (String) row.get("share_id")).doesNotHaveDuplicates()
			.allSatisfy(shareId -> assertThat(shareId).matches("[A-Za-z0-9]{6,16}"));
		assertThat(types.stream().collect(Collectors.groupingBy(row -> row.get("character_code"), Collectors.counting())))
			.containsOnly(Map.entry("NAERO", 8L), Map.entry("NAMBUL", 8L));
	}

	@Test
	@DisplayName("문항 16개 — 축마다 4개, 같은 축이 연달아 나오지 않고, 네 문항마다 네 상황이 한 번씩")
	void questions_balanced() {
		List<Map<String, Object>> questions = jdbc.queryForList(
			"SELECT axis, scenario, display_order FROM personality_question WHERE is_active ORDER BY display_order");
		assertThat(questions).hasSize(16);
		assertThat(questions).extracting(row -> (Integer) row.get("display_order"))
			.containsExactly(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16);
		assertThat(questions.stream().collect(Collectors.groupingBy(row -> row.get("axis"), Collectors.counting())))
			.hasSize(4).allSatisfy((axis, count) -> assertThat(count).isEqualTo(4L));
		for (int i = 1; i < questions.size(); i++) {
			assertThat(questions.get(i).get("axis")).isNotEqualTo(questions.get(i - 1).get("axis"));
		}
		for (int block = 0; block < 4; block++) {
			assertThat(questions.subList(block * 4, block * 4 + 4)).extracting(row -> row.get("scenario"))
				.containsExactlyInAnyOrder("FRIEND", "FAMILY", "WORK", "NEIGHBOR");
		}
	}

	@Test
	@DisplayName("선택지는 문항마다 5칸 — A 글자 · A 글자 · 보통 · B 글자 · B 글자, A와 B는 그 축의 서로 다른 글자")
	void choices_fiveCellsPerQuestion() {
		List<Map<String, Object>> questions = jdbc.queryForList(
			"SELECT id, axis FROM personality_question WHERE is_active ORDER BY display_order");
		int strictOnA = 0;
		for (Map<String, Object> question : questions) {
			List<String> letters = jdbc.queryForList(
				"SELECT letter FROM personality_choice WHERE question_id = ? ORDER BY display_order", String.class, question.get("id"));
			assertThat(letters).hasSize(5);
			String a = letters.get(0);
			String b = letters.get(4);
			assertThat(letters).containsExactly(a, a, null, b, b);
			assertThat(Set.of(a, b)).isEqualTo(AXIS_LETTERS.get((String) question.get("axis")));
			if (STRICT_LETTERS.contains(a)) {
				strictOnA++;
			}
		}
		assertThat(strictOnA).as("엄격 글자가 A인 문항은 축마다 2개씩 (콘텐츠 3-3)").isEqualTo(8);
	}

	@Test
	@DisplayName("시드를 다시 실행해도 행 수 · 선택지 id · 공유 주소가 그대로다 (응답 기록 · 공유 링크 보존)")
	void seed_rerun_isIdempotent() throws IOException {
		List<Map<String, Object>> before = snapshot();
		jdbc.execute(new ClassPathResource("db/migration/R__personality_content.sql").getContentAsString(StandardCharsets.UTF_8));
		assertThat(snapshot()).isEqualTo(before);
	}

	private List<Map<String, Object>> snapshot() {
		return jdbc.queryForList("""
			SELECT 'type' AS kind, t.code AS key, t.share_id AS value FROM personality_type t
			UNION ALL
			SELECT 'choice', q.display_order || '-' || c.display_order, c.id::text
			FROM personality_choice c JOIN personality_question q ON q.id = c.question_id
			ORDER BY 1, 2""");
	}

	private static List<String> allCodes() {
		List<String> codes = new ArrayList<>();
		for (String e : List.of("E", "I")) {
			for (String s : List.of("S", "N")) {
				for (String t : List.of("T", "F")) {
					for (String j : List.of("J", "P")) {
						codes.add(e + s + t + j);
					}
				}
			}
		}
		return codes;
	}
}
