"""AI 판결 생성(generate)에 넣을 참고 자료를 RAG로 찾는다 (BE-54, WBS 7.5.5).

사건 개요(비식별화한 사건 입력)로 검색어 임베딩(RETRIEVAL_QUERY)을 만들고, 로컬 DB의 함수로 찾는다.
  1) 양형기준 버전: 기준일(공소제기일 → 없으면 발생일)로 sentencing_guideline_at (docs/sentencing-guideline-rag.md 6장)
  2) 형량범위: 그 버전의 '형량범위' 조각 전부 (유형 판단은 모델 몫이라 검색하지 않고 바로 읽는다)
  3) 양형인자 · 정의: rag_guideline_search (그 버전 안에서만)
  4) 유사 판례: rag_precedent_search (자기 사건의 사건번호 해시로 사건 그룹 전체를 뺀다). 상한 이상은 자기 사건 의심으로 빼고,
     같은 양형기준 버전의 판례는 점수가 비슷할 때만 앞에 둔다(SAME_GUIDELINE_BONUS, 거르지는 않는다 — 판례가 적다)

검색은 DB 함수(V11 · V13)가 하고, 이 파일은 질의를 만들고 결과를 프롬프트용 자료로 정리한다. 백엔드 Java 검색(RagSearchService)은
서비스 요청용이고, 오프라인 생성은 여기서 같은 DB 함수를 부른다(BE-56 1단계).

참고 자료를 못 만들면(키 없음 · DB 접속 실패 · 버전 없음 · 판례 없음) 사건 입력의 기존 references를 그대로 쓰고 사유를 보고에 남긴다.
required=True면 하나라도 못 만들었을 때(PARTIAL 포함) RagContextError로 멈춘다(#143 리뷰).
대상 사건의 사건번호가 없으면(해시 없음) 유사 판례를 붙이지 않는다. 점수 상한만으로는 자기 사건 판결을 확실히 막는다고
보장할 수 없어서다(#143 리뷰. 자기 사건 점수는 rag_check.py self로 잰다).

보고(report)에는 버전 이름 · 기준 종류 · 판례 id · 점수만 남긴다. 사건번호 · 기준일 · 판례 본문은 넣지 않는다.
"""

import copy
import json
import math

from common import GUIDELINE_CATEGORIES, PENALTY_NAMES, format_months, format_won
from llm import LLMError, embed
from rag_store import case_number_hashes

EMBEDDING_MODEL = "gemini-embedding-001"
EMBEDDING_DIM = 3072
QUERY_MAX_CHARS = 6000  # gemini-embedding-001 입력 한도(2,048 토큰) 안으로 자른다

GUIDELINE_RANGE_SECTION = "형량범위"
GUIDELINE_SEARCH_SECTIONS = ("양형인자", "양형인자의 정의", "유형의 정의")

DEFAULT_OPTIONS = {
    "required": False,
    # 유사 판례: 상위 topK건을 쓴다. 같은 버전 우선 정렬을 위해 candidates건까지 받아 둔다.
    # minScore 미만은 빼고, maxScore 이상은 자기 사건 의심으로 뺀다 (rag-handover 5-4).
    # 10/15 실측(rag_check.py, 판례 53건): 다른 사건끼리 1위 0.725 ~ 0.811 · 5위 0.676 ~ 0.777, 실제 사건 개요 → 판례 0.72 ~ 0.81.
    # 점수가 좁게 몰려 있어 하한은 "관계없는 사건만 거르는" 0.70, 상한은 다른 사건 최고(0.81)보다 충분히 위인 0.88로 둔다.
    # 자기 사건은 사건번호 해시로 빼고(해시가 없으면 판례를 붙이지 않는다), 상한은 해시가 놓친 사본에 대한 두 번째 방어선이다.
    # 같은 사건의 다른 판결끼리는 0.816 ~ 0.935(10/15 self, 20쌍 중 7쌍이 0.88 미만)라 상한은 확실한 방어선이 아니고 경고에 가깝다.
    # 다른 사건 최고(0.811)와 겹쳐 상한을 낮춰도 풀리지 않는다. 판례가 늘면 다시 잰다
    "precedent": {"topK": 5, "candidates": 10, "minScore": 0.70, "maxScore": 0.88},
    # 양형인자 · 정의 검색 (확정 10/15, sentencing-guideline-rag 5장)
    # 10/15 실측: 양형인자 · 정의 조각 점수 0.645 ~ 0.705. 프롬프트가 길어지지 않게 6개만 넣는다
    "guideline": {"topK": 6, "minScore": 0.6},
}

# 같은 양형기준 버전 판례에 더하는 점수 (정렬에만 쓴다). 실측 점수가 0.72 ~ 0.81에 몰려 있어 버전으로 통째 앞세우면
# 덜 비슷한 판례가 더 비슷한 판례를 밀어낸다(10/15 확인). 비슷한 점수 사이에서만 같은 버전을 앞에 둔다
SAME_GUIDELINE_BONUS = 0.01

BASIS_INDICTMENT = "INDICTMENT_DATE"
BASIS_INCIDENT = "INCIDENT_DATE"


class RagContextError(Exception):
    pass


class RagUnavailable(Exception):
    """DB 실행기가 검색을 못 했을 때 (접속 실패 · 명령 없음 등). 대체 동작으로 넘어간다."""


# ---------------------------------------------------------------- 입력 정리

def options_with(overrides):
    options = copy.deepcopy(DEFAULT_OPTIONS)
    for key, value in (overrides or {}).items():
        if isinstance(value, dict) and isinstance(options.get(key), dict):
            options[key].update(value)
        else:
            options[key] = value
    return options


def option_errors(options, prefix="stages.rag"):
    """설정 값 검사. 오류 문구 목록."""
    errors = []
    if not isinstance(options.get("required"), bool):
        errors.append(f"{prefix}.required는 true · false다")
    precedent, guideline = options.get("precedent") or {}, options.get("guideline") or {}
    for group, key in (("precedent", "topK"), ("precedent", "candidates"), ("guideline", "topK")):
        value = (precedent if group == "precedent" else guideline).get(key)
        if not isinstance(value, int) or isinstance(value, bool) or value < 1:
            errors.append(f"{prefix}.{group}.{key}는 1 이상의 정수다")
    for group, key in (("precedent", "minScore"), ("precedent", "maxScore"), ("guideline", "minScore")):
        value = (precedent if group == "precedent" else guideline).get(key)
        if not isinstance(value, (int, float)) or isinstance(value, bool) or not -1 <= value <= 1:
            errors.append(f"{prefix}.{group}.{key}는 -1 ~ 1 사이 숫자다")
    if not errors:
        if precedent["candidates"] < precedent["topK"]:
            errors.append(f"{prefix}.precedent.candidates는 topK 이상이다")
        if precedent["minScore"] >= precedent["maxScore"]:
            errors.append(f"{prefix}.precedent.minScore는 maxScore보다 작다")
    return errors


def query_text(case):
    """검색어: 비식별화한 사건 개요 + 사건 상세 본문. 죄명 · 형벌 범위 · 판단 요소는 넣지 않는다(사실관계로 찾는다)."""
    parts = [case.get("overview") or ""]
    for section in case.get("sections") or []:
        if section.get("content"):
            parts.append(section["content"])
        for item in section.get("data") or []:
            if "label" in item and "value" in item:
                parts.append(f"{item['label']}: {item['value']}")
    text = "\n".join(p.strip() for p in parts if p and p.strip())
    return text[:QUERY_MAX_CHARS]


def base_date(indictment_date=None, incident_date=None):
    """양형기준 버전 기준일과 그 종류. 공소제기일 → 없으면 발생일 → 둘 다 없으면 (None, None)."""
    if indictment_date:
        return indictment_date, BASIS_INDICTMENT
    if incident_date:
        return incident_date, BASIS_INCIDENT
    return None, None


def own_case_hashes(sources):
    """대상 사건의 사건번호(심급별 · 병합이면 번호마다) 해시. rag_store · 백엔드 CaseNumberHash와 같은 규칙."""
    hashes = set()
    for source in sources or []:
        hashes.update(case_number_hashes(source.get("caseNumber")))
    return sorted(hashes)


# ---------------------------------------------------------------- SQL

def sql_text(value):
    return "NULL" if value is None else "'" + str(value).replace("'", "''") + "'"


def sql_vector(values):
    if len(values) != EMBEDDING_DIM or not all(isinstance(v, (int, float)) and math.isfinite(v) for v in values):
        raise RagContextError(f"검색어 임베딩이 {EMBEDDING_DIM}차원 실수 벡터가 아닙니다")
    return "'[" + ",".join(repr(float(v)) for v in values) + f"]'::vector({EMBEDDING_DIM})"


def sql_text_array(values):
    return "ARRAY[" + ",".join(sql_text(v) for v in values) + "]::text[]" if values else "ARRAY[]::text[]"


def build_query_sql(vector, crime_type, category, at, hashes, options):
    """한 번의 psql 호출로 버전 · 형량범위 · 양형인자 · 판례 후보를 JSON 한 줄로 받는다."""
    precedent, guideline = options["precedent"], options["guideline"]
    sections = "ARRAY[" + ",".join(sql_text(s) for s in GUIDELINE_SEARCH_SECTIONS) + "]::varchar[]"
    version_sql = (f"sentencing_guideline_at({sql_text(category)}, {sql_text(at)}::date)" if category and at
                   else "NULL::bigint")
    # 판례의 양형기준 버전은 검색할 때 판례 발생일로 정한다. 시드 실행 순서(R__40 판례 → R__41 양형기준)상 판례를 넣을 때는
    # 버전 행이 아직 없을 수 있어 rag_precedent.guideline_id를 비워 두기 때문이다(값이 있으면 그것을 쓴다)
    precedent_version_sql = (f"coalesce(p.guideline_id, CASE WHEN p.incident_date IS NULL THEN NULL "
                             f"ELSE sentencing_guideline_at({sql_text(category)}, p.incident_date) END)" if category
                             else "p.guideline_id")
    return f"""WITH q AS (SELECT {sql_vector(vector)} AS v),
g AS (SELECT {version_sql} AS id)
SELECT json_build_object(
  'guideline', (SELECT json_build_object('id', s.id, 'versionName', s.version_name, 'effectiveDate', s.effective_date)
                FROM sentencing_guideline s WHERE s.id = (SELECT id FROM g)),
  'ranges', (SELECT coalesce(json_agg(json_build_object('id', r.id, 'subject', r.subject, 'typeNo', r.type_no,
                                                         'content', r.content) ORDER BY r.type_no, r.id), '[]'::json)
             FROM rag_guideline r WHERE r.guideline_id = (SELECT id FROM g) AND r.section = {sql_text(GUIDELINE_RANGE_SECTION)}),
  'factors', (SELECT coalesce(json_agg(json_build_object('id', r.id, 'section', r.section, 'subject', r.subject,
                                                          'content', r.content, 'score', f.score) ORDER BY f.score DESC, r.id), '[]'::json)
              FROM rag_guideline_search((SELECT v FROM q), (SELECT id FROM g), {sections}, {int(guideline['topK'])},
                                        {float(guideline['minScore'])}) f
              JOIN rag_guideline r ON r.id = f.chunk_id),
  'precedents', (SELECT coalesce(json_agg(json_build_object('id', p.id, 'caseGroup', p.case_group, 'score', s.score,
                   'guidelineId', {precedent_version_sql}, 'chargeName', p.charge_name, 'courtLevel', p.court_level,
                   'penaltyType', p.penalty_type, 'prisonMonths', p.prison_months, 'suspensionMonths', p.suspension_months,
                   'fineAmount', p.fine_amount, 'sentencingFactors', p.sentencing_factors, 'factsSummary', p.facts_summary,
                   'sentencingReason', p.sentencing_reason) ORDER BY s.score DESC, p.id), '[]'::json)
                 FROM rag_precedent_search((SELECT v FROM q), {sql_text(crime_type)}, {sql_text_array(hashes)}, NULL,
                                           {int(precedent['candidates'])}, {float(precedent['minScore'])}) s
                 JOIN rag_precedent p ON p.id = s.precedent_id)
);
"""


def parse_json_output(text):
    """psql -At 출력(JSON 값 하나)을 읽는다. json_agg는 원소 사이에 줄바꿈을 넣으므로 줄 단위가 아니라 출력 전체를 읽는다."""
    body = (text or "").strip()
    if not body:
        raise RagUnavailable("검색 결과가 비었습니다")
    try:
        return json.loads(body)
    except json.JSONDecodeError as e:
        raise RagUnavailable(f"검색 결과를 읽지 못했습니다: {e}")


def parse_query_result(text):
    result = parse_json_output(text)
    if not isinstance(result, dict):
        raise RagUnavailable("검색 결과 형식이 다릅니다")
    for key in ("ranges", "factors", "precedents"):
        result[key] = result.get(key) or []
    return result


# ---------------------------------------------------------------- 정리

def penalty_text(item):
    penalty_type = item.get("penaltyType")
    if penalty_type == "PRISON" and item.get("prisonMonths"):
        text = f"징역 {format_months(item['prisonMonths'])}"
        if item.get("suspensionMonths"):
            text += f", 집행유예 {format_months(item['suspensionMonths'])}"
        return text
    if penalty_type == "FINE" and item.get("fineAmount"):
        return f"벌금 {format_won(item['fineAmount'])}"
    return PENALTY_NAMES.get(penalty_type, "형량 미상")


def pick_precedents(candidates, guideline_id, options, warnings):
    """상한 이상(자기 사건 의심)을 빼고, 점수순(같은 버전이면 작은 가산)으로 topK건."""
    precedent = options["precedent"]
    kept = []
    for item in candidates:
        if item["score"] >= precedent["maxScore"]:
            warnings.append(f"유사도 {item['score']:.3f}(상한 {precedent['maxScore']} 이상) 판례 1건을 자기 사건 의심으로 뺐습니다 "
                            f"(판례 id {item['id']})")
            continue
        kept.append(item)
    def rank(item):
        same = guideline_id is not None and item.get("guidelineId") == guideline_id
        return -(item["score"] + (SAME_GUIDELINE_BONUS if same else 0)), item["id"]
    return sorted(kept, key=rank)[:precedent["topK"]]


def precedent_reference(index, item, guideline_id):
    level = {"FIRST": "1심", "APPEAL": "항소심"}.get(item.get("courtLevel"), "")
    label = ", ".join(x for x in (item.get("chargeName"), level) if x)
    return {
        "title": f"유사 판례 {index}" + (f" ({label})" if label else ""),
        "summary": item["factsSummary"],
        "sentence": penalty_text(item),
        "sentencingReason": item.get("sentencingReason"),
        "sentencingFactors": item.get("sentencingFactors"),
        "sameGuideline": guideline_id is not None and item.get("guidelineId") == guideline_id,
    }


def guideline_text(category, guideline, ranges, factors):
    """프롬프트 '양형기준' 칸. 기준일은 넣지 않는다(사건 시점이 드러나 사전 학습 점검을 흐린다)."""
    lines = [f"{category} 양형기준 · {guideline['versionName']} (이 사건에 적용되는 버전)"]
    if ranges:
        lines.append("\n[형량범위] 유형을 먼저 정하고 그 유형의 감경 · 기본 · 가중 영역을 본다.")
        lines += [f"- {chunk_line(r['subject'] or '유형', r['content'])}" for r in ranges]
    if factors:
        lines.append("\n[이 사건과 관련 있을 수 있는 양형인자 · 정의 (검색 결과)]")
        lines += [f"- [{f['section']}] {chunk_line(f['subject'], f['content'])}" for f in factors]
    return "\n".join(lines)


def chunk_line(subject, content):
    """조각 본문이 제목으로 시작하면 제목을 한 번만 쓰고, 여러 줄 본문은 들여 써서 목록 항목 안에 둔다."""
    content = (content or "").rstrip()
    subject = (subject or "").strip()
    if subject and content.strip().startswith(subject):
        rest = content.strip()[len(subject):]
        first, _, more = rest.partition("\n")
        first = first.strip().lstrip(":—").strip()
        lines = ([first] if first else []) + [line.rstrip() for line in more.splitlines() if line.strip()]
    else:
        lines = [line.rstrip() for line in content.strip().splitlines() if line.strip()]
    if not subject:
        return "\n".join(lines)
    if not lines:
        return subject
    if len(lines) == 1 and not lines[0].startswith(" "):
        return f"{subject}: {lines[0]}"
    return subject + "".join("\n" + (line if line.startswith("  ") else "  " + line.lstrip()) for line in lines)


# ---------------------------------------------------------------- 진입점

def build_references(case, sources, *, indictment_date=None, incident_date=None, options=None, embedder=None, runner=None,
                     log=print):
    """참고 자료를 만든다. (references, report)를 돌려준다.

    sources: 원본 판결문 정보 목록(source_internal.json의 sources, 설정 덮어쓰기 반영). 사건번호 해시에만 쓴다.
    embedder(texts, task_type=...) → 벡터 목록. 기본 llm.embed. runner(sql) → psql 출력 문자열(RagUnavailable로 실패를 알린다).
    """
    options = options_with(options)
    errors = option_errors(options)
    if errors:
        raise RagContextError("설정 오류: " + "; ".join(errors))
    embedder = embedder or (lambda texts, task_type: embed(texts, model=EMBEDDING_MODEL, task_type=task_type,
                                                         dimensions=EMBEDDING_DIM))
    original = copy.deepcopy(case.get("references") or {})
    crime_type = case.get("crimeType")
    category = GUIDELINE_CATEGORIES.get(crime_type)
    at, basis = base_date(indictment_date, incident_date)
    hashes = own_case_hashes(sources)
    warnings = []
    report = {"status": "OK", "fallbackReasons": [], "embeddingModel": EMBEDDING_MODEL, "guideline": None,
              "guidelineBasis": basis, "guidelineFactors": [], "precedents": [], "excludedSuspects": 0,
              "ownCaseHashes": len(hashes), "warnings": warnings}

    def fallback(reason):
        if options["required"]:
            raise RagContextError(reason)
        log(f"⚠ RAG 대체: {reason}")
        report["status"] = "FALLBACK"
        report["fallbackReasons"].append(reason)
        return original, report

    if not hashes:
        warnings.append("대상 사건의 사건번호가 없어 자기 사건을 뺄 수 없으므로 유사 판례를 붙이지 않습니다")
    if category is None:
        warnings.append(f"범죄 유형 {crime_type}의 양형기준 분류가 없어 양형기준은 찾지 않습니다")
    elif at is None:
        warnings.append("공소제기일 · 발생일이 없어 양형기준 버전을 고르지 못합니다")

    text = query_text(case)
    if not text:
        return fallback("검색어로 쓸 사건 개요가 없습니다")
    try:
        vector = embedder([text], task_type="RETRIEVAL_QUERY")[0]
        sql = build_query_sql(vector, crime_type, category, at, hashes, options)
    except (LLMError, RagContextError) as e:
        return fallback(f"검색어 임베딩 실패: {e}")
    if runner is None:
        return fallback("DB 실행기가 없습니다")
    try:
        result = parse_query_result(runner(sql))
    except RagUnavailable as e:
        return fallback(f"DB 검색 실패: {e}")

    references = copy.deepcopy(original)
    guideline = result.get("guideline")
    guideline_id = guideline["id"] if guideline else None
    if guideline:
        references["sentencingGuideline"] = guideline_text(category, guideline, result["ranges"], result["factors"])
        report["guideline"] = guideline["versionName"]
        report["guidelineFactors"] = [{"id": f["id"], "score": round(f["score"], 4)} for f in result["factors"]]
    elif category and at:
        warnings.append("기준일에 시행 중인 양형기준 버전이 없습니다 (최초 시행 전이거나 조각 미적재)")
    if not guideline:
        report["fallbackReasons"].append("양형기준: 사건 입력의 값을 그대로 씀")

    candidates = result["precedents"]
    picked = pick_precedents(candidates, guideline_id, options, warnings) if hashes else []
    report["excludedSuspects"] = sum(1 for p in candidates if p["score"] >= options["precedent"]["maxScore"])
    if picked:
        references["similarCases"] = [precedent_reference(i, p, guideline_id) for i, p in enumerate(picked, 1)]
        report["precedents"] = [{"id": p["id"], "score": round(p["score"], 4),
                                 "sameGuideline": guideline_id is not None and p.get("guidelineId") == guideline_id}
                                for p in picked]
    else:
        references["similarCases"] = []
        report["fallbackReasons"].append(
            f"유사 판례: 점수 {options['precedent']['minScore']} 이상 판례 없음" if hashes
            else "유사 판례: 사건번호가 없어 자기 사건을 뺄 수 없으므로 붙이지 않음")
    if report["fallbackReasons"]:
        report["status"] = "PARTIAL" if (guideline or picked) else "FALLBACK"
        if options["required"]:  # 일부만 못 찾아도(PARTIAL) 멈춘다
            raise RagContextError("참고 자료를 다 찾지 못했습니다: " + "; ".join(report["fallbackReasons"]))
    for warning in warnings:
        log(f"⚠ {warning}")
    log(f"RAG: 양형기준 {report['guideline'] or '없음'}, 양형인자 {len(report['guidelineFactors'])}개, "
        f"유사 판례 {len(report['precedents'])}건 ({report['status']})")
    return references, report
