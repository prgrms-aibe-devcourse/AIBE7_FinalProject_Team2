package com.team2.project.admin.review;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PublishReadinessTest {

	@Test
	@DisplayName("모든 조건을 채우면 사유가 없고 ready")
	void ready() {
		PublishReadiness readiness = PublishReadiness.of(true, true, 1, 1, 1, true);

		assertThat(readiness.ready()).isTrue();
		assertThat(readiness.reasons()).isEmpty();
	}

	@Test
	@DisplayName("지면만 없으면 NO_PAPER 하나로 막힌다 (BE-147)")
	void noPaperOnly() {
		PublishReadiness readiness = PublishReadiness.of(true, true, 1, 1, 1, false);

		assertThat(readiness.ready()).isFalse();
		assertThat(readiness.reasons()).containsExactly("NO_PAPER");
	}

	@Test
	@DisplayName("모두 비면 A-13 표 순서대로, NO_PAPER는 맨 뒤")
	void allMissing_inTableOrder() {
		PublishReadiness readiness = PublishReadiness.of(false, false, 0, 0, 0, false);

		assertThat(readiness.reasons()).containsExactly("NO_PUBLISHED_AI", "NO_PUBLISHED_COURT", "NO_FACTORS",
			"NO_OVERVIEW_FACTOR", "NO_PENALTY_RULE", "NO_PAPER");
	}

	@Test
	@DisplayName("특별 모드 경고를 확인하지 않았으면 SPECIAL_MODE_UNREVIEWED가 맨 뒤에 붙는다 (BE-165)")
	void specialModePending() {
		assertThat(PublishReadiness.of(true, true, 1, 1, 1, true, true).reasons()).containsExactly("SPECIAL_MODE_UNREVIEWED");
		assertThat(PublishReadiness.of(true, true, 1, 1, 1, true, false).ready()).isTrue();
		assertThat(PublishReadiness.of(false, true, 1, 1, 1, true, true).reasons())
			.containsExactly("NO_PUBLISHED_AI", "SPECIAL_MODE_UNREVIEWED");
	}
}
