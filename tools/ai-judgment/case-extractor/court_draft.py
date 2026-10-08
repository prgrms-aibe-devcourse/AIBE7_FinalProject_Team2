"""재판부 판결(COURT) 초안 생성 · 자동 대조 검사 (BE-38).

사용법 (tools/ai-judgment/case-extractor에서):
    python3 court_draft.py --name fraud-1 --model openai:gpt-5.6-terra

case-extractor가 만든 세 파일을 읽는다 (cases/ 아래, git 제외).
- <name>.case.json: 판단 요소 목록 (factorId · label)
- <name>.source_internal.json: 원본 판결문 원문 (로컬에서 정규식 마스킹한 뒤 모델에 보낸다)
- <name>.court_judgment_internal.json: 실제 판결 형벌 · 형량 (추출기가 뽑은 값. 판결문 주문과 교차 확인한다)

만드는 파일
- <name>.court_draft.json: 재판부 판결 초안. BE-14 형식(judgment · judgmentFactors · excludedFactors)이라
  비공개 저장소 tools/court_judgment_to_sql.py와 공개 저장소 court_seed_sql.py(BE-38) 둘 다 받는다.
  **내부 전용. AI 판결 입력에 절대 넣지 않는다** (FR-4-1, REQ-041) — 실제 판결이 들어 있다.
- <name>.court_report.json: 검사 결과 (오류 · 경고 · 모델이 남긴 확인 메모)

자동 검사 (오류가 하나라도 있으면 초안을 만들지 않는다)
- 발췌 · 근거 문장이 원문을 **글자 그대로** 인용했는지: `⟦ ⟧`로 감싼 바꾼 부분(식별 정보 일반화)만 빼고 마스킹한 원문과 대조
- 형벌 · 형량: 추출기 값과 판결문 주문(로컬 정규식)을 교차 확인
- 판단 요소: 목록에 있는 번호 · 중복 없음 · 방향 UP/DOWN
- 사용자에게 보일 글(요약 · 이유 · 쉬운 설명 · 발췌 · 근거)에 개인정보가 남지 않았는지, 요약 길이, 평가 표현, 부가 처분 종류
초안은 사실 기록이라 반드시 원 판결문과 대조해 검수한 뒤 공개한다 (REQ-075 · 077, 관리자 후검수 BE-33).
"""

import argparse
import datetime
import json
import re
import sys
from pathlib import Path

from deidentify import premask, residual_check, scrub
from schema import _STRING, _object, validate_against_schema

EXTRACTOR_DIR = Path(__file__).resolve().parent
PARENT_DIR = EXTRACTOR_DIR.parent
sys.path.insert(0, str(PARENT_DIR))

from common import find_forbidden_keys, load_json, write_json  # noqa: E402
from extract_case import (  # noqa: E402
    DEFAULT_OUT_DIR, NORMAL_STOP_REASONS, TRUNCATED_REASONS, ExtractError, ModelUnavailableError, NAME_PATTERN,
    join_judgments, model_chain, run_with_fallback, split_model,
)
from llm import LLMError, api_key, call as llm_call, model_provider  # noqa: E402
from validate_output import FORBIDDEN_EXPRESSIONS, parse_output  # noqa: E402

COURT_PROMPT_VERSION = "court-v1"
DEFAULT_MODEL = "anthropic:claude-opus-5-5"
MAX_TOKENS = 16000
RETRIES = 2  # 형식 · 대조 오류가 있으면 오류 내용을 알려 주며 다시 요청하는 횟수
SUMMARY_MAX_LENGTH = 100  # judgment.summary varchar(100)
DISPOSITION_TYPES = ("COMMUNITY_SERVICE", "CONFISCATION")
MARK_OPEN, MARK_CLOSE = "⟦", "⟧"
MAX_REPLACED_CHARS = 40  # ⟦ ⟧ 하나가 대신할 수 있는 원문 길이 (식별 정보 한 덩어리). 최소 1자 — 0자 대체는 원문에 없는 말을 끼워 넣는 것이다
MAX_REPLACEMENT_LENGTH = 20  # ⟦ ⟧ 안에 쓸 수 있는 글자 수 (일반화한 말만, 문장을 끼워 넣지 못하게)
MIN_LITERAL_RATIO = 0.6  # 인용에서 원문 그대로인 글자의 최소 비율 (전부 ⟦ ⟧로 감싸 대조를 피하지 못하게)
MIN_LITERAL_RUN = 8  # 원문 그대로인 덩어리 중 가장 긴 것의 최소 길이

DRAFT_SCHEMA = _object({
    "summary": _STRING,
    "reasoning": _STRING,
    "plainExplanation": _STRING,
    "excerpt": _STRING,
    "extraDispositions": {"type": "array", "items": _object({
        "type": {"type": "string", "enum": list(DISPOSITION_TYPES)}, "value": _STRING})},
    "factors": {"type": "array", "items": _object({
        "factorId": {"type": "integer"}, "direction": {"type": "string", "enum": ["UP", "DOWN"]}, "evidence": _STRING})},
    "notes": {"type": "array", "items": _STRING},
})


class CourtDraftError(Exception):
    pass


# ---------------------------------------------------------------- 인용 대조

def _normalize(text):
    return re.sub(r"\s+", "", text)


def split_quote(quote):
    """인용 → [(원문 그대로 여부, 글자)]. ⟦ ⟧ 짝이 맞지 않으면 CourtDraftError."""
    parts, pos = [], 0
    for match in re.finditer(re.escape(MARK_OPEN) + r"(.*?)" + re.escape(MARK_CLOSE), quote):
        if match.start() > pos:
            parts.append((True, quote[pos:match.start()]))
        parts.append((False, match.group(1)))
        pos = match.end()
    if pos < len(quote):
        parts.append((True, quote[pos:]))
    if any(MARK_OPEN in text or MARK_CLOSE in text for literal, text in parts if literal):
        raise CourtDraftError("⟦ ⟧ 짝이 맞지 않습니다")
    return parts


def quote_matches(quote, source_text):
    """인용이 원문과 맞는지 (맞음 여부, 이유). ⟦ ⟧ 밖은 공백만 무시하고 글자 그대로 같아야 한다.

    ⟦ ⟧ 하나는 원문 1 ~ MAX_REPLACED_CHARS자(공백 제외)를 대신하고, 안의 글자는 MAX_REPLACEMENT_LENGTH자 이내다.
    정규식 와일드카드 대신 "다음 조각이 시작할 수 있는 위치 집합"을 앞에서부터 넓혀 가며 찾는다. 같은 단어가 많이
    반복되는 판결문에서도 위치 집합 크기 × 대체 길이에 비례해 끝난다 (정규식 역추적 폭발 없음).
    """
    try:
        parts = split_quote(quote)
    except CourtDraftError as e:
        return False, str(e)
    for is_literal, text in parts:
        if not is_literal and not 0 < len(_normalize(text)) <= MAX_REPLACEMENT_LENGTH:
            return False, f"⟦ ⟧ 안의 말은 1 ~ {MAX_REPLACEMENT_LENGTH}자여야 합니다 (식별 정보를 일반화한 말만)"
    literal = [_normalize(text) for is_literal, text in parts if is_literal]
    literal_chars = sum(len(text) for text in literal)
    total_chars = literal_chars + sum(len(_normalize(text)) for is_literal, text in parts if not is_literal)
    if not literal or literal_chars < MIN_LITERAL_RATIO * total_chars or max(len(t) for t in literal) < MIN_LITERAL_RUN:
        return False, "원문 그대로인 부분이 너무 적습니다 (⟦ ⟧로 바꾼 부분이 대부분)"
    if _locate(parts, _normalize(source_text)):
        return True, ""
    return False, "원문에서 찾을 수 없습니다 (⟦ ⟧ 밖의 글자가 원문과 다름)"


def _occurrences(text, piece):
    found, start = [], text.find(piece)
    while start != -1:
        found.append(start)
        start = text.find(piece, start + 1)
    return found


def _locate(parts, source):
    """공백을 뺀 원문(source)에서 조각들이 순서대로 놓일 수 있는지. starts는 다음 조각이 시작할 수 있는 위치 집합
    (None은 아무 데서나 시작 가능). ⟦ ⟧는 앞 조각 끝에서 1 ~ MAX_REPLACED_CHARS자를 건너뛴다."""
    starts = None
    for is_literal, text in parts:
        if is_literal:
            piece = _normalize(text)
            if not piece:
                continue
            ends = {s + len(piece) for s in _occurrences(source, piece) if starts is None or s in starts}
            if not ends:
                return False
            starts = ends
        else:
            if starts is None:  # 인용이 ⟦ ⟧로 시작하면 앞에 원문이 1자 이상 있는 아무 위치에서 시작할 수 있다
                starts = set(range(1, len(source) + 1))
            else:
                starts = {e + gap for e in starts for gap in range(1, MAX_REPLACED_CHARS + 1) if e + gap <= len(source)}
            if not starts:
                return False
    return True


def display_text(quote):
    """화면에 보일 인용: ⟦ ⟧ 표시만 지우고 바꾼 말은 남긴다."""
    return quote.replace(MARK_OPEN, "").replace(MARK_CLOSE, "")


# ---------------------------------------------------------------- 주문 대조

SENTENCE_PRISON = re.compile(r"징\s*역\s*(?:(\d+)\s*년)?\s*(?:(\d+)\s*(?:개월|월))?\s*에\s*처한다")
SENTENCE_FINE = re.compile(r"벌\s*금\s*([\d,]+)\s*원\s*에\s*처한다")
SENTENCE_DEATH = re.compile(r"사\s*형\s*에\s*처한다")
SENTENCE_LIFE = re.compile(r"무\s*기\s*징\s*역\s*에\s*처한다")
# "2년간 위 형의", "1년 6월간 각 형의" 등. 개월 단위 · "각"(여러 죄 · 여러 피고인)도 받는다
SENTENCE_SUSPENSION = re.compile(
    r"(\d+)\s*년\s*(?:(\d+)\s*(?:개월|월)\s*)?간\s*(?:위\s*|각\s*)?형\s*의\s*집\s*행\s*을\s*유\s*예")
COURT_LEVEL_RANK = {"FIRST": 0, "APPEAL": 1, "SUPREME": 2}


def parse_sentences(text):
    """판결문 주문에서 선고 형벌을 꺼낸다. [{penalty, prisonMonths, fineAmount, suspensionMonths}]"""
    suspension = SENTENCE_SUSPENSION.search(text)
    suspension_months = int(suspension.group(1)) * 12 + int(suspension.group(2) or 0) if suspension else None
    found = []
    for m in SENTENCE_PRISON.finditer(text):
        if not (m.group(1) or m.group(2)):
            continue
        months = int(m.group(1) or 0) * 12 + int(m.group(2) or 0)
        found.append({"penalty": "PRISON", "prisonMonths": months, "fineAmount": None, "suspensionMonths": suspension_months})
    for m in SENTENCE_FINE.finditer(text):
        found.append({"penalty": "FINE", "prisonMonths": None, "fineAmount": int(m.group(1).replace(",", "")),
                      "suspensionMonths": suspension_months})
    if SENTENCE_LIFE.search(text):
        found.append({"penalty": "LIFE", "prisonMonths": None, "fineAmount": None, "suspensionMonths": None})
    elif SENTENCE_DEATH.search(text):
        found.append({"penalty": "DEATH", "prisonMonths": None, "fineAmount": None, "suspensionMonths": None})
    return found


def effective_sentences(texts, levels=None, final_index=None):
    """실제로 확정된 선고 형벌: 최종 판결문의 주문. 최종 판결문 주문에 형이 없으면(상소 기각) 그 아래 심급으로 내려간다.

    levels: 판결문마다 심급(FIRST · APPEAL · SUPREME). final_index: 최종 판결 번호(0부터). 없으면 심급이 가장 높은
    판결(같으면 뒤의 것) — case_seed_sql.resolve_sources와 같은 규칙. 하급심 형량을 뽑은 추출 오류를 잡으려고
    모든 판결문이 아니라 확정된 형을 정한 판결문 하나와만 대조한다.
    """
    if not texts:
        return []
    levels = list(levels or [None] * len(texts))
    ranks = [COURT_LEVEL_RANK.get(level, -1) for level in levels]
    if final_index is None or not 0 <= final_index < len(texts):
        final_index = max(range(len(texts)), key=lambda i: (ranks[i], i))
    lower = sorted((i for i in range(len(texts)) if i != final_index and ranks[i] <= ranks[final_index]),
                   key=lambda i: (ranks[i], i), reverse=True)
    for i in [final_index] + lower:
        parsed = parse_sentences(texts[i])
        if parsed:
            return parsed
    return []


def check_sentence(court, texts, levels=None, final_index=None):
    """추출기 형벌 · 형량(court)이 확정된 선고(최종 판결문 주문)와 맞는지. (오류, 경고). 메시지에 형량 값은 적지 않는다."""
    final = court.get("reducedTo") or court.get("penaltyType")
    parsed = effective_sentences(texts, levels, final_index)
    if not parsed:
        return [], ["판결문 주문에서 형벌을 찾지 못해 형량 교차 확인을 하지 못했습니다 (원문과 직접 대조하세요)"]
    for s in parsed:
        if (s["penalty"] == final and s["prisonMonths"] == court.get("prisonMonths")
                and s["fineAmount"] == court.get("fineAmount") and s["suspensionMonths"] == court.get("suspensionMonths")):
            return [], []
    return ["추출기가 뽑은 형벌 · 형량이 최종 판결문 주문과 맞지 않습니다 (하급심 형량을 뽑았는지 court_judgment_internal.json을 원문과 대조하세요)"], []


# ---------------------------------------------------------------- 초안 검사

def check_draft(draft, case, source_text):
    """모델 응답(초안)을 검사한다. (오류, 경고)."""
    errors = validate_against_schema(draft, DRAFT_SCHEMA)
    if errors:
        return errors, []
    warnings = []
    labels = {int(f["factorId"]): f["label"] for f in case.get("factors", [])}

    ids = [f["factorId"] for f in draft["factors"]]
    if not ids:
        errors.append("재판부가 고려한 판단 요소가 없습니다")
    for fid in ids:
        if fid not in labels:
            errors.append(f"factors: 목록에 없는 판단 요소 번호 {fid}")
    if len(ids) != len(set(ids)):
        errors.append("factors: 같은 판단 요소가 두 번 있습니다")

    quotes = [("excerpt", draft["excerpt"])] + [(f"factors[{f['factorId']}].evidence", f["evidence"]) for f in draft["factors"]]
    for where, quote in quotes:
        if not quote.strip():
            errors.append(f"{where}: 비었습니다")
            continue
        ok, reason = quote_matches(quote, source_text)
        if not ok:
            errors.append(f"{where}: 원문 인용이 아닙니다 — {reason}")

    if len(draft["summary"]) > SUMMARY_MAX_LENGTH:
        errors.append(f"summary가 {SUMMARY_MAX_LENGTH}자를 넘습니다")
    texts = [("summary", draft["summary"]), ("reasoning", draft["reasoning"]),
             ("plainExplanation", draft["plainExplanation"]), ("excerpt", display_text(draft["excerpt"]))]
    texts += [(f"factors[{f['factorId']}].evidence", display_text(f["evidence"])) for f in draft["factors"]]
    texts += [(f"extraDispositions[{i}]", d["value"]) for i, d in enumerate(draft["extraDispositions"])]
    for where, text in texts:
        for word in FORBIDDEN_EXPRESSIONS:
            if word in text and where != "excerpt" and not where.endswith(".evidence"):
                errors.append(f"{where}: 평가 표현 '{word}'")
    pii_errors, pii_warnings = residual_check(texts)
    errors += pii_errors
    warnings += pii_warnings
    note_errors, _ = residual_check([(f"notes[{i}]", n) for i, n in enumerate(draft["notes"])])
    errors += note_errors
    return errors, warnings


def build_court_draft(draft, case, court, model):
    """검사를 통과한 응답 → BE-14 형식 초안. 형벌 · 형량은 추출기 값(주문과 교차 확인한 값)을 쓴다."""
    labels = {int(f["factorId"]): f["label"] for f in case.get("factors", [])}
    chosen = {f["factorId"] for f in draft["factors"]}
    return {
        "_comment": "재판부 판결 초안 (BE-38, 자동 생성). 내부 전용 — AI 판결 입력에 넣지 않는다. 원 판결문과 대조해 "
                    "검수한 뒤 공개한다. factorId는 factor.display_order와 같은 값이다.",
        "caseTitle": case.get("title"),
        "draft": {"model": model, "promptVersion": COURT_PROMPT_VERSION,
                  "createdAt": datetime.datetime.now(datetime.timezone.utc).astimezone().isoformat(timespec="seconds")},
        "judgment": {
            "subjectType": "COURT",
            "timing": "FINAL",
            "penaltyType": court["penaltyType"],
            "reducedTo": court.get("reducedTo"),
            "prisonMonths": court.get("prisonMonths"),
            "fineAmount": court.get("fineAmount"),
            "suspensionMonths": court.get("suspensionMonths"),
            "extraDispositions": draft["extraDispositions"],
            "summary": draft["summary"],
            "reasoning": draft["reasoning"],
            "plainExplanation": draft["plainExplanation"],
            "excerpt": display_text(draft["excerpt"]),
            "isPublished": False,
        },
        "judgmentFactors": [
            {"factorId": f["factorId"], "label": labels[f["factorId"]], "direction": f["direction"],
             "evidence": display_text(f["evidence"])}
            for f in sorted(draft["factors"], key=lambda f: f["factorId"])
        ],
        "excludedFactors": {
            "_comment": "재판부가 고려하지 않은 요소. judgment_factor 행을 만들지 않는다",
            "factorIds": sorted(fid for fid in labels if fid not in chosen),
        },
    }


# ---------------------------------------------------------------- 호출

def build_messages(case, court, masked_text):
    system = (EXTRACTOR_DIR / "prompts" / "court_draft_system.md").read_text(encoding="utf-8")
    factors = "\n".join(f"- {f['factorId']}. {f['label']}" for f in case["factors"])
    final = court.get("reducedTo") or court.get("penaltyType")
    user = (f"# 판단 요소 목록 (이 목록에서만 고른다)\n\n{factors}\n\n"
            f"# 재판부가 최종 선고한 형벌 종류\n\n{final} (형량은 판결문 주문을 따른다)\n\n"
            f"# 판결문\n\n<judgment>\n{masked_text}\n</judgment>")
    return system, user


def request_draft(system, user, case, masked_text, model, max_tokens=MAX_TOKENS, caller=llm_call):
    """모델에 초안을 요청하고 검사한다. 오류가 있으면 내용을 알려 주며 다시 요청한다. (초안, 경고, 실제 모델, 시도 횟수)"""
    last_errors = []
    for attempt in range(1 + RETRIES):
        message = user if not last_errors else (
            user + "\n\n(이전 응답의 오류: " + "; ".join(last_errors[:8]) + ". 인용은 원문을 글자 그대로 옮기고 바꾼 부분만 ⟦ ⟧로 "
            "감싼다. 고쳐서 다시 출력하세요)")
        try:
            result = caller(model, system, message, max_tokens=max_tokens, json_output=True)
        except LLMError as e:
            if e.kind:  # 과부하 · 한도: 다음 모델로 넘어갈 수 있다 (BE-45)
                raise ModelUnavailableError(str(e), e.kind) from e
            raise CourtDraftError(str(e)) from e
        if result.stop_reason in TRUNCATED_REASONS:
            raise CourtDraftError(f"출력이 최대 길이({max_tokens} 토큰)에서 잘렸습니다")
        if result.stop_reason not in NORMAL_STOP_REASONS:
            raise CourtDraftError(f"응답이 비정상 종료되었습니다 (사유: {result.stop_reason}). 다시 보내도 같으므로 멈춥니다")
        try:
            draft = parse_output(result.text)
        except (json.JSONDecodeError, ValueError) as e:
            last_errors = [f"JSON으로 읽을 수 없음: {e}"]
            continue
        errors, warnings = check_draft(draft, case, masked_text)
        if not errors:
            return draft, warnings, result.served_model, attempt + 1
        last_errors = errors
    raise CourtDraftError(f"{1 + RETRIES}번 모두 검사를 통과하지 못했습니다: " + " | ".join(last_errors[:8]))


def run(name, out_dir=DEFAULT_OUT_DIR, model=DEFAULT_MODEL, max_tokens=MAX_TOKENS, caller=None,
        case_path=None, court_path=None, source_path=None, final_index=None, log=print):
    """초안 생성 전체 흐름. (초안 파일, 보고서 파일). 실패하면 CourtDraftError (보고서는 남김).

    입력 파일은 기본으로 out_dir/<name>.case.json 등을 쓰고, 경로를 주면 그 파일을 쓴다(파이프라인 inputs).
    model은 문자열 또는 모델 목록이다. 목록이면 앞 모델의 호출이 과부하 · 한도로 막혔을 때 다음 모델이 처음부터
    다시 요청한다(재요청 횟수도 새로 센다) (BE-45).
    """
    if not NAME_PATTERN.match(name):
        raise CourtDraftError("--name은 영어 소문자 · 숫자 · 하이픈만 씁니다")
    out_dir = Path(out_dir)
    try:
        models = model_chain(model)
        for m in models:
            split_model(m)  # 모델 지정 검증 (manual · 알 수 없는 공급자 거부)
    except ExtractError as e:
        raise CourtDraftError(str(e)) from e

    def spec_of(m):
        return f"{model_provider(m)}:{split_model(m)[1]}"  # 공급자 규칙은 llm.model_provider 하나에서 정한다
    case = load_json(case_path or out_dir / f"{name}.case.json")
    court = load_json(court_path or out_dir / f"{name}.court_judgment_internal.json")
    source = load_json(source_path or out_dir / f"{name}.source_internal.json")
    if find_forbidden_keys(case):
        raise CourtDraftError("case.json에 실제 판결 등 금지 항목이 있습니다")
    # 원문이 있는 판결문만 쓴다. 마스킹 텍스트 · 주문 대조 · 최종 판결 번호가 모두 같은 목록(같은 순서)을 기준으로 하도록
    # 원래 번호를 함께 들고 다니고, 설정의 finalSourceIndex(전체 sources 기준)를 이 목록 기준으로 바꾼다
    indexed = [(i, s) for i, s in enumerate(source.get("sources", [])) if s.get("originalText")]
    if not indexed:
        raise CourtDraftError("source_internal.json에 판결문 원문이 없습니다")
    originals = [s["originalText"] for _, s in indexed]
    levels = [s.get("courtLevel") for _, s in indexed]
    positions = [i for i, _ in indexed]
    if final_index is not None:
        final_index = positions.index(final_index) if final_index in positions else None
    masked_text, _ = premask(join_judgments([(Path(s.get("file", "")), s["originalText"]) for _, s in indexed]))

    report_path = out_dir / f"{name}.court_report.json"
    draft_path = out_dir / f"{name}.court_draft.json"
    sentence_errors, sentence_warnings = check_sentence(court, originals, levels, final_index)
    report = {"promptVersion": COURT_PROMPT_VERSION, "requestedModel": models[0],
              "errors": list(sentence_errors),
              "warnings": list(sentence_warnings)}
    if sentence_errors:
        draft_path.unlink(missing_ok=True)
        report["status"] = "ERROR"
        write_json(report_path, report)
        raise CourtDraftError("형량 교차 확인 실패 — 초안을 만들지 않았습니다. 보고서: " + str(report_path))

    if caller is None:
        for m in models:  # 대체 모델의 키도 호출 전에 확인한다
            try:
                api_key(model_provider(m))
            except LLMError as e:
                raise CourtDraftError(str(e)) from e
        caller = llm_call
    system, user = build_messages(case, court, masked_text)
    try:
        (draft, warnings, served, attempts), used_model, skipped = run_with_fallback(
            models, lambda m: request_draft(system, user, case, masked_text, spec_of(m), max_tokens, caller), log)
    except (CourtDraftError, ModelUnavailableError) as e:
        draft_path.unlink(missing_ok=True)
        report.update(status="ERROR", errors=report["errors"] + [scrub(str(e))])
        if len(models) > 1:
            report.update(modelChain=models, fallbacks=[{**f, "error": scrub(f["error"])} for f in getattr(e, "skipped", [])])
        write_json(report_path, report)
        if isinstance(e, ModelUnavailableError):
            raise CourtDraftError(str(e)) from e
        raise
    result = build_court_draft(draft, case, court, served)
    if len(models) > 1:
        report.update(requestedModel=used_model, modelChain=models,
                      fallbacks=[{**f, "error": scrub(f["error"])} for f in skipped])
    report.update(status="NEEDS_REVIEW", model=served, attempts=attempts, warnings=report["warnings"] + warnings,
                  notes=[scrub(n) for n in draft["notes"]],
                  factorsChosen=len(result["judgmentFactors"]), factorsExcluded=len(result["excludedFactors"]["factorIds"]))
    write_json(draft_path, result)
    write_json(report_path, report)
    return draft_path, report_path


def main():
    parser = argparse.ArgumentParser(description="재판부 판결(COURT) 초안을 만들고 원문과 대조한다 (BE-38)")
    parser.add_argument("--name", required=True, help="case-extractor에서 쓴 이름")
    parser.add_argument("--out-dir", default=str(DEFAULT_OUT_DIR))
    parser.add_argument("--model", action="append",
                        help=f"'공급자:모델ID' (openai · gemini · anthropic, 기본: {DEFAULT_MODEL}). 여러 번 주면 앞 모델이 "
                             "과부하 · 한도로 막혔을 때 다음 모델로 넘어간다")
    parser.add_argument("--max-tokens", type=int, default=MAX_TOKENS)
    args = parser.parse_args()
    try:
        model = args.model[0] if args.model and len(args.model) == 1 else (args.model or DEFAULT_MODEL)
        paths = run(args.name, args.out_dir, model, args.max_tokens)
    except (CourtDraftError, OSError, json.JSONDecodeError) as e:
        print(f"[오류] {e}", file=sys.stderr)
        return 1
    for path in paths:
        print(path)
    print("자동 생성 초안입니다. 원 판결문과 대조해 검수한 뒤 공개하세요 (REQ-075 · 077).", file=sys.stderr)
    return 0


if __name__ == "__main__":
    sys.exit(main())
