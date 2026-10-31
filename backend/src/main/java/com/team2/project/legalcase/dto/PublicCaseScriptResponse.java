package com.team2.project.legalcase.dto;

import com.team2.project.legalcase.domain.CaseScript;
import com.team2.project.legalcase.domain.CaseScriptBlock;
import com.team2.project.legalcase.domain.ScriptAnswers;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 공개 사건 대본 (API 18, BE-109 · 블록은 BE-126). 관리자가 승인한(모든 블록 승인) 대본만 내려준다.
 *
 * <p>{@code blocks}는 단위 블록의 값({@code content})뿐이다. 고정 문구 · 연출 · 민감 안내 · 기사 · 4 ~ 6막은 블록에 없다 —
 * 프론트 공통 틀(FE-33 · FE-139)이 {@code meta}로 고정 문구를 골라 블록과 조립하고, 기사는 API 17, 민감 안내는 FE-129가 맡는다.
 * 검수용 값(블록의 {@code review} · {@code warnings} · {@code repairs}, 대사의 {@code validation} · {@code attempts} ·
 * {@code editedAt})은 내리지 않는다 — 고치기 전 문장과 대본 인용이 들어 있다.
 * <ul>
 *   <li><b>U0(설계도)는 내리지 않는다.</b> 화면에 직접 나가는 값이 아니라 U2 · U4가 보고 만든 재료다. 화면이 쓰는 값
 *       (마지막 답의 인정 여부)은 {@code meta.admits}로 준다</li>
 *   <li>U2 신문 답({@code questions[].a})은 여기서 "…네." · "…아닙니다."로 정규화한다({@link ScriptAnswers}). 저장값은 모델이
 *       쓴 그대로다 — 저장값에 계산한 값을 섞지 않는다</li>
 *   <li>U3는 줄마다 {@code factorId} · {@code role} · {@code text}만 준다</li>
 * </ul>
 *
 * <p>{@code evidence} · {@code cast}는 BE-109 때 모양 그대로 둔다(U3 대사와 같은 값) — 블록을 읽지 않는 화면이 그대로 돈다.
 * <b>{@code evidence} 순서는 배열 순서 그대로가 재생 순서다</b>.
 *
 * @param cast   배역 {@code {prosecutor, defense}}. {@link CaseScript#getCastRoles()}를 그대로 내려준다
 * @param blocks 단위 → 값. U1 · U2 · U3 · U4 중 대본에 있는 것만 (증거조사만 만든 대본은 U3뿐)
 * @param meta   {@code {cast, issue, admits}}. 분류 없이 만든 대본은 {@code issue}, U0가 없으면 {@code admits}가 null
 */
public record PublicCaseScriptResponse(List<EvidenceLine> evidence, Map<String, Object> cast,
		Map<String, Object> blocks, Map<String, Object> meta) {

	public static PublicCaseScriptResponse of(CaseScript script, Map<Integer, String> labelsByFactorId) {
		List<EvidenceLine> lines = script.evidence().stream().map(line -> EvidenceLine.from(line, labelsByFactorId))
			.toList();
		Map<String, Object> blocks = new LinkedHashMap<>();
		for (CaseScriptBlock block : script.getBlocks()) {
			switch (block.getUnit()) {
				case U0 -> {
				}
				case U2 -> blocks.put(block.getUnit().name(), withNormalizedAnswers(block.getContent()));
				case U3 -> blocks.put(block.getUnit().name(), block.lines().stream().map(PublicCaseScriptResponse::publicLine).toList());
				default -> blocks.put(block.getUnit().name(), block.getContent());
			}
		}
		Map<String, Object> meta = new LinkedHashMap<>();	// Map.of는 null 값을 허용하지 않는다
		meta.put("cast", script.getCastRoles());
		meta.put("issue", script.getIssue());
		meta.put("admits", script.getAdmits());
		return new PublicCaseScriptResponse(lines, script.getCastRoles(), blocks, meta);
	}

	/** U3 한 줄의 공개 값. 생성 내부 값(validation · attempts)과 배역 이름(character)은 뺀다 */
	private static Map<String, Object> publicLine(Map<String, Object> line) {
		Map<String, Object> copy = new LinkedHashMap<>();
		copy.put("factorId", line.get("factorId"));
		copy.put("role", line.get("role"));
		copy.put("text", line.get("text"));
		return copy;
	}

	/** U2 신문 답을 정규화한 사본. 저장값(엔티티)은 건드리지 않는다 */
	private static Object withNormalizedAnswers(Object content) {
		if (!(content instanceof Map<?, ?> opening) || !(opening.get("questions") instanceof List<?> questions)) {
			return content;
		}
		Map<Object, Object> copy = new LinkedHashMap<>(opening);
		copy.put("questions", questions.stream().map(question -> {
			if (!(question instanceof Map<?, ?> pair)) {
				return question;
			}
			Map<Object, Object> normalized = new LinkedHashMap<>(pair);
			normalized.put("a", ScriptAnswers.normalize(pair.get("a") instanceof String answer ? answer : null));
			return normalized;
		}).toList());
		return copy;
	}

	/**
	 * @param side      검사 · 변호인 코드값({@code PROSECUTOR} · {@code DEFENSE}). 다른 공개 API와 형식을
	 *                  맞추려고 코드값으로 준다 — 화면 한글(검사 · 변호인)로 바꾸는 것은 프론트 몫이다
	 * @param character 이 대사를 하는 배역 ({@link #cast}의 값과 같은 코드)
	 * @param card      이 대사가 다루는 증거 카드 문구 (= 사건의 판단 요소 문구). 파이프라인 사건에는 정적
	 *                  증거 카드가 없어 이 문구가 카드 역할을 그대로 한다 — 요소가 지워졌으면 비운다
	 * @param text      법정에서 실제로 하는 대사
	 */
	public record EvidenceLine(int factorId, String side, String character, String card, String text) {

		static EvidenceLine from(Map<String, Object> line, Map<Integer, String> labelsByFactorId) {
			int factorId = factorIdOf(line.get("factorId"));
			return new EvidenceLine(factorId, side(line.get("role")), (String) line.get("character"),
				labelsByFactorId.get(factorId), (String) line.get("text"));
		}

		/** 저장된 JSON의 숫자는 Integer · Long 어느 쪽으로도 올 수 있다 (admin 쪽과 같은 처리) */
		private static int factorIdOf(Object value) {
			return value instanceof Number number ? number.intValue() : -1;
		}

		/** 파이프라인은 role을 소문자로 준다(script_generation.py ROLE_BY_DIRECTION) — 대문자 코드로 바꿔 돌려준다 */
		private static String side(Object role) {
			return role instanceof String value ? value.toUpperCase(Locale.ROOT) : null;
		}
	}
}
