package com.team2.project.collection.repository;

import com.team2.project.collection.domain.CollectionItem;
import com.team2.project.collection.domain.CollectionItemId;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CollectionItemRepository extends JpaRepository<CollectionItem, CollectionItemId> {

	/**
	 * 항목 하나를 남긴다. 이미 가진 항목이면(다른 사건에서 같은 그림 · 같은 요청 재전송) 아무것도 하지 않아
	 * 처음 얻은 사건 · 시각이 그대로 남는다. 기본 키 충돌을 예외로 받으면 트랜잭션이 롤백 전용이 되므로
	 * DB에서 ON CONFLICT DO NOTHING으로 처리한다 (PostgreSQL, 출석 insertIfAbsent와 같은 방식)
	 * @return 새로 넣은 행 수 (0이면 이미 가짐)
	 */
	@Modifying
	@Query(value = """
		insert into member_collection_item (member_id, kind, item_id, case_id, acquired_at, seen_at)
		values (:memberId, :kind, :itemId, :caseId, :acquiredAt, :seenAt)
		on conflict (member_id, kind, item_id) do nothing
		""", nativeQuery = true)
	int insertIfAbsent(@Param("memberId") long memberId, @Param("kind") String kind,
		@Param("itemId") String itemId, @Param("caseId") long caseId, @Param("acquiredAt") Instant acquiredAt,
		@Param("seenAt") Instant seenAt);

	/** 모은 수 = 일러스트 + 의상 행 수 (도전과제 collected, BE-83) */
	long countByIdMemberId(long memberId);

	/** 종류별 수 (의상 사용자당 상한) */
	long countByIdMemberIdAndIdKind(long memberId, String kind);

	/** 그 사건에서 처음 얻은 종류별 수 (일러스트 사건당 상한) */
	long countByIdMemberIdAndIdKindAndCaseId(long memberId, String kind, Long caseId);

	/** 이미 가진 항목 id (한 요청의 상한 계산에서 이미 가진 것은 세지 않는다) */
	@Query("select c.id.itemId from CollectionItem c "
		+ "where c.id.memberId = :memberId and c.id.kind = :kind and c.id.itemId in :itemIds")
	List<String> findOwnedItemIds(@Param("memberId") long memberId, @Param("kind") String kind,
		@Param("itemIds") List<String> itemIds);

	/** 마이페이지(P-5) 컬렉션: 최근에 얻은 순 */
	List<CollectionItem> findByIdMemberIdOrderByAcquiredAtDescIdKindAscIdItemIdAsc(long memberId);

	/**
	 * 같은 종류의 항목들을 한 번에 "봤음"으로. 이미 봤거나 없는 항목은 건너뛴다 (멱등).
	 * 항목마다 UPDATE를 보내지 않고 종류별로 한 번만 보낸다 (PR #335 리뷰)
	 */
	@Modifying
	@Query("update CollectionItem c set c.seenAt = :now "
		+ "where c.id.memberId = :memberId and c.id.kind = :kind and c.id.itemId in :itemIds "
		+ "and c.seenAt is null")
	int markSeen(@Param("memberId") long memberId, @Param("kind") String kind,
		@Param("itemIds") Collection<String> itemIds, @Param("now") Instant now);

	/** 종류 하나를 전부 "봤음"으로 (컬렉션 탭을 열었을 때) */
	@Modifying
	@Query("update CollectionItem c set c.seenAt = :now "
		+ "where c.id.memberId = :memberId and c.id.kind = :kind and c.seenAt is null")
	int markSeenByKind(@Param("memberId") long memberId, @Param("kind") String kind,
		@Param("now") Instant now);
}
