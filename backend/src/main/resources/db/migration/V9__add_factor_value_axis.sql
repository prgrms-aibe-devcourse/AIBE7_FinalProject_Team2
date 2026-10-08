-- V9__add_factor_value_axis.sql
-- 판결 체험에서 사용자가 고르는 판단 요소에 가치관 축(value_axis)을 붙인다 (BE-47). 사용자 성향 매칭의 기초 데이터다.
--   APOLOGY_SINCERITY  ① 사과와 진정성 — 반성, 자수, 수사 협조, 사후 정황
--   FAULT_STANDARD     ② 잘잘못의 기준 — 범행 동기, 수단 · 방법, 계획성, 결과의 중대성
--   PRINCIPLE_RELATION ③ 원칙과 관계   — 피해 회복, 합의 · 처벌불원, 피해자 과실
--   ORDER_OPPORTUNITY  ④ 질서와 기회   — 전과, 연령, 가족 · 부양, 직업, 사회적 유대
-- NULL = 어느 축에도 맞지 않는 요소(성향 계산에서 제외). 자동 분류(추출기)가 붙인 값이 이상하면 관리자가 고칠 수 있게 NULL을 허용하고 값만 바꾸면 된다.
-- 사전 판단(OVERVIEW)과 형량 선택은 성향 계산에 쓰지 않는다. 그 제외는 계산하는 쪽에서 하며, 이 컬럼은 요소마다 값을 둔다.
-- 기존 행은 NULL로 두고, 사건별 값은 비공개 시드(private-seed)가 제목 + display_order로 채운다.
ALTER TABLE factor ADD COLUMN value_axis varchar(20);

ALTER TABLE factor ADD CONSTRAINT chk_factor_value_axis
    CHECK (value_axis IS NULL OR value_axis IN ('APOLOGY_SINCERITY', 'FAULT_STANDARD', 'PRINCIPLE_RELATION', 'ORDER_OPPORTUNITY'));
