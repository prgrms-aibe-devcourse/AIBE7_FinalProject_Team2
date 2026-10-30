package com.team2.project.collection.domain;

import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Arrays;
import java.util.Optional;

/**
 * 수집 항목 종류 (BE-83). DB에는 소문자 이름(key)으로 저장한다.
 * 이후 공용 배경 · 시간대(background · time)를 더할 수 있게 DB에 CHECK 제약을 두지 않고 여기서만 관리한다(V35)
 */
public enum CollectionKind {

	ILLUSTRATION("illustration"),	// 투명 배경 일러스트. 사건당 3장이 콘텐츠 규칙
	CAST("cast");					// 만난 모습(역할 의상) {who}:{role}. 사건과 무관, 모을 수 있는 것은 역할 의상 그림 목록(BE-136 CollectibleCatalog)

	/** 요청 검증용 정규식 (DTO @Pattern). 종류를 더하면 함께 고친다 */
	public static final String PATTERN = "^(illustration|cast)$";

	private final String key;

	CollectionKind(String key) {
		this.key = key;
	}

	@JsonValue
	public String key() {
		return key;
	}

	public static Optional<CollectionKind> fromKey(String key) {
		return Arrays.stream(values()).filter(kind -> kind.key.equals(key)).findFirst();
	}
}
