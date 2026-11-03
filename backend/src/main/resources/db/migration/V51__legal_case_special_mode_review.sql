-- V51__legal_case_special_mode_review.sql (BE-165, 확장 단계)
-- 특별 모드(검사 오류를 경고로 낮춰 적재)로 들어온 사건은 관리자가 낮춘 경고를 확인해야 공개할 수 있다.
--   NULL      = 특별 모드가 아니다 (기존 사건 전부)
--   PENDING   = 낮춘 경고가 있고 아직 확인하지 않았다 — 공개 조건 SPECIAL_MODE_UNREVIEWED
--   CONFIRMED = 관리자가 확인했다 (확인자 · 시각을 남긴다)
-- 칼럼 · 제약 추가뿐이라 이전 앱과 호환된다(BE-60)
ALTER TABLE legal_case ADD COLUMN special_mode_review varchar(10);
ALTER TABLE legal_case ADD COLUMN special_mode_reviewed_by_admin_id bigint;
ALTER TABLE legal_case ADD COLUMN special_mode_reviewed_at timestamptz;
ALTER TABLE legal_case ADD CONSTRAINT chk_legal_case_special_mode_review
    CHECK (special_mode_review IS NULL OR special_mode_review IN ('PENDING', 'CONFIRMED'));
ALTER TABLE legal_case ADD CONSTRAINT fk_legal_case_special_mode_admin
    FOREIGN KEY (special_mode_reviewed_by_admin_id) REFERENCES admin_account (id) ON DELETE SET NULL;
COMMENT ON COLUMN legal_case.special_mode_review IS '특별 모드 경고 확인 상태. NULL=해당 없음, PENDING=미확인(공개 불가), CONFIRMED=확인함 (BE-165)';

-- 감사 로그 액션 추가. 앞 마이그레이션(V47)의 목록을 모두 포함해야 한다
ALTER TABLE admin_audit_log DROP CONSTRAINT chk_admin_audit_log_action;
ALTER TABLE admin_audit_log ADD CONSTRAINT chk_admin_audit_log_action CHECK (action IN (
    'LOGIN_SUCCESS', 'LOGIN_FAILURE', 'LOGOUT', 'SOURCE_VIEW',
    'JUDGMENT_APPROVE', 'JUDGMENT_REJECT', 'CASE_UPDATE', 'VALUE_AXIS_CONFIRM', 'CASE_PUBLISH',
    'CASE_PHOTO_UPLOAD', 'CASE_PHOTO_DELETE', 'CASE_PAPER_GENERATE', 'CASE_PAPER_UPDATE',
    'CASE_IMPORT', 'CASE_IMPORT_LOAD',
    'CASE_SCRIPT_APPROVE', 'CASE_SCRIPT_REJECT', 'CASE_SCRIPT_UPDATE',
    'CASE_UNPUBLISH',
    'CASE_SCRIPT_REGENERATE', 'CASE_SCRIPT_REGENERATE_APPLY',
    'CASE_SPECIAL_MODE_CONFIRM'));
