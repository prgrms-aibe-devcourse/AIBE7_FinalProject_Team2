"""로컬 DB의 사건 → AI 판결 생성용 사건 입력 JSON (BE-76).

RAG 생성 비교 시험(docs/rag-handover.md 5-7)처럼, 이미 DB에 적재된 사건으로 다시 생성해 볼 때 쓴다.
LLM을 부르지 않고 로컬 DB만 읽는다(stages.load.db와 같은 접속 규칙, 원격 DB 거부).

만드는 파일 (모두 cases/ 아래, git 제외 — 실제 사건 내용 · 사건번호가 들어 있다):
  <name>.case.json             사건 입력 (build_prompt 형식). 참고 자료(references)는 비워 둔다 — RAG를 켜면 rag 단계가 채운다
  <name>.source_internal.json  원본 판결문 정보(사건번호 · 법원 · 선고일 · 심급)와 발생일. rag 단계가 자기 사건을 빼는 데 쓴다
  --pipeline을 주면 <name>-rag-off.pipeline.json · <name>-rag-on.pipeline.json (extract · court 끄고 위 두 파일을 입력으로)

판단 요소 번호(factorId)는 factor.display_order다(적재 SQL과 같은 기준). 섹션은 용어 설명(LAW_TERM) · 핵심 정리(SUMMARY)를
빼고 넣는다(생성 입력에 쓰지 않는 화면용 섹션). 원본 판결문 원문은 넣지 않는다.

사용법 (tools/ai-judgment에서, 로컬 DB는 bootRun으로 시드가 들어간 상태):
    python3 db_case_input.py <사건 id> --name my-case
    python3 db_case_input.py <사건 id> --name my-case --pipeline --model gemini:<모델ID> --runs 3
"""

import argparse
import sys

import pipeline
import rag_check
from build_prompt import InputError, check_case_input
from common import write_json

EXCLUDED_SECTIONS = ("LAW_TERM", "SUMMARY")
NAME_PATTERN_HELP = "영문 소문자 · 숫자 · 하이픈 (파일 이름에 쓴다. 사건을 특정할 수 있는 이름은 쓰지 않는다)"


class CaseInputError(Exception):
    pass


def case_sql(case_id):
    excluded = ", ".join(f"'{t}'" for t in EXCLUDED_SECTIONS)
    return f"""SELECT json_build_object(
  'caseId', c.id, 'title', c.title, 'crimeType', c.crime_type, 'chargeName', c.charge_name,
  'appliedLaw', c.applied_law, 'statutoryPenaltyText', c.statutory_penalty_text, 'overview', c.overview,
  'incidentDate', c.incident_date,
  'recommended', CASE WHEN c.recommended_min_months IS NULL THEN NULL ELSE json_build_object(
      'minMonths', c.recommended_min_months, 'maxMonths', c.recommended_max_months, 'basis', c.recommended_basis) END,
  'sections', (SELECT coalesce(json_agg(json_build_object('stage', s.stage, 'sectionType', s.section_type,
                 'title', s.title, 'content', s.content, 'data', s.data) ORDER BY s.display_order), '[]'::json)
               FROM case_section s WHERE s.case_id = c.id AND s.section_type NOT IN ({excluded})),
  'penaltyRules', (SELECT coalesce(json_agg(json_build_object('penaltyType', r.penalty_type,
                 'statutoryMin', r.statutory_min, 'statutoryMax', r.statutory_max, 'allowedMin', r.allowed_min,
                 'allowedMax', r.allowed_max, 'suspensionAllowed', r.suspension_allowed) ORDER BY r.display_order), '[]'::json)
               FROM penalty_rule r WHERE r.case_id = c.id),
  'factors', (SELECT coalesce(json_agg(json_build_object('factorId', f.display_order, 'label', f.label,
                 'revealStage', f.reveal_stage) ORDER BY f.display_order), '[]'::json)
               FROM factor f WHERE f.case_id = c.id),
  'sources', (SELECT coalesce(json_agg(json_build_object('caseNumber', cs.case_number, 'courtName', cs.court_name,
                 'decidedAt', cs.decided_at, 'courtLevel', cs.court_level, 'isFinal', cs.is_final) ORDER BY cs.id), '[]'::json)
               FROM case_source cs WHERE cs.case_id = c.id))
FROM legal_case c WHERE c.id = {int(case_id)};"""


def strip_empty(item):
    """섹션: content · data 중 비어 있는 쪽은 뺀다 (build_prompt는 data가 있으면 data를 쓴다)."""
    return {k: v for k, v in item.items() if v is not None}


def build(row):
    """DB 행 → (사건 입력, 원본 판결문 정보). 형식이 맞지 않으면 CaseInputError."""
    if not row:
        raise CaseInputError("사건이 없습니다")
    case = {
        "_comment": "로컬 DB에서 뽑은 사건 입력 (db_case_input.py, BE-76). 실제 사건 내용이라 cases/에만 둔다. "
                    "참고 자료는 비워 두었다 — RAG를 켜면 rag 단계가 채운다.",
        "caseId": row["caseId"],
        "title": row["title"],
        "crimeType": row["crimeType"],
        "chargeName": row["chargeName"],
        "appliedLaw": row["appliedLaw"],
        "statutoryPenaltyText": row["statutoryPenaltyText"],
        "overview": row["overview"],
        "sections": [strip_empty(s) for s in row["sections"]],
        "penaltyRules": row["penaltyRules"],
        "recommended": row["recommended"],
        "factors": row["factors"],
        "references": {},
    }
    if not case["recommended"]:
        del case["recommended"]
    try:
        check_case_input(case)
    except InputError as e:
        raise CaseInputError(f"생성 입력으로 쓸 수 없습니다: {e}")
    source = {
        "_comment": "원본 판결문 정보 (db_case_input.py, BE-76). 사건번호가 들어 있어 AI에 보내지 않는다. "
                    "rag 단계가 자기 사건을 빼는 해시에만 쓴다.",
        "sources": row["sources"],
        "incidentDate": row["incidentDate"],
    }
    return case, source


def pipeline_config(name, rag, model, runs):
    return {
        "_comment": f"RAG {'켬' if rag else '끔'} 생성 비교 시험 (BE-76, rag-handover 5-7). db_case_input.py가 만들었다. "
                    "extract · court는 끄고 DB에서 뽑은 사건 입력을 쓴다. --until select로 돌려 DB에 적재하지 않는다.",
        "name": f"{name}-rag-{'on' if rag else 'off'}",
        "sources": [],
        "inputs": {"case": f"{name}.case.json", "source": f"{name}.source_internal.json"},
        "stages": {
            "extract": {"enabled": False},
            "court": {"enabled": False},
            "contamination": {"enabled": False},
            "rag": {"enabled": rag},
            "generate": {"models": [model], "runs": runs, "maxRetries": 0, "timeout": 600},  # 사고 모델은 한 번에 3분 넘게 걸리기도 한다(10/15)
            "axis": {"enabled": False},
            "load": {"applyToDb": False},
        },
    }


def check_name(name):
    if not name or not name.isascii() or not all(ch.islower() or ch.isdigit() or ch == "-" for ch in name) or not name[0].isalnum():
        raise CaseInputError(f"--name은 {NAME_PATTERN_HELP}")


def main(argv=None):
    parser = argparse.ArgumentParser(description="로컬 DB 사건 → AI 판결 생성용 사건 입력 (BE-76)")
    parser.add_argument("case_id", type=int)
    parser.add_argument("--name", required=True, help=NAME_PATTERN_HELP)
    parser.add_argument("--pipeline", action="store_true", help="RAG 끔 · 켬 파이프라인 설정 두 개도 만든다")
    parser.add_argument("--model", help="--pipeline일 때 생성 모델 (공급자:모델ID)")
    parser.add_argument("--runs", type=int, default=3, help="--pipeline일 때 모델당 생성 횟수 (기본 3)")
    rag_check.add_db_arguments(parser)
    args = parser.parse_args(argv)
    try:
        check_name(args.name)
        if args.pipeline and not args.model:
            raise CaseInputError("--pipeline에는 --model이 필요합니다")
        if args.runs < 1:
            raise CaseInputError("--runs는 1 이상입니다")
        case, source = build(rag_check.json_row(case_sql(args.case_id), rag_check.db_config(args)))
    except (CaseInputError, rag_check.rag_context.RagUnavailable, pipeline.PipelineError) as e:
        print(f"[오류] {e}", file=sys.stderr)
        return 1
    out = pipeline.CASES_DIR
    out.mkdir(exist_ok=True)
    written = [out / f"{args.name}.case.json", out / f"{args.name}.source_internal.json"]
    write_json(written[0], case)
    write_json(written[1], source)
    if args.pipeline:
        for rag in (False, True):
            path = out / f"{args.name}-rag-{'on' if rag else 'off'}.pipeline.json"
            write_json(path, pipeline_config(args.name, rag, args.model, args.runs))
            written.append(path)
    print(f"사건 {args.case_id}: 섹션 {len(case['sections'])}개 · 형벌 규칙 {len(case['penaltyRules'])}개 · "
          f"판단 요소 {len(case['factors'])}개 · 원본 판결문 {len(source['sources'])}건")
    for path in written:
        print(f"  → {path.relative_to(pipeline.TOOL_DIR)}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
