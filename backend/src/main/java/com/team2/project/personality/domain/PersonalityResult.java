package com.team2.project.personality.domain;

import com.team2.project.experience.domain.AnonymousUser;
import com.team2.project.legalcase.domain.ValueAxis;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 성향 테스트 응시 결과 (확장 단계, BE-69). 재응시하면 새 행을 만들고 이전 현재 결과는 isCurrent = false로 둔다(이력 보존).
 * 사용자마다 현재 결과는 최대 1건이다(DB 부분 유니크 ux_personality_result_current_user).
 * axisScores는 화면 표시용(관대 쪽 비중 0 ~ 1)이고, 글자는 제출 때 정수로 판정해 type에 저장한다(성향 산식 6장).
 */
@Entity
@Table(name = "personality_result")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PersonalityResult {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "anonymous_user_id")
	private AnonymousUser anonymousUser;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "type_code")
	private PersonalityType type;

	@JdbcTypeCode(SqlTypes.JSON)
	private Map<ValueAxis, Double> axisScores;

	@JdbcTypeCode(SqlTypes.JSON)
	private List<PersonalityAnswer> answers;

	@Column(name = "is_current")
	private boolean current;

	/** 응시 시점에 완료한 사건 수(API 명세 9-1 v0.50, BE-104). V28 전에 저장한 행은 알 수 없어 null */
	private Integer completedExperienceCount;

	/** 성향카드 재심(P-2 baseline)으로 낸 결과. 사용자당 1건(V28 부분 유니크 ux_personality_result_baseline_retake_user) */
	@Column(name = "is_baseline_retake")
	private boolean baselineRetake;

	@CreationTimestamp
	private Instant createdAt;

	/** 새 현재 결과(일반 응시, 응시 시점 건수 모름). 테스트 · 이전 호출용 */
	public static PersonalityResult create(AnonymousUser anonymousUser, PersonalityType type,
		Map<ValueAxis, Double> axisScores, List<PersonalityAnswer> answers) {
		return create(anonymousUser, type, axisScores, answers, null, false);
	}

	/**
	 * 새 현재 결과. 같은 사용자의 이전 현재 결과는 저장 전에 내려 둬야 한다(PersonalityResultRepository.clearCurrent).
	 * completedExperienceCount: 응시 시점에 완료한 사건 수, baselineRetake: 성향카드 재심 결과인지(BE-104)
	 */
	public static PersonalityResult create(AnonymousUser anonymousUser, PersonalityType type,
		Map<ValueAxis, Double> axisScores, List<PersonalityAnswer> answers, Integer completedExperienceCount,
		boolean baselineRetake) {
		if (completedExperienceCount != null && completedExperienceCount < 0) {
			throw new IllegalArgumentException("완료 사건 수는 0 이상이어야 한다");
		}
		// 키는 개수가 아니라 집합으로 본다(null 키 + 세 축도 크기는 4). NaN · 무한대는 범위 비교를 빠져나가므로 따로 막는다
		if (axisScores == null || !axisScores.keySet().equals(EnumSet.allOf(ValueAxis.class))
			|| axisScores.values().stream().anyMatch(
				score -> score == null || !Double.isFinite(score) || score < 0 || score > 1)) {
			throw new IllegalArgumentException("네 축 모두 0 ~ 1 점수가 있어야 한다");
		}
		PersonalityResult result = new PersonalityResult();
		result.anonymousUser = anonymousUser;
		result.type = type;
		result.axisScores = new EnumMap<>(axisScores);
		result.answers = List.copyOf(answers);
		result.current = true;
		result.completedExperienceCount = completedExperienceCount;
		result.baselineRetake = baselineRetake;
		return result;
	}
}
