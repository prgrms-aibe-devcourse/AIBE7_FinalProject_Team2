package com.team2.project.legalcase.domain;

/**
 * 판단 요소가 대응하는 가치관 축 (factor.value_axis)
 * DB CHECK 제약과 같은 값을 쓴다. 어느 축에도 맞지 않는 요소는 null이다.
 */
public enum ValueAxis {
	APOLOGY_SINCERITY,	// ① 사과와 진정성 (반성, 자수, 수사 협조, 사후 정황)
	FAULT_STANDARD,		// ② 잘잘못의 기준 (범행 동기, 수단 · 방법, 계획성, 결과의 중대성)
	PRINCIPLE_RELATION,	// ③ 원칙과 관계 (피해 회복, 합의 · 처벌불원, 피해자 과실)
	ORDER_OPPORTUNITY	// ④ 질서와 기회 (전과, 연령, 가족 · 부양, 직업, 사회적 유대)
}
