"""사전 학습 점검 (REQ-102): 모델이 이 사건의 실제 판결을 이미 알고 있는지 확인한다.

1) 점검 프롬프트 만들기 — 개요와 죄명만 넣는다.
    python3 check_contamination.py prompt examples/case_input.json > out/contamination_prompt.md

2) 모델 응답을 여러 번(기본 최소 5개, 새 대화에서) 받아 파일로 저장한 뒤 판정한다.
    python3 check_contamination.py judge examples/court_judgment_internal.json out/run1.json ... out/run10.json

응답 하나를 먼저 분류하고(classify), 회차 묶음을 **비율로** 판정한다(aggregate, BE-37).
응답 분류
- KNOWS: 사건을 안다고 답함
- EXACT: 형벌 · 형량 · 집행유예를 정확히 맞힘
- CLOSE: 형벌 종류 · 집행유예 여부가 같고 형량이 허용 오차 안
  (징역: max(closeMinMonths, min(closeMaxMonths, closeRatio × 실제 형량)) 개월, 벌금: closeFineRatio)
- SAME_TYPE: 실제 판결이 사형 · 무기이고 형벌 종류를 맞힘 (형량 값이 없어 우연히 맞을 수 있음)
- INVALID: 응답 형식이 어긋남 (knowsCase가 true / false가 아님, 읽을 수 없음)
- FAR: 그 밖 (형벌 종류 · 집행유예 여부가 다르거나 형량이 멂, 예측 없음)

묶음 판정 (위에서부터 먼저 맞는 것)
- CONTAMINATED: KNOWS가 하나라도 있음
- INSUFFICIENT: 응답 수가 minAnswered보다 적음 (호출 실패 · 한도로 일부만 돌아온 경우 판정을 확정하지 않는다)
- CONTAMINATED: EXACT 비율 ≥ exactRatio
- SUSPECT: EXACT가 하나라도 있음, (EXACT + CLOSE + SAME_TYPE) 비율 ≥ suspectRatio, 또는 INVALID가 있음
- CLEAN: 그 밖
짧은 형량은 흔한 값이라 한두 번 정확히 맞혀도 우연일 수 있어 비율로 본다. 예전 기준(징역 ±2개월, 가장 나쁜 1건)은
짧은 형량에서 거의 항상 SUSPECT가 나와 변별력이 없었다.

실제 판결 파일(court_judgment_internal.json)은 내부 전용이다. 이 점검에만 쓰고, AI 판결 생성 프롬프트에는 넣지 않는다.
판정 근거(reason)에는 예측 형량 값을 쓰지 않는다 (생성 기록 generation_report로 이어진다).
"""

import argparse
import sys
from collections import Counter
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

VERDICTS = ("CLEAN", "SUSPECT", "INSUFFICIENT", "CONTAMINATED")
CATEGORIES = ("KNOWS", "EXACT", "CLOSE", "SAME_TYPE", "INVALID", "FAR")
DEFAULT_CRITERIA = {
    "minAnswered": 5,        # 이보다 응답이 적으면 INSUFFICIENT
    "closeRatio": 0.15,      # 징역 "가깝다": 실제 형량의 15%
    "closeMinMonths": 1,     # … 단 1개월보다 좁히지 않는다
    "closeMaxMonths": 6,     # … 단 6개월보다 넓히지 않는다 (긴 형량에서 느슨해지지 않게)
    "closeFineRatio": 0.1,   # 벌금 "가깝다": 실제 금액의 10%
    "exactRatio": 0.5,       # 정확히 맞힌 응답이 이 비율 이상이면 CONTAMINATED
    "suspectRatio": 0.5,     # 정확히 맞힘 · 가까움 · 종류만 같음이 이 비율 이상이면 SUSPECT
}


def build_contamination_prompt(case):
    return Template(read_prompt("contamination_check.md")).substitute(
        charge_name=case["chargeName"],
        overview=case["overview"],
    )


def criteria_with(overrides=None):
    """기본 임계값에 설정 값을 덮어쓴다 (None인 값은 기본값 유지)."""
    merged = dict(DEFAULT_CRITERIA)
    merged.update({k: v for k, v in (overrides or {}).items() if k in DEFAULT_CRITERIA and v is not None})
    return merged


def prison_tolerance(actual_months, criteria):
    """징역 "가깝다" 허용 오차(개월): 실제 형량의 closeRatio, closeMinMonths ~ closeMaxMonths로 제한."""
    return max(criteria["closeMinMonths"], min(criteria["closeMaxMonths"], criteria["closeRatio"] * actual_months))


def _has_suspension(judgment):
    return judgment.get("suspensionMonths") is not None


def _knows(value):
    """knowsCase → True / False / None(형식 오류)."""
    if value is True or (isinstance(value, str) and value.strip().lower() == "true"):
        return True
    if value is False or (isinstance(value, str) and value.strip().lower() == "false"):
        return False
    return None


def classify(court, prediction, criteria=None):
    """응답 하나를 분류하고 (분류, 이유)를 돌려준다. 이유는 응답별 기록(내부)에만 남는다.

    형벌은 최종 선고 형벌로 비교한다. 실제 판결 파일은 reducedTo가 있으면 그 값, 모델 응답의
    penaltyType은 "실제로 선고된 형벌"을 묻는 것이라 그대로 최종 선고 형벌로 본다.
    """
    criteria = criteria_with(criteria)
    if not isinstance(prediction, dict):
        return "INVALID", "JSON 객체가 아님"
    knows = _knows(prediction.get("knowsCase"))
    if knows is True:
        return "KNOWS", "사건을 안다고 답함"
    if knows is None:
        return "INVALID", "knowsCase가 true / false가 아님"

    actual_penalty = final_penalty(court)
    if prediction.get("penaltyType") != actual_penalty:
        return "FAR", "형벌 종류가 다름"
    if actual_penalty in NO_TERM_PENALTIES:
        return "SAME_TYPE", f"형벌 종류({PENALTY_NAMES[actual_penalty]})가 같음 — 형량 값이 없는 형벌"
    if _has_suspension(prediction) != _has_suspension(court):
        return "FAR", "집행유예 여부가 다름"

    key = "prisonMonths" if actual_penalty == "PRISON" else "fineAmount"
    predicted, actual = prediction.get(key), court.get(key)
    if not isinstance(predicted, int) or isinstance(predicted, bool) or actual is None:
        return "FAR", f"{key} 예측 없음"
    if predicted == actual and prediction.get("suspensionMonths") == court.get("suspensionMonths"):
        return "EXACT", "형벌 · 형량 · 집행유예를 정확히 맞힘"
    if key == "prisonMonths":
        tolerance = prison_tolerance(actual, criteria)
    else:
        tolerance = actual * criteria["closeFineRatio"]
    if abs(predicted - actual) <= tolerance:
        return "CLOSE", f"{key} 차이가 허용 오차 안"
    return "FAR", f"{key} 차이가 허용 오차 밖"


def aggregate(categories, criteria=None):
    """응답 분류 목록 → (판정, 이유, 분류별 개수). 이유에는 예측 형량 값을 쓰지 않는다."""
    criteria = criteria_with(criteria)
    counts = Counter(categories)
    answered = len(categories)
    summary = {c: counts[c] for c in CATEGORIES if counts[c]}
    if counts["KNOWS"]:
        return "CONTAMINATED", f"사건을 안다고 답한 응답 {counts['KNOWS']}개", summary
    if answered < criteria["minAnswered"]:
        return "INSUFFICIENT", f"응답 {answered}개 < 최소 {criteria['minAnswered']}개 — 판정을 확정하지 않음", summary
    exact_ratio = counts["EXACT"] / answered
    near_ratio = (counts["EXACT"] + counts["CLOSE"] + counts["SAME_TYPE"]) / answered
    if exact_ratio >= criteria["exactRatio"]:
        return "CONTAMINATED", f"정확히 맞힌 응답 {counts['EXACT']}/{answered}", summary
    if counts["EXACT"]:
        return "SUSPECT", f"정확히 맞힌 응답 {counts['EXACT']}/{answered} (비율 기준 미만, 우연일 수 있음)", summary
    if near_ratio >= criteria["suspectRatio"]:
        return "SUSPECT", (f"가까운 응답 {counts['CLOSE'] + counts['SAME_TYPE']}/{answered}"
                           f"(기준 {criteria['suspectRatio']:.0%} 이상)"), summary
    if counts["INVALID"]:
        return "SUSPECT", f"형식이 어긋난 응답 {counts['INVALID']}개 — 팀이 확인", summary
    return "CLEAN", f"응답 {answered}개 중 정확히 · 가깝게 맞힌 비율이 기준 미만", summary


def judge(court, predictions, criteria=None):
    """응답 목록 → (판정, 응답별 [(분류, 이유)], 이유, 분류별 개수)."""
    results = [classify(court, p, criteria) for p in predictions]
    verdict, reason, summary = aggregate([category for category, _ in results], criteria)
    return verdict, results, reason, summary


def main():
    parser = argparse.ArgumentParser(description="사전 학습 점검 (REQ-102)")
    sub = parser.add_subparsers(dest="command", required=True)

    prompt_cmd = sub.add_parser("prompt", help="점검 프롬프트를 만든다")
    prompt_cmd.add_argument("case_input")

    judge_cmd = sub.add_parser("judge", help="모델 응답을 판정한다")
    judge_cmd.add_argument("court_judgment", help="실제 판결 (내부 전용)")
    judge_cmd.add_argument("responses", nargs="+", help="모델 응답 파일 (기본 최소 5개)")
    judge_cmd.add_argument("--min-answered", type=int, default=DEFAULT_CRITERIA["minAnswered"],
                           help=f"이보다 응답이 적으면 INSUFFICIENT (기본 {DEFAULT_CRITERIA['minAnswered']})")

    args = parser.parse_args()

    if args.command == "prompt":
        print(f"<!-- prompt_version: {CONTAMINATION_PROMPT_VERSION} -->")
        print(build_contamination_prompt(load_json(args.case_input)))
        return 0

    court = load_json(args.court_judgment)
    predictions = []
    for path in args.responses:
        with open(path, encoding="utf-8") as f:
            try:
                predictions.append(parse_output(f.read()))
            except ValueError:  # JSONDecodeError 포함 — 읽을 수 없는 응답은 INVALID로 센다
                predictions.append(None)
    criteria = criteria_with({"minAnswered": args.min_answered})
    verdict, results, reason, summary = judge(court, predictions, criteria)
    for path, (category, why) in zip(args.responses, results):
        print(f"[{category}] {path}: {why}")
    print(f"분류: {summary}")
    print(f"최종 판정: {verdict} — {reason}")
    return 0 if verdict == "CLEAN" else 1


if __name__ == "__main__":
    sys.exit(main())
