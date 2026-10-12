package com.team2.project.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/**
 * RAG 판례 저장소(rag_precedent)와 유사 판례 검색 함수(rag_precedent_search)를 실제 PostgreSQL(pgvector)에서 검증한다 (BE-51).
 *
 * 검색은 판결하려는 사건 자체(같은 사건 그룹의 다른 심급 포함)를 빼야 한다. 그렇지 않으면 실제 판결 결과가 AI에게 노출된다.
 * 벡터는 축 하나만 1인 3072차원 단위 벡터와 그 조합으로 만들어, 코사인 유사도를 손으로 계산할 수 있게 한다.
 * 테스트마다 롤백된다.
 */
@SpringBootTest
@Transactional
class RagPrecedentSearchTest {

	private static final int DIM = 3072;

	@Autowired JdbcTemplate jdbc;

	/** 비공개 시드(R__40)가 적재된 DB에서도 이 테스트의 행만 보게 한다 (테스트마다 롤백되므로 실제 행은 남는다) */
	@BeforeEach
	void clearSeededPrecedents() {
		jdbc.update("DELETE FROM rag_precedent");
	}

	@Test
	@DisplayName("가까운 순으로 돌려주고, 점수 하한 아래 · 다른 죄명 판례는 뺀다")
	void search_ordersByCosineSimilarity_andFiltersByScoreAndCrime() {
		long near = insert("LAW_GO_KR", "1", "G-A", hash('a'), "MURDER", vector(0, 1.0, 1, 0.1));
		long mid = insert("LAW_GO_KR", "2", "G-B", hash('b'), "MURDER", vector(0, 1.0, 1, 1.0));
		insert("LBOX", "3", "G-C", null, "MURDER", vector(1, 1.0));          // 질의와 직교 (점수 0)
		insert("LAW_GO_KR", "4", "G-D", hash('d'), "FRAUD", vector(0, 1.0)); // 다른 죄명

		List<Long> ids = search(vector(0, 1.0), "MURDER", null, null, 5, 0.5);

		assertThat(ids).containsExactly(near, mid);
	}

	@Test
	@DisplayName("판결하려는 사건의 사건번호 해시가 있는 사건 그룹은 다른 심급까지 통째로 뺀다")
	void search_excludesWholeGroupOfTargetCaseNumber() {
		insert("LAW_GO_KR", "10", "G-SELF", hash('1'), "MURDER", vector(0, 1.0));             // 대상 사건 1심
		insert("LAW_GO_KR", "11", "G-SELF", hash('2'), "MURDER", vector(0, 1.0, 1, 0.01));    // 같은 사건 항소심 (해시 다름)
		insert("LBOX", "12", "G-SELF", null, "MURDER", vector(0, 1.0, 2, 0.01));              // 같은 사건 다른 출처 (해시 없음)
		long other = insert("LAW_GO_KR", "13", "G-OTHER", hash('3'), "MURDER", vector(0, 1.0, 1, 0.5));

		List<Long> ids = search(vector(0, 1.0), "MURDER", new String[] {hash('1')}, null, 5, 0.0);

		assertThat(ids).containsExactly(other);
	}

	@Test
	@DisplayName("직접 지정한 사건 그룹을 빼고, 같은 사건 그룹은 가장 가까운 판례 1건만 돌려준다")
	void search_excludesGivenGroups_andReturnsOnePerGroup() {
		long groupBest = insert("LAW_GO_KR", "20", "G-PAIR", hash('4'), "MURDER", vector(0, 1.0, 1, 0.1));
		insert("LAW_GO_KR", "21", "G-PAIR", hash('5'), "MURDER", vector(0, 1.0, 1, 0.3));     // 같은 사건의 덜 가까운 판결
		insert("LBOX", "22", "G-SKIP", null, "MURDER", vector(0, 1.0));                       // 가장 가깝지만 제외 그룹
		long single = insert("LBOX", "23", "G-SINGLE", null, "MURDER", vector(0, 1.0, 1, 0.6));

		List<Long> ids = search(vector(0, 1.0), "MURDER", null, new String[] {"G-SKIP"}, 5, 0.0);

		assertThat(ids).containsExactly(groupBest, single);
	}

	@Test
	@DisplayName("top_k만큼만 돌려준다")
	void search_limitsToTopK() {
		IntStream.range(0, 4).forEach(i -> insert("LBOX", "3" + i, "G-K" + i, null, "MURDER", vector(0, 1.0, 1, 0.1 * (i + 1))));

		assertThat(search(vector(0, 1.0), "MURDER", null, null, 2, 0.0)).hasSize(2);
	}

	@Test
	@DisplayName("사용 보류 판례는 검색에서 빼지만, 그 판례의 사건번호 해시로도 자기 사건 그룹을 뺀다")
	void search_excludesOnHold_butHoldStillCountsForSelfExclusion() {
		long held = insert("LBOX", "40", "G-HOLD", null, "MURDER", vector(0, 1.0));
		long kept = insert("LAW_GO_KR", "41", "G-KEPT", hash('6'), "MURDER", vector(0, 1.0, 1, 0.2));
		insert("LAW_GO_KR", "42", "G-SELF-HELD", hash('7'), "MURDER", vector(0, 1.0, 1, 0.1));
		insert("LBOX", "43", "G-SELF-HELD", null, "MURDER", vector(0, 1.0, 1, 0.05));
		jdbc.update("UPDATE rag_precedent SET on_hold = true, hold_reason = '라이선스 확인 전' WHERE id = ? OR source_ref = '42'", held);

		assertThat(search(vector(0, 1.0), "MURDER", new String[] {hash('7')}, null, 5, 0.0)).containsExactly(kept);
	}

	@Test
	@DisplayName("보류하면 사유가 있어야 하고, 보류가 아니면 사유가 없어야 한다")
	void hold_reasonMustMatchFlag() {
		long id = insert("LBOX", "44", "G-H", null, "MURDER", vector(0, 1.0));
		assertThatThrownBy(() -> jdbc.update("UPDATE rag_precedent SET on_hold = true WHERE id = ?", id))
			.isInstanceOf(DataIntegrityViolationException.class)
			.hasMessageContaining("chk_rag_precedent_hold");
	}

	@Test
	@DisplayName("임베딩 차원 기록이 실제 벡터 차원과 다르면 거절한다")
	void embeddingDim_mismatch_isRejected() {
		assertThatThrownBy(() -> jdbc.update("""
			INSERT INTO rag_precedent (source, source_ref, case_group, crime_type, court_level, facts_summary,
			    sentencing_reason, embedding, embedding_model, embedding_dim)
			VALUES ('LBOX', '99', 'G-X', 'MURDER', 'FIRST', '요약', '양형 이유', ?::vector, 'gemini-embedding-001', 768)""",
			vector(0, 1.0)))
			.isInstanceOf(DataIntegrityViolationException.class)
			.hasMessageContaining("chk_rag_precedent_embedding_dim");
	}

	@Test
	@DisplayName("사건번호 해시는 SHA-256 hex 64자만 받는다 (사건번호 원문을 넣지 않게)")
	void caseNumberHash_plainCaseNumber_isRejected() {
		assertThatThrownBy(() -> insert("LAW_GO_KR", "98", "G-Y", "2020고합123", "MURDER", vector(0, 1.0)))
			.isInstanceOf(DataIntegrityViolationException.class)
			.hasMessageContaining("chk_rag_precedent_case_number_hash");
	}

	private long insert(String source, String ref, String group, String caseNumberHash, String crime, String embedding) {
		return jdbc.queryForObject("""
			INSERT INTO rag_precedent (source, source_ref, case_number_hash, case_group, crime_type, court_level,
			    facts_summary, sentencing_reason, embedding, embedding_model, embedding_dim)
			VALUES (?, ?, ?, ?, ?, 'FIRST', '사실관계 요약', '양형 이유', ?::vector, 'gemini-embedding-001', 3072)
			RETURNING id""", Long.class, source, ref, caseNumberHash, group, crime, embedding);
	}

	private List<Long> search(String query, String crime, String[] hashes, String[] groups, int topK, double minScore) {
		return jdbc.queryForList(
			"SELECT precedent_id FROM rag_precedent_search(?::vector, ?, ?::text[], ?::text[], ?, ?)",
			Long.class, query, crime, hashes, groups, topK, minScore);
	}

	/** (축 번호, 값) 쌍으로 만든 3072차원 벡터 리터럴. 나머지 축은 0 */
	private static String vector(Object... axisValues) {
		double[] values = new double[DIM];
		for (int i = 0; i < axisValues.length; i += 2) {
			values[(Integer) axisValues[i]] = (Double) axisValues[i + 1];
		}
		return IntStream.range(0, DIM).mapToObj(i -> Double.toString(values[i])).collect(Collectors.joining(",", "[", "]"));
	}

	private static String hash(char c) {
		return String.valueOf(c).repeat(64);
	}
}
