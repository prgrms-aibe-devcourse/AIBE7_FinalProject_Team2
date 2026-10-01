package com.team2.project.experience.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.team2.project.experience.domain.AnonymousUser;
import com.team2.project.experience.domain.Experience;
import com.team2.project.experience.repository.AnonymousUserRepository;
import com.team2.project.experience.repository.ExperienceRepository;
import com.team2.project.legalcase.repository.LegalCaseRepository;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 조회 API는 @Transactional(readOnly = true) 안에서 내 체험을 찾는다.
 * 읽기 전용 트랜잭션에서도 최근 접속 시각 갱신(UPDATE)이 실패하지 않는지 확인한다 (별도 트랜잭션으로 갱신).
 * 트랜잭션을 직접 나눠야 해서 테스트 롤백 대신 끝난 뒤 직접 지운다.
 */
@SpringBootTest
class MyExperienceReadOnlyTransactionTest {

	@Autowired JdbcTemplate jdbc;
	@Autowired MyExperienceService myExperienceService;
	@Autowired AnonymousUserRepository anonymousUserRepository;
	@Autowired ExperienceRepository experienceRepository;
	@Autowired LegalCaseRepository legalCaseRepository;
	@Autowired PlatformTransactionManager transactionManager;

	private Long caseId;
	private UUID anonymousId;

	@BeforeEach
	void setUp() {
		new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
			caseId = jdbc.queryForObject("""
				INSERT INTO legal_case (title, crime_type, charge_name, short_intro, overview, applied_law,
				    statutory_penalty_text, status)
				VALUES ('읽기 전용 시험', 'FRAUD', '사기', '소개', '개요', '형법 제347조', '10년 이하 징역', 'PUBLISHED')
				RETURNING id""", Long.class);
			AnonymousUser user = anonymousUserRepository.save(AnonymousUser.issue(Instant.now()));
			anonymousId = user.getId();
			experienceRepository.save(Experience.start(user, legalCaseRepository.getReferenceById(caseId)));
		});
		jdbc.update("UPDATE anonymous_user SET last_seen_at = now() - interval '1 hour' WHERE id = ?", anonymousId);
	}

	@AfterEach
	void tearDown() {
		jdbc.update("DELETE FROM experience WHERE case_id = ?", caseId);
		jdbc.update("DELETE FROM anonymous_user WHERE id = ?", anonymousId);
		jdbc.update("DELETE FROM legal_case WHERE id = ?", caseId);
	}

	@Test
	@DisplayName("읽기 전용 트랜잭션 안에서도 내 체험을 찾고, 최근 접속 시각은 10분이 지났을 때만 갱신한다")
	void getMyExperience_insideReadOnlyTransaction_touchesLastSeen() {
		Instant before = lastSeenAt();
		TransactionTemplate readOnly = new TransactionTemplate(transactionManager);
		readOnly.setReadOnly(true);

		Experience experience = readOnly.execute(status -> myExperienceService.getMyExperience(caseId, Optional.of(anonymousId)));

		assertThat(experience).isNotNull();
		Instant touched = lastSeenAt();
		assertThat(touched).isAfter(before);

		// 10분 안에 다시 조회하면 갱신하지 않는다 (조회마다 UPDATE 방지)
		readOnly.execute(status -> myExperienceService.getMyExperience(caseId, Optional.of(anonymousId)));
		assertThat(lastSeenAt()).isEqualTo(touched);
	}

	private Instant lastSeenAt() {
		return jdbc.queryForObject("SELECT last_seen_at FROM anonymous_user WHERE id = ?", Timestamp.class, anonymousId)
			.toInstant();
	}
}
