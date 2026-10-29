package com.team2.project.legalcase.service;

import com.team2.project.legalcase.domain.CastRoles;
import com.team2.project.legalcase.domain.CrimeType;
import com.team2.project.legalcase.repository.LegalCaseRepository;
import java.util.HashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 새 사건의 배역을 정한다 (BE-125).
 *
 * <p>규칙 (character-profile-worldview 5-2 "범죄 유형별 균형이 연속 금지보다 우선"):
 * <ol>
 *   <li>같은 범죄 유형에서 피고인 역(묶음 B)을 <b>덜 맡은</b> 캐릭터가 이번 피고인 역을 맡는다.</li>
 *   <li>같으면 그 유형에서 마지막으로 배정된 사건의 <b>반대</b>로 한다(번갈아).</li>
 *   <li>그 유형의 첫 사건이면 기본값(남불 피고인 역 = 내로 판사 · 검사)이다. 서비스 설정 기본값과 같다.</li>
 * </ol>
 * 1 · 2만 쓰면 "마지막의 반대"와 같은 결과지만, 관리자가 배역을 고쳤거나 초기값이 한쪽으로 쏠린 유형에서는
 * 마지막만 보면 쏠림이 이어진다 — 그래서 수를 먼저 본다.
 *
 * <p><b>호출하는 쪽의 트랜잭션 안에서만 쓴다</b>({@code MANDATORY}). 범죄 유형별 잠금이 그 트랜잭션이 끝날 때
 * 풀려야 같은 유형의 다른 적재가 이 사건까지 세고 배정한다.
 */
@Component
@RequiredArgsConstructor
public class CaseCastAssigner {

	static final String DEFAULT_DEFENSE = CastRoles.NAMBUL;

	private final LegalCaseRepository caseRepository;

	@Transactional(propagation = Propagation.MANDATORY)
	public Map<String, String> assign(CrimeType crimeType) {
		lock(crimeType);
		String type = crimeType.name();

		Map<String, Long> counts = new HashMap<>();
		for (Object[] row : caseRepository.countDefenseByCrimeType(type)) {
			counts.put((String) row[0], ((Number) row[1]).longValue());
		}
		long naero = counts.getOrDefault(CastRoles.NAERO, 0L);
		long nambul = counts.getOrDefault(CastRoles.NAMBUL, 0L);
		String last = naero == nambul ? caseRepository.findLastDefenseByCrimeType(type) : null;
		return CastRoles.withDefense(nextDefense(naero, nambul, last));
	}

	/**
	 * 그 범죄 유형의 배정 잠금만 잡는다. 규칙을 거치지 않고 배역을 정하는 쪽(대본의 배역을 그대로 쓰는 적재)도
	 * 이 잠금을 잡아야, 규칙으로 배정하는 적재가 세는 도중에 그 사건이 끼어들지 못한다.
	 */
	@Transactional(propagation = Propagation.MANDATORY)
	public void lock(CrimeType crimeType) {
		caseRepository.lockCastAssignment(crimeType.name());
	}

	/**
	 * 규칙만 떼어 낸 것 (DB 없이 시험하고, 동시 적재 테스트가 기대값을 만들 때 쓴다).
	 *
	 * @param naeroDefense  그 유형에서 내로가 피고인 역을 맡은 사건 수
	 * @param nambulDefense 그 유형에서 남불이 피고인 역을 맡은 사건 수
	 * @param lastDefense   그 유형에서 마지막으로 배정된 사건의 피고인 역. 없으면 null
	 * @return 이번 사건의 피고인 역
	 */
	public static String nextDefense(long naeroDefense, long nambulDefense, String lastDefense) {
		if (naeroDefense != nambulDefense) {
			return naeroDefense < nambulDefense ? CastRoles.NAERO : CastRoles.NAMBUL;
		}
		return lastDefense == null ? DEFAULT_DEFENSE : CastRoles.other(lastDefense);
	}
}
