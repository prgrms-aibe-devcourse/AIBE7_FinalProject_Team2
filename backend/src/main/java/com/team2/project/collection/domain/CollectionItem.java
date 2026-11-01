package com.team2.project.collection.domain;

import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 모은 일러스트 · 의상 한 장 (BE-83, V35). 같은 그림이면 같은 줄이고, caseId는 처음 얻은 사건이다.
 * 저장은 이미 가진 항목을 조용히 무시해야 해서 엔티티 save()가 아니라 저장소의 insertIfAbsent(ON CONFLICT DO NOTHING)로 한다.
 * 엔티티는 스키마 검증(ddl-auto: validate)과 조회용이다 (출석 Attendance와 같은 방식)
 */
@Entity
@Table(name = "member_collection_item")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CollectionItem {

	@EmbeddedId
	private CollectionItemId id;

	private Long caseId;

	private Instant acquiredAt;

	private Instant seenAt;	// null이면 "새로 얻음"

	public boolean isNew() {
		return seenAt == null;
	}
}
