package com.team2.project.rag;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.team2.project.common.exception.BusinessException;
import com.team2.project.rag.domain.CaseNumberHash;
import com.team2.project.rag.domain.RagPrecedentHit;
import com.team2.project.rag.embedding.EmbeddingException;
import com.team2.project.rag.embedding.QueryEmbeddingClient;
import com.team2.project.rag.service.RagSearchResult;
import com.team2.project.rag.service.RagSearchService;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/**
 * 사건 하나에 대한 유사 판례 검색 (BE-51)을 실제 PostgreSQL(pgvector)에서 확인한다.
 * 임베딩 API 대신 가짜 클라이언트를 쓴다. 벡터는 축 하나만 1인 3072차원 벡터로 만들어 유사도를 손으로 계산할 수 있게 한다.
 * 테스트마다 롤백된다.
 */
@SpringBootTest
@Transactional
class RagSearchServiceTest {

	private static final int DIM = 3072;

	@Autowired RagSearchService service;
	@Autowired JdbcTemplate jdbc;
	@Autowired FakeEmbeddingClient embedding;

	private Long caseId;

	@BeforeEach
	void setUp() {
		embedding.reset();
		jdbc.update("DELETE FROM rag_precedent");  // 비공개 시드(R__40)가 적재된 DB에서도 이 테스트의 행만 본다 (롤백됨)
		caseId = jdbc.queryForObject("""
			INSERT INTO legal_case (title, crime_type, charge_name, short_intro, overview, applied_law,
			    statutory_penalty_text, status)
			VALUES ('RAG 검색 시험 사건', 'MURDER', '살인', '소개', '비식별화한 사건 개요', '형법 제250조 제1항',
			    '사형, 무기 또는 5년 이상의 징역', 'PUBLISHED')
			RETURNING id""", Long.class);
		insertSource("FIRST", "2019고합123", false);
		insertSource("APPEAL", "2020노45", true);
	}

	@Test
	@DisplayName("자기 사건(1심 · 항소심 사건번호 해시가 있는 사건 그룹)을 통째로 빼고 비슷한 순으로 돌려준다")
	void searchSimilar_excludesOwnCaseGroup() {
		insertPrecedent("1", "G-SELF", CaseNumberHash.of("2019고합123"), vector(0, 1.0));  // 대상 사건 1심
		insertPrecedent("2", "G-SELF", null, vector(0, 1.0, 1, 0.01));                    // 같은 사건 다른 출처
		insertPrecedent("3", "G-APPEAL", CaseNumberHash.of("2020 노 45"), vector(0, 1.0)); // 대상 사건 항소심 (공백 무시)
		insertPrecedent("4", "G-NEAR", CaseNumberHash.of("2018고합9"), vector(0, 1.0, 1, 0.2));
		insertPrecedent("5", "G-FAR", null, vector(0, 1.0, 1, 1.0));
		embedding.next = vector(0, 1.0);

		RagSearchResult result = service.searchSimilar(caseId);

		assertThat(result.enabled()).isTrue();
		assertThat(result.precedents()).extracting(RagPrecedentHit::caseGroup).containsExactly("G-NEAR", "G-FAR");
		assertThat(result.precedents().get(0).score()).isGreaterThan(result.precedents().get(1).score());
		assertThat(result.precedents().get(0).factsSummary()).isEqualTo("사실관계 요약 4");
		// 검색어는 비식별화한 사건 개요뿐이다 (사건번호 · 원문은 보내지 않는다)
		assertThat(embedding.queries).containsExactly("비식별화한 사건 개요");
	}

	@Test
	@DisplayName("직접 지정한 사건 그룹도 뺀다 (공범 별도 판결 등)")
	void searchSimilar_excludesGivenGroups() {
		insertPrecedent("1", "G-CO", null, vector(0, 1.0));
		insertPrecedent("2", "G-OTHER", null, vector(0, 1.0, 1, 0.5));
		embedding.next = vector(0, 1.0);

		assertThat(service.searchSimilar(caseId, List.of("G-CO")).precedents())
			.extracting(RagPrecedentHit::caseGroup).containsExactly("G-OTHER");
	}

	@Test
	@DisplayName("점수 하한(0.6) 아래 판례는 빼고, top-k(5)까지만 돌려준다")
	void searchSimilar_appliesMinScoreAndTopK() {
		IntStream.range(0, 7).forEach(i -> insertPrecedent("1" + i, "G-K" + i, null, vector(0, 1.0, 1, 0.1 * (i + 1))));
		insertPrecedent("99", "G-ORTHOGONAL", null, vector(1, 1.0));  // 점수 0
		embedding.next = vector(0, 1.0);

		List<RagPrecedentHit> hits = service.searchSimilar(caseId).precedents();

		assertThat(hits).hasSize(5);
		assertThat(hits).allSatisfy(hit -> assertThat(hit.score()).isGreaterThanOrEqualTo(0.6));
	}

	@Test
	@DisplayName("API 키가 없으면 꺼짐을 알리고 임베딩을 부르지 않는다 (호출하는 쪽은 퓨샷으로 대체)")
	void searchSimilar_disabled_returnsDisabled() {
		embedding.enabled = false;

		RagSearchResult result = service.searchSimilar(caseId);

		assertThat(result.enabled()).isFalse();
		assertThat(result.precedents()).isEmpty();
		assertThat(embedding.queries).isEmpty();
	}

	@Test
	@DisplayName("판례 임베딩 모델이 검색어와 다르면 멈춘다")
	void searchSimilar_embeddingModelMismatch_isError() {
		jdbc.update("""
			INSERT INTO rag_precedent (source, source_ref, case_group, crime_type, court_level, facts_summary,
			    sentencing_reason, embedding, embedding_model, embedding_dim)
			VALUES ('LBOX', 'old', 'G-OLD', 'MURDER', 'FIRST', '요약', '양형 이유', ?::vector, 'other-embedding', 3072)""",
			literal(vector(0, 1.0)));

		assertThatThrownBy(() -> service.searchSimilar(caseId)).isInstanceOf(EmbeddingException.class)
			.hasMessageContaining("other-embedding");
		assertThat(embedding.queries).isEmpty();
	}

	@Test
	@DisplayName("없는 사건이면 CASE_NOT_FOUND")
	void searchSimilar_unknownCase_isNotFound() {
		assertThatThrownBy(() -> service.searchSimilar(-1L)).isInstanceOf(BusinessException.class);
	}

	private void insertSource(String level, String caseNumber, boolean isFinal) {
		jdbc.update("""
			INSERT INTO case_source (case_id, court_level, case_number, is_final, source_org)
			VALUES (?, ?, ?, ?, '법원 공개 판결문')""", caseId, level, caseNumber, isFinal);
	}

	private void insertPrecedent(String ref, String group, String caseNumberHash, float[] embeddingVector) {
		jdbc.update("""
			INSERT INTO rag_precedent (source, source_ref, case_number_hash, case_group, crime_type, court_level,
			    facts_summary, sentencing_reason, embedding, embedding_model, embedding_dim)
			VALUES ('LAW_GO_KR', ?, ?, ?, 'MURDER', 'FIRST', ?, '양형 이유', ?::vector, 'gemini-embedding-001', 3072)""",
			ref, caseNumberHash, group, "사실관계 요약 " + ref, literal(embeddingVector));
	}

	/** (축 번호, 값) 쌍으로 만든 3072차원 벡터. 나머지 축은 0 */
	private static float[] vector(Object... axisValues) {
		float[] values = new float[DIM];
		for (int i = 0; i < axisValues.length; i += 2) {
			values[(Integer) axisValues[i]] = ((Double) axisValues[i + 1]).floatValue();
		}
		return values;
	}

	private static String literal(float[] values) {
		return IntStream.range(0, values.length).mapToObj(i -> Float.toString(values[i]))
			.collect(Collectors.joining(",", "[", "]"));
	}

	/** 임베딩 API 대신 정해 둔 벡터를 돌려주고, 받은 검색어를 남긴다 */
	static class FakeEmbeddingClient implements QueryEmbeddingClient {

		boolean enabled = true;
		float[] next;
		final List<String> queries = new ArrayList<>();

		void reset() {
			enabled = true;
			next = null;
			queries.clear();
		}

		@Override
		public boolean enabled() {
			return enabled;
		}

		@Override
		public String model() {
			return "gemini-embedding-001";
		}

		@Override
		public float[] embedQuery(String text) {
			queries.add(text);
			return next;
		}
	}

	@TestConfiguration
	static class FakeEmbeddingConfig {

		@Bean
		@Primary
		FakeEmbeddingClient fakeEmbeddingClient() {
			return new FakeEmbeddingClient();
		}
	}
}
