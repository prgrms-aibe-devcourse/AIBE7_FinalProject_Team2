package com.team2.project.legalcase.repository;

import com.team2.project.legalcase.domain.CrimeType;
import com.team2.project.legalcase.domain.SentenceRangeOption;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SentenceRangeOptionRepository extends JpaRepository<SentenceRangeOption, Long> {

	/** 범죄 유형별 사전 판단 구간, 표시 순서 (API 4 · 5) */
	List<SentenceRangeOption> findAllByCrimeTypeOrderByDisplayOrderAsc(CrimeType crimeType);

	/** 사건의 범죄 유형에 속한 구간인지 (API 5 검증, 다른 범죄 유형의 구간은 거절) */
	boolean existsByIdAndCrimeType(Long id, CrimeType crimeType);
}
