package com.team2.project.legalcase.domain;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 사건 분류 {@code {mood, issue, victim, thirdParty}} (BE-123 자동 분류, 저장은 BE-129 — {@link LegalCase#getClassification()}).
 * 대본 블록을 고르는 값이고, 관리자가 A-11로 고친 값이 블록 재생성(BE-129 2차) 입력으로 그대로 넘어간다.
 *
 * <p><b>정본은 파이썬이다</b>(`tools/ai-judgment/script_assembler.py`의 {@code MOODS} · {@code ISSUES} · {@code VICTIMS}). 같은
 * 목록인지는 공용 계약 파일(`contracts/script_blocks.json` {@code classification})을 읽는 {@code ScriptBlockContractTest}가 본다.
 * 민감 플래그는 {@link LegalCase#getSensitiveFlags()}가 따로 갖는다({@link SensitiveFlag}).
 */
public final class ScriptClassification {

	public static final List<String> MOODS = List.of("tragic", "impulsive", "cold", "betrayal", "calculated", "tense");
	public static final List<String> ISSUES =
		List.of("admitted", "intent_disputed", "denied", "mental_state", "defense_claim");
	public static final List<String> VICTIMS = List.of("punish", "forgive", "mixed", "unknown");

	/** 분류 축 이름. 관리자 확인 필요 목록({@code classificationNeedsReview})에서도 이 이름을 쓴다 */
	public static final List<String> AXES = List.of("mood", "issue", "victim", "thirdParty");

	private ScriptClassification() {
	}

	/**
	 * 관리자가 보낸 분류를 검사한다. 네 축이 모두 있고 아는 값이어야 한다 — 한 축만 보내는 수정은 받지 않는다(재생성에 통째로 넘기는
	 * 값이라 일부만 있으면 파이프라인 설정 검사에서 막힌다). 모르는 키도 거절한다.
	 *
	 * @return 검사를 마친 분류(축 순서), 형식이 틀리면 empty
	 */
	public static Optional<Map<String, Object>> parse(Object value) {
		if (!(value instanceof Map<?, ?> raw) || !Set.copyOf(AXES).equals(raw.keySet())) {
			return Optional.empty();
		}
		if (!(raw.get("mood") instanceof String mood) || !MOODS.contains(mood)
			|| !(raw.get("issue") instanceof String issue) || !ISSUES.contains(issue)
			|| !(raw.get("victim") instanceof String victim) || !VICTIMS.contains(victim)
			|| !(raw.get("thirdParty") instanceof Boolean thirdParty)) {
			return Optional.empty();
		}
		Map<String, Object> parsed = new LinkedHashMap<>();
		parsed.put("mood", mood);
		parsed.put("issue", issue);
		parsed.put("victim", victim);
		parsed.put("thirdParty", thirdParty);
		return Optional.of(parsed);
	}
}
