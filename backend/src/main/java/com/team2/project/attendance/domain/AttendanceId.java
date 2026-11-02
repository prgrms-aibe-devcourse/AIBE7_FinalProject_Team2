package com.team2.project.attendance.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.time.LocalDate;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 출석 기본 키: 회원 × 출석 날짜 (하루 한 줄, V41) */
@Embeddable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AttendanceId implements Serializable {

	@Column(name = "member_id")
	private Long memberId;

	@Column(name = "attended_on")
	private LocalDate attendedOn;

	public AttendanceId(Long memberId, LocalDate attendedOn) {
		this.memberId = memberId;
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
		return Objects.equals(memberId, other.memberId) && Objects.equals(attendedOn, other.attendedOn);
	}

	@Override
	public int hashCode() {
		return Objects.hash(memberId, attendedOn);
	}
}
