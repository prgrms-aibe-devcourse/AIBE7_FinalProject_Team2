-- V31__add_crime_type_robbery_theft.sql
-- 범죄 유형 추가: 강도(ROBBERY) · 절도(THEFT). 화면 분류명은 둘 다 "재산범죄"(CrimeType 코드 상수, ERD 3-1)
--
-- 범위 (범죄 유형 추가 B안)
-- - ROBBERY: 강도 — 강도치상 · 강도상해 포함
-- - THEFT:   절도 — 특수절도 · 특가법 절도 포함
-- - 상해치사 · 폭행치사는 새 유형을 만들지 않고 기존 INJURY(상해)에 넣는다
-- - 위험운전치사상은 확정 사건이 없어 이번에는 넣지 않는다
--
-- 기존 행은 바꾸지 않는다. CHECK는 허용 값을 넓히기만 하고, 형량 구간은 기존 22행(ID 1 ~ 22) 뒤에 이어 붙인다
-- (BE-60 "main으로 가는 마이그레이션은 추가만").
-- V30은 레벨 · 직업(BE-106 #294)이 쓴다. Flyway out-of-order가 꺼져 있어(기본값) 이 V31이 먼저 적용된 DB는
-- 나중에 V30을 받지 못하고 검증에 실패한다 → #294가 develop에 먼저 머지된 뒤 이 변경을 머지한다.

-- ---------- 1) 범죄 유형 CHECK 4곳 (V1 · V2 · V11 · V24) ----------

ALTER TABLE sentence_range_option DROP CONSTRAINT chk_sentence_range_option_crime_type;
ALTER TABLE sentence_range_option ADD CONSTRAINT chk_sentence_range_option_crime_type
    CHECK (crime_type IN ('MURDER', 'FRAUD', 'INJURY', 'ROBBERY', 'THEFT'));

ALTER TABLE legal_case DROP CONSTRAINT chk_legal_case_crime_type;
ALTER TABLE legal_case ADD CONSTRAINT chk_legal_case_crime_type
    CHECK (crime_type IN ('MURDER', 'FRAUD', 'INJURY', 'ROBBERY', 'THEFT'));

ALTER TABLE rag_precedent DROP CONSTRAINT chk_rag_precedent_crime_type;
ALTER TABLE rag_precedent ADD CONSTRAINT chk_rag_precedent_crime_type
    CHECK (crime_type IN ('MURDER', 'FRAUD', 'INJURY', 'ROBBERY', 'THEFT'));

ALTER TABLE case_import DROP CONSTRAINT chk_case_import_crime_type;
ALTER TABLE case_import ADD CONSTRAINT chk_case_import_crime_type
    CHECK (crime_type IN ('MURDER', 'FRAUD', 'INJURY', 'ROBBERY', 'THEFT'));

-- ---------- 2) 사전 판단 형량 구간 (S-03 선택지, REQ-016 · FR-2-8) ----------
-- V1 공통 7개와 같은 값 · 같은 display_order 규칙을 쓴다.
-- - 강도: 법정형에 벌금이 없어 벌금형을 빼고 무기징역을 더한다 (7개, 강도상해 · 강도치상은 무기 또는 7년 이상)
--         강도살인(사형 또는 무기)은 이번 범위가 아니라 사형 구간은 두지 않는다
-- - 절도: 공통 7개 그대로 (단순 절도는 벌금형이 있다)
-- 강도를 먼저 넣어 ID가 강도 23 ~ 29, 절도 30 ~ 36이 되도록 유형 · 표시 순서대로 넣는다
INSERT INTO sentence_range_option (crime_type, label, kind, min_months, max_months, display_order)
SELECT s.crime_type, s.label, s.kind, s.min_months, s.max_months, s.display_order
FROM (
    -- 공통 구간 7개
    SELECT t.crime_type, t.type_order, o.label, o.kind, o.min_months, o.max_months, o.display_order
    FROM (VALUES ('ROBBERY', 1), ('THEFT', 2)) AS t(crime_type, type_order)
    CROSS JOIN (VALUES
        ('벌금형',                     'FINE',      NULL::int, NULL::int, 1),
        ('징역형 집행유예',            'SUSPENDED', NULL,      NULL,      2),
        ('실형 3년 미만',              'PRISON',    NULL,      36,        3),
        ('실형 3년 이상 ~ 5년 미만',   'PRISON',    36,        60,        4),
        ('실형 5년 이상 ~ 10년 미만',  'PRISON',    60,        120,       5),
        ('실형 10년 이상 ~ 20년 미만', 'PRISON',    120,       240,       6),
        ('실형 20년 이상',             'PRISON',    240,       NULL,      7)
    ) AS o(label, kind, min_months, max_months, display_order)
    -- 강도는 법정형에 벌금이 없어 벌금형 구간을 두지 않는다
    WHERE NOT (t.crime_type = 'ROBBERY' AND o.kind = 'FINE')

    UNION ALL

    -- 강도 전용 구간 1개 (개월 범위 없음)
    SELECT 'ROBBERY', 1, '무기징역', 'LIFE', NULL::int, NULL::int, 8
) AS s
ORDER BY s.type_order, s.display_order;
