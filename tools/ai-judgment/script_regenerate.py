"""판결 체험 대본 — 블록 재생성 (BE-129 2차).

관리자가 검수 화면에서 배역 · 분류 · 사건 내용 · 기사를 고쳤거나 블록이 마음에 들지 않을 때, 고른 블록과 그 블록을 보고 만든
뒤 블록만 다시 만든다. 대본 전체를 다시 만들지 않는다 — 다른 블록은 관리자가 이미 승인했을 수 있다.

- 다시 만들 블록: 고른 블록 + `BLOCK_BASIS`에서 그 블록을 입력으로 받는 뒤 블록(U0 → U2 → U4, U2 → U4). 연동 표의 정본이
  `BLOCK_BASIS`라 여기서 따로 표를 두지 않는다. 다시 만들 블록이 기대는 앞 블록이 아예 없으면(증거조사만 있는 대본 등) 그 앞
  블록도 함께 만든다.
- 다시 만들지 않는 블록은 지금 값(부르는 쪽이 넘긴 `content`)을 입력으로 쓴다. 예: U4만 다시 만들면 지금 U0 쟁점 · U2 변호인
  입장을 본다.
- 앞 블록을 만들지 못하면 그 블록에 기대는 뒤 블록은 만들지 않고 `pending`에 남긴다 — 옛 앞 블록으로 만들면 관리자가 바꾸려던
  값이 그대로 남는다. 호출 한도가 소진되면 그 자리에서 멈춘다(`stopped`).
- 만든 뒤 대본 전체를 검수하고(BE-122), 오류 줄 고쳐 쓰기(BE-122 2차)는 다시 만든 블록에만 한다.
- 판은 지금 판에서 이어 오른다. `basis`에는 그때 본 앞 블록의 판(다시 만들지 않은 블록은 지금 판)을 남긴다(`build_blocks`).

분류에는 민감 플래그(`sensitiveFlags`)가 **반드시** 있어야 한다. 백엔드는 분류(`legal_case.classification`)와 민감 플래그
(`sensitive_flags`, 관리자가 A-11로 고친다)를 따로 저장해서, 합치지 않고 넘기면 플래그가 빠진다. `classification_errors`는
빠진 플래그를 빈 목록으로 읽으므로 민감 사건이 안내 · 표현 가드 없이 조용히 다시 만들어진다(PR #370 리뷰). 그래서 여기서는 키가
없으면 거절한다. 빈 목록은 "민감 요소 없음"으로 받는다.
"""

import copy
import json

from llm import LLMQuotaExhaustedError
from llm import call as default_call
from script_assembler import (BLOCK_BASIS, BLOCK_UNITS, UNIT_STAGES, ask_unit, build_blocks, check_closing, check_design,
                              check_intro, check_opening, classification_errors, closing_request, design_request,
                              evidence_blocks, intro_request, opening_lines, opening_request, public_case,
                              strip_line_prefix)
from script_generation import generate_evidence_lines
from script_repair import repair_units
from script_review import ERROR, review_failed, review_script

# 블록마다 입력으로 받는 앞 블록 (U2는 U0, U4는 U0 · U2)
INPUT_BLOCKS = {unit: tuple(dep for dep in BLOCK_BASIS[unit] if dep in BLOCK_UNITS) for unit in BLOCK_UNITS}


class RegenerationError(Exception):
    """재생성 입력이 잘못됐다. args[0]은 오류 목록이다."""


def regeneration_classification_errors(cls):
    """재생성에 넘기는 분류 검사: 대본 전체 생성과 같은 검사 + 민감 플래그 키가 있어야 한다(모듈 설명)."""
    errors = classification_errors(cls)
    if isinstance(cls, dict) and "sensitiveFlags" not in cls:
        errors.append("stages.script.classification에 sensitiveFlags가 없습니다 — 재생성은 민감 플래그를 합쳐 넘겨야 한다"
                      "(없으면 빈 목록 [])")
    return errors


def unit_list_errors(units):
    if not isinstance(units, list) or not units:
        return ["다시 만들 블록(regenerate)은 U0 ~ U4 중 하나 이상의 목록이다"]
    errors = [f"다시 만들 블록에 모르는 단위가 있습니다: {unit}" for unit in units if unit not in BLOCK_UNITS]
    if len(set(map(str, units))) != len(units):
        errors.append("다시 만들 블록에 같은 단위가 두 번 있습니다")
    return errors


def block_errors(blocks):
    """지금 블록(`{단위: {"content", "revision"}}`) 검사."""
    if not isinstance(blocks, dict):
        return ["지금 블록(blocks)은 {단위: {content, revision}} 객체다"]
    errors = []
    for unit, block in blocks.items():
        if unit not in BLOCK_UNITS:
            errors.append(f"지금 블록에 모르는 단위가 있습니다: {unit}")
            continue
        if not isinstance(block, dict) or block.get("content") is None:
            errors.append(f"지금 블록 {unit}에 content가 없습니다")
            continue
        content, revision = block["content"], block.get("revision")
        if not isinstance(content, list if unit == "U3" else dict):
            errors.append(f"지금 블록 {unit}의 content는 {'목록' if unit == 'U3' else '객체'}다")
        if not isinstance(revision, int) or isinstance(revision, bool) or revision < 1:
            errors.append(f"지금 블록 {unit}의 revision은 1 이상 정수다")
    return errors


def regeneration_targets(units, existing):
    """다시 만들 블록 (U0 → U4 순서). 고른 블록 + 그 블록을 입력으로 받는 뒤 블록 + 없는 앞 블록."""
    targets = set(units)
    while True:
        later = {unit for unit in BLOCK_UNITS if any(dep in targets for dep in INPUT_BLOCKS[unit])}
        missing = {dep for unit in targets for dep in INPUT_BLOCKS[unit] if dep not in existing}
        added = (later | missing) - targets
        if not added:
            return [unit for unit in BLOCK_UNITS if unit in targets]
        targets |= added


def _existing(blocks, paper):
    """지금 블록을 generate_units 반환값 모양으로. 다시 만들지 않는 블록이 다시 만드는 블록의 입력 · 검수 대상이 된다."""
    units = {unit: {"unit": unit, "value": copy.deepcopy(block["content"]), "errors": [], "warnings": [], "attempts": 0}
             for unit, block in blocks.items() if unit != "U3"}
    evidence = copy.deepcopy(blocks["U3"]["content"]) if "U3" in blocks else []
    return {"units": units, "paper": paper, "evidence": evidence, "stopped": False, "pending": []}


def regenerate_blocks(case, judgment_output, cast, cls, blocks, units, *, model, paper=None, caller=default_call,
                      max_retries=2, temperature=None, max_tokens=6000, evidence_max_tokens=2000, timeout=150,
                      evidence_timeout=60, delay=0.0, repair_retries=2, log=None):
    """고른 블록(`units`)과 그에 기대는 블록을 다시 만든다. `blocks`는 지금 블록 `{단위: {"content", "revision"}}`,
    `paper`는 지금 기사(U1을 다시 만들 때 필요하다).

    돌려주는 값: {"units": 다시 만들기로 한 블록, "regenerated": 실제로 만든 블록, "pending": 만들지 못한 블록,
    "stopped": 호출 한도로 멈췄는가, "blocks": 만든 블록(build_blocks 형식), "meta": 메타, "review": 대본 전체 검수 결과,
    "repairs": 고쳐 쓰기 기록, "publishable": 만든 블록에 검수 오류가 없는가}"""
    errors = unit_list_errors(units) + block_errors(blocks) + regeneration_classification_errors(cls)
    targets = [] if errors else regeneration_targets(units, blocks)
    if "U1" in targets and not paper:
        errors.append("1막 해설(U1)을 다시 만들려면 기사(paper)가 필요하다")
    if errors:
        raise RegenerationError(errors)
    if log:
        log(f"다시 만들 블록: {' · '.join(targets)} (고른 블록 {' · '.join(units)})")
    generated = _existing(blocks, paper)
    value = lambda unit: (generated["units"].get(unit) or {}).get("value")  # noqa: E731
    opts = dict(model=model, caller=caller, max_retries=max_retries, temperature=temperature,
                max_tokens=max_tokens, timeout=timeout, delay=delay, log=log)
    source = json.dumps(public_case(case, UNIT_STAGES["U0"]), ensure_ascii=False)
    made, failed, stopped = [], set(), False
    try:
        for unit in targets:
            if any(dep in failed for dep in INPUT_BLOCKS[unit]):
                failed.add(unit)  # 바꾸려던 앞 블록이 그대로라 옛 값으로 만들지 않는다
                continue
            if unit == "U3":
                guide, sensitive = evidence_blocks(cls)
                lines, stopped = generate_evidence_lines(
                    case, judgment_output, cast, model=model, caller=caller, max_retries=max_retries,
                    temperature=temperature, max_tokens=evidence_max_tokens, timeout=evidence_timeout, delay=delay,
                    log=log, guide=guide, sensitive=sensitive)
                if stopped:  # 줄이 모자란 증거조사로 지금 블록을 덮지 않는다
                    break
                generated["evidence"] = lines
                made.append(unit)
                continue
            if unit == "U0":
                result = ask_unit("U0", *design_request(case, cls), lambda v: check_design(v, cls["issue"]),
                                  lambda v: [q["q"] for q in v["questions"][:3]], source, **opts)
            elif unit == "U1":
                result = ask_unit("U1", *intro_request(paper, cls), check_intro,
                                  lambda v: [strip_line_prefix(x) for x in v["lines"][:5]],
                                  json.dumps(paper, ensure_ascii=False), **opts)
            elif unit == "U2":
                result = ask_unit("U2", *opening_request(case, cls, cast, value("U0")), check_opening, opening_lines,
                                  source, **opts)
            else:
                result = ask_unit("U4", *closing_request(case, cls, cast, value("U0"), value("U2")), check_closing,
                                  lambda v: [v["prosecutor"], v["defense"]], source, **opts)
            if result["value"]:
                generated["units"][unit] = result
                made.append(unit)
            else:
                failed.add(unit)
    except LLMQuotaExhaustedError as e:
        stopped = True
        if log:
            log(f"⚠ 호출 한도 소진 — 남은 블록은 다시 만들지 않습니다: {e}")
    if stopped and log:
        log("⚠ 호출 한도 소진으로 멈췄습니다 — 만들지 못한 블록은 지금 값을 둡니다")
    pending = [unit for unit in targets if unit not in made]
    try:
        generated["review"] = review_script(case, judgment_output, generated, strip_prefix=strip_line_prefix)
    except Exception as e:  # noqa: BLE001 — generate_units와 같다: 검사가 실패해도 만든 블록은 남기고 공개만 막는다
        if log:
            log(f"⚠ 검수를 돌리지 못했습니다 — 만든 블록은 남기고 공개할 수 없게 둡니다: {e!r}")
        generated["review"] = [review_failed(e)]
    if repair_retries and not stopped and any(i["severity"] == ERROR and i["unit"] in made for i in generated["review"]):
        generated = repair_units(case, judgment_output, cast, cls, generated, model=model, caller=caller,
                                 retries=repair_retries, max_retries=max_retries, temperature=temperature,
                                 max_tokens=max_tokens, evidence_max_tokens=evidence_max_tokens, timeout=timeout,
                                 evidence_timeout=evidence_timeout, delay=delay, log=log, units=made)
    new_blocks, meta = build_blocks(case, cast, cls, generated, base={u: b["revision"] for u, b in blocks.items()},
                                    units=made)
    review = generated["review"]
    publishable = bool(made) and not pending and not any(
        i["severity"] == ERROR and (i["unit"] in made or i["unit"] not in BLOCK_UNITS) for i in review)
    return {"units": targets, "regenerated": made, "pending": pending, "stopped": stopped, "blocks": new_blocks,
            "meta": meta, "review": review, "repairs": generated.get("repairs", []), "publishable": publishable}
