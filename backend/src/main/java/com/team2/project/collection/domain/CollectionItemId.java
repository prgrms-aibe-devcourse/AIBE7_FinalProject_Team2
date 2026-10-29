package com.team2.project.collection.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 수집 항목 기본 키: 익명 사용자 × 종류 × 항목 id (같은 그림은 한 줄, V35) */
@Embeddable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CollectionItemId implements Serializable {

	@Column(name = "anonymous_user_id")
	private UUID anonymousUserId;

	@Column(name = "kind")
	private String kind;

	@Column(name = "item_id")
	private String itemId;

	public CollectionItemId(UUID anonymousUserId, String kind, String itemId) {
		this.anonymousUserId = anonymousUserId;
		this.kind = kind;
		this.itemId = itemId;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (!(o instanceof CollectionItemId other)) {
			return false;
		}
		return Objects.equals(anonymousUserId, other.anonymousUserId) && Objects.equals(kind, other.kind)
			&& Objects.equals(itemId, other.itemId);
	}

	@Override
	public int hashCode() {
		return Objects.hash(anonymousUserId, kind, itemId);
	}
}
