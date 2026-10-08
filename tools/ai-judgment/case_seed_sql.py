"""비식별화한 사건 파일 → 사건 콘텐츠 적재 SQL (BE-31). 사건은 항상 DRAFT(사용자에게 안 보임)로 넣는다.

case-extractor 결과 세 파일을 합쳐 legal_case · case_section · penalty_rule · factor · case_source 행을 만든다.
- <name>.case.json: 사건 · 섹션 · 형벌 규칙 · 판단 요소 · 목록 카드(listing)
- <name>.report.json: 판단 요소 preLabel · summaryTag, 형벌 규칙 근거, 비식별화 항목
- <name>.source_internal.json: 원본 판결문 정보(사건번호 · 법원명 · 선고일 · 원문, 로컬에서 꺼낸 내부 전용 값)

- 같은 제목의 DRAFT 사건이 이미 있으면 건너뛴다 (같은 SQL을 두 번 실행해도 안전). 같은 제목의 사건이 DRAFT가 아니면
  다른 사건일 수 있으므로 멈춘다(전체 취소). 이미 공개된 사건에 AI 판결만 넣을 때는 파이프라인 load.case=false로 사건 SQL을 뺀다
- 양형기준 연결(guideline_id)은 비워 둔다(이후 RAG로 연결). 사건 발생일(incident_date)은 source_internal.json의
  incidentDate(원문 · 선고일과 대조해 확인한 값, BE-38) 또는 파이프라인 설정 incidentDate로 넣고, 없으면 비워 둔다
- 재판부 판결(COURT)은 넣지 않는다. 지금처럼 따로 작성해 넣는다(private-seed R__20 방식)
- 공개(PUBLISHED)는 관리자가 검수 후 한다. 이 SQL은 공개하지 않는다

python3 case_seed_sql.py cases/x.case.json cases/x.report.json cases/x.source_internal.json > out/x.case.sql
"""

import argparse
import datetime
import sys

from common import VALUE_AXES, load_json
from to_seed_sql import dollar_quote_tag, sql_int, sql_jsonb, sql_text

DEFAULT_SOURCE_ORG = "법원 공개 판결문"  # 사용자에게 보일 수 있는 유일한 출처 칸. 법원명 · 서비스명을 쓰지 않는다
COURT_LEVELS = ("FIRST", "APPEAL", "SUPREME")
# ERD 컬럼 길이 (DB에서 실패하기 전에 막는다)
MAX_LENGTHS = {
    "title": 100, "charge_name": 100, "short_intro": 200, "applied_law": 200, "statutory_penalty_text": 200,
    "section.title": 100, "factor.label": 100, "factor.pre_label": 100, "factor.summary_tag": 20,
    "penalty_rule.allowed_basis": 300, "case_number": 50, "court_name": 50, "source_org": 50,
}


class CaseSeedError(Exception):
    pass


def _check_length(errors, name, value):
    if value is not None and len(str(value)) > MAX_LENGTHS[name]:
        errors.append(f"{name}가 {MAX_LENGTHS[name]}자를 넘습니다")


def resolve_sources(sources, overrides=None, final_index=None):
    """원본 정보에 설정 값을 덮어쓰고 최종 확정 판결을 정한다.

    overrides: 파일 순서대로의 덮어쓸 값 목록 ({caseNumber, courtName, decidedAt, courtLevel, note}). 자동으로 못 찾은 값을 채운다.
    final_index: 최종 확정 판결 번호(0부터). 없으면 심급이 가장 높은 판결(같으면 뒤의 것).
    """
    overrides = overrides or []
    resolved = []
    for i, source in enumerate(sources):
        merged = dict(source)
        if i < len(overrides):
            merged.update({k: v for k, v in overrides[i].items() if v is not None})
        resolved.append(merged)
    if not resolved:
        return resolved
    if final_index is None:
        ranks = [COURT_LEVELS.index(s["courtLevel"]) if s.get("courtLevel") in COURT_LEVELS else -1 for s in resolved]
        final_index = max(range(len(resolved)), key=lambda i: (ranks[i], i))
    if not 0 <= final_index < len(resolved):
        raise CaseSeedError(f"최종 판결 번호 {final_index}가 판결문 개수({len(resolved)})를 벗어났습니다")
    for i, source in enumerate(resolved):
        source["isFinal"] = i == final_index
    return resolved


def check_inputs(case, report, sources, source_org):
    errors = []
    listing = case.get("listing") or {}
    if not listing.get("shortIntro"):
        errors.append("case.json에 목록 카드 소개(listing.shortIntro)가 없습니다. case-extractor(extract-v2 이상)로 다시 만드세요")
    for key, name in (("title", "title"), ("chargeName", "charge_name"), ("appliedLaw", "applied_law"),
                      ("statutoryPenaltyText", "statutory_penalty_text")):
        if not case.get(key):
            errors.append(f"case.json에 {key}가 없습니다")
        _check_length(errors, name, case.get(key))
    _check_length(errors, "short_intro", listing.get("shortIntro"))
    if listing.get("difficulty") not in (None, "LOW", "MID", "HIGH"):
        errors.append(f"difficulty는 LOW · MID · HIGH 중 하나입니다: {listing.get('difficulty')}")
    for section in case.get("sections", []):
        _check_length(errors, "section.title", section.get("title"))

    extras = {e["factorId"]: e for e in report.get("factorExtras", [])}
    for factor in case.get("factors", []):
        extra = extras.get(factor["factorId"])
        if not extra or not extra.get("summaryTag"):
            errors.append(f"판단 요소 {factor['factorId']}: 보고서에 summaryTag가 없습니다")
            continue
        _check_length(errors, "factor.label", factor["label"])
        _check_length(errors, "factor.pre_label", extra.get("preLabel"))
        _check_length(errors, "factor.summary_tag", extra["summaryTag"])
        if not factor.get("revealStage"):
            errors.append(f"판단 요소 {factor['factorId']}: revealStage가 없습니다")
        # valueAxis는 없거나 null이어도 된다(extract-v5 이전 보고서 · 어느 축에도 맞지 않는 요소). 값이 있으면 허용 값이어야 한다
        if extra.get("valueAxis") not in (None, *VALUE_AXES):
            errors.append(f"판단 요소 {factor['factorId']}: valueAxis는 {' · '.join(VALUE_AXES)} 중 하나이거나 null입니다")
    for basis in (report.get("penaltyRuleBasis") or {}).values():
        _check_length(errors, "penalty_rule.allowed_basis", basis)

    if not sources:
        errors.append("원본 판결문 정보가 없습니다 (source_internal.json)")
    for i, source in enumerate(sources):
        label = f"판결문 {i + 1}({source.get('file', '?')})"
        if not source.get("caseNumber"):
            errors.append(f"{label}: 사건번호를 찾지 못했습니다. 설정 파일 sources[{i}].caseNumber로 넣으세요")
        if source.get("courtLevel") not in COURT_LEVELS:
            errors.append(f"{label}: 심급(FIRST · APPEAL · SUPREME)을 정하지 못했습니다. sources[{i}].courtLevel로 넣으세요")
        _check_length(errors, "case_number", source.get("caseNumber"))
        _check_length(errors, "court_name", source.get("courtName"))
    if not source_org.strip():
        errors.append("source_org가 비었습니다")
    _check_length(errors, "source_org", source_org)
    if errors:
        raise CaseSeedError("사건 적재 SQL을 만들 수 없습니다:\n- " + "\n- ".join(errors))


def sql_date(value):
    """YYYY-MM-DD → SQL date 값 (없으면 NULL). 형식이 틀리면 CaseSeedError."""
    if value is None:
        return "NULL"
    try:
        datetime.date.fromisoformat(value)
    except (TypeError, ValueError):
        raise CaseSeedError(f"사건 발생일은 YYYY-MM-DD여야 합니다: {value!r}") from None
    return sql_text(value) + "::date"


def build_case_sql(case, report, sources, *, source_org=DEFAULT_SOURCE_ORG, source_note=None, incident_date=None):
    """사건 콘텐츠 INSERT를 담은 DO 블록 (BEGIN/COMMIT 없음). sources는 resolve_sources를 거친 값."""
    check_inputs(case, report, sources, source_org)
    incident_sql = sql_date(incident_date)
    listing = case["listing"]
    recommended = case.get("recommended") or {}
    extras = {e["factorId"]: e for e in report["factorExtras"]}
    basis = report.get("penaltyRuleBasis") or {}
    title = sql_text(case["title"])

    sections = "\n".join(
        f"""    INSERT INTO case_section (case_id, stage, section_type, title, content, data, display_order)
    VALUES (v_case_id, {sql_text(s['stage'])}, {sql_text(s['sectionType'])}, {sql_text(s.get('title'))}, """
        f"""{sql_text(s.get('content'))}, {sql_jsonb(s['data']) if s.get('data') is not None else 'NULL'}, {i});"""
        for i, s in enumerate(case["sections"], start=1)
    )
    rules = "\n".join(
        f"""    INSERT INTO penalty_rule (case_id, penalty_type, statutory_min, statutory_max, allowed_min, allowed_max,
                              allowed_basis, suspension_allowed, display_order)
    VALUES (v_case_id, {sql_text(r['penaltyType'])}, {sql_int(r.get('statutoryMin'))}, {sql_int(r.get('statutoryMax'))}, """
        f"""{sql_int(r['allowedMin'])}, {sql_int(r['allowedMax'])}, {sql_text(basis.get(r['penaltyType']))}, """
        f"""{'true' if r['suspensionAllowed'] else 'false'}, {i});"""
        for i, r in enumerate(case["penaltyRules"], start=1)
    )
    factors = "\n".join(
        f"""    INSERT INTO factor (case_id, label, pre_label, reveal_stage, summary_tag, value_axis, display_order)
    VALUES (v_case_id, {sql_text(f['label'])}, {sql_text(extras[f['factorId']].get('preLabel'))}, """
        f"""{sql_text(f['revealStage'])}, {sql_text(extras[f['factorId']]['summaryTag'])}, """
        f"""{sql_text(extras[f['factorId']].get('valueAxis'))}, {sql_int(f['factorId'])});"""
        for f in case["factors"]
    )
    source_rows = "\n".join(
        f"""    INSERT INTO case_source (case_id, court_level, case_number, court_name, decided_at, is_final, source_org,
                             original_text, note)
    VALUES (v_case_id, {sql_text(s['courtLevel'])}, {sql_text(s['caseNumber'])}, {sql_text(s.get('courtName'))}, """
        f"""{sql_text(s.get('decidedAt')) + '::date' if s.get('decidedAt') else 'NULL'}, {'true' if s['isFinal'] else 'false'}, """
        f"""{sql_text(source_org)}, {sql_text(s.get('originalText'))}, {sql_text(s.get('note') or source_note)});"""
        for s in sources
    )

    inner = f"""
DECLARE
    v_case_id bigint;
BEGIN
    -- 같은 제목의 사건이 DRAFT가 아니면(공개 · 검토 중) 다른 사건일 수 있으므로 멈춘다 (모델이 만든 중립 제목은 겹칠 수 있다)
    IF EXISTS (SELECT 1 FROM legal_case WHERE title = {title} AND status <> 'DRAFT') THEN
        RAISE EXCEPTION '같은 제목의 공개 · 검토 중 사건이 이미 있습니다. 제목을 바꾸거나 확인하세요 (title=%)', {title};
    END IF;
    -- 같은 제목의 DRAFT(이전 파이프라인 적재분)면 건너뛴다 (같은 SQL을 두 번 실행해도 안전)
    IF EXISTS (SELECT 1 FROM legal_case WHERE title = {title}) THEN
        RAISE NOTICE '사건 적재 건너뜀: 같은 제목의 DRAFT 사건이 이미 있습니다 (title=%)', {title};
        RETURN;
    END IF;

    -- DRAFT: 사용자에게 보이지 않는다. 관리자가 검수 · 재판부 판결 등록 뒤 PUBLISHED로 바꾼다
    INSERT INTO legal_case (
        title, crime_type, charge_name, short_intro, keywords, difficulty, estimated_minutes, overview,
        thumbnail_url, deidentified_items, applied_law, statutory_penalty_text,
        recommended_min_months, recommended_max_months, recommended_basis, guideline_id, incident_date,
        status, published_at
    ) VALUES (
        {title}, {sql_text(case['crimeType'])}, {sql_text(case['chargeName'])}, {sql_text(listing['shortIntro'])},
        {sql_jsonb(listing.get('keywords') or [])}, {sql_text(listing.get('difficulty'))}, {sql_int(listing.get('estimatedMinutes'))},
        {sql_text(case['overview'])},
        NULL, {sql_jsonb(report.get('deidentifiedItems') or [])}, {sql_text(case['appliedLaw'])}, {sql_text(case['statutoryPenaltyText'])},
        {sql_int(recommended.get('minMonths'))}, {sql_int(recommended.get('maxMonths'))}, {sql_text(recommended.get('basis'))}, NULL, {incident_sql},
        'DRAFT', NULL
    )
    RETURNING id INTO v_case_id;

{sections}

{rules}

{factors}

{source_rows}
END"""
    tag = dollar_quote_tag(inner)
    comment_title = " ".join(str(case["title"]).split())
    return f"""-- 사건 콘텐츠 적재 (DRAFT): {comment_title}
-- 같은 제목의 사건이 있으면 건너뛴다. 원본 판결문(case_source)이 들어 있으므로 공개 저장소에 두지 않는다
DO {tag}{inner} {tag};
"""


def main():
    parser = argparse.ArgumentParser(description="비식별화한 사건 파일을 사건 콘텐츠 적재 SQL(DRAFT)로 바꾼다")
    parser.add_argument("case_input")
    parser.add_argument("report")
    parser.add_argument("source_internal")
    parser.add_argument("--source-org", default=DEFAULT_SOURCE_ORG)
    parser.add_argument("--source-note", help="case_source.note (받은 경로 등)")
    parser.add_argument("--incident-date", help="사건 발생일 YYYY-MM-DD (주면 source_internal.json 값보다 우선)")
    parser.add_argument("--final-index", type=int, help="최종 확정 판결 번호(0부터). 없으면 심급이 가장 높은 판결")
    args = parser.parse_args()
    try:
        source = load_json(args.source_internal)
        sources = resolve_sources(source["sources"], final_index=args.final_index)
        sql = build_case_sql(load_json(args.case_input), load_json(args.report), sources,
                             source_org=args.source_org, source_note=args.source_note,
                             incident_date=args.incident_date or source.get("incidentDate"))
    except CaseSeedError as e:
        print(f"[오류] {e}", file=sys.stderr)
        return 1
    print(f"BEGIN;\n{sql}COMMIT;")
    return 0


if __name__ == "__main__":
    sys.exit(main())
