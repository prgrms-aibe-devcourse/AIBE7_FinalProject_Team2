-- V26__case_incident_photo_role.sql
-- 사건 발단 · 결론 사진 역할 칸, 1단계 (BE-78 2차 검토, BE-101).
--
-- 지금까지 case_incident_photo는 display_order(업로드 개수로 채번)로만 순서를 뒀는데, 삭제 · 교체
-- API를 붙이면 채번이 기존 행과 겹친다. role(ORIGIN · CONCLUSION) 칸으로 "사건당 역할별 1장"을
-- 보장하고, 같은 역할 재업로드는 교체로 처리한다(결정: docs/decisions/BE-78.md "확정 사항").
--
-- 3단계로 나눈 이유: ddl-auto: validate라 배포 실패 시 바로 앞 단계 앱이 지금 스키마에서 떠야 한다
-- (BE-60 "main으로 가는 마이그레이션은 추가만"). display_order를 한 번에 지우면 이전 앱(이 배포
-- 이전 앱)은 여전히 display_order를 쓰므로 뜨지 못한다. 그래서
--   1단계(이 파일): role 추가(NULL 허용) + CHECK + UNIQUE(case_id, role) + 백필. display_order는
--     남기고 새 코드가 ORIGIN = 0 · CONCLUSION = 1로 계속 채운다. PostgreSQL UNIQUE는 NULL을
--     중복으로 보지 않으므로 역할 없는 행(옛 3번째 이상 사진)이 여럿이어도 걸리지 않는다.
--   2단계(다음 배포): display_order의 NOT NULL만 푼다. 코드는 display_order를 더 이상 읽지도
--     쓰지도 않고 role만 채운다. 되돌아가도 1단계 앱이 display_order를 계속 채우므로 뜬다.
--   3단계(그다음 배포): display_order와 그 유니크 제약을 지우고 role을 NOT NULL로 바꾼다.
-- display_order가 2 이상인 행(사건당 3장 이상 올라간 경우)은 role을 NULL로 둔다. 자리가 둘뿐이라
-- 어느 역할에 넣어도 유니크와 겹친다. 3단계 전에 역할 없는 행이 남아 있는지 사람이 확인하고
-- 정리한다 — 이 마이그레이션에 DELETE는 넣지 않는다(BE-60 "데이터 손상 위험이 있는 변경은 사람이
-- 판단").

ALTER TABLE case_incident_photo ADD COLUMN role varchar(20);

ALTER TABLE case_incident_photo ADD CONSTRAINT chk_case_incident_photo_role
    CHECK (role IS NULL OR role IN ('ORIGIN', 'CONCLUSION'));

UPDATE case_incident_photo SET role = CASE display_order
    WHEN 0 THEN 'ORIGIN'
    WHEN 1 THEN 'CONCLUSION'
    ELSE NULL
END;

ALTER TABLE case_incident_photo ADD CONSTRAINT uk_case_incident_photo_case_role UNIQUE (case_id, role);

-- 관리자 감사 로그(V14 · V19 · V21 · V24)에 사진 삭제 행동을 추가한다(A-13-9).
ALTER TABLE admin_audit_log DROP CONSTRAINT chk_admin_audit_log_action;
ALTER TABLE admin_audit_log ADD CONSTRAINT chk_admin_audit_log_action CHECK (action IN (
    'LOGIN_SUCCESS', 'LOGIN_FAILURE', 'LOGOUT', 'SOURCE_VIEW',
    'JUDGMENT_APPROVE', 'JUDGMENT_REJECT', 'CASE_UPDATE', 'VALUE_AXIS_CONFIRM', 'CASE_PUBLISH',
    'CASE_PHOTO_UPLOAD', 'CASE_PAPER_GENERATE', 'CASE_PAPER_UPDATE', 'CASE_IMPORT', 'CASE_PHOTO_DELETE'));
