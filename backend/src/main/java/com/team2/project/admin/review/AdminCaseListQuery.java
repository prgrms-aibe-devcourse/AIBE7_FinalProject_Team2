package com.team2.project.admin.review;

import com.team2.project.admin.review.dto.AdminCaseListResponse.Item;
import com.team2.project.admin.review.dto.AdminCaseListResponse.Pending;
import com.team2.project.legalcase.domain.CaseStatus;
import com.team2.project.legalcase.domain.CrimeType;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * A-5 검수 사건 목록 조회. 사건마다 대기 후보 수 · 공개 판결 여부 · 최근 활동 시각을 한 번에 집계해야 해서 SQL로 읽는다.
 * "대기 후보" = AI · 재판부 판결 중 검수 기록이 없고(review_status NULL) 비공개인 것 (Judgment.reviewState()와 같은 규칙)
 */
@Repository
@RequiredArgsConstructor
public class AdminCaseListQuery {

	private static final ZoneId KST = ZoneId.of("Asia/Seoul");

	// 사건별 집계. 최근 활동 = 사건 수정 시각과 그 사건 후보의 적재 · 검수 시각 중 가장 늦은 값 (API 명세 A-5)
	private static final String SUMMARY = """
		SELECT c.id, c.title, c.crime_type, c.status, c.created_at, c.updated_at,
		       (SELECT count(*) FROM judgment j WHERE j.case_id = c.id AND j.subject_type = 'AI'
		            AND j.review_status IS NULL AND NOT j.is_published) AS ai_pending,
		       (SELECT count(*) FROM judgment j WHERE j.case_id = c.id AND j.subject_type = 'COURT'
		            AND j.review_status IS NULL AND NOT j.is_published) AS court_pending,
		       (SELECT count(*) FROM factor f WHERE f.case_id = c.id AND f.value_axis_status = 'AUTO') AS axis_auto,
		       EXISTS (SELECT 1 FROM judgment j WHERE j.case_id = c.id AND j.subject_type = 'AI' AND j.is_published) AS has_ai,
		       EXISTS (SELECT 1 FROM judgment j WHERE j.case_id = c.id AND j.subject_type = 'COURT' AND j.is_published) AS has_court,
		       GREATEST(c.updated_at,
		                (SELECT max(GREATEST(j.created_at, COALESCE(j.reviewed_at, j.created_at)))
		                 FROM judgment j WHERE j.case_id = c.id AND j.subject_type IN ('AI', 'COURT'))) AS last_activity
		FROM legal_case c
		WHERE c.status IN (:statuses)
		""";

	private static final String PENDING_FILTER = """
		WHERE CAST(:pending AS boolean) IS NULL OR CAST(:pending AS boolean) = (ai_pending + court_pending > 0)
		""";

	private final NamedParameterJdbcTemplate jdbc;

	public List<Item> find(Set<CaseStatus> statuses, Boolean pending, int page, int size) {
		MapSqlParameterSource params = params(statuses, pending)
			.addValue("limit", size)
			.addValue("offset", (long) page * size);
		return jdbc.query("SELECT * FROM (" + SUMMARY + ") s " + PENDING_FILTER
			+ "ORDER BY last_activity DESC, id DESC LIMIT :limit OFFSET :offset", params, AdminCaseListQuery::toItem);
	}

	public long count(Set<CaseStatus> statuses, Boolean pending) {
		Long total = jdbc.queryForObject("SELECT count(*) FROM (" + SUMMARY + ") s " + PENDING_FILTER,
			params(statuses, pending), Long.class);
		return total == null ? 0 : total;
	}

	private static MapSqlParameterSource params(Set<CaseStatus> statuses, Boolean pending) {
		return new MapSqlParameterSource()
			.addValue("statuses", statuses.stream().map(Enum::name).toList())
			.addValue("pending", pending);
	}

	private static Item toItem(ResultSet rs, int rowNum) throws SQLException {
		return new Item(
			rs.getLong("id"),
			rs.getString("title"),
			CrimeType.valueOf(rs.getString("crime_type")),
			CaseStatus.valueOf(rs.getString("status")),
			new Pending(rs.getInt("ai_pending"), rs.getInt("court_pending"), rs.getInt("axis_auto")),
			rs.getBoolean("has_ai"),
			rs.getBoolean("has_court"),
			kst(rs.getTimestamp("created_at")),
			kst(rs.getTimestamp("updated_at")),
			kst(rs.getTimestamp("last_activity")));
	}

	private static OffsetDateTime kst(Timestamp timestamp) {
		return timestamp == null ? null : timestamp.toInstant().atZone(KST).toOffsetDateTime();
	}
}
