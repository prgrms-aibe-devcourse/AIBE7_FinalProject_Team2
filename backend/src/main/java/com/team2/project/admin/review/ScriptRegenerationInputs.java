package com.team2.project.admin.review;

import com.team2.project.judgment.domain.Judgment;
import com.team2.project.judgment.domain.JudgmentFactor;
import com.team2.project.legalcase.domain.CasePaper;
import com.team2.project.legalcase.domain.CaseScript;
import com.team2.project.legalcase.domain.CaseScriptBlock;
import com.team2.project.legalcase.domain.CaseSection;
import com.team2.project.legalcase.domain.Factor;
import com.team2.project.legalcase.domain.LegalCase;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import tools.jackson.databind.json.JsonMapper;

/**
 * 대본 블록 재생성(BE-129 2차) 입력을 DB 값으로 만든다. 파이프라인 서비스 요청 형식은 `tools/ai-judgment/service/README.md`
 * "대본 블록 재생성"이 기준이다. 등록 작업 폴더는 적재 뒤 지워지므로(COMMON-51) 파이프라인이 읽는 값을 모두 여기서 넘긴다.
 *
 * <p><b>대본이 읽는 값만 보낸다.</b> 사건 입력은 `db_case_input.py`(BE-76)와 같은 규칙이지만 대본 모듈이 실제로 읽는 필드
 * (제목 · 죄명 · 적용 법조 · 개요 · 섹션 · 판단 요소 · 권고형)만 담는다 — 사건번호 · 법원 같은 원본 정보와 형벌 규칙은 대본에 쓰이지
 * 않아 외부 모델 쪽으로 보낼 이유가 없다. 용어 설명(LAW_TERM) · 핵심 정리(SUMMARY) 섹션은 생성 입력에 쓰지 않는 화면용이라 뺀다.
 */
final class ScriptRegenerationInputs {

	private static final Set<String> EXCLUDED_SECTIONS = Set.of("LAW_TERM", "SUMMARY");
	private static final JsonMapper JSON = JsonMapper.builder().build();

	private ScriptRegenerationInputs() {
	}

	/**
	 * 서비스 요청의 {@code regenerate} 객체.
	 *
	 * @param judgment 대본이 따른 AI 판결 — U3가 판단 요소 방향을, 검수가 형량 숫자를 읽는다(판결 누출 검사)
	 */
	static Map<String, Object> request(LegalCase legalCase, CaseScript script, CasePaper paper, List<CaseSection> sections,
			List<Factor> factors, Judgment judgment, List<JudgmentFactor> judgmentFactors, List<String> units,
			boolean cascade) {
		Map<String, Object> regenerate = new LinkedHashMap<>();
		regenerate.put("units", units);
		regenerate.put("cascade", cascade);
		regenerate.put("case", caseInput(legalCase, sections, factors));
		regenerate.put("judgment", judgmentInput(judgment, judgmentFactors));
		regenerate.put("classification", classification(legalCase));
		regenerate.put("cast", new LinkedHashMap<>(legalCase.getCastRoles()));
		Map<String, Object> blocks = new LinkedHashMap<>();
		for (CaseScriptBlock block : script.getBlocks()) {
			Map<String, Object> value = new LinkedHashMap<>();
			value.put("content", block.getContent());
			value.put("revision", block.getRevision());
			blocks.put(block.getUnit().name(), value);
		}
		regenerate.put("blocks", blocks);
		regenerate.put("paper", paper == null ? null : paperInput(paper));
		return regenerate;
	}

	/**
	 * 분류 + 민감 플래그. 파이프라인은 민감 플래그 키가 없으면 거절한다 — 둘은 따로 저장되어 합쳐 넘겨야 하고, 빠지면 민감 사건이
	 * 안내 · 표현 가드 없이 다시 만들어진다(PR #370 리뷰). 미분류(null)인 사건은 요청하기 전에 막는다
	 */
	static Map<String, Object> classification(LegalCase legalCase) {
		Map<String, Object> classification = new LinkedHashMap<>(legalCase.getClassification());
		classification.put("sensitiveFlags", List.copyOf(legalCase.getSensitiveFlags()));
		return classification;
	}

	/**
	 * 요청 당시 입력 기록. 반영할 때 다시 만들어 비교하고, 다르면 거절한다 — 그 사이 대사를 고치거나(블록 판) 다른 재생성을 반영했거나,
	 * 사건 내용 · 기사 · 배역 · 분류 · 민감 플래그 · AI 판결을 바꿨으면 옛 입력으로 만든 블록이 들어가기 때문이다. 값은 JSON으로 저장했다
	 * 읽어도 같게 나오는 모양(정수는 long)으로 맞춘다.
	 *
	 * <p>사건 내용 판({@code content_revision})은 제목 · 개요를 고칠 때만 오른다. 파이프라인에 보낸 사건 입력에는 섹션 · 판단 요소 문구 ·
	 * 적용 법조 · 권고형도 있고 AI 판결(방향 · 형량)도 있어서, <b>보낸 입력 그대로의 해시</b>를 함께 남긴다(PR #381 리뷰 2번) —
	 * 섹션을 고치거나 다른 AI 판결 후보를 공개해도 반영이 막힌다.
	 *
	 * @param caseInput     파이프라인에 보낸(보낼) 사건 입력 ({@link #caseInput})
	 * @param judgmentInput 파이프라인에 보낸(보낼) AI 판결 입력. AI 판결이 없으면 null
	 */
	static Map<String, Object> snapshot(LegalCase legalCase, CaseScript script, Integer paperRevision,
			Object caseInput, Object judgmentInput) {
		Map<String, Object> blocks = new LinkedHashMap<>();
		for (CaseScriptBlock block : script.getBlocks()) {
			blocks.put(block.getUnit().name(), (long) block.getRevision());
		}
		Map<String, Object> snapshot = new LinkedHashMap<>();
		snapshot.put("blocks", blocks);
		snapshot.put("case", (long) legalCase.getContentRevision());
		snapshot.put("paper", paperRevision == null ? null : paperRevision.longValue());
		snapshot.put("cast", legalCase.getCastRoles() == null ? null : new LinkedHashMap<>(legalCase.getCastRoles()));
		snapshot.put("classification", legalCase.getClassification() == null ? null
			: new LinkedHashMap<>(legalCase.getClassification()));
		snapshot.put("sensitiveFlags", legalCase.getSensitiveFlags() == null ? null
			: List.copyOf(legalCase.getSensitiveFlags()));
		snapshot.put("caseInput", digest(caseInput));
		snapshot.put("judgmentInput", digest(judgmentInput));
		return snapshot;
	}

	/** 입력의 SHA-256 (JSON으로 적은 뒤). 같은 DB 값이면 같은 순서 · 같은 모양으로 만들어지므로 같은 값이 나온다 */
	private static String digest(Object value) {
		if (value == null) {
			return null;
		}
		try {
			byte[] hash = MessageDigest.getInstance("SHA-256")
				.digest(JSON.writeValueAsString(value).getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(hash);
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException("SHA-256을 쓸 수 없다", e);
		}
	}

	/** 저장했다 읽은 기록과 지금 기록이 같은지. JSON 숫자는 Integer · Long 어느 쪽으로도 오므로 long으로 맞춰 비교한다 */
	static boolean sameInputs(Map<String, Object> saved, Map<String, Object> current) {
		return normalize(saved).equals(normalize(current));
	}

	private static Object normalize(Object value) {
		if (value instanceof Number number) {
			return number.longValue();
		}
		if (value instanceof Map<?, ?> map) {
			Map<Object, Object> normalized = new LinkedHashMap<>();
			map.forEach((key, item) -> normalized.put(key, normalize(item)));
			return normalized;
		}
		if (value instanceof List<?> list) {
			return list.stream().map(ScriptRegenerationInputs::normalize).toList();
		}
		return value;
	}

	static Map<String, Object> caseInput(LegalCase legalCase, List<CaseSection> sections, List<Factor> factors) {
		Map<String, Object> input = new LinkedHashMap<>();
		input.put("caseId", legalCase.getId());
		input.put("title", legalCase.getTitle());
		input.put("chargeName", legalCase.getChargeName());
		input.put("appliedLaw", legalCase.getAppliedLaw());
		input.put("overview", legalCase.getOverview());
		if (legalCase.getRecommendedMinMonths() != null && legalCase.getRecommendedMaxMonths() != null) {
			input.put("recommended", Map.of("minMonths", legalCase.getRecommendedMinMonths(),
				"maxMonths", legalCase.getRecommendedMaxMonths()));
		}
		List<Map<String, Object>> sectionInputs = new ArrayList<>();
		for (CaseSection section : sections) {
			if (EXCLUDED_SECTIONS.contains(section.getSectionType())) {
				continue;
			}
			Map<String, Object> item = new LinkedHashMap<>();
			item.put("stage", section.getStage().name());
			item.put("sectionType", section.getSectionType());
			item.put("title", section.getTitle());
			item.put("content", section.getContent());
			sectionInputs.add(item);
		}
		input.put("sections", sectionInputs);
		List<Map<String, Object>> factorInputs = new ArrayList<>();
		for (Factor factor : factors) {
			Map<String, Object> item = new LinkedHashMap<>();
			item.put("factorId", factor.getDisplayOrder());		// 파이프라인 번호 = display_order (적재 규칙)
			item.put("label", factor.getLabel());
			item.put("revealStage", factor.getRevealStage().name());
			factorInputs.add(item);
		}
		input.put("factors", factorInputs);
		return input;
	}

	static Map<String, Object> judgmentInput(Judgment judgment, List<JudgmentFactor> judgmentFactors) {
		List<Map<String, Object>> factors = new ArrayList<>();
		for (JudgmentFactor judgmentFactor : judgmentFactors) {
			if (judgmentFactor.getDirection() == null) {
				continue;
			}
			Map<String, Object> item = new LinkedHashMap<>();
			item.put("factorId", judgmentFactor.getFactor().getDisplayOrder());
			item.put("direction", judgmentFactor.getDirection().name());
			factors.add(item);
		}
		Map<String, Object> input = new LinkedHashMap<>();
		input.put("factors", factors);
		input.put("prisonMonths", judgment.getPrisonMonths());
		input.put("suspensionMonths", judgment.getSuspensionMonths());
		input.put("fineAmount", judgment.getFineAmount());
		return input;
	}

	private static Map<String, Object> paperInput(CasePaper paper) {
		Map<String, Object> input = new LinkedHashMap<>();
		input.put("headline", paper.getHeadline());
		input.put("deck", paper.getDeck());
		input.put("caption", paper.getCaption());
		input.put("article", paper.getArticle());
		return input;
	}
}
