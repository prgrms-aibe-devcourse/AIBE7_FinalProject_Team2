"""검수를 통과한 AI 판결을 DB 적재용 SQL(PostgreSQL)로 바꾼다.

사용법:
    python3 to_seed_sql.py examples/case_input.json examples/ai_output_sample.json \\
        --model-name "모델 이름" --reviewed-by "검수자" [--accept-warnings] > out/ai_judgment.sql

- 만들기 전에 validate_output.py와 같은 검증을 다시 돌린다. 오류가 있으면 SQL을 만들지 않는다.
- 경고가 있으면 팀 검수에서 확인했다는 뜻으로 --accept-warnings를 붙여야 만든다 (REQ-078).
- 이미 공개된 AI 판결이 있으면 비공개로 바꾸고 새 판결을 공개한다. 기존 행은 지우지 않는다
  (ERD ai_generation 비고 · REQ-079, 공개 판결 부분 유니크 인덱스).
- 사건 입력의 caseId · factorId는 DB의 legal_case.id · factor.id와 같아야 한다. 시드는 ID를 지정하지 않고 넣으므로
  (ERD 7장), 시드 적재 후 DB에서 조회한 값을 입력 파일에 적는다. 요소가 이 사건 소속이 아니면 SQL이 멈춘다.
- 감경해 형벌 종류가 바뀐 판결은 reduced_to에 함께 넣는다 (ERD v1.4).
"""

import argparse
import json
import sys

from build_prompt import InputError, build_prompt
from common import load_json
from validate_output import parse_output, validate

NAME_MAX_LENGTH = 50  # ai_generation.model_name · reviewed_by varchar(50)


def sql_text(value):
    if value is None:
        return "NULL"
    return "'" + str(value).replace("'", "''") + "'"


def sql_int(value):
    return "NULL" if value is None else str(int(value))


def sql_jsonb(value):
    return sql_text(json.dumps(value, ensure_ascii=False)) + "::jsonb"


def build_sql(case, output, prompt, model_name, reviewed_by):
    case_id = sql_int(case["caseId"])
    factor_rows = ",\n        ".join(
        f"({sql_int(f['factorId'])}, {sql_text(f['direction'])})" for f in output["factors"]
    )
    factor_insert = (
        f""",
factors AS (
    INSERT INTO judgment_factor (judgment_id, factor_id, direction)
    SELECT new_judgment.id, f.factor_id, f.direction
    FROM new_judgment, (VALUES
        {factor_rows}
    ) AS f(factor_id, direction)
)"""
        if output["factors"]
        else ""
    )
    input_snapshot = {"promptVersion": prompt["promptVersion"], "system": prompt["system"], "user": prompt["user"]}
    # 제목에 줄바꿈이 있으면 주석이 끝나 뒤 내용이 SQL로 실행된다. 공백을 한 칸으로 합친다.
    title = " ".join(str(case.get("title", "")).split())
    factor_ids = sorted({int(f["factorId"]) for f in output["factors"]})
    factor_check = (
        f"""
-- 판단 요소가 모두 이 사건 소속인지 확인한다 (다른 사건 요소면 외래 키만으로는 막히지 않는다)
DO $$
BEGIN
    IF (SELECT count(*) FROM factor WHERE case_id = {case_id} AND id IN ({", ".join(map(str, factor_ids))})) <> {len(factor_ids)} THEN
        RAISE EXCEPTION '판단 요소 id가 caseId={case_id} 사건 소속이 아닙니다. 사건 입력 파일의 id를 DB 값으로 확인하세요';
    END IF;
END $$;
"""
        if factor_ids
        else ""
    )

    return f"""-- AI 판결 적재: {title} (caseId={case_id}, prompt={prompt['promptVersion']})
BEGIN;
{factor_check}
-- 기존 공개 AI 판결은 비공개로 돌린다 (행은 지우지 않음)
UPDATE judgment
SET is_published = false
WHERE case_id = {case_id} AND subject_type = 'AI' AND is_published = true;

WITH new_judgment AS (
    INSERT INTO judgment (
        case_id, subject_type, timing,
        penalty_type, reduced_to, prison_months, fine_amount, suspension_months,
        extra_dispositions, reasoning, summary, reference_tags,
        is_published, created_at
    ) VALUES (
        {case_id}, 'AI', 'FINAL',
        {sql_text(output['penaltyType'])}, {sql_text(output.get('reducedTo'))}, {sql_int(output.get('prisonMonths'))}, {sql_int(output.get('fineAmount'))}, {sql_int(output.get('suspensionMonths'))},
        '[]'::jsonb, {sql_text(output['reasoning'])}, {sql_text(output['summary'])}, {sql_jsonb(output['referenceTags'])},
        true, now()
    )
    RETURNING id
){factor_insert}
INSERT INTO ai_generation (
    judgment_id, model_name, prompt_version,
    input_snapshot, raw_output,
    review_status, reviewed_by, reviewed_at, created_at
)
SELECT new_judgment.id, {sql_text(model_name)}, {sql_text(prompt['promptVersion'])},
       {sql_jsonb(input_snapshot)}, {sql_jsonb(output)},
       'APPROVED', {sql_text(reviewed_by)}, now(), now()
FROM new_judgment;

COMMIT;
"""


def main():
    parser = argparse.ArgumentParser(description="검수 통과한 AI 판결을 적재 SQL로 바꾼다")
    parser.add_argument("case_input")
    parser.add_argument("ai_output")
    parser.add_argument("--model-name", required=True, help="생성에 쓴 모델 이름 (ai_generation.model_name)")
    parser.add_argument("--reviewed-by", required=True, help="검수자 이름 (ai_generation.reviewed_by)")
    parser.add_argument("--accept-warnings", action="store_true", help="경고를 팀 검수에서 확인했음")
    args = parser.parse_args()

    for label, value in (("--model-name", args.model_name), ("--reviewed-by", args.reviewed_by)):
        if not value.strip() or len(value) > NAME_MAX_LENGTH:
            print(f"[오류] {label}는 1 ~ {NAME_MAX_LENGTH}자여야 합니다 (ai_generation varchar(50))", file=sys.stderr)
            return 1

    case = load_json(args.case_input)
    with open(args.ai_output, encoding="utf-8") as f:
        output = parse_output(f.read())

    try:
        prompt = build_prompt(case)
    except InputError as e:
        print(f"[오류] {e}", file=sys.stderr)
        return 1

    report = validate(case, output)
    if not report.ok:
        print(report.to_text(), file=sys.stderr)
        print("[중단] 검증 오류가 있어 SQL을 만들지 않았습니다", file=sys.stderr)
        return 1
    if report.warnings and not args.accept_warnings:
        print(report.to_text(), file=sys.stderr)
        print("[중단] 경고가 있습니다. 팀 검수 후 --accept-warnings를 붙여 다시 실행하세요", file=sys.stderr)
        return 1

    print(build_sql(case, output, prompt, args.model_name, args.reviewed_by))
    return 0


if __name__ == "__main__":
    sys.exit(main())
