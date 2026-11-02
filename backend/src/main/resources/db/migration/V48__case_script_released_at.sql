-- V48__case_script_released_at.sql
-- 대본 상태 scriptStatus (BE-126 2차-2). 대본이 처음 사용자에게 나간 시각을 남겨 "준비 중"과 "점검 중"을 가른다.
--   READY       = 다섯 단위 모두 승인 (지금 체험할 수 있다)
--   MAINTENANCE = 한 번 나간 뒤(released_at 있음) 블록 수정 · 반려 · 재생성 반영으로 승인이 풀렸다 — API 18은 503
--   PREPARING   = 아직 한 번도 나가지 않았다 — API 18은 404
--
-- "나갔다" = 사건이 공개(PUBLISHED)된 상태에서 대본이 공개 조건(다섯 단위 모두 승인)을 채웠다. 초안 사건에서 승인만 한 대본은
-- 사용자가 본 적이 없어 나간 것이 아니다 — 그 대본을 고친 뒤 사건을 공개하면 "준비 중"이 맞다. 한 번 채우면 지우지 않는다
-- (공개 취소 · 통거절 뒤에도 "한 번 나간 대본"이다).
--
-- 결정: docs/decisions/BE-126.md "2차-2"

ALTER TABLE case_script ADD COLUMN released_at timestamptz;

-- 이미 나가 있는 대본(공개 사건 + 다섯 단위 모두 승인)은 마지막 승인 시각으로 채운다. 운영은 대본 0행이라(2026-10-30 확인)
-- 개발 · 로컬 DB용이다. 공개 시각이 승인보다 늦으면 공개 시각이 나간 시각이다
UPDATE case_script s
SET released_at = GREATEST(c.published_at, (SELECT max(b.reviewed_at) FROM case_script_block b WHERE b.script_id = s.id))
FROM legal_case c
WHERE c.id = s.case_id
  AND c.status = 'PUBLISHED'
  AND (SELECT count(*) FROM case_script_block b WHERE b.script_id = s.id AND b.review_status = 'APPROVED') = 5;
