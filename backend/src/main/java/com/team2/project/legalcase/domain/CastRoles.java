package com.team2.project.legalcase.domain;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 사건 배역 (legal_case.cast_roles, BE-125). {@code {"prosecutor": "naero", "defense": "nambul"}} 모양이다.
 *
 * <p>묶음 A({@code prosecutor}) = 판사 · 검사, 묶음 B({@code defense}) = 변호인 · 피고인 역. 각 묶음은 한
 * 캐릭터가 맡고 사건마다 내로 · 남불이 번갈아 맡는다(COMMON-65 · character-profile-worldview 5-2).
 * 파이프라인 stages.script.cast · case_script.cast_roles와 같은 키 · 값을 쓴다.
 *
 * <p>DB는 모양을 검사하지 않으므로(V35) 저장하기 전에 {@link #parse}로 확인한다.
 */
public final class CastRoles {

	public static final String PROSECUTOR = "prosecutor";
	public static final String DEFENSE = "defense";
	public static final String NAERO = "naero";
	public static final String NAMBUL = "nambul";

	private static final Set<String> CHARACTERS = Set.of(NAERO, NAMBUL);

	private CastRoles() {
	}

	/** 피고인 역(묶음 B)을 정하면 나머지 캐릭터가 묶음 A를 맡는다 */
	public static Map<String, String> withDefense(String defense) {
		Map<String, String> cast = new LinkedHashMap<>();
		cast.put(PROSECUTOR, other(defense));
		cast.put(DEFENSE, defense);
		return cast;
	}

	/** 다른 캐릭터 */
	public static String other(String character) {
		return NAERO.equals(character) ? NAMBUL : NAERO;
	}

	/**
	 * 배역 값을 검사한다. 키가 정확히 prosecutor · defense 둘이고, 값이 내로 · 남불 중 하나씩이어야 한다.
	 * 공동 피고인 배역(COMMON-65 2차)이 생기면 키를 여기서 넓힌다.
	 *
	 * @return 맞으면 같은 내용의 새 Map, 아니면 비어 있다
	 */
	public static Optional<Map<String, String>> parse(Object value) {
		if (!(value instanceof Map<?, ?> map) || map.size() != 2) {
			return Optional.empty();
		}
		Object prosecutor = map.get(PROSECUTOR);
		Object defense = map.get(DEFENSE);
		if (!(prosecutor instanceof String p) || !(defense instanceof String d)
			|| !CHARACTERS.contains(p) || !CHARACTERS.contains(d) || p.equals(d)) {
			return Optional.empty();
		}
		return Optional.of(withDefense(d));
	}
}
