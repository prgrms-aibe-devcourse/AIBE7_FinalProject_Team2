package com.team2.project.admin.review;

import java.util.ArrayList;
import java.util.List;

/**
 * 사건 공개 조건 (API 명세 A-13 표). A-6은 화면에 보여 주고, A-13(BE-43)은 공개 전에 다시 확인한다.
 * 가치관 축이 아직 AUTO인 요소가 있어도 공개는 막지 않는다
 */
public record PublishReadiness(List<String> reasons) {

	public static PublishReadiness of(boolean hasPublishedAi, boolean hasPublishedCourt, int factorCount,
		int overviewFactorCount, int penaltyRuleCount) {
		List<String> reasons = new ArrayList<>();
		if (!hasPublishedAi) {
			reasons.add("NO_PUBLISHED_AI");
		}
		if (!hasPublishedCourt) {
			reasons.add("NO_PUBLISHED_COURT");
		}
		if (factorCount == 0) {
			reasons.add("NO_FACTORS");
		}
		if (overviewFactorCount == 0) {
			reasons.add("NO_OVERVIEW_FACTOR");
		}
		if (penaltyRuleCount == 0) {
			reasons.add("NO_PENALTY_RULE");
		}
		return new PublishReadiness(List.copyOf(reasons));
	}

	public boolean ready() {
		return reasons.isEmpty();
	}
}
