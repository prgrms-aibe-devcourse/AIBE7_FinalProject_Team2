package com.team2.project.collection.dto;

import java.util.List;

/** 마이페이지 P-5 collection (BE-83). collected = items 수 (일러스트 + 의상), items는 최근에 얻은 순 */
public record CollectionView(int collected, List<CollectionItemView> items) {

	public static final CollectionView EMPTY = new CollectionView(0, List.of());

	public static CollectionView of(List<CollectionItemView> items) {
		return new CollectionView(items.size(), items);
	}
}
