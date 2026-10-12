package com.team2.project.rag.repository;

import com.team2.project.rag.domain.RagPrecedentHit;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * rag_precedent 조회 (V11). 벡터 타입은 JPA로 다루지 않고 JdbcTemplate으로 검색 함수 rag_precedent_search를 부른다.
 * 행은 비공개 시드(R__40)가 넣으므로 쓰기 메서드는 두지 않는다.
 */
@Repository
@RequiredArgsConstructor
public class RagPrecedentRepository {

	private static final String SEARCH_SQL = """
		SELECT p.id, s.case_group, s.score, p.charge_name, p.court_level, p.incident_date, p.guideline_id,
		       p.penalty_type, p.prison_months, p.suspension_months, p.fine_amount, p.facts_summary,
		       p.sentencing_reason, p.sentencing_factors::text AS sentencing_factors, p.flags::text AS flags
		FROM rag_precedent_search(?::vector, ?, ?::text[], ?::text[], ?, ?) s
		JOIN rag_precedent p ON p.id = s.precedent_id
		ORDER BY s.score DESC, p.id""";

	private final JdbcTemplate jdbc;

	/**
	 * 자기 사건을 뺀 유사 판례 (사건 그룹당 1건, 점수 높은 순)
	 *
	 * @param excludeHashes 판결하려는 사건의 사건번호 해시. 그 해시가 있는 사건 그룹 전체를 뺀다
	 * @param excludeGroups 직접 뺄 사건 그룹
	 */
	public List<RagPrecedentHit> search(float[] query, String crimeType, Collection<String> excludeHashes,
		Collection<String> excludeGroups, int topK, double minScore) {
		return jdbc.query(SEARCH_SQL, RagPrecedentRepository::toHit, vectorLiteral(query), crimeType,
			excludeHashes.toArray(String[]::new), excludeGroups.toArray(String[]::new), topK, minScore);
	}

	/** 저장된 판례의 (임베딩 모델, 차원) 종류. 검색어 임베딩과 다르면 비교할 수 없다 */
	public List<String> findEmbeddingSpecs() {
		return jdbc.queryForList(
			"SELECT DISTINCT embedding_model || ':' || embedding_dim FROM rag_precedent ORDER BY 1", String.class);
	}

	static String vectorLiteral(float[] vector) {
		StringBuilder literal = new StringBuilder(vector.length * 12).append('[');
		for (int i = 0; i < vector.length; i++) {
			if (i > 0) {
				literal.append(',');
			}
			literal.append(vector[i]);
		}
		return literal.append(']').toString();
	}

	private static RagPrecedentHit toHit(ResultSet rs, int rowNum) throws SQLException {
		return new RagPrecedentHit(
			rs.getLong("id"),
			rs.getString("case_group"),
			rs.getDouble("score"),
			rs.getString("charge_name"),
			rs.getString("court_level"),
			rs.getObject("incident_date", LocalDate.class),
			rs.getObject("guideline_id", Long.class),
			rs.getString("penalty_type"),
			rs.getObject("prison_months", Integer.class),
			rs.getObject("suspension_months", Integer.class),
			rs.getObject("fine_amount", Long.class),
			rs.getString("facts_summary"),
			rs.getString("sentencing_reason"),
			rs.getString("sentencing_factors"),
			rs.getString("flags"));
	}
}
