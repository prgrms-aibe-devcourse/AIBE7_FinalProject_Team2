-- V46__import_cast_and_case_classification.sql
-- 대본 생성 통합 실행 (BE-129 1차). 사건 등록 한 번에 추출 → 분류 → 대본 → 적재(초안)까지 돌린다(해송님 결정, 2026-10-29).
-- 결정: docs/decisions/BE-129.md

-- 등록 요청을 받을 때 정한 배역 {prosecutor, defense}. 대본은 배역 이름을 대사에 넣어 만들어지므로 대본보다 먼저 정해
-- 파이프라인에 넘긴다(요청의 crime_type으로 정한다 — 판결문에서 읽은 유형이 다르면 BE-138이 멈춘다).
-- 범죄 유형별 균형 계산(CaseCastAssigner)은 사건과 함께 아직 적재되지 않은 등록(case_id IS NULL)의 이 값도 센다 —
-- 그래야 등록 중인 사건이 있을 때 다음 등록이 같은 배역을 받지 않는다. 실패한 등록(FAILED)은 세지 않는다.
-- 비어 있으면 대본 없이 돌린 등록이거나 이 칸이 생기기 전의 등록이다
ALTER TABLE case_import ADD COLUMN cast_roles jsonb;

-- 사건 자동 분류 결과 (BE-123). {mood, issue, victim, thirdParty} — 대본을 다시 만들 때(BE-129 2차) 파이프라인에 그대로
-- 넘기는 값이라 한 칸에 둔다(해송님 제안안). 민감 플래그는 sensitive_flags(V37)가 따로 갖는다(관리자가 A-11로 고친다).
-- 비어 있으면 분류하지 않은 사건이다
ALTER TABLE legal_case ADD COLUMN classification jsonb;
-- 표가 갈렸거나 규칙만 잡아 관리자가 확인해야 하는 축 · 플래그 이름 배열 (예: ["mood", "sensitiveFlags.VIOLENCE"]).
-- 검수 화면이 표시한다. 분류하지 않은 사건은 비어 있다
ALTER TABLE legal_case ADD COLUMN classification_needs_review jsonb;
