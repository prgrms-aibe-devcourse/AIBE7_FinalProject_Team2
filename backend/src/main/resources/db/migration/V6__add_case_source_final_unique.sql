-- V6__add_case_source_final_unique.sql
-- 사건마다 최종 확정 판결(is_final = true)은 1건만 둔다 (ERD 3-1 case_source, FR-5-4)
-- 1심이 그대로 확정되면 1심 행이, 항소심에서 형량이 바뀌면 항소심 행이 최종 확정 판결이다.
-- CaseSourceRepository.findFinalByCaseId가 단건으로 조회하므로 중복이 있으면 조회가 실패한다.
CREATE UNIQUE INDEX uk_case_source_final
    ON case_source (case_id)
    WHERE is_final = TRUE;
