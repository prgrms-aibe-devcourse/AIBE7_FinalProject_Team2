-- V7__add_legal_case_title_factor_display_order_unique.sql
-- BE-15 적재 SQL(tools/ai-judgment/to_seed_sql.py)이 legal_case.title과
-- factor(case_id, display_order)를 조회 키로 쓰므로(환경마다 id가 달라도 같은 SQL을 쓰기 위함),
-- 두 값의 유일성을 DB 제약으로 못 박는다. 시드 스크립트도 이미 title로 중복 삽입을 막고
-- display_order를 요소 번호로 1부터 매긴다는 전제로 작성돼 있어, 이 제약은 그 전제를 지킬 뿐이다.
ALTER TABLE legal_case ADD CONSTRAINT uk_legal_case_title UNIQUE (title);
ALTER TABLE factor ADD CONSTRAINT uk_factor_case_display_order UNIQUE (case_id, display_order);
