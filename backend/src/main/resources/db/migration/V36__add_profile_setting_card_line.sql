-- V36__add_profile_setting_card_line.sql
-- 프로필카드 아래 줄 설정 (BE-114, FE-116 · API 명세 P-8 · P-5 v0.65 · ERD v1.46).
--
-- card_line: 프로필카드 아래에 보일 줄 — type(성향) · signature(한 줄 소개) · job(직업) 중 하나. 기본 'type'
-- card_level: 레벨을 그 줄 윗줄에 붙일지. 기본 false
-- 기존 행은 기본값으로 채워진다. 칸만 더한다(BE-60 "main으로 가는 마이그레이션은 추가만").
ALTER TABLE profile_setting
    ADD COLUMN card_line  varchar(10) NOT NULL DEFAULT 'type',
    ADD COLUMN card_level boolean     NOT NULL DEFAULT false,
    ADD CONSTRAINT chk_profile_setting_card_line CHECK (card_line IN ('type', 'signature', 'job'));
