-- V37__legal_case_cast_sensitive_flags.sql (BE-125)
-- 사건마다 배역(내로 · 남불 중 누가 판사 · 검사인가)과 민감 플래그를 기록한다.
--
-- cast_roles: {"prosecutor": "naero", "defense": "nambul"} 모양. case_script.cast_roles(V32) · 파이프라인
-- stages.script.cast와 **같은 모양**이라 옮길 때 변환이 없다. 칸을 둘로 펼치지 않은 이유: 공동 피고인이 생기면
-- 키를 더하면 되고(COMMON-65 2차) 그때 마이그레이션이 필요 없다. 비어 있으면 아직 배정하지 않은 사건이다.
--
-- sensitive_flags: 파이프라인 분류(BE-123)가 켠 플래그 이름 배열(예: ["MENTAL_HEALTH"]). keywords(V2)와 같이
-- 열린 목록으로 두고 DB CHECK를 걸지 않는다 — 플래그 목록을 정하는 쪽은 파이프라인이라, 값이 늘 때마다
-- 제약을 다시 만들면 앞 마이그레이션의 값을 빠뜨리는 사고가 난다(V32 · V34 감사 로그 CHECK 머리말).
-- 허용 값 검사와 "사용자에게 안내할 플래그" 구분은 코드(SensitiveFlags)가 한다.
--
-- 두 칸 모두 값은 넣지 않는다. 이미 적재된 사건의 초기값은 실제 사건 정보라 비공개 시드 저장소의 UPDATE로 넣는다.

ALTER TABLE legal_case ADD COLUMN cast_roles jsonb;
ALTER TABLE legal_case ADD COLUMN sensitive_flags jsonb;
