"""판결문(PDF · txt)을 비식별화해 tools/ai-judgment 사건 입력 형식(case.json)으로 가공한다.

사용법 (tools/ai-judgment/case-extractor에서):
    python3 extract_case.py ../cases/raw/판결문.pdf --name long-marriage-conflict
    python3 extract_case.py ../cases/raw/판결문.txt --name long-marriage-conflict --dry-run
    python3 extract_case.py ../cases/raw/1심.pdf ../cases/raw/항소심.pdf --name long-marriage-conflict   # 여러 심급

순서: 텍스트 추출 → 패턴 마스킹(로컬) → Claude API로 비식별화 · 구조화 → 남은 개인정보 · 판결 누출 검사 → 파일 저장.
검사에서 오류가 나오면 case.json을 만들지 않고 보고서만 남긴다.
"""

import argparse
import json
import re
import sys
from pathlib import Path

from deidentify import extract_source_info, premask, residual_check, scrub
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
    visible_texts,
)

EXTRACTOR_DIR = Path(__file__).resolve().parent
PARENT_DIR = EXTRACTOR_DIR.parent
sys.path.insert(0, str(PARENT_DIR))

from build_prompt import InputError, check_case_input  # noqa: E402
from common import find_forbidden_keys, write_json  # noqa: E402

DEFAULT_MODEL = "claude-opus-5-5"
DEFAULT_EFFORT = "high"
DEFAULT_OUT_DIR = PARENT_DIR / "cases"  # tools/ai-judgment/.gitignore가 막는 위치
MAX_TOKENS = 64000

NAME_PATTERN = re.compile(r"^[a-z0-9]+(?:-[a-z0-9]+)*$")
TXT_ENCODINGS = ("utf-8-sig", "cp949", "euc-kr")


class ExtractError(Exception):
    pass


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


def build_messages(masked_text):
    system = (EXTRACTOR_DIR / "prompts" / "extract_system.md").read_text(encoding="utf-8")
    user = "다음 판결문을 가공해 줘.\n\n<judgment>\n" + masked_text + "\n</judgment>"
    return system, user


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
        raise ExtractError("요청 한도를 넘었습니다. 잠시 뒤 다시 실행하세요") from e
    except anthropic.BadRequestError as e:
        raise ExtractError(f"잘못된 요청: {e.message}") from e
    except anthropic.APIStatusError as e:
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
        dry_run=False, call=call_claude):
    """전체 흐름. 만든 파일 경로 목록을 돌려준다. 검사 오류가 있으면 ExtractError(보고서는 남김)."""
    if not NAME_PATTERN.match(name):
        raise ExtractError("--name은 영어 소문자 · 숫자 · 하이픈만 씁니다 (사건을 특정할 수 없는 이름, 예: long-marriage-conflict)")
    out_dir = Path(out_dir)
    judgments = read_judgments(input_path)
    masked_text, mask_counts = premask(join_judgments(judgments))
    system, user = build_messages(masked_text)

    if dry_run:
        path = out_dir / f"{name}.request.md"
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(f"# [SYSTEM]\n\n{system}\n\n# [USER]\n\n{user}\n", encoding="utf-8")
        return [path]

    output, served_model = call(system, user, model, effort)
    try:
        case_input, court, errors, warnings = process_output(output)
    except (KeyError, TypeError, OutputError) as e:
        raise ExtractError(f"응답 형식이 스키마와 다릅니다: {e}") from e

    report = {
        "promptVersion": EXTRACT_PROMPT_VERSION,
        "model": served_model,
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
        "sources": build_source_records(judgments),
    })
    return [case_path, court_path, report_path, source_path]


def main():
    parser = argparse.ArgumentParser(description="판결문을 비식별화해 사건 입력 JSON(case.json)으로 가공한다")
    parser.add_argument("input", nargs="+", help="판결문 파일 (.pdf 또는 .txt). 1심 · 항소심처럼 여러 개를 함께 넣을 수 있다")
    parser.add_argument("--name", required=True, help="출력 파일 이름 (영어 소문자 · 하이픈, 사건을 특정할 수 없게)")
    parser.add_argument("--out-dir", default=str(DEFAULT_OUT_DIR), help=f"출력 폴더 (기본: {DEFAULT_OUT_DIR})")
    parser.add_argument("--model", default=DEFAULT_MODEL, help=f"Claude 모델 (기본: {DEFAULT_MODEL})")
    parser.add_argument("--effort", default=DEFAULT_EFFORT, choices=("low", "medium", "high", "xhigh", "max"))
    parser.add_argument("--dry-run", action="store_true", help="API를 호출하지 않고, 보낼 내용만 파일로 저장한다")
    args = parser.parse_args()
    try:
        paths = run(args.input, args.name, args.out_dir, args.model, args.effort, args.dry_run)
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
