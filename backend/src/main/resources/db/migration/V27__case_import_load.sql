-- V27__case_import_load.sql
-- 사건 등록 적재 (BE-93 2차). 파이프라인 결과를 사건으로 만드는 단계가 생겨 감사 로그 행동을 추가한다.
-- 표는 늘리지 않는다 — 적재 결과는 기존 사건 표(legal_case · judgment …)에 그대로 들어가고,
-- 어느 등록에서 나온 사건인지는 case_import.case_id(V24)가 이미 가리킨다.

-- 관리자 감사 로그(V14 · V19 · V21 · V24 · V26)에 적재 행동을 추가한다.
-- 이 제약은 PostgreSQL에서 고쳐 쓸 수 없어 매번 지우고 전체 목록으로 다시 만든다. 그래서
-- **앞 마이그레이션이 더한 행동을 빠뜨리면 조용히 지워진다** — 아래 목록은 V26(BE-101)이 더한
-- CASE_PHOTO_DELETE까지 포함한다.
ALTER TABLE admin_audit_log DROP CONSTRAINT chk_admin_audit_log_action;
ALTER TABLE admin_audit_log ADD CONSTRAINT chk_admin_audit_log_action CHECK (action IN (
    'LOGIN_SUCCESS', 'LOGIN_FAILURE', 'LOGOUT', 'SOURCE_VIEW',
    'JUDGMENT_APPROVE', 'JUDGMENT_REJECT', 'CASE_UPDATE', 'VALUE_AXIS_CONFIRM', 'CASE_PUBLISH',
    'CASE_PHOTO_UPLOAD', 'CASE_PHOTO_DELETE', 'CASE_PAPER_GENERATE', 'CASE_PAPER_UPDATE',
    'CASE_IMPORT', 'CASE_IMPORT_LOAD'));
