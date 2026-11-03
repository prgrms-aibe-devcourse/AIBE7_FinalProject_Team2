package com.team2.project.level.repository;

import com.team2.project.level.domain.XpEvent;
import java.time.LocalDate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface XpEventRepository extends JpaRepository<XpEvent, Long> {

	/**
	 * 지급을 남긴다. 같은 (사용자, 수급처, 대상)이 이미 있으면(다시 누름 · 동시 요청) 아무것도 하지 않는다.
	 * 기본 키 충돌을 예외로 받으면 트랜잭션이 롤백 전용이 되므로 ON CONFLICT DO NOTHING으로 처리한다 (PostgreSQL)
	 * @return 새로 넣은 행 수 (0이면 이미 받음)
	 */
	@Modifying
	@Query(value = """
		insert into member_xp_event (member_id, source, ref_key, group_key, amount, awarded_on)
		values (:userId, :source, :refKey, :groupKey, :amount, :awardedOn)
		on conflict (member_id, source, ref_key) do nothing
		""", nativeQuery = true)
	int insertIfAbsent(@Param("userId") long userId, @Param("source") String source, @Param("refKey") String refKey,
		@Param("groupKey") String groupKey, @Param("amount") int amount, @Param("awardedOn") LocalDate awardedOn);

	/** 원장 합계. 기록이 없으면 0 */
	@Query(value = "select coalesce(sum(amount), 0) from member_xp_event where member_id = :userId", nativeQuery = true)
	int sumAmount(@Param("userId") long userId);

	/** 그날 받은 횟수 (일일 상한) */
	@Query(value = "select count(*) from member_xp_event where member_id = :userId and source = :source and awarded_on = :day",
		nativeQuery = true)
	int countOnDay(@Param("userId") long userId, @Param("source") String source, @Param("day") LocalDate day);

	/** 같은 묶음에서 받은 횟수 (묶음 상한) */
	@Query(value = "select count(*) from member_xp_event where member_id = :userId and source = :source and group_key = :groupKey",
		nativeQuery = true)
	int countInGroup(@Param("userId") long userId, @Param("source") String source, @Param("groupKey") String groupKey);

	/**
	 * 지급 한 건을 거둔다(글 삭제 회수): 행은 남기고 금액만 0으로 만든다. 행이 남아 하루 상한 횟수 · 같은 대상 중복 지급 방지는 그대로라,
	 * 쓰고 지우기를 반복해도 상한이 다시 비지 않는다(BE-167 리뷰). @return 바꾼 행 수
	 */
	@Modifying
	@Query(value = "update member_xp_event set amount = 0 where member_id = :userId and source = :source and ref_key = :refKey", nativeQuery = true)
	int zeroOne(@Param("userId") long userId, @Param("source") String source, @Param("refKey") String refKey);

	/** 같은 묶음의 지급을 모두 거둔다(글이 받은 좋아요 XP). @return 지운 행 수 */
	@Modifying
	@Query(value = "delete from member_xp_event where member_id = :userId and source = :source and group_key = :groupKey", nativeQuery = true)
	int deleteGroup(@Param("userId") long userId, @Param("source") String source, @Param("groupKey") String groupKey);
}
