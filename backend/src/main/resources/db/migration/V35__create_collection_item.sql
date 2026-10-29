-- V35__create_collection_item.sql (확장 단계, BE-83 · FE-132)
-- 일러스트 · 의상 수집 상태와 도전과제 해금 기록. 획득 조건 판정은 프론트(collectionOf · castOf)가 하고,
-- 서버는 항목 · 획득 시각(서버 시각) · 확인 여부만 저장한다(docs/decisions/BE-83.md).
-- 테이블 추가 · 행 추가뿐이라 BE-60 "main으로 가는 마이그레이션은 추가만" 규칙에 맞는다.

-- 수집 항목. 같은 그림이면 같은 key다(사건별이 아니라 그림별, BE-83 댓글 결정). case_id는 처음 얻은 사건이다.
-- kind는 이후 background · time이 더해질 수 있어 CHECK 제약을 두지 않는다(V23 profile_deco와 같은 이유). 값은 서버 코드가 검사한다.
-- 익명 사용자 FK는 ON DELETE CASCADE: 익명 사용자에 딸린 기록이라 함께 지워져야 하고, 운영에서는 익명 사용자를 지우지 않는다
CREATE TABLE collection_item (
    anonymous_user_id uuid        NOT NULL,
    kind              varchar(20) NOT NULL,              -- illustration · cast
    item_id           varchar(64) NOT NULL,              -- 일러스트 id(case-1-origin) · 의상 {who}:{role}(naero:judge)
    case_id           bigint      NOT NULL,              -- 처음 얻은 사건 (체험 완료 검증 기준)
    acquired_at       timestamptz NOT NULL DEFAULT now(), -- 서버 시각 (백필이면 그 사건의 첫 완료 시각)
    seen_at           timestamptz,                       -- null이면 "새로 얻음"
    CONSTRAINT pk_collection_item PRIMARY KEY (anonymous_user_id, kind, item_id),
    CONSTRAINT fk_collection_item_anonymous_user FOREIGN KEY (anonymous_user_id)
        REFERENCES anonymous_user (id) ON DELETE CASCADE,
    CONSTRAINT fk_collection_item_legal_case FOREIGN KEY (case_id)
        REFERENCES legal_case (id) ON DELETE CASCADE
);

-- 사건당 상한 검사용 (사용자 단독 조회는 기본 키 첫 컬럼으로 충분)
CREATE INDEX idx_collection_item_user_case ON collection_item (anonymous_user_id, case_id, kind);

-- 도전과제 해금 기록 (BE-96 "새로 열림" 후속). 판정은 지금처럼 계산하고, 새로 CLEARED가 된 것만 남긴다.
-- 기록이 있으면 표가 바뀌어 다시 미달이 되어도 CLEARED를 유지한다(열렸던 도전과제는 다시 잠기지 않는다, BE-96)
CREATE TABLE achievement_unlock (
    anonymous_user_id uuid        NOT NULL,
    achievement_id    varchar(40) NOT NULL,              -- ProfileDecoCatalog.ACHIEVEMENTS의 id (first-case, collect-5 …)
    unlocked_at       timestamptz NOT NULL DEFAULT now(),
    seen_at           timestamptz,                       -- null이면 "새로 열림"
    CONSTRAINT pk_achievement_unlock PRIMARY KEY (anonymous_user_id, achievement_id),
    CONSTRAINT fk_achievement_unlock_anonymous_user FOREIGN KEY (anonymous_user_id)
        REFERENCES anonymous_user (id) ON DELETE CASCADE
);

-- 배포 시점에 이미 달성한 도전과제를 "본 것"으로 남긴다(1회성 백필). 남기지 않으면 첫 조회 때 전부 "새로 열림"으로 뜬다.
-- 목표값은 이 마이그레이션을 만든 때(2026-10-29)의 ProfileDecoCatalog 값 그대로다(ProfileDecoCatalogTest가 같은지 확인한다).
-- 수집 도전과제(collect-*)는 배포 시점 수집 수가 0이라 넣지 않는다
WITH stats AS (
    SELECT au.id AS anonymous_user_id,
           (SELECT count(DISTINCT e.case_id) FROM experience e
             WHERE e.anonymous_user_id = au.id AND e.status = 'COMPLETED')  AS completed,
           EXISTS (SELECT 1 FROM personality_result p
                    WHERE p.anonymous_user_id = au.id AND p.is_current)      AS has_type,
           (SELECT count(*) FROM attendance a WHERE a.anonymous_user_id = au.id) AS attendance_days
      FROM anonymous_user au
)
INSERT INTO achievement_unlock (anonymous_user_id, achievement_id, unlocked_at, seen_at)
SELECT s.anonymous_user_id, x.achievement_id, now(), now()
  FROM stats s
 CROSS JOIN LATERAL (VALUES
        ('first-case',  s.completed >= 1),
        ('cases-3',     s.completed >= 3),
        ('personality', s.has_type),
        ('attend-3',    s.attendance_days >= 3),
        ('attend-7',    s.attendance_days >= 7),
        ('attend-30',   s.attendance_days >= 30)
       ) AS x(achievement_id, cleared)
 WHERE x.cleared;
