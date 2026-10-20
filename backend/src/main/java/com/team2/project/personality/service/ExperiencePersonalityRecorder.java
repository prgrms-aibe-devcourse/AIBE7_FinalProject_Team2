package com.team2.project.personality.service;

import com.team2.project.experience.domain.Experience;
import com.team2.project.experience.repository.ExperienceRepository;
import com.team2.project.judgment.domain.Direction;
import com.team2.project.judgment.repository.JudgmentFactorRepository;
import com.team2.project.judgment.repository.JudgmentFactorRepository.AxisDirectionCount;
import com.team2.project.legalcase.domain.ValueAxis;
import com.team2.project.personality.domain.AxisCount;
import com.team2.project.personality.domain.ExperiencePersonality;
import com.team2.project.personality.repository.ExperiencePersonalityRepository;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 판결 체험 하나의 성향 기여(축별 감경 · 가중 개수)를 저장한다 (확장 단계, BE-64 · WBS 7.6.7, 성향 산식 3장)
 *
 * 체험이 처음 COMPLETED가 될 때(API 13 비교 공개) 상태 변경과 같은 트랜잭션에서 한 번만 부른다 (3-4).
 * - 단독으로 부르지 못하게 MANDATORY로 둔다. 저장이 실패하면 COMPLETED 전이도 함께 롤백되어
 *   "완료했는데 성향 기여가 없는" 체험이 생기지 않는다 (P-5 experienceCount가 완료 체험 수와 어긋나지 않게, #128)
 * - 그 순간의 factor.value_axis로 세어 개수를 고정 저장한다. 나중에 관리자가 축을 바꿔도 지난 체험은 바뀌지 않는다 (3-4)
 * - 첫 회차 체험만 저장한다. 두 번째 체험부터는 실제 판결을 이미 알고 있어 통계에서 뺀다 (3-1, ERD 결정 #5, #109)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ExperiencePersonalityRecorder {

	private final JudgmentFactorRepository judgmentFactorRepository;
	private final ExperiencePersonalityRepository experiencePersonalityRepository;
	private final ExperienceRepository experienceRepository;

	/**
	 * 체험의 성향 기여를 센 뒤 저장한다. 첫 회차가 아니면 아무것도 하지 않는다.
	 * 넘긴 체험은 상태 전이 뒤라 영속성 컨텍스트에서 분리돼 있을 수 있다(ExperienceTransitionService).
	 * id · attemptNo 값만 읽고, 저장에는 참조를 새로 얻어 쓴다.
	 */
	@Transactional(propagation = Propagation.MANDATORY)
	public void recordOnCompleted(Experience experience) {
		if (experience.getAttemptNo() != Experience.FIRST_ATTEMPT) {
			return;
		}
		Long experienceId = experience.getId();
		Map<ValueAxis, AxisCount> axisCounts = countByAxis(
			judgmentFactorRepository.countFinalFactorsByAxis(experienceId));
		// 고른 요소가 없는 축(또는 전부)은 of()가 {down: 0, up: 0}으로 채운다 — 판단 요소가 0개여도 행은 남긴다 (3-4).
		// 맵을 직접 만들어 저장하지 않는다: 축 값이 null이면 V15 CHECK(chk_experience_personality_axis_counts)에 걸린다 (#124)
		experiencePersonalityRepository.save(
			ExperiencePersonality.of(experienceRepository.getReferenceById(experienceId), axisCounts));
		log.debug("체험 성향 기여 저장: experienceId={}, axisCounts={}", experienceId, axisCounts);
	}

	/** 축 · 방향별 개수 행을 축별 AxisCount로 모은다. 감경(DOWN) → 관대 글자 down, 가중(UP) → 엄격 글자 up (3-2) */
	static Map<ValueAxis, AxisCount> countByAxis(List<AxisDirectionCount> rows) {
		Map<ValueAxis, int[]> raw = new EnumMap<>(ValueAxis.class);	// 축 → [down, up]
		for (AxisDirectionCount row : rows) {
			int[] counts = raw.computeIfAbsent(row.getAxis(), axis -> new int[2]);
			int index = row.getDirection() == Direction.DOWN ? 0 : 1;
			counts[index] += Math.toIntExact(row.getCount());
		}
		Map<ValueAxis, AxisCount> result = new EnumMap<>(ValueAxis.class);
		raw.forEach((axis, counts) -> result.put(axis, new AxisCount(counts[0], counts[1])));
		return result;
	}
}
