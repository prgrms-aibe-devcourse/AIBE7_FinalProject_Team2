package com.team2.project.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.team2.project.judgment.domain.Judgment;
import com.team2.project.judgment.domain.ReviewStatus;
import com.team2.project.judgment.repository.JudgmentRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/**
 * 판결 후보 검수 기록(V16) 매핑 · 제약 (BE-41, WBS 7.4.7). 실제 PostgreSQL, 테스트마다 롤백.
 * 제약 위반은 트랜잭션을 깨뜨리므로 테스트마다 마지막 문장에서만 확인한다.
 */
@SpringBootTest
@Transactional
class JudgmentReviewPersistenceTest {

	@Autowired JdbcTemplate jdbc;
	@Autowired EntityManager em;
	@Autowired JudgmentRepository judgmentRepository;

	private Long caseId;

	@BeforeEach
	void setUp() {
		caseId = jdbc.queryForObject("""
			INSERT INTO legal_case (title, crime_type, charge_name, short_intro, overview, applied_law,
			    statutory_penalty_text, status)
			VALUES ('검수 시험 사건 ' || gen_random_uuid(), 'MURDER', '살인', '소개', '개요', '형법 제250조 제1항',
			    '사형, 무기 또는 5년 이상의 징역', 'DRAFT')
			RETURNING id""", Long.class);
	}

	private Long candidate(String subjectType, boolean published) {
		return jdbc.queryForObject("""
			INSERT INTO judgment (case_id, subject_type, timing, penalty_type, prison_months, is_published)
			VALUES (?, ?, 'FINAL', 'PRISON', 120, ?) RETURNING id""", Long.class, caseId, subjectType, published);
	}

	private Judgment read(Long id) {
		em.clear();
		return judgmentRepository.findById(id).orElseThrow();
	}

	@Test
	@DisplayName("검수 기록이 없으면 공개 중인 후보는 승인, 아니면 대기로 본다 (시드 · 파이프라인은 검수 칸을 채우지 않는다)")
	void reviewState_withoutRecord() {
		assertThat(read(candidate("AI", false)).reviewState()).isEqualTo(ReviewStatus.PENDING);
		assertThat(read(candidate("COURT", true)).reviewState()).isEqualTo(ReviewStatus.APPROVED);
	}

	@Test
	@DisplayName("재판부 후보도 반려 · 사유 · 검수 시각을 저장하고 읽는다")
	void courtRejection_mapped() {
		Long id = candidate("COURT", false);
		jdbc.update("""
			UPDATE judgment SET review_status = 'REJECTED', review_note = '다른 사건 판결문', reviewed_at = now()
			WHERE id = ?""", id);
		Judgment judgment = read(id);
		assertThat(judgment.getReviewStatus()).isEqualTo(ReviewStatus.REJECTED);
		assertThat(judgment.reviewState()).isEqualTo(ReviewStatus.REJECTED);
		assertThat(judgment.getReviewNote()).isEqualTo("다른 사건 판결문");
		assertThat(judgment.getReviewedAt()).isNotNull();
		assertThat(judgment.getReviewedByAdminId()).isNull();
	}

	@Test
	@DisplayName("저장하는 검수 값은 승인 · 반려뿐이다 (대기는 NULL)")
	void pendingValue_rejected() {
		Long id = candidate("AI", false);
		assertThatThrownBy(() -> jdbc.update("UPDATE judgment SET review_status = 'PENDING' WHERE id = ?", id))
			.isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("chk_judgment_review_status");
	}

	@Test
	@DisplayName("반려한 후보는 공개할 수 없다")
	void rejectedPublished_rejected() {
		Long id = candidate("AI", true);
		assertThatThrownBy(() -> jdbc.update("UPDATE judgment SET review_status = 'REJECTED' WHERE id = ?", id))
			.isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("chk_judgment_review_rejected");
	}

	@Test
	@DisplayName("반려 사유는 반려에만 둔다")
	void noteWithoutRejection_rejected() {
		Long id = candidate("COURT", true);
		assertThatThrownBy(() -> jdbc.update(
			"UPDATE judgment SET review_status = 'APPROVED', review_note = '메모' WHERE id = ?", id))
			.isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("chk_judgment_review_note");
	}

	@Test
	@DisplayName("모르는 관리자 id는 FK가 막는다")
	void unknownAdmin_rejected() {
		Long id = candidate("AI", false);
		assertThatThrownBy(() -> jdbc.update(
			"UPDATE judgment SET review_status = 'APPROVED', reviewed_by_admin_id = -1 WHERE id = ?", id))
			.isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("fk_judgment_reviewed_by_admin");
	}
}
