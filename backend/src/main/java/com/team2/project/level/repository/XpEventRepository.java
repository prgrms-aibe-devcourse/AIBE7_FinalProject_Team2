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
}
