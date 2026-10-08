"""판단 요소 가치관 축 분류 투표 (BE-49).

추출기(extract-v5)는 요소마다 축을 한 번 정한다. 한 번 정한 값은 흔들릴 수 있어서, 요소가 확정된 뒤 **축만** 같은 모델에
여러 번 물어 투표한다. 추출 자체를 여러 번 돌리면 회차마다 요소 문구 · 개수가 달라져 같은 요소끼리 비교할 수 없다.

- 기본값 = 최다표 축. 동률이면 추출기가 정한 값이 최다표 안에 있으면 그것, 아니면 축 순서(① ~ ④, 없음)상 앞의 것
- 최다표가 요청 횟수의 과반(절반 초과)이 아니면 needsReview = true → 관리자가 후검수에서 확인한다 (BE-43 · FE-16)
  표가 갈린 경우(동률 포함)와 유효 응답이 모자라 근거가 약한 경우(예: 5회 중 1개만 유효)를 함께 잡는다
- 결과는 보고서 factorExtras[].valueAxis · valueAxisVotes 형식이다. 적재 SQL이 value_axis · value_axis_votes로 넣는다 (BE-48)
  {"runs": 5, "counts": {"FAULT_STANDARD": 3, "NONE": 2}, "needsReview": false}
  counts는 표를 받은 축만, "어느 축에도 맞지 않음(null)" 표는 "NONE" 키로 센다. runs는 집계에 들어간 유효 응답 수다
"""

import copy

from case_seed_sql import VALUE_AXES
from common import read_prompt
from validate_output import parse_output

NONE_KEY = "NONE"
AXIS_KEYS = (*VALUE_AXES, NONE_KEY)  # 동률일 때 고르는 순서


class AxisVoteError(Exception):
    pass


def build_axis_prompt(case):
    """(system, user). 사건 개요와 판단 요소 문구만 보낸다 (판결 · 형량 정보는 보내지 않는다)."""
    lines = [
        "# 사건", "",
        f"- 죄명: {case.get('chargeName') or '-'}", "",
        "## 사건 개요", "",
        case.get("overview") or "(없음)", "",
        "# 판단 요소 목록 (번호마다 가치관 축을 하나씩 고른다)", "",
    ]
    lines += [f"- {f['factorId']}. {f['label']}" for f in case["factors"]]
    lines += ["", "---", "", "위 판단 요소마다 가치관 축을 하나씩 골라, 시스템 지시의 JSON 형식으로만 답한다.", ""]
    return read_prompt("axis_system.md"), "\n".join(lines)


def parse_axis_answer(text, factor_ids):
    """응답 → {factorId: 축 또는 None}. 형식이 틀리면 AxisVoteError (그 회차는 집계에서 뺀다)."""
    try:
        output = parse_output(text)
    except ValueError as e:  # JSONDecodeError 포함
        raise AxisVoteError(f"응답을 읽을 수 없음: {e}")
    if not isinstance(output, dict) or not isinstance(output.get("factors"), list):
        raise AxisVoteError("factors 목록이 없습니다")
    answer, seen, errors = {}, set(), []
    for item in output["factors"]:
        if not isinstance(item, dict):
            errors.append("factors의 원소는 객체입니다")
            continue
        factor_id, axis = item.get("factorId"), item.get("valueAxis")
        if factor_id not in factor_ids or isinstance(factor_id, bool):
            errors.append(f"목록에 없는 요소 번호: {factor_id}")
        elif factor_id in seen:
            errors.append(f"요소 {factor_id}에 두 번 답했습니다")
        elif "valueAxis" not in item:
            errors.append(f"요소 {factor_id}: valueAxis가 없습니다 (어느 축에도 맞지 않으면 null)")
        elif axis is not None and axis not in VALUE_AXES:
            errors.append(f"요소 {factor_id}: 허용하지 않는 축 {axis}")
        else:
            answer[factor_id] = axis
        if factor_id in factor_ids and not isinstance(factor_id, bool):
            seen.add(factor_id)
    missing = [i for i in factor_ids if i not in seen]
    if missing:
        errors.append(f"답하지 않은 요소: {', '.join(map(str, missing))}")
    if errors:
        raise AxisVoteError(" · ".join(errors))
    return answer


def aggregate_votes(answers, factor_ids, preferred=None, requested_runs=None):
    """유효 응답 목록 → {factorId: {"valueAxis": 축 또는 None, "valueAxisVotes": {...}}}.

    preferred: 추출기가 정한 축 {factorId: 축}. 동률일 때만 쓴다.
    requested_runs: 요청한 횟수. 과반은 이 수 기준이다(없으면 유효 응답 수). 실패한 회차가 많으면 표가 모여도 확인 필요다
    """
    if not answers:
        raise AxisVoteError("집계할 응답이 없습니다")
    preferred = preferred or {}
    runs = len(answers)
    quorum = max(runs, requested_runs or 0)  # 과반 판정 기준 (유효 응답이 모자라면 요청 횟수)
    result = {}
    for factor_id in factor_ids:
        counts = {}
        for answer in answers:
            key = answer[factor_id] or NONE_KEY
            counts[key] = counts.get(key, 0) + 1
        top = max(counts.values())
        winners = [key for key in AXIS_KEYS if counts.get(key) == top]
        wanted = preferred.get(factor_id) or NONE_KEY
        chosen = wanted if wanted in winners else winners[0]
        result[factor_id] = {
            "valueAxis": None if chosen == NONE_KEY else chosen,
            "valueAxisVotes": {"runs": runs, "counts": {key: counts[key] for key in AXIS_KEYS if key in counts},
                               "needsReview": top * 2 <= quorum},
        }
    return result


def apply_votes(report, votes):
    """보고서의 factorExtras에 투표 결과(valueAxis · valueAxisVotes)를 덮어쓴 사본. votes는 votes.json의 factors 목록."""
    merged = copy.deepcopy(report)
    by_id = {v["factorId"]: v for v in votes}
    for extra in merged.get("factorExtras", []):
        vote = by_id.get(extra["factorId"])
        if vote:
            extra["valueAxis"] = vote["valueAxis"]
            extra["valueAxisVotes"] = vote["valueAxisVotes"]
    return merged
