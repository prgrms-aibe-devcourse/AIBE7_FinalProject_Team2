package com.team2.project.pipeline;

import java.util.Map;

/**
 * 대본 블록 재생성 작업 요청 (BE-129 2차, 파이프라인 서비스 `POST /jobs`의 `regenerate` 모양).
 *
 * <p>판결문 원문은 없다. 백엔드가 DB 값으로 모은 입력이다 — 형식은 `tools/ai-judgment/service/README.md` "대본 블록 재생성".
 *
 * @param regenerate {@code units} · {@code cascade} · {@code case} · {@code judgment} · {@code classification}(민감 플래그를
 *                   합친 값) · {@code cast} · {@code blocks} · {@code paper}
 * @param stages     {@code {"script": {"model": ...}}}만. 배역 · 분류 · 블록은 서비스가 {@code regenerate}에서만 받는다
 */
public record PipelineRegenerationRequest(Map<String, Object> regenerate, Map<String, Object> stages) {
}
