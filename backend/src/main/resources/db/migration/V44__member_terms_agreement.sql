-- V44__member_terms_agreement.sql (확장 단계, BE-133 · API 명세 M-5 v0.74 · ERD v1.51)
-- 아이디 회원가입(M-5)의 약관 동의 기록. 가입할 때 동의한 시각과 그때의 약관 버전을 남긴다
--
-- 칸만 추가하고 NULL을 허용한다(BE-60 "main으로 가는 마이그레이션은 추가만"). 배포 중 구버전 코드가 회원을 만들어도 두 칸이 함께 NULL이라 CHECK를 통과한다.
-- 기존 회원 · 카카오 회원은 NULL로 남는다 — "동의 기록이 없다"는 뜻이다(docs/decisions/BE-133.md)
-- 한 ALTER로 묶어 member 테이블 잠금 · CHECK 검사를 한 번만 한다(기존 행은 모두 NULL이라 검사는 바로 통과한다)
ALTER TABLE member
    ADD COLUMN terms_agreed_at timestamptz,
    ADD COLUMN terms_version   varchar(20),
    -- 동의 시각과 약관 버전은 함께 있거나 함께 없다(한쪽만 있으면 어떤 약관에 언제 동의했는지 알 수 없다)
    ADD CONSTRAINT ck_member_terms_agreement CHECK ((terms_agreed_at IS NULL) = (terms_version IS NULL));
