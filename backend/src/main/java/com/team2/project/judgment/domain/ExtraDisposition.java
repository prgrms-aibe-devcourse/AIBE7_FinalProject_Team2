package com.team2.project.judgment.domain;

/**
 * 부가 처분 1건 (jsonb 배열 원소). 예: { "type": "COMMUNITY_SERVICE", "value": "120시간" }
 */
public record ExtraDisposition(ExtraDispositionType type, String value) {
}
