package com.team2.project.experience.service;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.MeterRegistry;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/**
 * 체험 시작 커스텀 메트릭(lawnambul.experience.started) (BE-66, WBS 7.2.1)
 * 실제로 새 체험을 만들 때만 세고, 이미 있는 체험을 다시 돌려줄 때는 세지 않는지 확인한다.
 */
@SpringBootTest
@Transactional
class ExperienceServiceMetricsTest {

	@Autowired JdbcTemplate jdbc;
	@Autowired ExperienceService experienceService;
	@Autowired MeterRegistry meterRegistry;

	private Long caseId;

	@BeforeEach
	void setUp() {
		caseId = jdbc.queryForObject("""
			INSERT INTO legal_case (title, crime_type, charge_name, short_intro, overview, applied_law,
			    statutory_penalty_text, status)
			VALUES ('BE-66 메트릭 시험 사건', 'MURDER', '살인', '소개', '개요', '형법 제250조', '사형, 무기 또는 5년 이상의 징역', 'PUBLISHED')
			RETURNING id""", Long.class);
	}

	private double experienceStartedCount() {
		return meterRegistry.get("lawnambul.experience.started").counter().count();
	}

	@Test
	@DisplayName("새 체험을 만들 때만 세고, 같은 사용자가 다시 조회하면 세지 않는다")
	void start_onlyCountsNewlyCreatedExperience() {
		double before = experienceStartedCount();

		var first = experienceService.start(caseId, Optional.empty());
		assertThat(first.created()).isTrue();
		assertThat(experienceStartedCount()).isEqualTo(before + 1);

		var again = experienceService.start(caseId, Optional.of(first.issuedCookieId()));
		assertThat(again.created()).isFalse();
		assertThat(experienceStartedCount()).isEqualTo(before + 1);
	}
}
