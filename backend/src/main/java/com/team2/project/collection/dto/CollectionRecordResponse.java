package com.team2.project.collection.dto;

import java.util.List;

/**
 * P-9 응답 (BE-83).
 * added: 이번 요청으로 새로 저장된 항목만(이미 가진 것은 빠진다), collected: 모은 수(일러스트 + 의상),
 * newlyUnlocked: 이번 요청으로 새로 해금이 기록된 도전과제 id
 */
public record CollectionRecordResponse(List<CollectionItemView> added, int collected, List<String> newlyUnlocked) {
}
