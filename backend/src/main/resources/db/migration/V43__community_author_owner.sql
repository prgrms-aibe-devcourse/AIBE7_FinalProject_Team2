-- V43__community_author_owner.sql (확장 단계, BE-140 · ERD v1.50)
-- 커뮤니티 글 · 댓글의 작성자를 "회원 또는 익명 ID"로 넓힌다. 지금은 쓰기가 로그인 전용이라 회원만 쓰지만,
-- 반응을 보고 비로그인 작성을 열 수 있게 자리를 남긴다(BE-140 결정 5). 명함은 회원이면 회원 기준, 아니면 익명 ID 기준으로 그린다(작성자 Owner).
--
-- BE-60 "main으로 가는 마이그레이션은 추가만": 지금 앱은 두 칸을 모두 채우므로 NOT NULL만 풀고 "적어도 하나" 제약을 더한다.
-- 회원 기준 전환(BE-140 PR3 · PR4)이 끝나 회원 글에 익명 ID를 남기지 않게 되면 "정확히 하나"로 좁히는 일은 다음 배포로 나눈다
ALTER TABLE community_post
    ALTER COLUMN member_id DROP NOT NULL,
    ALTER COLUMN anonymous_user_id DROP NOT NULL,
    ADD CONSTRAINT chk_community_post_author CHECK (num_nonnulls(member_id, anonymous_user_id) >= 1);

ALTER TABLE community_comment
    ALTER COLUMN member_id DROP NOT NULL,
    ALTER COLUMN anonymous_user_id DROP NOT NULL,
    ADD CONSTRAINT chk_community_comment_author CHECK (num_nonnulls(member_id, anonymous_user_id) >= 1);
