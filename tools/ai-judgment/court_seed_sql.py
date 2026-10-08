"""재판부 판결(COURT) 초안 → 비공개 적재 SQL (BE-38). 표준 라이브러리만 쓴다.

입력은 BE-14 형식 JSON(`judgment` · `judgmentFactors` · `excludedFactors`)이다. case-extractor/court_draft.py가
만든 초안이나 사람이 쓴 BE-14 JSON 모두 받는다.

python3 court_seed_sql.py cases/x.court_draft.json --title "<legal_case.title>" > out/x.court.sql

- **비공개(is_published=false)로만 넣는다.** 기존 공개 재판부 판결은 건드리지 않는다. 관리자가 원 판결문과 대조해 검수한 뒤
  공개한다(관리자 후검수 BE-33). 공개된 재판부 판결을 바로 바꾸는 기존 방식은 비공개 저장소 tools/court_judgment_to_sql.py(R__20)
- 사건은 legal_case.title(STRICT), 판단 요소는 factor.display_order · label로 실행 시점에 찾는다. 하나라도 다르면 멈춘다
- 같은 사건에 내용(요약 · 이유 · 발췌 · 형량)이 같은 재판부 판결이 이미 있으면 건너뛴다 (같은 SQL을 두 번 실행해도 안전)
- judgment 테이블 CHECK(V4)와 같은 규칙으로 먼저 검사해, 잘못된 값이 DB 오류로 처음 드러나지 않게 한다
- 재판부 판결은 실제 판결이 들어 있는 내부 자료다. AI 판결 입력(build_prompt.py)에 넣지 않는다 (FR-4-1)
"""

import argparse
import sys

from common import NO_TERM_PENALTIES, PENALTY_TYPES, REDUCIBLE_TO, load_json
from to_seed_sql import dollar_quote_tag, sql_int, sql_jsonb, sql_text

SUMMARY_MAX_LENGTH = 100  # judgment.summary varchar(100)
DISPOSITION_TYPES = ("COMMUNITY_SERVICE", "CONFISCATION")
REQUIRED_TEXT = ("summary", "reasoning", "plainExplanation", "excerpt")


class CourtSeedError(Exception):
    pass


def _positive_int(value):
    return isinstance(value, int) and not isinstance(value, bool) and value > 0


def check_court(data):
    """judgment · judgment_factor CHECK와 같은 규칙. 오류는 모아서 CourtSeedError 하나로 알린다.

    오류에는 항목 위치와 위반 종류만 남기고 **입력 값(모델이 쓴 글 등)은 넣지 않는다** — 오류가 파이프라인 state.json으로 이어진다.
    """
    if not isinstance(data.get("judgment"), dict) or not isinstance(data.get("judgmentFactors"), list):
        raise CourtSeedError("judgment(객체)와 judgmentFactors(배열)가 필요합니다")
    j, errors = data["judgment"], []
    if j.get("subjectType", "COURT") != "COURT" or j.get("timing", "FINAL") != "FINAL":
        errors.append("judgment는 COURT · FINAL이어야 합니다")
    missing = [k for k in REQUIRED_TEXT if not str(j.get(k) or "").strip()]
    if missing:
        errors.append("비어 있는 항목: " + ", ".join(missing))
    penalty, reduced = j.get("penaltyType"), j.get("reducedTo")
    if penalty not in PENALTY_TYPES:
        errors.append("penaltyType이 허용 목록에 없습니다")
    elif reduced is not None and reduced not in REDUCIBLE_TO[penalty]:
        errors.append("감경 조합(penaltyType → reducedTo)이 허용되지 않습니다")
    final = reduced or penalty
    prison, fine, suspension = j.get("prisonMonths"), j.get("fineAmount"), j.get("suspensionMonths")
    for key, value in (("prisonMonths", prison), ("fineAmount", fine), ("suspensionMonths", suspension)):
        if value is not None and not _positive_int(value):
            errors.append(f"{key}는 양의 정수여야 합니다")
    if final == "PRISON" and (prison is None or fine is not None):
        errors.append("징역이면 prisonMonths가 필요하고 fineAmount는 없어야 합니다")
    if final == "FINE" and (fine is None or prison is not None):
        errors.append("벌금이면 fineAmount가 필요하고 prisonMonths는 없어야 합니다")
    if final in NO_TERM_PENALTIES and any(v is not None for v in (prison, fine, suspension)):
        errors.append("사형 · 무기는 형량 · 집행유예 값이 없어야 합니다")
    if penalty in NO_TERM_PENALTIES and suspension is not None:
        errors.append("사형 · 무기를 고른 판결에는 집행유예를 넣을 수 없습니다")
    if len(str(j.get("summary") or "")) > SUMMARY_MAX_LENGTH:
        errors.append(f"summary는 {SUMMARY_MAX_LENGTH}자 이내입니다")
    for i, d in enumerate(j.get("extraDispositions") or []):
        if not isinstance(d, dict) or d.get("type") not in DISPOSITION_TYPES or not str(d.get("value") or "").strip():
            errors.append(f"extraDispositions[{i}]: 부가 처분 형식이 올바르지 않습니다")

    ids = []
    for i, f in enumerate(data["judgmentFactors"]):
        fid = f.get("factorId") if isinstance(f, dict) else None
        if not _positive_int(fid):
            errors.append(f"judgmentFactors[{i}]: factorId가 양의 정수가 아닙니다")
            continue
        ids.append(fid)
        if f.get("direction") not in ("UP", "DOWN"):
            errors.append(f"요소 {fid}: direction은 UP/DOWN입니다")
        if not str(f.get("label") or "").strip() or not str(f.get("evidence") or "").strip():
            errors.append(f"요소 {fid}: label · evidence가 비었습니다")
    if not ids:
        errors.append("재판부가 고려한 판단 요소가 없습니다")
    if len(ids) != len(set(ids)):
        errors.append("같은 판단 요소가 두 번 있습니다")
    if errors:
        raise CourtSeedError("재판부 판결 적재 SQL을 만들 수 없습니다:\n- " + "\n- ".join(errors))


def build_court_sql(data, title):
    """비공개 재판부 판결 INSERT를 담은 DO 블록 (BEGIN/COMMIT 없음)."""
    check_court(data)
    if data.get("caseTitle") is not None and data["caseTitle"] != title:
        raise CourtSeedError("재판부 판결 파일의 caseTitle이 적재할 사건 제목과 다릅니다")
    j = data["judgment"]
    factors = sorted(data["judgmentFactors"], key=lambda f: f["factorId"])
    title_literal = sql_text(title)
    label_rows = ",\n            ".join(f"({f['factorId']}, {sql_text(f['label'])})" for f in factors)
    factor_rows = ",\n        ".join(
        f"({f['factorId']}, {sql_text(f['direction'])}, {sql_text(f['evidence'])})" for f in factors)
    same = (f"summary = {sql_text(j['summary'])} AND reasoning = {sql_text(j['reasoning'])} "
            f"AND excerpt = {sql_text(j['excerpt'])} AND penalty_type = {sql_text(j['penaltyType'])} "
            f"AND prison_months IS NOT DISTINCT FROM {sql_int(j.get('prisonMonths'))} "
            f"AND fine_amount IS NOT DISTINCT FROM {sql_int(j.get('fineAmount'))} "
            f"AND suspension_months IS NOT DISTINCT FROM {sql_int(j.get('suspensionMonths'))}")
    inner = f"""
DECLARE
    v_case_id     bigint;
    v_judgment_id bigint;
BEGIN
    BEGIN
        SELECT id INTO STRICT v_case_id FROM legal_case WHERE title = {title_literal};
    EXCEPTION
        WHEN NO_DATA_FOUND THEN
            RAISE EXCEPTION 'legal_case에서 title=%를 찾을 수 없습니다 (사건을 먼저 적재)', {title_literal};
        WHEN TOO_MANY_ROWS THEN
            RAISE EXCEPTION 'legal_case에서 title=%가 유일하지 않습니다', {title_literal};
    END;
    -- 판단 요소 번호(display_order) · 라벨이 DB와 모두 같은지 확인
    IF EXISTS (
        SELECT 1 FROM (VALUES
            {label_rows}
        ) AS v(display_order, label)
        LEFT JOIN factor f ON f.case_id = v_case_id AND f.display_order = v.display_order
        WHERE f.id IS NULL OR f.label IS DISTINCT FROM v.label
    ) THEN
        RAISE EXCEPTION '재판부 판결의 판단 요소 번호 · 라벨이 DB와 다릅니다 (title=%)', {title_literal};
    END IF;
    -- 같은 내용의 재판부 판결이 이미 있으면 건너뛴다 (같은 SQL을 두 번 실행해도 안전)
    IF EXISTS (SELECT 1 FROM judgment WHERE case_id = v_case_id AND subject_type = 'COURT' AND {same}) THEN
        RAISE NOTICE '재판부 판결 적재 건너뜀: 같은 내용의 재판부 판결이 이미 있습니다 (title=%)', {title_literal};
        RETURN;
    END IF;
    -- 비공개로 넣는다. 기존 공개 재판부 판결은 건드리지 않는다 (관리자가 검수 후 공개 · 교체, BE-33)
    INSERT INTO judgment (
        case_id, subject_type, timing,
        penalty_type, reduced_to, prison_months, fine_amount, suspension_months,
        extra_dispositions, summary, reasoning, plain_explanation, excerpt,
        is_published, created_at
    ) VALUES (
        v_case_id, 'COURT', 'FINAL',
        {sql_text(j['penaltyType'])}, {sql_text(j.get('reducedTo'))}, {sql_int(j.get('prisonMonths'))}, {sql_int(j.get('fineAmount'))}, {sql_int(j.get('suspensionMonths'))},
        {sql_jsonb(j.get('extraDispositions') or [])}, {sql_text(j['summary'])}, {sql_text(j['reasoning'])}, {sql_text(j['plainExplanation'])}, {sql_text(j['excerpt'])},
        false, now()
    )
    RETURNING id INTO v_judgment_id;
    -- 재판부가 고려한 요소만 넣는다. 행이 없는 요소는 "고려하지 않음"(ERD)
    INSERT INTO judgment_factor (judgment_id, factor_id, direction, evidence)
    SELECT v_judgment_id, f.id, v.direction, v.evidence
    FROM factor f
    JOIN (VALUES
        {factor_rows}
    ) AS v(display_order, direction, evidence) ON f.display_order = v.display_order
    WHERE f.case_id = v_case_id;
END"""
    tag = dollar_quote_tag(inner)
    comment_title = " ".join(str(title).split())
    return f"""-- 재판부 판결 적재 (비공개 · 검수 대기): {comment_title}
-- 실제 판결이 들어 있는 내부 자료. 관리자가 원 판결문과 대조해 검수한 뒤 공개한다
DO {tag}{inner} {tag};
"""


def main():
    parser = argparse.ArgumentParser(description="재판부 판결(COURT) 초안을 비공개 적재 SQL로 바꾼다 (BE-38)")
    parser.add_argument("court_judgment", help="BE-14 형식 JSON (court_draft.py 출력 등)")
    parser.add_argument("--title", required=True, help="legal_case.title")
    args = parser.parse_args()
    try:
        sql = build_court_sql(load_json(args.court_judgment), args.title)
    except CourtSeedError as e:
        print(f"[오류] {e}", file=sys.stderr)
        return 1
    print(f"BEGIN;\n{sql}COMMIT;")
    return 0


if __name__ == "__main__":
    sys.exit(main())
