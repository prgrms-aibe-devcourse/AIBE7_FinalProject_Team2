-- V39__member_nickname_unique.sql (확장 단계, BE-139 · API 명세 M-7 v0.69 · ERD v1.49)
-- 닉네임 중복 금지. 대소문자만 다른 닉네임도 같은 것으로 본다(아이디와 같은 규칙). 닉네임이 없는 회원(NULL)은 겹쳐도 된다
--
-- 이미 겹치는 닉네임(BE-113 가입은 중복을 막지 않았다)은 먼저 가입한 회원(작은 id)만 남기고 나머지는 NULL로 비운다.
-- 비운 회원은 닉네임을 다시 정할 수 있고, 그 전까지 커뮤니티에는 '방청객 {회원 ID}'로 보인다(BE-115).
-- 새 인덱스만 만들고 칸 · 행은 지우지 않는다(BE-60 "main으로 가는 마이그레이션은 추가만"). 닉네임은 표시용이라 비워도 로그인에 영향이 없다
UPDATE member m
SET nickname = NULL
WHERE m.nickname IS NOT NULL
  AND EXISTS (SELECT 1 FROM member o WHERE lower(o.nickname) = lower(m.nickname) AND o.id < m.id);

CREATE UNIQUE INDEX uq_member_nickname ON member (lower(nickname)) WHERE nickname IS NOT NULL;
