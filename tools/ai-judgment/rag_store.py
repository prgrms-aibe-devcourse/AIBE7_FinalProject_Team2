"""AI 판결 RAG 판례 저장소 적재 도구 (BE-51). 결정: docs/ai-judgment-pipeline.md 8-1, 수집: docs/rag-precedent-sources.md

판례 후보(비공개 저장소 work/BE-50-precedent-candidates.json)와 원문(raw/BE-50/)을 받아 세 단계로 rag_precedent 시드를 만든다.

    python3 rag_store.py build --candidates <후보.json> --raw <raw/BE-50> --model <공급자:모델ID>
    python3 rag_store.py embed
    python3 rag_store.py sql --candidates <후보.json> --output ../../backend/private-seed/seed/R__40_rag_precedent.sql

- build: 판결문을 로컬에서 마스킹(premask)한 뒤 모델에 보내 비식별화한 사실관계 요약(검색 기준) · 양형 이유(프롬프트 본문) ·
  법조문 · 주문 형량 · 양형인자로 나눈다. 결과는 남은 개인정보 검사(residual_check)를 통과해야 저장한다.
  마스킹한 판결문 원문을 보내므로 무료 등급이 있는 모델은 --allow-free-tier를 붙여야 쓴다(README "판결문 단계의 무료 등급 백업"과
  같은 위험, 공개 판결문에만). 사건번호 · 법원명 · 선고일은 보내지 않는다
- embed: 비식별화한 사실관계 요약(factsSummary)**만** gemini-embedding-001(RETRIEVAL_DOCUMENT, 3072차원)로 임베딩한다.
  무료 등급이어도 된다(8-1: 공개 판결문을 비식별화한 텍스트만 넣는다)
- sql: Flyway 반복 마이그레이션(R__40)을 만든다. rag_precedent를 통째로 지우고 다시 넣는다. 비공개 저장소 체크아웃 안에만 쓴다
- 중간 결과는 out/rag/(git 제외)에 판례마다 파일로 둔다. 이미 끝난 판례는 다시 부르지 않으므로 끊겨도 이어서 돈다
- 사건 그룹 ID는 그룹에서 가장 앞선 판례 키(예: LAW_GO_KR-143304)다. 후보 목록을 다시 만들어도 같은 그룹이면 같은 ID가 나온다
"""

import argparse
import hashlib
import json
import math
import re
import sys
import time
from pathlib import Path

import llm
from common import load_json, read_prompt, write_json
from validate_output import parse_output

TOOL_DIR = Path(__file__).resolve().parent
sys.path.insert(0, str(TOOL_DIR / "case-extractor"))
from deidentify import premask, residual_check  # noqa: E402 (case-extractor의 표준 라이브러리 모듈)

OUT_DIR = TOOL_DIR / "out" / "rag"
SOURCES = {"law.go.kr": "LAW_GO_KR", "LBox": "LBOX"}
COURT_LEVELS = {"1심": "FIRST", "항소심": "APPEAL"}
CHARGE_NAMES = {"살인": "살인", "미수": "살인미수", "존속": "존속살해", "강도살인": "강도살인", "촉탁 · 승낙": "촉탁 · 승낙 살인"}
PENALTY_TYPES = ("DEATH", "LIFE", "PRISON", "FINE")
GUIDELINE_CATEGORY = "살인범죄"
EMBEDDING_MODEL = "gemini-embedding-001"
EMBEDDING_DIM = 3072
TEXT_LIMITS = {"factsSummary": 2000, "sentencingReason": 4000}
# 출처별 사용 보류 (on_hold). 행은 넣되 검색에서 뺀다. 확인되면 여기서 지우고 sql을 다시 만든다
SOURCE_HOLDS = {
    "LBOX": "LBox Open 라이선스 확인 전 (데이터셋 카드 CC BY-NC 4.0 · 논문 CC BY-NC-ND 4.0, 2026-10-12 팀 결정)",
}
# 판례별 사용 보류. 살인 관련 공소사실이 무죄이고 다른 죄만 유죄인 판결은 형량 · 양형 이유가 살인 기준이 아니다.
# 사실관계 요약은 살인 사건과 비슷해 유사도로 걸러지지 않으므로(상위 5건에 듦) 넣되 검색에서 뺀다 (팀 결정 2026-10-12)
ACQUITTED_MURDER = "살인 관련 공소사실 무죄 (다른 죄만 유죄, 형량 · 양형 이유가 살인 기준이 아님)"
PRECEDENT_HOLDS = {
    "LAW_GO_KR-161166": ACQUITTED_MURDER,  # 사체은닉
    "LAW_GO_KR-173650": ACQUITTED_MURDER,  # 유기치사 (예비적 공소사실)
    "LAW_GO_KR-184805": ACQUITTED_MURDER,  # 재물손괴
    "LAW_GO_KR-190236": ACQUITTED_MURDER,  # 협박
    "LAW_GO_KR-206273": ACQUITTED_MURDER,  # 특수상해
    "LAW_GO_KR-623093": ACQUITTED_MURDER,  # 협박
}
MURDER_CHARGE = re.compile(r"살인|살해")  # 유죄 죄명이 이 계열이 아니면 flags.checks에 확인 필요 (예: 유기치사)
# 요약 임베딩 코사인 유사도가 이 이상이면 같은 사건으로 묶는다. 판례 38건 분포(2026-10-12): 같은 사건(1심 · 항소심,
# 출처만 다른 같은 판결) 0.97 ~ 0.98, 다른 사건 최고 0.93, 전체 중앙값 0.82. 판례가 늘면 다시 본다
GROUP_MERGE_SCORE = 0.95


class RagStoreError(Exception):
    pass


# ---------- 후보 → 적재 대상 ----------

def precedent_key(source, ref):
    return f"{source}-{ref}"


# 사건번호 하나 (예: 2014고13, 2008재고합1, 2016 고합 79). 병합 표기 "2014고13, 2014고14(병합)"에서 하나씩 뽑는다
CASE_NUMBER_TOKEN = re.compile(r"(?:19|20)\d{2}\s*[가-힣]{1,3}\s*\d+")


def case_number_hash(number):
    """사건번호 하나(공백 제거)의 SHA-256 hex. 원문 사건번호는 저장하지 않는다 (자기 사건 제외 검색에만 쓴다)."""
    return hashlib.sha256(re.sub(r"\s", "", number).encode("utf-8")).hexdigest() if number else None


def case_number_hashes(number):
    """사건번호 칸 → 사건번호마다의 해시 목록 (정렬 · 중복 제거). 형식에 맞는 번호가 없으면 칸 전체를 하나로 해시한다.

    병합 사건은 law.go.kr 칸에 "2014고13, 2014고14(병합)"처럼 여러 번호가 오지만, 대상 사건의 case_source에는 번호가
    하나만 들어갈 수 있다. 칸 전체를 해시하면 두 쪽이 달라 자기 사건 제외가 조용히 실패하므로 번호마다 해시한다.
    백엔드 CaseNumberHash.ofAll과 같은 규칙이어야 한다 (CaseNumberHashTest가 이 함수의 값과 비교).
    """
    if not number or not number.strip():
        return []
    tokens = {re.sub(r"\s", "", t) for t in CASE_NUMBER_TOKEN.findall(number)} or {re.sub(r"\s", "", number)}
    return sorted(case_number_hash(t) for t in tokens)


def targets(candidates):
    """BE-50 후보 목록 → 적재 대상 (조건 통과 · 중복 아님). 팀 결정(2026-10-10)으로 사람 확인 없이 전부 넣는다."""
    rows = [c for c in candidates if c["passed"] and not c["duplicateOf"]]
    groups = {}
    for row in rows:
        row["key"] = precedent_key(SOURCES[row["source"]], row["sourceId"])
        groups.setdefault(row["caseGroup"], []).append(row["key"])
    result = []
    for row in rows:
        level = COURT_LEVELS.get(row["level"])
        if level is None:
            raise RagStoreError(f"{row['key']}: 심급을 알 수 없습니다 ({row['level']}). 후보 목록의 level을 고친다")
        decided = row.get("decidedAt")
        result.append({
            "key": row["key"],
            "source": SOURCES[row["source"]],
            "sourceRef": row["sourceId"],
            "caseNumberHashes": case_number_hashes(row.get("caseNumber")),
            "caseGroup": min(groups[row["caseGroup"]]),
            "crimeType": "MURDER",
            "chargeName": CHARGE_NAMES.get(row["type"], row["type"]),
            "courtLevel": level,
            "decidedAt": f"{decided[:4]}-{decided[4:6]}-{decided[6:8]}" if decided else None,
            "incidentDate": row.get("incidentDate"),
            "guideline": row.get("guideline"),
            "flags": {
                "verified": False,
                "checks": row.get("checks") or [],
                "guidelineByIncidentDate": row.get("guidelineByIncidentDate"),
                "partlyAcquitted": row["partlyAcquitted"],
                "concurrent": row["concurrent"],
                "beforePenaltyRevision": row.get("beforePenaltyRevision"),
            },
        })
    return result


def read_text(raw_dir, target):
    """원문 본문. law.go.kr는 판례내용(판시사항 · 판결요지 · 참조조문은 대법원 저작권이라 쓰지 않는다), LBox는 본문 전체."""
    raw_dir = Path(raw_dir)
    if target["source"] == "LAW_GO_KR":
        body = load_json(raw_dir / "law" / f"{target['sourceRef']}.json")["PrecService"]["판례내용"]
        return re.sub(r"<[^>]+>", "\n", body.replace("<br/>", "\n"))
    return (raw_dir / "lbox" / f"{target['sourceRef']}.txt").read_text(encoding="utf-8")


# ---------- build: 비식별화 · 분리 ----------

def build_prompt(text):
    """(system, user). 판결문은 로컬 마스킹 후 <judgment> 태그로 감싼다."""
    masked, _ = premask(text)
    user = "\n".join(["<judgment>", masked.strip(), "</judgment>", "",
                      "위 판결문을 시스템 지시에 따라 유사 판례 자료 JSON 하나로 정리한다."])
    return read_prompt("rag_precedent_system.md"), user


def _months(value, name, errors):
    if value is not None and (not isinstance(value, int) or isinstance(value, bool) or value <= 0):
        errors.append(f"sentence.{name}는 null 또는 양의 정수다")


def validate_result(output):
    """모델 응답 형식 검사. 오류 목록 (비어 있으면 통과)."""
    if not isinstance(output, dict):
        return ["응답이 JSON 객체가 아닙니다"]
    errors = []
    for name, limit in TEXT_LIMITS.items():
        value = output.get(name)
        if not isinstance(value, str) or not value.strip():
            errors.append(f"{name}가 비어 있습니다")
        elif len(value) > limit:
            errors.append(f"{name}가 {limit}자를 넘습니다 ({len(value)}자)")
    charge = output.get("chargeName")
    if charge is not None and (not isinstance(charge, str) or not charge.strip() or len(charge) > 100):
        errors.append("chargeName은 100자 이하 문자열이다")
    statutes = output.get("statutes")
    if not isinstance(statutes, list) or not all(isinstance(s, str) for s in statutes):
        errors.append("statutes는 문자열 목록이다")
    sentence = output.get("sentence")
    if sentence is not None:
        if not isinstance(sentence, dict) or sentence.get("penaltyType") not in PENALTY_TYPES:
            errors.append(f"sentence.penaltyType은 {' · '.join(PENALTY_TYPES)} 중 하나다")
        else:
            _months(sentence.get("prisonMonths"), "prisonMonths", errors)
            _months(sentence.get("suspensionMonths"), "suspensionMonths", errors)
            fine = sentence.get("fineAmount")
            if fine is not None and (not isinstance(fine, int) or isinstance(fine, bool) or fine <= 0):
                errors.append("sentence.fineAmount는 null 또는 양의 정수다")
            if sentence["penaltyType"] == "PRISON" and sentence.get("prisonMonths") is None:
                errors.append("징역인데 prisonMonths가 없습니다")
    factors = output.get("sentencingFactors")
    if not isinstance(factors, dict) or not all(
            isinstance(factors.get(k), list) and all(isinstance(v, str) for v in factors[k])
            for k in ("aggravating", "mitigating")):
        errors.append("sentencingFactors는 aggravating · mitigating 문자열 목록을 가진 객체다")
    return errors


def privacy_texts(output):
    """남은 개인정보 검사 대상: 저장 · 임베딩 · 프롬프트에 들어가는 모든 자유 텍스트."""
    texts = [("factsSummary", output.get("factsSummary")), ("sentencingReason", output.get("sentencingReason"))]
    factors = output.get("sentencingFactors") or {}
    for kind in ("aggravating", "mitigating"):
        texts += [(f"sentencingFactors.{kind}", v) for v in factors.get(kind) or []]
    texts += [("statutes", v) for v in output.get("statutes") or []]
    texts.append(("chargeName", output.get("chargeName")))
    return texts


def build_one(target, text, model, *, caller=llm.call, max_retries=2, max_tokens=8000, timeout=300):
    """판례 하나 → 비식별화 결과. 형식 오류 · 남은 개인정보가 있으면 다시 부르고, 끝까지 남으면 RagStoreError."""
    system, user = build_prompt(text)
    problems = []
    for attempt in range(max_retries + 1):
        prompt = user if not problems else (
            user + "\n\n앞 응답의 문제: " + " · ".join(problems) + ". 고쳐서 JSON 하나로 다시 답한다.")
        result = caller(model, system, prompt, max_tokens=max_tokens, timeout=timeout)
        try:
            output = parse_output(result.text)
        except ValueError as e:
            problems = [f"JSON을 읽을 수 없음 ({e})"]
            continue
        problems = validate_result(output)
        if not problems:
            errors, warnings = residual_check(privacy_texts(output))
            problems = errors  # 위치 · 규칙 이름만 담겨 원래 값이 남지 않는다
            if not problems:
                return {**target, "result": output, "model": result.model, "servedModel": result.served_model,
                        "privacyWarnings": warnings, "attempts": attempt + 1}
    raise RagStoreError(f"{target['key']}: {' · '.join(problems)}")


def run_build(args, caller=llm.call):
    if args.model.strip().lower().startswith("free:"):
        raise RagStoreError("build는 free: 자동 선택을 쓰지 않는다. 모델을 '공급자:모델ID'로 정한다")
    free_tier = llm.has_free_tier(args.model)
    if free_tier and not args.allow_free_tier:
        raise RagStoreError(f"{args.model}은 무료 등급이 있는 모델이다. build는 마스킹한 판결문 원문을 보내므로 "
                            "결제를 연결한 키가 아니면 입력이 제품 개선에 쓰일 수 있다. 팀이 받아들였으면 --allow-free-tier를 붙인다 "
                            "(README \"판결문 단계의 무료 등급 백업\"과 같은 위험)")
    if free_tier:
        print("⚠️  무료 등급 허용: 마스킹한 공개 판결문 원문이 무료 등급으로 전송될 수 있다 (입력이 제품 개선에 쓰이고 "
              "사람이 검토할 수 있음). 공개 판결문에만 쓴다", file=sys.stderr)
    items = targets(load_json(args.candidates))
    if args.limit:
        items = items[:args.limit]
    out = Path(args.out) / "precedents"
    failed = []
    for n, target in enumerate(items, start=1):
        path = out / f"{target['key']}.json"
        if path.exists():
            continue
        try:
            built = build_one(target, read_text(args.raw, target), args.model, caller=caller,
                              max_retries=args.max_retries, max_tokens=args.max_tokens)
            if free_tier:
                built["usedFreeTier"] = True  # 무료 등급이 있는 모델로 만들었음 (키가 실제로 무료인지는 알 수 없다)
            write_json(path, built)
            discard_embedding(Path(args.out), target["key"])
            print(f"[{n}/{len(items)}] {target['key']} 완료", file=sys.stderr)
        except (RagStoreError, llm.LLMError) as e:
            failed.append(str(e))
            print(f"[{n}/{len(items)}] 실패: {e}", file=sys.stderr)
            if isinstance(e, llm.LLMQuotaExhaustedError):
                break
        if args.delay:
            time.sleep(args.delay)
    done = len(list(out.glob("*.json"))) if out.exists() else 0
    print(f"비식별화 완료 {done}건 / 대상 {len(items)}건, 이번 실패 {len(failed)}건", file=sys.stderr)
    return failed


# ---------- manual: 대화형 세션(채팅 · 코딩 에이전트)이 직접 정리할 때 ----------

MANUAL_MODEL = "manual:claude-session"


def run_prepare(args):
    """남은 판례의 마스킹한 판결문을 manual/inputs/<key>.txt로 꺼낸다. 세션은 이 파일만 읽는다 (원문 · 메타데이터는 주지 않는다)."""
    out = Path(args.out)
    items = [t for t in targets(load_json(args.candidates)) if not (out / "precedents" / f"{t['key']}.json").exists()]
    for target in items:
        _, user = build_prompt(read_text(args.raw, target))
        path = out / "manual" / "inputs" / f"{target['key']}.txt"
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(user, encoding="utf-8")
    sizes = {t["key"]: (out / "manual" / "inputs" / f"{t['key']}.txt").stat().st_size for t in items}
    write_json(out / "manual" / "pending.json", sizes)
    print(f"남은 판례 {len(items)}건 → {out / 'manual' / 'inputs'}", file=sys.stderr)


def check_result(output):
    """build와 같은 검사 (형식 + 남은 개인정보). (오류 목록, 경고 목록)."""
    problems = validate_result(output)
    if problems:
        return problems, []
    return residual_check(privacy_texts(output))


def run_import(args):
    """manual/outputs/<key>.json(모델 응답 JSON 하나)을 검사해 precedents/에 넣는다. 실패한 키 목록을 돌려준다."""
    out = Path(args.out)
    by_key = {t["key"]: t for t in targets(load_json(args.candidates))}
    keys = args.keys.split(",") if args.keys else sorted(p.stem for p in (out / "manual" / "outputs").glob("*.json"))
    failed = []
    for key in keys:
        source = out / "manual" / "outputs" / f"{key}.json"
        if key not in by_key:
            failed.append(key)
            print(f"{key}: 후보 목록에 없는 판례", file=sys.stderr)
            continue
        try:
            output = parse_output(source.read_text(encoding="utf-8"))
        except (OSError, ValueError) as e:
            failed.append(key)
            print(f"{key}: 읽을 수 없음 ({e})", file=sys.stderr)
            continue
        errors, warnings = check_result(output)
        if errors:
            failed.append(key)
            print(f"{key}: {' · '.join(errors)}", file=sys.stderr)
            continue
        write_json(out / "precedents" / f"{key}.json",
                   {**by_key[key], "result": output, "model": args.model, "servedModel": None,
                    "privacyWarnings": warnings, "attempts": 1})
        discard_embedding(out, key)
        print(f"{key}: 통과" + (f" (경고 {len(warnings)}건)" if warnings else ""), file=sys.stderr)
    return failed


def discard_embedding(out, key):
    """요약을 새로 썼으면 예전 임베딩을 지운다. 남겨 두면 embed가 건너뛰어 새 요약과 옛 벡터가 한 행에 들어간다."""
    path = Path(out) / "embeddings" / f"{key}.json"
    if path.exists():
        path.unlink()


# ---------- embed ----------

def embedding_input(precedent):
    """임베딩에 넣는 텍스트. 비식별화한 사실관계 요약 하나뿐이다 (원문 · 사건번호 · 메타데이터는 넣지 않는다)."""
    return precedent["result"]["factsSummary"]


def run_embed(args, http=None):
    out = Path(args.out)
    pending = []
    for path in sorted((out / "precedents").glob("*.json")):
        if not (out / "embeddings" / path.name).exists():
            pending.append(load_json(path))
    for start in range(0, len(pending), llm.EMBEDDING_BATCH):
        batch = pending[start:start + llm.EMBEDDING_BATCH]
        vectors = llm.embed([embedding_input(p) for p in batch], model=args.model, dimensions=EMBEDDING_DIM, http=http)
        for precedent, vector in zip(batch, vectors):
            write_json(out / "embeddings" / f"{precedent['key']}.json",
                       {"model": args.model, "dim": len(vector), "values": vector})
        if args.delay and start + llm.EMBEDDING_BATCH < len(pending):
            time.sleep(args.delay)
    print(f"임베딩 {len(pending)}건 새로 만듦", file=sys.stderr)


# ---------- sql ----------

def sql_text(value):
    return "NULL" if value is None else "'" + str(value).replace("'", "''") + "'"


def sql_int(value):
    return "NULL" if value is None else str(int(value))


def sql_text_array(values):
    return "NULL" if not values else "ARRAY[" + ", ".join(sql_text(v) for v in values) + "]::text[]"


def number_hashes(precedent):
    """사건번호 해시 목록. 예전 형식(caseNumberHash 하나)으로 저장된 판례도 받는다 (--candidates로 다시 맞추면 새 형식)."""
    if "caseNumberHashes" in precedent:
        return precedent["caseNumberHashes"]
    return [precedent["caseNumberHash"]] if precedent.get("caseNumberHash") else []


def sql_jsonb(value):
    return "NULL" if value is None else sql_text(json.dumps(value, ensure_ascii=False)) + "::jsonb"


def sql_vector(values):
    return "'[" + ",".join(repr(float(v)) for v in values) + "]'::vector"


def guideline_sql(name):
    """양형기준 버전 이름 → sentencing_guideline id 조회. 시행 전 · DB에 없는 버전이면 NULL."""
    if not name or name.startswith("없음") or "DB 미등록" in name:
        return "NULL"
    return (f"(SELECT id FROM sentencing_guideline WHERE crime_category = {sql_text(GUIDELINE_CATEGORY)} "
            f"AND version_name = {sql_text(name)} ORDER BY id LIMIT 1)")


COLUMNS = ("source, source_ref, case_number_hashes, case_group, crime_type, charge_name, statutes, court_level, "
           "decided_at, incident_date, guideline_id, penalty_type, prison_months, suspension_months, fine_amount, "
           "recommended_range, sentencing_factors, facts_summary, sentencing_reason, embedding, embedding_model, "
           "embedding_dim, on_hold, hold_reason, flags")


def hold_reason(precedent):
    """사용 보류 사유 (출처 · 판례별을 합친다). 보류가 아니면 None."""
    reasons = [r for r in (SOURCE_HOLDS.get(precedent["source"]), PRECEDENT_HOLDS.get(precedent["key"])) if r]
    return " · ".join(reasons) or None


def insert_sql(precedent, embedding):
    result = precedent["result"]
    hold = hold_reason(precedent)
    sentence = result.get("sentence") or {}
    flags = dict(precedent["flags"])
    if precedent.get("privacyWarnings"):
        flags["privacyWarnings"] = len(precedent["privacyWarnings"])
    if result.get("notes"):
        flags["notes"] = result["notes"]
    if precedent.get("usedFreeTier"):
        flags["usedFreeTier"] = True
    charge = (result.get("chargeName") or "").strip()
    if charge and not MURDER_CHARGE.search(charge):
        # 팀 결정(2026-10-10): 빼지 않고 확인 필요로만 표시한다 (판례 조건 8-1의 대상 죄명이 아님)
        flags["checks"] = [*flags.get("checks", []), f"유죄 죄명이 살인 계열이 아님 ({charge})"]
    values = [
        sql_text(precedent["source"]), sql_text(precedent["sourceRef"]), sql_text_array(number_hashes(precedent)),
        sql_text(precedent["caseGroup"]), sql_text(precedent["crimeType"]),
        sql_text((result.get("chargeName") or "").strip() or precedent["chargeName"]),
        sql_jsonb(result.get("statutes")), sql_text(precedent["courtLevel"]),
        f"DATE {sql_text(precedent['decidedAt'])}" if precedent["decidedAt"] else "NULL",
        f"DATE {sql_text(precedent['incidentDate'])}" if precedent["incidentDate"] else "NULL",
        guideline_sql(precedent["guideline"]), sql_text(sentence.get("penaltyType")),
        sql_int(sentence.get("prisonMonths")), sql_int(sentence.get("suspensionMonths")),
        sql_int(sentence.get("fineAmount")), "NULL", sql_jsonb(result.get("sentencingFactors")),
        sql_text(result["factsSummary"]), sql_text(result["sentencingReason"]), sql_vector(embedding["values"]),
        sql_text(embedding["model"]), sql_int(embedding["dim"]),
        "true" if hold else "false", sql_text(hold), sql_jsonb(flags),
    ]
    return f"INSERT INTO rag_precedent ({COLUMNS})\nVALUES ({', '.join(values)});"


def cosine(a, b, norm_a=None, norm_b=None):
    dot = sum(x * y for x, y in zip(a, b))
    norm_a = norm_a or math.sqrt(sum(x * x for x in a))
    norm_b = norm_b or math.sqrt(sum(y * y for y in b))
    return dot / (norm_a * norm_b) if norm_a and norm_b else 0.0


def merge_groups(pairs, threshold=GROUP_MERGE_SCORE):
    """사실관계 요약 임베딩이 threshold 이상 비슷한 판례끼리 같은 사건 그룹으로 묶는다 (pairs: [(판례, 임베딩)]).

    1심 · 항소심은 사실을 다른 문장으로 다시 쓰고, LBox는 사건번호가 없어 후보 목록 단계에서 못 묶은 같은 사건이 남는다.
    같은 사건이 다른 그룹에 있으면 자기 사건 제외 검색에서 빠지지 않아 실제 판결이 노출되므로, 비슷하면 묶는 쪽으로 둔다.
    새 그룹 ID는 묶인 판례 키 중 가장 앞선 값이다. 묶인 판례는 flags.groupMergedByEmbedding = true. 묶은 쌍 목록을 돌려준다.
    """
    norms = [math.sqrt(sum(v * v for v in e["values"])) for _, e in pairs]
    parent = {p["caseGroup"]: p["caseGroup"] for p, _ in pairs}

    def find(group):
        while parent[group] != group:
            group = parent[group]
        return group

    merged = []
    for i, (a, ea) in enumerate(pairs):
        for j in range(i + 1, len(pairs)):
            b, eb = pairs[j]
            if find(a["caseGroup"]) == find(b["caseGroup"]):
                continue
            score = cosine(ea["values"], eb["values"], norms[i], norms[j])
            if score >= threshold:
                ga, gb = find(a["caseGroup"]), find(b["caseGroup"])
                parent[max(ga, gb)] = min(ga, gb)
                merged.append((a["key"], b["key"], round(score, 4)))
    for precedent, _ in pairs:
        group = find(precedent["caseGroup"])
        if group != precedent["caseGroup"]:
            precedent["flags"] = {**precedent["flags"], "groupMergedByEmbedding": True}
            precedent["caseGroup"] = group
    return merged


def refresh_metadata(precedent, current):
    """정리 결과(result)는 그대로 두고 메타데이터(사건 그룹 · 발생일 · 양형기준 · 표시)를 최신 후보 목록 값으로 바꾼다."""
    return {**precedent, **current, "result": precedent["result"]}


def build_sql(out_dir, merge_threshold=GROUP_MERGE_SCORE, candidates=None):
    """candidates(최신 후보 목록)를 주면 메타데이터를 그 값으로 맞추고, 후보에서 빠진 판례(사본 판정 등)는 적재하지 않는다."""
    out = Path(out_dir)
    current = {t["key"]: t for t in targets(candidates)} if candidates is not None else None
    pairs, missing, dropped = [], [], []
    for path in sorted((out / "precedents").glob("*.json")):
        embedding_path = out / "embeddings" / path.name
        if not embedding_path.exists():
            missing.append(path.stem)
            continue
        precedent = load_json(path)
        if current is not None:
            if path.stem not in current:
                dropped.append(path.stem)
                continue
            precedent = refresh_metadata(precedent, current[path.stem])
        pairs.append((precedent, load_json(embedding_path)))
    if dropped:
        print(f"후보 목록에 없어 적재하지 않음 {len(dropped)}건 (사본 판정 · 조건 미통과): {', '.join(dropped)}", file=sys.stderr)
    if missing:
        raise RagStoreError(f"임베딩이 없는 판례 {len(missing)}건: embed를 먼저 실행한다 ({', '.join(missing[:5])} …)")
    if not pairs:
        raise RagStoreError("적재할 판례가 없습니다 (build · embed를 먼저 실행한다)")
    merged = merge_groups(pairs, merge_threshold)
    for a, b, score in merged:
        print(f"같은 사건으로 묶음 (요약 유사도 {score}): {a} · {b}", file=sys.stderr)
    rows = [insert_sql(precedent, embedding) for precedent, embedding in pairs]
    held = sum(1 for precedent, _ in pairs if hold_reason(precedent))
    header = [
        "-- R__40_rag_precedent.sql — AI 판결 RAG 판례 저장소 (BE-51). 공개 저장소 tools/ai-judgment/rag_store.py sql 출력",
        "-- 손으로 고치지 않는다. 판례를 바꾸려면 build · embed · sql을 다시 실행한다",
        "-- 파일이 바뀌면 Flyway가 다시 실행해 rag_precedent를 통째로 바꾼다 (다른 테이블은 이 표를 참조하지 않는다)",
        f"-- 판례 {len(rows)}건 (사용 보류 {held}건: 출처 {', '.join(sorted(SOURCE_HOLDS)) or '없음'} · 판례별 {len(PRECEDENT_HOLDS)}건). "
        "사람 확인 없이 전부 넣었다(팀 결정 2026-10-10, flags.verified = false)",
        "",
        "DELETE FROM rag_precedent;",
        "",
    ]
    return "\n".join(header) + "\n\n".join(rows) + "\n"


def run_sql(args):
    from pipeline import PipelineError, check_sql_dir  # 비공개 저장소 체크아웃 안에만 쓴다 (BE-31과 같은 검사)
    output = Path(args.output).resolve()
    try:
        check_sql_dir(output.parent)
    except PipelineError as e:
        raise RagStoreError(str(e))
    candidates = load_json(args.candidates) if args.candidates else None
    if candidates is None:
        print("주의: --candidates 없이 만들면 정리할 때 저장한 메타데이터(사건 그룹 · 양형기준 등)를 그대로 쓴다", file=sys.stderr)
    sql = build_sql(args.out, args.merge_threshold, candidates)
    tmp = output.with_suffix(".tmp")
    tmp.write_text(sql, encoding="utf-8")
    tmp.replace(output)
    print(f"{output} ({sql.count('INSERT INTO')}건)", file=sys.stderr)


def main(argv=None):
    parser = argparse.ArgumentParser(description="AI 판결 RAG 판례 저장소 적재 (BE-51)")
    sub = parser.add_subparsers(dest="command", required=True)
    build = sub.add_parser("build", help="판결문 비식별화 · 사실관계 요약 / 양형 이유 분리")
    build.add_argument("--candidates", required=True, help="비공개 저장소 work/BE-50-precedent-candidates.json")
    build.add_argument("--raw", required=True, help="비공개 저장소 raw/BE-50 (판결문 원문, git 제외)")
    build.add_argument("--model", required=True, help="'공급자:모델ID'")
    build.add_argument("--allow-free-tier", action="store_true",
                       help="무료 등급이 있는 모델 허용 (마스킹한 공개 판결문 원문이 무료 등급으로 갈 수 있음, 팀이 위험을 받아들였을 때만)")
    build.add_argument("--limit", type=int, help="앞에서부터 이만큼만 (시험용)")
    build.add_argument("--max-retries", type=int, default=2)
    build.add_argument("--max-tokens", type=int, default=8000)
    build.add_argument("--delay", type=float, default=0.0, help="호출 사이 쉬는 초")
    prepare = sub.add_parser("prepare", help="세션이 직접 정리할 판례의 마스킹한 판결문 꺼내기 (manual/inputs)")
    importer = sub.add_parser("import", help="세션이 쓴 결과(manual/outputs) 검사 후 넣기")
    importer.add_argument("--keys", help="쉼표로 구분한 판례 키 (기본: outputs 전체)")
    importer.add_argument("--model", default=MANUAL_MODEL, help=f"기록할 모델 이름 (기본 {MANUAL_MODEL})")
    for command in (prepare, importer):
        command.add_argument("--candidates", required=True, help="비공개 저장소 work/BE-50-precedent-candidates.json")
    prepare.add_argument("--raw", required=True, help="비공개 저장소 raw/BE-50")
    embed = sub.add_parser("embed", help="사실관계 요약 임베딩")
    embed.add_argument("--model", default=EMBEDDING_MODEL)
    embed.add_argument("--delay", type=float, default=0.0, help="100건 묶음 사이 쉬는 초 (무료 등급 분당 한도)")
    sql = sub.add_parser("sql", help="R__40 시드 SQL 만들기")
    sql.add_argument("--output", required=True, help="비공개 저장소 seed/R__40_rag_precedent.sql")
    sql.add_argument("--candidates", help="최신 후보 목록 (work/BE-50-precedent-candidates.json). 주면 메타데이터를 다시 맞춘다")
    sql.add_argument("--merge-threshold", type=float, default=GROUP_MERGE_SCORE,
                     help=f"요약 유사도가 이 이상이면 같은 사건 그룹으로 묶는다 (기본 {GROUP_MERGE_SCORE})")
    for command in (build, prepare, importer, embed, sql):
        command.add_argument("--out", default=str(OUT_DIR), help="중간 결과 폴더 (기본 out/rag, git 제외)")
    args = parser.parse_args(argv)
    try:
        if args.command == "build":
            return 1 if run_build(args) else 0
        if args.command == "prepare":
            run_prepare(args)
        elif args.command == "import":
            return 1 if run_import(args) else 0
        elif args.command == "embed":
            run_embed(args)
        else:
            run_sql(args)
    except (RagStoreError, llm.LLMError) as e:
        print(f"오류: {e}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
