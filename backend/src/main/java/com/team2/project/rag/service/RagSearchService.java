package com.team2.project.rag.service;

import com.team2.project.common.exception.BusinessException;
import com.team2.project.common.exception.ErrorCode;
import com.team2.project.legalcase.domain.LegalCase;
import com.team2.project.legalcase.repository.CaseSourceRepository;
import com.team2.project.legalcase.repository.LegalCaseRepository;
import com.team2.project.rag.config.RagProperties;
import com.team2.project.rag.domain.CaseNumberHash;
import com.team2.project.rag.domain.RagPrecedentHit;
import com.team2.project.rag.embedding.EmbeddingException;
import com.team2.project.rag.embedding.QueryEmbeddingClient;
import com.team2.project.rag.repository.RagPrecedentRepository;
import java.util.Collection;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 사건 하나에 대한 유사 판례 검색 (BE-51). AI 판결 생성(generate, BE-54) · 관리자 검색 확인(BE-39 이후)이 쓴다.
 *
 * - 검색어는 비식별화한 사건 개요(legal_case.overview)다. 원문 · 사건번호는 임베딩 API로 보내지 않는다
 * - 사건의 원본 판결문(case_source) 사건번호를 해시로 바꿔, 그 해시가 있는 사건 그룹(다른 심급 포함)을 통째로 뺀다.
 *   그래야 실제 판결 결과가 AI에게 노출되지 않는다
 * - 판례 임베딩 모델 · 차원이 검색어와 다르면 비교할 수 없으므로 멈춘다
 */
@Service
@RequiredArgsConstructor
public class RagSearchService {

	private final LegalCaseRepository legalCaseRepository;
	private final CaseSourceRepository caseSourceRepository;
	private final RagPrecedentRepository ragPrecedentRepository;
	private final QueryEmbeddingClient embeddingClient;
	private final RagProperties properties;

	@Transactional(readOnly = true)
	public RagSearchResult searchSimilar(Long caseId) {
		return searchSimilar(caseId, List.of());
	}

	/**
	 * @param excludeGroups 사건번호로 찾지 못하는 같은 사건(공범 별도 판결 · 병합 사건 등)을 알고 있을 때 직접 뺄 사건 그룹
	 */
	@Transactional(readOnly = true)
	public RagSearchResult searchSimilar(Long caseId, Collection<String> excludeGroups) {
		LegalCase legalCase = legalCaseRepository.findById(caseId)
			.orElseThrow(() -> new BusinessException(ErrorCode.CASE_NOT_FOUND));
		if (!embeddingClient.enabled()) {
			return RagSearchResult.disabled();
		}
		if (legalCase.getOverview() == null || legalCase.getOverview().isBlank()) {
			throw new EmbeddingException("사건 개요가 비어 있어 유사 판례를 찾을 수 없습니다 (caseId=" + caseId + ")");
		}
		checkEmbeddingSpec();
		List<String> excludeHashes = caseSourceRepository.findCaseNumbersByCaseId(caseId).stream()
			.map(CaseNumberHash::of)
			.toList();
		float[] query = embeddingClient.embedQuery(legalCase.getOverview());
		RagProperties.Search search = properties.search();
		List<RagPrecedentHit> hits = ragPrecedentRepository.search(query, legalCase.getCrimeType().name(),
			excludeHashes, excludeGroups, search.topK(), search.minScore());
		return new RagSearchResult(true, hits);
	}

	private void checkEmbeddingSpec() {
		String expected = embeddingClient.model() + ":" + properties.embedding().dimensions();
		List<String> stored = ragPrecedentRepository.findEmbeddingSpecs();
		if (stored.stream().anyMatch(spec -> !spec.equals(expected))) {
			throw new EmbeddingException("판례 임베딩(" + String.join(", ", stored) + ")이 검색어 임베딩(" + expected
				+ ")과 다릅니다. 같은 모델 · 차원으로 다시 적재한다");
		}
	}
}
