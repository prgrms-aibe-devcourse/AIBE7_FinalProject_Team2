package com.team2.project.collection.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.team2.project.collection.domain.CollectionItem;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;

/** 모은 항목 한 장 (P-9 응답 added · 마이페이지 P-5 collection.items, BE-83). isNew: 아직 보지 않았으면 true("새로 얻음") */
public record CollectionItemView(String kind, String itemId, Long caseId, OffsetDateTime acquiredAt,
	@JsonProperty("isNew") boolean isNew) {

	private static final ZoneId KST = ZoneId.of("Asia/Seoul");

	public static CollectionItemView from(CollectionItem item) {
		return new CollectionItemView(item.getId().getKind(), item.getId().getItemId(), item.getCaseId(),
			kst(item.getAcquiredAt()), item.isNew());
	}

	public static CollectionItemView of(String kind, String itemId, long caseId, Instant acquiredAt, boolean seen) {
		return new CollectionItemView(kind, itemId, caseId, kst(acquiredAt), !seen);
	}

	private static OffsetDateTime kst(Instant instant) {
		return instant.atZone(KST).toOffsetDateTime();
	}
}
