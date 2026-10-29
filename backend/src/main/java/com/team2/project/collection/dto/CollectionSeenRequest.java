package com.team2.project.collection.dto;

import com.team2.project.collection.domain.CollectionKind;
import com.team2.project.collection.dto.CollectionRecordRequest.Item;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 컬렉션 "봤음" P-10 POST /collection/seen (BE-83). items(항목 지정)와 kind(종류 전부) 중 하나만 보낸다
 */
public record CollectionSeenRequest(
	@Size(max = CollectionRecordRequest.MAX_ITEMS * 5) List<@NotNull @Valid Item> items,
	@Pattern(regexp = CollectionKind.PATTERN) String kind) {

	public CollectionSeenRequest {
		items = items == null ? null : Collections.unmodifiableList(new ArrayList<>(items));
	}

	@AssertTrue(message = "items와 kind 중 하나만 보내야 합니다.")
	public boolean isOneTarget() {
		boolean hasItems = items != null && !items.isEmpty();
		boolean hasKind = kind != null;
		return hasItems != hasKind;
	}
}
