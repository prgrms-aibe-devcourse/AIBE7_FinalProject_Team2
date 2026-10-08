-- V10__add_factor_value_axis_review.sql
-- 판단 요소 가치관 축(value_axis)의 후검수 상태와 자동 분류 투표 기록을 남긴다 (BE-48).
-- 축은 AI(파이프라인 투표, BE-49) 또는 사람 초안(비공개 시드)이 기본값을 정하고, 관리자가 후검수로 확정한다(4개 축 또는 NULL).
--   AUTO      = 기본값 (아직 관리자가 확정하지 않음). 시드 · 적재 SQL이 값을 다시 맞출 수 있다
--   CONFIRMED = 관리자가 확정함. NULL로 확정한 것도 포함한다. 시드 · 적재 SQL은 이 행을 건드리지 않는다
-- 값만으로는 "관리자가 일부러 NULL로 둔 요소"와 "아직 채우지 않은 요소"를 구분할 수 없어서 상태를 따로 둔다.
-- 기존 행은 모두 AUTO로 둔다(아직 관리자 확정 기능이 없다).
ALTER TABLE factor ADD COLUMN value_axis_status varchar(20) NOT NULL DEFAULT 'AUTO';

ALTER TABLE factor ADD CONSTRAINT chk_factor_value_axis_status
    CHECK (value_axis_status IN ('AUTO', 'CONFIRMED'));

-- 자동 분류 투표 기록. 관리자 화면에서 기본값의 근거로 보여 준다. 사람 초안 · 투표 없이 정한 값은 NULL
--   예: {"runs": 5, "counts": {"FAULT_STANDARD": 3, "PRINCIPLE_RELATION": 2}, "needsReview": false}
--   counts: 표를 받은 축만 담는다. "어느 축에도 맞지 않음(NULL)" 표는 "NONE" 키로 센다
--   needsReview: 최다표가 요청 횟수의 과반이 아니면(동률 · 유효 응답 부족 포함) true (관리자 확인 필요)
ALTER TABLE factor ADD COLUMN value_axis_votes jsonb;
