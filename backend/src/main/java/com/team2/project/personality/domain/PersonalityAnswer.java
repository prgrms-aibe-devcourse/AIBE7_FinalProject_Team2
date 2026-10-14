package com.team2.project.personality.domain;

/** 성향 테스트 응답 하나 (personality_result.answers 배열의 원소) */
public record PersonalityAnswer(long questionId, long choiceId) {
}
