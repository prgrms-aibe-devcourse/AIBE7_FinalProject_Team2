package com.team2.project.admin.caseimport.load;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.team2.project.admin.caseimport.domain.CaseImport;
import com.team2.project.common.exception.BusinessException;
import com.team2.project.common.exception.ErrorCode;
import com.team2.project.judgment.repository.AiGenerationRepository;
import com.team2.project.judgment.repository.JudgmentFactorRepository;
import com.team2.project.judgment.repository.JudgmentRepository;
import com.team2.project.legalcase.domain.CrimeType;
import com.team2.project.legalcase.repository.CaseSectionRepository;
import com.team2.project.legalcase.repository.CaseScriptRepository;
import com.team2.project.legalcase.repository.CaseSourceRepository;
import com.team2.project.legalcase.repository.FactorRepository;
import com.team2.project.legalcase.repository.LegalCaseRepository;
import com.team2.project.legalcase.repository.PenaltyRuleRepository;
import com.team2.project.legalcase.repository.SentencingGuidelineRepository;
import com.team2.project.pipeline.PipelineResult;
import java.sql.SQLException;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import tools.jackson.databind.json.JsonMapper;

/**
 * 제목 유니크 제약에 걸렸을 때의 응답 (BE-93 2차, PR #242 리뷰).
 *
 * <p>적재 전 {@code existsByTitle} 검사를 통과한 <b>뒤에</b> 제약에 걸리는 경우를 본다 —
 * 서로 다른 등록이 같은 제목으로 동시에 적재될 때 생긴다. 실제 DB로는 그 순간을 만들 수 없어
 * 저장소를 가짜로 두고 제약 위반만 흉내 낸다.
 */
class CaseImportLoaderTitleRaceTest {

	@Test
	@DisplayName("사전 검사를 지나친 뒤 제목 제약에 걸려도 409 DUPLICATE_TITLE로 나간다")
	void titleConstraintBecomesDuplicateTitle() {
		LegalCaseRepository caseRepository = mock(LegalCaseRepository.class);
		when(caseRepository.existsByTitle(any())).thenReturn(false);	// 검사 시점에는 없었다
		when(caseRepository.saveAndFlush(any())).thenThrow(uniqueViolation());

		CaseImportLoader loader = new CaseImportLoader(caseRepository, mock(CaseSectionRepository.class),
			mock(PenaltyRuleRepository.class), mock(FactorRepository.class), mock(CaseSourceRepository.class),
			mock(CaseScriptRepository.class),
			mock(JudgmentRepository.class), mock(JudgmentFactorRepository.class), mock(AiGenerationRepository.class),
			mock(SentencingGuidelineRepository.class), new CaseContentMapper(), new JudgmentMapper());

		assertThatThrownBy(() -> loader.load(caseImport(), result()))
			.isInstanceOf(BusinessException.class)
			.extracting(e -> ((BusinessException) e).getErrorCode()).isEqualTo(ErrorCode.DUPLICATE_TITLE);
	}

	@Test
	@DisplayName("제목이 아닌 다른 저장 오류는 그대로 올려 보낸다 (제목 문제로 둔갑시키지 않는다)")
	void otherFailuresAreNotDisguised() {
		LegalCaseRepository caseRepository = mock(LegalCaseRepository.class);
		when(caseRepository.existsByTitle(any())).thenReturn(false);
		when(caseRepository.saveAndFlush(any()))
			.thenThrow(new DataIntegrityViolationException("널 제약 위반"));	// 유니크 위반이 아니다

		CaseImportLoader loader = new CaseImportLoader(caseRepository, mock(CaseSectionRepository.class),
			mock(PenaltyRuleRepository.class), mock(FactorRepository.class), mock(CaseSourceRepository.class),
			mock(CaseScriptRepository.class),
			mock(JudgmentRepository.class), mock(JudgmentFactorRepository.class), mock(AiGenerationRepository.class),
			mock(SentencingGuidelineRepository.class), new CaseContentMapper(), new JudgmentMapper());

		assertThatThrownBy(() -> loader.load(caseImport(), result()))
			.isInstanceOf(DataIntegrityViolationException.class);
	}

	/** PostgreSQL 유니크 제약 위반 (SQLSTATE 23505) */
	private static DataIntegrityViolationException uniqueViolation() {
		return new DataIntegrityViolationException("제약 위반",
			new SQLException("duplicate key value violates unique constraint \"uk_legal_case_title\"", "23505"));
	}

	private static CaseImport caseImport() {
		return CaseImport.start("job", CrimeType.MURDER);
	}

	private static PipelineResult result() {
		JsonMapper mapper = JsonMapper.builder().build();
		return new PipelineResult("job",
			mapper.readTree("""
				{"title": "같은 제목", "crimeType": "MURDER", "chargeName": "살인", "overview": "개요",
				 "appliedLaw": "형법 제250조", "statutoryPenaltyText": "법정형",
				 "listing": {"shortIntro": "소개", "keywords": []},
				 "sections": [{"stage": "DETAIL", "sectionType": "FACTS", "content": "본문"}],
				 "penaltyRules": [{"penaltyType": "PRISON", "allowedMin": 60, "allowedMax": 600,
				                   "suspensionAllowed": true}],
				 "factors": [{"factorId": 1, "label": "요소", "revealStage": "OVERVIEW"}]}"""),
			mapper.readTree("""
				{"factorExtras": [{"factorId": 1, "summaryTag": "태그"}]}"""),
			null,
			mapper.readTree("""
				{"penaltyType": "PRISON", "prisonMonths": 168, "summary": "요약", "reasoning": "이유",
				 "factors": []}"""),
			new PipelineResult.Selection("gemini:test", 1, "consensus", "합의", List.of()), null, null);
	}
}
