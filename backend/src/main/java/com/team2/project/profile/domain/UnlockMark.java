package com.team2.project.profile.domain;

import java.time.Instant;

/** 도전과제 해금 기록 한 줄의 판정용 값 (BE-83). seen이 false면 "새로 열림" */
public record UnlockMark(Instant unlockedAt, boolean seen) {
}
