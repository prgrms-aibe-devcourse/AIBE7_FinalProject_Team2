"""판결 체험 대본 — 검수 오류 고쳐 쓰기 (BE-122 2차).

`script_review.review_script`가 남긴 오류(`severity: error`)를 README "재생성 범위"대로 고친다:
실패한 줄만 고쳐 쓰기 → 정해진 횟수를 넘기면 그 단위 재생성 → 대본 전체는 다시 만들지 않는다.

- 줄 고쳐 쓰기: 그 줄만 앞뒤 대사 · 고칠 점과 함께 보내, 단위를 만들 때와 같은 블록(`unit_prompt`) · 사건 데이터로 다시 쓰게 한다.
  고친 줄은 검사(`review_unit` · `review_evidence`)를 다시 돌려 그 줄의 오류가 없어졌을 때만 받는다.
- 단위 재생성: 줄을 `retries`번 고쳐도 남으면, 또는 검사가 처음부터 `fix: unit`을 줬으면 그 단위를 다시 만든다(한 번).
  **U1 · U4만** 다시 만든다. U0(설계도)는 U2 · U4가 기대고, U2는 쟁점이 모두진술 · 신문과 한 호출이라 다시 만들면 U4까지
  어긋난다(BE-119). U3는 요소마다 따로 부르므로 그 줄을 다시 만드는 것이 곧 재생성이다.
- 고친 뒤 대본 전체를 다시 검사해 `review`를 바꾸고, 무엇을 했는지 `repairs`에 남긴다. 끝내 남은 오류는 그대로 두어
  공개를 막는다(`publishable: false`) — 관리자가 검수 화면에서 고친다(BE-129).

경고는 고치지 않는다. 경고는 문장 비교(휴리스틱)라 모델에게 다시 쓰게 하면 맞는 대사를 바꿀 수 있어서다.
"""

import copy
import json
import time

from llm import LLMError, LLMQuotaExhaustedError
from llm import call as default_call
from script_assembler import (NO_SENTENCING, QUESTION_RULES, ask_unit, build_user, check_closing, check_design,
                              check_intro, check_opening, closing_request, evidence_blocks, intro_request,
                              strip_line_prefix, unit_prompt, unit_warnings)
from script_generation import build_evidence_prompt, ordered_evidence_factors, validate_line
from script_review import (ERROR, FIX_LINE, FIX_UNIT, review_evidence, review_failed, review_script, review_unit,
                           unit_context, unit_lines)

UNIT_ORDER = ("U0", "U1", "U2", "U3", "U4")
# 줄을 고쳐도 남으면 단위째 다시 만드는 단위 (모듈 설명 참고)
REGENERATE_UNITS = ("U1", "U4")
# 단위가 바뀌면 낡는(다시 만들어야 할 수 있는) 뒤 단위 — BE-129 연동 표(U0 → U2 → U4, U2 → U4). U2는 설계도의 질문을,
# U4는 설계도의 쟁점과 U2의 변호인 입장을 받는다. U1 · U3 · U4를 바꿔도 다른 단위는 그대로다
AFFECTS = {"U0": ["U2", "U4"], "U1": [], "U2": ["U4"], "U3": [], "U4": []}

# path 첫 키 → 고쳐 쓸 줄의 설명 (모델에게 어떤 줄인지 알린다)
LINE_ROLES = {
    "issues": "설계도의 쟁점 한 줄(15자 안팎, 판결 방향을 담지 않는다)",
    "questions": "검사의 피고인신문 질문(예 · 아니오로 답하는 사실 질문)",
    "lines": "1막 기사 해설 대사(해요체 — 모든 문장을 \"~요\"로 끝낸다)",
    "indict": "검사의 공소사실 대사(사실로 말한다)",
    "plea": "변호인의 입장 대사",
    "issues_narr": "진행 해설 대사(해요체 — 모든 문장을 \"~요\"로 끝낸다, 쟁점의 답을 정하지 않는다)",
    "cards": "화면 카드에 실리는 요약 글",
    "prosecutor": "3막 검사 의견",
    "defense": "3막 변호인 최종변론",
}


# ---------------------------------------------------------------- JSON Pointer

def _tokens(path):
    return [int(t) if t.isdigit() else t for t in path.split("/")[1:]]


def get_at(value, path):
    for token in _tokens(path):
        value = value[token]
    return value


def set_at(value, path, text):
    """`path` 자리를 `text`로 바꾼 복사본."""
    result = copy.deepcopy(value)
    *parents, last = _tokens(path)
    target = result
    for token in parents:
        target = target[token]
    target[last] = text
    return result


# ---------------------------------------------------------------- 줄 고쳐 쓰기 요청

def _rejected_lines(rejected):
    """지난 시도에서 받지 않은 줄과 그 이유. 같은 실수를 되풀이하지 않게 함께 보낸다."""
    if not rejected:
        return []
    text, problems = rejected
    return [f"지난번에 고쳐 쓴 줄 {json.dumps(text, ensure_ascii=False)}도 받지 않았습니다:",
            *[f"- {message}" for message in problems]]


def line_request(unit, path, text, problems, case, cls, units, paper, rejected=None):
    """U0 · U1 · U2 · U4의 줄 하나를 고쳐 쓰는 (system, user). 블록 · 사건 데이터는 단위를 만들 때와 같다.
    `rejected`는 지난 시도에서 받지 않은 (줄, 이유)다."""
    design, opening = (units.get("U0") or {}).get("value"), (units.get("U2") or {}).get("value")
    system, scene, data = unit_prompt(unit, case, cls, design=design, opening=opening, paper=paper)
    value = units[unit]["value"]
    lines = [(p, t) for p, t, kind in unit_lines(unit, value, strip_line_prefix) if kind != "card"]
    index = next((i for i, (p, _) in enumerate(lines) if p == path), None)
    before = lines[index - 1][1] if index else None
    after = lines[index + 1][1] if index is not None and index + 1 < len(lines) else None
    key = _tokens(path)[0]
    tasks = [
        f"이미 만든 대본에서 줄 하나만 고쳐 쓰세요. 고칠 줄: {LINE_ROLES.get(key, key)}. 다른 줄은 바꾸지 않습니다.",
        f"지금 줄: {json.dumps(text, ensure_ascii=False)}",
        *([f"앞 줄: {json.dumps(before, ensure_ascii=False)}"] if before else []),
        *([f"뒤 줄: {json.dumps(after, ensure_ascii=False)}"] if after else []),
        "고칠 점:",
        *[f"- {message}" for message in problems],
        *_rejected_lines(rejected),
        "- 말하는 사람 · 줄의 역할 · 담긴 사실은 그대로 두고 고칠 점만 고친다. 줄 첫머리의 정해진 문구(예: \"공소사실을 말씀드리겠습니다.\", "
        "\"검사 측 의견입니다.\")는 그대로 둔다.",
        "- 숫자는 scene_data에 있는 표기 그대로 쓰고, scene_data에 없는 숫자 · 감정 · 사건을 더하지 않는다.",
        "- 대사는 두 줄, 100자 이내다. 앞에 번호 · 화자 이름을 붙이지 않는다.",
        *([NO_SENTENCING] if unit in ("U2", "U4") else []),
        *(QUESTION_RULES if key == "questions" else ()),
        '출력은 JSON 객체 하나: {"text":"고친 줄"}',
    ]
    return system, build_user(scene, data, tasks)


def evidence_line_request(case, judgment_output, cast, cls, evidence, index, problems, rejected=None):
    """U3 증거조사 대사 하나를 다시 만드는 (system, user, factor). BE-107 프롬프트에 지난 대사 · 고칠 점을 덧붙인다."""
    factors = {f["factorId"]: f for f in ordered_evidence_factors(case, judgment_output)}
    line = evidence[index]
    factor = factors[line["factorId"]]
    earlier = [e for e in evidence[:index] if e.get("role") == line.get("role") and e.get("text")]
    guide, sensitive = evidence_blocks(cls)
    system, user, _, _ = build_evidence_prompt(
        case, factor, cast, is_first_for_role=not earlier, previous_text=earlier[-1]["text"] if earlier else None,
        guide=guide, sensitive=sensitive)
    extra = ["", "[고쳐 쓰기]"]
    if line.get("text"):
        extra.append(f"지난번 대사: {json.dumps(line['text'], ensure_ascii=False)}")
    extra += ["고칠 점:", *[f"- {message}" for message in problems], *_rejected_lines(rejected),
              "같은 사실을 소개하되 고칠 점만 고쳐 대사 한 줄(또는 두 줄)로 다시 쓰세요."]
    return system, user + "\n".join(extra), factor


# ---------------------------------------------------------------- 고쳐 쓰기

def _call(caller, model, system, user, *, json_output, opts):
    if opts["delay"]:
        time.sleep(opts["delay"])
    return caller(model, system, user, temperature=opts["temperature"], max_tokens=opts["max_tokens"],
                  timeout=opts["timeout"], json_output=json_output)


def _path_errors(issues, path):
    return [i for i in issues if i["path"] == path and i["severity"] == ERROR]


def _other_errors(issues, path):
    return [i for i in issues if i["path"] != path and i["severity"] == ERROR]


UNIT_CHECKS = {"U0": check_design, "U1": check_intro, "U2": check_opening, "U4": check_closing}


def rewrite_line(unit, path, issues, case, judgment_output, cls, generated, *, model, caller, retries, opts, log):
    """줄 하나를 최대 `retries`번 고쳐 쓴다. 돌려주는 값: (성공 여부, 시도 수, 받은 줄 또는 None, 받지 않은 마지막 줄 또는 None).
    받지 않은 시도는 단위 값에 넣지 않고, 다음 요청에 원래 고칠 점과 함께 "받지 않은 줄 · 이유"로 알린다.

    받는 기준: 그 줄의 오류가 없어지고, **단위 전체의 다른 오류가 늘지 않아야** 한다(고친 줄이 이웃 줄과 겹치거나 다른 곳에
    오류를 만들면 받지 않는다 — 단위 재생성과 같은 기준, PR #355 리뷰)."""
    units = generated["units"]
    value = units[unit]["value"]
    current, problems, rejected = get_at(value, path), [i["message"] for i in issues], None
    ctx = unit_context(unit, case, judgment_output, generated)
    others_before = _other_errors(review_unit(unit, value, ctx, strip_prefix=strip_line_prefix), path)
    for attempt in range(1, retries + 1):
        system, user = line_request(unit, path, current, problems, case, cls, units, generated.get("paper"), rejected)
        try:
            result = _call(caller, model, system, user, json_output=True, opts=opts)
            text = json.loads(result.text).get("text")
        except LLMQuotaExhaustedError:
            raise
        except (LLMError, TypeError, ValueError, AttributeError) as e:
            if log:
                log(f"[{unit}{path}] 고쳐 쓰기 {attempt}/{retries} 실패: {e}")
            continue
        if not isinstance(text, str) or not text.strip():
            continue
        text = strip_line_prefix(text) if unit == "U1" else text.strip()
        patched = set_at(value, path, text)
        check = UNIT_CHECKS[unit]
        broken = check(patched, cls["issue"]) if unit == "U0" else check(patched)  # 형식(예: 구형 금지)이 깨졌는가
        after = [] if broken else review_unit(unit, patched, ctx, strip_prefix=strip_line_prefix)
        remaining = broken or [i["message"] for i in _path_errors(after, path)]
        if not remaining and len(_other_errors(after, path)) > len(others_before):
            remaining = [f"다른 줄에 오류가 생겼습니다: {_other_errors(after, path)[0]['message']}"]
        if not remaining:
            units[unit] = {**units[unit], "value": patched,
                           "warnings": unit_warnings(unit, patched, case, generated.get("paper"))}
            return True, attempt, text, None
        rejected = (text, remaining)
        if log:
            log(f"[{unit}{path}] 고쳐 쓰기 {attempt}/{retries}: 받지 않음 — {rejected[1][0]}")
    return False, retries, None, rejected[0] if rejected else None


def rewrite_evidence(index, issues, case, judgment_output, cast, cls, generated, *, model, caller, retries, opts, log):
    """U3 증거조사 대사 하나를 최대 `retries`번 다시 만든다. BE-107처럼 대사 텍스트만 받는다(JSON이 아니다)."""
    evidence = generated["evidence"]
    problems, rejected = [i["message"] for i in issues], None
    others_before = _other_errors(review_evidence(evidence, case, judgment_output), f"/{index}")
    for attempt in range(1, retries + 1):
        system, user, factor = evidence_line_request(case, judgment_output, cast, cls, evidence, index, problems, rejected)
        try:
            result = _call(caller, model, system, user, json_output=False,
                           opts={**opts, "max_tokens": opts["evidence_max_tokens"], "timeout": opts["evidence_timeout"]})
        except LLMQuotaExhaustedError:
            raise
        except LLMError as e:
            if log:
                log(f"[U3/{index}] 고쳐 쓰기 {attempt}/{retries} 실패: {e}")
            continue
        text = (result.text or "").strip()
        validation = validate_line(text, factor)
        patched = [*evidence[:index], {**evidence[index], "text": text}, *evidence[index + 1:]]
        after = review_evidence(patched, case, judgment_output)
        remaining = validation["errors"] or [i["message"] for i in _path_errors(after, f"/{index}")]
        if not remaining and len(_other_errors(after, f"/{index}")) > len(others_before):
            remaining = [f"다른 줄에 오류가 생겼습니다: {_other_errors(after, f'/{index}')[0]['message']}"]
        if not remaining:
            evidence[index] = {**evidence[index], "text": text, "validation": validation,
                               "attempts": evidence[index].get("attempts", 0) + attempt}
            return True, attempt, text, None
        rejected = (text, remaining)
        if log:
            log(f"[U3/{index}] 고쳐 쓰기 {attempt}/{retries}: 받지 않음 — {remaining[0]}")
    return False, retries, None, rejected[0] if rejected else None


def regenerate_unit(unit, case, judgment_output, cast, cls, generated, *, model, caller, opts, log):
    """U1 · U4를 한 번 다시 만든다. 새 값의 오류가 줄었을 때만 바꾼다(늘거나 같으면 지금 값을 둔다).
    돌려주는 값: (오류가 모두 풀렸는가, 바꿨는가, 전 오류 수, 후 오류 수 — 바꾸지 않았으면 전과 같다)."""
    units = generated["units"]
    design, opening = (units.get("U0") or {}).get("value"), (units.get("U2") or {}).get("value")
    if unit == "U1":
        request, check, lines_of = intro_request(generated["paper"], cls), check_intro, \
            (lambda v: [strip_line_prefix(x) for x in v["lines"][:5]])
        source = json.dumps(generated["paper"], ensure_ascii=False)
    else:
        request, check, lines_of = closing_request(case, cls, cast, design, opening), check_closing, \
            (lambda v: [v["prosecutor"], v["defense"]])
        source = json.dumps(unit_prompt("U4", case, cls, design=design, opening=opening)[2], ensure_ascii=False)
    result = ask_unit(unit, *request, check, lines_of, source, model=model, caller=caller, max_retries=opts["max_retries"],
                      temperature=opts["temperature"], max_tokens=opts["max_tokens"], timeout=opts["timeout"],
                      delay=opts["delay"], log=log)
    ctx = unit_context(unit, case, judgment_output, generated)
    count = lambda value: len([i for i in review_unit(unit, value, ctx, strip_prefix=strip_line_prefix)  # noqa: E731
                               if i["severity"] == ERROR])
    before = count(units[unit]["value"])
    if not result["value"]:
        return False, False, before, before
    after = count(result["value"])
    if after < before:
        units[unit] = {**units[unit], "value": result["value"], "warnings": result["warnings"],
                       "attempts": units[unit].get("attempts", 0) + result["attempts"]}
        return after == 0, True, before, after
    return False, False, before, before


def repair_units(case, judgment_output, cast, cls, generated, *, model, caller=default_call, retries=2, max_retries=2,
                 temperature=None, max_tokens=6000, evidence_max_tokens=2000, timeout=150, evidence_timeout=60,
                 delay=0.0, log=None):
    """검수 오류를 고친다. `generated`(generate_units 반환값)를 고친 복사본을 돌려준다 — `units` · `evidence`가 바뀌고
    `review`는 다시 검사한 결과, `repairs`는 한 일의 기록이다:

        줄: {"unit": "U2", "path": "/indict/1", "codes": ["NUMBER_NOT_IN_SOURCE"], "action": "line", "attempts": 2,
             "ok": true, "before": "고치기 전 줄", "after": "받은 줄" | null, "lastRejected": "받지 않은 마지막 줄" | null,
             "affects": ["U4"]}
        단위: {"unit": "U4", "path": "", "codes": [...], "action": "unit", "attempts": 1, "ok": true,
               "errorsBefore": 2, "errorsAfter": 0, "affects": []}

    `before` · `after`는 관리자가 자동으로 고친 줄의 원문을 볼 수 있게 남긴다(BE-129 검수 화면). `affects`는 이 일로 값이
    바뀌어 낡았을 수 있는 뒤 단위(`AFFECTS`)다 — 값이 바뀌지 않았으면 빈 목록이다(BE-126 낡음 표시).

    `retries`는 줄 하나를 고쳐 쓰는 최대 횟수다(0이면 고치지 않는다). 호출 한도가 소진되면 그 자리에서 멈추고 남은 오류는 둔다."""
    generated = copy.deepcopy(generated)
    generated.setdefault("repairs", [])
    if retries <= 0:
        return generated
    opts = dict(temperature=temperature, max_tokens=max_tokens, evidence_max_tokens=evidence_max_tokens,
                timeout=timeout, evidence_timeout=evidence_timeout, delay=delay, max_retries=max_retries)
    errors = [i for i in generated.get("review", []) if i["severity"] == ERROR and i.get("fix") in (FIX_LINE, FIX_UNIT)]
    by_line = {}
    for issue in errors:
        by_line.setdefault((issue["unit"], issue["path"]), []).append(issue)
    regenerate = []
    try:
        for (unit, path), issues in sorted(by_line.items(), key=lambda item: UNIT_ORDER.index(item[0][0])):
            codes = sorted({i["code"] for i in issues})
            if any(i["fix"] == FIX_UNIT for i in issues):
                if unit in REGENERATE_UNITS and unit not in regenerate:
                    regenerate.append(unit)
                continue
            if unit == "U3":
                index = int(path[1:])
                before = generated["evidence"][index].get("text")
                ok, attempts, after, last = rewrite_evidence(index, issues, case, judgment_output, cast, cls, generated,
                                                             model=model, caller=caller, retries=retries, opts=opts,
                                                             log=log)
            else:
                before = get_at(generated["units"][unit]["value"], path)
                ok, attempts, after, last = rewrite_line(unit, path, issues, case, judgment_output, cls, generated,
                                                         model=model, caller=caller, retries=retries, opts=opts, log=log)
            record = {"unit": unit, "path": path, "codes": codes, "action": "line", "attempts": attempts, "ok": ok,
                      "before": before, "after": after, "lastRejected": last, "affects": AFFECTS[unit] if ok else []}
            if unit == "U3":
                record["factorId"] = issues[0].get("factorId")
            generated["repairs"].append(record)
            if not ok and unit in REGENERATE_UNITS and unit not in regenerate:
                regenerate.append(unit)
        for unit in regenerate:
            if log:
                log(f"[{unit}] 줄을 고쳐도 오류가 남아 단위를 다시 만듭니다")
            ok, replaced, errors_before, errors_after = regenerate_unit(unit, case, judgment_output, cast, cls, generated,
                                                                        model=model, caller=caller, opts=opts, log=log)
            codes = sorted({i["code"] for i in errors if i["unit"] == unit})
            generated["repairs"].append({"unit": unit, "path": "", "codes": codes, "action": "unit", "attempts": 1, "ok": ok,
                                         "errorsBefore": errors_before, "errorsAfter": errors_after,
                                         "affects": AFFECTS[unit] if replaced else []})
    except LLMQuotaExhaustedError as e:
        if log:
            log(f"⚠ 호출 한도 소진 — 고쳐 쓰기를 멈추고 남은 검수 오류는 그대로 둡니다: {e}")
    try:
        generated["review"] = review_script(case, judgment_output, generated, strip_prefix=strip_line_prefix)
    except Exception as e:  # noqa: BLE001 — generate_units와 같다: 검사가 실패해도 고친 결과는 남기고 공개만 막는다
        generated["review"] = [review_failed(e)]
    return generated
