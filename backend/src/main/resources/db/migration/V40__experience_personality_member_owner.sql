-- V40__experience_personality_member_owner.sql (확장 단계, BE-140 · ERD v1.50)
-- 체험 · 성향 테스트 결과의 주인을 "익명 ID 또는 회원 ID 중 정확히 하나"로 바꾼다(이중 소유).
-- 로그인 없이도 체험 · 성향 테스트를 할 수 있으므로 두 테이블만 손님(익명 ID)과 회원이 함께 쓴다. 나머지 사용자 데이터는 회원 키 새 테이블(V41)로 간다.
--
-- BE-60 "main으로 가는 마이그레이션은 추가만": 지금 앱은 늘 익명 ID만 채우고 회원 ID는 쓰지 않으므로, NOT NULL을 풀고 칸 · 제약 · 인덱스를 더해도 그대로 돈다.
-- 기존 유니크(익명 ID, 사건, 회차)는 그대로 둔다 — NULL끼리는 겹치지 않아 회원 행을 막지 않는다. 회원 쪽 유니크는 부분 인덱스로 따로 둔다.

-- 체험 ----------------------------------------------------------------------------------------------
-- experience.member_id는 V3부터 있던 "체험 시작 시 로그인 상태였다면 회원 ID" 자리인데 어떤 코드도 채우지 않았다.
-- 뜻을 "체험의 주인 회원"으로 바꾼다. 혹시 두 칸이 다 찬 행이 있으면 회원 ID를 비워 지금 주인(익명 ID)을 지킨다
UPDATE experience SET member_id = NULL WHERE member_id IS NOT NULL AND anonymous_user_id IS NOT NULL;

ALTER TABLE experience
    ALTER COLUMN anonymous_user_id DROP NOT NULL,
    ADD CONSTRAINT fk_experience_member FOREIGN KEY (member_id) REFERENCES member (id),
    ADD CONSTRAINT chk_experience_owner CHECK (num_nonnulls(anonymous_user_id, member_id) = 1);

-- 같은 회원이 같은 사건을 같은 회차로 두 번 시작할 수 없다(익명 쪽 uk_experience_user_case_attempt와 같은 규칙)
CREATE UNIQUE INDEX uk_experience_member_case_attempt ON experience (member_id, case_id, attempt_no) WHERE member_id IS NOT NULL;

-- 성향 테스트 결과 ------------------------------------------------------------------------------------
ALTER TABLE personality_result
    ALTER COLUMN anonymous_user_id DROP NOT NULL,
    ADD COLUMN member_id bigint,
    ADD CONSTRAINT fk_personality_result_member FOREIGN KEY (member_id) REFERENCES member (id),
    ADD CONSTRAINT chk_personality_result_owner CHECK (num_nonnulls(anonymous_user_id, member_id) = 1);

-- 익명 쪽과 같은 규칙: 회원마다 현재 결과 하나 · 재심 결과 하나, 최근 결과 조회
CREATE UNIQUE INDEX ux_personality_result_current_member ON personality_result (member_id) WHERE is_current AND member_id IS NOT NULL;
CREATE UNIQUE INDEX ux_personality_result_baseline_retake_member ON personality_result (member_id) WHERE is_baseline_retake AND member_id IS NOT NULL;
CREATE INDEX ix_personality_result_member_created ON personality_result (member_id, created_at DESC) WHERE member_id IS NOT NULL;
