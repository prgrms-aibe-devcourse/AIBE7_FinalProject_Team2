package com.team2.project.attendance.domain;

import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

/**
 * 출석 기록 (BE-97, V22). 익명 사용자가 사이트에 들어온 날마다 한 줄. 줄 수가 누적 출석 일수다.
 * 저장은 같은 날 중복을 조용히 무시해야 해서 엔티티 save()가 아니라 저장소의 insertIfAbsent(ON CONFLICT DO NOTHING)로 한다.
 * 엔티티는 스키마 검증(ddl-auto: validate)과 조회용이다.
 */
@Entity
@Table(name = "attendance")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Attendance {

	@EmbeddedId
	private AttendanceId id;

	@CreationTimestamp
	private Instant createdAt;
}
