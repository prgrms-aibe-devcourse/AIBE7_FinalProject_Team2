package com.team2.project.attendance.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 출석 기본 키: 익명 사용자 × 출석 날짜 (하루 한 줄, V22) */
@Embeddable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AttendanceId implements Serializable {

	@Column(name = "anonymous_user_id")
	private UUID anonymousUserId;

	@Column(name = "attended_on")
	private LocalDate attendedOn;

	public AttendanceId(UUID anonymousUserId, LocalDate attendedOn) {
		this.anonymousUserId = anonymousUserId;
		this.attendedOn = attendedOn;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (!(o instanceof AttendanceId other)) {
			return false;
		}
		return Objects.equals(anonymousUserId, other.anonymousUserId) && Objects.equals(attendedOn, other.attendedOn);
	}

	@Override
	public int hashCode() {
		return Objects.hash(anonymousUserId, attendedOn);
	}
}
