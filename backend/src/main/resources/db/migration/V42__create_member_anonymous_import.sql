-- V42__create_member_anonymous_import.sql (확장 단계, BE-140 · API 명세 M-9 ~ M-11 · ERD v1.50)
-- 첫 로그인 가져오기 결정. 회원마다 한 번만 정한다 — 기록이 있으면 다시 묻지 않는다.
-- IMPORTED: 그 익명 ID의 체험을 회원 것으로 옮겼다(M-10). DISCARDED: 옮기지 않고 그 익명 ID를 버렸다(M-11) —
-- 익명 기록은 지우지 않고 그대로 두며(되돌릴 여지 · 나중에 정리 배치로 지운다), 브라우저의 익명 ID 쿠키만 지운다.
-- 테이블 추가뿐이라 BE-60 "main으로 가는 마이그레이션은 추가만" 규칙에 맞는다
CREATE TABLE member_anonymous_import (
    member_id         bigint      NOT NULL,
    anonymous_user_id uuid        NOT NULL,           -- 결정 때 이 브라우저 쿠키의 익명 ID
    decision          varchar(10) NOT NULL,
    experience_count  int         NOT NULL DEFAULT 0, -- 옮긴 체험 수(IMPORTED). DISCARDED면 0
    decided_at        timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT pk_member_anonymous_import PRIMARY KEY (member_id),
    CONSTRAINT fk_member_anonymous_import_member FOREIGN KEY (member_id) REFERENCES member (id) ON DELETE CASCADE,
    CONSTRAINT fk_member_anonymous_import_anonymous_user FOREIGN KEY (anonymous_user_id) REFERENCES anonymous_user (id),
    CONSTRAINT chk_member_anonymous_import_decision CHECK (decision IN ('IMPORTED', 'DISCARDED')),
    CONSTRAINT chk_member_anonymous_import_count CHECK (experience_count >= 0 AND (decision = 'IMPORTED' OR experience_count = 0))
);
-- FK 칸 인덱스(ERD 7장 규칙). 같은 익명 ID로 결정한 회원 찾기에도 쓴다
CREATE INDEX idx_member_anonymous_import_anonymous_user ON member_anonymous_import (anonymous_user_id);
