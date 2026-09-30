-- V6__seed_sample_case.sql
-- 개발용 임시 시드: 가상 살인 사건 1건 (ERD v1.5 6장 · API 명세 v0.5 예시와 같은 값)
--
-- ⚠️ 설명용으로 지어낸 가상 사건이다. 실제 판례가 아니다.
--    - 법정형 · 선고 가능 범위는 형법 조문 그대로 (형법 제250조 제1항, 제55조 제1항)
--    - 형량 · 판단 요소 방향 · 권고 범위 · 판결문 발췌는 지어낸 값
--    - 실제 사건 데이터는 저장소가 공개라 여기에 넣지 않는다 (BE-16 D5, 저장소 밖에서 주입)
--
-- 사전 판단 형량 구간(sentence_range_option)은 V1에서 이미 넣었으므로 여기서 넣지 않는다.
-- ID에 기대지 않도록 PL/pgSQL 변수로 새로 만든 행의 ID를 받아 쓴다.

DO $$
DECLARE
    v_guideline_id bigint;
    v_case_id      bigint;
    v_ai_id        bigint;
    v_court_id     bigint;
BEGIN
    -- 양형기준 버전 (가상 예시 값)
    INSERT INTO sentencing_guideline (crime_category, version_name, effective_date, source_url)
    VALUES ('살인범죄', '가상 예시 버전', DATE '2024-01-01', NULL)
    RETURNING id INTO v_guideline_id;

    -- 사건
    INSERT INTO legal_case (
        title, crime_type, charge_name, short_intro, keywords, difficulty, estimated_minutes,
        overview, thumbnail_url, deidentified_items, applied_law, statutory_penalty_text,
        recommended_min_months, recommended_max_months, recommended_basis,
        guideline_id, incident_date, status, published_at
    ) VALUES (
        '빌린 돈 문제로 찾아온 지인을 살해한 사건',
        'MURDER',
        '살인',
        '빌린 돈 문제로 찾아온 지인과 다투다 흉기로 살해한 사건입니다.',
        '["돈 문제", "집으로 찾아옴"]'::jsonb,
        'HIGH',
        15,
        '피고인이 빌린 돈을 갚지 못해 오래 다투던 지인이 집으로 찾아오자, 말다툼 끝에 집에 있던 흉기로 피해자를 살해하고 구호 조치 없이 집을 나간 사건이다.',
        NULL,
        NULL,
        '형법 제250조 제1항',
        '사형, 무기 또는 5년 이상의 징역',
        84,
        144,
        '살인범죄 제2유형(보통 동기 살인). 특별감경인자 1개(실질적 피해 회복 — 5,000만 원 공탁)가 있고 특별가중인자는 없어 감경영역(징역 7년 ~ 12년)을 적용한다. 계획 없이 다투던 중 벌어진 범행이라 특별가중인자인 ''계획적 살인 범행''에 해당하지 않는다.',
        v_guideline_id,
        DATE '2024-03-15',
        'PUBLISHED',
        now()
    )
    RETURNING id INTO v_case_id;

    -- 사건 정보 섹션 (섹션 ① 개요는 legal_case.overview를 쓴다)
    INSERT INTO case_section (case_id, stage, section_type, title, content, data, display_order) VALUES
    (v_case_id, 'DETAIL', 'FACTS', '주요 사실관계',
     '피고인은 피해자에게서 돈을 빌렸으나 갚지 못했고, 사건 3개월 전부터 변제 문제로 여러 차례 다퉜다. 사건 당일 피해자가 변제를 요구하며 피고인의 집으로 찾아왔고, 말다툼이 이어지던 중 피고인은 집에 있던 흉기를 집어 들어 피해자를 공격했다. 피해자는 그 자리에서 숨졌고, 피고인은 구호 조치 없이 집을 나갔다.',
     NULL, 1),
    (v_case_id, 'DETAIL', 'DAMAGE', '피해 결과', NULL,
     '[{"label": "피해자 수", "value": "1명"}, {"label": "피해 결과", "value": "사망"}, {"label": "피해자와의 관계", "value": "지인 (돈을 빌린 사이)"}, {"label": "범행 도구", "value": "집에 있던 흉기"}]'::jsonb,
     2),
    (v_case_id, 'DETAIL', 'DEFENDANT', '피고인 관련 사실',
     '30대이고 형사처벌을 받은 전력이 없다. 수사 초기부터 범행을 인정하고 반성하고 있다.',
     NULL, 3),
    (v_case_id, 'DETAIL', 'SETTLEMENT', '합의 · 피해 회복',
     '피해 회복을 위해 5,000만 원을 공탁했으나 합의에 이르지 못했다. 피해자에게는 부양하던 어린 자녀 2명이 있고, 유족은 엄벌을 원한다.',
     NULL, 4),
    (v_case_id, 'ARGUMENT', 'PROSECUTOR', '검사',
     '피고인은 흉기로 피해자를 공격해 생명을 빼앗았고, 범행 뒤 구호 조치 없이 현장을 떠났다. 피해자에게는 부양하던 어린 자녀가 있고 유족이 엄벌을 원하므로 무겁게 처벌해야 한다.',
     NULL, 1),
    (v_case_id, 'ARGUMENT', 'DEFENSE', '피고인 · 변호인',
     '미리 계획한 범행이 아니라 말다툼 중 순간적으로 벌어진 우발적 범행이다. 피고인은 오랜 채무로 정신적으로 지쳐 있었고, 수사 초기부터 범행을 인정하며 반성하고 있다. 피해 회복을 위해 5,000만 원을 공탁했고 형사처벌 전력이 없다.',
     NULL, 2),
    (v_case_id, 'LAW', 'LAW_TERM', '용어 설명', NULL,
     '[{"term": "법정형", "desc": "법률에 정해진 처벌 범위. 살인죄는 사형, 무기 또는 5년 이상의 징역이다."}, {"term": "작량감경", "desc": "범행 경위 등 참작할 사정이 있을 때 판사가 형을 줄이는 것. 징역은 하한과 상한이 1/2이 되고, 무기징역은 징역 10년 ~ 50년, 사형은 무기징역 또는 징역 20년 ~ 50년이 된다."}, {"term": "양형기준", "desc": "양형위원회가 정한 형량 권고 기준. 판사가 따라야 할 의무는 없지만 벗어나면 이유를 적어야 한다."}, {"term": "보통 동기 살인", "desc": "살인범죄 양형기준의 제2유형. 원한이나 금전 문제 등 흔히 볼 수 있는 동기로 저지른 살인을 말한다."}, {"term": "특별양형인자", "desc": "권고 영역(감경 · 기본 · 가중)을 정하는 주요 사정. 이 사건처럼 감경요소만 있으면 감경영역이 된다."}, {"term": "감경영역", "desc": "형을 가볍게 할 특별한 사정이 있어 기본 권고 형량보다 낮은 구간이 적용되는 구간."}, {"term": "공탁", "desc": "피해자 측이 합의하지 않아도 피해 회복을 위해 돈을 법원에 맡기는 것."}]'::jsonb,
     1),
    (v_case_id, 'SUMMARY', 'SUMMARY', '핵심 사실 요약', NULL,
     '["집으로 찾아온 지인 1명을 살해", "다투던 중 집에 있던 흉기를 사용", "사건 3개월 전부터 변제 문제로 갈등", "범행 뒤 구호 조치 없이 현장을 떠남", "수사 초기부터 자백 · 반성", "유족 엄벌 요구, 5,000만 원 공탁", "형사처벌 전력 없음"]'::jsonb,
     1);

    -- 형벌별 법정형과 선고 가능 범위 (벌금은 법정형에 없어 행을 두지 않는다)
    INSERT INTO penalty_rule (case_id, penalty_type, statutory_min, statutory_max, allowed_min, allowed_max, allowed_basis, suspension_allowed, display_order) VALUES
    (v_case_id, 'DEATH',  NULL, NULL, 240, 600, '사형을 작량감경하면 무기 또는 징역 20 ~ 50년 (형법 제55조 제1항 제1호)', false, 1),
    (v_case_id, 'LIFE',   NULL, NULL, 120, 600, '무기징역을 작량감경하면 징역 10 ~ 50년 (형법 제55조 제1항 제2호)', false, 2),
    (v_case_id, 'PRISON', 60,   360,  30,  360, '유기징역 선택, 작량감경 시 하한 1/2. 법률상 감경 · 가중 사유 없음', true, 3);

    -- 판단 요소 11개 (display_order = ERD 6장 요소 번호)
    INSERT INTO factor (case_id, label, pre_label, reveal_stage, summary_tag, display_order) VALUES
    (v_case_id, '빌린 돈을 갚지 못해 오래 다툼이 있었다',                 '돈 문제로 오래 다툼이 있었다', 'OVERVIEW', '범행 경위',     1),
    (v_case_id, '다투던 중 집에 있던 흉기를 집어 들었다',                 '다투던 중 흉기를 집어 들었다', 'OVERVIEW', '범행 방식',     2),
    (v_case_id, '범행 뒤 구호 조치 없이 현장을 떠났다',                   '범행 뒤 현장을 떠났다',        'OVERVIEW', '범행 후 정황',  3),
    (v_case_id, '사건 3개월 전부터 변제 문제로 여러 차례 다퉜다',         NULL,                            'DETAIL',   '범행 경위',     4),
    (v_case_id, '유족이 엄벌을 원한다',                                   NULL,                            'DETAIL',   '피해자 의사',   5),
    (v_case_id, '피해자에게는 부양하던 어린 자녀 2명이 있다',             NULL,                            'DETAIL',   '피해 결과',     6),
    (v_case_id, '수사 초기부터 범행을 인정하고 반성하고 있다',            NULL,                            'DETAIL',   '반성',          7),
    (v_case_id, '형사처벌 전력이 없다',                                   NULL,                            'DETAIL',   '전력',          8),
    (v_case_id, '피해 회복을 위해 5,000만 원을 공탁했다',                 NULL,                            'DETAIL',   '피해 회복',     9),
    (v_case_id, '피고인은 우발적 범행이라고 주장한다',                    NULL,                            'ARGUMENT', '범행 경위',     10),
    (v_case_id, '피고인은 오랜 채무로 정신적으로 지쳐 있었다고 주장한다', NULL,                            'ARGUMENT', '피고인 사정',   11);

    -- 원본 판결문 (내부 전용). 가상 사건이라 실제 사건번호 · 법원 · 원문이 없다
    INSERT INTO case_source (case_id, court_level, case_number, court_name, decided_at, is_final, source_org, original_text, note)
    VALUES (v_case_id, 'FIRST', 'SAMPLE-0001', NULL, NULL, true, '가상 예시 사건 (실제 판례 아님)', NULL,
            '개발용 임시 시드. 실제 사건 등록 시 교체한다 (BE-16 D5).');

    -- AI 판결 (오프라인 생성 · 검수 결과 적재를 가정한 가상 값)
    INSERT INTO judgment (case_id, subject_type, timing, penalty_type, reduced_to, prison_months, fine_amount, suspension_months,
                          extra_dispositions, summary, reasoning, reference_tags, is_published)
    VALUES (v_case_id, 'AI', 'FINAL', 'PRISON', NULL, 144, NULL, NULL,
            '[]'::jsonb,
            '다투다 벌어진 범행과 공탁 · 반성을 함께 저울질한 판단',
            '흉기를 집어 들어 피해자를 공격하고 구호 조치 없이 자리를 떠난 점은 무겁지만, 공탁으로 피해 회복을 시도했고 범행을 인정하며 전력이 없는 점을 고려했다.',
            '["형법 제250조", "살인범죄 양형기준", "유사 판례 5건"]'::jsonb,
            true)
    RETURNING id INTO v_ai_id;

    -- 재판부 판결 (가상 값)
    INSERT INTO judgment (case_id, subject_type, timing, penalty_type, reduced_to, prison_months, fine_amount, suspension_months,
                          extra_dispositions, summary, reasoning, plain_explanation, excerpt, is_published)
    VALUES (v_case_id, 'COURT', 'FINAL', 'PRISON', NULL, 120, NULL, NULL,
            '[{"type": "CONFISCATION", "value": "범행에 사용한 흉기"}]'::jsonb,
            '유족의 처벌 의사를 무겁게 보면서도 공탁과 반성을 감안한 판단',
            '피고인은 흉기로 피해자의 생명을 빼앗고 구호 조치 없이 현장을 떠났으며, 어린 자녀를 둔 유족이 엄벌을 원한다. 다만 계획적인 범행으로 보기 어렵고, 피해 회복을 위해 상당한 금액을 공탁했으며, 범행을 인정하고 반성하며 형사처벌 전력이 없는 점을 참작했다.',
            '재판부는 피해자의 생명을 빼앗은 결과와 유족의 사정을 무겁게 봤어요. 그래도 미리 계획한 범행이 아니고, 공탁으로 피해를 갚으려 했고, 처음부터 잘못을 인정한 점을 고려해 권고 범위 안에서 형을 정했어요.',
            '(가상 예시 발췌) 피고인이 피해 회복을 위하여 상당한 금액을 공탁한 점, 수사 초기부터 범행을 인정하며 반성하고 있는 점, 형사처벌 전력이 없는 점은 유리한 정상이다.',
            true)
    RETURNING id INTO v_court_id;

    -- 판단 요소 평가 (ERD 6장 judgment_factor)
    INSERT INTO judgment_factor (judgment_id, factor_id, direction, evidence)
    SELECT v_ai_id, f.id, x.direction, NULL
    FROM (VALUES (2, 'UP'), (3, 'UP'), (5, 'UP'), (7, 'DOWN'), (8, 'DOWN'), (9, 'DOWN')) AS x(factor_no, direction)
    JOIN factor f ON f.case_id = v_case_id AND f.display_order = x.factor_no;

    INSERT INTO judgment_factor (judgment_id, factor_id, direction, evidence)
    SELECT v_court_id, f.id, x.direction, x.evidence
    FROM (VALUES
        (2, 'UP',   '(가상) 피고인은 다투던 중 흉기를 들어 피해자를 공격하였다.'),
        (3, 'UP',   '(가상) 피고인은 범행 후 피해자에 대한 아무런 구호 조치 없이 현장을 이탈하였다.'),
        (5, 'UP',   '(가상) 유족은 피고인에 대한 엄벌을 탄원하고 있다.'),
        (6, 'UP',   '(가상) 피해자에게는 부양하던 어린 자녀들이 있다.'),
        (7, 'DOWN', '(가상) 피고인은 수사 초기부터 범행을 인정하며 반성하고 있다.'),
        (8, 'DOWN', '(가상) 피고인은 형사처벌 전력이 없다.'),
        (9, 'DOWN', '(가상) 피고인은 피해 회복을 위하여 상당한 금액을 공탁하였다.')
    ) AS x(factor_no, direction, evidence)
    JOIN factor f ON f.case_id = v_case_id AND f.display_order = x.factor_no;
END $$;
