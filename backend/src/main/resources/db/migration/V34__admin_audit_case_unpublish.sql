-- V34__admin_audit_case_unpublish.sql (확장 단계, BE-118)
-- 관리자 사건 공개 취소(A-13-14)의 감사 로그 행동 CASE_UNPUBLISH를 추가한다. 허용값만 넓혀 기존 행과 충돌하지 않는다.
-- 이 제약은 PostgreSQL에서 고쳐 쓸 수 없어 매번 지우고 전체 목록으로 다시 만든다. 그래서
-- **앞 마이그레이션이 더한 행동을 빠뜨리면 조용히 지워진다** — 아래 목록은 V32(BE-108)까지 포함한다.
ALTER TABLE admin_audit_log DROP CONSTRAINT chk_admin_audit_log_action;
ALTER TABLE admin_audit_log ADD CONSTRAINT chk_admin_audit_log_action CHECK (action IN (
    'LOGIN_SUCCESS', 'LOGIN_FAILURE', 'LOGOUT', 'SOURCE_VIEW',
    'JUDGMENT_APPROVE', 'JUDGMENT_REJECT', 'CASE_UPDATE', 'VALUE_AXIS_CONFIRM', 'CASE_PUBLISH',
    'CASE_PHOTO_UPLOAD', 'CASE_PHOTO_DELETE', 'CASE_PAPER_GENERATE', 'CASE_PAPER_UPDATE',
    'CASE_IMPORT', 'CASE_IMPORT_LOAD',
    'CASE_SCRIPT_APPROVE', 'CASE_SCRIPT_REJECT', 'CASE_SCRIPT_UPDATE',
    'CASE_UNPUBLISH'));
