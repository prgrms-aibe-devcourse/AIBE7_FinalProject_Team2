-- V32__member_local_login.sql (확장 단계, BE-113)
-- 아이디 · 비밀번호 회원가입 · 로그인. 회원 행은 그대로 두고 비밀번호 정보만 별도 테이블에 둔다
-- → 카카오 회원 행 · 기존 쿼리는 영향이 없다 (docs/decisions/BE-113.md 결정 1)

-- 로그인 공급자에 LOCAL 추가. 허용값만 넓혀 기존 행(KAKAO)과 충돌하지 않는다
ALTER TABLE member DROP CONSTRAINT ck_member_provider;
ALTER TABLE member ADD CONSTRAINT ck_member_provider CHECK (provider IN ('KAKAO', 'LOCAL'));

-- 표시 이름. LOCAL 회원만 채운다(카카오 회원은 NULL → 화면은 "카카오 회원")
ALTER TABLE member ADD COLUMN nickname varchar(12);

-- LOCAL 회원의 로그인 정보. member.provider_user_id에는 UUID를 넣고, 로그인 아이디는 여기 한 곳에만 둔다
CREATE TABLE member_password (
    member_id     bigint       NOT NULL,
    login_id      varchar(20)  NOT NULL,                  -- 소문자로 바꿔 저장한다
    password_hash varchar(255) NOT NULL,                  -- {argon2} 접두어 포함 (BE-90)
    changed_at    timestamptz  NOT NULL DEFAULT now(),
    CONSTRAINT pk_member_password PRIMARY KEY (member_id),
    CONSTRAINT uq_member_password_login_id UNIQUE (login_id),
    CONSTRAINT fk_member_password_member FOREIGN KEY (member_id) REFERENCES member (id),
    CONSTRAINT ck_member_password_login_id CHECK (login_id ~ '^[a-z0-9_]{4,20}$')
);
