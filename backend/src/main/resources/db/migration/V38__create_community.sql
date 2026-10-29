-- V38__create_community.sql
-- 커뮤니티 글 · 댓글 · 좋아요 (BE-115, FE-47 · FE-88 · FE-89 · FE-116 · API 명세 12장 v0.68 · ERD v1.48).
--
-- 쓰기(글 · 댓글 · 좋아요)는 로그인한 회원만 한다. 글쓴이는 member_id로 정하고, 명함(레벨 · 직업 · 꾸미기 · 카드 아래 줄)과
-- 경험치는 지금 구조대로 익명 ID 기준이라 쓴 요청의 익명 ID(anonymous_user_id)를 함께 남긴다(docs/decisions/BE-115.md).
-- 판결 공유 글의 판결(verdict)은 붙인 시점의 값을 그대로 남긴다(뒤에 다시 체험해도 글은 바뀌지 않는다).
-- 좋아요 수 · 댓글 수는 목록 정렬(좋아요순 · 댓글순)과 쪽 나누기에 쓰려고 글에 함께 센다. 좋아요 · 댓글을 넣고 뺄 때 같은 트랜잭션에서 더하고 뺀다.
-- 새 테이블만 만든다(BE-60 "main으로 가는 마이그레이션은 추가만").
CREATE TABLE community_post (
    id                bigserial     NOT NULL,
    member_id         bigint        NOT NULL,
    anonymous_user_id uuid          NOT NULL,
    category          varchar(10)   NOT NULL,
    case_id           bigint,
    title             varchar(60)   NOT NULL,
    body              varchar(2000) NOT NULL,
    verdict           jsonb,
    like_count        integer       NOT NULL DEFAULT 0,
    comment_count     integer       NOT NULL DEFAULT 0,
    created_at        timestamptz   NOT NULL DEFAULT now(),
    CONSTRAINT pk_community_post PRIMARY KEY (id),
    CONSTRAINT fk_community_post_member FOREIGN KEY (member_id) REFERENCES member (id),
    CONSTRAINT fk_community_post_anonymous_user FOREIGN KEY (anonymous_user_id) REFERENCES anonymous_user (id),
    CONSTRAINT fk_community_post_case FOREIGN KEY (case_id) REFERENCES legal_case (id),
    CONSTRAINT chk_community_post_category CHECK (category IN ('VERDICT', 'QUESTION', 'FREE')),
    CONSTRAINT chk_community_post_verdict_case CHECK (verdict IS NULL OR case_id IS NOT NULL),
    CONSTRAINT chk_community_post_counts CHECK (like_count >= 0 AND comment_count >= 0)
);

CREATE INDEX idx_community_post_created ON community_post (created_at DESC, id DESC);
CREATE INDEX idx_community_post_case ON community_post (case_id, created_at DESC);
CREATE INDEX idx_community_post_member ON community_post (member_id, created_at DESC);

CREATE TABLE community_comment (
    id                bigserial    NOT NULL,
    post_id           bigint       NOT NULL,
    member_id         bigint       NOT NULL,
    anonymous_user_id uuid         NOT NULL,
    body              varchar(500) NOT NULL,
    created_at        timestamptz  NOT NULL DEFAULT now(),
    CONSTRAINT pk_community_comment PRIMARY KEY (id),
    CONSTRAINT fk_community_comment_post FOREIGN KEY (post_id) REFERENCES community_post (id) ON DELETE CASCADE,
    CONSTRAINT fk_community_comment_member FOREIGN KEY (member_id) REFERENCES member (id),
    CONSTRAINT fk_community_comment_anonymous_user FOREIGN KEY (anonymous_user_id) REFERENCES anonymous_user (id)
);

CREATE INDEX idx_community_comment_post ON community_comment (post_id, id);
CREATE INDEX idx_community_comment_created ON community_comment (created_at DESC);
CREATE INDEX idx_community_comment_member ON community_comment (member_id, created_at DESC);

-- 좋아요: 회원 한 명이 글 하나에 하나(누를 때마다 켜고 끈다)
CREATE TABLE community_like (
    post_id    bigint      NOT NULL,
    member_id  bigint      NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT pk_community_like PRIMARY KEY (post_id, member_id),
    CONSTRAINT fk_community_like_post FOREIGN KEY (post_id) REFERENCES community_post (id) ON DELETE CASCADE,
    CONSTRAINT fk_community_like_member FOREIGN KEY (member_id) REFERENCES member (id)
);
