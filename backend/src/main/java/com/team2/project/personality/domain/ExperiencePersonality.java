package com.team2.project.personality.domain;

import com.team2.project.experience.domain.Experience;
import com.team2.project.legalcase.domain.ValueAxis;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 판결 체험 하나의 성향 기여 (확장 단계, BE-69). 체험이 처음 COMPLETED가 될 때 한 번 저장하고 고치지 않는다(성향 산식 3-4, 7.6.7).
 * 축 점수(소수) 대신 축별 감경 · 가중 개수를 저장한다 — 종합 판정을 정확한 분수로 하고, value_axis가 나중에 바뀌어도
 * 지난 체험이 바뀌지 않게 하기 위해서다(성향 산식 3-3 · 6장). 체험마다 한 행(DB 유니크).
 */
@Entity
@Table(name = "experience_personality")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ExperiencePersonality {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@OneToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "experience_id")
	private Experience experience;

	@JdbcTypeCode(SqlTypes.JSON)
	private Map<ValueAxis, AxisCount> axisCounts;

	@CreationTimestamp
	private Instant createdAt;

	/** 네 축 모두 키를 둔다. 고른 요소가 없는 축은 AxisCount.EMPTY */
	public static ExperiencePersonality of(Experience experience, Map<ValueAxis, AxisCount> counts) {
		Objects.requireNonNull(counts, "counts");
		EnumMap<ValueAxis, AxisCount> all = new EnumMap<>(ValueAxis.class);
		for (ValueAxis axis : ValueAxis.values()) {
			AxisCount count = counts.get(axis);	// 키가 없거나 값이 null이면 0 · 0 (getOrDefault는 null 값을 그대로 둔다)
			all.put(axis, count == null ? AxisCount.EMPTY : count);
		}
		ExperiencePersonality contribution = new ExperiencePersonality();
		contribution.experience = experience;
		contribution.axisCounts = all;
		return contribution;
	}

	public AxisCount countOf(ValueAxis axis) {
		return axisCounts.getOrDefault(axis, AxisCount.EMPTY);
	}
}
