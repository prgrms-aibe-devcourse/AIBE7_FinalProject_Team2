package com.team2.project.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.team2.project.experience.domain.AnonymousUser;
import com.team2.project.experience.domain.Experience;
import com.team2.project.experience.domain.ExperienceStatus;
import com.team2.project.experience.domain.InvalidExperienceStateException;
import com.team2.project.experience.domain.InvalidReviewStepException;
import com.team2.project.experience.domain.ReviewStepOutOfOrderException;
import com.team2.project.experience.repository.AnonymousUserRepository;
import com.team2.project.experience.repository.ExperienceRepository;
import com.team2.project.judgment.domain.Direction;
import com.team2.project.judgment.domain.ExtraDispositionType;
import com.team2.project.judgment.domain.InvalidJudgmentException;
import com.team2.project.judgment.domain.Judgment;
import com.team2.project.judgment.domain.JudgmentFactor;
import com.team2.project.judgment.domain.SubjectType;
import com.team2.project.judgment.repository.JudgmentFactorRepository;
import com.team2.project.judgment.repository.JudgmentRepository;
import com.team2.project.legalcase.domain.CaseSection;
import com.team2.project.legalcase.domain.CrimeType;
import com.team2.project.legalcase.domain.Factor;
import com.team2.project.legalcase.domain.LegalCase;
import com.team2.project.legalcase.domain.PenaltyType;
import com.team2.project.legalcase.domain.RangeKind;
import com.team2.project.legalcase.domain.RevealStage;
import com.team2.project.legalcase.domain.SentenceRangeOption;
import com.team2.project.legalcase.repository.CaseSectionRepository;
import com.team2.project.legalcase.repository.FactorRepository;
import com.team2.project.legalcase.repository.LegalCaseRepository;
import com.team2.project.legalcase.repository.PenaltyRuleRepository;
import com.team2.project.legalcase.repository.SentenceRangeOptionRepository;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/**
 * 엔티티 매핑 · Repository 조회 검증 (실제 PostgreSQL, 테스트마다 롤백)
 * 사건 콘텐츠와 AI · 재판부 판결은 운영처럼 SQL로 넣고, 사용자 판단은 엔티티로 저장한다.
 */
@SpringBootTest
@Transactional
class JpaMappingTest {

	@Autowired JdbcTemplate jdbc;
	@Autowired EntityManager em;
	@Autowired LegalCaseRepository legalCaseRepository;
	@Autowired CaseSectionRepository caseSectionRepository;
	@Autowired PenaltyRuleRepository penaltyRuleRepository;
	@Autowired FactorRepository factorRepository;
	@Autowired SentenceRangeOptionRepository rangeOptionRepository;
	@Autowired AnonymousUserRepository anonymousUserRepository;
	@Autowired ExperienceRepository experienceRepository;
	@Autowired JudgmentRepository judgmentRepository;
	@Autowired JudgmentFactorRepository judgmentFactorRepository;

	private Long caseId;
	private Long overviewFactorId;
	private Long detailFactorId;

	@BeforeEach
	void setUp() {
		// 시드 규칙대로 ID를 지정하지 않고 넣는다
		caseId = jdbc.queryForObject("""
			INSERT INTO legal_case (title, crime_type, charge_name, short_intro, keywords, overview,
			    applied_law, statutory_penalty_text, status, published_at)
			VALUES ('시험 살인 사건', 'MURDER', '살인', '소개', '["지인 사이", "우발적 상황"]'::jsonb, '개요',
			    '형법 제250조 제1항', '사형, 무기 또는 5년 이상의 징역', 'PUBLISHED', now())
			RETURNING id""", Long.class);
		jdbc.update("""
			INSERT INTO case_section (case_id, stage, section_type, title, data, display_order)
			VALUES (?, 'DETAIL', 'DAMAGE', '피해 결과', '[{"label": "피해자 수", "value": "1명"}]'::jsonb, 1),
			       (?, 'LAW', 'LAW_TERM', '용어 설명', '[{"term": "작량감경", "desc": "재판상 감경"}]'::jsonb, 1),
			       (?, 'SUMMARY', 'SUMMARY', '핵심 사실 요약', '["지인 1명을 살해", "형사처벌 전력 없음"]'::jsonb, 1)""",
			caseId, caseId, caseId);
		jdbc.update("""
			INSERT INTO penalty_rule (case_id, penalty_type, statutory_min, statutory_max, allowed_min, allowed_max,
			    suspension_allowed, display_order)
			VALUES (?, 'DEATH', NULL, NULL, 240, 600, false, 1),
			       (?, 'LIFE',  NULL, NULL, 120, 600, false, 2),
			       (?, 'PRISON', 60,  360,  30, 360, true, 3)""", caseId, caseId, caseId);
		overviewFactorId = jdbc.queryForObject("""
			INSERT INTO factor (case_id, label, pre_label, reveal_stage, summary_tag, display_order)
			VALUES (?, '흉기를 사용했다', '흉기를 썼다', 'OVERVIEW', '범행 수단', 1) RETURNING id""", Long.class, caseId);
		detailFactorId = jdbc.queryForObject("""
			INSERT INTO factor (case_id, label, reveal_stage, summary_tag, display_order)
			VALUES (?, '범행을 인정하고 반성했다', 'DETAIL', '반성', 2) RETURNING id""", Long.class, caseId);
		jdbc.update("""
			INSERT INTO judgment (case_id, subject_type, timing, penalty_type, reduced_to, prison_months,
			    extra_dispositions, reference_tags, is_published)
			VALUES (?, 'COURT', 'FINAL', 'LIFE', 'PRISON', 180,
			    '[{"type": "CONFISCATION", "value": "흉기"}]'::jsonb, NULL, true),
			       (?, 'AI', 'FINAL', 'PRISON', NULL, 144, NULL, '["형법 제250조", "살인범죄 양형기준"]'::jsonb, true),
			       (?, 'AI', 'FINAL', 'PRISON', NULL, 100, NULL, NULL, false)""", caseId, caseId, caseId);
	}

	@Test
	@DisplayName("사건 콘텐츠와 jsonb 컬럼을 읽는다")
	void findPublishedById_withJsonbColumns_readsContent() {
		LegalCase legalCase = legalCaseRepository.findPublishedById(caseId).orElseThrow();

		assertThat(legalCase.getKeywords()).containsExactly("지인 사이", "우발적 상황");
		assertThat(legalCase.getCreatedAt()).isNotNull();
		// data는 섹션마다 원소 형식이 다르다: DAMAGE · LAW_TERM은 객체, SUMMARY는 문자열 (ERD 3-1)
		Map<String, List<Object>> dataByType = caseSectionRepository.findAllByCaseId(caseId).stream()
			.collect(Collectors.toMap(CaseSection::getSectionType, CaseSection::getData));
		assertThat(dataByType.get("DAMAGE").get(0)).isEqualTo(Map.of("label", "피해자 수", "value", "1명"));
		assertThat(dataByType.get("LAW_TERM").get(0)).isEqualTo(Map.of("term", "작량감경", "desc", "재판상 감경"));
		assertThat(dataByType.get("SUMMARY")).containsExactly("지인 1명을 살해", "형사처벌 전력 없음");
		assertThat(penaltyRuleRepository.findAllByCaseId(caseId))
			.extracting(r -> r.getPenaltyType())
			.containsExactly(PenaltyType.DEATH, PenaltyType.LIFE, PenaltyType.PRISON);
		assertThat(penaltyRuleRepository.findByCaseIdAndPenaltyType(caseId, PenaltyType.LIFE).orElseThrow()
			.isWithinAllowedRange(180)).isTrue();
		assertThat(factorRepository.findAllByCaseIdAndRevealStage(caseId, RevealStage.OVERVIEW))
			.extracting(Factor::getId).containsExactly(overviewFactorId);
	}

	@Test
	@DisplayName("사전 판단 형량 구간은 V1 고정값(사기 7개, 살인 8개)이다")
	void findAllByCrimeType_rangeOptions_matchV1FixedData() {
		List<SentenceRangeOption> fraud = rangeOptionRepository.findAllByCrimeTypeOrderByDisplayOrderAsc(CrimeType.FRAUD);
		List<SentenceRangeOption> murder = rangeOptionRepository.findAllByCrimeTypeOrderByDisplayOrderAsc(CrimeType.MURDER);

		assertThat(fraud).hasSize(7).first().extracting(SentenceRangeOption::getId).isEqualTo(1L);
		assertThat(murder).hasSize(8).extracting(SentenceRangeOption::getKind)
			.doesNotContain(RangeKind.FINE).contains(RangeKind.LIFE, RangeKind.DEATH);
	}

	@Test
	@DisplayName("공개된 AI · 재판부 판결만 조회한다")
	void findPublishedJudgment_unpublished_isExcluded() {
		Judgment court = judgmentRepository.findPublishedJudgment(caseId, SubjectType.COURT).orElseThrow();
		Judgment ai = judgmentRepository.findPublishedJudgment(caseId, SubjectType.AI).orElseThrow();

		assertThat(court.getFinalPenaltyType()).isEqualTo(PenaltyType.PRISON);	// 무기 → 징역 감경
		assertThat(court.getExtraDispositions()).first()
			.satisfies(d -> assertThat(d.type()).isEqualTo(ExtraDispositionType.CONFISCATION));
		assertThat(ai.getPrisonMonths()).isEqualTo(144);	// 비공개 AI 판결(100개월)은 제외
		assertThat(ai.getReferenceTags()).contains("살인범죄 양형기준");
	}

	@Test
	@DisplayName("체험을 진행하고 사용자 사전 판단 · 최종 판결을 저장한다")
	void experienceFlow_userJudgments_areSaved() {
		Instant now = Instant.now();
		AnonymousUser issued = AnonymousUser.issue(now);
		AnonymousUser user = anonymousUserRepository.save(issued);
		assertThat(user).isSameAs(issued);	// merge가 아니라 persist로 바로 INSERT (Persistable)
		LegalCase legalCase = legalCaseRepository.getReferenceById(caseId);
		Experience experience = experienceRepository.save(Experience.start(user, legalCase));
		SentenceRangeOption lifeRange = rangeOptionRepository
			.findAllByCrimeTypeOrderByDisplayOrderAsc(CrimeType.MURDER).stream()
			.filter(o -> o.getKind() == RangeKind.LIFE).findFirst().orElseThrow();

		// 사전 판단 + 작용 요소
		Judgment pre = judgmentRepository.save(Judgment.userPre(experience, lifeRange));
		judgmentFactorRepository.save(JudgmentFactor.forPre(pre, factorRepository.getReferenceById(overviewFactorId)));
		experience.markPreJudged(now);

		// 섹션 확인: 2 ~ 4 밖은 거절, 건너뛰기는 거절, 중복은 무시, 마지막 섹션이면 REVIEWED
		assertThatThrownBy(() -> experience.confirmReviewStep(1, now)).isInstanceOf(InvalidReviewStepException.class);
		assertThatThrownBy(() -> experience.confirmReviewStep(3, now)).isInstanceOf(ReviewStepOutOfOrderException.class);
		assertThat(experience.confirmReviewStep(2, now)).isTrue();
		assertThat(experience.confirmReviewStep(2, now)).isFalse();
		experience.confirmReviewStep(3, now);
		experience.confirmReviewStep(4, now);
		assertThat(experience.getStatus()).isEqualTo(ExperienceStatus.REVIEWED);
		// 확인을 마친 뒤 범위 밖 번호가 와도 상태가 되돌아가지 않는다
		assertThatThrownBy(() -> experience.confirmReviewStep(5, now)).isInstanceOf(InvalidReviewStepException.class);
		assertThat(experience.getStatus()).isEqualTo(ExperienceStatus.REVIEWED);
		assertThat(experience.getLastReviewedStep()).isEqualTo(4);

		// 최종 판결: 사형을 골라 징역 300개월로 감경
		Judgment verdict = judgmentRepository.save(
			Judgment.userFinal(experience, PenaltyType.DEATH, PenaltyType.PRISON, 300, null, null, null));
		judgmentFactorRepository.save(
			JudgmentFactor.forFinal(verdict, factorRepository.getReferenceById(detailFactorId), Direction.DOWN));
		experience.markVerdictConfirmed(now);
		assertThat(experience.revealCourt(now)).isTrue();
		assertThat(experience.revealCourt(now)).isFalse();	// 다시 공개해도 그대로 (멱등)
		experience.revealComparison(now);

		em.flush();
		em.clear();

		Experience found = experienceRepository.findLatest(user.getId(), caseId).orElseThrow();
		assertThat(found.getStatus()).isEqualTo(ExperienceStatus.COMPLETED);
		assertThat(found.getLastReviewedStep()).isEqualTo(4);
		assertThat(judgmentRepository.findPreJudgment(found.getId())).isPresent();
		Judgment savedVerdict = judgmentRepository.findUserFinalJudgment(found.getId()).orElseThrow();
		assertThat(savedVerdict.getFinalPenaltyType()).isEqualTo(PenaltyType.PRISON);
		assertThat(judgmentFactorRepository.findAllByJudgmentIds(List.of(pre.getId(), savedVerdict.getId())))
			.extracting(jf -> jf.getFactor().getId()).containsExactly(overviewFactorId, detailFactorId);
		assertThat(experienceRepository.countCompletedFirstAttempts(caseId)).isEqualTo(1);
	}

	@Test
	@DisplayName("규칙에 맞지 않는 판결 값과 상태 이동은 거절한다")
	void userFinalAndTransition_invalidValues_throw() {
		AnonymousUser user = anonymousUserRepository.save(AnonymousUser.issue(Instant.now()));
		Experience experience = experienceRepository.save(
			Experience.start(user, legalCaseRepository.getReferenceById(caseId)));

		// 허용되지 않은 감경 조합, 사형 · 무기 감경 후 집행유예, 0 이하 형량
		assertThatThrownBy(() -> Judgment.userFinal(experience, PenaltyType.LIFE, PenaltyType.LIFE, null, null, null, null))
			.isInstanceOfSatisfying(InvalidJudgmentException.class,
				e -> assertThat(e.getReason().getApiErrorCode()).isEqualTo("INVALID_PENALTY_TYPE"));
		assertThatThrownBy(() -> Judgment.userFinal(experience, PenaltyType.LIFE, PenaltyType.PRISON, 120, null, 24, null))
			.isInstanceOfSatisfying(InvalidJudgmentException.class,
				e -> assertThat(e.getReason().getApiErrorCode()).isEqualTo("INVALID_SUSPENSION"));
		assertThatThrownBy(() -> Judgment.userFinal(experience, PenaltyType.PRISON, null, 0, null, null, null))
			.isInstanceOfSatisfying(InvalidJudgmentException.class,
				e -> assertThat(e.getReason()).isEqualTo(InvalidJudgmentException.Reason.INVALID_TERM_VALUES));

		// 사전 판단 전에 판결 확정 불가
		assertThatThrownBy(() -> experience.markVerdictConfirmed(Instant.now()))
			.isInstanceOf(InvalidExperienceStateException.class);
	}
}
