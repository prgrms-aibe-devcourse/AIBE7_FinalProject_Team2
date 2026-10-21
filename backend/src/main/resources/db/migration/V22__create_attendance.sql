-- V22__create_attendance.sql
-- 출석 기록 (BE-97). 익명 사용자가 사이트에 들어온 날을 하루 한 줄씩 남긴다. 줄 수가 누적 출석 일수다.
-- 날짜(attended_on)는 서버가 설정 시간대 · 하루 시작 시각 기준으로 계산해 넣는다(attendance.zone · day-start-hour,
-- docs/decisions/BE-97.md). 날짜를 남겨 두어 연속 출석 계산 · 로그인 회원 이관(회원에 연결된 익명 ID들의 날짜를
-- 중복 없이 센다)에 그대로 쓴다. V20은 비워 두기로 한 번호라 쓰지 않는다(BE-92).
CREATE TABLE attendance (
    anonymous_user_id uuid        NOT NULL,
    attended_on       date        NOT NULL,
    created_at        timestamptz NOT NULL DEFAULT now(),
    -- 하루 한 줄. 같은 날 다시 출석하면 INSERT ... ON CONFLICT DO NOTHING으로 무시한다
    CONSTRAINT pk_attendance PRIMARY KEY (anonymous_user_id, attended_on),
    CONSTRAINT fk_attendance_anonymous_user FOREIGN KEY (anonymous_user_id) REFERENCES anonymous_user (id)
);

-- anonymous_user_id는 기본 키의 첫 컬럼이라 별도 인덱스 생략
