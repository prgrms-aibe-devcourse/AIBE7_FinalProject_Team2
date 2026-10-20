package com.team2.project.imagegen.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.team2.project.common.exception.BusinessException;
import com.team2.project.common.exception.ErrorCode;
import com.team2.project.imagegen.CasePhotoPromptClient;
import com.team2.project.imagegen.ImageGenException;
import com.team2.project.legalcase.domain.CasePaper;
import com.team2.project.legalcase.domain.Factor;
import com.team2.project.legalcase.domain.LegalCase;
import com.team2.project.legalcase.repository.CasePaperRepository;
import com.team2.project.legalcase.repository.FactorRepository;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 신문 지면 콘텐츠(헤드라인 · 부제 · 캡션 · 본문) 생성 · 저장 (BE-92, COMMON-41).
 *
 * `CasePhotoPromptClient`(이름은 "사진"이지만 범용 Gemini 텍스트 클라이언트, BE-91)를 그대로 재사용한다.
 * 한 번 호출로 모든 필드를 받아야 해서(필드마다 따로 부르면 호출 수만큼 지연이 커진다, BE-91의 40 ~ 65초
 * 지연 경험 참고) Gemini에게 JSON 하나로 응답하라고 지시하고 그대로 파싱한다.
 */
@Service
@RequiredArgsConstructor
public class CasePaperService {

	/** 기사 본문까지 담아야 해서 사진 프롬프트(400)보다 넉넉히 둔다 */
	private static final int MAX_OUTPUT_TOKENS = 1200;
	private static final String DEFAULT_DESK = "사회면";

	private final CasePhotoPromptClient contentClient;
	private final CasePaperRepository paperRepository;
	private final FactorRepository factorRepository;
	private final ObjectMapper objectMapper = new ObjectMapper();

	/** 사건 내용을 바탕으로 신문 지면을 짓는다. 이미 있으면 내용을 덮어쓰고, 없으면 새로 만든다 */
	@Transactional
	public CasePaper generatePaper(LegalCase legalCase) {
		String instruction = buildInstruction(legalCase);
		GeneratedPaper generated = generate(instruction);

		return paperRepository.findByLegalCaseId(legalCase.getId())
			.map(paper -> {
				paper.overwrite(DEFAULT_DESK, generated.headline(), generated.deck(), generated.caption(),
					generated.article());
				return paper;
			})
			.orElseGet(() -> paperRepository.save(
				CasePaper.of(legalCase, DEFAULT_DESK, generated.headline(), generated.deck(), generated.caption(),
					generated.article())));
	}

	public CasePaper getPaper(Long caseId) {
		return paperRepository.findByLegalCaseId(caseId)
			.orElseThrow(() -> new BusinessException(ErrorCode.PAPER_NOT_FOUND));
	}

	private String buildInstruction(LegalCase legalCase) {
		String factorText = factorRepository.findAllByCaseId(legalCase.getId()).stream()
			.map(Factor::getLabel)
			.collect(Collectors.joining(" / "));
		return "다음 사건을 메인 화면 신문 지면에 실으려고 해. 아래 JSON 형식으로만 응답해줘(다른 설명 없이 JSON만,"
			+ " 코드블록 표시도 하지 말고):\n"
			+ "{\"headline\": \"...\", \"deck\": \"...\", \"caption\": \"...\", \"article\": [\"문단1\", \"문단2\"]}\n\n"
			+ "- headline: 신문 헤드라인 한 줄. 사건 당사자의 실명 · 지역 등 구체적인 신상은 넣지 말고, 선정적이지"
			+ " 않은 중립적인 문구로 작성해줘.\n"
			+ "- deck: 헤드라인을 보충하는 부제 한 줄.\n"
			+ "- caption: 사건 대표사진 아래 들어갈 짧은 설명 한 줄.\n"
			+ "- article: 본문 문단 2 ~ 4개로 나눈 배열. 각 문단은 2 ~ 3문장.\n\n"
			+ "사건 요약: " + legalCase.getOverview() + "\n"
			+ "죄명: " + legalCase.getChargeName() + "\n"
			+ (factorText.isBlank() ? "" : "세부 사정: " + factorText + "\n");
	}

	private GeneratedPaper generate(String instruction) {
		if (!contentClient.enabled()) {
			throw new BusinessException(ErrorCode.PROMPT_GENERATION_FAILED, "신문 지면 생성이 꺼져 있습니다 (GEMINI_API_KEY 없음)");
		}
		String raw;
		try {
			raw = contentClient.generateText(instruction, MAX_OUTPUT_TOKENS);
		} catch (ImageGenException e) {
			throw new BusinessException(ErrorCode.PROMPT_GENERATION_FAILED, ErrorCode.PROMPT_GENERATION_FAILED.getMessage());
		}
		return parse(raw);
	}

	private GeneratedPaper parse(String raw) {
		String json = stripCodeFence(raw);
		try {
			GeneratedPaper paper = objectMapper.readValue(json, GeneratedPaper.class);
			if (paper.headline() == null || paper.headline().isBlank() || paper.article() == null
					|| paper.article().isEmpty()) {
				throw new BusinessException(ErrorCode.PROMPT_GENERATION_FAILED, "신문 지면 응답에 headline · article이 없습니다");
			}
			return paper;
		} catch (JsonProcessingException e) {
			throw new BusinessException(ErrorCode.PROMPT_GENERATION_FAILED, "신문 지면 응답을 JSON으로 읽지 못했습니다");
		}
	}

	/** Gemini가 지시를 어기고 ```json ... ``` 코드블록으로 감싸 보낼 때를 대비한다 */
	private static String stripCodeFence(String raw) {
		String text = raw.strip();
		if (text.startsWith("```")) {
			int firstNewline = text.indexOf('\n');
			int lastFence = text.lastIndexOf("```");
			if (firstNewline != -1 && lastFence > firstNewline) {
				text = text.substring(firstNewline + 1, lastFence).strip();
			}
		}
		return text;
	}

	record GeneratedPaper(String headline, String deck, String caption, List<String> article) {
	}
}
