-- V7__add_legal_case_title_factor_display_order_unique.sql
-- BE-15 적재 SQL(tools/ai-judgment/to_seed_sql.py)이 legal_case.title과
-- factor(case_id, display_order)를 조회 키로 쓰므로(환경마다 id가 달라도 같은 SQL을 쓰기 위함),
-- 두 값의 유일성을 DB 제약으로 못 박는다. 시드 스크립트도 이미 title로 중복 삽입을 막고
-- display_order를 요소 번호로 1부터 매긴다는 전제로 작성돼 있어, 이 제약은 그 전제를 지킬 뿐이다.
--
-- 제약을 추가하기 전에 기존 데이터에 중복이 있는지 먼저 확인한다. 중복이 있으면 ALTER TABLE이
-- 실패하며 마이그레이션이 중단되는데, 어떤 값이 중복인지 바로 보이도록 데이터를 지우지 않고
-- 메시지로 알려준다(정리는 사람이 판단해서 한다).
DO $$
DECLARE
    dup_titles  text;
    dup_factors text;
BEGIN
    SELECT string_agg(title, ', ') INTO dup_titles
    FROM (SELECT title FROM legal_case GROUP BY title HAVING count(*) > 1) t;
    IF dup_titles IS NOT NULL THEN
        RAISE EXCEPTION 'legal_case.title 중복이 있어 UNIQUE 제약을 추가할 수 없습니다 (정리 후 다시 시도): %', dup_titles;
    END IF;

    SELECT string_agg(case_id || '번 사건의 display_order ' || display_order, ', ') INTO dup_factors
    FROM (SELECT case_id, display_order FROM factor GROUP BY case_id, display_order HAVING count(*) > 1) f;
    IF dup_factors IS NOT NULL THEN
        RAISE EXCEPTION 'factor(case_id, display_order) 중복이 있어 UNIQUE 제약을 추가할 수 없습니다 (정리 후 다시 시도): %', dup_factors;
    END IF;
END $$;

ALTER TABLE legal_case ADD CONSTRAINT uk_legal_case_title UNIQUE (title);
ALTER TABLE factor ADD CONSTRAINT uk_factor_case_display_order UNIQUE (case_id, display_order);
