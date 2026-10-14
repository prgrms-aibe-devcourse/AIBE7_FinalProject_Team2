-- V16__add_judgment_review.sql
-- 판결 후보 검수 기록 (BE-41 · BE-42, WBS 7.4.7). API 명세 8-2 · 8-3 #4 · #5.
-- AI · 재판부 후보를 같은 방식으로 검수하도록 judgment에 검수 칸을 둔다.
-- - 재판부 후보(COURT)는 지금까지 검수 상태 · 반려 사유를 둘 곳이 없었다(is_published만 있음).
-- - review_status가 NULL이면 "검수 기록 없음"이다. 화면에는 공개 중이면 승인, 아니면 대기로 보여 준다
--   (파이프라인 · 시드 SQL은 이 칸을 채우지 않아도 된다).
-- - 검수자는 관리자 계정 id로 남긴다. ai_generation.reviewed_by(varchar 50)는 이메일이 들어가지 않을 수 있어(8-3 #5)
--   기존 값은 그대로 두고 새 검수부터 이 칸을 쓴다. 계정이 지워져도 기록은 남긴다(ON DELETE SET NULL, 감사 로그와 같음).
-- - ai_generation.review_status는 기존 도구가 읽으므로 승인 · 반려 때 함께 맞춘다(BE-42).

ALTER TABLE judgment
    ADD COLUMN review_status        varchar(20),
    ADD COLUMN reviewed_by_admin_id bigint,
    ADD COLUMN reviewed_at          timestamptz,
    ADD COLUMN review_note          varchar(500);

ALTER TABLE judgment
    ADD CONSTRAINT fk_judgment_reviewed_by_admin FOREIGN KEY (reviewed_by_admin_id)
        REFERENCES admin_account (id) ON DELETE SET NULL,
    -- 저장하는 값은 승인 · 반려뿐이다. 대기는 NULL
    ADD CONSTRAINT chk_judgment_review_status CHECK (review_status IS NULL OR review_status IN ('APPROVED', 'REJECTED')),
    -- 사용자 판단은 검수 대상이 아니다
    ADD CONSTRAINT chk_judgment_review_user CHECK (
        subject_type <> 'USER'
        OR (review_status IS NULL AND reviewed_by_admin_id IS NULL AND reviewed_at IS NULL AND review_note IS NULL)),
    -- 반려한 후보는 공개할 수 없고, 사유는 반려에만 둔다
    ADD CONSTRAINT chk_judgment_review_rejected CHECK (NOT (review_status = 'REJECTED' AND is_published)),
    ADD CONSTRAINT chk_judgment_review_note CHECK (review_note IS NULL OR review_status = 'REJECTED');

-- 기존 데이터: AI는 ai_generation의 검수 결과를, 공개 중인 AI · 재판부 판결은 승인으로 옮긴다
UPDATE judgment j
SET review_status = g.review_status,
    reviewed_at = g.reviewed_at
FROM ai_generation g
WHERE g.judgment_id = j.id
  AND g.review_status IN ('APPROVED', 'REJECTED')
  AND NOT (g.review_status = 'REJECTED' AND j.is_published);

UPDATE judgment
SET review_status = 'APPROVED'
WHERE subject_type IN ('AI', 'COURT') AND is_published AND review_status IS NULL;

-- 검수 목록(A-5)의 "대기 후보 수": 검수 기록이 없고 공개되지 않은 AI · 재판부 후보
CREATE INDEX ix_judgment_review_pending ON judgment (case_id, subject_type)
    WHERE subject_type IN ('AI', 'COURT') AND review_status IS NULL AND NOT is_published;
