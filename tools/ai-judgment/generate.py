"""AI 판결을 여러 모델 · 여러 회차로 생성하고 검증까지 기록한다 (BE-30).

사용법:
    # API로 생성 (모델은 '공급자:모델ID', 여러 개 지정 가능)
    python3 generate.py run cases/case.json --model openai:gpt-5 --model gemini:gemini-3.7-flash --runs 3

    # 채팅 화면(Claude 앱 · AI Studio 등)에서 받은 응답 파일을 같은 기록에 넣기
    python3 generate.py import cases/case.json --model manual:gemini-app out/answer1.json out/answer2.json

결과는 out/runs/<batch>/<공급자__모델>/run-001.json 형태로 쌓인다 (git 제외 폴더).
- run-NNN.json: 메타(모델 · 토큰 · 소요 시간) · 원문 응답 · 파싱 결과 · 검증 결과
- run-NNN.output.json: 파싱한 판결 JSON만. 검수를 마치면 to_seed_sql.py에 그대로 넣는다
- batch.json: 이 묶음의 사건 제목 · 프롬프트 버전 · 프롬프트 해시. 프롬프트가 다른 실행이 섞이지 않게 막는다
모델별 비교표는 compare.py로 만든다.

생성 원칙(검색 · 그라운딩 끔, 매 회차 새 요청)은 README "외부 LLM으로 생성할 때"와 같다.
API 호출은 매 회차 독립 요청이라 이전 회차 응답이 섞이지 않는다.
"""

import argparse
import datetime
import hashlib
import json
import re
import sys
import time
from pathlib import Path

from build_prompt import InputError, build_prompt
from common import TOOL_DIR, final_penalty, load_json
from llm import DEFAULT_MAX_TOKENS, DEFAULT_TIMEOUT, LLMError, api_key, call, parse_model_spec
from validate_output import parse_output, validate

RUNS_DIR = TOOL_DIR / "out" / "runs"
RUN_SCHEMA = "ai-judgment-run-v1"
BATCH_NAME_PATTERN = re.compile(r"^[A-Za-z0-9][A-Za-z0-9._-]*$")


class GenerateError(Exception):
    pass


def model_slug(spec):
    """폴더 이름으로 쓸 수 있게 '공급자:모델' → '공급자__모델' (영숫자 · . · - · _ 외는 _로)."""
    provider, model = parse_model_spec(spec)
    return f"{provider}__{re.sub(r'[^A-Za-z0-9._-]', '_', model)}"


def prompt_digest(prompt):
    text = json.dumps([prompt["promptVersion"], prompt["system"], prompt["user"]], ensure_ascii=False)
    return hashlib.sha256(text.encode("utf-8")).hexdigest()


def prepare_batch(case, batch_dir):
    """batch.json을 만들거나, 이미 있으면 같은 사건 · 같은 프롬프트인지 확인한다."""
    try:
        prompt = build_prompt(case)
    except InputError as e:
        raise GenerateError(str(e)) from e
    info = {
        "caseTitle": case["title"],
        "promptVersion": prompt["promptVersion"],
        "promptSha256": prompt_digest(prompt),
    }
    path = Path(batch_dir) / "batch.json"
    if path.exists():
        existing = load_json(path)
        if existing.get("promptSha256") != info["promptSha256"]:
            raise GenerateError(
                f"{batch_dir}는 다른 사건 · 프롬프트로 만든 묶음입니다 "
                f"(기존: {existing.get('caseTitle')} / {existing.get('promptVersion')}). "
                "비교가 섞이지 않도록 --batch로 새 이름을 정하세요"
            )
    else:
        path.parent.mkdir(parents=True, exist_ok=True)
        info["createdAt"] = _now()
        _write_json(path, info)
        (path.parent / "prompt.md").write_text(
            f"<!-- prompt_version: {prompt['promptVersion']} -->\n# [SYSTEM]\n\n{prompt['system']}\n# [USER]\n\n{prompt['user']}\n",
            encoding="utf-8",
        )
    return prompt


def evaluate(case, raw_text):
    """원문 응답 → (파싱한 판결, 파싱 오류, 검증 결과)."""
    try:
        output = parse_output(raw_text)
    except (json.JSONDecodeError, ValueError) as e:
        return None, f"JSON으로 읽을 수 없습니다: {e}", {"ok": False, "errors": ["JSON 파싱 실패"], "warnings": []}
    report = validate(case, output)
    return output, None, {"ok": report.ok, "errors": report.errors, "warnings": report.warnings}


def next_run_index(model_dir):
    indexes = [int(m.group(1)) for p in Path(model_dir).glob("run-*.json")
               if (m := re.match(r"^run-(\d+)\.json$", p.name))]
    return max(indexes, default=0) + 1


def save_run(model_dir, spec, case, prompt, raw_text, meta, call_error=None, settings=None):
    """실행 한 건을 기록한다. 호출이 실패해도 기록을 남겨 비교표의 실패율에 넣는다."""
    model_dir = Path(model_dir)
    model_dir.mkdir(parents=True, exist_ok=True)
    index = next_run_index(model_dir)
    if call_error is None:
        output, parse_error, validation = evaluate(case, raw_text)
    else:
        output, parse_error, validation = None, None, {"ok": False, "errors": ["API 호출 실패"], "warnings": []}
    record = {
        "schema": RUN_SCHEMA,
        "runIndex": index,
        "modelSpec": spec,
        "createdAt": _now(),
        "promptVersion": prompt["promptVersion"],
        "settings": settings or {},
        "meta": meta,
        "callError": call_error,
        "rawText": raw_text,
        "parseError": parse_error,
        "output": output,
        "finalPenalty": final_penalty(output) if isinstance(output, dict) else None,
        "validation": validation,
    }
    path = model_dir / f"run-{index:03d}.json"
    _write_json(path, record)
    if output is not None:
        _write_json(model_dir / f"run-{index:03d}.output.json", output)
    return path, record


def generate_runs(case, specs, runs, batch_dir, *, temperature=None, max_tokens=DEFAULT_MAX_TOKENS,
                  timeout=DEFAULT_TIMEOUT, delay=0.0, caller=call, log=print):
    """모델마다 runs회 생성해 기록하고, 기록 목록을 돌려준다. 파이프라인(BE-31)에서도 이 함수를 쓴다."""
    for spec in specs:
        provider, _ = parse_model_spec(spec)
        if provider == "manual":
            raise GenerateError(f"{spec}: manual 공급자는 run이 아니라 import로 넣습니다")
        if caller is call:
            api_key(provider)  # 키가 없으면 실패 기록을 쌓지 않고 바로 멈춘다
    prompt = prepare_batch(case, batch_dir)
    settings = {"temperature": temperature, "maxTokens": max_tokens}
    records = []
    for spec in specs:
        model_dir = Path(batch_dir) / model_slug(spec)
        for i in range(runs):
            if records and delay:
                time.sleep(delay)
            try:
                result = caller(spec, prompt["system"], prompt["user"],
                                temperature=temperature, max_tokens=max_tokens, timeout=timeout)
                path, record = save_run(model_dir, spec, case, prompt, result.text, result.meta(), settings=settings)
            except LLMError as e:
                provider, model = parse_model_spec(spec)
                meta = {"provider": provider, "model": model}
                path, record = save_run(model_dir, spec, case, prompt, None, meta, call_error=str(e), settings=settings)
            records.append(record)
            log(f"[{spec}] {i + 1}/{runs} → {path.name}: {describe(record)}")
    return records


def import_runs(case, spec, response_paths, batch_dir, log=print):
    """채팅 화면에서 받은 응답 파일을 manual 실행으로 기록한다."""
    provider, model = parse_model_spec(spec)
    if provider != "manual":
        raise GenerateError(f"import에는 manual:<이름>을 씁니다 (받은 값: {spec})")
    # 중간 파일이 없어 일부만 기록되고 멈추지 않게 먼저 모두 확인한다
    missing = [str(p) for p in response_paths if not Path(p).is_file()]
    if missing:
        raise GenerateError(f"응답 파일이 없습니다: {', '.join(missing)}")
    prompt = prepare_batch(case, batch_dir)
    model_dir = Path(batch_dir) / model_slug(spec)
    records = []
    for response_path in response_paths:
        raw_text = Path(response_path).read_text(encoding="utf-8")
        meta = {"provider": "manual", "model": model, "source": Path(response_path).name}
        path, record = save_run(model_dir, spec, case, prompt, raw_text, meta)
        records.append(record)
        log(f"[{spec}] {response_path} → {path.name}: {describe(record)}")
    return records


def describe(record):
    if record["callError"]:
        return f"호출 실패 — {record['callError']}"
    if record["parseError"]:
        return f"파싱 실패 — {record['parseError']}"
    validation = record["validation"]
    verdict = "통과" if validation["ok"] else "실패"
    output = record["output"]
    usage = (record.get("meta") or {}).get("usage") or {}
    tokens = f", 토큰 {usage['totalTokens']}" if usage.get("totalTokens") is not None else ""
    return (f"검증 {verdict} (오류 {len(validation['errors'])}, 경고 {len(validation['warnings'])}) · "
            f"{record['finalPenalty']} {output.get('prisonMonths') or output.get('fineAmount') or ''}{tokens}").rstrip()


def _now():
    return datetime.datetime.now(datetime.timezone.utc).astimezone().isoformat(timespec="seconds")


def _write_json(path, value):
    Path(path).write_text(json.dumps(value, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def batch_dir_for(args):
    name = args.batch or Path(args.case_input).stem
    if not BATCH_NAME_PATTERN.match(name):
        raise GenerateError(f"--batch는 영문 · 숫자 · . · - · _만 씁니다: {name!r}")
    return Path(args.runs_dir) / name


def main():
    parser = argparse.ArgumentParser(description="AI 판결을 여러 모델로 생성 · 검증해 기록한다")
    sub = parser.add_subparsers(dest="command", required=True)

    common_args = argparse.ArgumentParser(add_help=False)
    common_args.add_argument("case_input", help="사건 입력 JSON")
    common_args.add_argument("--batch", help="결과 묶음 이름 (기본: 사건 파일 이름). 같은 묶음끼리 비교표를 만든다")
    common_args.add_argument("--runs-dir", default=str(RUNS_DIR), help=f"결과 폴더 (기본: {RUNS_DIR})")

    run_cmd = sub.add_parser("run", parents=[common_args], help="API로 생성한다")
    run_cmd.add_argument("--model", action="append", required=True, dest="models",
                         help="'공급자:모델ID' (openai · anthropic · gemini). 여러 번 지정 가능")
    run_cmd.add_argument("--runs", type=int, default=1, help="모델마다 생성할 횟수 (기본 1)")
    run_cmd.add_argument("--temperature", type=float, help="지정하지 않으면 공급자 기본값")
    run_cmd.add_argument("--max-tokens", type=int, default=DEFAULT_MAX_TOKENS)
    run_cmd.add_argument("--timeout", type=int, default=DEFAULT_TIMEOUT, help="요청 한 건 대기 시간(초)")
    run_cmd.add_argument("--delay", type=float, default=0.0, help="요청 사이 쉬는 시간(초). 무료 등급 분당 한도용")

    import_cmd = sub.add_parser("import", parents=[common_args], help="채팅 화면에서 받은 응답 파일을 넣는다")
    import_cmd.add_argument("--model", required=True, help="'manual:<이름>' (예: manual:gemini-app)")
    import_cmd.add_argument("responses", nargs="+", help="응답 파일 (JSON 또는 ```json 코드 블록)")

    args = parser.parse_args()
    try:
        case = load_json(args.case_input)
        batch_dir = batch_dir_for(args)
        if args.command == "run":
            if args.runs < 1:
                raise GenerateError("--runs는 1 이상이어야 합니다")
            records = generate_runs(case, args.models, args.runs, batch_dir, temperature=args.temperature,
                                    max_tokens=args.max_tokens, timeout=args.timeout, delay=args.delay)
        else:
            records = import_runs(case, args.model, args.responses, batch_dir)
    except (GenerateError, LLMError) as e:
        print(f"[오류] {e}", file=sys.stderr)
        return 1
    passed = sum(1 for r in records if r["validation"]["ok"])
    print(f"검증 통과 {passed}/{len(records)} · 결과 폴더: {batch_dir}")
    print(f"비교표: python3 compare.py {batch_dir}")
    return 0 if passed else 1


if __name__ == "__main__":
    sys.exit(main())
