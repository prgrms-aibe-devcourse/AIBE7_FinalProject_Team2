-- V29__create_profile_setting.sql
-- 프로필 선택 설정 (BE-104, FE-77 · API 명세 P-8 v0.51 · ERD v1.37). 익명 사용자당 한 줄.
--
-- shown_card: 공개 성향카드 — 초심(initial) · 재심(retake) · 최근(current) 중 커뮤니티에서 다른 사람에게 보여 줄 카드
-- profile_card_type: 프로필카드 아래 성향 — 같은 세 값 중 하나. 공개 성향카드와는 별개의 값이다
-- bio: 한 줄 소개(앞뒤 공백을 뺀 0 ~ 40자, 줄바꿈 없음). 빈 문자열 = 소개 없음
-- 저장 기록이 없으면 서버가 기본값(current · current · 빈 소개)을 내려 준다.
--
-- 프로필 꾸미기(profile_deco, P-7 전체 저장)와 테이블을 나눈다. 세 값은 화면마다 따로 바뀌어 부분 저장(PATCH)이고,
-- 한쪽 저장이 다른 쪽 값을 덮어쓰지 않게 하려는 것이다(docs/decisions/BE-104.md).
-- 새 테이블만 만든다(BE-60 "main으로 가는 마이그레이션은 추가만").
CREATE TABLE profile_setting (
    anonymous_user_id uuid        NOT NULL,
    shown_card        varchar(10) NOT NULL DEFAULT 'current',
    profile_card_type varchar(10) NOT NULL DEFAULT 'current',
    bio               varchar(40) NOT NULL DEFAULT '',
    updated_at        timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT pk_profile_setting PRIMARY KEY (anonymous_user_id),
    CONSTRAINT fk_profile_setting_anonymous_user FOREIGN KEY (anonymous_user_id) REFERENCES anonymous_user (id),
    CONSTRAINT chk_profile_setting_shown_card CHECK (shown_card IN ('initial', 'retake', 'current')),
    CONSTRAINT chk_profile_setting_profile_card_type CHECK (profile_card_type IN ('initial', 'retake', 'current')),
    CONSTRAINT chk_profile_setting_bio CHECK (bio = btrim(bio) AND bio !~ '[\r\n]')
);
