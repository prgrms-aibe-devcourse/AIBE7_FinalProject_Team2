package com.team2.project.collection.dto;

import com.team2.project.collection.domain.CollectionKind;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 수집 기록 P-9 POST /collection (BE-83). 사건 체험을 마치고 얻은 일러스트 · 의상을 한 번에 보낸다.
 * backfill: 기존 사용자 백필(마이페이지 진입 때 빠진 사건). true면 그 사건의 첫 완료 시각 · "본 것"으로 남긴다
 */
public record CollectionRecordRequest(
	@NotNull @Positive Long caseId,
	Boolean backfill,
	@NotEmpty @Size(max = MAX_ITEMS) List<@NotNull @Valid Item> items) {

	/** 한 요청에 보낼 수 있는 항목 수 (사건당 일러스트 3장 + 의상 몇 벌이면 충분하다) */
	public static final int MAX_ITEMS = 20;

	/** 항목 id 형식: 소문자 · 숫자로 시작, 소문자 · 숫자 · : _ - 로 64자까지. 서버는 의미 없는 문자열로만 다룬다 */
	public static final String ITEM_ID_PATTERN = "^[a-z0-9][a-z0-9:_-]{0,63}$";

	public CollectionRecordRequest {
		// null 원소는 생성 시 예외 대신 Bean Validation에서 형식 오류로 처리한다 (VerdictRequest와 같다)
		items = items == null ? null : Collections.unmodifiableList(new ArrayList<>(items));
	}

	public boolean isBackfill() {
		return Boolean.TRUE.equals(backfill);
	}

	public record Item(
		@NotBlank @Pattern(regexp = CollectionKind.PATTERN) String kind,
		@NotBlank @Pattern(regexp = ITEM_ID_PATTERN) String itemId) {
	}
}
