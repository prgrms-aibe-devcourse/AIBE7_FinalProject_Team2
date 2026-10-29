package com.team2.project.legalcase.service;

import static com.team2.project.legalcase.domain.CastRoles.NAERO;
import static com.team2.project.legalcase.domain.CastRoles.NAMBUL;
import static org.assertj.core.api.Assertions.assertThat;

import com.team2.project.legalcase.domain.CastRoles;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 배역 배정 규칙 (BE-125). 잠금 · 조회는 CaseImportLoaderTest가 실제 DB로 본다 */
class CaseCastAssignerTest {

	@Test
	@DisplayName("그 유형의 첫 사건은 기본값 — 남불이 피고인 역, 내로가 판사 · 검사")
	void firstCaseUsesDefault() {
		assertThat(CaseCastAssigner.nextDefense(0, 0, null)).isEqualTo(NAMBUL);
	}

	@Test
	@DisplayName("수가 같으면 마지막 배정의 반대로 번갈아 맡는다")
	void tieAlternatesFromLast() {
		assertThat(CaseCastAssigner.nextDefense(2, 2, NAMBUL)).isEqualTo(NAERO);
		assertThat(CaseCastAssigner.nextDefense(2, 2, NAERO)).isEqualTo(NAMBUL);
	}

	@Test
	@DisplayName("쏠려 있으면 마지막과 같은 배정이 이어지더라도 덜 맡은 쪽이 맡는다 (균형이 연속 금지보다 우선)")
	void balanceBeatsAlternation() {
		// 남불 5 · 내로 4이고 마지막이 내로였어도 내로가 한 번 더 맡아 5 : 5가 된다
		assertThat(CaseCastAssigner.nextDefense(4, 5, NAERO)).isEqualTo(NAERO);
		assertThat(CaseCastAssigner.nextDefense(3, 1, NAMBUL)).isEqualTo(NAMBUL);
	}

	@Test
	@DisplayName("규칙대로 이어 배정하면 어느 시점에서 시작해도 두 캐릭터의 차이가 1을 넘지 않게 된다")
	void staysBalancedOverTime() {
		long naero = 0;
		long nambul = 3;	// 초기값이 쏠린 유형
		String last = NAMBUL;
		for (int i = 0; i < 10; i++) {
			last = CaseCastAssigner.nextDefense(naero, nambul, last);
			if (NAERO.equals(last)) {
				naero++;
			} else {
				nambul++;
			}
		}
		assertThat(Math.abs(naero - nambul)).isLessThanOrEqualTo(1);
	}

	@Test
	@DisplayName("배역 모양 검사: 키 둘 · 내로와 남불을 하나씩만 받는다")
	void castRolesParse() {
		assertThat(CastRoles.parse(Map.of("prosecutor", "nambul", "defense", "naero")))
			.contains(Map.of("prosecutor", "nambul", "defense", "naero"));
		for (Object invalid : List.of(Map.of("prosecutor", "naero", "defense", "naero"),
			Map.of("prosecutor", "naero"), Map.of("prosecutor", "naero", "defense", "nambul", "judge", "naero"),
			Map.of("prosecutor", "나로", "defense", "남불"), Map.of("prosecutor", 1, "defense", "nambul"),
			"naero", List.of())) {
			assertThat(CastRoles.parse(invalid)).as(String.valueOf(invalid)).isEmpty();
		}
		assertThat(CastRoles.parse(null)).isEmpty();
	}
}
