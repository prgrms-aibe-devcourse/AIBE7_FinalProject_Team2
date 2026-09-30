"""사전 학습 점검 (REQ-102): 모델이 이 사건의 실제 판결을 이미 알고 있는지 확인한다.

1) 점검 프롬프트 만들기 — 개요와 죄명만 넣는다.
    python3 check_contamination.py prompt examples/case_input.json > out/contamination_prompt.md

2) 모델 응답을 여러 번(3회 이상 권장, 새 대화에서) 받아 파일로 저장한 뒤 판정한다.
    python3 check_contamination.py judge examples/court_judgment_internal.json out/run1.json out/run2.json out/run3.json

판정 (가장 나쁜 결과를 최종 판정으로 쓴다)
- CONTAMINATED: 사건을 안다고 답했거나, 형벌 · 형량 · 집행유예를 정확히 맞혔다 → 사건을 빼거나 다시 가공한다
- SUSPECT: 형벌 종류와 집행유예 여부가 같고 형량이 매우 가깝다, 또는 실제 판결이 사형 · 무기인데 형벌 종류를 맞혔다 → 팀이 검토한다
- CLEAN: 문제없음

실제 판결 파일(court_judgment_internal.json)은 내부 전용이다. 이 점검에만 쓰고, AI 판결 생성 프롬프트에는 넣지 않는다.
"""

import argparse
import sys
from string import Template

from common import (
    CONTAMINATION_PROMPT_VERSION,
    NO_TERM_PENALTIES,
    PENALTY_NAMES,
    final_penalty,
    load_json,
    read_prompt,
)
from validate_output import parse_output

VERDICT_ORDER = ("CLEAN", "SUSPECT", "CONTAMINATED")
CLOSE_PRISON_MONTHS = 2  # 징역 ±2개월 이내면 "가깝다"
CLOSE_FINE_RATIO = 0.1  # 벌금 ±10% 이내면 "가깝다"


def build_contamination_prompt(case):
    return Template(read_prompt("contamination_check.md")).substitute(
        charge_name=case["chargeName"],
        overview=case["overview"],
    )


def _has_suspension(judgment):
    return judgment.get("suspensionMonths") is not None


def judge_one(court, prediction):
    """응답 하나를 판정하고 (판정, 이유)를 돌려준다.

    형벌은 최종 선고 형벌로 비교한다. 실제 판결 파일은 reducedTo가 있으면 그 값, 모델 응답의
    penaltyType은 "실제로 선고된 형벌"을 묻는 것이라 그대로 최종 선고 형벌로 본다.
    """
    knows_case = prediction.get("knowsCase")
    if knows_case is True or (isinstance(knows_case, str) and knows_case.strip().lower() == "true"):
        return "CONTAMINATED", f"사건을 안다고 답함: {prediction.get('note', '')}"
    if not (knows_case is False or (isinstance(knows_case, str) and knows_case.strip().lower() == "false")):
        # 형식이 어긋난 응답은 "모른다"로 넘기지 않고 팀이 본다
        return "SUSPECT", f"knowsCase가 true / false가 아님: {knows_case!r}"

    actual_penalty = final_penalty(court)
    if prediction.get("penaltyType") != actual_penalty:
        return "CLEAN", "형벌 종류가 다름"
    if actual_penalty in NO_TERM_PENALTIES:
        # 사형 · 무기는 형량 값이 없어 형벌 종류만 같아도 맞힌 것처럼 보인다. 선택지가 적어 우연히 맞을 수 있으므로 팀이 본다.
        return "SUSPECT", f"형벌 종류({PENALTY_NAMES[actual_penalty]})가 같음 — 형량 값이 없는 형벌이라 팀이 검토"
    if _has_suspension(prediction) != _has_suspension(court):
        return "CLEAN", "집행유예 여부가 다름"

    key = "prisonMonths" if actual_penalty == "PRISON" else "fineAmount"
    predicted, actual = prediction.get(key), court.get(key)
    if not isinstance(predicted, int) or actual is None:
        return "CLEAN", f"{key} 예측 없음"

    same_value = predicted == actual
    same_suspension = prediction.get("suspensionMonths") == court.get("suspensionMonths")
    if same_value and same_suspension:
        return "CONTAMINATED", "형벌 · 형량 · 집행유예를 정확히 맞힘"

    if key == "prisonMonths":
        close = abs(predicted - actual) <= CLOSE_PRISON_MONTHS
    else:
        close = abs(predicted - actual) <= actual * CLOSE_FINE_RATIO
    if close:
        return "SUSPECT", f"{key} 예측 {predicted} / 실제 {actual}로 매우 가까움"
    return "CLEAN", f"{key} 예측 {predicted} / 실제 {actual}"


def judge(court, predictions):
    results = [judge_one(court, p) for p in predictions]
    final = max((verdict for verdict, _ in results), key=VERDICT_ORDER.index)
    return final, results


def main():
    parser = argparse.ArgumentParser(description="사전 학습 점검 (REQ-102)")
    sub = parser.add_subparsers(dest="command", required=True)

    prompt_cmd = sub.add_parser("prompt", help="점검 프롬프트를 만든다")
    prompt_cmd.add_argument("case_input")

    judge_cmd = sub.add_parser("judge", help="모델 응답을 판정한다")
    judge_cmd.add_argument("court_judgment", help="실제 판결 (내부 전용)")
    judge_cmd.add_argument("responses", nargs="+", help="모델 응답 파일 (3개 이상 권장)")

    args = parser.parse_args()

    if args.command == "prompt":
        print(f"<!-- prompt_version: {CONTAMINATION_PROMPT_VERSION} -->")
        print(build_contamination_prompt(load_json(args.case_input)))
        return 0

    court = load_json(args.court_judgment)
    predictions = []
    for path in args.responses:
        with open(path, encoding="utf-8") as f:
            predictions.append(parse_output(f.read()))
    final, results = judge(court, predictions)
    for path, (verdict, reason) in zip(args.responses, results):
        print(f"[{verdict}] {path}: {reason}")
    if len(predictions) < 3:
        print("[WARN] 응답이 3개 미만입니다. 새 대화에서 3회 이상 받아 판정하세요")
    print(f"최종 판정: {final}")
    return 0 if final == "CLEAN" else 1


if __name__ == "__main__":
    sys.exit(main())
