package com.team2.project.judgment.repository;

import com.team2.project.judgment.domain.Direction;
import com.team2.project.judgment.domain.JudgmentFactor;
import com.team2.project.legalcase.domain.ValueAxis;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface JudgmentFactorRepository extends JpaRepository<JudgmentFactor, Long> {

	/** 여러 판단의 요소 기록을 한 번에 (API 14 매트릭스). factor를 함께 읽어 N+1을 막는다 */
	@Query("select jf from JudgmentFactor jf join fetch jf.factor f "
		+ "where jf.judgment.id in :judgmentIds order by f.displayOrder")
	List<JudgmentFactor> findAllByJudgmentIds(@Param("judgmentIds") Collection<Long> judgmentIds);

	/**
	 * 판결 성향 기여 계산용: 체험의 사용자 최종 판단(USER · FINAL)에서 고른 요소를 가치관 축 · 방향별로 센다
	 * (성향 산식 3-1 · 7장 countAxis, BE-64 · WBS 7.6.7)
	 * - value_axis가 NULL인 요소는 뺀다 (어느 축에도 맞지 않는 요소)
	 * - 방향이 없는 행은 뺀다 (최종 판단은 방향이 필수지만, 사전 판단 행이 섞이지 않게 한 번 더 막는다)
	 * - value_axis_status(AUTO · CONFIRMED)는 따지지 않는다 — 관리자 확정을 기다리면 성향이 거의 계산되지 않는다 (3-1)
	 * timing · subject_type 조건은 서비스 코드에 쓰지 않고 이 메서드에만 둔다 (ERD 7장, JudgmentRepository와 같은 규칙)
	 */
	@Query("select f.valueAxis as axis, jf.direction as direction, count(jf) as count "
		+ "from JudgmentFactor jf join jf.factor f join jf.judgment j "
		+ "where j.experience.id = :experienceId "
		+ "and j.subjectType = com.team2.project.judgment.domain.SubjectType.USER "
		+ "and j.timing = com.team2.project.judgment.domain.Timing.FINAL "
		+ "and f.valueAxis is not null and jf.direction is not null "
		+ "group by f.valueAxis, jf.direction")
	List<AxisDirectionCount> countFinalFactorsByAxis(@Param("experienceId") Long experienceId);

	/** 축 · 방향별로 고른 요소 개수 ({@link #countFinalFactorsByAxis}의 결과 한 줄) */
	interface AxisDirectionCount {

		ValueAxis getAxis();

		Direction getDirection();

		long getCount();
	}
}
