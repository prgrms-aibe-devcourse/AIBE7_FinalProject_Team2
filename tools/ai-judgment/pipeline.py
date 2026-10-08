"""판결문 → 비식별화 → (사전 학습 점검) → AI 판결 생성 → 회차 선택 → 적재까지 이어서 실행한다 (BE-31).

사용법:
    python3 pipeline.py run cases/my-case.pipeline.json                   # 설정대로 실행 (끝난 단계는 건너뛰고 이어서)
    python3 pipeline.py run cases/my-case.pipeline.json --from generate   # generate부터 다시
    python3 pipeline.py run cases/my-case.pipeline.json --until select    # 적재 전까지만
    python3 pipeline.py run cases/my-case.pipeline.json --skip contamination
    python3 pipeline.py status cases/my-case.pipeline.json                # 단계별 상태

단계: extract → court → contamination → generate → select → axis → load
- extract: case-extractor로 판결문을 비식별화 · 구조화한다. 서비스 대상이 아니라고 판정되면 멈춘다(설정으로 끌 수 있음)
- contamination: 모델마다 사전 학습 점검을 N회 자동으로 한다(기본 꺼짐). CONTAMINATED는 멈춤, SUSPECT는 진행(설정)
- generate: 모델마다 N회 생성 · 검증한다. 검증을 통과한 회차가 없으면 최대 K회 더 생성한다
- select: 검증을 통과한 회차 중 하나를 고른다 (consensus · first-valid · fewest-warnings · manual)
- axis: 판단 요소의 가치관 축만 같은 모델에 N회 물어 투표한다 (BE-49). 표가 갈린 요소는 관리자 확인 필요로 표시한다
- load: 사건(DRAFT) + AI 판결(비공개 · PENDING) 적재 SQL을 private-seed/loads/에 남기고 **로컬 DB에만** 적재한다
사람 검수 단계는 없다. 적재한 사건 · AI 판결은 사용자에게 보이지 않고, 관리자가 나중에 검수 · 공개한다(후검수).

설정 예시는 pipeline.example.json, 설명은 README "파이프라인 (BE-31)".
"""

import argparse
import copy
import datetime
import hashlib
import json
import os
import shutil
import subprocess
import sys
import time
import urllib.parse
from pathlib import Path

from build_prompt import InputError, build_prompt
from case_seed_sql import DEFAULT_SOURCE_ORG, CaseSeedError, build_case_sql, resolve_sources
from court_seed_sql import CourtSeedError, build_court_sql
from check_contamination import DEFAULT_CRITERIA, aggregate, build_contamination_prompt, classify, criteria_with
from axis_vote import AxisVoteError, aggregate_votes, apply_votes, build_axis_prompt, parse_axis_answer
from common import AXIS_PROMPT_VERSION, TOOL_DIR, load_json, write_json
from compare import _majority, direction_table, load_runs
from generate import RUNS_DIR, GenerateError, generate_runs, model_slug, prompt_digest
from llm import (DEFAULT_MAX_ATTEMPTS, DEFAULT_MAX_WAIT, LLMError, LLMQuotaExhaustedError, api_key, call,
                 check_retry, configure_retry, free_tier_model_list, has_free_tier, model_provider,
                 parse_model_spec)
from to_seed_sql import NAME_MAX_LENGTH, build_sql
from validate_output import parse_output

STAGES = ("extract", "court", "contamination", "generate", "select", "axis", "load")
PIPELINE_DIR = TOOL_DIR / "out" / "pipeline"
PUBLIC_ROOT = TOOL_DIR.parent.parent  # 공개 저장소 루트
PRIVATE_SEED_DIR = PUBLIC_ROOT / "backend" / "private-seed"
CASES_DIR = TOOL_DIR / "cases"
EXTRACTOR_DIR = TOOL_DIR / "case-extractor"
SELECT_STRATEGIES = ("consensus", "first-valid", "fewest-warnings", "manual")
LOCAL_HOSTS = ("localhost", "127.0.0.1", "::1")

DEFAULT_CONFIG = {
    "name": None,
    # 판결문 원본. 여러 개면 같은 사건의 심급별 판결문(1심 · 항소심). 자동으로 못 찾은 원본 정보는 여기에 직접 넣는다
    # [{"path": "...", "caseNumber": null, "courtName": null, "decidedAt": null, "courtLevel": null, "note": null}]
    "sources": [],
    "finalSourceIndex": None,  # 최종 확정 판결 번호(0부터). 없으면 심급이 가장 높은 판결
    # 사건 발생일 YYYY-MM-DD. 주면 추출 결과(source_internal.json incidentDate)보다 우선한다 (BE-38)
    "incidentDate": None,
    # LLM 호출 재시도 (BE-36). 과부하(503 · 529) · 한도(429) · 서버 오류 때 요청 한 건을 보내는 최대 횟수(첫 시도 포함)와
    # 한 번에 기다릴 최대 시간(초). 무료 등급이면 각 단계의 delay(호출 사이 대기)도 함께 둔다 (README "무료 등급 한도 대응")
    "retry": {"maxAttempts": DEFAULT_MAX_ATTEMPTS, "maxWait": DEFAULT_MAX_WAIT},
    # 무료 모델 자동 사용 (BE-45): generate.models · contamination.models에 "free:<공급자>"를 적으면 free_models.json의 모델을
    # 쓴다. max개만 동시에 쓰고(안전장치), 일 한도로 막히면 다음 모델이 자리를 채운다. include는 쓸 모델 ID(앞이 우선), exclude는 뺄 모델 ID
    "freeModels": {"max": 3, "include": None, "exclude": []},
    # 판결문 원문을 보내는 extract · court의 모델 목록 끝에 "free:<공급자>"를 백업으로 쓸 수 있게 허용한다 (기본 꺼짐).
    # 무료 등급은 입력을 제품 개선에 쓰고 사람이 검토할 수 있으므로 공개된 판결문에만 켠다 (README "무료 등급 모델")
    "allowFreeTierForJudgment": False,
    # extract를 건너뛸 때 쓸 파일 (extract를 돌리면 그 결과가 우선)
    # courtDraft: 재판부 판결(BE-14 형식) 파일. court 단계를 끄고 사람이 쓴 판결을 넣을 때 쓴다 (BE-38)
    "inputs": {"case": None, "court": None, "report": None, "source": None, "courtDraft": None},
    "stages": {
        # model: Claude는 모델 ID(또는 anthropic:모델ID), 다른 공급자는 openai:모델ID · gemini:모델ID (BE-35). maxTokens는 Claude 외 공급자의 출력 상한
        # 목록으로 주면 앞 모델이 과부하 · 한도로 막혔을 때(재시도를 다 쓴 뒤) 다음 모델로 넘어간다 (BE-45)
        "extract": {"enabled": True, "model": "claude-opus-5-5", "effort": "high", "requireEligible": True,
                    "maxTokens": None},
        # 재판부 판결(COURT) 초안 자동 생성 (BE-38). model이 null이면 extract 모델을 쓴다. 결과는 비공개로 적재된다
        "court": {"enabled": True, "model": None, "maxTokens": 16000},
        "contamination": {
            "enabled": False, "runs": 10, "models": None,  # models가 없으면 generate.models를 점검한다
            "onContaminated": "stop", "onSuspect": "continue", "onInsufficient": "stop", "delay": 0.0, "timeout": 300,
            # 판정 기준 (BE-37, check_contamination.py). 비우면 기본값: 최소 응답 5개, 징역 허용 오차
            # max(1, min(6, 15%))개월, 벌금 10%, 정확히 맞힘 50% 이상 CONTAMINATED, 가까움 50% 이상 SUSPECT
            "criteria": {},
        },
        "generate": {
            "enabled": True, "models": [], "runs": 3, "maxRetries": 3,
            "temperature": None, "maxTokens": 16000, "timeout": 300, "delay": 0.0,
        },
        "select": {"enabled": True, "strategy": "consensus", "model": None, "run": None},
        # 가치관 축 분류 투표 (BE-49). 같은 모델로 runs회 묻고 요소별로 표를 센다. 최다표가 runs의 과반이 아니면(동률 · 유효 응답 부족 포함) 확인 필요.
        # model이 null이면 generate.models의 첫 모델(free: 제외)을 쓴다(비식별화한 사건 내용이라 generate와 같은 공급자로 보낸다).
        # 응답 형식이 틀리면 그 회차를 최대 maxRetries번 다시 묻고, 그래도 틀리면 집계에서 뺀다
        "axis": {"enabled": True, "model": None, "runs": 5, "maxRetries": 2, "temperature": None, "maxTokens": 4000,
                 "timeout": 120, "delay": 0.0},
        "load": {
            # court: 재판부 판결 초안이 있으면 비공개로 함께 적재한다 (BE-38)
            "enabled": True, "case": True, "court": True, "applyToDb": True,
            "sqlDir": "../../backend/private-seed/loads",  # tools/ai-judgment 기준
            "sourceOrg": DEFAULT_SOURCE_ORG, "sourceNote": None,
            "db": {"mode": "docker", "container": "lawnambul-postgres", "user": "lawnambul", "database": "lawnambul",
                   "url": None},
        },
    },
}
CONTAMINATION_ACTIONS = {"onContaminated": ("stop", "exclude"), "onSuspect": ("continue", "exclude", "stop"),
                         "onInsufficient": ("stop", "continue", "exclude")}
# 판정 기준 값의 허용 범위 (정수 키 · 비율 키)
CRITERIA_INT_KEYS = ("minAnswered", "closeMinMonths", "closeMaxMonths")
CRITERIA_RATIO_KEYS = ("closeRatio", "closeFineRatio", "exactRatio", "suspectRatio")


class PipelineError(Exception):
    pass


# ---------------------------------------------------------------- 설정 · 상태

def _merge(base, override):
    merged = copy.deepcopy(base)
    for key, value in (override or {}).items():
        if isinstance(value, dict) and isinstance(merged.get(key), dict):
            merged[key] = _merge(merged[key], value)
        else:
            merged[key] = value
    return merged


def load_config(path):
    path = Path(path)
    config = _merge(DEFAULT_CONFIG, load_json(path))
    config["_dir"] = str(path.resolve().parent)
    validate_config(config)
    return config


FREE_PREFIX = "free:"


def is_free_token(spec):
    """`free:<공급자>` — free_models.json의 그 공급자 무료 모델을 쓰라는 표시 (BE-45)."""
    return isinstance(spec, str) and spec.strip().lower().startswith(FREE_PREFIX)


def free_token_provider(spec):
    return spec.strip().lower()[len(FREE_PREFIX):].strip()


def free_tokens(config):
    stages = config["stages"]
    return [spec.strip().lower() for spec in list(stages["generate"]["models"]) + list(stages["contamination"]["models"] or [])
            if is_free_token(spec)]


def free_pool(config, providers=None):
    """freeModels의 include · exclude를 적용한 무료 모델 목록(`공급자:모델ID`). include가 있으면 그 순서가 우선순위다."""
    cfg = config["freeModels"]
    include, exclude = cfg.get("include"), set(cfg.get("exclude") or [])
    pool = []
    for spec in free_tier_model_list():
        provider, _, model = spec.partition(":")
        if (providers is not None and provider not in providers) or (include is not None and model not in include) \
                or model in exclude:
            continue
        pool.append(spec)
    if include is not None:
        pool.sort(key=lambda spec: include.index(spec.partition(":")[2]))
    return pool


def free_models_errors(config):
    errors = []
    cfg = config["freeModels"]
    if not isinstance(cfg, dict) or set(cfg) - {"max", "include", "exclude"}:
        return ["freeModels는 max · include · exclude만 가진 객체다"]
    if not isinstance(cfg["max"], int) or isinstance(cfg["max"], bool) or cfg["max"] < 1:
        errors.append("freeModels.max는 1 이상의 정수다 (동시에 쓸 무료 모델 수)")
    for key in ("include", "exclude"):
        value = cfg.get(key)
        if value is not None and (not isinstance(value, list) or not all(isinstance(m, str) and m.strip() for m in value)):
            errors.append(f"freeModels.{key}는 모델 ID 문자열 목록이다 (공급자 접두어 없이, 예: gemini-2.5-flash)")
    if errors:
        return errors
    known = {spec.partition(":")[0] for spec in free_tier_model_list()}
    for token in sorted(set(free_tokens(config))):
        provider = free_token_provider(token)
        if provider not in known:
            errors.append(f"{token}: free_models.json에 {provider!r} 공급자가 없다 (있는 공급자: {', '.join(sorted(known)) or '없음'})")
        elif not free_pool(config, {provider}):
            errors.append(f"{token}: freeModels의 include · exclude를 적용하면 쓸 수 있는 무료 모델이 없다")
    stages = config["stages"]
    allow = config.get("allowFreeTierForJudgment")
    if not isinstance(allow, bool):
        errors.append("allowFreeTierForJudgment는 true · false다")
    for stage in ("extract", "court"):
        value = stages[stage].get("model")
        models = [m for m in ([value] if isinstance(value, str) else value or []) if isinstance(m, str)]
        tokens = [m for m in models if is_free_token(m)]
        if not tokens:
            continue
        if allow is not True:
            errors.append(f"stages.{stage}.model에는 free:를 쓸 수 없다 (판결문 원문을 보내는 단계는 유료 키만 쓴다. 무료 등급을 "
                          "마지막 백업으로 허용하려면 allowFreeTierForJudgment를 true로 한다)")
            continue
        if not any(not is_free_token(m) for m in models):
            errors.append(f"stages.{stage}.model: free:는 백업이다. 유료 모델을 하나 이상 앞에 둔다")
        elif any(is_free_token(m) for m in models[:max(i for i, m in enumerate(models) if not is_free_token(m)) + 1]):
            errors.append(f"stages.{stage}.model: free:는 목록 맨 끝에 둔다 (유료 모델이 모두 막힌 뒤에만 쓰는 마지막 수단)")
        known_tokens = {t.strip().lower() for t in tokens}
        for token in sorted(known_tokens):
            provider = free_token_provider(token)
            if provider not in known:
                errors.append(f"{token}: free_models.json에 {provider!r} 공급자가 없다")
            elif not free_pool(config, {provider}):
                errors.append(f"{token}: freeModels의 include · exclude를 적용하면 쓸 수 있는 무료 모델이 없다")
    return errors


def resolve_free_models(config, state, log, fresh=False):
    """`free:` 표시를 실제 모델로 펼쳐 state["freeModels"]에 기록한다 (BE-45).

    pool: 조건을 통과한 무료 모델 전체, active: 지금 쓰는 max개, reserve: 대기(일 한도로 막힌 모델 자리를 채운다).
    설정이 같으면 이어 실행해도 같은 목록을 쓴다. fresh이거나 설정이 바뀌었으면 처음부터 다시 펼친다.
    """
    tokens = sorted(set(free_tokens(config)))
    if not tokens:
        state.pop("freeModels", None)
        return
    cfg = config["freeModels"]
    source = {"tokens": tokens, "max": cfg["max"], "include": cfg.get("include"), "exclude": sorted(cfg.get("exclude") or []),
              "models": free_tier_model_list()}
    existing = state.get("freeModels")
    if existing and not fresh and existing.get("source") == source:
        return
    pool = free_pool(config, {free_token_provider(t) for t in tokens})
    state["freeModels"] = {"source": source, "pool": pool, "active": pool[:cfg["max"]], "reserve": pool[cfg["max"]:],
                           "replaced": [], "skipped": [], "kept": []}
    log(f"무료 모델 자동 사용: {', '.join(pool[:cfg['max']])} (동시 {cfg['max']}개까지)"
        + (f" · 대기 {', '.join(pool[cfg['max']:])}" if pool[cfg["max"]:] else ""))


def expand_judgment_models(config, model, log=print):
    """extract · court 모델 설정의 `free:<공급자>`를 무료 백업 모델(freeModels.max개까지)로 펼친다. (모델 설정, 무료 백업 집합)

    free: 표시가 없으면 설정을 그대로 돌려준다. 백업 공급자의 API 키가 없으면 그 모델은 빼고 알린다(백업이라 실행을 막지 않는다).
    유료 모델과 같은 ID가 목록에 직접 적혀 있으면 직접 적은 쪽이 우선이고 무료 백업으로 치지 않는다."""
    raw = [model] if isinstance(model, str) else list(model)
    if not any(is_free_token(m) for m in raw):
        return model, set()
    models, free = [], set()
    for spec in raw:
        if not is_free_token(spec):
            if spec not in models:
                models.append(spec)
            continue
        for candidate in free_pool(config, {free_token_provider(spec)})[:config["freeModels"]["max"]]:
            if candidate in raw or candidate in models:
                continue
            try:
                api_key(parse_model_spec(candidate)[0])
            except LLMError as e:
                log(f"무료 백업 모델 {candidate} 제외: {e}")
                continue
            models.append(candidate)
            free.add(candidate)
    return models, free


def expand_models(state, raw):
    """모델 목록의 `free:<공급자>`를 state의 현재 활성 무료 모델로 바꾼다. 적힌 순서와 위치를 지킨다."""
    active = (state.get("freeModels") or {}).get("active", [])
    models = []
    for spec in raw:
        found = [s for s in active if s.startswith(free_token_provider(spec) + ":")] if is_free_token(spec) else [spec]
        models += [s for s in found if s not in models]
    return models


def replace_exhausted(state, spec, stage, log, vet=None):
    """일 한도가 소진된 무료 모델의 자리를 대기 모델이 채운다 (BE-45). 새 모델 이름 또는 None.

    새 모델은 새로운 독립 투표자다: 자기 이름으로 처음부터 기록하고, 소진된 모델이 이미 끝낸 회차는 그 모델 이름으로 남는다
    (generate 단계에서는 kept로 선택 후보에 남긴다). vet(새 모델)이 False면(사전 학습 점검에서 제외) 건너뛰고 다음 대기 모델을 본다.
    """
    fm = state.get("freeModels")
    if not fm or spec not in fm["active"]:
        return None
    while fm["reserve"]:
        new = fm["reserve"].pop(0)
        try:
            api_key(parse_model_spec(new)[0])
        except LLMError as e:
            fm["skipped"].append({"model": new, "reason": str(e)})
            log(f"[{new}] 대체 후보에서 제외: {e}")
            continue
        if vet is not None and not vet(new):
            fm["skipped"].append({"model": new, "reason": "사전 학습 점검에서 제외"})
            log(f"[{new}] 사전 학습 점검에서 제외되어 대체 후보에서 뺍니다")
            continue
        fm["active"][fm["active"].index(spec)] = new
        fm["replaced"].append({"model": spec, "by": new, "stage": stage})
        if stage == "generate":
            fm["kept"].append(spec)
        log(f"[{spec}] 일 한도 소진 → 무료 모델 {new}가 자리를 채웁니다 (자기 이름으로 처음부터 {stage})")
        return new
    log(f"[{spec}] 일 한도 소진 — 자리를 채울 무료 모델이 더 없습니다")
    return None


def chain_errors(label, value, expected):
    """stages.extract.model · stages.court.model 검사. 문자열이거나, 앞 모델이 막히면 다음 모델로 넘어가는 목록 (BE-45)."""
    models = [value] if isinstance(value, str) else value
    if not isinstance(models, list) or not models or not all(isinstance(m, str) and m.strip() for m in models):
        return [f"{label}은 {expected}이다"]
    errors = []
    if len({m.strip() for m in models}) != len(models):
        errors.append(f"{label}에 같은 모델이 두 번 들어 있다")
    for m in models:
        if is_free_token(m):
            continue  # free_models_errors가 검사한다 (allowFreeTierForJudgment · 위치)
        if ":" in m:
            try:
                if parse_model_spec(m)[0] == "manual":
                    errors.append(f"비식별화 · 재판부 판결 초안은 API 공급자만 쓴다 (manual 불가): {m}")
            except LLMError as e:
                errors.append(str(e))
    return errors


def free_tier_notices(config, steps):
    """무료 등급이 있는 모델이 들어 있을 때 안내 (BE-45). 목록은 free_models.json.

    모델에 무료 등급이 있다는 것이지 키가 무료라는 뜻은 아니므로(결제를 연결한 키는 유료) 막지 않고 알리기만 한다.
    실제 판결문을 보내는 단계(extract · court)는 무료 키를 쓰면 안 되므로 따로 강하게 알린다.
    """
    stages, notices = config["stages"], []
    judgment = [("비식별화", "extract", stages["extract"]["model"])]
    if "court" in steps:
        judgment.append(("재판부 판결 초안", "court", court_model(config)))
    for label, stage, model in judgment:
        if stage not in steps:
            continue
        listed = [model] if isinstance(model, str) else model
        tokens = [m.strip().lower() for m in listed if is_free_token(m)]
        free = [m for m in listed if not is_free_token(m) and has_free_tier(m)]
        if tokens:
            notices.append(f"⚠ 주의: {label}에 무료 등급 백업이 켜져 있습니다 ({', '.join(tokens)}, allowFreeTierForJudgment). "
                           "유료 모델이 모두 막히면 판결문 원문(정규식 마스킹만 거침)이 무료 등급으로 전송되어 제품 개선에 쓰이고 "
                           "사람이 검토할 수 있습니다. 공개된 판결문에만 쓰세요 (README)")
        if free:
            notices.append(f"주의: {label} 모델({', '.join(free)})에는 무료 등급이 있습니다. 실제 판결문은 결제를 연결한 "
                           "유료 키로만 보내세요 (무료 등급은 입력을 제품 개선에 쓰고 사람이 검토할 수 있습니다, README)")
    if {"contamination", "generate"} & set(steps):
        models = list(stages["generate"]["models"]) + list(stages["contamination"]["models"] or [])
        free = sorted({m.strip().lower() if is_free_token(m) else m for m in models if is_free_token(m) or has_free_tier(m)})
        if free:
            notices.append(f"무료 등급이 있는 모델: {', '.join(free)}. 무료 키라면 분당 · 일당 한도가 있으니 delay를 두고 "
                           "교차 호출 · 재시도 설정을 확인하세요 (README \"무료 등급 한도 대응\"). 무료 등급은 입력이 제품 개선에 쓰일 수 있습니다")
    return notices


def chain_text(model):
    """모델 설정(문자열 또는 목록) → 안내 문구용 `모델1 → 모델2`."""
    return " → ".join([model] if isinstance(model, str) else model)


def providers_text(model):
    """모델 설정의 실제 공급자 (외부 전송 안내용). 목록이면 대체 모델까지 모두 적는다."""
    return ", ".join(sorted({free_token_provider(m) if is_free_token(m) else model_provider(m)
                             for m in ([model] if isinstance(model, str) else model)}))


def validate_config(config):
    errors = []
    name = config.get("name") or ""
    if not name or not all(c.islower() or c.isdigit() or c == "-" for c in name):
        errors.append("name은 영어 소문자 · 숫자 · 하이픈으로 정한다 (사건을 특정할 수 없는 이름, 예: long-marriage-conflict)")
    stages = config["stages"]
    if config.get("incidentDate") is not None:
        try:
            datetime.date.fromisoformat(config["incidentDate"])
        except (TypeError, ValueError):
            errors.append("incidentDate는 YYYY-MM-DD다")
    if stages["extract"]["enabled"] and not config["sources"]:
        errors.append("extract를 쓰려면 sources에 판결문 경로를 넣는다")
    extract_model = stages["extract"]["model"]
    court_model = stages["court"].get("model")
    if court_model is not None:
        errors += chain_errors("stages.court.model", court_model, "null 또는 모델 이름 문자열 · 목록")
    court_max_tokens = stages["court"].get("maxTokens")
    if not isinstance(court_max_tokens, int) or isinstance(court_max_tokens, bool) or court_max_tokens <= 0:
        errors.append("stages.court.maxTokens는 양의 정수다")
    extract_max_tokens = stages["extract"].get("maxTokens")
    if extract_max_tokens is not None and (not isinstance(extract_max_tokens, int) or isinstance(extract_max_tokens, bool)
                                           or extract_max_tokens <= 0):
        errors.append("stages.extract.maxTokens는 null 또는 양의 정수다")
    errors += chain_errors("stages.extract.model", extract_model, "모델 이름 문자열 · 목록 (예: claude-opus-5-5, openai:모델ID)")
    errors += free_models_errors(config)
    for spec in stages["generate"]["models"] + (stages["contamination"]["models"] or []):
        if is_free_token(spec):
            continue  # free_models_errors가 검사한다
        try:
            if parse_model_spec(spec)[0] == "manual":
                errors.append(f"파이프라인은 API 공급자만 쓴다 (manual 불가): {spec}")
        except LLMError as e:
            errors.append(str(e))
    if stages["generate"]["enabled"] and not stages["generate"]["models"]:
        errors.append("stages.generate.models에 '공급자:모델ID'를 하나 이상 넣는다")
    for key in ("runs", "maxRetries"):
        if not isinstance(stages["generate"][key], int) or stages["generate"][key] < (1 if key == "runs" else 0):
            errors.append(f"stages.generate.{key}가 올바르지 않다")
    if not isinstance(stages["contamination"]["runs"], int) or stages["contamination"]["runs"] < 1:
        errors.append("stages.contamination.runs는 1 이상이다")
    for key, allowed in CONTAMINATION_ACTIONS.items():
        if stages["contamination"][key] not in allowed:
            errors.append(f"stages.contamination.{key}는 {' · '.join(allowed)} 중 하나다")
    errors += criteria_errors(stages["contamination"].get("criteria"), stages["contamination"]["runs"])
    select = stages["select"]
    if select["strategy"] not in SELECT_STRATEGIES:
        errors.append(f"stages.select.strategy는 {' · '.join(SELECT_STRATEGIES)} 중 하나다")
    if select["strategy"] == "manual" and not select["run"]:
        errors.append("manual 선택은 stages.select.run에 '공급자__모델/run-003' 형식으로 회차를 적는다")
    errors += axis_errors(config)
    if stages["load"]["db"]["mode"] not in ("docker", "psql"):
        errors.append("stages.load.db.mode는 docker · psql 중 하나다")
    retry = config["retry"]
    if not isinstance(retry, dict) or set(retry) - {"maxAttempts", "maxWait"}:
        errors.append("retry는 maxAttempts · maxWait만 가진 객체다")
    else:
        try:
            check_retry(retry["maxAttempts"], retry["maxWait"])
        except LLMError as e:
            errors.append(f"retry: {e}")
    if errors:
        raise PipelineError("설정 오류:\n- " + "\n- ".join(errors))


def axis_errors(config):
    """가치관 축 분류 투표 설정(stages.axis) 검사 (BE-49)."""
    cfg = config["stages"]["axis"]
    errors = []
    model = cfg.get("model")
    if model is not None:
        if not isinstance(model, str) or is_free_token(model):
            errors.append("stages.axis.model은 null 또는 '공급자:모델ID' 문자열이다 (free: 자동 선택은 쓰지 않는다)")
        else:
            try:
                if parse_model_spec(model)[0] == "manual":
                    errors.append(f"파이프라인은 API 공급자만 쓴다 (manual 불가): {model}")
            except LLMError as e:
                errors.append(str(e))
    elif cfg["enabled"] and not config["stages"]["generate"]["models"]:
        errors.append("stages.axis.model이 없으면 generate.models의 첫 모델을 쓴다. 둘 다 없으니 stages.axis.model을 넣거나 axis를 끈다")
    for key, minimum in (("runs", 1), ("maxRetries", 0), ("maxTokens", 1), ("timeout", 1)):
        value = cfg.get(key)
        if not isinstance(value, int) or isinstance(value, bool) or value < minimum:
            errors.append(f"stages.axis.{key}는 {minimum} 이상 정수다")
    return errors


def criteria_errors(criteria, runs):
    """사전 학습 점검 판정 기준(stages.contamination.criteria) 검사."""
    if criteria is None:
        return []
    if not isinstance(criteria, dict):
        return ["stages.contamination.criteria는 객체다"]
    errors = [f"stages.contamination.criteria.{key}는 알 수 없는 항목이다" for key in criteria if key not in DEFAULT_CRITERIA]
    for key in CRITERIA_INT_KEYS:
        value = criteria.get(key)
        if value is not None and (not isinstance(value, int) or isinstance(value, bool) or value < (1 if key == "minAnswered" else 0)):
            errors.append(f"stages.contamination.criteria.{key}는 0 이상(minAnswered는 1 이상) 정수다")
    for key in CRITERIA_RATIO_KEYS:
        value = criteria.get(key)
        if value is not None and (isinstance(value, bool) or not isinstance(value, (int, float)) or not 0 < value <= 1):
            errors.append(f"stages.contamination.criteria.{key}는 0 초과 1 이하 숫자다")
    if not errors:
        merged = criteria_with(criteria)
        if merged["closeMinMonths"] > merged["closeMaxMonths"]:
            errors.append("stages.contamination.criteria.closeMinMonths가 closeMaxMonths보다 크다")
        if isinstance(runs, int) and merged["minAnswered"] > runs:
            errors.append("stages.contamination.criteria.minAnswered가 runs보다 커서 항상 INSUFFICIENT가 된다")
    return errors


def resolve_path(config, value):
    """설정 파일 위치 기준 상대 경로를 절대 경로로."""
    if value is None:
        return None
    path = Path(value)
    return path if path.is_absolute() else (Path(config["_dir"]) / path).resolve()


def state_path(config):
    return PIPELINE_DIR / config["name"] / "state.json"


def load_state(config):
    path = state_path(config)
    return load_json(path) if path.exists() else {"stages": {}}


def save_state(config, state):
    write_json(state_path(config), state)


def _now():
    return datetime.datetime.now(datetime.timezone.utc).astimezone().isoformat(timespec="seconds")


# ---------------------------------------------------------------- 단계 사이 입력

def stage_output(state, stage):
    entry = state["stages"].get(stage) or {}
    return entry.get("outputs") or {} if entry.get("status") == "done" else {}


def input_file(config, state, key):
    """case · court · report · source 파일: extract 결과가 있으면 그것, 없으면 설정 inputs."""
    produced = stage_output(state, "court") if key == "courtDraft" else stage_output(state, "extract")
    path = produced.get(key) or resolve_path(config, config["inputs"].get(key))
    if not path or not Path(path).exists():
        raise PipelineError(f"{key} 파일이 없습니다. extract 단계를 돌리거나 설정 inputs.{key}에 경로를 넣으세요")
    return Path(path)


def batch_dir_for(config, case):
    """생성 묶음 폴더: <name>-<프롬프트 해시 8자리>. 사건 내용 · 프롬프트가 바뀌면(예: --from extract로 다시 가공) 새 묶음을 써서
    다른 프롬프트의 회차가 섞이지 않고, 이전 묶음 때문에 생성이 막히지도 않는다."""
    try:
        digest = prompt_digest(build_prompt(case))[:8]
    except InputError as e:
        raise PipelineError(str(e))
    return RUNS_DIR / f"{config['name']}-{digest}"


def active_models(config, state, include_kept=True):
    """생성 · 선택에 쓸 모델: generate.models(free:는 지금 쓰는 무료 모델로 펼침)에서 사전 학습 점검으로 뺀 모델을 제외한다.

    include_kept: 일 한도로 막혀 다른 모델로 대체됐지만 이미 끝낸 회차가 있는 모델(무료 모델 자동 사용)도 포함한다
    (선택 후보용, 새로 호출하지는 않는다)."""
    contamination = stage_output(state, "contamination")
    excluded = set(contamination.get("excluded") or [])
    # 점검 중 일 한도로 다른 모델에 자리를 내준 모델은 점검을 다 받지 못했으므로 생성에 쓰지 않는다 (목록이 어긋나도 방어)
    excluded |= {spec for spec, result in (contamination.get("results") or {}).items() if result.get("replacedBy")}
    models = [spec for spec in expand_models(state, config["stages"]["generate"]["models"]) if spec not in excluded]
    if include_kept:
        models += [spec for spec in (state.get("freeModels") or {}).get("kept", [])
                   if spec not in models and spec not in excluded]
    return models


# ---------------------------------------------------------------- 단계

def extract_provider(config):
    """비식별화 모델의 실제 공급자 (외부 전송 안내용). 규칙은 llm.model_provider와 같다 (비식별화 호출도 같은 함수를 쓴다).
    모델 목록이면 대체 모델의 공급자까지 모두 적는다 (BE-45)."""
    return providers_text(config["stages"]["extract"]["model"])


def stage_extract(config, state, log):
    sys.path.insert(0, str(EXTRACTOR_DIR))
    try:
        from extract_case import ExtractError, run as extract_run  # noqa: E402 (case-extractor는 패키지 설치가 필요)
    except ImportError as e:
        raise PipelineError(f"case-extractor를 불러오지 못했습니다 (pip install -r case-extractor/requirements.txt): {e}")
    cfg = config["stages"]["extract"]
    sources = [resolve_path(config, s["path"]) for s in config["sources"]]
    model, free_backup = expand_judgment_models(config, cfg["model"], log)
    log(f"판결문 {len(sources)}개 → {chain_text(model)}로 비식별화 · 구조화 (로컬 마스킹 후 전송)")
    try:
        case_path, court_path, report_path, source_path = extract_run(
            sources, config["name"], out_dir=CASES_DIR, model=model, effort=cfg["effort"],
            max_tokens=cfg.get("maxTokens"), log=log, free_models=free_backup)
    except ExtractError as e:
        raise PipelineError(f"비식별화 실패: {e}")
    outputs = {"case": str(case_path), "court": str(court_path), "report": str(report_path), "source": str(source_path)}
    report = load_json(report_path)
    eligibility = report.get("eligibility") or {}
    outputs["eligible"] = eligibility.get("eligible")
    outputs["warnings"] = len(report.get("warnings", []))
    outputs["model"] = report.get("model")  # 실제로 응답한 모델 (court 단계와 같다)
    if report.get("usedFreeTier"):
        outputs["usedFreeTier"] = True
        log("⚠ 무료 등급 백업 모델이 판결문 원문을 처리했습니다 (제품 개선에 쓰이고 사람이 검토할 수 있습니다). report.json의 usedFreeTier 참고")
    if report.get("fallbacks"):  # 앞 모델이 과부하 · 한도로 막혀 대체 모델이 응답했다 (BE-45)
        outputs["fallbacks"] = [{"model": f["model"], "kind": f["kind"]} for f in report["fallbacks"]]
    if eligibility.get("eligible") is False and cfg["requireEligible"]:
        raise PipelineError("서비스 대상이 아닌 판결로 판정됐습니다 (stages.extract.requireEligible=false로 무시 가능): "
                            + " / ".join(eligibility.get("reasons", [])), outputs)
    return outputs


def court_model(config):
    """재판부 판결 초안 모델: stages.court.model, 없으면 extract 모델."""
    return config["stages"]["court"].get("model") or config["stages"]["extract"]["model"]


def stage_court(config, state, log, caller=None):
    """재판부 판결(COURT) 초안 생성 (BE-38). 원문을 마스킹해 모델에 보내고 인용 · 형량 대조 검사를 통과한 초안만 남긴다.
    초안은 실제 판결이 들어 있는 내부 파일이다. 이후 contamination · generate는 이 파일을 읽지 않는다."""
    sys.path.insert(0, str(EXTRACTOR_DIR))
    try:
        from court_draft import CourtDraftError, run as court_run  # noqa: E402
    except ImportError as e:
        raise PipelineError(f"court_draft를 불러오지 못했습니다: {e}")
    cfg = config["stages"]["court"]
    model, free_backup = expand_judgment_models(config, court_model(config), log)
    log(f"재판부 판결 초안 → {chain_text(model)} (마스킹한 판결문 전송, 인용은 원문과 대조)")
    try:
        draft_path, report_path = court_run(
            config["name"], out_dir=CASES_DIR, model=model, max_tokens=cfg["maxTokens"], caller=caller,
            case_path=input_file(config, state, "case"), court_path=input_file(config, state, "court"),
            source_path=input_file(config, state, "source"), final_index=config["finalSourceIndex"], log=log,
            free_models=free_backup)
    except CourtDraftError as e:
        raise PipelineError(f"재판부 판결 초안 실패: {e}")
    report = load_json(report_path)
    log(f"초안: 고려한 요소 {report.get('factorsChosen')}개 · 제외 {report.get('factorsExcluded')}개 · "
        f"경고 {len(report.get('warnings', []))} · 시도 {report.get('attempts')}회")
    outputs = {"courtDraft": str(draft_path), "report": str(report_path), "model": report.get("model"),
               "warnings": len(report.get("warnings", []))}
    if report.get("usedFreeTier"):
        outputs["usedFreeTier"] = True
        log("⚠ 무료 등급 백업 모델이 판결문 원문을 처리했습니다 (제품 개선에 쓰이고 사람이 검토할 수 있습니다). court_report.json의 usedFreeTier 참고")
    if report.get("fallbacks"):  # 앞 모델이 과부하 · 한도로 막혀 대체 모델이 응답했다 (BE-45)
        outputs["fallbacks"] = [{"model": f["model"], "kind": f["kind"]} for f in report["fallbacks"]]
    return outputs


def contamination_runs(config, state, models, log, caller, replace=None):
    """models 각각을 사전 학습 점검 runs회 호출한다 (모델을 번갈아 가는 회차 순서, BE-44).

    일 한도가 소진된 모델은 남은 회차를 건너뛰고(BE-36), replace(모델)이 새 모델을 주면(무료 모델 자동 사용, BE-45)
    그 모델도 점검을 처음부터 runs회 받는다. (모델 목록(대체 포함), 모델별 응답 분류, {소진된 모델: 새 모델})
    """
    cfg = config["stages"]["contamination"]
    case = load_json(input_file(config, state, "case"))
    court = load_json(input_file(config, state, "court"))
    prompt = build_contamination_prompt(case)
    models = list(models)
    for spec in models:
        api_key(parse_model_spec(spec)[0])
    out_dir = PIPELINE_DIR / config["name"] / "contamination"
    criteria = criteria_with(cfg.get("criteria"))
    categories = {spec: [] for spec in models}
    next_run = {spec: 1 for spec in models}
    exhausted, replaced = set(), {}  # 일 한도 · 크레딧이 바닥난 모델 / 대신 들어온 모델
    called = False
    while True:
        pending = [spec for spec in models if spec not in exhausted and next_run[spec] <= cfg["runs"]]
        if not pending:
            break
        for spec in pending:
            i = next_run[spec]
            next_run[spec] += 1
            if called and cfg["delay"]:  # 실제 호출 직전에만 쉰다 (건너뛴 모델 · 마지막 호출 뒤에는 쉬지 않는다)
                time.sleep(cfg["delay"])
            called = True
            record = {"modelSpec": spec, "run": i, "createdAt": _now()}
            quota_exhausted = False
            try:
                result = caller(spec, "", prompt, timeout=cfg["timeout"])
                record.update(meta=result.meta(), rawText=result.text)
                try:
                    category, reason = classify(court, parse_output(result.text), criteria)
                except ValueError as e:  # JSONDecodeError 포함 — 읽을 수 없는 응답은 형식 오류로 센다
                    category, reason = "INVALID", f"응답을 읽을 수 없음: {e}"
            except LLMError as e:
                category, reason = None, f"호출 실패: {e}"  # 응답 수에 넣지 않는다 (표본 부족 판정으로 이어짐)
                quota_exhausted = isinstance(e, LLMQuotaExhaustedError)
            record.update(category=category, reason=reason)
            write_json(out_dir / model_slug(spec) / f"run-{i:03d}.json", record)
            if category:
                categories[spec].append(category)
            if quota_exhausted:
                # 일 한도 · 크레딧이 바닥났으니 남은 회차도 같은 실패로 쌓일 뿐이다. 이 모델만 멈춘다 (BE-36)
                exhausted.add(spec)
                if i < cfg["runs"]:
                    log(f"[{spec}] 호출 한도 소진 → 남은 {cfg['runs'] - i}회는 호출하지 않고 건너뜁니다")
                new = replace(spec) if replace else None
                if new and new not in categories:
                    replaced[spec] = new
                    models.append(new)
                    categories[new], next_run[new] = [], 1
    return models, categories, replaced


def contamination_verdicts(config, models, categories, replaced, log):
    """점검 응답 분류 → 모델별 판정과 설정(onContaminated 등)에 따른 제외 · 멈춤. (판정, 제외한 모델, 멈춤 사유)"""
    cfg = config["stages"]["contamination"]
    criteria = criteria_with(cfg.get("criteria"))
    results, excluded, stop_reasons = {}, [], []
    for spec in models:
        # 호출이 모두 실패해도(응답 0개) INSUFFICIENT로 판정해 onInsufficient를 따른다
        answered = categories[spec]
        final, reason, counts = aggregate(answered, criteria)
        results[spec] = {"verdict": final, "reason": reason, "counts": counts, "answered": len(answered),
                         "runs": cfg["runs"], "criteria": criteria}
        if spec in replaced:
            # 자리를 다른 모델이 채웠으므로 이 모델은 더 쓰지 않는다 (제외 · 멈춤 설정을 적용하지 않는다)
            results[spec]["replacedBy"] = replaced[spec]
            log(f"[{spec}] {final} — 일 한도 소진으로 {replaced[spec]}가 대신합니다 (응답 {len(answered)}/{cfg['runs']})")
            continue
        log(f"[{spec}] {final} — {reason} {counts} (응답 {len(answered)}/{cfg['runs']})")
        action = {"CONTAMINATED": cfg["onContaminated"], "SUSPECT": cfg["onSuspect"],
                  "INSUFFICIENT": cfg["onInsufficient"]}.get(final, "continue")
        if action == "stop" and spec in replaced.values():
            # 자동으로 자리를 채운 모델 때문에 파이프라인 전체를 멈추지 않는다. 그 모델만 제외한다 (BE-45)
            log(f"[{spec}] 자동으로 들어온 모델이라 멈추지 않고 제외합니다")
            action = "exclude"
        if action == "exclude":
            excluded.append(spec)
        elif action == "stop":
            stop_reasons.append(f"{spec}: {final}")
    return results, excluded, stop_reasons


def stage_contamination(config, state, log, caller=call):
    cfg = config["stages"]["contamination"]
    resolve_free_models(config, state, log, fresh=True)  # 점검은 처음부터 다시 도는 단계라 무료 모델 목록도 처음부터
    models = expand_models(state, cfg["models"] or config["stages"]["generate"]["models"])
    models, categories, replaced = contamination_runs(
        config, state, models, log, caller, replace=lambda spec: replace_exhausted(state, spec, "contamination", log))
    results, excluded, stop_reasons = contamination_verdicts(config, models, categories, replaced, log)
    outputs = {"results": results, "excluded": excluded}
    if stop_reasons:
        raise PipelineError("사전 학습 점검에서 멈춤: " + ", ".join(stop_reasons), outputs)
    if excluded and not [m for m in expand_models(state, config["stages"]["generate"]["models"]) if m not in excluded]:
        raise PipelineError("사전 학습 점검으로 생성 모델이 모두 빠졌습니다: " + ", ".join(excluded), outputs)
    return outputs


def vet_replacement(config, state, spec, log, caller):
    """생성 중 대신 들어올 무료 모델을 사전 학습 점검에 먼저 통과시킨다 (BE-45). 통과해야 생성에 참여한다.

    점검이 꺼져 있거나 이번 실행에서 점검 단계를 돌리지 않았으면 점검하지 않는다. 결과는 점검 단계 결과(state)에 더한다."""
    entry = state["stages"].get("contamination") or {}
    if not config["stages"]["contamination"]["enabled"] or entry.get("status") != "done":
        return True
    models, categories, replaced = contamination_runs(config, state, [spec], log, caller)
    results, excluded, stop_reasons = contamination_verdicts(config, models, categories, replaced, log)
    if stop_reasons:
        # 설정이 stop이어도 자동으로 들어올 후보 때문에 생성 도중 파이프라인 전체를 멈추지 않는다. 그 후보만 제외하고 다음 대기 모델을 본다
        log(f"[{spec}] 대체 후보가 점검에서 걸렸습니다 ({', '.join(stop_reasons)}) — 멈추지 않고 제외합니다")
        excluded = excluded + [spec]
    outputs = entry.setdefault("outputs", {})
    outputs.setdefault("results", {}).update(results)
    outputs["excluded"] = list(outputs.get("excluded") or []) + excluded
    return spec not in excluded


def stage_generate(config, state, log, caller=call):
    cfg = config["stages"]["generate"]
    case = load_json(input_file(config, state, "case"))
    batch_dir = batch_dir_for(config, case)
    options = {"temperature": cfg["temperature"], "max_tokens": cfg["maxTokens"], "timeout": cfg["timeout"],
               "delay": cfg["delay"], "caller": caller, "log": log, "replacement_runs": cfg["runs"],
               "on_exhausted": lambda spec: replace_exhausted(
                   state, spec, "generate", log, vet=lambda new: vet_replacement(config, state, new, log, caller))}
    models = active_models(config, state, include_kept=False)
    by_spec = {spec: [] for spec in models}
    retries = {spec: 0 for spec in models}

    def collect(records):
        for record in records:
            by_spec.setdefault(record["modelSpec"], []).append(record)  # 대신 들어온 모델은 여기서 처음 나타난다
            retries.setdefault(record["modelSpec"], 0)

    def summarize():
        return {spec: {"runs": len(records), "valid": sum(1 for r in records if r["validation"]["ok"]),
                       "retries": retries[spec]} for spec, records in by_spec.items()}

    try:
        # 모델을 번갈아 가며 회차 순서로 생성한다 (BE-44)
        collect(generate_runs(case, models, cfg["runs"], batch_dir, **options))
        # 검증을 통과한 회차가 하나도 없는 모델은 한 번씩 더 생성한다 (최대 maxRetries회). 한도 소진이면 재생성도 실패한다
        while True:
            pending = [spec for spec, records in by_spec.items() if retries[spec] < cfg["maxRetries"]
                       and not any(r["validation"]["ok"] for r in records)
                       and not any(r["callErrorKind"] == LLMQuotaExhaustedError.kind for r in records)]
            if not pending:
                break
            for spec in pending:
                retries[spec] += 1
                log(f"[{spec}] 검증 통과 회차 없음 → 재생성 {retries[spec]}/{cfg['maxRetries']}")
            if cfg["delay"]:  # 앞 호출과 재생성 첫 호출 사이도 간격을 둔다
                time.sleep(cfg["delay"])
            collect(generate_runs(case, pending, 1, batch_dir, **options))
    except (GenerateError, LLMError, PipelineError) as e:
        # 대체 모델 점검 중 오류(PipelineError)도 그때까지의 기록을 요약에 남긴다 (회차 파일은 이미 디스크에 있다)
        message = e.args[0] if isinstance(e, PipelineError) else e
        raise PipelineError(f"생성 실패: {message}", {"batchDir": str(batch_dir), "models": summarize()})
    summary = summarize()
    outputs = {"batchDir": str(batch_dir), "models": summary}
    fm = state.get("freeModels")
    if fm and fm["replaced"]:
        outputs["freeModels"] = {"active": fm["active"], "replaced": fm["replaced"]}
    if not any(s["valid"] for s in summary.values()):
        raise PipelineError("검증을 통과한 회차가 없습니다 (재생성 포함). out/runs의 기록과 compare.py로 원인을 보세요", outputs)
    return outputs


def _candidate_key(record):
    return f"{model_slug(record['modelSpec'])}/run-{record['runIndex']:03d}"


def select_run(case, groups, models, strategy, manual_run=None):
    """검증을 통과한 회차 중 하나를 고른다. (기록, 고른 이유, 점수) — 점수는 consensus일 때만."""
    candidates = [r for spec in models for r in groups.get(spec, []) if r["validation"]["ok"]]
    if strategy == "manual":
        wanted = manual_run.replace(".json", "")
        for spec, records in groups.items():
            for record in records:
                if _candidate_key(record) == wanted:
                    if spec not in models:
                        raise PipelineError(f"{manual_run}는 이번 선택 대상 모델이 아닙니다 (사전 학습 점검 제외 · select.model 확인)")
                    if not record["validation"]["ok"]:
                        raise PipelineError(f"{manual_run}는 검증을 통과하지 못한 회차라 적재할 수 없습니다")
                    return record, "설정에서 직접 지정", None
        raise PipelineError(f"{manual_run} 회차를 찾지 못했습니다 (형식: 공급자__모델/run-003)")
    if not candidates:
        raise PipelineError("검증을 통과한 회차가 없습니다")
    order = {id(r): i for i, r in enumerate(candidates)}  # 모델 순서 · 회차 순서
    if strategy == "first-valid":
        return candidates[0], "검증을 통과한 첫 회차", None
    if strategy == "fewest-warnings":
        best = min(candidates, key=lambda r: (len(r["validation"]["warnings"]), order[id(r)]))
        return best, f"경고가 가장 적은 회차 (경고 {len(best['validation']['warnings'])})", None
    # consensus: 후보 전체의 요소별 다수 의견과 가장 많이 일치하는 회차. 같으면 경고가 적은 것, 그다음 앞선 것
    factor_ids = sorted(int(f["factorId"]) for f in case.get("factors", []))
    table = direction_table(candidates, factor_ids)
    consensus = {fid: _majority(values) for fid, values in table.items() if values}
    scores = {id(r): sum(table[fid][order[id(r)]] == consensus[fid] for fid in consensus) for r in candidates}
    best = max(candidates, key=lambda r: (scores[id(r)], -len(r["validation"]["warnings"]), -order[id(r)]))
    return best, (f"판단 요소 다수 의견 일치 {scores[id(best)]}/{len(consensus)} "
                  f"(후보 {len(candidates)}개 중 최고, 동점이면 경고 적은 · 앞선 회차)"), scores[id(best)]


def stage_select(config, state, log):
    cfg = config["stages"]["select"]
    case = load_json(input_file(config, state, "case"))
    batch_dir = Path(stage_output(state, "generate").get("batchDir") or batch_dir_for(config, case))
    _, groups = load_runs(batch_dir)
    models = active_models(config, state)
    if cfg["model"]:
        if cfg["model"] not in models:
            raise PipelineError(f"select.model({cfg['model']})이 생성 대상 모델에 없습니다")
        models = [cfg["model"]]
    record, reason, score = select_run(case, groups, models, cfg["strategy"], cfg["run"])
    key = _candidate_key(record)
    log(f"선택: {key} — {reason}")
    return {
        "runFile": str(batch_dir / f"{key}.json"),
        "outputFile": str(batch_dir / f"{key}.output.json"),
        "modelSpec": record["modelSpec"],
        "runIndex": record["runIndex"],
        "strategy": cfg["strategy"],
        "reason": reason,
        "score": score,
        "warnings": record["validation"]["warnings"],
    }


def build_generation_report(config, state, run_record, selection):
    """ai_generation.generation_report (V8). 관리자 후검수 화면이 볼 값. 사건 내용 · 실제 판결 값은 넣지 않는다."""
    raw = json.dumps(run_record["output"], ensure_ascii=False, sort_keys=True)
    run_key = hashlib.sha256(f"{config['name']}|{selection['modelSpec']}|{selection['runIndex']}|{raw}".encode()).hexdigest()
    contamination = stage_output(state, "contamination").get("results", {}).get(selection["modelSpec"])
    return {
        "runKey": run_key[:32],
        "pipeline": config["name"],
        "modelSpec": selection["modelSpec"],
        "servedModel": (run_record.get("meta") or {}).get("servedModel"),
        "runIndex": selection["runIndex"],
        "generatedAt": run_record.get("createdAt"),
        "usage": (run_record.get("meta") or {}).get("usage"),
        "validationWarnings": run_record["validation"]["warnings"],
        "selection": {"strategy": selection["strategy"], "reason": selection["reason"], "score": selection["score"]},
        # 판정 · 근거 · 분류별 개수 · 기준만 남긴다 (예측 형량 등은 남기지 않음)
        "contamination": ({"verdict": contamination["verdict"], "reason": contamination.get("reason"),
                           "counts": contamination["counts"], "criteria": contamination.get("criteria")}
                          if contamination else {"verdict": "SKIPPED"}),
    }


def axis_model(config, state):
    """가치관 축 분류 모델: stages.axis.model, 없으면 생성에 쓰는 첫 모델(free:는 지금 쓰는 무료 모델로 펼친 뒤).
    한 모델만 쓴다(같은 모델로 runs회)."""
    model = config["stages"]["axis"].get("model")
    if model:
        return model
    return next(iter(active_models(config, state, include_kept=False)), None)


def stage_axis(config, state, log, caller=call):
    """판단 요소의 가치관 축만 같은 모델에 runs회 물어 투표한다 (BE-49). 결과는 load 단계가 보고서에 덮어써 적재한다.

    요소가 확정된 뒤(extract 결과) 축만 묻는다. 회차마다 응답 형식을 검사하고, 틀리면 maxRetries번까지 다시 묻는다.
    호출 한도가 바닥나면 남은 회차를 멈추고 받은 응답만 센다. 유효 응답이 하나도 없으면 멈춘다(--skip axis면 추출기 값으로 적재)."""
    cfg = config["stages"]["axis"]
    spec = axis_model(config, state)
    if not spec:
        raise PipelineError("가치관 축 분류 모델이 없습니다. stages.axis.model을 넣거나 --skip axis로 추출기 값을 씁니다")
    case = load_json(input_file(config, state, "case"))
    report = load_json(input_file(config, state, "report"))
    factor_ids = [f["factorId"] for f in case["factors"]]
    preferred = {e["factorId"]: e.get("valueAxis") for e in report.get("factorExtras", [])}
    system, user = build_axis_prompt(case)
    api_key(parse_model_spec(spec)[0])
    out_dir = PIPELINE_DIR / config["name"] / "axis"
    if out_dir.exists():
        shutil.rmtree(out_dir)  # 이전 실행의 회차와 섞지 않는다 (투표는 이번 실행의 응답만 센다)
    log(f"가치관 축 분류: {spec} {cfg['runs']}회 투표 (요소 {len(factor_ids)}개)")
    answers, failures, attempts, stopped = [], 0, 0, False
    for run in range(1, cfg["runs"] + 1):
        for retry in range(cfg["maxRetries"] + 1):
            if attempts and cfg["delay"]:
                time.sleep(cfg["delay"])
            attempts += 1
            record = {"modelSpec": spec, "run": run, "attempt": retry + 1, "createdAt": _now()}
            try:
                result = caller(spec, system, user, temperature=cfg["temperature"], max_tokens=cfg["maxTokens"],
                                timeout=cfg["timeout"])
                record.update(meta=result.meta() if hasattr(result, "meta") else {}, rawText=result.text)
                answer = parse_axis_answer(result.text, factor_ids)
            except LLMQuotaExhaustedError as e:
                record["error"] = f"호출 한도 소진: {e}"
                stopped = True
            except LLMError as e:
                record["error"] = f"호출 실패: {e}"
            except AxisVoteError as e:
                record["error"] = f"형식 오류: {e}"
            else:
                record["answer"] = {str(k): v for k, v in answer.items()}
                answers.append(answer)
            write_json(out_dir / f"run-{run:03d}-{retry + 1}.json", record)
            if "error" not in record or stopped:
                break
            failures += 1
            log(f"[{run}회차] {record['error'].splitlines()[0]}" + (" — 다시 묻습니다" if retry < cfg["maxRetries"] else " — 집계에서 뺍니다"))
        if stopped:
            log(f"호출 한도 소진 → 남은 회차는 호출하지 않습니다 (유효 응답 {len(answers)}개로 집계)")
            break
    if not answers:
        raise PipelineError("가치관 축 분류에서 유효한 응답을 받지 못했습니다. 설정을 확인하거나 --skip axis로 추출기 값을 씁니다",
                            {"model": spec, "attempts": attempts})
    votes = aggregate_votes(answers, factor_ids, preferred, requested_runs=cfg["runs"])
    factors = [{"factorId": i, **votes[i]} for i in factor_ids]
    votes_file = out_dir / "votes.json"
    write_json(votes_file, {"promptVersion": AXIS_PROMPT_VERSION, "model": spec, "runs": len(answers),
                            "requestedRuns": cfg["runs"], "createdAt": _now(), "factors": factors})
    needs_review = [f["factorId"] for f in factors if f["valueAxisVotes"]["needsReview"]]
    changed = [f["factorId"] for f in factors if f["valueAxis"] != preferred.get(f["factorId"])]
    for f in factors:
        if f["factorId"] in needs_review:
            counts = " · ".join(f"{k} {v}" for k, v in f["valueAxisVotes"]["counts"].items())
            log(f"⚠ 요소 {f['factorId']}: {counts} → 관리자 확인 필요 (기본값 {f['valueAxis'] or '없음'})")
    if len(answers) < cfg["runs"]:
        log(f"⚠ 유효 응답 {len(answers)}/{cfg['runs']}개로 집계했습니다")
    log(f"결과: 확인 필요 {len(needs_review)}개 · 추출기 값과 다름 {len(changed)}개 (요소 {len(factor_ids)}개)")
    return {"votesFile": str(votes_file), "model": spec, "runs": len(answers), "requestedRuns": cfg["runs"],
            "attempts": attempts, "failures": failures, "needsReview": needs_review, "changedFromExtract": changed}


def stage_load(config, state, log, apply=None):
    cfg = config["stages"]["load"]
    selection = stage_output(state, "select")
    if not selection:
        raise PipelineError("select 단계 결과가 없습니다")
    case = load_json(input_file(config, state, "case"))
    run_record = load_json(selection["runFile"])
    output = run_record["output"]
    try:
        prompt = build_prompt(case)
    except InputError as e:
        raise PipelineError(str(e))

    parts = []
    if cfg["case"]:
        report = load_json(input_file(config, state, "report"))
        # 가치관 축 투표 결과(BE-49)를 추출기 값 위에 덮어쓴다. axis를 끄면(enabled: false) 예전 투표가 남아 있어도 추출기 값 그대로.
        # --skip axis는 다른 단계처럼 "이번에 다시 돌리지 않는다"는 뜻이라, 끝난 투표 결과가 있으면 그것을 쓴다
        axis = stage_output(state, "axis") if config["stages"]["axis"]["enabled"] else {}
        if axis.get("votesFile"):
            report = apply_votes(report, load_json(axis["votesFile"])["factors"])
            log(f"가치관 축: 투표 결과 사용 ({axis['model']} {axis['runs']}회, 확인 필요 {len(axis['needsReview'])}개)")
        source = load_json(input_file(config, state, "source"))
        overrides = [{k: v for k, v in s.items() if k != "path"} for s in config["sources"]]
        try:
            sources = resolve_sources(source["sources"], overrides, config["finalSourceIndex"])
            incident_date = config.get("incidentDate") or source.get("incidentDate")
            parts.append(build_case_sql(case, report, sources, source_org=cfg["sourceOrg"], source_note=cfg["sourceNote"],
                                        incident_date=incident_date))
        except CaseSeedError as e:
            raise PipelineError(str(e))

    court_path = None
    if cfg["court"]:
        produced = stage_output(state, "court").get("courtDraft") or resolve_path(config, config["inputs"].get("courtDraft"))
        if produced and Path(produced).exists():
            court_path = Path(produced)
            try:
                parts.append(build_court_sql(load_json(court_path), case["title"]))
            except CourtSeedError as e:
                raise PipelineError(str(e))
        else:
            log("재판부 판결 초안이 없어 사건 · AI 판결만 적재합니다 (공개 전에 재판부 판결이 필요합니다)")

    model_name = (run_record.get("meta") or {}).get("servedModel") or parse_model_spec(selection["modelSpec"])[1]
    if len(model_name) > NAME_MAX_LENGTH:
        model_name = parse_model_spec(selection["modelSpec"])[1][:NAME_MAX_LENGTH]
    report_json = build_generation_report(config, state, run_record, selection)
    parts.append(build_sql(case, output, prompt, model_name, None, flyway=True, pending=True,
                           generation_report=report_json))

    sql_dir = Path(cfg["sqlDir"])
    if not sql_dir.is_absolute():
        sql_dir = (TOOL_DIR / sql_dir).resolve()  # tools/ai-judgment 기준
    check_sql_dir(sql_dir)
    stamp = datetime.datetime.now().strftime("%Y%m%d-%H%M%S")
    sql_path = sql_dir / f"{stamp}-{config['name']}.sql"
    sql_dir.mkdir(exist_ok=True)
    header = (f"-- AI 판결 파이프라인 적재 (BE-31): {config['name']}\n"
              f"-- 생성: {_now()} · 모델 {selection['modelSpec']} · {selection['reason']}\n"
              "-- 사건은 DRAFT, 재판부 판결 · AI 판결은 비공개(AI는 PENDING)로 넣는다. 사용자에게 보이지 않고 관리자가 검수 · 공개한다.\n"
              "-- 원본 판결문(case_source)이 들어 있다. 공개 저장소에 두지 않는다. 같은 파일을 두 번 실행해도 중복되지 않는다.\n"
              "-- 운영 반영: 서버에서 psql -v ON_ERROR_STOP=1 -f <이 파일>\n")
    sql_path.write_text(header + "BEGIN;\n" + "\n".join(parts) + "COMMIT;\n", encoding="utf-8")
    log(f"SQL: {sql_path}")
    outputs = {"sqlFile": str(sql_path), "applied": False, "runKey": report_json["runKey"],
               "court": court_path is not None}
    if cfg["applyToDb"]:
        messages = (apply or apply_sql)(sql_path, cfg["db"])
        outputs.update(applied=True, messages=messages)
        for message in messages:
            log(message)
    return outputs


def check_sql_dir(sql_dir):
    """적재 SQL에는 마스킹 전 원문(case_source)이 들어가므로 비공개 저장소 체크아웃 안에만 둔다.

    - 상위 폴더가 실제 git 체크아웃이어야 한다. 서브모듈을 받지 않으면 private-seed는 빈 폴더로 남아 exists()만으로는
      통과하는데, 그러면 비공개 저장소에 커밋되지 않고 나중에 submodule update도 실패할 수 있다(서브모듈의 .git은 파일)
    - 공개 저장소 작업 트리 안이면 private-seed 아래만 허용한다 (공개 저장소에 원문이 커밋되지 않게)
    """
    root = sql_dir.parent
    if not (root / ".git").exists():
        raise PipelineError(f"SQL 보관 폴더가 git 저장소 체크아웃이 아닙니다: {root} "
                            "(git submodule update --init backend/private-seed 로 받거나 stages.load.sqlDir를 바꾸세요). "
                            "원본 판결문이 들어 있어 비공개 저장소 밖에 두지 않습니다")
    resolved = sql_dir.resolve()
    if _is_within(resolved, PUBLIC_ROOT.resolve()) and not _is_within(resolved, PRIVATE_SEED_DIR.resolve()):
        raise PipelineError(f"SQL 보관 폴더가 공개 저장소 안에 있습니다: {sql_dir} (backend/private-seed 아래만 됩니다)")


def _is_within(path, parent):
    try:
        path.relative_to(parent)
        return True
    except ValueError:
        return False


def _is_local_host(host):
    """libpq 호스트 값이 이 컴퓨터인지. '/'로 시작하면 유닉스 소켓 디렉터리다."""
    return host in LOCAL_HOSTS or (isinstance(host, str) and host.startswith("/"))


def _local_docker_endpoint():
    """현재 docker 대상(DOCKER_HOST, 없으면 현재 context)이 로컬인지 확인한다. 원격이면 PipelineError."""
    endpoint = os.environ.get("DOCKER_HOST")
    if not endpoint:
        try:
            completed = subprocess.run(["docker", "context", "inspect", "--format", "{{.Endpoints.docker.Host}}"],
                                       capture_output=True, timeout=30)
            endpoint = completed.stdout.decode("utf-8", "replace").strip()
        except (OSError, subprocess.SubprocessError):
            endpoint = ""
    if not endpoint:
        raise PipelineError("docker 대상(context)을 확인하지 못했습니다. 로컬 docker인지 확인할 수 없어 적재하지 않습니다")
    parsed = urllib.parse.urlparse(endpoint)
    local = parsed.scheme in ("unix", "npipe") or (parsed.scheme == "tcp" and parsed.hostname in LOCAL_HOSTS)
    if not local:
        raise PipelineError(f"원격 docker({endpoint})에는 적재하지 않습니다. 운영은 SQL 파일을 서버에서 실행하세요")
    return endpoint


def apply_sql(sql_path, db):
    """로컬 DB에만 적재한다. 운영 반영은 SQL 파일을 별도 절차로 실행한다."""
    if db["mode"] == "docker":
        if not shutil.which("docker"):
            raise PipelineError("docker 명령이 없습니다 (stages.load.db.mode=psql로 바꾸거나 applyToDb=false)")
        _local_docker_endpoint()  # DOCKER_HOST · docker context가 원격을 가리키면 거부
        command = ["docker", "exec", "-i", db["container"], "psql", "-v", "ON_ERROR_STOP=1", "-q",
                   "-U", db["user"], "-d", db["database"]]
        env = None
    else:
        url = db.get("url") or os.environ.get("DB_URL") or "postgresql://localhost:5432/lawnambul"
        url = url[len("jdbc:"):] if url.startswith("jdbc:") else url
        parsed = urllib.parse.urlparse(url)
        # libpq는 쿼리의 host · hostaddr를 URL 호스트보다 우선해 쓰고, 쉼표로 여러 호스트를 받는다. 모두 로컬이어야 한다
        query = urllib.parse.parse_qs(parsed.query)
        hosts = [h for key in ("host", "hostaddr") for value in query.get(key, []) for h in value.split(",")]
        if parsed.hostname:
            hosts.append(parsed.hostname)
        if not hosts:
            # URL에 호스트가 없으면(postgresql:///db) libpq는 PGHOST · PGHOSTADDR, 없으면 로컬 유닉스 소켓을 쓴다
            hosts = [h for env in ("PGHOST", "PGHOSTADDR") for h in os.environ.get(env, "").split(",") if h]
        if any(not _is_local_host(h) for h in hosts):
            raise PipelineError(f"로컬 DB에만 직접 적재합니다 (받은 호스트: {', '.join(str(h) for h in hosts)}). "
                                "운영은 만들어진 SQL 파일을 서버에서 실행하세요")
        if not shutil.which("psql"):
            raise PipelineError("psql 명령이 없습니다 (stages.load.db.mode=docker로 바꾸세요)")
        command = ["psql", url, "-v", "ON_ERROR_STOP=1", "-q"]
        env = {**os.environ, "PGUSER": os.environ.get("DB_USERNAME", "lawnambul"),
               "PGPASSWORD": os.environ.get("DB_PASSWORD", "lawnambul")}
    with open(sql_path, "rb") as f:
        completed = subprocess.run(command, stdin=f, capture_output=True, env=env, timeout=300)
    stderr = completed.stderr.decode("utf-8", "replace")
    if completed.returncode != 0:
        raise PipelineError("DB 적재 실패 (트랜잭션 전체 취소):\n" + stderr.strip()[-2000:])
    # NOTICE(건너뜀 · 갱신 행 수)와 WARNING(반영하지 못한 요소 등)을 로그로 돌려준다. WARNING은 ⚠로 구분한다
    return [("⚠ " if "WARNING" in line else "") + line.strip() for line in stderr.splitlines()
            if "NOTICE" in line or "WARNING" in line]


STAGE_FUNCTIONS = {
    "extract": stage_extract,
    "court": stage_court,
    "contamination": stage_contamination,
    "generate": stage_generate,
    "select": stage_select,
    "axis": stage_axis,
    "load": stage_load,
}


# ---------------------------------------------------------------- 실행

def plan(config, state, start=None, until=None, skip=(), rerun=False):
    """이번에 돌릴 단계: 설정에서 켜져 있고, start ~ until 안이고, skip이 아닌 단계.
    이미 끝난(done) 단계는 건너뛴다. 단 앞 단계가 이번에 다시 돌면 뒤 단계 결과는 낡은 것이므로 끝났어도 함께 돌린다
    (예: generate가 실패한 뒤 이어서 돌리면 generate · select · load). start를 주면 그 단계부터는 끝났어도 다시 돌린다."""
    start_i = STAGES.index(start) if start else 0
    until_i = STAGES.index(until) if until else len(STAGES) - 1
    steps = []
    for i, stage in enumerate(STAGES):
        if not config["stages"][stage]["enabled"] or stage in skip or not start_i <= i <= until_i:
            continue
        done = (state["stages"].get(stage) or {}).get("status") == "done"
        if done and not rerun and not start and not steps:
            continue
        steps.append(stage)
    return steps


def run_pipeline(config, start=None, until=None, skip=(), rerun=False, log=print, functions=None):
    functions = functions or STAGE_FUNCTIONS
    configure_retry(max_attempts=config["retry"]["maxAttempts"], max_wait=config["retry"]["maxWait"])
    state = load_state(config)
    steps = plan(config, state, start, until, skip, rerun)
    if not steps:
        log("돌릴 단계가 없습니다 (모두 끝났거나 꺼짐). 다시 돌리려면 --from <단계>")
        return state
    if "contamination" not in steps:  # 점검 단계는 시작할 때 스스로 무료 모델 목록을 처음부터 펼친다
        resolve_free_models(config, state, log, fresh=rerun or start == "contamination")
    providers = sorted({free_token_provider(m) if is_free_token(m) else parse_model_spec(m)[0]
                        for m in config["stages"]["generate"]["models"] + (config["stages"]["contamination"]["models"] or [])})
    log(f"단계: {' → '.join(steps)}")
    if "extract" in steps:
        log(f"외부 전송: 비식별화 단계에서 마스킹한 판결문이 {extract_provider(config)}로 전송됩니다")
    if "court" in steps:
        log(f"외부 전송: 재판부 판결 초안 단계에서 마스킹한 판결문이 {providers_text(court_model(config))}로 전송됩니다")
    if {"contamination", "generate"} & set(steps):
        log(f"외부 전송: 비식별화한 사건 내용이 {', '.join(providers)}로 전송됩니다")
    if "axis" in steps:
        # 무료 모델(free:)은 점검 단계에서 다시 정해지므로 지금 모델을 모를 수 있다. 그때는 생성 공급자로 안내한다(축 모델은 그중 하나)
        axis_spec = axis_model(config, state)
        log(f"외부 전송: 가치관 축 분류 단계에서 비식별화한 사건 개요 · 판단 요소가 "
            f"{providers_text(axis_spec) if axis_spec else ', '.join(providers)}로 전송됩니다")
    for notice in free_tier_notices(config, steps):
        log(notice)
    for stage in steps:
        log(f"\n== {stage} ==")
        entry = {"status": "running", "startedAt": _now()}
        state["stages"][stage] = entry
        save_state(config, state)
        try:
            outputs = functions[stage](config, state, log)
        except PipelineError as e:
            entry.update(status="failed", finishedAt=_now(), error=str(e.args[0]),
                         outputs=e.args[1] if len(e.args) > 1 else {})
            save_state(config, state)
            raise
        entry.update(status="done", finishedAt=_now(), outputs=outputs)
        # 앞 단계를 다시 돌리면 뒤 단계 결과는 낡은 것이 되므로 상태를 지운다
        for later in STAGES[STAGES.index(stage) + 1:]:
            if later not in steps:
                state["stages"].pop(later, None)
        save_state(config, state)
    return state


def print_status(config, log=print):
    state = load_state(config)
    log(f"파이프라인: {config['name']} ({state_path(config)})")
    for stage in STAGES:
        enabled = config["stages"][stage]["enabled"]
        entry = state["stages"].get(stage)
        status = entry["status"] if entry else ("-" if enabled else "꺼짐")
        line = f"- {stage}: {status}"
        if entry and entry.get("error"):
            line += f" — {entry['error'].splitlines()[0]}"
        log(line)
    generated = stage_output(state, "generate")
    if generated:
        log(f"생성 묶음: {generated['batchDir']} (비교표: python3 compare.py {generated['batchDir']})")
    selection = stage_output(state, "select")
    if selection:
        log(f"선택된 회차: {selection['modelSpec']} run-{selection['runIndex']:03d} ({selection['reason']})")
    axis = stage_output(state, "axis")
    if axis:
        review = ", ".join(map(str, axis["needsReview"])) or "없음"
        log(f"가치관 축: {axis['model']} {axis['runs']}/{axis['requestedRuns']}회 투표 · 관리자 확인 필요 요소 {review}")
    load = stage_output(state, "load")
    if load:
        log(f"적재 SQL: {load['sqlFile']} · DB 적재: {'예' if load['applied'] else '아니오'}")


def main():
    parser = argparse.ArgumentParser(description="판결문 → AI 판결 적재 파이프라인 (BE-31)")
    sub = parser.add_subparsers(dest="command", required=True)
    run_cmd = sub.add_parser("run", help="파이프라인을 실행한다")
    run_cmd.add_argument("config", help="설정 파일 (pipeline.example.json 참고)")
    run_cmd.add_argument("--from", dest="start", choices=STAGES, help="이 단계부터 (끝났어도) 다시 돌린다")
    run_cmd.add_argument("--until", choices=STAGES, help="이 단계까지만 돌린다")
    run_cmd.add_argument("--skip", action="append", default=[], choices=STAGES, help="건너뛸 단계 (여러 번 가능)")
    run_cmd.add_argument("--rerun", action="store_true", help="끝난 단계도 모두 다시 돌린다")
    status_cmd = sub.add_parser("status", help="단계별 상태를 본다")
    status_cmd.add_argument("config")
    args = parser.parse_args()
    try:
        config = load_config(args.config)
        if args.command == "status":
            print_status(config)
            return 0
        state = run_pipeline(config, args.start, args.until, args.skip, args.rerun)
    except PipelineError as e:
        print(f"\n[중단] {e.args[0]}", file=sys.stderr)
        return 1
    except (OSError, json.JSONDecodeError) as e:
        print(f"[오류] 파일을 읽지 못했습니다: {e}", file=sys.stderr)
        return 1
    except LLMError as e:
        print(f"[오류] {e}", file=sys.stderr)
        return 1
    load = stage_output(state, "load")
    if load.get("applied"):
        print("\n완료. 로컬 DB에 적재한 사건 · 재판부 판결 · AI 판결은 비공개이며 관리자 검수 후 공개됩니다.")
    elif load:
        print(f"\n완료. 적재 SQL만 만들었습니다(DB 적재 안 함): {load['sqlFile']}")
    else:
        print("\n완료. 적재(load) 단계는 돌지 않았습니다. 이어서 적재하려면 다시 run 하세요.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
