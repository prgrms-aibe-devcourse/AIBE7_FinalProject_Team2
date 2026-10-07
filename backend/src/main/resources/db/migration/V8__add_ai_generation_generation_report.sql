-- V8__add_ai_generation_generation_report.sql
-- AI 판결 자동 파이프라인(BE-31)이 사람 검수 없이 비공개(PENDING)로 적재하고, 관리자가 나중에 검수 · 공개한다(후검수).
-- 관리자가 검수할 때 볼 생성 정보를 남긴다: 검증 경고, 사전 학습 점검 결과, 여러 회차 중 이 회차를 고른 방법, 실행 식별자.
-- 실행 식별자(runKey)는 같은 적재 SQL을 두 번 실행해도 행이 두 번 생기지 않게 하는 데도 쓴다.
-- 기존 행(R__30 등 사람이 검수해 APPROVED로 넣은 판결)은 NULL로 둔다.
ALTER TABLE ai_generation ADD COLUMN generation_report jsonb;
