-- V50__legal_case_unlock_order.sql (BE-146)
-- 사건 순차 해금의 순서. 작은 값이 먼저 열린다. 비어 있으면 값이 있는 사건 뒤에 사건 id 순으로 놓인다.
-- 운영자가 SQL로 정한다(관리자 화면 없음, docs/decisions/BE-146.md). 칼럼 · 제약 추가뿐이라 이전 앱과 호환된다(BE-60)
ALTER TABLE legal_case ADD COLUMN unlock_order integer;
ALTER TABLE legal_case ADD CONSTRAINT chk_legal_case_unlock_order CHECK (unlock_order IS NULL OR unlock_order >= 1);
COMMENT ON COLUMN legal_case.unlock_order IS '순차 해금 순서(1부터, 작을수록 먼저). NULL이면 맨 뒤 · 사건 id 순 (BE-146)';
