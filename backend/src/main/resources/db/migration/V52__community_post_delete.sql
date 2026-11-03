-- V52__community_post_delete.sql (BE-167, 확장 단계)
-- 커뮤니티 글 삭제(본인 · 관리자). 글을 지우면 그 글로 받은 경험치를 회수하되 레벨은 내리지 않는다.
--   LEVEL_FLOOR = 회수 뒤 XP가 지금 레벨의 시작 XP보다 모자랄 때 그 차이만큼 채우는 보정 지급(ref_key = 지운 글 ID)
--   회수한 지급 행은 지우지 않고 금액만 0으로 둔다(하루 상한 · 중복 지급 방지 유지) → amount 하한을 > 0에서 >= 0으로 넓힌다
-- 제약 목록을 넓히기만 한다(BE-60). 앞 마이그레이션의 값을 모두 포함해야 한다
ALTER TABLE member_xp_event DROP CONSTRAINT chk_member_xp_event_amount;
ALTER TABLE member_xp_event ADD CONSTRAINT chk_member_xp_event_amount CHECK (amount >= 0);

ALTER TABLE member_xp_event DROP CONSTRAINT chk_member_xp_event_source;
ALTER TABLE member_xp_event ADD CONSTRAINT chk_member_xp_event_source CHECK (source IN (
    'POST', 'SHARE_POST', 'COMMENT', 'LIKE_GIVEN', 'LIKE_RECEIVED', 'SNS_SHARE', 'LEVEL_FLOOR'));

-- 감사 로그: 관리자 글 삭제 (V51의 목록 + COMMUNITY_POST_DELETE / 대상 COMMUNITY_POST)
ALTER TABLE admin_audit_log DROP CONSTRAINT chk_admin_audit_log_action;
ALTER TABLE admin_audit_log ADD CONSTRAINT chk_admin_audit_log_action CHECK (action IN (
    'LOGIN_SUCCESS', 'LOGIN_FAILURE', 'LOGOUT', 'SOURCE_VIEW',
    'JUDGMENT_APPROVE', 'JUDGMENT_REJECT', 'CASE_UPDATE', 'VALUE_AXIS_CONFIRM', 'CASE_PUBLISH',
    'CASE_PHOTO_UPLOAD', 'CASE_PHOTO_DELETE', 'CASE_PAPER_GENERATE', 'CASE_PAPER_UPDATE',
    'CASE_IMPORT', 'CASE_IMPORT_LOAD',
    'CASE_SCRIPT_APPROVE', 'CASE_SCRIPT_REJECT', 'CASE_SCRIPT_UPDATE',
    'CASE_UNPUBLISH',
    'CASE_SCRIPT_REGENERATE', 'CASE_SCRIPT_REGENERATE_APPLY',
    'CASE_SPECIAL_MODE_CONFIRM',
    'COMMUNITY_POST_DELETE'));
ALTER TABLE admin_audit_log DROP CONSTRAINT chk_admin_audit_log_target_type;
ALTER TABLE admin_audit_log ADD CONSTRAINT chk_admin_audit_log_target_type CHECK (
    target_type IS NULL OR target_type IN ('CASE', 'JUDGMENT', 'FACTOR', 'CASE_SOURCE', 'COMMUNITY_POST'));
