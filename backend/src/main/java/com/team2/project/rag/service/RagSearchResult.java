package com.team2.project.rag.service;

import com.team2.project.rag.domain.RagPrecedentHit;
import java.util.List;

/**
 * 유사 판례 검색 결과
 *
 * @param enabled false면 RAG가 꺼져 있다(API 키 없음). 이때 precedents는 비어 있고, 호출하는 쪽은 퓨샷으로 대체한다 (8-1)
 */
public record RagSearchResult(boolean enabled, List<RagPrecedentHit> precedents) {

	public static RagSearchResult disabled() {
		return new RagSearchResult(false, List.of());
	}
}
