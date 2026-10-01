package com.team2.project.judgment.repository;

import com.team2.project.judgment.domain.AiGeneration;
import org.springframework.data.jpa.repository.JpaRepository;

/** (확장 단계) AI 판결 생성 기록 */
public interface AiGenerationRepository extends JpaRepository<AiGeneration, Long> {
}
