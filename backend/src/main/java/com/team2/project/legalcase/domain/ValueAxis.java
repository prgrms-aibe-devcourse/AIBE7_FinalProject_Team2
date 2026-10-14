package com.team2.project.legalcase.domain;

/**
 * 판단 요소가 대응하는 가치관 축 (factor.value_axis)
 * DB CHECK 제약과 같은 값을 쓴다. 어느 축에도 맞지 않는 요소는 null이다.
 */
public enum ValueAxis {
	APOLOGY_SINCERITY("E", "I"),	// ① 사과와 진정성 (반성, 자수, 수사 협조, 사후 정황)
	FAULT_STANDARD("S", "N"),		// ② 잘잘못의 기준 (범행 동기, 수단 · 방법, 계획성, 결과의 중대성)
	PRINCIPLE_RELATION("T", "F"),	// ③ 원칙과 관계 (피해 회복, 합의 · 처벌불원, 피해자 과실)
	ORDER_OPPORTUNITY("J", "P");	// ④ 질서와 기회 (전과, 연령, 가족 · 부양, 직업, 사회적 유대)

	// 판결 성향의 엄격 · 관대 글자 (성향 산식 2-1). 내부 계산용이며 화면 · API 응답에는 내보내지 않는다(#119)
	private final String strictLetter;
	private final String lenientLetter;

	ValueAxis(String strictLetter, String lenientLetter) {
		this.strictLetter = strictLetter;
		this.lenientLetter = lenientLetter;
	}

	public String strictLetter() {
		return strictLetter;
	}

	public String lenientLetter() {
		return lenientLetter;
	}

	/** 판정값의 부호로 글자를 고른다. 0(동점)은 관대 글자 (성향 산식 2-3) */
	public String letterOf(int sign) {
		return sign >= 0 ? lenientLetter : strictLetter;
	}
}
