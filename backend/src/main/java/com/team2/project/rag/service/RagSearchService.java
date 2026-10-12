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
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 사건 하나에 대한 유사 판례 검색 (BE-51). AI 판결 생성(generate, BE-54) · 관리자 검색 확인(BE-39 이후)이 쓴다.
 *
 * - 검색어는 비식별화한 사건 개요(legal_case.overview)다. 원문 · 사건번호는 임베딩 API로 보내지 않는다
 * - 사건의 원본 판결문(case_source) 사건번호를 번호마다 해시로 바꿔, 그 해시가 하나라도 있는 사건 그룹(다른 심급 포함)을 통째로 뺀다.
 *   그래야 실제 판결 결과가 AI에게 노출되지 않는다
 * - 판례 임베딩 모델 · 차원이 검색어와 다르면 비교할 수 없으므로 멈춘다. 판례 저장소가 비어 있으면 RAG 꺼짐으로 알린다
 * - 점수가 rag.search.max-score 이상인 판례는 해시로 못 잡은 자기 사건일 수 있어 빼고 경고한다
 * - 서비스 트랜잭션을 두지 않는다. 임베딩 HTTP 호출(시간 제한 · 재시도 대기)이 DB 연결을 붙잡지 않게, 조회는 저장소마다
 *   짧게 끝낸다 (legal_case는 개요 · 범죄 유형 같은 기본 칼럼만 읽는다)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RagSearchService {

	/** 같은 사건 의심(max-score 이상)으로 뺄 판례를 대비해 top-k보다 더 받는 수 */
	private static final int NEAR_DUPLICATE_SLACK = 3;

	private final LegalCaseRepository legalCaseRepository;
	private final CaseSourceRepository caseSourceRepository;
	private final RagPrecedentRepository ragPrecedentRepository;
	private final QueryEmbeddingClient embeddingClient;
	private final RagProperties properties;

	public RagSearchResult searchSimilar(Long caseId) {
		return searchSimilar(caseId, List.of());
	}

	/**
	 * @param excludeGroups 사건번호로 찾지 못하는 같은 사건(공범 별도 판결 · 병합 사건 등)을 알고 있을 때 직접 뺄 사건 그룹
	 */
	public RagSearchResult searchSimilar(Long caseId, Collection<String> excludeGroups) {
		LegalCase legalCase = legalCaseRepository.findById(caseId)
			.orElseThrow(() -> new BusinessException(ErrorCode.CASE_NOT_FOUND));
		if (!embeddingClient.enabled()) {
			return RagSearchResult.disabled();
		}
		if (legalCase.getOverview() == null || legalCase.getOverview().isBlank()) {
			throw new EmbeddingException("사건 개요가 비어 있어 유사 판례를 찾을 수 없습니다 (caseId=" + caseId + ")");
		}
		if (!checkEmbeddingSpec()) {
			// 판례 저장소가 비어 있으면(시드 미적재) "비슷한 판례 없음"과 구분해 RAG를 쓸 수 없는 상태로 알린다 → 호출하는 쪽이 퓨샷으로 대체
			log.warn("rag_precedent가 비어 있어 RAG를 쓰지 않습니다 (R__40 시드 적재 확인, caseId={})", caseId);
			return RagSearchResult.disabled();
		}
		List<String> excludeHashes = caseSourceRepository.findCaseNumbersByCaseId(caseId).stream()
			.flatMap(number -> CaseNumberHash.ofAll(number).stream())
			.distinct()
			.toList();
		if (excludeHashes.isEmpty()) {
			// 사건번호 값은 로그에 남기지 않는다 (case_source는 내부 전용)
			log.warn("원본 판결문 사건번호가 없어 자기 사건 제외 없이 유사 판례를 검색합니다 (caseId={}, 직접 제외 그룹 {}개)", caseId,
				excludeGroups.size());
		}
		float[] query = embeddingClient.embedQuery(legalCase.getOverview());
		RagProperties.Search search = properties.search();
		// 같은 사건 의심으로 빠질 판례가 있어도 top-k를 채우게 조금 더 받는다
		List<RagPrecedentHit> hits = ragPrecedentRepository.search(query, legalCase.getCrimeType().name(),
			excludeHashes, excludeGroups, search.topK() + NEAR_DUPLICATE_SLACK, search.minScore());
		List<RagPrecedentHit> safe = hits.stream()
			.filter(hit -> {
				boolean nearDuplicate = hit.score() >= search.maxScore();
				if (nearDuplicate) {
					log.warn("자기 사건 의심 판례 제외 (caseId={}, precedentId={}, score={})", caseId, hit.id(), hit.score());
				}
				return !nearDuplicate;
			})
			.limit(search.topK())
			.toList();
		return new RagSearchResult(true, safe);
	}

	/** 판례가 있으면 true. 판례 임베딩 모델 · 차원이 검색어와 다르면 멈춘다 */
	private boolean checkEmbeddingSpec() {
		String expected = embeddingClient.model() + ":" + properties.embedding().dimensions();
		List<String> stored = ragPrecedentRepository.findEmbeddingSpecs();
		if (stored.isEmpty()) {
			return false;
		}
		if (stored.stream().anyMatch(spec -> !spec.equals(expected))) {
			throw new EmbeddingException("판례 임베딩(" + String.join(", ", stored) + ")이 검색어 임베딩(" + expected
				+ ")과 다릅니다. 같은 모델 · 차원으로 다시 적재한다");
		}
		return true;
	}
}
