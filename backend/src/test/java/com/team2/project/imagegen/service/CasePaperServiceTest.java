package com.team2.project.imagegen.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.team2.project.common.exception.BusinessException;
import com.team2.project.common.exception.ErrorCode;
import com.team2.project.imagegen.CasePhotoPromptClient;
import com.team2.project.imagegen.ImageGenException;
import com.team2.project.legalcase.domain.CasePaper;
import com.team2.project.legalcase.domain.Factor;
import com.team2.project.legalcase.domain.LegalCase;
import com.team2.project.legalcase.repository.CasePaperRepository;
import com.team2.project.legalcase.repository.FactorRepository;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 신문 지면 콘텐츠 생성 (BE-92). Gemini는 가짜로 대체한다 */
class CasePaperServiceTest {

	private FakeContentClient contentClient;
	private CasePaperRepository paperRepository;
	private FactorRepository factorRepository;
	private CasePaperService service;

	@BeforeEach
	void setUp() {
		contentClient = new FakeContentClient();
		paperRepository = mock(CasePaperRepository.class);
		factorRepository = mock(FactorRepository.class);
		service = new CasePaperService(contentClient, paperRepository, factorRepository);
	}

	@Test
	@DisplayName("Gemini가 돌려준 JSON을 파싱해 headline · deck · caption · article을 채운다")
	void generatePaper_parsesJsonResponse() {
		LegalCase legalCase = legalCase(1L, "사건 개요", "살인");
		when(factorRepository.findAllByCaseId(1L)).thenReturn(List.of(factor("세부 사정 1")));
		when(paperRepository.findByLegalCaseId(1L)).thenReturn(Optional.empty());
		when(paperRepository.save(org.mockito.ArgumentMatchers.any())).thenAnswer(inv -> inv.getArgument(0));
		contentClient.nextResponse = "{\"headline\":\"헤드라인\",\"deck\":\"부제\",\"caption\":\"캡션\","
			+ "\"article\":[\"문단1\",\"문단2\"]}";

		CasePaper paper = service.generatePaper(legalCase);

		assertThat(paper.getHeadline()).isEqualTo("헤드라인");
		assertThat(paper.getDeck()).isEqualTo("부제");
		assertThat(paper.getCaption()).isEqualTo("캡션");
		assertThat(paper.getArticle()).containsExactly("문단1", "문단2");
		assertThat(paper.getDesk()).isEqualTo("사회면");
		assertThat(contentClient.lastInstruction).contains("사건 개요").contains("살인").contains("세부 사정 1");
	}

	@Test
	@DisplayName("```json 코드블록으로 감싸 보내도 그대로 파싱한다")
	void generatePaper_stripsCodeFence() {
		LegalCase legalCase = legalCase(1L, "개요", "상해");
		when(factorRepository.findAllByCaseId(1L)).thenReturn(List.of());
		when(paperRepository.findByLegalCaseId(1L)).thenReturn(Optional.empty());
		when(paperRepository.save(org.mockito.ArgumentMatchers.any())).thenAnswer(inv -> inv.getArgument(0));
		contentClient.nextResponse = "```json\n{\"headline\":\"H\",\"deck\":null,\"caption\":null,"
			+ "\"article\":[\"문단\"]}\n```";

		CasePaper paper = service.generatePaper(legalCase);

		assertThat(paper.getHeadline()).isEqualTo("H");
		assertThat(paper.getArticle()).containsExactly("문단");
	}

	@Test
	@DisplayName("이미 신문 지면이 있으면 새로 만들지 않고 내용만 덮어쓴다")
	void generatePaper_overwritesExisting() {
		LegalCase legalCase = legalCase(1L, "개요", "사기");
		CasePaper existing = CasePaper.of(legalCase, "사회면", "예전 헤드라인", "예전 부제", "예전 캡션", List.of("예전 문단"));
		when(factorRepository.findAllByCaseId(1L)).thenReturn(List.of());
		when(paperRepository.findByLegalCaseId(1L)).thenReturn(Optional.of(existing));
		contentClient.nextResponse = "{\"headline\":\"새 헤드라인\",\"deck\":\"새 부제\",\"caption\":\"새 캡션\","
			+ "\"article\":[\"새 문단\"]}";

		CasePaper paper = service.generatePaper(legalCase);

		assertThat(paper).isSameAs(existing);
		assertThat(paper.getHeadline()).isEqualTo("새 헤드라인");
		org.mockito.Mockito.verify(paperRepository, org.mockito.Mockito.never()).save(org.mockito.ArgumentMatchers.any());
	}

	@Test
	@DisplayName("JSON으로 파싱할 수 없는 응답은 PROMPT_GENERATION_FAILED로 실패한다")
	void generatePaper_invalidJson_fails() {
		LegalCase legalCase = legalCase(1L, "개요", "살인");
		when(factorRepository.findAllByCaseId(1L)).thenReturn(List.of());
		contentClient.nextResponse = "이건 JSON이 아닙니다";

		assertThatThrownBy(() -> service.generatePaper(legalCase)).isInstanceOf(BusinessException.class)
			.extracting(e -> ((BusinessException) e).getErrorCode()).isEqualTo(ErrorCode.PROMPT_GENERATION_FAILED);
	}

	@Test
	@DisplayName("headline · article이 빠진 응답은 실패한다")
	void generatePaper_missingRequiredFields_fails() {
		LegalCase legalCase = legalCase(1L, "개요", "살인");
		when(factorRepository.findAllByCaseId(1L)).thenReturn(List.of());
		contentClient.nextResponse = "{\"deck\":\"부제\"}";

		assertThatThrownBy(() -> service.generatePaper(legalCase)).isInstanceOf(BusinessException.class)
			.extracting(e -> ((BusinessException) e).getErrorCode()).isEqualTo(ErrorCode.PROMPT_GENERATION_FAILED);
	}

	@Test
	@DisplayName("Gemini 호출이 꺼져 있으면 PROMPT_GENERATION_FAILED로 알린다")
	void disabled_wrapsAsBusinessException() {
		contentClient.enabled = false;
		LegalCase legalCase = legalCase(1L, "개요", "살인");
		when(factorRepository.findAllByCaseId(1L)).thenReturn(List.of());

		assertThatThrownBy(() -> service.generatePaper(legalCase)).isInstanceOf(BusinessException.class)
			.extracting(e -> ((BusinessException) e).getErrorCode()).isEqualTo(ErrorCode.PROMPT_GENERATION_FAILED);
	}

	private static LegalCase legalCase(Long id, String overview, String chargeName) {
		try {
			Constructor<LegalCase> constructor = LegalCase.class.getDeclaredConstructor();
			constructor.setAccessible(true);
			LegalCase legalCase = constructor.newInstance();
			setField(legalCase, "id", id);
			setField(legalCase, "overview", overview);
			setField(legalCase, "chargeName", chargeName);
			return legalCase;
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException(e);
		}
	}

	private static Factor factor(String label) {
		try {
			Constructor<Factor> constructor = Factor.class.getDeclaredConstructor();
			constructor.setAccessible(true);
			Factor factor = constructor.newInstance();
			setField(factor, "label", label);
			return factor;
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException(e);
		}
	}

	private static void setField(Object target, String fieldName, Object value) throws ReflectiveOperationException {
		Field field = target.getClass().getDeclaredField(fieldName);
		field.setAccessible(true);
		field.set(target, value);
	}

	static class FakeContentClient implements CasePhotoPromptClient {

		boolean enabled = true;
		String nextResponse;
		String lastInstruction;

		@Override
		public boolean enabled() {
			return enabled;
		}

		@Override
		public String generateText(String instruction) {
			return generateText(instruction, 400);
		}

		@Override
		public String generateText(String instruction, int maxOutputTokens) {
			if (!enabled) {
				throw new ImageGenException("꺼져 있습니다");
			}
			lastInstruction = instruction;
			return nextResponse;
		}
	}
}
