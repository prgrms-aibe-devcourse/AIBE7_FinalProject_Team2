package com.team2.project.collection.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 수집 항목 기본 키: 회원 × 종류 × 항목 id (같은 그림은 한 줄, V41) */
@Embeddable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CollectionItemId implements Serializable {

	@Column(name = "member_id")
	private Long memberId;

	@Column(name = "kind")
	private String kind;

	@Column(name = "item_id")
	private String itemId;

	public CollectionItemId(Long memberId, String kind, String itemId) {
		this.memberId = memberId;
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
		return Objects.equals(memberId, other.memberId) && Objects.equals(kind, other.kind)
			&& Objects.equals(itemId, other.itemId);
	}

	@Override
	public int hashCode() {
		return Objects.hash(memberId, kind, itemId);
	}
}
