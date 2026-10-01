"""AI가 낸 판결 JSON을 사건 입력 기준으로 검증한다.

사용법:
    python3 validate_output.py examples/case_input.json examples/ai_output_sample.json

- ERROR: 서비스에 올릴 수 없다. 프롬프트 · 자료를 보완해 다시 생성한다.
- WARN: 자동으로 막지는 않지만 팀 검수에서 반드시 확인한다 (REQ-078).
오류가 하나라도 있으면 종료 코드 1을 돌려준다.
"""

import argparse
import json
import re
import sys

from common import (
    DIRECTIONS,
    NO_TERM_PENALTIES,
    PENALTY_NAMES,
    PENALTY_TYPES,
    REDUCIBLE_TO,
    SUMMARY_MAX_LENGTH,
    SUSPENSION_MAX_FINE_AMOUNT,
    SUSPENSION_MAX_MONTHS,
    SUSPENSION_MAX_PRISON_MONTHS,
    SUSPENSION_MIN_MONTHS,
    factors_by_id,
    final_penalty,
    format_penalty_range,
    load_json,
    rules_by_type,
)

OUTPUT_KEYS = {
    "penaltyType",
    "reducedTo",
    "prisonMonths",
    "fineAmount",
    "suspensionMonths",
    "factors",
    "reasoning",
    "summary",
    "referenceTags",
}

# 판결 이유 · 요약에 쓰지 않는 평가 표현 (요구사항 11장 톤 원칙)
FORBIDDEN_EXPRESSIONS = ("정답", "오답", "옳다", "옳은", "틀렸", "틀린", "이중 잣대", "점수")

# 판결 이유에 나온 숫자가 입력에 없으면 "없는 사실을 만든 것"일 수 있다 (REQ-078)
NUMBER_PATTERN = re.compile(r"\d[\d,]*(?:\.\d+)?\s*(?:억|만|천)?\s*(?:원|명|회|년|개월|건|%)")


class Report:
    def __init__(self):
        self.errors = []
        self.warnings = []

    def error(self, message):
        self.errors.append(message)

    def warn(self, message):
        self.warnings.append(message)

    @property
    def ok(self):
        return not self.errors

    def to_text(self):
        lines = [f"[ERROR] {m}" for m in self.errors] + [f"[WARN] {m}" for m in self.warnings]
        verdict = "통과" if self.ok else "실패"
        lines.append(f"결과: {verdict} (오류 {len(self.errors)}, 경고 {len(self.warnings)})")
        return "\n".join(lines)


def parse_output(text):
    """모델 응답 문자열에서 JSON을 꺼낸다. 코드 블록(```)으로 감싸 온 경우도 받아 준다."""
    stripped = text.strip()
    fence = re.match(r"^```(?:json)?\s*(.*?)\s*```$", stripped, re.S)
    if fence:
        stripped = fence.group(1)
    return json.loads(stripped)


def _is_int(value):
    return isinstance(value, int) and not isinstance(value, bool)


def check_keys(output, report):
    missing = OUTPUT_KEYS - output.keys()
    extra = output.keys() - OUTPUT_KEYS
    if missing:
        report.error("빠진 항목: " + ", ".join(sorted(missing)))
    if extra:
        report.warn("정의되지 않은 항목 (적재 시 무시됨): " + ", ".join(sorted(extra)))


def check_penalty(case, output, report):
    """형벌 · 감경 · 형량 · 집행유예를 검사한다 (API 9 판결 제출 규칙과 같은 기준, ERD v1.4)."""
    penalty_type = output.get("penaltyType")
    reduced_to = output.get("reducedTo")
    rules = rules_by_type(case)
    if penalty_type not in PENALTY_TYPES:
        report.error(f"penaltyType은 {' / '.join(PENALTY_TYPES)} 중 하나여야 합니다: {penalty_type!r}")
        return
    rule = rules.get(penalty_type)
    if rule is None:
        report.error(f"이 사건에서 허용되지 않은 형벌입니다: {penalty_type}")
        return
    if reduced_to is not None and reduced_to not in REDUCIBLE_TO[penalty_type]:
        allowed = " · ".join(REDUCIBLE_TO[penalty_type]) or "없음 (null이어야 함)"
        report.error(f"{penalty_type}은 {reduced_to!r}로 감경할 수 없습니다 (가능: {allowed})")
        return

    final = final_penalty(output)
    suspension = output.get("suspensionMonths")
    if final in NO_TERM_PENALTIES:
        for key in ("prisonMonths", "fineAmount", "suspensionMonths"):
            if output.get(key) is not None:
                report.error(f"최종 선고 형벌이 {final}({PENALTY_NAMES[final]})이면 {key}는 null이어야 합니다")
        check_recommended(case, final, None, report)
        return

    value_key, other_key = ("prisonMonths", "fineAmount") if final == "PRISON" else ("fineAmount", "prisonMonths")
    value = output.get(value_key)
    if not _is_int(value):
        report.error(f"최종 선고 형벌이 {final}이면 {value_key}는 정수여야 합니다: {value!r}")
    elif not rule["allowedMin"] <= value <= rule["allowedMax"]:
        report.error(f"{value_key}={value}가 {penalty_type}의 선고할 수 있는 범위({format_penalty_range(rule)}) 밖입니다")
    if output.get(other_key) is not None:
        report.error(f"최종 선고 형벌이 {final}이면 {other_key}는 null이어야 합니다")

    check_suspension(rule, penalty_type, final, value, suspension, report)
    check_recommended(case, final, value, report)


def check_suspension(rule, penalty_type, final, value, suspension, report):
    if suspension is None:
        return
    if not _is_int(suspension):
        report.error(f"suspensionMonths는 정수 또는 null이어야 합니다: {suspension!r}")
        return
    if penalty_type in NO_TERM_PENALTIES:
        report.error(f"{PENALTY_NAMES[penalty_type]}을 고르면 감경하더라도 집행유예를 붙일 수 없습니다")
        return
    if not rule.get("suspensionAllowed"):
        report.error(f"이 사건의 {penalty_type}에는 집행유예를 붙일 수 없습니다")
    if not SUSPENSION_MIN_MONTHS <= suspension <= SUSPENSION_MAX_MONTHS:
        report.error(f"집행유예 기간은 {SUSPENSION_MIN_MONTHS} ~ {SUSPENSION_MAX_MONTHS}개월이어야 합니다: {suspension}")
    if _is_int(value):
        if final == "PRISON" and value > SUSPENSION_MAX_PRISON_MONTHS:
            report.error(f"징역 {value}개월에는 집행유예를 붙일 수 없습니다 (3년 이하만 가능)")
        if final == "FINE" and value > SUSPENSION_MAX_FINE_AMOUNT:
            report.error(f"벌금 {value}원에는 집행유예를 붙일 수 없습니다 (500만 원 이하만 가능)")


def check_recommended(case, final, value, report):
    """권고 형량 범위(징역 개월) 밖이면 경고한다. 사형 · 무기는 권고 범위보다 무거운 것으로 본다."""
    recommended = case.get("recommended")
    if not recommended:
        return
    range_text = f"{recommended['minMonths']} ~ {recommended['maxMonths']}개월"
    if final in NO_TERM_PENALTIES:
        report.warn(
            f"최종 선고 형벌이 {PENALTY_NAMES[final]}으로 권고 범위(징역 {range_text})보다 무겁습니다."
            " 판결 이유에 까닭이 있는지 검수하세요"
        )
        return
    if final != "PRISON" or not _is_int(value):
        return
    if not recommended["minMonths"] <= value <= recommended["maxMonths"]:
        report.warn(f"징역 {value}개월이 권고 범위({range_text}) 밖입니다. 판결 이유에 까닭이 있는지 검수하세요")


def check_factors(case, output, report):
    factors = output.get("factors")
    if not isinstance(factors, list):
        report.error("factors는 배열이어야 합니다")
        return
    if not factors:
        report.warn("고려한 판단 요소가 하나도 없습니다. S-09 비교가 비어 보입니다")
    known = factors_by_id(case)
    seen = set()
    for i, factor in enumerate(factors):
        factor_id = factor.get("factorId") if isinstance(factor, dict) else None
        if factor_id not in known:
            report.error(f"factors[{i}]: 목록에 없는 판단 요소입니다 (factorId={factor_id!r})")
            continue
        if factor_id in seen:
            report.error(f"factors[{i}]: 같은 판단 요소가 중복됐습니다 (factorId={factor_id})")
        seen.add(factor_id)
        if factor.get("direction") not in DIRECTIONS:
            report.error(f"factors[{i}]: direction은 UP / DOWN이어야 합니다 (factorId={factor_id})")
        reason = factor.get("reason", "")
        if not isinstance(reason, str):
            report.error(f"factors[{i}]: reason은 문자열이어야 합니다 (factorId={factor_id})")
        elif not reason.strip():
            report.warn(f"factors[{i}]: 이유(reason)가 비어 있습니다 (factorId={factor_id})")


def check_texts(output, report):
    reasoning = output.get("reasoning")
    summary = output.get("summary")
    if not isinstance(reasoning, str) or not reasoning.strip():
        report.error("reasoning(판결 이유)이 비어 있습니다")
    if not isinstance(summary, str) or not summary.strip():
        report.error("summary(한 줄 요약)가 비어 있습니다")
    elif len(summary) > SUMMARY_MAX_LENGTH:
        report.error(f"summary가 {SUMMARY_MAX_LENGTH}자를 넘습니다 ({len(summary)}자)")
    for label, text in (("reasoning", reasoning), ("summary", summary)):
        if isinstance(text, str):
            for expression in FORBIDDEN_EXPRESSIONS:
                if expression in text:
                    report.error(f"{label}에 평가 표현 '{expression}'이 있습니다")

    tags = output.get("referenceTags")
    if not isinstance(tags, list) or not all(isinstance(tag, str) and tag.strip() for tag in tags):
        report.error("referenceTags는 비어 있지 않은 문자열 배열이어야 합니다")
    elif not tags:
        report.warn("referenceTags가 비어 있습니다. S-07 '참고한 자료'가 비어 보입니다")


def _normalize_number(text):
    return re.sub(r"[\s,]", "", text)


def check_unknown_numbers(case, output, report):
    """판결 이유 · 요소 이유에 나온 숫자 표현이 사건 입력에 있는지 본다. 없으면 검수 대상으로 표시한다."""
    source = _normalize_number(json.dumps(case, ensure_ascii=False))
    # 형식이 틀린 값은 check_texts · check_factors가 이미 오류로 남긴다. 여기서는 문자열만 본다.
    factors = output.get("factors")
    texts = [output.get("reasoning")] + (
        [f.get("reason") for f in factors if isinstance(f, dict)] if isinstance(factors, list) else []
    )
    for text in (t for t in texts if isinstance(t, str)):
        for match in NUMBER_PATTERN.findall(text):
            if _normalize_number(match) not in source:
                report.warn(f"입력에 없는 숫자 표현 '{match.strip()}' — 없는 사실을 만든 것인지 검수하세요")


def validate(case, output):
    report = Report()
    if not isinstance(output, dict):
        report.error("출력이 JSON 객체가 아닙니다")
        return report
    check_keys(output, report)
    check_penalty(case, output, report)
    check_factors(case, output, report)
    check_texts(output, report)
    check_unknown_numbers(case, output, report)
    return report


def main():
    parser = argparse.ArgumentParser(description="AI 판결 출력 JSON을 검증한다")
    parser.add_argument("case_input", help="사건 입력 JSON")
    parser.add_argument("ai_output", help="모델이 낸 판결 (JSON 또는 ```json 코드 블록)")
    args = parser.parse_args()

    case = load_json(args.case_input)
    with open(args.ai_output, encoding="utf-8") as f:
        raw = f.read()
    try:
        output = parse_output(raw)
    except json.JSONDecodeError as e:
        print(f"[ERROR] JSON으로 읽을 수 없습니다: {e}")
        return 1

    report = validate(case, output)
    print(report.to_text())
    return 0 if report.ok else 1


if __name__ == "__main__":
    sys.exit(main())
