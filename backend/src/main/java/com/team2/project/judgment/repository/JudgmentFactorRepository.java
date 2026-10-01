package com.team2.project.judgment.repository;

import com.team2.project.judgment.domain.JudgmentFactor;
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
}
