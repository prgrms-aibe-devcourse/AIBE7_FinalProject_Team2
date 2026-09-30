"""사건 입력 파일로 AI 판결 생성 프롬프트를 만든다.

사용법:
    python3 build_prompt.py examples/case_input.json > out/prompt.md

입력 파일에서 **허용된 항목만 골라** 프롬프트에 넣는다(화이트리스트).
실제 판결 · 사용자 판결 · 원본 판결문 정보가 입력 파일에 섞여 있으면 프롬프트를 만들지 않고 멈춘다
(요구사항 FR-4-1 · REQ-041).
"""

import argparse
import sys
from string import Template

from common import (
    NO_TERM_PENALTIES,
    PENALTY_NAMES,
    PROMPT_VERSION,
    REDUCIBLE_TO,
    find_forbidden_keys,
    format_months,
    format_penalty_range,
    load_json,
    read_prompt,
)

REQUIRED_CASE_KEYS = (
    "title",
    "chargeName",
    "appliedLaw",
    "statutoryPenaltyText",
    "overview",
    "sections",
    "penaltyRules",
    "factors",
)


class InputError(Exception):
    pass


def check_case_input(case):
    forbidden = find_forbidden_keys(case)
    if forbidden:
        raise InputError(
            "AI 입력에 넣으면 안 되는 항목이 있습니다 (실제 판결 · 사용자 판결 · 원본 판결문): "
            + ", ".join(forbidden)
        )
    missing = [key for key in REQUIRED_CASE_KEYS if not case.get(key)]
    if missing:
        raise InputError("필수 항목이 비어 있습니다: " + ", ".join(missing))
    for rule in case["penaltyRules"]:
        if rule.get("penaltyType") not in PENALTY_NAMES:
            raise InputError(f"지원하지 않는 형벌 종류입니다: {rule.get('penaltyType')}")
        if rule.get("allowedMin") is None or rule.get("allowedMax") is None:
            raise InputError(f"{rule['penaltyType']}의 선고 가능 범위(allowedMin · allowedMax)가 없습니다")
        if rule["penaltyType"] in NO_TERM_PENALTIES and rule.get("suspensionAllowed"):
            # 사형 · 무기는 감경해도 징역 10년 이상이라 집행유예 대상이 아니다 (ERD penalty_rule v1.4)
            raise InputError(f"{rule['penaltyType']}은 suspensionAllowed가 false여야 합니다")


def render_sections(sections):
    blocks = []
    for section in sections:
        title = section.get("title") or section.get("sectionType", "")
        if section.get("data"):
            body = "\n".join(f"- {item['label']}: {item['value']}" for item in section["data"])
        else:
            body = section.get("content", "")
        blocks.append(f"### {title}\n\n{body}")
    return "\n\n".join(blocks)


def render_penalty_rules(rules):
    lines = []
    for rule in rules:
        penalty_type = rule["penaltyType"]
        suspension = "집행유예 가능" if rule.get("suspensionAllowed") else "집행유예 불가"
        reducible = REDUCIBLE_TO[penalty_type]
        reduce_text = (
            ", 감경하면 " + " · ".join(f"`{target}`({PENALTY_NAMES[target]})" for target in reducible) + "로 선고 가능"
            if reducible
            else ""
        )
        lines.append(f"- `{penalty_type}` ({PENALTY_NAMES[penalty_type]}): {format_penalty_range(rule)}, {suspension}{reduce_text}")
    lines.append("- 위에 없는 형벌 종류(무죄 등)는 고를 수 없다.")
    return "\n".join(lines)


def render_recommended(recommended):
    if not recommended:
        return "(제공되지 않음)"
    return (
        f"징역 {format_months(recommended['minMonths'])} ~ {format_months(recommended['maxMonths'])}"
        f" — {recommended.get('basis', '')}"
    )


def render_factors(factors):
    return "\n".join(f"- {factor['factorId']}. {factor['label']}" for factor in factors)


def render_similar_cases(cases):
    if not cases:
        return "(제공되지 않음)"
    return "\n".join(
        f"- {case['title']}: {case['summary']} → {case['sentence']}" for case in cases
    )


def build_prompt(case):
    check_case_input(case)
    references = case.get("references", {})
    user = Template(read_prompt("judgment_user.md")).substitute(
        title=case["title"],
        charge_name=case["chargeName"],
        applied_law=case["appliedLaw"],
        statutory_penalty_text=case["statutoryPenaltyText"],
        overview=case["overview"],
        sections=render_sections(case["sections"]),
        penalty_rules=render_penalty_rules(case["penaltyRules"]),
        recommended=render_recommended(case.get("recommended")),
        factors=render_factors(case["factors"]),
        sentencing_guideline=references.get("sentencingGuideline") or "(제공되지 않음)",
        similar_cases=render_similar_cases(references.get("similarCases")),
    )
    return {"promptVersion": PROMPT_VERSION, "system": read_prompt("judgment_system.md"), "user": user}


def main():
    parser = argparse.ArgumentParser(description="AI 판결 생성 프롬프트를 만든다")
    parser.add_argument("case_input", help="사건 입력 JSON")
    args = parser.parse_args()
    try:
        prompt = build_prompt(load_json(args.case_input))
    except InputError as e:
        print(f"[오류] {e}", file=sys.stderr)
        return 1
    print(f"<!-- prompt_version: {prompt['promptVersion']} -->")
    print("# [SYSTEM]\n")
    print(prompt["system"])
    print("# [USER]\n")
    print(prompt["user"])
    return 0


if __name__ == "__main__":
    sys.exit(main())
