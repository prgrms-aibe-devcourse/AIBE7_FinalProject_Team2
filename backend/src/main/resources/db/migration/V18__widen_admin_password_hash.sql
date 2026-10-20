-- V18__widen_admin_password_hash.sql
-- 비밀번호 해시를 Argon2id로 전환(BE-90, WBS 7.4.4). {argon2}... 인코딩은 BCrypt보다 길어 varchar(100)을 넘는다.
-- admin_account는 트래픽이 적은 테이블이지만, 장기 트랜잭션이 잠겨 있으면 무기한 대기하지 않도록 잠금 대기 시간을 둔다.
-- SET LOCAL로 둬 이 마이그레이션 트랜잭션에만 적용하고, 커넥션 풀로 재사용되는 세션에는 남지 않게 한다.
SET LOCAL lock_timeout = '5s';
ALTER TABLE admin_account ALTER COLUMN password_hash TYPE varchar(255);
