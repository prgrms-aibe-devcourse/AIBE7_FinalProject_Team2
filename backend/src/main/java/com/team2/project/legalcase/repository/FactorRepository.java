package com.team2.project.legalcase.repository;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.team2.project.legalcase.domain.Factor;
import com.team2.project.legalcase.domain.RevealStage;

public interface FactorRepository extends JpaRepository<Factor, Long> {

	List<Factor> findByCaseIdAndRevealStageOrderByDisplayOrder(Long caseId, RevealStage revealStage);

	/** 이 사건의 해당 공개 단계 요소 중 ids에 든 것의 개수. ids에 없는 · 남의 사건 · 다른 단계 요소는 세지 않는다. */
	long countByCaseIdAndRevealStageAndIdIn(Long caseId, RevealStage revealStage, Collection<Long> ids);
}
