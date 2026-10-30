package com.team2.project.illustration.dto;

import java.util.Map;

/**
 * 일러스트 총 개수 (BE-142).
 *
 * @param total  전체 장수
 * @param byKind 종류(이미지 폴더)별 장수. 키는 {@code cases}, {@code characters/scene}처럼 {@code images/} 아래 폴더 경로
 */
public record IllustrationSummary(int total, Map<String, Integer> byKind) {
}
