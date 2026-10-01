package com.team2.project.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.team2.project.comparison.domain.ComparisonAnalysis;
import com.team2.project.comparison.repository.ComparisonAnalysisRepository;
import com.team2.project.experience.domain.AnonymousUser;
import com.team2.project.experience.domain.Experience;
import com.team2.project.experience.repository.AnonymousUserRepository;
import com.team2.project.experience.repository.ExperienceRepository;
import com.team2.project.legalcase.domain.CaseSection;
import com.team2.project.legalcase.domain.CrimeType;
import com.team2.project.legalcase.domain.Factor;
import com.team2.project.legalcase.domain.LegalCase;
import com.team2.project.legalcase.repository.CaseSectionRepository;
import com.team2.project.legalcase.repository.FactorRepository;
import com.team2.project.legalcase.repository.LegalCaseRepository;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/**
 * Repository 조회 조건 · 정렬과 DB 제약조건 검증 (실제 PostgreSQL, 테스트마다 롤백)
 * 기존 시드 데이터가 DB에 있을 수 있어서, 목록 조회는 이 테스트가 넣은 사건 ID만 골라서 비교한다.
 * 제약 위반은 이후 트랜잭션을 중단시키므로 테스트 하나에서 한 가지만 확인한다.
 */
@SpringBootTest
@Transactional
class RepositoryQueryIntegrationTest {

	@Autowired JdbcTemplate jdbc;
	@Autowired LegalCaseRepository legalCaseRepository;
	@Autowired CaseSectionRepository caseSectionRepository;
	@Autowired FactorRepository factorRepository;
	@Autowired AnonymousUserRepository anonymousUserRepository;
	@Autowired ExperienceRepository experienceRepository;
	@Autowired ComparisonAnalysisRepository comparisonAnalysisRepository;

	private Long insertCase(String title, String crimeType, String status, String publishedAt) {
		return jdbc.queryForObject("""
			INSERT INTO legal_case (title, crime_type, charge_name, short_intro, keywords, overview,
			    applied_law, statutory_penalty_text, status, published_at)
			VALUES (?, ?, '죄명', '소개', '[]'::jsonb, '개요', '법령', '법정형', ?, ?::timestamptz)
			RETURNING id""", Long.class, title, crimeType, status, publishedAt);
	}

	/** JDBC로 바로 참조하므로 저장 직후 flush해서 INSERT를 먼저 보낸다 */
	private UUID newUserId() {
		return anonymousUserRepository.saveAndFlush(AnonymousUser.issue(Instant.now())).getId();
	}

	private Experience startExperience(Long caseId) {
		AnonymousUser user = anonymousUserRepository.save(AnonymousUser.issue(Instant.now()));
		return experienceRepository.save(Experience.start(user, legalCaseRepository.getReferenceById(caseId)));
	}

	@Test
	@DisplayName("공개된 사건만 최신 공개순으로 조회한다")
	void findAllPublished_draftExcluded_orderedByPublishedAtDesc() {
		Long oldest = insertCase("오래된 사건", "MURDER", "PUBLISHED", "2099-01-01T00:00:00Z");
		Long newest = insertCase("최신 사건", "FRAUD", "PUBLISHED", "2099-03-01T00:00:00Z");
		Long middle = insertCase("중간 사건", "MURDER", "PUBLISHED", "2099-02-01T00:00:00Z");
		Long draft = insertCase("비공개 사건", "MURDER", "DRAFT", "2099-04-01T00:00:00Z");
		Set<Long> mine = Set.of(oldest, newest, middle, draft);

		List<Long> result = legalCaseRepository.findAllPublished().stream()
			.map(LegalCase::getId).filter(mine::contains).toList();

		assertThat(result).containsExactly(newest, middle, oldest);
	}

	@Test
	@DisplayName("범죄 유형으로 공개 사건을 거른다")
	void findAllPublishedByCrimeType_otherTypeAndDraft_areExcluded() {
		Long murder = insertCase("살인 사건", "MURDER", "PUBLISHED", "2099-01-01T00:00:00Z");
		Long fraud = insertCase("사기 사건", "FRAUD", "PUBLISHED", "2099-01-02T00:00:00Z");
		Long draftMurder = insertCase("비공개 살인 사건", "MURDER", "DRAFT", "2099-01-03T00:00:00Z");
		Set<Long> mine = Set.of(murder, fraud, draftMurder);

		List<Long> result = legalCaseRepository.findAllPublishedByCrimeType(CrimeType.MURDER).stream()
			.map(LegalCase::getId).filter(mine::contains).toList();

		assertThat(result).containsExactly(murder);
	}

	@Test
	@DisplayName("비공개 사건은 단건 조회에서도 없는 것으로 본다")
	void findPublishedById_draft_isEmpty() {
		Long draft = insertCase("비공개 사건", "MURDER", "DRAFT", "2099-01-01T00:00:00Z");
		Long published = insertCase("공개 사건", "MURDER", "PUBLISHED", "2099-01-01T00:00:00Z");

		assertThat(legalCaseRepository.findPublishedById(draft)).isEmpty();
		assertThat(legalCaseRepository.findPublishedById(published)).isPresent();
	}

	@Test
	@DisplayName("섹션은 단계 안에서 표시 순서대로, 판단 요소는 표시 순서대로 조회한다")
	void findAllByCaseId_sectionsAndFactors_areOrderedByDisplayOrder() {
		Long caseId = insertCase("정렬 사건", "MURDER", "PUBLISHED", "2099-01-01T00:00:00Z");
		jdbc.update("""
			INSERT INTO case_section (case_id, stage, section_type, title, data, display_order)
			VALUES (?, 'DETAIL', 'DAMAGE', '두 번째', '[]'::jsonb, 2),
			       (?, 'DETAIL', 'DAMAGE', '첫 번째', '[]'::jsonb, 1)""", caseId, caseId);
		jdbc.update("""
			INSERT INTO factor (case_id, label, reveal_stage, summary_tag, display_order)
			VALUES (?, '나중 요소', 'DETAIL', '태그', 5), (?, '먼저 요소', 'OVERVIEW', '태그', 1)""", caseId, caseId);

		assertThat(caseSectionRepository.findAllByCaseId(caseId)).extracting(CaseSection::getTitle)
			.containsExactly("첫 번째", "두 번째");
		assertThat(factorRepository.findAllByCaseId(caseId)).extracting(Factor::getLabel)
			.containsExactly("먼저 요소", "나중 요소");
	}

	@Test
	@DisplayName("내 체험은 가장 최근 회차를 돌려준다")
	void findLatest_multipleAttempts_returnsHighestAttemptNo() {
		Long caseId = insertCase("회차 사건", "MURDER", "PUBLISHED", "2099-01-01T00:00:00Z");
		Experience first = startExperience(caseId);
		experienceRepository.flush();
		UUID userId = first.getAnonymousUser().getId();
		Long secondId = jdbc.queryForObject("""
			INSERT INTO experience (anonymous_user_id, case_id, attempt_no) VALUES (?, ?, 2) RETURNING id""",
			Long.class, userId, caseId);

		Experience latest = experienceRepository.findLatest(userId, caseId).orElseThrow();

		assertThat(latest.getId()).isEqualTo(secondId);
		assertThat(latest.getAttemptNo()).isEqualTo(2);
		assertThat(experienceRepository.findLatest(userId, caseId + 1_000_000)).isEmpty();
	}

	@Test
	@DisplayName("참여자 수는 1회차이면서 완료한 체험만 센다")
	void countCompletedFirstAttempts_onlyCompletedFirstAttempt_isCounted() {
		Long caseId = insertCase("참여자 사건", "MURDER", "PUBLISHED", "2099-01-01T00:00:00Z");
		Long otherCaseId = insertCase("다른 사건", "MURDER", "PUBLISHED", "2099-01-01T00:00:00Z");
		String sql = "INSERT INTO experience (anonymous_user_id, case_id, attempt_no, status) VALUES (?, ?, ?, ?)";
		jdbc.update(sql, newUserId(), caseId, 1, "COMPLETED");
		jdbc.update(sql, newUserId(), caseId, 1, "COMPLETED");
		jdbc.update(sql, newUserId(), caseId, 1, "AI_REVEALED");
		jdbc.update(sql, newUserId(), caseId, 2, "COMPLETED");
		jdbc.update(sql, newUserId(), otherCaseId, 1, "COMPLETED");

		assertThat(experienceRepository.countCompletedFirstAttempts(caseId)).isEqualTo(2);
		assertThat(experienceRepository.countCompletedFirstAttempts(otherCaseId)).isEqualTo(1);
	}

	@Test
	@DisplayName("체험의 비교 분석을 체험 ID로 조회한다")
	void findByExperienceId_savedAnalysis_isFound() {
		Long caseId = insertCase("비교 사건", "MURDER", "PUBLISHED", "2099-01-01T00:00:00Z");
		Experience withAnalysis = startExperience(caseId);
		Experience without = startExperience(caseId);
		comparisonAnalysisRepository.save(ComparisonAnalysis.pending(withAnalysis, "model", "v1"));

		assertThat(comparisonAnalysisRepository.findByExperienceId(withAnalysis.getId())).isPresent();
		assertThat(comparisonAnalysisRepository.findByExperienceId(without.getId())).isEmpty();
	}

	@Test
	@DisplayName("사건마다 같은 형벌 규칙은 한 번만 넣을 수 있다")
	void penaltyRule_duplicatePenaltyType_isRejected() {
		Long caseId = insertCase("형벌 사건", "MURDER", "PUBLISHED", "2099-01-01T00:00:00Z");
		String sql = """
			INSERT INTO penalty_rule (case_id, penalty_type, allowed_min, allowed_max, suspension_allowed, display_order)
			VALUES (?, 'PRISON', 30, 360, true, ?)""";
		jdbc.update(sql, caseId, 1);

		assertThatThrownBy(() -> jdbc.update(sql, caseId, 2))
			.isInstanceOf(DataIntegrityViolationException.class)
			.hasMessageContaining("uk_penalty_rule_case_penalty_type");
	}

	@Test
	@DisplayName("같은 사용자는 같은 사건을 같은 회차로 두 번 시작할 수 없다")
	void experience_duplicateUserCaseAttempt_isRejected() {
		Long caseId = insertCase("중복 체험 사건", "MURDER", "PUBLISHED", "2099-01-01T00:00:00Z");
		Experience experience = startExperience(caseId);
		experienceRepository.flush();

		assertThatThrownBy(() -> jdbc.update(
			"INSERT INTO experience (anonymous_user_id, case_id, attempt_no) VALUES (?, ?, 1)",
			experience.getAnonymousUser().getId(), caseId))
			.isInstanceOf(DataIntegrityViolationException.class)
			.hasMessageContaining("uk_experience_user_case_attempt");
	}

	@Test
	@DisplayName("사건마다 공개된 AI 판결은 1건만 넣을 수 있다")
	void judgment_duplicatePublishedAi_isRejected() {
		Long caseId = insertCase("판결 사건", "MURDER", "PUBLISHED", "2099-01-01T00:00:00Z");
		String sql = """
			INSERT INTO judgment (case_id, subject_type, timing, penalty_type, prison_months, is_published)
			VALUES (?, 'AI', 'FINAL', 'PRISON', ?, ?)""";
		jdbc.update(sql, caseId, 144, true);
		// 비공개 판결은 몇 건이든 넣을 수 있다
		jdbc.update(sql, caseId, 100, false);

		assertThatThrownBy(() -> jdbc.update(sql, caseId, 120, true))
			.isInstanceOf(DataIntegrityViolationException.class)
			.hasMessageContaining("uk_judgment_published");
	}
}
