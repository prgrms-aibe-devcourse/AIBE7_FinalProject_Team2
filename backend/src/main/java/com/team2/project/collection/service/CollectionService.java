package com.team2.project.collection.service;

import com.team2.project.collection.domain.CollectionKind;
import com.team2.project.collection.dto.CollectionItemView;
import com.team2.project.collection.dto.CollectionRecordRequest;
import com.team2.project.collection.dto.CollectionRecordRequest.Item;
import com.team2.project.collection.dto.CollectionRecordResponse;
import com.team2.project.collection.dto.CollectionSeenRequest;
import com.team2.project.collection.dto.CollectionView;
import com.team2.project.collection.repository.CollectionItemRepository;
import com.team2.project.common.exception.BusinessException;
import com.team2.project.common.exception.ErrorCode;
import com.team2.project.common.exception.FieldErrorDetail;
import com.team2.project.experience.domain.Experience;
import com.team2.project.experience.repository.AnonymousUserRepository;
import com.team2.project.experience.repository.ExperienceRepository;
import com.team2.project.experience.service.AnonymousUserService;
import com.team2.project.legalcase.repository.LegalCaseRepository;
import com.team2.project.profile.domain.ProfileDecoCatalog;
import com.team2.project.profile.domain.ProfileStats;
import com.team2.project.profile.domain.UnlockMark;
import com.team2.project.profile.service.AchievementUnlockRecorder;
import com.team2.project.profile.service.ProfileStatsReader;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 일러스트 · 의상 수집 상태 (BE-83, docs/decisions/BE-83.md).
 * 획득 조건 판정은 프론트(collectionOf · castOf)가 하고, 서버는 항목 · 획득 시각(서버 시각) · 확인 여부만 저장한다.
 * 서버는 대본을 몰라 가짜 항목 id를 막지 못하므로 최소 검증만 한다: 사건 체험 완료 · 형식 · 종류별 상한
 */
@Service
@RequiredArgsConstructor
public class CollectionService {

	private final CollectionItemRepository collectionItemRepository;
	private final ExperienceRepository experienceRepository;
	private final LegalCaseRepository legalCaseRepository;
	private final AnonymousUserService anonymousUserService;
	private final AnonymousUserRepository anonymousUserRepository;
	private final ProfileStatsReader statsReader;
	private final AchievementUnlockRecorder unlockRecorder;
	private final Clock clock;

	/**
	 * P-9 수집 기록. 멱등: 이미 가진 항목(다른 사건에서 얻은 같은 그림 포함)은 건너뛰고 처음 사건 · 시각을 유지한다.
	 * 하나라도 검증에 걸리면 아무것도 저장하지 않는다.
	 * 쿠키가 없거나 그 사건을 완료하지 않았으면 거절한다(익명 ID를 새로 발급하지 않는다 — 체험을 마친 사용자만 부른다)
	 */
	@Transactional
	public CollectionRecordResponse record(Optional<UUID> anonymousId, CollectionRecordRequest request) {
		UUID userId = anonymousUserService.findAndTouch(anonymousId)
			.orElseThrow(() -> new BusinessException(ErrorCode.EXPERIENCE_NOT_FOUND))
			.getId();
		// 같은 사용자의 동시 기록을 차례로 처리한다(상한 검사 후 둘 다 넣어 상한을 넘기지 않게)
		anonymousUserRepository.findByIdForUpdate(userId);
		long caseId = request.caseId();
		requireCompleted(userId, caseId);

		// 같은 요청 안의 중복은 하나로 본다. 종류별로 나눠 아직 없는 것만 남긴다
		Map<CollectionKind, List<String>> requested = groupByKind(request.items());
		Map<CollectionKind, List<String>> fresh = new LinkedHashMap<>();
		requested.forEach((kind, itemIds) -> {
			Set<String> owned = new HashSet<>(collectionItemRepository.findOwnedItemIds(userId, kind.key(), itemIds));
			fresh.put(kind, itemIds.stream().filter(itemId -> !owned.contains(itemId)).toList());
		});
		checkLimits(userId, caseId, fresh);

		Instant now = clock.instant();
		boolean backfill = request.isBackfill();
		// 백필은 그 사건을 처음 완료한 시각 · "본 것"으로 남겨 "새로 얻음"이 한꺼번에 뜨지 않게 한다
		Instant acquiredAt = backfill ? firstCompletedAtOr(userId, caseId, now) : now;
		Instant seenAt = backfill ? acquiredAt : null;
		List<CollectionItemView> added = new ArrayList<>();
		fresh.forEach((kind, itemIds) -> itemIds.forEach(itemId -> {
			if (collectionItemRepository.insertIfAbsent(userId, kind.key(), itemId, caseId, acquiredAt, seenAt) == 1) {
				added.add(CollectionItemView.of(kind.key(), itemId, caseId, acquiredAt, backfill));
			}
		}));

		// 수집 도전과제(collect-5 · collect-20)가 열렸으면 같은 트랜잭션에서 해금을 남긴다(백필이면 "본 것")
		ProfileStats stats = statsReader.read(userId);
		Map<String, UnlockMark> unlocked = unlockRecorder.record(userId, stats, backfill);
		return new CollectionRecordResponse(added, stats.collected(), List.copyOf(unlocked.keySet()));
	}

	/** P-10 컬렉션 "봤음". 쿠키가 없거나 모르는 사용자 · 이미 본 항목 · 없는 항목은 조용히 건너뛴다(멱등) */
	@Transactional
	public void markSeen(Optional<UUID> anonymousId, CollectionSeenRequest request) {
		anonymousUserService.findAndTouch(anonymousId).ifPresent(user -> {
			Instant now = clock.instant();
			if (request.kind() != null) {
				collectionItemRepository.markSeenByKind(user.getId(), request.kind(), now);
				return;
			}
			// 종류별로 묶어 종류마다 UPDATE 한 번 (일러스트 · 의상이라 많아야 2번, PR #335 리뷰)
			request.items().stream()
				.collect(Collectors.groupingBy(Item::kind, LinkedHashMap::new,
					Collectors.mapping(Item::itemId, Collectors.toSet())))
				.forEach((kind, itemIds) -> collectionItemRepository.markSeen(user.getId(), kind, itemIds, now));
		});
	}

	/** 마이페이지 P-5 collection (최근에 얻은 순) */
	@Transactional(readOnly = true)
	public CollectionView view(UUID anonymousUserId) {
		return CollectionView.of(
			collectionItemRepository.findByIdAnonymousUserIdOrderByAcquiredAtDescIdKindAscIdItemIdAsc(anonymousUserId)
				.stream().map(CollectionItemView::from).toList());
	}

	/** 사건이 있고, 이 사용자가 완료한 회차가 하나라도 있어야 한다(다시 하기 중이어도 통과, existsCompleted BE-106) */
	private void requireCompleted(UUID userId, long caseId) {
		if (!legalCaseRepository.existsById(caseId)) {
			throw new BusinessException(ErrorCode.CASE_NOT_FOUND);
		}
		if (experienceRepository.existsCompleted(userId, caseId)) {
			return;
		}
		Experience latest = experienceRepository.findLatest(userId, caseId)
			.orElseThrow(() -> new BusinessException(ErrorCode.EXPERIENCE_NOT_FOUND));
		// 체험은 했지만 아직 끝내지 않음 → 지금 단계를 알려 준다 (API 명세 1-4 currentStatus)
		throw new BusinessException(ErrorCode.INVALID_STATE, "사건 체험을 마친 뒤에 모을 수 있습니다.",
			latest.getStatus().name(), null);
	}

	/**
	 * 종류별 상한: 일러스트는 이 사건에서 처음 얻은 수, 의상은 사용자 전체 수. 이미 가진 것은 세지 않는다.
	 * 넘으면 409 COLLECTION_LIMIT_EXCEEDED, details에 넘친 종류를 담는다
	 */
	private void checkLimits(UUID userId, long caseId, Map<CollectionKind, List<String>> fresh) {
		List<FieldErrorDetail> exceeded = new ArrayList<>();
		List<String> illustrations = fresh.getOrDefault(CollectionKind.ILLUSTRATION, List.of());
		if (!illustrations.isEmpty() && collectionItemRepository.countByIdAnonymousUserIdAndIdKindAndCaseId(userId,
			CollectionKind.ILLUSTRATION.key(), caseId) + illustrations.size()
			> ProfileDecoCatalog.COLLECTION_ILLUSTRATIONS_PER_CASE) {
			exceeded.add(new FieldErrorDetail("items.kind", CollectionKind.ILLUSTRATION.key()));
		}
		List<String> casts = fresh.getOrDefault(CollectionKind.CAST, List.of());
		if (!casts.isEmpty() && collectionItemRepository.countByIdAnonymousUserIdAndIdKind(userId,
			CollectionKind.CAST.key()) + casts.size() > ProfileDecoCatalog.COLLECTION_CASTS_PER_USER) {
			exceeded.add(new FieldErrorDetail("items.kind", CollectionKind.CAST.key()));
		}
		if (!exceeded.isEmpty()) {
			throw new BusinessException(ErrorCode.COLLECTION_LIMIT_EXCEEDED,
				ErrorCode.COLLECTION_LIMIT_EXCEEDED.getMessage(), null, exceeded);
		}
	}

	private Instant firstCompletedAtOr(UUID userId, long caseId, Instant fallback) {
		Instant first = experienceRepository.findFirstCompletedAt(userId, caseId);
		return first != null ? first : fallback;	// 완료 시각이 비어 있는 옛 기록은 지금 시각
	}

	private static Map<CollectionKind, List<String>> groupByKind(List<Item> items) {
		Map<CollectionKind, List<String>> grouped = new LinkedHashMap<>();
		for (Item item : items) {
			// 형식은 DTO가 이미 검사했다(@Pattern). 알 수 없는 종류는 여기까지 오지 않는다
			CollectionKind kind = CollectionKind.fromKey(item.kind())
				.orElseThrow(() -> BusinessException.ofField(ErrorCode.VALIDATION_ERROR, "items.kind"));
			List<String> itemIds = grouped.computeIfAbsent(kind, ignored -> new ArrayList<>());
			if (!itemIds.contains(item.itemId())) {
				itemIds.add(item.itemId());
			}
		}
		return grouped;
	}
}
