"""검수를 통과한 AI 판결을 DB 적재용 SQL(PostgreSQL)로 바꾼다.

사용법:
    python3 to_seed_sql.py examples/case_input.json examples/ai_output_sample.json \\
        --model-name "모델 이름" --reviewed-by "검수자" [--accept-warnings] [--flyway] > out/ai_judgment.sql

- 만들기 전에 validate_output.py와 같은 검증을 다시 돌린다. 오류가 있으면 SQL을 만들지 않는다.
- 경고가 있으면 팀 검수에서 확인했다는 뜻으로 --accept-warnings를 붙여야 만든다 (REQ-078).
- 이미 공개된 AI 판결이 있으면 비공개로 바꾸고 새 판결을 공개한다. 기존 행은 지우지 않는다
  (ERD ai_generation 비고 · REQ-079, 공개 판결 부분 유니크 인덱스).
- 사건은 legal_case.title로, 판단 요소는 factor.display_order(사건 안에서만 유일)로 찾는다. 환경마다
  auto-increment id가 달라도(로컬은 가상 시드가 먼저 들어가 번호가 밀린다) 같은 SQL을 그대로 쓸 수 있다.
  입력 파일의 caseId · factorId를 시드 적재 후 DB id로 바꿔 적을 필요가 없다. 요소가 이 사건 소속이
  아니면(display_order가 없으면) SQL이 멈춘다.
- 기본 출력은 BEGIN; ~ COMMIT;으로 감싸 psql에 바로 붙여 넣을 수 있게 한다. Flyway(R__ 반복 마이그레이션)에
  그대로 쓸 때는 --flyway를 붙인다. Flyway가 마이그레이션마다 자체 트랜잭션으로 감싸므로, 파일 안에
  BEGIN/COMMIT이 또 있으면 그 트랜잭션이 중간에 끝나 적용 기록이 어긋날 수 있다.
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


def build_sql(case, output, prompt, model_name, reviewed_by, *, flyway=False):
    # 제목에 줄바꿈이 있으면 주석이 끝나 뒤 내용이 SQL로 실행된다. 공백을 한 칸으로 합친다.
    title = " ".join(str(case.get("title", "")).split())
    title_literal = sql_text(title)

    display_orders = sorted({int(f["factorId"]) for f in output["factors"]})
    factor_check = (
        f"""
    IF (SELECT count(*) FROM factor WHERE case_id = v_case_id AND display_order IN ({", ".join(map(str, display_orders))})) <> {len(display_orders)} THEN
        RAISE EXCEPTION '판단 요소 번호(factorId)가 사건(title=%) 소속이 아닙니다. 사건 입력 파일의 factorId를 확인하세요', {title_literal};
    END IF;
"""
        if display_orders
        else ""
    )

    factor_rows = ",\n        ".join(
        f"({sql_int(f['factorId'])}, {sql_text(f['direction'])})" for f in output["factors"]
    )
    factor_insert = (
        f"""
    INSERT INTO judgment_factor (judgment_id, factor_id, direction)
    SELECT v_judgment_id, f.id, v.direction
    FROM factor f
    JOIN (VALUES
        {factor_rows}
    ) AS v(display_order, direction) ON f.display_order = v.display_order
    WHERE f.case_id = v_case_id;
"""
        if output["factors"]
        else ""
    )

    input_snapshot = {"promptVersion": prompt["promptVersion"], "system": prompt["system"], "user": prompt["user"]}

    body = f"""-- AI 판결 적재: {title} (prompt={prompt['promptVersion']})
-- 사건은 legal_case.title, 판단 요소는 factor.display_order로 찾는다 (환경마다 id가 달라도 같은 SQL을 쓴다)
DO $$
DECLARE
    v_case_id     bigint;
    v_judgment_id bigint;
BEGIN
    SELECT id INTO v_case_id FROM legal_case WHERE title = {title_literal};
    IF v_case_id IS NULL THEN
        RAISE EXCEPTION 'legal_case에서 title=%를 찾을 수 없습니다', {title_literal};
    END IF;
{factor_check}
    -- 기존 공개 AI 판결은 비공개로 돌린다 (행은 지우지 않음)
    UPDATE judgment
    SET is_published = false
    WHERE case_id = v_case_id AND subject_type = 'AI' AND is_published = true;

    INSERT INTO judgment (
        case_id, subject_type, timing,
        penalty_type, reduced_to, prison_months, fine_amount, suspension_months,
        extra_dispositions, reasoning, summary, reference_tags,
        is_published, created_at
    ) VALUES (
        v_case_id, 'AI', 'FINAL',
        {sql_text(output['penaltyType'])}, {sql_text(output.get('reducedTo'))}, {sql_int(output.get('prisonMonths'))}, {sql_int(output.get('fineAmount'))}, {sql_int(output.get('suspensionMonths'))},
        '[]'::jsonb, {sql_text(output['reasoning'])}, {sql_text(output['summary'])}, {sql_jsonb(output['referenceTags'])},
        true, now()
    )
    RETURNING id INTO v_judgment_id;
{factor_insert}
    INSERT INTO ai_generation (
        judgment_id, model_name, prompt_version,
        input_snapshot, raw_output,
        review_status, reviewed_by, reviewed_at, created_at
    ) VALUES (
        v_judgment_id, {sql_text(model_name)}, {sql_text(prompt['promptVersion'])},
        {sql_jsonb(input_snapshot)}, {sql_jsonb(output)},
        'APPROVED', {sql_text(reviewed_by)}, now(), now()
    );
END $$;
"""

    if flyway:
        return body
    return f"BEGIN;\n{body}\nCOMMIT;\n"


def main():
    parser = argparse.ArgumentParser(description="검수 통과한 AI 판결을 적재 SQL로 바꾼다")
    parser.add_argument("case_input")
    parser.add_argument("ai_output")
    parser.add_argument("--model-name", required=True, help="생성에 쓴 모델 이름 (ai_generation.model_name)")
    parser.add_argument("--reviewed-by", required=True, help="검수자 이름 (ai_generation.reviewed_by)")
    parser.add_argument("--accept-warnings", action="store_true", help="경고를 팀 검수에서 확인했음")
    parser.add_argument(
        "--flyway",
        action="store_true",
        help="BEGIN/COMMIT 없이 출력한다 (Flyway R__ 반복 마이그레이션에 그대로 쓸 때). "
        "기본값은 BEGIN/COMMIT을 포함해 psql에 바로 붙여 넣을 수 있게 한다",
    )
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

    print(build_sql(case, output, prompt, args.model_name, args.reviewed_by, flyway=args.flyway))
    return 0


if __name__ == "__main__":
    sys.exit(main())
