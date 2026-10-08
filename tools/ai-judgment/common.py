"""AI 판결 오프라인 도구 공통 함수.

형량 단위는 API 명세서 1-1과 같다: 징역 · 집행유예는 개월(int), 벌금은 원(int).
"""

import json
from pathlib import Path

TOOL_DIR = Path(__file__).resolve().parent
PROMPT_DIR = TOOL_DIR / "prompts"

# 프롬프트를 고치면 버전을 올린다. ai_generation.prompt_version에 그대로 기록된다.
PROMPT_VERSION = "judgment-v2"
CONTAMINATION_PROMPT_VERSION = "contamination-v2"

# 법정형에서 고르는 형벌 (ERD v1.4 penalty_type). 무죄는 MVP에서 뺐다.
PENALTY_TYPES = ("DEATH", "LIFE", "PRISON", "FINE")
PENALTY_NAMES = {"DEATH": "사형", "LIFE": "무기징역", "PRISON": "징역", "FINE": "벌금"}

# 형벌 종류가 바뀌는 감경만 reduced_to에 기록한다 (ERD judgment.reduced_to · API 9 reducedTo).
# 유기징역 안의 작량감경은 기록하지 않는다.
REDUCIBLE_TO = {"DEATH": ("LIFE", "PRISON"), "LIFE": ("PRISON",), "PRISON": (), "FINE": ()}

# 형량 값(prisonMonths · fineAmount)이 없는 형벌. 최종 선고 형벌이 이것이면 형량 · 집행유예는 모두 null이다.
NO_TERM_PENALTIES = ("DEATH", "LIFE")

DIRECTIONS = ("UP", "DOWN")

# 판단 요소 가치관 축 (ERD factor.value_axis, BE-47). null = 어느 축에도 맞지 않음(성향 계산 제외).
# 축이 바뀌면 함께 고친다: Java ValueAxis · V9 CHECK(chk_factor_value_axis) · 추출기 프롬프트(extract_system.md)
VALUE_AXES = ("APOLOGY_SINCERITY", "FAULT_STANDARD", "PRINCIPLE_RELATION", "ORDER_OPPORTUNITY")

# 집행유예 가능 조건 (형법 제62조, ERD penalty_rule 비고 · API 8 suspensionRule)
SUSPENSION_MAX_PRISON_MONTHS = 36
SUSPENSION_MAX_FINE_AMOUNT = 5_000_000
SUSPENSION_MIN_MONTHS = 12
SUSPENSION_MAX_MONTHS = 60

SUMMARY_MAX_LENGTH = 100  # ERD judgment.summary varchar(100)

# AI 판결 입력에 절대 들어가면 안 되는 키 (요구사항 FR-4-1 제외 정보, REQ-041)
FORBIDDEN_INPUT_KEYS = {
    "courtJudgment",
    "courtFactors",
    "courtReasoning",
    "excerpt",
    "plainExplanation",
    "userJudgment",
    "userFactors",
    "caseNumber",
    "courtName",
    "decidedAt",
    "originalText",
}


def load_json(path):
    with open(path, encoding="utf-8") as f:
        return json.load(f)


def write_json(path, value):
    """JSON 파일 쓰기 (한글 그대로, 들여쓰기 2칸, 끝 줄바꿈). 폴더가 없으면 만든다."""
    path = Path(path)
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def read_prompt(name):
    return (PROMPT_DIR / name).read_text(encoding="utf-8")


def find_forbidden_keys(value, path="$"):
    """중첩 구조 전체에서 금지 키를 찾아 경로 목록으로 돌려준다."""
    found = []
    if isinstance(value, dict):
        for key, child in value.items():
            child_path = f"{path}.{key}"
            if key in FORBIDDEN_INPUT_KEYS:
                found.append(child_path)
            found.extend(find_forbidden_keys(child, child_path))
    elif isinstance(value, list):
        for i, child in enumerate(value):
            found.extend(find_forbidden_keys(child, f"{path}[{i}]"))
    return found


def format_months(months):
    """개월 수를 화면 표기와 같은 "N년 N개월"로 바꾼다."""
    years, rest = divmod(months, 12)
    if years and rest:
        return f"{years}년 {rest}개월"
    if years:
        return f"{years}년"
    return f"{rest}개월"


def format_won(amount):
    """원 단위 금액을 "2천만 원", "2만 5천 원"처럼 바꾼다."""
    if amount is None:
        return ""
    units = [(100_000_000, "억"), (10_000, "만")]
    parts = []
    rest = amount
    for size, name in units:
        count, rest = divmod(rest, size)
        if count:
            parts.append(f"{_format_under_10000(count)}{name}")
    if rest:
        parts.append(_format_under_10000(rest))
    return " ".join(parts) + " 원" if parts else "0 원"


def _format_under_10000(n):
    thousands, rest = divmod(n, 1000)
    text = f"{thousands}천" if thousands else ""
    return text + (str(rest) if rest else "") if (thousands or rest) else "0"


def final_penalty(judgment):
    """최종 선고 형벌: reducedTo가 있으면 그 값, 없으면 penaltyType (ERD judgment.reduced_to)."""
    return judgment.get("reducedTo") or judgment.get("penaltyType")


def format_penalty_range(rule):
    """선고할 수 있는 범위를 사람이 읽는 문구로 바꾼다 (API 6 allowedRanges.text와 같은 뜻)."""
    penalty_type = rule["penaltyType"]
    prison_range = f"징역 {format_months(rule['allowedMin'])} ~ {format_months(rule['allowedMax'])}"
    if penalty_type == "DEATH":
        return f"사형, 또는 감경 시 무기징역이나 {prison_range}"
    if penalty_type == "LIFE":
        return f"무기징역, 또는 감경 시 {prison_range}"
    if penalty_type == "PRISON":
        return prison_range
    return f"벌금 {format_won(rule['allowedMin'])} ~ {format_won(rule['allowedMax'])}"


def rules_by_type(case):
    return {rule["penaltyType"]: rule for rule in case.get("penaltyRules", [])}


def factors_by_id(case):
    return {factor["factorId"]: factor for factor in case.get("factors", [])}
