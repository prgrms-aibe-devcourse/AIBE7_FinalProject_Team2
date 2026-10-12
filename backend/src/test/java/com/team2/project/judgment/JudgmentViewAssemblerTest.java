package com.team2.project.judgment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.team2.project.judgment.domain.Direction;
import com.team2.project.judgment.domain.ExtraDisposition;
import com.team2.project.judgment.domain.ExtraDispositionType;
import com.team2.project.judgment.domain.Judgment;
import com.team2.project.judgment.domain.JudgmentFactor;
import com.team2.project.judgment.domain.SubjectType;
import com.team2.project.judgment.dto.JudgmentView;
import com.team2.project.judgment.service.JudgmentViewAssembler;
import com.team2.project.legalcase.domain.Factor;
import com.team2.project.legalcase.domain.PenaltyType;
import java.util.List;
import org.junit.jupiter.api.Test;

/** 판결 응답 공통 형식 조립 — 주체에 따라 내려가는 필드가 다르다 (API 명세 판결 응답 공통 형식) */
class JudgmentViewAssemblerTest {

	private final JudgmentViewAssembler assembler = new JudgmentViewAssembler();

	/** 모든 선택 필드를 채운 판단. 주체만 바꿔 가며 무엇이 걸러지는지 본다 */
	private static Judgment judgment(SubjectType subjectType) {
		Judgment judgment = mock(Judgment.class);
		when(judgment.getSubjectType()).thenReturn(subjectType);
		when(judgment.getPenaltyType()).thenReturn(PenaltyType.PRISON);
		when(judgment.getPrisonMonths()).thenReturn(120);
		when(judgment.getSummary()).thenReturn("등록된 한 줄 요약");
		when(judgment.getReasoning()).thenReturn("판결 이유");
		when(judgment.getExcerpt()).thenReturn("판결문 발췌");
		when(judgment.getPlainExplanation()).thenReturn("쉬운 설명");
		when(judgment.getExtraDispositions())
			.thenReturn(List.of(new ExtraDisposition(ExtraDispositionType.CONFISCATION, "흉기")));
		return judgment;
	}

	private static JudgmentFactor factorRecord() {
		Factor factor = mock(Factor.class);
		when(factor.getId()).thenReturn(2L);
		when(factor.getLabel()).thenReturn("흉기를 집어 들었다");
		when(factor.getSummaryTag()).thenReturn("범행 방식");
		JudgmentFactor judgmentFactor = mock(JudgmentFactor.class);
		when(judgmentFactor.getFactor()).thenReturn(factor);
		when(judgmentFactor.getDirection()).thenReturn(Direction.UP);
		when(judgmentFactor.getEvidence()).thenReturn("판결문 근거 문장");
		return judgmentFactor;
	}

	@Test
	void toView_court_keepsExcerptPlainExplanationAndEvidence() {
		JudgmentView view = assembler.toView(judgment(SubjectType.COURT), List.of(factorRecord()));

		assertThat(view.subjectType()).isEqualTo(SubjectType.COURT);
		assertThat(view.summary()).isEqualTo("등록된 한 줄 요약");
		assertThat(view.reasoning()).isEqualTo("판결 이유");
		assertThat(view.excerpt()).isEqualTo("판결문 발췌");
		assertThat(view.plainExplanation()).isEqualTo("쉬운 설명");
		assertThat(view.factors()).singleElement()
			.satisfies(factor -> {
				assertThat(factor.factorId()).isEqualTo(2L);
				assertThat(factor.label()).isEqualTo("흉기를 집어 들었다");
				assertThat(factor.direction()).isEqualTo(Direction.UP);
				assertThat(factor.evidence()).isEqualTo("판결문 근거 문장");
			});
	}

	@Test
	void toView_ai_dropsCourtOnlyFields() {
		JudgmentView view = assembler.toView(judgment(SubjectType.AI), List.of(factorRecord()));

		assertThat(view.summary()).isEqualTo("등록된 한 줄 요약");
		assertThat(view.reasoning()).isEqualTo("판결 이유");
		assertThat(view.excerpt()).isNull();
		assertThat(view.plainExplanation()).isNull();
		assertThat(view.factors()).singleElement().satisfies(factor -> assertThat(factor.evidence()).isNull());
	}

	@Test
	void toView_user_buildsSummaryFromTagsAndHasNoReasoning() {
		JudgmentView view = assembler.toView(judgment(SubjectType.USER), List.of(factorRecord()));

		assertThat(view.summary()).isEqualTo("범행 방식을 무겁게 본 판단");
		assertThat(view.reasoning()).isNull();
		assertThat(view.excerpt()).isNull();
		assertThat(view.plainExplanation()).isNull();
		assertThat(view.factors()).singleElement().satisfies(factor -> assertThat(factor.evidence()).isNull());
	}

	@Test
	void toView_onlyCourtKeepsExtraDispositions() {
		// 명세 판결 응답 공통 형식: extraDispositions는 USER · AI 모두 []
		assertThat(assembler.toView(judgment(SubjectType.COURT), List.of()).extraDispositions()).hasSize(1);
		assertThat(assembler.toView(judgment(SubjectType.AI), List.of()).extraDispositions()).isEmpty();
		assertThat(assembler.toView(judgment(SubjectType.USER), List.of()).extraDispositions()).isEmpty();
	}

	@Test
	void toView_noFactor_returnsEmptyListsAndFallbackSummary() {
		Judgment judgment = mock(Judgment.class);
		when(judgment.getSubjectType()).thenReturn(SubjectType.USER);

		JudgmentView view = assembler.toView(judgment, List.of());

		assertThat(view.extraDispositions()).isEmpty();
		assertThat(view.factors()).isEmpty();
		assertThat(view.summary()).isEqualTo("판단 요소를 고르지 않은 판단");
	}
}
