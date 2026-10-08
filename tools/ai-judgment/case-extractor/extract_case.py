"""판결문(PDF · txt)을 비식별화해 tools/ai-judgment 사건 입력 형식(case.json)으로 가공한다.

사용법 (tools/ai-judgment/case-extractor에서):
    python3 extract_case.py ../cases/raw/판결문.pdf --name long-marriage-conflict
    python3 extract_case.py ../cases/raw/판결문.txt --name long-marriage-conflict --dry-run
    python3 extract_case.py ../cases/raw/1심.pdf ../cases/raw/항소심.pdf --name long-marriage-conflict   # 여러 심급

순서: 텍스트 추출 → 패턴 마스킹(로컬) → Claude API로 비식별화 · 구조화 → 남은 개인정보 · 판결 누출 검사 → 파일 저장.
검사에서 오류가 나오면 case.json을 만들지 않고 보고서만 남긴다.
"""

import argparse
import datetime
import json
import re
import sys
from pathlib import Path

from deidentify import date_in_text, extract_source_info, premask, residual_check, scrub
from schema import (
    EXTRACT_PROMPT_VERSION,
    OUTPUT_SCHEMA,
    OutputError,
    build_case_input,
    build_court_judgment,
    build_factor_extras,
    check_output,
    court_evaluation_check,
    sentence_leak_check,
    validate_against_schema,
    visible_texts,
)

EXTRACTOR_DIR = Path(__file__).resolve().parent
PARENT_DIR = EXTRACTOR_DIR.parent
sys.path.insert(0, str(PARENT_DIR))

from build_prompt import InputError, check_case_input  # noqa: E402
from common import find_forbidden_keys, write_json  # noqa: E402
from llm import LLMError, api_key, call as llm_call, model_provider, parse_model_spec  # noqa: E402
from validate_output import parse_output  # noqa: E402

DEFAULT_MODEL = "claude-opus-5-5"
DEFAULT_EFFORT = "high"
DEFAULT_OUT_DIR = PARENT_DIR / "cases"  # tools/ai-judgment/.gitignore가 막는 위치
MAX_TOKENS = 64000
NON_CLAUDE_MAX_TOKENS = 32000  # Claude 외 공급자의 기본 출력 상한 (공급자마다 모델별 한도가 달라 더 작게 잡는다)
SCHEMA_RETRIES = 2  # Claude 외 공급자가 JSON · 스키마를 어겼을 때 다시 요청하는 횟수
TRUNCATED_REASONS = ("length", "max_tokens", "MAX_TOKENS")
# 정상 종료 사유. 이 밖의 사유(Gemini SAFETY · RECITATION, OpenAI content_filter 등)는 차단 · 거절이라 다시 보내도
# 같으므로 재시도하지 않는다 (판결문을 같은 공급자에게 거듭 보내지 않게)
NORMAL_STOP_REASONS = (None, "stop", "STOP", "end_turn", "stop_sequence")

NAME_PATTERN = re.compile(r"^[a-z0-9]+(?:-[a-z0-9]+)*$")
TXT_ENCODINGS = ("utf-8-sig", "cp949", "euc-kr")


class ExtractError(Exception):
    pass


class ModelUnavailableError(ExtractError):
    """호출 자체가 막혀 응답을 받지 못했다 (과부하 · 분당 한도 · 일 한도). 다음 모델로 넘어갈 수 있다 (BE-45).

    kind는 llm.LLMError.kind와 같다: overloaded · rate_limit · daily_quota. 형식 · 검사 실패 같은 품질 문제는
    이 오류가 아니며, 다른 모델로 덮지 않고 그대로 멈춘다.
    """

    def __init__(self, message, kind=None):
        super().__init__(message)
        self.kind = kind
        self.skipped = []


# rate_limit은 분당 한도로 단정하지 않는다: Claude의 429(rate_limit_error)는 요청률 · 사용량 · 지출 한도를 모두 가리킬 수 있다
KIND_LABELS = {"overloaded": "과부하", "rate_limit": "요청 한도(429)", "daily_quota": "일 한도 · 크레딧 소진"}


def model_chain(model):
    """모델 설정(문자열 또는 목록) → 모델 지정 목록. 앞 모델부터 쓰고, 호출이 막히면 다음 모델로 넘어간다 (BE-45)."""
    models = [model] if isinstance(model, str) else model
    if not isinstance(models, (list, tuple)) or not models or not all(isinstance(m, str) and m.strip() for m in models):
        raise ExtractError(f"모델은 모델 이름 문자열이거나 그 목록입니다: {model!r}")
    models = [m.strip() for m in models]
    if len(set(models)) != len(models):
        raise ExtractError("모델 목록에 같은 모델이 두 번 들어 있습니다: " + ", ".join(models))
    return models


FREE_TIER_WARNING = "⚠ 무료 등급 모델입니다: 판결문 원문이 제품 개선에 쓰이고 사람이 검토할 수 있습니다"


def run_with_fallback(models, attempt, log=print, free_models=()):
    """모델 목록을 앞에서부터 attempt(모델)로 시도한다 (BE-45). (결과, 응답한 모델, 건너뛴 기록)을 돌려준다.

    호출이 막힌 경우(ModelUnavailableError: 과부하 · 한도, 앞 모델의 재시도는 이미 끝남)에만 다음 모델로 넘어간다.
    다음 모델은 같은 입력으로 처음부터 다시 시작하고, 앞 모델의 부분 결과는 쓰지 않는다 (한 결과에 두 모델이 섞이지 않게).
    품질 문제(형식 · 검사 실패)는 넘어가지 않고 그대로 멈춘다.
    free_models: 무료 등급 백업으로 허용한 모델. 그 모델로 넘어가는 순간 경고를 함께 남긴다 (allowFreeTierForJudgment).
    """
    skipped = []
    for index, spec in enumerate(models):
        try:
            return attempt(spec), spec, skipped
        except ModelUnavailableError as e:
            skipped.append({"model": spec, "kind": e.kind, "error": str(e)})
            if index + 1 == len(models):
                e.skipped, e.model = skipped, spec
                if len(models) == 1:
                    raise  # 원래 오류와 원인 체인을 그대로 둔다
                final = ModelUnavailableError(
                    "모든 모델을 쓸 수 없었습니다: " + " | ".join(
                        f"{s['model']} ({KIND_LABELS.get(s['kind'], s['kind'])}) {s['error'].splitlines()[0]}" for s in skipped),
                    kind=e.kind)
                final.skipped, final.model = skipped, spec
                raise final from e
            following = models[index + 1]
            log(f"[{spec}] 호출 불가 ({KIND_LABELS.get(e.kind, e.kind)}) → 다음 모델 {following}로 처음부터 다시 요청합니다"
                + (f"\n{FREE_TIER_WARNING}: {following}" if following in free_models else ""))
        except Exception as e:
            # 품질 문제는 다른 모델로 넘기지 않고 멈추되, 어느 모델에서 났고 앞에서 어떤 모델을 건너뛰었는지 알 수 있게 붙인다
            e.skipped, e.model = skipped, spec
            raise


def read_judgment(path):
    """판결문 파일에서 텍스트를 꺼낸다. PDF는 pypdf로, txt는 UTF-8 · CP949 순서로 읽는다."""
    path = Path(path)
    if not path.is_file():
        raise ExtractError(f"파일이 없습니다: {path}")
    suffix = path.suffix.lower()
    if suffix == ".pdf":
        text = _read_pdf(path)
    elif suffix == ".txt":
        text = _read_txt(path)
    else:
        raise ExtractError("PDF(.pdf) 또는 텍스트(.txt) 파일만 받습니다")
    if len(text.strip()) < 200:
        raise ExtractError("텍스트를 거의 추출하지 못했습니다. 스캔본 PDF라면 OCR로 텍스트를 만든 뒤 txt로 넣으세요")
    return text


def _read_pdf(path):
    try:
        from pypdf import PdfReader
    except ImportError as e:
        raise ExtractError("PDF를 읽으려면 pypdf가 필요합니다: pip install -r requirements.txt") from e
    reader = PdfReader(str(path))
    return "\n".join(page.extract_text() or "" for page in reader.pages)


def _read_txt(path):
    data = path.read_bytes()
    for encoding in TXT_ENCODINGS:
        try:
            return data.decode(encoding)
        except UnicodeDecodeError:
            continue
    raise ExtractError("텍스트 파일 인코딩을 알 수 없습니다 (UTF-8 · CP949로 저장하세요)")


def read_judgments(input_paths):
    """판결문 여러 개(예: 1심 · 항소심)를 읽어 [(파일, 원문)]으로 돌려준다."""
    if isinstance(input_paths, (str, Path)):
        input_paths = [input_paths]
    return [(Path(path), read_judgment(path)) for path in input_paths]


def join_judgments(judgments):
    """모델에 보낼 한 덩어리. 여러 개면 판결문마다 머리표를 붙인다."""
    if len(judgments) == 1:
        return judgments[0][1]
    return "\n\n".join(f"===== 판결문 {i} =====\n{text}" for i, (_, text) in enumerate(judgments, start=1))


def build_source_records(judgments):
    """case_source 적재용 원본 정보 (내부 전용). 원문은 마스킹 전 그대로 둔다."""
    return [{"file": path.name, **extract_source_info(text), "originalText": text} for path, text in judgments]


def resolve_incident_date(value, judgments, sources):
    """모델이 준 사건 발생일을 확인한다 (BE-38). (확정한 날짜 또는 None, 경고 목록).

    날짜 형식, 원문에 실제로 나오는지, 선고일보다 늦지 않은지를 본다. 하나라도 어긋나면 넣지 않는다(관리자가 채운다).
    경고에는 날짜 값을 적지 않는다 (보고서에 정확한 날짜를 남기지 않게).
    """
    if value is None:
        return None, ["사건 발생일을 찾지 못했습니다 (관리자가 채우거나 파이프라인 설정 incidentDate로 넣으세요)"]
    try:
        parsed = datetime.date.fromisoformat(value)
    except (TypeError, ValueError):
        return None, ["사건 발생일 형식이 YYYY-MM-DD가 아니라 넣지 않았습니다"]
    if not any(date_in_text(value, text) for _, text in judgments):
        return None, ["모델이 준 사건 발생일이 원문에서 확인되지 않아 넣지 않았습니다"]
    decided = [s.get("decidedAt") for s in sources if s.get("decidedAt")]
    if decided and parsed > min(datetime.date.fromisoformat(d) for d in decided):
        return None, ["사건 발생일이 선고일보다 늦어 넣지 않았습니다"]
    return value, []


def build_messages(masked_text):
    system = (EXTRACTOR_DIR / "prompts" / "extract_system.md").read_text(encoding="utf-8")
    user = "다음 판결문을 가공해 줘.\n\n<judgment>\n" + masked_text + "\n</judgment>"
    return system, user


def split_model(model):
    """모델 지정 → (공급자, 모델 ID). 공급자가 없는 이름(`claude-opus-5-5`)과 `anthropic:`는 Claude SDK 경로(claude)다."""
    try:
        provider = model_provider(model)  # 공급자 규칙은 llm.model_provider 하나에서 정한다
        name = parse_model_spec(model)[1] if ":" in model else model
    except LLMError as e:
        raise ExtractError(str(e)) from e
    if provider == "manual":
        raise ExtractError("비식별화는 API 공급자만 씁니다 (manual 불가)")
    return ("claude" if provider == "anthropic" else provider), name


def schema_instruction():
    """구조화 출력을 강제할 수 없는 공급자에게 보낼 스키마 안내 (사용자 메시지 끝에 붙인다)."""
    return ("\n\n---\n응답은 아래 JSON 스키마를 따르는 **JSON 객체 하나만** 출력한다. 코드 블록 표시나 설명 문장을 붙이지 않는다. "
            "모든 필드를 채우고, 스키마에 없는 필드는 넣지 않는다.\n\n<json_schema>\n"
            + json.dumps(OUTPUT_SCHEMA, ensure_ascii=False) + "\n</json_schema>")


def call_llm(system, user, model, effort, max_tokens=None, caller=llm_call):
    """Claude 외 공급자(OpenAI · Gemini) 호출. (응답 JSON, 실제 응답한 모델 이름)을 돌려준다 (BE-35).

    스키마를 강제하는 기능이 없으므로 user에 스키마 안내(`schema_instruction`)가 이미 붙어 있어야 한다(run이 붙인다).
    응답이 JSON이 아니거나 스키마와 다르면 오류 내용을 알려 다시 요청한다(최대 SCHEMA_RETRIES회).
    effort는 Claude 전용이라 쓰지 않는다. caller는 테스트에서 가짜 호출을 넣는 자리다.
    """
    spec = model
    request = user
    last_error = None
    for attempt in range(1 + SCHEMA_RETRIES):
        message = request if last_error is None else (
            request + f"\n\n(이전 응답이 형식 오류였습니다: {last_error}. 형식을 지켜 다시 출력하세요)")
        try:
            result = caller(spec, system, message, max_tokens=max_tokens or NON_CLAUDE_MAX_TOKENS, json_output=True)
        except LLMError as e:
            if e.kind:  # 과부하 · 한도: 다음 모델로 넘어갈 수 있다 (BE-45)
                raise ModelUnavailableError(str(e), e.kind) from e
            raise ExtractError(str(e)) from e
        if result.stop_reason in TRUNCATED_REASONS:
            raise ExtractError(f"출력이 최대 길이({max_tokens or NON_CLAUDE_MAX_TOKENS} 토큰)에서 잘렸습니다. "
                               "--max-tokens를 늘리거나 판결문을 나눠 넣으세요")
        if result.stop_reason not in NORMAL_STOP_REASONS:
            raise ExtractError(f"응답이 비정상 종료되었습니다 (사유: {result.stop_reason}). 공급자의 안전 필터 · 인용 차단 등이면 "
                               "다시 보내도 같으므로 재시도하지 않습니다. 다른 모델을 쓰세요")
        try:
            output = parse_output(result.text)
        except (json.JSONDecodeError, ValueError) as e:
            last_error = f"JSON으로 읽을 수 없음: {e}"
            continue
        errors = validate_against_schema(output, OUTPUT_SCHEMA)
        if errors:
            last_error = "; ".join(errors[:5])
            continue
        return output, result.served_model
    raise ExtractError(f"{spec}: 응답이 {1 + SCHEMA_RETRIES}번 모두 형식에 맞지 않았습니다 ({last_error}). "
                       "다른 모델을 쓰거나 Claude(스키마 강제)를 쓰세요")


def call_model(system, user, model, effort, max_tokens=None):
    """모델 지정에 맞는 호출을 고른다. `claude-…` · `anthropic:…`는 Claude SDK(구조화 출력), `openai:…` · `gemini:…`는 call_llm."""
    provider, name = split_model(model)
    if provider == "claude":
        return call_claude(system, user, name, effort)
    return call_llm(system, user, f"{provider}:{name}", effort, max_tokens)


def call_claude(system, user, model, effort):
    """Claude API 호출. (응답 JSON, 실제 응답한 모델 이름)을 돌려준다.

    구조화 출력(output_config.format)으로 스키마에 맞는 JSON만 받는다. 판결문이 길어 출력도 길어질 수 있어 스트리밍으로 받는다.
    모델이 요청을 거절하면 서버 측 대체 모델(fallbacks: default)이 이어서 처리한다.
    """
    try:
        import anthropic
    except ImportError as e:
        raise ExtractError("Claude API를 쓰려면 anthropic 패키지가 필요합니다: pip install -r requirements.txt") from e

    client = anthropic.Anthropic()
    try:
        with client.beta.messages.stream(
            model=model,
            max_tokens=MAX_TOKENS,
            betas=["server-side-fallback-2026-07-01"],
            fallbacks="default",
            system=system,
            messages=[{"role": "user", "content": user}],
            output_config={"effort": effort, "format": {"type": "json_schema", "schema": OUTPUT_SCHEMA}},
        ) as stream:
            message = stream.get_final_message()
    except anthropic.AuthenticationError as e:
        raise ExtractError("API 인증 실패: ANTHROPIC_API_KEY 또는 `ant auth login`을 확인하세요") from e
    except anthropic.RateLimitError as e:
        raise ModelUnavailableError("요청 한도를 넘었습니다. 잠시 뒤 다시 실행하세요", "rate_limit") from e
    except anthropic.BadRequestError as e:
        raise ExtractError(f"잘못된 요청: {e.message}") from e
    except anthropic.APIStatusError as e:
        if e.status_code in (503, 529):
            raise ModelUnavailableError(f"서비스 과부하 ({e.status_code}): {e.message}", "overloaded") from e
        raise ExtractError(f"API 오류 ({e.status_code}): {e.message}") from e
    except anthropic.APIConnectionError as e:
        raise ExtractError("API에 연결하지 못했습니다. 네트워크를 확인하세요") from e

    if message.stop_reason == "refusal":
        raise ExtractError("모델이 요청을 거절했습니다 (대체 모델도 거절)")
    if message.stop_reason == "max_tokens":
        raise ExtractError(f"출력이 max_tokens({MAX_TOKENS})에서 잘렸습니다")
    return parse_response_text(message.content), message.model


def parse_response_text(content):
    """응답 블록에서 JSON 본문을 꺼낸다. 대체 모델로 넘어갔으면 마지막 fallback 블록 뒤의 텍스트만 쓴다."""
    parts = []
    for block in content:
        if block.type == "fallback":
            parts = []
        elif block.type == "text":
            parts.append(block.text)
    text = "".join(parts).strip()
    if not text:
        raise ExtractError("응답에 JSON 본문이 없습니다")
    try:
        return json.loads(text)
    except json.JSONDecodeError as e:
        raise ExtractError(f"응답 JSON을 읽지 못했습니다: {e}") from e


def report_texts(output):
    """보고서에만 저장하는 자유 텍스트 [(위치, 문자열)]. 사용자에게 보이지 않아도 개인정보 검사는 한다."""
    texts = [(f"reviewNotes[{i}]", note) for i, note in enumerate(output.get("reviewNotes", []))]
    texts += [(f"penaltyRuleBasis[{r['penaltyType']}]", r["allowedBasis"]) for r in output.get("penaltyRules", [])]
    texts += [(f"eligibility.reasons[{i}]", reason)
              for i, reason in enumerate((output.get("eligibility") or {}).get("reasons", []))]
    return texts


def process_output(output):
    """모델 응답 → (case.json, 실제 판결, 오류 목록, 경고 목록)."""
    errors, warnings = check_output(output)
    case_input = build_case_input(output)
    court = build_court_judgment(output)

    forbidden = find_forbidden_keys(case_input)
    if forbidden:
        errors.append("AI 입력에 넣으면 안 되는 항목: " + ", ".join(forbidden))
    try:
        check_case_input(case_input)
    except InputError as e:
        errors.append(f"사건 입력 형식 오류 (build_prompt.py 기준): {e}")

    texts = visible_texts(case_input)
    pii_errors, pii_warnings = residual_check(texts)
    leak_errors, leak_warnings = sentence_leak_check(case_input, court)
    report_errors, report_warnings = residual_check(report_texts(output))
    errors += pii_errors + report_errors + leak_errors
    warnings += pii_warnings + report_warnings + leak_warnings + court_evaluation_check(case_input)
    return case_input, court, errors, warnings


def run(input_path, name, out_dir=DEFAULT_OUT_DIR, model=DEFAULT_MODEL, effort=DEFAULT_EFFORT,
        dry_run=False, call=None, max_tokens=None, log=print, free_models=()):
    """전체 흐름. 만든 파일 경로 목록을 돌려준다. 검사 오류가 있으면 ExtractError(보고서는 남김).

    model은 문자열 또는 모델 목록이다. 목록이면 앞 모델의 호출이 과부하 · 한도로 막혔을 때 다음 모델로 넘어간다 (BE-45).
    free_models는 그중 무료 등급 백업으로 허용한 모델이다. 응답한 모델이 이 중 하나면 보고서 usedFreeTier가 true다.
    """
    if not NAME_PATTERN.match(name):
        raise ExtractError("--name은 영어 소문자 · 숫자 · 하이픈만 씁니다 (사건을 특정할 수 없는 이름, 예: long-marriage-conflict)")
    out_dir = Path(out_dir)
    models = model_chain(model)
    providers = [split_model(m)[0] for m in models]  # 모델 지정이 잘못됐으면 판결문을 읽기 전에 알린다
    judgments = read_judgments(input_path)
    masked_text, mask_counts = premask(join_judgments(judgments))
    system, user = build_messages(masked_text)

    def request_for(spec):
        # 스키마를 강제할 수 없는 공급자에게는 스키마를 프롬프트로 보낸다 (dry-run 파일에도 그대로)
        return user if split_model(spec)[0] == "claude" else user + schema_instruction()

    if dry_run:
        path = out_dir / f"{name}.request.md"
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(f"# [SYSTEM]\n\n{system}\n\n# [USER]\n\n{request_for(models[0])}\n", encoding="utf-8")
        return [path]

    if call is None:
        for provider in providers:  # 대체 모델의 키도 호출 전에 확인한다
            if provider != "claude":
                try:
                    api_key(provider)
                except LLMError as e:
                    raise ExtractError(str(e)) from e
        call = lambda s, u, m, e: call_model(s, u, m, e, max_tokens)  # noqa: E731
    (output, served_model), used_model, skipped = run_with_fallback(
        models, lambda spec: call(system, request_for(spec), spec, effort), log, free_models)
    try:
        case_input, court, errors, warnings = process_output(output)
    except (KeyError, TypeError, OutputError) as e:
        raise ExtractError(f"응답 형식이 스키마와 다릅니다: {e}") from e

    sources = build_source_records(judgments)
    incident_date, incident_warnings = resolve_incident_date(output.get("incidentDate"), judgments, sources)
    warnings += incident_warnings
    report = {
        "promptVersion": EXTRACT_PROMPT_VERSION,
        "model": served_model,
        "requestedModel": used_model,
        **({"modelChain": models, "fallbacks": [{**f, "error": scrub(f["error"])} for f in skipped]}
           if len(models) > 1 else {}),
        **({"usedFreeTier": used_model in free_models} if free_models else {}),
        "status": "ERROR" if errors else "NEEDS_REVIEW",
        "premasked": mask_counts,
        "deidentifiedItems": output.get("deidentifiedItems", []),
        "factorExtras": build_factor_extras(output),
        "penaltyRuleBasis": {r["penaltyType"]: scrub(r["allowedBasis"]) for r in output.get("penaltyRules", [])},
        "eligibility": {
            "eligible": output["eligibility"]["eligible"],
            "reasons": [scrub(reason) for reason in output["eligibility"]["reasons"]],
        },
        "errors": errors,
        "warnings": warnings,
        "reviewNotes": [scrub(note) for note in output.get("reviewNotes", [])],
    }
    report_path = out_dir / f"{name}.report.json"
    write_json(report_path, report)
    case_path = out_dir / f"{name}.case.json"
    court_path = out_dir / f"{name}.court_judgment_internal.json"
    source_path = out_dir / f"{name}.source_internal.json"
    if errors:
        # 같은 이름으로 다시 돌렸을 때 이전 실행의 결과물이 남아 통과한 것처럼 보이지 않게 한다
        for stale in (case_path, court_path, source_path, out_dir / f"{name}.request.md"):
            stale.unlink(missing_ok=True)
        raise ExtractError(f"검사 오류 {len(errors)}건 — case.json을 만들지 않았습니다. 보고서: {report_path}")

    write_json(case_path, case_input)
    write_json(court_path, court)
    # 원본 판결문 정보(사건번호 · 법원명 · 선고일 · 원문)는 API로 보내지 않고 로컬에서 꺼낸 값이다. 내부 전용 (BE-31)
    write_json(source_path, {
        "_comment": "내부 전용. 사건 적재 SQL의 case_source에만 쓴다. 사용자 화면 · AI 입력 · 공개 저장소에 넣지 않는다.",
        # 사건 발생일 (BE-38). 모델이 원문에서 찾고 원문 · 선고일과 대조해 확인한 값. legal_case.incident_date에 들어간다
        "incidentDate": incident_date,
        "sources": sources,
    })
    return [case_path, court_path, report_path, source_path]


def positive_int(text):
    """--max-tokens: 양의 정수만 받는다 (0이면 기본값으로 조용히 바뀌는 것을 막는다)."""
    try:
        value = int(text)
    except ValueError:
        raise argparse.ArgumentTypeError(f"양의 정수여야 합니다: {text!r}") from None
    if value <= 0:
        raise argparse.ArgumentTypeError(f"양의 정수여야 합니다: {text!r}")
    return value


def main():
    parser = argparse.ArgumentParser(description="판결문을 비식별화해 사건 입력 JSON(case.json)으로 가공한다")
    parser.add_argument("input", nargs="+", help="판결문 파일 (.pdf 또는 .txt). 1심 · 항소심처럼 여러 개를 함께 넣을 수 있다")
    parser.add_argument("--name", required=True, help="출력 파일 이름 (영어 소문자 · 하이픈, 사건을 특정할 수 없게)")
    parser.add_argument("--out-dir", default=str(DEFAULT_OUT_DIR), help=f"출력 폴더 (기본: {DEFAULT_OUT_DIR})")
    parser.add_argument("--model", action="append",
                        help=f"모델 (기본: {DEFAULT_MODEL}). Claude는 모델 ID(또는 anthropic:모델 ID), 다른 공급자는 "
                             "'openai:모델ID' · 'gemini:모델ID' (키는 환경변수). 여러 번 주면 앞 모델이 과부하 · 한도로 막혔을 때 "
                             "다음 모델로 넘어간다")
    parser.add_argument("--effort", default=DEFAULT_EFFORT, choices=("low", "medium", "high", "xhigh", "max"),
                        help="Claude 전용 (다른 공급자는 무시)")
    parser.add_argument("--max-tokens", type=positive_int, help=f"Claude 외 공급자의 출력 상한 (기본 {NON_CLAUDE_MAX_TOKENS})")
    parser.add_argument("--dry-run", action="store_true", help="API를 호출하지 않고, 보낼 내용만 파일로 저장한다")
    args = parser.parse_args()
    try:
        model = args.model[0] if args.model and len(args.model) == 1 else (args.model or DEFAULT_MODEL)
        paths = run(args.input, args.name, args.out_dir, model, args.effort, args.dry_run,
                    max_tokens=args.max_tokens)
    except ExtractError as e:
        print(f"[오류] {e}", file=sys.stderr)
        return 1
    for path in paths:
        print(path)
    if not args.dry_run:
        print("팀 검수 전 초안입니다. 보고서의 warnings · reviewNotes를 확인하고 원 판결문과 대조하세요 (REQ-075).",
              file=sys.stderr)
    return 0


if __name__ == "__main__":
    sys.exit(main())
