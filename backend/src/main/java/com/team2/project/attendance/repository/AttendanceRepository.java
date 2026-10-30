package com.team2.project.attendance.repository;

import com.team2.project.attendance.domain.Attendance;
import com.team2.project.attendance.domain.AttendanceId;
import java.time.LocalDate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AttendanceRepository extends JpaRepository<Attendance, AttendanceId> {

	/**
	 * 그날 출석을 남긴다. 이미 있으면(같은 날 다시 접속 · 동시 요청) 아무것도 하지 않는다.
	 * 기본 키 충돌을 예외로 받으면 트랜잭션이 롤백 전용이 되므로 DB에서 ON CONFLICT DO NOTHING으로 처리한다 (PostgreSQL)
	 * @return 새로 넣은 행 수 (0이면 이미 출석)
	 */
	@Modifying
	@Query(value = """
		insert into member_attendance (member_id, attended_on)
		values (:memberId, :attendedOn)
		on conflict (member_id, attended_on) do nothing
		""", nativeQuery = true)
	int insertIfAbsent(@Param("memberId") long memberId, @Param("attendedOn") LocalDate attendedOn);

	/** 누적 출석 일수 */
	long countByIdMemberId(long memberId);
}
