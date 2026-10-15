package com.team2.project.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.team2.project.experience.domain.AnonymousUser;
import com.team2.project.experience.domain.Experience;
import com.team2.project.experience.repository.AnonymousUserRepository;
import com.team2.project.experience.repository.ExperienceRepository;
import com.team2.project.legalcase.domain.LegalCase;
import com.team2.project.legalcase.domain.ValueAxis;
import com.team2.project.personality.domain.AxisCount;
import com.team2.project.personality.domain.ExperiencePersonality;
import com.team2.project.personality.domain.PersonalityAnswer;
import com.team2.project.personality.domain.PersonalityCharacter;
import com.team2.project.personality.domain.PersonalityChoice;
import com.team2.project.personality.domain.PersonalityQuestion;
import com.team2.project.personality.domain.PersonalityResult;
import com.team2.project.personality.domain.PersonalityScenario;
import com.team2.project.personality.domain.PersonalityType;
import com.team2.project.personality.repository.ExperiencePersonalityRepository;
import com.team2.project.personality.repository.PersonalityChoiceRepository;
import com.team2.project.personality.repository.PersonalityQuestionRepository;
import com.team2.project.personality.repository.PersonalityResultRepository;
import com.team2.project.personality.repository.PersonalityTypeRepository;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/**
 * 판결 성향 테이블(V15) 매핑 · 제약 검증 (BE-69 · WBS 7.6.3). 실제 PostgreSQL, 테스트마다 롤백.
 * 콘텐츠(문항 · 선택지 · 유형)는 운영처럼 SQL 시드로 넣고, 결과 · 체험 기여는 엔티티로 저장한다.
 * 제약 위반은 트랜잭션을 깨뜨리므로 테스트마다 마지막 문장에서만 확인한다.
 */
@SpringBootTest
@Transactional
class PersonalityPersistenceTest {

	private static final Map<ValueAxis, Double> SCORES = Map.of(
		ValueAxis.APOLOGY_SINCERITY, 0.5, ValueAxis.FAULT_STANDARD, 0.75,
		ValueAxis.PRINCIPLE_RELATION, 0.25, ValueAxis.ORDER_OPPORTUNITY, 0.6875);

	@Autowired JdbcTemplate jdbc;
	@Autowired EntityManager em;
	@Autowired PersonalityQuestionRepository questionRepository;
	@Autowired PersonalityChoiceRepository choiceRepository;
	@Autowired PersonalityTypeRepository typeRepository;
	@Autowired PersonalityResultRepository resultRepository;
	@Autowired ExperiencePersonalityRepository contributionRepository;
	@Autowired AnonymousUserRepository anonymousUserRepository;
	@Autowired ExperienceRepository experienceRepository;

	private Long questionId;
	private AnonymousUser user;

	@BeforeEach
	void setUp() {
		// 유형 16개는 콘텐츠 시드(R__personality_content)가 넣어 둔다. 여기서는 INTP · ESTJ를 쓴다
		questionId = jdbc.queryForObject("""
			INSERT INTO personality_question (axis, scenario, content, option_a, option_b, display_order)
			VALUES ('APOLOGY_SINCERITY', 'FRIEND', '약속을 어긴 친구가 사과한다', '행동을 보고 풀겠다', '말을 믿겠다', 9001)
			RETURNING id""", Long.class);
		// 매우 A(E) · 조금 A(E) · 보통 · 조금 B(I) · 매우 B(I)
		jdbc.update("""
			INSERT INTO personality_choice (question_id, axis, letter, score, display_order)
			VALUES (?, 'APOLOGY_SINCERITY', 'E', 2, 1), (?, 'APOLOGY_SINCERITY', 'E', 1, 2),
			       (?, 'APOLOGY_SINCERITY', NULL, 0, 3),
			       (?, 'APOLOGY_SINCERITY', 'I', 1, 4), (?, 'APOLOGY_SINCERITY', 'I', 2, 5)""",
			questionId, questionId, questionId, questionId, questionId);
		user = anonymousUserRepository.save(AnonymousUser.issue(Instant.now()));
	}

	/**
	 * 선택지가 하나도 없는 문항. 제약 위반 테스트용이다.
	 * setUp 문항은 순서 1 ~ 5(CHECK로 이 범위만 허용)를 이미 다 써서, 거기에 넣으면
	 * 확인하려는 제약보다 유니크(question_id, display_order)가 먼저 막는다.
	 */
	private Long emptyQuestion() {
		return jdbc.queryForObject("""
			INSERT INTO personality_question (axis, scenario, content, option_a, option_b, display_order)
			VALUES ('APOLOGY_SINCERITY', 'WORK', '선택지 없는 시험 문항', 'A 행동', 'B 행동', 9002)
			RETURNING id""", Long.class);
	}

	private static String swapCase(String value) {
		StringBuilder swapped = new StringBuilder(value.length());
		for (char c : value.toCharArray()) {
			swapped.append(Character.isUpperCase(c) ? Character.toLowerCase(c) : Character.toUpperCase(c));
		}
		return swapped.toString();
	}

	private Experience startExperience() {
		Long caseId = jdbc.queryForObject("""
			INSERT INTO legal_case (title, crime_type, charge_name, short_intro, overview, applied_law,
			    statutory_penalty_text, status, published_at)
			VALUES ('성향 시험 사건 ' || gen_random_uuid(), 'MURDER', '살인', '소개', '개요', '형법 제250조 제1항',
			    '사형, 무기 또는 5년 이상의 징역', 'PUBLISHED', now())
			RETURNING id""", Long.class);
		return experienceRepository.save(Experience.start(user, em.find(LegalCase.class, caseId)));
	}

	@Test
	@DisplayName("문항 · 선택지를 읽는다 — A · B 문장은 문항에, 선택지는 글자 · 점수 · 순서만, 보통은 글자 없음")
	void questionAndChoices_mapped() {
		PersonalityQuestion question = questionRepository.findById(questionId).orElseThrow();
		assertThat(question.getAxis()).isEqualTo(ValueAxis.APOLOGY_SINCERITY);
		assertThat(question.getScenario()).isEqualTo(PersonalityScenario.FRIEND);
		assertThat(question.getOptionA()).isEqualTo("행동을 보고 풀겠다");
		assertThat(question.getOptionB()).isEqualTo("말을 믿겠다");
		assertThat(question.isActive()).isTrue();
		assertThat(questionRepository.findAllByActiveTrueOrderByDisplayOrderAsc()).extracting(PersonalityQuestion::getId)
			.contains(questionId);

		List<PersonalityChoice> choices = choiceRepository.findAllByQuestion_IdInOrderByQuestion_IdAscDisplayOrderAsc(
			List.of(questionId));
		assertThat(choices).extracting(PersonalityChoice::getLetter).containsExactly("E", "E", null, "I", "I");
		assertThat(choices).extracting(PersonalityChoice::getScore).containsExactly(2, 1, 0, 1, 2);
		assertThat(choices.get(2).isNeutral()).isTrue();
	}

	@Test
	@DisplayName("선택지 글자는 문항 축의 글자만 (복합 FK로 끌어온 축 + CHECK)")
	void choice_letterOfOtherAxis_rejected() {
		assertThatThrownBy(() -> jdbc.update("""
			INSERT INTO personality_choice (question_id, axis, letter, score, display_order)
			VALUES (?, 'APOLOGY_SINCERITY', 'S', 2, 1)""", emptyQuestion()))
			.isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("chk_personality_choice_letter");
	}

	@Test
	@DisplayName("선택지 축이 문항 축과 다르면 복합 FK가 거절한다")
	void choice_axisDifferentFromQuestion_rejected() {
		assertThatThrownBy(() -> jdbc.update("""
			INSERT INTO personality_choice (question_id, axis, letter, score, display_order)
			VALUES (?, 'FAULT_STANDARD', 'S', 2, 1)""", emptyQuestion()))
			.isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("fk_personality_choice_question_axis");
	}

	@Test
	@DisplayName("글자 없는 선택지는 0점만 (letter NULL + score 1은 CHECK가 막는다)")
	void choice_nullLetterWithScore_rejected() {
		assertThatThrownBy(() -> jdbc.update("""
			INSERT INTO personality_choice (question_id, axis, letter, score, display_order)
			VALUES (?, 'APOLOGY_SINCERITY', NULL, 1, 1)""", emptyQuestion()))
			.isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("chk_personality_choice_letter");
	}

	@Test
	@DisplayName("칸 순서와 점수가 어긋나면 막는다 (매우 칸에 1점)")
	void choice_orderScoreMismatch_rejected() {
		assertThatThrownBy(() -> jdbc.update("""
			INSERT INTO personality_choice (question_id, axis, letter, score, display_order)
			VALUES (?, 'APOLOGY_SINCERITY', 'E', 1, 1)""", emptyQuestion()))
			.isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("chk_personality_choice_order_score");
	}

	@Test
	@DisplayName("보통 칸(3)이 아닌 곳에 글자 없는 선택지는 막는다")
	void choice_neutralOutsideMiddle_rejected() {
		assertThatThrownBy(() -> jdbc.update("""
			INSERT INTO personality_choice (question_id, axis, letter, score, display_order)
			VALUES (?, 'APOLOGY_SINCERITY', NULL, 0, 1)""", emptyQuestion()))
			.isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("chk_personality_choice_order_score");
	}

	@Test
	@DisplayName("유형은 공유 주소(shareId)로 찾고, 캐릭터 · 설명 칸이 매핑된다")
	void type_findByShareId() {
		String shareId = jdbc.queryForObject("SELECT share_id FROM personality_type WHERE code = 'INTP'", String.class);
		PersonalityType type = typeRepository.findByShareId(shareId).orElseThrow();
		assertThat(type.getCode()).isEqualTo("INTP");
		assertThat(type.getName()).isEqualTo("사연 분석러형");
		assertThat(type.getCharacter()).isEqualTo(PersonalityCharacter.NAMBUL);
		assertThat(type.getTolerance()).isEqualTo(3);
		assertThat(type.getMeme()).isNotBlank();
		assertThat(type.getDailyDescription()).isNotBlank();
		assertThat(type.getVerdictDescription()).isNotBlank();
		assertThat(type.getImageUrl()).isEqualTo("/images/characters/personality/29.webp");	// 유형 13 + 남불 16 (BE-73, 번호표)
		assertThat(typeRepository.findByShareId(swapCase(shareId))).isEmpty();	// 대소문자 구분
	}

	@Test
	@DisplayName("관용 지수는 코드의 I · N · F · P 개수와 같아야 한다")
	void type_wrongTolerance_rejected() {
		assertThatThrownBy(() -> jdbc.update("UPDATE personality_type SET tolerance = 2 WHERE code = 'INFP'"))
			.isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("chk_personality_type_tolerance");
	}

	@Test
	@DisplayName("공유 주소는 영숫자 6 ~ 16자만")
	void type_badShareId_rejected() {
		assertThatThrownBy(() -> jdbc.update("UPDATE personality_type SET share_id = 'a-b' WHERE code = 'INFP'"))
			.isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("chk_personality_type_share_id");
	}

	@Test
	@DisplayName("결과의 축 점수 · 응답(jsonb)을 저장하고 읽는다")
	void result_jsonbRoundTrip() {
		PersonalityResult saved = resultRepository.saveAndFlush(PersonalityResult.create(user,
			typeRepository.getReferenceById("INTP"), SCORES, List.of(new PersonalityAnswer(questionId, 3))));
		em.clear();

		PersonalityResult read = resultRepository.findByAnonymousUser_IdAndCurrentTrue(user.getId()).orElseThrow();
		assertThat(read.getId()).isEqualTo(saved.getId());
		assertThat(read.getType().getCode()).isEqualTo("INTP");
		assertThat(read.getAxisScores()).containsEntry(ValueAxis.APOLOGY_SINCERITY, 0.5)
			.containsEntry(ValueAxis.ORDER_OPPORTUNITY, 0.6875).hasSize(4);
		assertThat(read.getAnswers()).containsExactly(new PersonalityAnswer(questionId, 3));
		assertThat(read.getCreatedAt()).isNotNull();
	}

	@Test
	@DisplayName("재응시: 이전 현재 결과를 내리고 새 결과를 저장하면 이력은 남고 현재는 1건")
	void result_retake_keepsHistoryOneCurrent() {
		resultRepository.saveAndFlush(PersonalityResult.create(user, typeRepository.getReferenceById("INTP"), SCORES, List.of()));

		assertThat(resultRepository.clearCurrent(user.getId())).isEqualTo(1);
		resultRepository.saveAndFlush(PersonalityResult.create(user, typeRepository.getReferenceById("ESTJ"), SCORES, List.of()));

		assertThat(jdbc.queryForObject("SELECT count(*) FROM personality_result WHERE anonymous_user_id = ?", Long.class,
			user.getId())).isEqualTo(2);
		assertThat(resultRepository.findByAnonymousUser_IdAndCurrentTrue(user.getId()).orElseThrow().getType().getCode())
			.isEqualTo("ESTJ");
	}

	@Test
	@DisplayName("현재 결과를 내리지 않고 두 번째 현재 결과를 넣으면 부분 유니크가 막는다")
	void result_twoCurrent_rejected() {
		resultRepository.saveAndFlush(PersonalityResult.create(user, typeRepository.getReferenceById("INTP"), SCORES, List.of()));

		assertThatThrownBy(() -> resultRepository.saveAndFlush(
			PersonalityResult.create(user, typeRepository.getReferenceById("ESTJ"), SCORES, List.of())))
			.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	@DisplayName("결과 축 점수는 네 축 모두 0 ~ 1 (엔티티가 먼저 막는다)")
	void result_missingAxis_rejectedByEntity() {
		assertThatThrownBy(() -> PersonalityResult.create(user, null,
			Map.of(ValueAxis.APOLOGY_SINCERITY, 0.5), List.of())).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> PersonalityResult.create(user, null, null, List.of()))
			.isInstanceOf(IllegalArgumentException.class);
		// NaN은 0 ~ 1 비교를 둘 다 빠져나가므로 따로 막아야 한다 (#124 리뷰)
		assertThatThrownBy(() -> PersonalityResult.create(user, null, Map.of(
			ValueAxis.APOLOGY_SINCERITY, Double.NaN, ValueAxis.FAULT_STANDARD, 0.5,
			ValueAxis.PRINCIPLE_RELATION, 0.5, ValueAxis.ORDER_OPPORTUNITY, 0.5), List.of()))
			.isInstanceOf(IllegalArgumentException.class);
		// 키 개수가 4여도 네 축이 아니면 막는다 (null 키 + 세 축)
		Map<ValueAxis, Double> nullKey = new HashMap<>();
		nullKey.put(null, 0.5);
		nullKey.put(ValueAxis.FAULT_STANDARD, 0.5);
		nullKey.put(ValueAxis.PRINCIPLE_RELATION, 0.5);
		nullKey.put(ValueAxis.ORDER_OPPORTUNITY, 0.5);
		assertThatThrownBy(() -> PersonalityResult.create(user, null, nullKey, List.of()))
			.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	@DisplayName("체험 기여(axis_counts)를 저장하고 읽는다 — 고르지 않은 축도 키가 있고 0 · 0")
	void contribution_jsonbRoundTrip() {
		Experience experience = startExperience();
		contributionRepository.saveAndFlush(ExperiencePersonality.of(experience, Map.of(
			ValueAxis.APOLOGY_SINCERITY, new AxisCount(1, 0), ValueAxis.FAULT_STANDARD, new AxisCount(0, 2))));
		em.clear();

		ExperiencePersonality read = contributionRepository.findByExperience_Id(experience.getId()).orElseThrow();
		assertThat(read.countOf(ValueAxis.APOLOGY_SINCERITY)).isEqualTo(new AxisCount(1, 0));
		assertThat(read.countOf(ValueAxis.FAULT_STANDARD)).isEqualTo(new AxisCount(0, 2));
		assertThat(read.countOf(ValueAxis.ORDER_OPPORTUNITY).hasData()).isFalse();
		assertThat(read.getAxisCounts()).hasSize(4);
		assertThat(read.getAxisCounts().values()).doesNotContainNull();
		assertThat(contributionRepository.findFirstAttemptsByAnonymousUser(user.getId()))
			.extracting(p -> p.getExperience().getId()).containsExactly(experience.getId());
	}

	@Test
	@DisplayName("기여 개수에 null 값이 들어와도 0 · 0으로 채운다 (getOrDefault는 null 값을 그대로 둔다)")
	void contribution_nullValue_filledEmpty() {
		Map<ValueAxis, AxisCount> counts = new HashMap<>();
		counts.put(ValueAxis.APOLOGY_SINCERITY, null);
		counts.put(ValueAxis.FAULT_STANDARD, new AxisCount(1, 1));
		ExperiencePersonality contribution = ExperiencePersonality.of(startExperience(), counts);
		assertThat(contribution.getAxisCounts().get(ValueAxis.APOLOGY_SINCERITY)).isEqualTo(AxisCount.EMPTY);
		assertThat(contribution.getAxisCounts().get(ValueAxis.FAULT_STANDARD)).isEqualTo(new AxisCount(1, 1));
	}

	@Test
	@DisplayName("체험마다 기여는 한 건만 (다시 저장하면 유니크가 막는다)")
	void contribution_duplicate_rejected() {
		Experience experience = startExperience();
		contributionRepository.saveAndFlush(ExperiencePersonality.of(experience, Map.of()));

		assertThatThrownBy(() -> contributionRepository.saveAndFlush(ExperiencePersonality.of(experience, Map.of())))
			.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	@DisplayName("축 개수는 0 이상의 정수 (DB CHECK)")
	void contribution_negativeCount_rejected() {
		Experience experience = startExperience();

		assertThatThrownBy(() -> jdbc.update("""
			INSERT INTO experience_personality (experience_id, axis_counts) VALUES (?, '{
			  "APOLOGY_SINCERITY": {"down": -1, "up": 0}, "FAULT_STANDARD": {"down": 0, "up": 0},
			  "PRINCIPLE_RELATION": {"down": 0, "up": 0}, "ORDER_OPPORTUNITY": {"down": 0, "up": 0}}'::jsonb)""",
			experience.getId()))
			.isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("chk_experience_personality_axis_counts");
	}

	@Test
	@DisplayName("축 키가 하나라도 빠지면 DB CHECK가 막는다")
	void contribution_missingAxisKey_rejected() {
		Experience experience = startExperience();

		assertThatThrownBy(() -> jdbc.update("""
			INSERT INTO experience_personality (experience_id, axis_counts)
			VALUES (?, '{"APOLOGY_SINCERITY": {"down": 1, "up": 0}}'::jsonb)""", experience.getId()))
			.isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("chk_experience_personality_axis_counts");
	}
}
