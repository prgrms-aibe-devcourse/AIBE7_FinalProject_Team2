-- V28__personality_result_baseline_retake.sql
-- 성향 결과에 응시 시점 완료 사건 수 · 성향카드 재심(다시 정하기) 표시 (BE-104, FE-77 · API 명세 v0.50 · ERD v1.36).
--
-- completed_experience_count: 응시 시점에 완료한 사건 수(완료한 회차가 하나라도 있는 사건 수, P-5 도전과제
--   completed와 같은 셈). "체험 n건 뒤에 다시 한 테스트"를 남긴다. 이 마이그레이션 전 행은 알 수 없어 NULL이다.
-- is_baseline_retake: 성향카드 재심(P-2 baseline: true)으로 낸 결과. 처음 성향(가장 오래된 행)을 덮어쓰지 않고
--   한 건 더 남긴다. 사용자당 1건은 부분 유니크 인덱스로 DB가 보장한다(동시 제출도 한 건만 들어간다).
--
-- 추가만 한다(BE-60 "main으로 가는 마이그레이션은 추가만"): 두 칸 모두 NULL 허용이거나 기본값이 있어,
-- 이 배포 이전 앱이 지금 스키마에서 그대로 떠서 응시를 저장해도(재심 아님, 건수 NULL) 깨지지 않는다.

ALTER TABLE personality_result ADD COLUMN completed_experience_count integer;
ALTER TABLE personality_result ADD CONSTRAINT chk_personality_result_completed_experience_count
    CHECK (completed_experience_count IS NULL OR completed_experience_count >= 0);

ALTER TABLE personality_result ADD COLUMN is_baseline_retake boolean NOT NULL DEFAULT false;

-- 익명 사용자마다 재심 결과는 최대 한 건.
CREATE UNIQUE INDEX ux_personality_result_baseline_retake_user
    ON personality_result (anonymous_user_id) WHERE is_baseline_retake;
