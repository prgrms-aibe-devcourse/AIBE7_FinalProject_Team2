package com.team2.project.legalcase.repository;

import com.team2.project.legalcase.domain.SentencingGuideline;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SentencingGuidelineRepository extends JpaRepository<SentencingGuideline, Long> {

	/**
	 * 기준일에 적용할 양형기준 버전 (BE-93 2차 적재).
	 *
	 * <p>V13이 만든 DB 함수를 그대로 쓴다 — 파이썬 시드 SQL도 같은 함수를 부른다. 판례가 적재된
	 * (rag_guideline이 있는) 버전 중 기준일 이전으로 가장 최근 것을 고른다. 없으면 빈 값이고, 그때는
	 * 사건의 guideline_id가 비게 된다(사기 · 상해는 아직 양형기준을 넣지 않았다).
	 */
	@Query(value = "select sentencing_guideline_at(:category, cast(:at as date))", nativeQuery = true)
	Long findIdApplicableAt(@Param("category") String category, @Param("at") java.time.LocalDate at);
}
