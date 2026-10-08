"""판결문 → 비식별화 → (사전 학습 점검) → AI 판결 생성 → 회차 선택 → 적재까지 이어서 실행한다 (BE-31).

사용법:
    python3 pipeline.py run cases/my-case.pipeline.json                   # 설정대로 실행 (끝난 단계는 건너뛰고 이어서)
    python3 pipeline.py run cases/my-case.pipeline.json --from generate   # generate부터 다시
    python3 pipeline.py run cases/my-case.pipeline.json --until select    # 적재 전까지만
    python3 pipeline.py run cases/my-case.pipeline.json --skip contamination
    python3 pipeline.py status cases/my-case.pipeline.json                # 단계별 상태

단계: extract → contamination → generate → select → load
- extract: case-extractor로 판결문을 비식별화 · 구조화한다. 서비스 대상이 아니라고 판정되면 멈춘다(설정으로 끌 수 있음)
- contamination: 모델마다 사전 학습 점검을 N회 자동으로 한다(기본 꺼짐). CONTAMINATED는 멈춤, SUSPECT는 진행(설정)
- generate: 모델마다 N회 생성 · 검증한다. 검증을 통과한 회차가 없으면 최대 K회 더 생성한다
- select: 검증을 통과한 회차 중 하나를 고른다 (consensus · first-valid · fewest-warnings · manual)
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
from common import TOOL_DIR, load_json, write_json
from compare import _majority, direction_table, load_runs
from generate import RUNS_DIR, GenerateError, generate_runs, model_slug, prompt_digest
from llm import (DEFAULT_MAX_ATTEMPTS, DEFAULT_MAX_WAIT, LLMError, LLMQuotaExhaustedError, api_key, call,
                 check_retry, configure_retry, model_provider, parse_model_spec)
from to_seed_sql import NAME_MAX_LENGTH, build_sql
from validate_output import parse_output

STAGES = ("extract", "court", "contamination", "generate", "select", "load")
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


def chain_errors(label, value, expected):
    """stages.extract.model · stages.court.model 검사. 문자열이거나, 앞 모델이 막히면 다음 모델로 넘어가는 목록 (BE-45)."""
    models = [value] if isinstance(value, str) else value
    if not isinstance(models, list) or not models or not all(isinstance(m, str) and m.strip() for m in models):
        return [f"{label}은 {expected}이다"]
    errors = []
    if len({m.strip() for m in models}) != len(models):
        errors.append(f"{label}에 같은 모델이 두 번 들어 있다")
    for m in models:
        if ":" in m:
            try:
                if parse_model_spec(m)[0] == "manual":
                    errors.append(f"비식별화 · 재판부 판결 초안은 API 공급자만 쓴다 (manual 불가): {m}")
            except LLMError as e:
                errors.append(str(e))
    return errors


def chain_text(model):
    """모델 설정(문자열 또는 목록) → 안내 문구용 `모델1 → 모델2`."""
    return " → ".join([model] if isinstance(model, str) else model)


def providers_text(model):
    """모델 설정의 실제 공급자 (외부 전송 안내용). 목록이면 대체 모델까지 모두 적는다."""
    return ", ".join(sorted({model_provider(m) for m in ([model] if isinstance(model, str) else model)}))


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
    for spec in stages["generate"]["models"] + (stages["contamination"]["models"] or []):
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


def active_models(config, state):
    """생성 · 선택에 쓸 모델: generate.models에서 사전 학습 점검으로 뺀 모델을 제외한다."""
    excluded = set(stage_output(state, "contamination").get("excluded") or [])
    return [spec for spec in config["stages"]["generate"]["models"] if spec not in excluded]


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
    log(f"판결문 {len(sources)}개 → {chain_text(cfg['model'])}로 비식별화 · 구조화 (로컬 마스킹 후 전송)")
    try:
        case_path, court_path, report_path, source_path = extract_run(
            sources, config["name"], out_dir=CASES_DIR, model=cfg["model"], effort=cfg["effort"],
            max_tokens=cfg.get("maxTokens"), log=log)
    except ExtractError as e:
        raise PipelineError(f"비식별화 실패: {e}")
    outputs = {"case": str(case_path), "court": str(court_path), "report": str(report_path), "source": str(source_path)}
    report = load_json(report_path)
    eligibility = report.get("eligibility") or {}
    outputs["eligible"] = eligibility.get("eligible")
    outputs["warnings"] = len(report.get("warnings", []))
    if report.get("fallbacks"):  # 앞 모델이 과부하 · 한도로 막혀 대체 모델이 응답했다 (BE-45)
        outputs["fallbacks"] = [{"model": f["model"], "kind": f["kind"]} for f in report["fallbacks"]]
        outputs["model"] = report.get("requestedModel")
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
    cfg, model = config["stages"]["court"], court_model(config)
    log(f"재판부 판결 초안 → {chain_text(model)} (마스킹한 판결문 전송, 인용은 원문과 대조)")
    try:
        draft_path, report_path = court_run(
            config["name"], out_dir=CASES_DIR, model=model, max_tokens=cfg["maxTokens"], caller=caller,
            case_path=input_file(config, state, "case"), court_path=input_file(config, state, "court"),
            source_path=input_file(config, state, "source"), final_index=config["finalSourceIndex"], log=log)
    except CourtDraftError as e:
        raise PipelineError(f"재판부 판결 초안 실패: {e}")
    report = load_json(report_path)
    log(f"초안: 고려한 요소 {report.get('factorsChosen')}개 · 제외 {report.get('factorsExcluded')}개 · "
        f"경고 {len(report.get('warnings', []))} · 시도 {report.get('attempts')}회")
    outputs = {"courtDraft": str(draft_path), "report": str(report_path), "model": report.get("model"),
               "warnings": len(report.get("warnings", []))}
    if report.get("fallbacks"):  # 앞 모델이 과부하 · 한도로 막혀 대체 모델이 응답했다 (BE-45)
        outputs["fallbacks"] = [{"model": f["model"], "kind": f["kind"]} for f in report["fallbacks"]]
    return outputs


def stage_contamination(config, state, log, caller=call):
    cfg = config["stages"]["contamination"]
    case = load_json(input_file(config, state, "case"))
    court = load_json(input_file(config, state, "court"))
    prompt = build_contamination_prompt(case)
    models = cfg["models"] or config["stages"]["generate"]["models"]
    for spec in models:
        api_key(parse_model_spec(spec)[0])
    out_dir = PIPELINE_DIR / config["name"] / "contamination"
    criteria = criteria_with(cfg.get("criteria"))
    results, excluded, stop_reasons = {}, [], []
    for spec in models:
        categories = []
        for i in range(1, cfg["runs"] + 1):
            record = {"modelSpec": spec, "run": i, "createdAt": _now()}
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
            else:
                quota_exhausted = False
            record.update(category=category, reason=reason)
            write_json(out_dir / model_slug(spec) / f"run-{i:03d}.json", record)
            if category:
                categories.append(category)
            if quota_exhausted:
                # 일 한도 · 크레딧이 바닥났으니 남은 회차도 같은 실패로 쌓일 뿐이다. 이 모델은 여기서 멈춘다 (BE-36)
                if i < cfg["runs"]:
                    log(f"[{spec}] 호출 한도 소진 → 남은 {cfg['runs'] - i}회는 호출하지 않고 건너뜁니다")
                break
            if cfg["delay"]:
                time.sleep(cfg["delay"])
        # 호출이 모두 실패해도(응답 0개) INSUFFICIENT로 판정해 onInsufficient를 따른다
        final, reason, counts = aggregate(categories, criteria)
        results[spec] = {"verdict": final, "reason": reason, "counts": counts, "answered": len(categories),
                         "runs": cfg["runs"], "criteria": criteria}
        log(f"[{spec}] {final} — {reason} {counts} (응답 {len(categories)}/{cfg['runs']})")
        action = {"CONTAMINATED": cfg["onContaminated"], "SUSPECT": cfg["onSuspect"],
                  "INSUFFICIENT": cfg["onInsufficient"]}.get(final, "continue")
        if action == "exclude":
            excluded.append(spec)
        elif action == "stop":
            stop_reasons.append(f"{spec}: {final}")
    outputs = {"results": results, "excluded": excluded}
    if stop_reasons:
        raise PipelineError("사전 학습 점검에서 멈춤: " + ", ".join(stop_reasons), outputs)
    if excluded and not [m for m in config["stages"]["generate"]["models"] if m not in excluded]:
        raise PipelineError("사전 학습 점검으로 생성 모델이 모두 빠졌습니다: " + ", ".join(excluded), outputs)
    return outputs


def stage_generate(config, state, log, caller=call):
    cfg = config["stages"]["generate"]
    case = load_json(input_file(config, state, "case"))
    batch_dir = batch_dir_for(config, case)
    options = {"temperature": cfg["temperature"], "max_tokens": cfg["maxTokens"], "timeout": cfg["timeout"],
               "delay": cfg["delay"], "caller": caller, "log": log}
    summary = {}
    try:
        for spec in active_models(config, state):
            records = generate_runs(case, [spec], cfg["runs"], batch_dir, **options)
            retries = 0
            # 검증을 통과한 회차가 하나도 없으면 한 번씩 더 생성한다 (최대 maxRetries회)
            while (not any(r["validation"]["ok"] for r in records) and retries < cfg["maxRetries"]
                   and not any(r["callErrorKind"] == LLMQuotaExhaustedError.kind for r in records)):  # 한도 소진이면 재생성도 실패한다
                retries += 1
                log(f"[{spec}] 검증 통과 회차 없음 → 재생성 {retries}/{cfg['maxRetries']}")
                records += generate_runs(case, [spec], 1, batch_dir, **options)
            summary[spec] = {"runs": len(records), "valid": sum(1 for r in records if r["validation"]["ok"]),
                             "retries": retries}
    except (GenerateError, LLMError) as e:
        raise PipelineError(f"생성 실패: {e}", {"batchDir": str(batch_dir), "models": summary})
    outputs = {"batchDir": str(batch_dir), "models": summary}
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
    return [line.strip() for line in stderr.splitlines() if "NOTICE" in line]


STAGE_FUNCTIONS = {
    "extract": stage_extract,
    "court": stage_court,
    "contamination": stage_contamination,
    "generate": stage_generate,
    "select": stage_select,
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
    providers = sorted({parse_model_spec(m)[0] for m in config["stages"]["generate"]["models"]
                        + (config["stages"]["contamination"]["models"] or [])})
    log(f"단계: {' → '.join(steps)}")
    if "extract" in steps:
        log(f"외부 전송: 비식별화 단계에서 마스킹한 판결문이 {extract_provider(config)}로 전송됩니다")
    if "court" in steps:
        log(f"외부 전송: 재판부 판결 초안 단계에서 마스킹한 판결문이 {providers_text(court_model(config))}로 전송됩니다")
    if {"contamination", "generate"} & set(steps):
        log(f"외부 전송: 비식별화한 사건 내용이 {', '.join(providers)}로 전송됩니다")
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
