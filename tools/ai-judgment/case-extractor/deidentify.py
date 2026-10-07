"""판결문 비식별화 보조: API로 보내기 전 패턴 마스킹, 받은 뒤 남은 개인정보 검사.

마스킹은 정규식으로 확실히 잡히는 값(주민등록번호, 전화번호, 사건번호, 법원명, 상세 주소 등)만 한다.
인명 · 직업 · 발언 같은 문맥이 필요한 비식별화는 모델이 하고, 결과는 residual_check와 팀 검수로 다시 본다.
"""

import re

RRN = re.compile(r"\d{6}\s?-\s?[1-8]\d{6}")
EMAIL = re.compile(r"[\w.+-]+@[\w-]+(?:\.[\w-]+)+")
PHONE = re.compile(r"(?<!\d)0\d{1,2}[-\s]?\d{3,4}[-\s]?\d{4}(?!\d)")
# 2099-01-10 같은 연도로 시작하는 짧은 날짜는 계좌번호가 아니다. 더 긴 숫자열의 일부도 잡지 않는다.
ACCOUNT = re.compile(
    r"(?<!\d)(?!(?:19|20)\d{2}-\d{1,2}-\d{1,2}(?!\d))\d{2,6}-\d{2,6}-\d{2,8}(?:-\d{1,4})?(?!\d)"
)
CASE_NUMBER = re.compile(
    r"(?:19|20)?\d{2}\s?(?:재)?(?:고합|고단|고정|고약|감고|감노|감도|전고|전노|전도|초기|노|도)\s?\d{1,7}(?!\d)"
)
SEIZURE_NUMBER = re.compile(r"(?:19|20)\d{2}\s?압\s?(?:제\s?)?\d+(?:-\d+)?\s?호?")
COURT = re.compile(r"[가-힣]{2,}(?:지방|고등|가정|행정)법원(?:\s?[가-힣]{2,}지원)?|대\s?법\s?원(?!\s?양형위원회)")
PLATE = re.compile(r"(?<!\d)\d{2,3}\s?[가-힣]\s?\d{4}(?!\d)")
ROAD_ADDRESS = re.compile(r"[가-힣\d]+(?:대로|로|길)\s?\d{1,4}(?:-\d{1,4})?(?=[\s,)]|$)")
UNIT_ADDRESS = re.compile(r"\d{1,4}동\s?\d{1,4}호")
LOT_ADDRESS = re.compile(r"[가-힣]+동\s?\d{1,5}(?:-\d{1,5})?\s?번지")

# (항목 이름, 패턴, 바꿀 값). 순서가 중요하다: 주민등록번호를 계좌 · 전화보다, 전화를 계좌보다 먼저 둔다.
MASK_RULES = [
    ("주민등록번호", RRN, "[주민등록번호]"),
    ("이메일", EMAIL, "[이메일]"),
    ("전화번호", PHONE, "[전화번호]"),
    ("계좌번호", ACCOUNT, "[계좌번호]"),
    ("사건번호", CASE_NUMBER, "[사건번호]"),
    ("압수번호", SEIZURE_NUMBER, "[압수번호]"),
    ("법원명", COURT, "[법원명]"),
    ("차량번호", PLATE, "[차량번호]"),
    ("주소", ROAD_ADDRESS, "[주소]"),
    ("주소", UNIT_ADDRESS, "[주소]"),
    ("주소", LOT_ADDRESS, "[주소]"),
]

MASK_TOKEN = re.compile(r"\[(?:주민등록번호|이메일|계좌번호|전화번호|사건번호|압수번호|법원명|차량번호|주소)\]")

# 결과 검사에서 오류로 보는 패턴 (나오면 case.json을 만들지 않는다)
RESIDUAL_ERROR_RULES = [
    ("마스킹 표시가 남음", MASK_TOKEN),
    ("주민등록번호", RRN),
    ("이메일", EMAIL),
    ("전화번호", PHONE),
    ("계좌번호", ACCOUNT),
    ("사건번호", CASE_NUMBER),
    ("법원명", re.compile(r"[가-힣]{2,}(?:지방|고등|가정|행정)법원|[가-힣]{2,}지원(?=[\s,.)]|$)")),
    ("상세 주소", re.compile(r"[가-힣]+(?:시|도)\s[가-힣]+(?:구|군)|\d{1,4}동\s?\d{1,4}호")),
    ("정확한 날짜", re.compile(r"(?:19|20)\d{2}\s?[.년]\s?\d{1,2}\s?[.월]\s?\d{1,2}")),
    ("정확한 나이", re.compile(r"(?:만\s?)?\d{1,3}\s?세(?![가-힣])")),
]

# 결과 검사에서 경고로 보는 패턴 (팀 검수에서 확인한다)
ROLE_NAME = re.compile(
    r"(피고인|피해자|증인|변호인|검사|판사|재판장|공범)\s?([가-힣]{2,4})(?=씨|은|는|이|가|을|를|의|에게|과|와|,|\s)"
)
# 역할어 뒤에 와도 사람 이름이 아닌 말
ROLE_NAME_ALLOWED = {
    "측", "측은", "측이", "본인", "자신", "가족", "유족", "들", "등", "모두", "및", "역시", "또한", "스스로",
    "에게", "으로", "으로서", "로서", "로부터", "와의", "과의", "사이", "쪽", "부부", "남편", "아내", "자녀",
    "어머니", "아버지", "주장", "진술", "명의", "소유", "집", "자택",
}


def premask(text):
    """확실한 개인정보 패턴을 표시로 바꾼다. (바꾼 텍스트, {항목: 개수})를 돌려준다."""
    counts = {}
    for name, pattern, token in MASK_RULES:
        text, n = pattern.subn(token, text)
        if n:
            counts[name] = counts.get(name, 0) + n
    return text, counts


def scrub(text):
    """residual_check 오류 패턴에 걸리는 값을 지운다. 보고서에 저장하는 자유 텍스트용이다."""
    for _, pattern in RESIDUAL_ERROR_RULES[1:]:
        text = pattern.sub("[삭제됨]", text)
    return text


def residual_check(texts):
    """비식별화 결과에 남은 개인정보를 찾는다.

    texts: [(위치, 문자열)] — 사용자에게 보일 수 있는 필드만 넣는다(개요 · 섹션 · 판단 요소).
    (오류 목록, 경고 목록)을 돌려준다. 보고서에 원본 식별자가 남지 않도록 메시지에는 위치와 규칙만 담는다.
    """
    errors, warnings = [], []
    for where, text in texts:
        if not text:
            continue
        for name, pattern in RESIDUAL_ERROR_RULES:
            for match in pattern.finditer(text):
                errors.append(f"{where}: {name} 의심")
        for match in ROLE_NAME.finditer(text):
            word = match.group(2)
            if word in ROLE_NAME_ALLOWED or any(word.startswith(a) for a in ROLE_NAME_ALLOWED):
                continue
            warnings.append(f"{where}: 실명일 수 있음")
    return errors, warnings


# 원본 판결문 정보 (case_source, BE-31). 마스킹하기 **전에** 로컬에서만 꺼내 내부 파일 · DB에만 둔다. API로 보내지 않는다
DECIDED_AT = re.compile(r"(?:판결\s?)?선\s?고\s*((?:19|20)\d{2})\s?\.\s?(\d{1,2})\s?\.\s?(\d{1,2})\s?\.")
# 사건번호의 사건 부호 → 심급 (ERD case_source.court_level)
COURT_LEVELS = (
    (re.compile(r"(?:고합|고단|고정|고약|감고|전고|초기)"), "FIRST"),
    (re.compile(r"(?:감노|전노|노)"), "APPEAL"),
    (re.compile(r"(?:감도|전도|도)"), "SUPREME"),
)


def extract_source_info(text):
    """판결문 원문에서 사건번호 · 법원명 · 선고일 · 심급을 꺼낸다. 못 찾은 값은 None (설정 파일로 직접 넣는다).

    판결문 머리(사건 · 법원 · 선고)가 앞에 오므로 각각 처음 나온 값을 쓴다.
    """
    case_number = CASE_NUMBER.search(text)
    court = COURT.search(text)
    decided = DECIDED_AT.search(text)
    number = re.sub(r"\s", "", case_number.group(0)) if case_number else None
    level = None
    if number:
        for pattern, name in COURT_LEVELS:
            if pattern.search(number):
                level = name
                break
    return {
        "caseNumber": number,
        "courtName": re.sub(r"\s", "", court.group(0)) if court else None,
        "decidedAt": (f"{decided.group(1)}-{int(decided.group(2)):02d}-{int(decided.group(3)):02d}"
                      if decided else None),
        "courtLevel": level,
    }
