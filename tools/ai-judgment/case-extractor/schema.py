"""모델 응답 스키마와, 응답을 tools/ai-judgment 사건 입력 형식(case.json)으로 바꾸는 코드.

모델에게는 섹션을 평평한 필드로 받고(구조화 출력 스키마를 단순하게 두려고), 여기서 case.json의 sections 배열로 조립한다.
실제 판결(courtJudgment)은 case.json에 넣지 않고 내부 전용 파일로 따로 쓴다(FR-4-1, REQ-041).
"""

import re
import sys
from pathlib import Path

PARENT_DIR = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(PARENT_DIR))

from common import NO_TERM_PENALTIES, PENALTY_TYPES, REDUCIBLE_TO, format_months, format_won  # noqa: E402

# 프롬프트나 스키마를 고치면 올린다. 보고서(report.json)에 기록된다.
EXTRACT_PROMPT_VERSION = "extract-v1"

CRIME_TYPES = ("MURDER", "FRAUD", "INJURY")  # ERD legal_case.crime_type
REVEAL_STAGES = ("OVERVIEW", "DETAIL", "ARGUMENT", "LAW")  # ERD factor.reveal_stage

LABEL_MAX_LENGTH = 100  # ERD factor.label · pre_label varchar(100)
SUMMARY_TAG_MAX_LENGTH = 20  # ERD factor.summary_tag varchar(20)
TITLE_MAX_LENGTH = 100  # ERD legal_case.title varchar(100)

_STRING = {"type": "string"}
_NULLABLE_STRING = {"type": ["string", "null"]}
_NULLABLE_INT = {"type": ["integer", "null"]}


def _object(properties):
    return {
        "type": "object",
        "properties": properties,
        "required": list(properties),
        "additionalProperties": False,
    }


OUTPUT_SCHEMA = _object({
    "title": _STRING,
    "crimeType": {"type": "string", "enum": list(CRIME_TYPES)},
    "chargeName": _STRING,
    "appliedLaw": _STRING,
    "statutoryPenaltyText": _STRING,
    "overview": _STRING,
    "facts": _STRING,
    "damage": {"type": "array", "items": _object({"label": _STRING, "value": _STRING})},
    "defendant": _STRING,
    "settlement": _STRING,
    "prosecutor": _STRING,
    "defense": _STRING,
    "lawTerms": {"type": "array", "items": _object({"term": _STRING, "desc": _STRING})},
    "penaltyRules": {
        "type": "array",
        "items": _object({
            "penaltyType": {"type": "string", "enum": list(PENALTY_TYPES)},
            "statutoryMin": _NULLABLE_INT,
            "statutoryMax": _NULLABLE_INT,
            "allowedMin": {"type": "integer"},
            "allowedMax": {"type": "integer"},
            "suspensionAllowed": {"type": "boolean"},
            "allowedBasis": _STRING,
        }),
    },
    "recommended": {
        "anyOf": [
            _object({"minMonths": {"type": "integer"}, "maxMonths": {"type": "integer"}, "basis": _STRING}),
            {"type": "null"},
        ]
    },
    "sentencingGuideline": _NULLABLE_STRING,
    "factors": {
        "type": "array",
        "items": _object({
            "label": _STRING,
            "preLabel": _NULLABLE_STRING,
            "revealStage": {"type": "string", "enum": list(REVEAL_STAGES)},
            "summaryTag": _STRING,
        }),
    },
    "courtJudgment": _object({
        "penaltyType": {"type": "string", "enum": list(PENALTY_TYPES)},
        "reducedTo": {"anyOf": [{"type": "string", "enum": list(PENALTY_TYPES)}, {"type": "null"}]},
        "prisonMonths": _NULLABLE_INT,
        "fineAmount": _NULLABLE_INT,
        "suspensionMonths": _NULLABLE_INT,
    }),
    "deidentifiedItems": {"type": "array", "items": _STRING},
    "reviewNotes": {"type": "array", "items": _STRING},
})

# (응답 필드, stage, sectionType, 화면 라벨). 순서가 case.json sections 순서다 (ERD case_section).
TEXT_SECTIONS = [
    ("facts", "DETAIL", "FACTS", "주요 사실관계"),
    ("defendant", "DETAIL", "DEFENDANT", "피고인 관련 사실"),
    ("settlement", "DETAIL", "SETTLEMENT", "합의 · 피해 회복"),
    ("prosecutor", "ARGUMENT", "PROSECUTOR", "검사 측 (불리한 사정)"),
    ("defense", "ARGUMENT", "DEFENSE", "피고인 · 변호인 측 (유리한 사정 · 변호인 주장)"),
]


class OutputError(Exception):
    pass


def build_sections(output):
    sections = []
    for field, stage, section_type, title in TEXT_SECTIONS[:1]:
        sections.append({"stage": stage, "sectionType": section_type, "title": title, "content": output[field]})
    if output.get("damage"):
        sections.append({"stage": "DETAIL", "sectionType": "DAMAGE", "title": "피해 결과", "data": output["damage"]})
    for field, stage, section_type, title in TEXT_SECTIONS[1:]:
        if output.get(field, "").strip():
            sections.append({"stage": stage, "sectionType": section_type, "title": title, "content": output[field]})
    if output.get("lawTerms"):
        sections.append({"stage": "LAW", "sectionType": "LAW_TERM", "title": "용어 설명", "data": output["lawTerms"]})
    return sections


def build_case_input(output):
    """모델 응답 → case.json. factorId는 1부터 붙여 시드 SQL의 factor.display_order와 맞춘다(README 참고).
    caseId는 파일 구분용일 뿐 DB와 맞출 필요가 없다."""
    factors = [
        {"factorId": i, "label": f["label"], "revealStage": f["revealStage"]}
        for i, f in enumerate(output["factors"], start=1)
    ]
    # 형벌 표시 순서(penalty_rule.display_order): 법조문 표기 순서대로 무거운 형벌부터 DEATH → LIFE → PRISON → FINE (ERD v1.9).
    # 모델이 다른 순서로 줘도 PENALTY_TYPES 순서로 맞춘다
    penalty_rules = [
        {key: rule[key] for key in
         ("penaltyType", "statutoryMin", "statutoryMax", "allowedMin", "allowedMax", "suspensionAllowed")}
        for rule in sorted(output["penaltyRules"], key=lambda rule: PENALTY_TYPES.index(rule["penaltyType"]))
    ]
    return {
        "_comment": "판결문 가공 스크립트(case-extractor)가 만든 초안. 팀 검수 전이다. caseId는 null(파일 구분용일 "
                    "뿐 DB와 맞출 필요 없음), factorId는 1부터 붙여 시드 SQL의 display_order와 맞춘다. "
                    "git에 올리지 않는다.",
        "caseId": None,
        "title": output["title"],
        "crimeType": output["crimeType"],
        "chargeName": output["chargeName"],
        "appliedLaw": output["appliedLaw"],
        "statutoryPenaltyText": output["statutoryPenaltyText"],
        "overview": output["overview"],
        "sections": build_sections(output),
        "penaltyRules": penalty_rules,
        "recommended": output.get("recommended"),
        "factors": factors,
        "references": {
            "sentencingGuideline": output.get("sentencingGuideline"),
            # 유사 판례는 대상 사건 판결문만으로 만들 수 없다. 팀이 대상 사건을 뺀 판례로 채운다 (FR-4-2)
            "similarCases": [],
        },
    }


def build_court_judgment(output):
    court = output["courtJudgment"]
    return {
        "_comment": "내부 전용. 사전 학습 점검(check_contamination.py)에서만 쓴다. AI 판결 생성 프롬프트에 절대 넣지 않는다.",
        "caseId": None,
        **court,
    }


def build_factor_extras(output):
    """case.json 형식에는 없지만 시드(factor 테이블)에 필요한 값. 보고서에 남긴다."""
    return [
        {"factorId": i, "preLabel": f.get("preLabel"), "summaryTag": f["summaryTag"]}
        for i, f in enumerate(output["factors"], start=1)
    ]


def check_output(output):
    """스키마로 막을 수 없는 규칙을 검사한다. (오류 목록, 경고 목록)."""
    errors, warnings = [], []
    if len(output["title"]) > TITLE_MAX_LENGTH:
        errors.append(f"사건 제목이 {TITLE_MAX_LENGTH}자를 넘습니다")
    if not output["factors"]:
        errors.append("판단 요소가 없습니다")
    if not any(f["revealStage"] == "OVERVIEW" for f in output["factors"]):
        errors.append("사전 판단에 쓸 OVERVIEW 판단 요소가 없습니다")
    for i, f in enumerate(output["factors"], start=1):
        if len(f["label"]) > LABEL_MAX_LENGTH:
            errors.append(f"판단 요소 {i}: 문구가 {LABEL_MAX_LENGTH}자를 넘습니다")
        if len(f["summaryTag"]) > SUMMARY_TAG_MAX_LENGTH:
            errors.append(f"판단 요소 {i}: 요약 태그가 {SUMMARY_TAG_MAX_LENGTH}자를 넘습니다")
        if f.get("preLabel") and f["revealStage"] != "OVERVIEW":
            warnings.append(f"판단 요소 {i}: preLabel은 OVERVIEW 요소에만 씁니다")
        if f["revealStage"] == "OVERVIEW" and not f.get("preLabel"):
            warnings.append(f"판단 요소 {i}: OVERVIEW 요소에 사전 판단용 짧은 문구(preLabel)가 없습니다")

    types = [rule["penaltyType"] for rule in output["penaltyRules"]]
    if len(types) != len(set(types)):
        errors.append("형벌 규칙에 같은 형벌 종류가 두 번 있습니다 (uk_penalty_rule_case_penalty_type)")
    for rule in output["penaltyRules"]:
        if rule["allowedMin"] > rule["allowedMax"]:
            errors.append(f"{rule['penaltyType']}: 선고 가능 하한이 상한보다 큽니다")
        if rule["penaltyType"] in NO_TERM_PENALTIES and (
            rule["statutoryMin"] is not None or rule["statutoryMax"] is not None
        ):
            errors.append(f"{rule['penaltyType']}: 사형 · 무기는 법정형 하한 · 상한이 null이어야 합니다 (ERD v1.5 CHECK)")
    warnings.append("형벌 규칙(선고 가능 범위)은 모델이 계산한 값입니다. 법조문으로 다시 확인하세요 (ERD penalty_rule)")

    court = output["courtJudgment"]
    if court["reducedTo"] and court["reducedTo"] not in REDUCIBLE_TO[court["penaltyType"]]:
        errors.append(f"실제 판결: {court['penaltyType']} → {court['reducedTo']} 감경은 허용되지 않는 조합입니다")
    return errors, warnings


def visible_texts(case_input):
    """사용자에게 보일 수 있는 문자열 [(위치, 문자열)]. 비식별화 · 판결 누출 검사 대상이다."""
    texts = [("title", case_input["title"]), ("overview", case_input["overview"])]
    for i, section in enumerate(case_input["sections"]):
        where = f"sections[{i}]({section['sectionType']})"
        if section.get("content"):
            texts.append((where, section["content"]))
        for item in section.get("data") or []:
            texts.append((where, " ".join(str(v) for v in item.values())))
    for factor in case_input["factors"]:
        texts.append((f"factors[{factor['factorId']}]", factor["label"]))
    return texts


_AMOUNT_END = r"(?!\d|이상|이하|미만|이내|초과|부터|까지|에서)"


def _alternatives(forms):
    return "(?:" + "|".join(re.escape(f) for f in sorted(forms, key=len, reverse=True)) + ")"


def _month_forms(months):
    """개월 수의 표기 변형 (공백은 뺀 형태): "10년", "120개월", "10년6개월"."""
    return {format_months(months).replace(" ", ""), f"{months}개월"}


def _won_forms(amount):
    """금액의 표기 변형: "2천만원", "2천만", "20,000,000원"."""
    text = format_won(amount).replace(" ", "")
    return {text, text.removesuffix("원"), f"{amount:,}원", f"{amount}원"}


def _leak_patterns(court):
    """(표시할 형량, 공백 없는 문장에서 찾을 패턴) 목록. 형량 용어와 기간 · 금액이 함께 있는 표현만 본다."""
    patterns = []

    def add(label, term, forms, before):
        # before: 역방향 표현에서 앞에 올 수 없는 글자. 더 큰 금액 · 기간의 뒷부분("1억 2천만원", "2년 6개월")을 잘라 오탐하지 않는다
        alt = _alternatives(forms)
        patterns.append((label, re.compile(f"(?:{term}{alt}{_AMOUNT_END}|(?<![{before}]){alt}(?:간|의)?{term})")))

    if court.get("prisonMonths"):
        add(f"징역 {format_months(court['prisonMonths'])}", "징역형?", _month_forms(court["prisonMonths"]), r"\d년")
    if court.get("fineAmount"):
        add(f"벌금 {format_won(court['fineAmount'])}", "벌금형?", _won_forms(court["fineAmount"]), r"\d억천백")
    if court.get("suspensionMonths"):
        add(f"집행유예 {format_months(court['suspensionMonths'])}", "집행유예", _month_forms(court["suspensionMonths"]), r"\d년")
        patterns.append(("집행유예", re.compile("징역형?의?집행을?유예")))
    return patterns


def sentence_leak_check(case_input, court):
    """실제 선고 형량이 AI 입력(개요 · 사실관계 · 주장 · 요소)에 드러났는지 본다 (FR-4-1).

    공백과 어순이 달라도("징역10년", "10년의 징역", "징역 120개월") 잡되,
    법정형 범위("징역 10년 이상")나 범행 기간 같은 다른 숫자는 형량 용어와 붙어 있을 때만 본다.
    """
    patterns = _leak_patterns(court)
    errors, warnings = [], []
    for where, text in visible_texts(case_input):
        if where.startswith("sections") and "LAW_TERM" in where:
            continue  # 용어 설명은 법정형 · 감경 범위 숫자를 쓴다
        compact = re.sub(r"\s+", "", text)
        for label, pattern in patterns:
            if pattern.search(compact):
                errors.append(f"{where}: 실제 선고 형량이 드러남 — \"{label}\"")
        if "선고" in text:
            warnings.append(f"{where}: '선고'라는 말이 있음 — 재판부 판단이 드러나지 않는지 확인")
    return errors, warnings


# 판결문 양형 이유에서 재판부가 평가 · 결론을 말할 때 쓰는 표현 (공백을 뺀 문장에서 찾는다).
# 사실("범행을 인정했다", "유족이 엄벌을 원한다")은 걸리지 않도록 평가에만 쓰이는 말로 좁힌다.
_MITIGATING_LABEL = "유리한 · 불리한 정상"
_COURT_EVALUATION_PATTERNS = [
    ("죄책", re.compile("죄책")),
    ("죄질", re.compile("죄질")),
    ("엄중", re.compile("엄중")),
    ("참작한다", re.compile("참작(?:할만한|한다|하였|하기로)")),
    ("용서 · 용납 · 정당화될 수 없다", re.compile("(?:용서|용납|정당화)(?:될|할)수없")),
    ("반인륜", re.compile("반인륜")),
    ("~함이 마땅 · 상당하다", re.compile("(?:함이|봄이|보는것이)(?:마땅|상당)")),
    (_MITIGATING_LABEL, re.compile("(?:유리|불리)한정상")),
    ("재판부 · 원심", re.compile("재판부|원심")),
]


def court_evaluation_check(case_input):
    """재판부의 평가 · 결론 문장이 사용자 화면과 AI 입력에 남았는지 본다 (프롬프트 2절, FR-4-1).

    판결문 양형 이유를 옮기다 "죄책이 무거워 엄중한 처벌이 필요하다"처럼 재판부 결론이 섞이면
    판결 공개 전에 재판부 판단이 드러나고 AI 판결도 그 결론을 보고 만들게 된다. 사실과 섞여 있을 수 있어 경고로만 남긴다.
    검사 · 피고인 측 섹션(PROSECUTOR · DEFENSE)은 각 측의 주장이라 같은 표현이 정상일 수 있어, 재판부를 명시하지 않았다면
    "재판부 평가" 대신 "양형 이유 표현일 수 있음"으로 안내한다.
    """
    warnings = []
    for where, text in visible_texts(case_input):
        if where.startswith("sections") and "LAW_TERM" in where:
            continue  # 용어 설명은 "참작" 같은 법률 용어를 쓴다
        compact = re.sub(r"\s+", "", text)
        # 검사 · 피고인 측 섹션은 각 측의 주장이라 "엄중한 처벌이 필요하다" 같은 말이 흔하다.
        # "재판부" · "원심"이 함께 없으면 재판부 평가라고 단정하지 않고, 판결문 양형 이유 표현일 수 있다는 안내만 낸다
        party_section = ("(PROSECUTOR)" in where or "(DEFENSE)" in where) and not re.search("재판부|원심", compact)
        for label, pattern in _COURT_EVALUATION_PATTERNS:
            if not pattern.search(compact):
                continue
            if party_section:
                warnings.append(f"{where}: 판결문 양형 이유의 표현일 수 있음 \"{label}\" — 재판부의 평가 · 결론을 옮긴 문장이면 빼고 각 측의 사정 · 주장으로 바꿔 쓴다 (프롬프트 2절)")
                continue
            warnings.append(f"{where}: 재판부 평가로 보이는 표현 \"{label}\" — 사실만 남기고 평가 · 결론은 뺀다 (프롬프트 2절)")
    return warnings
