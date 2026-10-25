"""판결 체험 대본 — 증거조사 대사 생성 (BE-107, WBS 7.5.6 대본 생성 B안).

범위는 해송님과 합의한 것만이다(Jira BE-107 댓글, 2026-10-24 ~ 25):

- **이번 범위**: 증거조사 대사 — 판단 요소(`factors[]`) 1건당 대사 1줄. 방향이 `UP`(가중)이면 검사 측,
  `DOWN`(감경)이면 변호인 측이 소개한다(`frontend/src/content/case-1/script.json` 2막 9 ~ 15번과 같은 순서 —
  검사 측 먼저, 변호인 측 다음).
- **이번 범위 밖(후속)**: 피고인신문 질의응답. `case-1` 대본의 질문 3개(계획 여부 · 범행 뒤 행동 · 공소사실
  인정 여부)는 판단 요소에서 기계적으로 끌어낼 수 없는 **절차적 정형 질문**이다 — 실제 서비스 사건 목록이
  정해지고 `FE-33` 수작업 대본이 쌓여 "질문이 몇 개이고 답이 예/아니오 중 어느 쪽인지"를 판단할 근거
  (예: `case_input.json`에 계획성 · 도주 여부 같은 플래그)가 생긴 뒤에 다시 설계한다(`COMMON-65` "추후
  재검토" 참고). 공소사실 · 모두진술 · 쟁점정리 · 1막 기사 해설 · 최종의견도 같은 이유로 범위 밖이다.

말투 · 배역 · 가드레일은 `docs/character/character-asset-architecture.md` 5-2(5블록 구조: BASE PERSONA →
ROLE → SKIN → SCENE CONTEXT → GUARDRAILS, SKIN은 사건 화면에서 빠진다)와 `docs/decisions/COMMON-65.md`를
따른다. 블록 원문은 `tools/ai-judgment/prompts/script_*.md`에 있다.

**배역(`cast`)은 호출하는 쪽이 설정으로 넘긴다 — 이 모듈은 자동으로 정하지 않는다**(해송님 정정, 2026-10-24).
`cast`는 `{"prosecutor": "naero" 또는 "nambul", "defense": 나머지 하나}` 형태다.

항목(판단 요소) 단위로 모델을 한 번씩 불러 대사 한 줄을 받는다 — 막 전체를 한 번에 JSON으로 요청하지
않는다(해송님 승인 방식, 형식 오류 · 환각 위험을 줄인다).
"""

import re
import time

from common import read_prompt
from llm import LLMError, LLMQuotaExhaustedError
from llm import call as default_call

ROLE_BY_DIRECTION = {"UP": "prosecutor", "DOWN": "defense"}
CHARACTERS = ("naero", "nambul")
MAX_LINES = 2
BANNED_WORDS = ("정령", "명령", "배우", "천사", "악마", "MBTI",
                "로울", "로봉", "로북", "엄벌", "중도", "자비")


class ScriptGenerationError(Exception):
    """설정 오류(cast 등). 호출 실패는 포함하지 않는다 — 그건 LLMError다."""


def cast_errors(cast):
    """stages.script.cast 설정 검사. 오류 목록."""
    errors = []
    if not isinstance(cast, dict):
        return ["stages.script.cast는 객체다"]
    for role in ("prosecutor", "defense"):
        character = cast.get(role)
        if character not in CHARACTERS:
            errors.append(f"stages.script.cast.{role}는 {' 또는 '.join(CHARACTERS)} 중 하나다")
    if cast.get("prosecutor") and cast.get("prosecutor") == cast.get("defense"):
        errors.append("stages.script.cast.prosecutor와 defense는 같은 캐릭터일 수 없다 (기본형은 서로 다른 캐릭터가 맡는다)")
    return errors


def ordered_evidence_factors(judgment_output):
    """증거로 소개할 판단 요소 — 검사 측(UP) 먼저, 변호인 측(DOWN) 다음(case-1과 같은 순서).

    `judgment_output`은 사건 입력(case.json)이 아니라 **select 단계가 고른 판결의 출력**이다
    (`factorId` · `direction` · `reason`을 가진 쪽 — case.json의 `factors[]`는 `label`만 있고
    아직 방향이 없다. `frontend/src/content/case-1/script.json`의 증거 카드가 "API 8
    GET .../experience/verdict-form factors"를 근거로 적어 둔 것과 같다). 방향이 없는(선택 안 함)
    요소는 증거로 소개하지 않는다."""
    factors = [f for f in judgment_output.get("factors", []) if f.get("direction") in ROLE_BY_DIRECTION]
    return sorted(factors, key=lambda f: f["direction"] != "UP")


def build_evidence_prompt(case, factor, cast):
    """판단 요소 하나를 법정 증거로 소개하는 대사의 (system, user, role, character)를 만든다."""
    role = ROLE_BY_DIRECTION[factor["direction"]]
    character = cast[role]
    system = "\n\n".join([
        read_prompt(f"script_persona_{character}.md").rstrip(),
        read_prompt(f"script_role_{role}.md").rstrip(),
        read_prompt("script_guardrails.md").rstrip(),
    ])
    user = (
        "[SCENE CONTEXT]\n"
        f"사건: {case['title']}\n"
        f"사건 개요: {case['overview']}\n"
        f"지금 소개할 판단 요소: {factor['reason']}\n\n"
        "위 판단 요소 하나를 법정에서 증거로 소개하는 대사를 쓰세요."
    )
    return system, user, role, character


def validate_line(text, factor):
    """생성된 대사 한 줄을 검사한다. errors는 재시도 대상, warnings는 그대로 쓰되 기록만 남긴다."""
    errors, warnings = [], []
    stripped = (text or "").strip()
    lines = [line for line in stripped.splitlines() if line.strip()]
    if not lines:
        errors.append("빈 응답입니다")
    elif len(lines) > MAX_LINES:
        errors.append(f"대사가 {MAX_LINES}줄을 넘습니다 ({len(lines)}줄)")
    for word in BANNED_WORDS:
        if word in stripped:
            errors.append(f"금지어 포함: {word}")
    source = factor.get("reason", "")
    for number in re.findall(r"\d+", stripped):
        if number not in source:
            warnings.append(f"판단 요소 원문에 없는 숫자 '{number}'가 들어갔습니다 — 확인 필요")
    return {"ok": not errors, "errors": errors, "warnings": warnings}


def generate_evidence_lines(case, judgment_output, cast, *, model, caller=default_call, max_retries=2,
                             temperature=None, max_tokens=200, timeout=60, delay=0.0, log=None):
    """판단 요소마다 증거조사 대사 한 줄을 생성한다. `case`는 제목 · 개요(SCENE CONTEXT)만 쓰고,
    판단 요소(방향 · 이유)는 `judgment_output`(select 단계가 고른 판결 출력)에서 가져온다
    (`ordered_evidence_factors` 설명 참고).

    돌려주는 값: (lines, stopped) — lines는 순서대로 쌓인 결과 목록
    [{factorId, role, character, text, validation, attempts}], stopped는 호출 한도 소진으로
    남은 항목을 건너뛰었으면 True(axis 단계와 같은 정책 — 받은 것만으로 집계하고 멈춘다).
    """
    results, stopped = [], False
    for factor in ordered_evidence_factors(judgment_output):
        system, user, role, character = build_evidence_prompt(case, factor, cast)
        text, validation, attempts = None, None, 0
        for attempt in range(max_retries + 1):
            attempts += 1
            if attempt and delay:
                time.sleep(delay)
            try:
                result = caller(model, system, user, temperature=temperature, max_tokens=max_tokens,
                                timeout=timeout, json_output=False)
            except LLMQuotaExhaustedError as e:
                if log:
                    log(f"⚠ 호출 한도 소진 — 남은 판단 요소는 건너뜁니다: {e}")
                stopped = True
                break
            except LLMError as e:
                validation = {"ok": False, "errors": [f"호출 실패: {e}"], "warnings": []}
                continue
            text = result.text
            validation = validate_line(text, factor)
            if validation["ok"]:
                break
            if log:
                log(f"[요소 {factor['factorId']}] {validation['errors'][0]}"
                    + (" — 다시 요청합니다" if attempt < max_retries else " — 실패로 남깁니다"))
        if stopped:
            break
        results.append({
            "factorId": factor["factorId"], "role": role, "character": character,
            "text": (text or "").strip() if validation and validation["ok"] else None,
            "validation": validation, "attempts": attempts,
        })
    return results, stopped


def balance_warning(lines):
    """검사 · 변호인 대사 분량이 한쪽으로 심하게 쏠렸는지 본다(해송님 요청 — 분량 · 어조를 맞춰야 함).

    길이만으로는 어조까지 볼 수 없어 분량(글자 수)만 본다. 한쪽 평균이 다른 쪽의 1.5배를 넘으면 경고
    문자열을, 비교할 수 없으면(어느 한쪽이 비어 있음) None을 돌려준다. 자동으로 고치지는 않는다 — 관리자
    후검수(BE-108)에서 보도록 경고만 남긴다.
    """
    by_role = {"prosecutor": [], "defense": []}
    for line in lines:
        if line["text"]:
            by_role[line["role"]].append(len(line["text"]))
    if not by_role["prosecutor"] or not by_role["defense"]:
        return None
    avg_p = sum(by_role["prosecutor"]) / len(by_role["prosecutor"])
    avg_d = sum(by_role["defense"]) / len(by_role["defense"])
    longer, shorter = max(avg_p, avg_d), min(avg_p, avg_d)
    if shorter and longer / shorter > 1.5:
        side = "검사" if avg_p > avg_d else "변호인"
        return f"{side} 쪽 대사가 평균적으로 눈에 띄게 깁니다(검사 {avg_p:.0f}자 · 변호인 {avg_d:.0f}자) — 분량을 맞춰야 하는지 확인하세요"
    return None
