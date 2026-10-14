package com.team2.project.judgment.repository;

import com.team2.project.judgment.domain.AiGeneration;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/** (확장 단계) AI 판결 생성 기록 */
public interface AiGenerationRepository extends JpaRepository<AiGeneration, Long> {

	/** 판결 후보 목록(A-6)용: 여러 판결의 생성 기록을 한 번에 */
	List<AiGeneration> findAllByJudgment_IdIn(Collection<Long> judgmentIds);

	/** 판결 후보 상세(A-8)용 */
	Optional<AiGeneration> findByJudgment_Id(Long judgmentId);
}
