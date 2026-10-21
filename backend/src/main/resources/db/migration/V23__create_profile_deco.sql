-- V23__create_profile_deco.sql
-- 프로필 꾸미기 설정 (BE-96, FE-68). 익명 사용자당 한 줄: 대표 이미지 · 카드 테두리 · 배경 · 카드 책갈피
-- (모양 · 모양별 색 · 좌우 아이콘) · 책갈피 캐릭터(캐릭터 · 얼굴 · 얼굴 종류 · 리본 색).
-- 값 목록 · 잠금은 서버 코드(ProfileDecoCatalog)가 검사한다. 도전과제 · 보상 표가 아직 임시안이라 CHECK 제약을 두지 않는다
-- (표가 바뀔 때마다 마이그레이션이 필요해지지 않게). 저장 기록이 없으면 서버가 기본값을 내려 준다(docs/decisions/BE-96.md).
CREATE TABLE profile_deco (
    anonymous_user_id   uuid         NOT NULL,
    portrait            varchar(200) NOT NULL,  -- 대표 이미지 경로 (/images/…/x.webp, 형식은 서버가 검사)
    frame               varchar(30)  NOT NULL,  -- 카드 테두리
    background          varchar(30)  NOT NULL,  -- 카드 배경
    sash_style          varchar(30)  NOT NULL,  -- 카드 책갈피 모양 (none · short · long)
    sash_color_short    varchar(30)  NOT NULL,  -- 짧은 책갈피의 색 (모양마다 따로 기억한다)
    sash_color_long     varchar(30)  NOT NULL,  -- 긴 띠의 색
    sash_icon_left      varchar(30)  NOT NULL,  -- 짧은 책갈피 왼쪽 아이콘
    sash_icon_right     varchar(30)  NOT NULL,  -- 짧은 책갈피 오른쪽 아이콘
    bookmark_character  varchar(30)  NOT NULL,  -- 책갈피 캐릭터 (naero · nambul · law-*)
    bookmark_face       varchar(30)  NOT NULL,  -- 책갈피 얼굴 표정
    bookmark_face_style varchar(30)  NOT NULL,  -- 얼굴 종류 (generated · cropped)
    bookmark_color      varchar(30)  NOT NULL,  -- 책갈피 리본 색
    updated_at          timestamptz  NOT NULL DEFAULT now(),  -- 로그인 이관 때 가장 최근 꾸미기를 고르는 기준
    CONSTRAINT pk_profile_deco PRIMARY KEY (anonymous_user_id),
    CONSTRAINT fk_profile_deco_anonymous_user FOREIGN KEY (anonymous_user_id) REFERENCES anonymous_user (id)
);
