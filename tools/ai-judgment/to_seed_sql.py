"""검수를 통과한 AI 판결을 DB 적재용 SQL(PostgreSQL)로 바꾼다.

사용법:
    python3 to_seed_sql.py examples/case_input.json examples/ai_output_sample.json \\
        --model-name "모델 이름" --reviewed-by "검수자" [--reviewed-at 2026-10-06T00:00:00+09:00] \\
        [--factor-labels out/runs/<batch>/<모델>/run-NNN.factor-labels.json] \\
        [--accept-warnings] [--flyway] > out/ai_judgment.sql

- 만들기 전에 validate_output.py와 같은 검증을 다시 돌린다. 오류가 있으면 SQL을 만들지 않는다.
- 경고가 있으면 팀 검수에서 확인했다는 뜻으로 --accept-warnings를 붙여야 만든다 (REQ-078).
- 이미 공개된 AI 판결이 있으면 비공개로 바꾸고 새 판결을 공개한다. 기존 행은 지우지 않는다
  (ERD ai_generation 비고 · REQ-079, 공개 판결 부분 유니크 인덱스).
- 사건은 legal_case.title로, 판단 요소는 factor.display_order(사건 안에서만 유일, DB에
  UNIQUE(case_id, display_order) 제약 있음)로 찾는다. 환경마다 auto-increment id가 달라도
  (로컬은 가상 시드가 먼저 들어가 번호가 밀린다) 같은 SQL을 그대로 쓸 수 있다. 입력 파일의
  caseId · factorId를 시드 적재 후 DB id로 바꿔 적을 필요가 없다.
- 번호(display_order)만으로는 사건 파일과 시드의 요소 순서가 어긋나도(시드에서 요소 추가 · 삭제 ·
  순서 변경 등) 걸러지지 않으므로, factor.label까지 함께 맞는지 확인하고 다르면 멈춘다.
- 이 두 검사는 모두 "지금" 사건 파일 · DB 기준이라, 사건 파일과 DB가 함께(사이좋게) 바뀌면
  서로는 맞아떨어진다. 그 사이 판단 요소 구성이 바뀐 뒤의 **예전** AI 출력을 그대로 적재하면
  지금은 뜻이 달라진 같은 번호의 요소에 조용히 연결될 수 있다. --factor-labels에 generate.py가
  남긴 생성 시점 라벨 스냅샷(run-NNN.factor-labels.json)을 주면, 그때 라벨과 지금 사건 파일의
  라벨이 다른 요소가 있는지 먼저 확인해 다르면 멈춘다.
- title 조회는 STRICT로 하여, 제목이 없거나(NO_DATA_FOUND) legal_case.title UNIQUE 제약이
  없던 과거 데이터로 중복돼 있으면(TOO_MANY_ROWS) 각각 다른 메시지로 멈춘다.
- 기본 출력은 BEGIN; ~ COMMIT;으로 감싸 psql에 바로 붙여 넣을 수 있게 한다. Flyway(R__ 반복
  마이그레이션)에 그대로 쓸 때는 --flyway를 붙인다. Flyway가 마이그레이션마다 자체 트랜잭션으로
  감싸므로, 파일 안에 BEGIN/COMMIT이 또 있으면 그 트랜잭션이 중간에 끝나 적용 기록이 어긋날 수 있다.
- 감경해 형벌 종류가 바뀐 판결은 reduced_to에 함께 넣는다 (ERD v1.4).
- ai_generation.reviewed_at(팀 검수 시각, REQ-078 · 079)은 --reviewed-at으로 실제 검수 시각을 고정해 넣는다.
  --flyway(R__ 반복 마이그레이션)는 로컬 · 개발 · 운영에서 각각 다른 시각에 실행되므로 --reviewed-at이 필수다.
  psql 수동 실행용(기본)은 빼도 되지만 now()(SQL 실행 시각)가 검수 시각으로 남는다(경고를 낸다).
  현재보다 미래 시각이면 입력 실수일 수 있어 경고한다. created_at(적재 시각)은 now() 그대로 둔다.
"""

import argparse
import datetime
import json
import sys

from build_prompt import InputError, build_prompt
from common import factors_by_id, load_json
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


def dollar_quote_tag(text):
    """text 안에 나타나지 않는 dollar-quote 구분자를 고른다.

    사람이 쓴 reasoning · summary · title 등에 흔치 않은 '$sql_seed$' 같은 문자열이 그대로
    들어 있으면 DO 블록이 그 자리에서 조기 종료된다. 충돌하면 번호를 붙여 다시 고른다.
    """
    tag = "$sql_seed$"
    i = 0
    while tag in text:
        i += 1
        tag = f"$sql_seed{i}$"
    return tag


# 실제 세계 시간대 범위(UTC−12:00 ~ UTC+14:00). Python은 ±24시간 미만이면 받아들이지만
# PostgreSQL 숫자 오프셋은 약 ±15:59까지만 받으므로, 범위 밖 값은 SQL 적재 단계가 아니라 여기서 막는다
MIN_UTC_OFFSET = datetime.timedelta(hours=-12)
MAX_UTC_OFFSET = datetime.timedelta(hours=14)


def parse_reviewed_at(text):
    """--reviewed-at 값(ISO 8601, 시간대 필수) → 시간대가 있는 datetime.
    형식이 틀리거나, 시간대가 없거나, 시간대 오프셋이 UTC−12:00 ~ UTC+14:00 밖이면 ValueError."""
    value = text.strip()
    if value.endswith("Z"):  # Python 3.10 이하 fromisoformat은 Z를 읽지 못한다
        value = value[:-1] + "+00:00"
    try:
        parsed = datetime.datetime.fromisoformat(value)
    except ValueError:
        raise ValueError(f"--reviewed-at 형식이 올바르지 않습니다: {text!r} (예: 2026-10-06T00:00:00+09:00)") from None
    if parsed.tzinfo is None:
        raise ValueError(f"--reviewed-at에 시간대를 넣어야 합니다: {text!r} (예: 2026-10-06T00:00:00+09:00)")
    offset = parsed.utcoffset()
    if not MIN_UTC_OFFSET <= offset <= MAX_UTC_OFFSET:
        raise ValueError(f"--reviewed-at의 시간대 오프셋이 범위(UTC-12:00 ~ UTC+14:00)를 벗어났습니다: {text!r}")
    return parsed


def sql_reviewed_at(reviewed_at):
    """검수 시각 SQL 값. 없으면 now()(실행 시각)."""
    if reviewed_at is None:
        return "now()"
    return f"TIMESTAMPTZ '{reviewed_at.isoformat(sep=' ')}'"


def build_sql(case, output, prompt, model_name, reviewed_by, *, flyway=False, reviewed_at=None):
    raw_title = str(case.get("title", ""))
    title_literal = sql_text(raw_title)
    # 주석 줄은 제목에 줄바꿈이 있으면 거기서 끝나 뒤 내용이 SQL로 실행된다. 주석에만 공백 한 칸으로 합친
    # 값을 쓰고, 조회 · 오류 메시지에는 legal_case.title과 그대로 비교되는 원본 제목을 쓴다.
    comment_title = " ".join(raw_title.split())

    case_factors = factors_by_id(case)
    factor_items = sorted(
        (int(f["factorId"]), f["direction"], case_factors[int(f["factorId"])]["label"]) for f in output["factors"]
    )

    label_rows = ",\n            ".join(f"({order}, {sql_text(label)})" for order, _, label in factor_items)
    factor_check = (
        f"""
    IF EXISTS (
        SELECT 1 FROM (VALUES
            {label_rows}
        ) AS v(display_order, label)
        LEFT JOIN factor f ON f.case_id = v_case_id AND f.display_order = v.display_order
        WHERE f.id IS NULL OR f.label IS DISTINCT FROM v.label
    ) THEN
        RAISE EXCEPTION '판단 요소 번호 · 라벨이 DB와 다릅니다 (사건 파일과 시드의 요소 순서를 확인하세요, title=%)', {title_literal};
    END IF;
"""
        if factor_items
        else ""
    )

    direction_rows = ",\n        ".join(f"({order}, {sql_text(direction)})" for order, direction, _ in factor_items)
    factor_insert = (
        f"""
    INSERT INTO judgment_factor (judgment_id, factor_id, direction)
    SELECT v_judgment_id, f.id, v.direction
    FROM factor f
    JOIN (VALUES
        {direction_rows}
    ) AS v(display_order, direction) ON f.display_order = v.display_order
    WHERE f.case_id = v_case_id;
"""
        if factor_items
        else ""
    )

    input_snapshot = {"promptVersion": prompt["promptVersion"], "system": prompt["system"], "user": prompt["user"]}

    inner = f"""
DECLARE
    v_case_id     bigint;
    v_judgment_id bigint;
BEGIN
    BEGIN
        SELECT id INTO STRICT v_case_id FROM legal_case WHERE title = {title_literal};
    EXCEPTION
        WHEN NO_DATA_FOUND THEN
            RAISE EXCEPTION 'legal_case에서 title=%를 찾을 수 없습니다', {title_literal};
        WHEN TOO_MANY_ROWS THEN
            RAISE EXCEPTION 'legal_case에서 title=%가 유일하지 않습니다 (DB 확인 필요)', {title_literal};
    END;
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
        'APPROVED', {sql_text(reviewed_by)}, {sql_reviewed_at(reviewed_at)}, now()
    );
END"""

    tag = dollar_quote_tag(inner)
    body = f"""-- AI 판결 적재: {comment_title} (prompt={prompt['promptVersion']})
-- 사건은 legal_case.title, 판단 요소는 factor.display_order · label로 찾는다 (환경마다 id가 달라도 같은 SQL을 쓴다)
DO {tag}{inner} {tag};
"""

    if flyway:
        return body
    return f"BEGIN;\n{body}\nCOMMIT;\n"


def check_factor_label_drift(case, output, factor_labels_path):
    """생성 시점 라벨 스냅샷과 지금 사건 파일의 라벨이 다른 요소가 있으면 오류 메시지 목록을 돌려준다.

    factorId · display_order가 그대로여도, 그 사이 사건 파일(과 DB)에서 요소의 label이 바뀌었다면
    이 출력은 지금과 다른 판단 요소 구성으로 생성된 것이다. 사건 파일 · DB가 함께 바뀌면 서로는
    맞아떨어지므로 build_sql의 DB 대조만으로는 잡히지 않는다.
    """
    if factor_labels_path is None:
        return []
    snapshot = load_json(factor_labels_path)
    case_factors = factors_by_id(case)
    errors = []
    for factor in output.get("factors") or []:
        factor_id = factor.get("factorId") if isinstance(factor, dict) else None
        if factor_id is None:
            continue
        key = str(factor_id)
        if key not in snapshot:
            continue
        current_label = case_factors.get(int(factor_id), {}).get("label")
        snapshot_label = snapshot[key]
        if snapshot_label != current_label:
            errors.append(
                f"factorId={factor_id}: 생성 시점 라벨({snapshot_label!r})과 지금 사건 파일의 라벨"
                f"({current_label!r})이 다릅니다. 판단 요소 구성이 바뀐 뒤의 이전 출력일 수 있습니다"
            )
    return errors


def main():
    parser = argparse.ArgumentParser(description="검수 통과한 AI 판결을 적재 SQL로 바꾼다")
    parser.add_argument("case_input")
    parser.add_argument("ai_output")
    parser.add_argument("--model-name", required=True, help="생성에 쓴 모델 이름 (ai_generation.model_name)")
    parser.add_argument("--reviewed-by", required=True, help="검수자 이름 (ai_generation.reviewed_by)")
    parser.add_argument(
        "--factor-labels",
        help="generate.py가 남긴 생성 시점 판단 요소 라벨 스냅샷 (run-NNN.factor-labels.json). "
        "주면 그때 라벨과 지금 사건 파일의 라벨이 다른 요소가 있는지 먼저 확인한다",
    )
    parser.add_argument(
        "--reviewed-at",
        help="팀 검수 시각, ISO 8601 · 시간대 필수 (예: 2026-10-06T00:00:00+09:00). "
        "ai_generation.reviewed_at에 고정값으로 넣는다. --flyway에는 필수, 그 밖에는 빼면 SQL 실행 시각(now())이 남는다",
    )
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

    reviewed_at = None
    if args.reviewed_at is not None:
        try:
            reviewed_at = parse_reviewed_at(args.reviewed_at)
        except ValueError as e:
            print(f"[오류] {e}", file=sys.stderr)
            return 1
        # 검수 시각이 현재보다 미래면 입력 실수일 가능성이 높다 (막지는 않는다)
        if reviewed_at > datetime.datetime.now(datetime.timezone.utc):
            print(f"[경고] --reviewed-at이 현재보다 미래입니다: {reviewed_at.isoformat()}", file=sys.stderr)
    elif args.flyway:
        # Flyway 파일은 여러 환경에서 다른 시각에 실행되므로 검수 시각을 반드시 고정한다
        print("[오류] --flyway에는 --reviewed-at이 필요합니다 (환경마다 실행 시각이 달라 검수 시각이 어긋납니다)",
              file=sys.stderr)
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

    drift_errors = check_factor_label_drift(case, output, args.factor_labels)
    if drift_errors:
        print("\n".join(f"[ERROR] {e}" for e in drift_errors), file=sys.stderr)
        print("[중단] 판단 요소 라벨이 생성 시점과 달라 SQL을 만들지 않았습니다", file=sys.stderr)
        return 1

    if reviewed_at is None:
        print("[경고] --reviewed-at이 없어 ai_generation.reviewed_at에 now()(SQL 실행 시각)가 들어갑니다. "
              "실제 검수 시각을 넣으려면 --reviewed-at을 붙이세요", file=sys.stderr)
    print(build_sql(case, output, prompt, args.model_name, args.reviewed_by, flyway=args.flyway, reviewed_at=reviewed_at))
    return 0


if __name__ == "__main__":
    sys.exit(main())
