"""판결 체험 대본 — 검수 조건 (BE-122, `prompts/script/README.md` "검수 조건" 표).

조립기(BE-121)가 단위마다 하는 검사는 형식(키 · 개수)과 BE-107 `validate_line`(길이 · 금지어)뿐이다. 여기서는 생성이
끝난 단위 블록(U0 ~ U4)을 README 표 전체로 다시 보고 **검사 결과 목록**을 돌려준다. 고쳐 쓰거나 다시 만들지는 않는다 —
목록을 받아 줄을 고쳐 쓰거나 단위를 다시 만드는 쪽은 호출하는 쪽이다(관리자 재생성 연결은 BE-129).

검사 결과 하나의 형식 (BE-129 · 관리자 검수 화면이 쓰는 계약, README "검수 결과 형식"):

    {"unit": "U2", "path": "/issues_narr/1", "code": "FORMAL_ENDING", "severity": "error", "fix": "line",
     "message": "해요체로 끝내야 합니다: '…'"}

- `unit`: 블록 이름 `U0` ~ `U4`.
- `path`: 블록 값 안의 위치(JSON Pointer, RFC 6901). U3는 블록 값이 증거조사 대사 목록이라 `/{순번}`이다. 블록 전체에
  걸린 결과(줄 하나를 가리킬 수 없음)는 `""`.
- `code`: 검사 종류(`CODES`). 화면 · 집계는 이 값으로 고른다 — `message`(사람이 읽는 설명)는 바뀔 수 있다.
- `severity`: `error`(이대로 내보낼 수 없다) · `warning`(사람이 확인할 것, 공개는 막지 않는다).
- `fix`: 오류를 고치는 범위. `line`(그 줄만 고쳐 쓴다) · `unit`(그 단위를 다시 만든다). 경고는 `null`.
  README "재생성 범위": 실패한 줄만 고쳐 쓰기 → 그 단위 재생성 → 대본 전체는 다시 만들지 않는다. U2는 쟁점이 모두진술 ·
  신문과 한 호출이라 단위째 다시 만들면 U4까지 어긋나므로 늘 `line`이다(BE-119).
- `factorId`: U3 결과에만 있다. 그 대사가 소개하는 판단 요소.
- 검사 자체가 돌지 못하면(`REVIEW_FAILED`) `unit` · `path`가 `""`이고 `fix`는 `null`인 결과 하나만 남는다.

휴리스틱(문장 비교)인 검사는 모두 경고다. 오류는 원문 대조 · 공개 범위 · 형식처럼 기계적으로 판단할 수 있는 것만 둔다.
"""

import json
import re

from script_generation import BANNED_PATTERNS, MAX_CHARS, MAX_LINES, _number_tokens

ERROR, WARNING = "error", "warning"
FIX_LINE, FIX_UNIT = "line", "unit"

# 검사 종류 → 심각도 (README "검수 조건" 표 순서, 뒤 둘은 BE-119 "남은 것"의 후보)
CODES = {
    "LINE_TOO_LONG": ERROR,            # 대사 100자 · 두 줄 초과
    "BANNED_WORD": ERROR,              # 세계관 금지어 (BE-107)
    "NUMBER_NOT_IN_SOURCE": ERROR,     # 입력에 없는 숫자
    "KOREAN_NUMERAL": ERROR,           # 입력의 아라비아 숫자를 한글 수사로 풀어 씀
    "FORMAL_ENDING": ERROR,            # 안내 · 해설 대사의 "~습니다 · ~입니다" 끝맺음
    "LINE_MISSING": ERROR,             # 요청한 줄이 비었음(줄 수 미달)
    "FACT_AS_CLAIM": WARNING,          # 사실로 적힌 요소를 "~라고 주장합니다"로 소개
    "EMOTION_NOT_IN_SOURCE": WARNING,  # 입력에 없는 피해자 · 유족 감정 어휘
    "ONE_SIDED": WARNING,              # 유리 · 불리 사정이 둘 다 있는데 한쪽만
    "VERDICT_LEAK": ERROR,             # 판결 정보 · 양형기준(권고형) 누출, 구형
    "ISSUE_QUESTION_MISMATCH": WARNING,  # 신문 질문이 설계도(쟁점에 맞춘 질문)와 다르거나 정신상태를 물음
    "EVIDENCE_FILLER": WARNING,        # 증거조사 둘째 줄 군더더기
    "REPEATED_LINE": WARNING,          # 같은 단위 안에서 같은 말 되풀이
    "REVIEW_FAILED": ERROR,            # 검사 자체가 돌지 못함(값 모양이 어긋남) — 검수 못 한 대본은 공개하지 않는다
}

# 해요체로 말하는 배역(안내 · 진행 해설, `script_role_guide.md` · `script_role_narrator.md`). 검사 · 변호인은 "~습니다"가 맞다
HAEYO_KINDS = ("guide", "narrator")
SPOKEN_KINDS = ("guide", "narrator", "prosecutor", "defense")

# 문장 끝의 "~니다 · ~니까"(합쇼체). 뒤에 따옴표 · 닫는 괄호 · 문장부호만 오는 경우
_FORMAL_END = re.compile(r"[가-힣](?:니다|니까)[\"'”’」』)]*[.!?…]*[\"'”’」』)]*$")

# 한글 수사 + 단위. 값이 입력의 아라비아 숫자와 같을 때만 오류로 본다(BE-119 6차 시험: 원문 횟수 · 기간을 "○○ 번" ·
# "석 달"로 풀었다). "두 사람" · "한 번 생각해 봐요"처럼 수를 옮긴 게 아닌 말까지 막지 않기 위해서다
_NATIVE_ONES = {"한": 1, "두": 2, "세": 3, "네": 4, "다섯": 5, "여섯": 6, "일곱": 7, "여덟": 8, "아홉": 9}
_NATIVE_TENS = {"열": 10, "스무": 20, "스물": 20, "서른": 30, "마흔": 40, "쉰": 50, "예순": 60, "일흔": 70, "여든": 80,
                "아흔": 90}
_SINO_DIGITS = {"일": 1, "이": 2, "삼": 3, "사": 4, "오": 5, "육": 6, "칠": 7, "팔": 8, "구": 9}
# 단위 뒤에는 조사 · 접미사("원을", "년간", "번이나")나 문장부호만 온다 — "사건"처럼 낱말의 일부인 경우를 거른다
_AFTER_UNIT = r"(?=[을를이가은는에의도과와로간째씩께부까만\s.,!?…]|$)"
_NATIVE = re.compile(
    rf"(?<![가-힣])(?P<tens>{'|'.join(_NATIVE_TENS)})?(?P<ones>{'|'.join(sorted(_NATIVE_ONES, key=len, reverse=True))})?"
    rf"\s*(?:번(?!째)|차례|명|개월|달|해|살|건|곳|군데|시간|주일|회){_AFTER_UNIT}")
_MONTH_WORD = re.compile(rf"(?<![가-힣])(?P<n>[석넉])\s*달{_AFTER_UNIT}")
_SINO = re.compile(
    r"(?<![가-힣])(?P<num>(?:[이삼사오육칠팔구]?천)?(?:[이삼사오육칠팔구]?백)?(?:[이삼사오육칠팔구]?십)?[일이삼사오육칠팔구]?)"
    rf"(?P<space>\s*)(?P<unit>만\s*원|억|원|년|개월|회|명){_AFTER_UNIT}")

# 피해자 · 유족 감정 어휘 (BE-119 7차 시험에서 데이터에 없는 "상처"가 나왔다). 입력에 같은 낱말이 있으면 쓸 수 있다
EMOTION_WORDS = ("상처", "고통", "슬픔", "슬퍼", "아픔", "괴로", "비통", "절망", "원망", "분노", "두려", "공포", "불안",
                 "눈물", "트라우마", "원통", "한스러")

# 판결 · 양형기준 낱말. 공개된 입력에 같은 낱말이 있으면(전과 사실의 "집행유예" 등) 누출이 아니다.
# "무죄"는 넣지 않는다 — 부인 사건의 변호인 입장("무죄를 주장합니다")은 판결이 아니다
LEAK_TERMS = ("권고형", "권고 범위", "권고범위", "양형기준", "감경영역", "가중영역", "기본영역", "선고형", "형량 범위", "집행유예")
# 구형 · 형량 요구 문형. U2 · U4 생성 검사(script_assembler.sentencing_errors)도 이 값을 쓰고, 여기서는 모든 단위에 건다.
# 전과 사실("징역 10개월을 선고받은 전력")은 막지 않도록 요구 문형이 뒤따르는 경우만 잡는다(PR #329 리뷰)
SENTENCING_PATTERN = r"구형|(?:징역|금고|벌금)[^.\n]{0,15}\d[^.\n]{0,15}(?:처해|처하|구합니다|구하|선고해|내려)"
SENTENCING = re.compile(SENTENCING_PATTERN)
# 형량 문형("징역 2년" · "벌금 500만 원"). 값이 권고형 · AI 판결 형량과 같고 입력에 같은 문형이 없으면 누출이다. 숫자만 비교하면
# 입력 어딘가에 같은 한 자리 숫자("2")가 있을 때 "징역 2년"(AI 판결 24개월)을 놓친다(PR #352 해송님 리뷰)
PENALTY = re.compile(r"(?:징역|금고|벌금|집행유예)\s*(?P<n>\d[\d,]*)\s*(?P<unit>년|개월|만\s*원|억\s*원|원)"
                     r"|(?P<n2>\d[\d,]*)\s*(?P<unit2>년|개월)\s*형(?![가-힣]*사)")  # "2년형" (형사 · 형사재판은 아니다)
# 판결 전에 공개되지 않는 단계 (BE-119 공개 범위)
HIDDEN_STAGES = ("LAW", "SUMMARY")

CLAIM = re.compile(r"주장(?:합니다|해요|했|한다|하고|하며|하는|하던|이에요|입니다)")
# 피고인신문에서 묻지 않는 정신상태 · 진단 · 치료 이력(쟁점 블록 `issue/mental_state.md` U0 · U2 — 답변으로 내면 · 병력을
# 말하게 된다). 블록은 정신상태 사건에만 들어가지만, 다른 쟁점의 사건에서 이런 질문이 나와도 같은 이유로 확인한다
# 병력을 가리키는 낱말만 둔다. "약을 · 복용"은 약물이 사건 수단이거나 범행 뒤 행동인 사건의 사실 질문까지 걸었다(PR #352 리뷰),
# "정신" 하나만으로는 "정신을 잃었다" 같은 사실과 겹친다
MENTAL_QUESTION = re.compile(r"정신과|정신(?:질환|병|건강|상태|감정)|진단|치료|병원|우울|조현|망상|심신|환청|상담")
# 증거조사 둘째 줄이 사실을 더하지 않고 당부만 붙이는 경우(BE-119 "남은 것": "이 사정도 함께 살펴 주시기 바랍니다")
FILLER = re.compile(r"(?:살펴|고려해|참고해|감안해|헤아려)\s*주(?:시기|시길)\s*바랍니다|살펴\s*주십시오|함께\s*살펴")

_JOSA = re.compile(r"(?:으로|에서|에게|한테|까지|부터|처럼|보다|이라는|라는|이다|였다|했다|하고|하여|해서|으며|"
                   r"은|는|이|가|을|를|에|의|와|과|도|로|만)$")


def make_issue(unit, path, code, message, *, fix=None, factor_id=None):
    """검사 결과 하나. 심각도는 `CODES`가 정하고, 경고에는 `fix`를 두지 않는다."""
    severity = CODES[code]
    issue = {"unit": unit, "path": path, "code": code, "severity": severity,
             "fix": fix if severity == ERROR else None, "message": message}
    if factor_id is not None:
        issue["factorId"] = factor_id
    return issue


def has_errors(issues):
    return any(i["severity"] == ERROR for i in issues)


# ---------------------------------------------------------------- 낱말 · 수 (휴리스틱)

def stems(text):
    """두 글자 이상 낱말에서 끝의 조사 · 어미 하나를 뗀 것. 문장끼리 겹치는지 대략 보는 데만 쓴다."""
    result = set()
    for word in re.findall(r"[가-힣A-Za-z0-9]{2,}", text or ""):
        stem = _JOSA.sub("", word)
        if len(stem) >= 2:
            result.add(stem)
    return result


def overlap(source, text):
    """`source`의 낱말 중 `text`에 나오는 비율 (0 ~ 1). `source`에 낱말이 없으면 0."""
    words = stems(source)
    if not words:
        return 0.0
    return sum(1 for w in words if w in text) / len(words)


def _sino_value(num):
    """"삼백오십" → 350. 빈 문자열이면 None."""
    if not num:
        return None
    total, digit = 0, None
    for ch in num:
        if ch in _SINO_DIGITS:
            digit = _SINO_DIGITS[ch]
        else:
            total += (digit or 1) * {"십": 10, "백": 100, "천": 1000}[ch]
            digit = None
    return total + (digit or 0)


def korean_numerals(text):
    """대사 속 한글 수사를 (쓴 말, 값)으로. 값을 정할 수 없는 말은 뺀다."""
    found = []
    for m in _NATIVE.finditer(text):
        if m.group("tens") or m.group("ones"):
            value = _NATIVE_TENS.get(m.group("tens"), 0) + _NATIVE_ONES.get(m.group("ones"), 0)
            if not (m.group("ones") == "한" and not m.group("tens")):  # "한 번"은 수를 옮긴 말이 아닌 경우가 많다
                found.append((m.group(0).strip(), value))
    for m in _MONTH_WORD.finditer(text):
        found.append((m.group(0).strip(), 3 if m.group("n") == "석" else 4))
    for m in _SINO.finditer(text):
        num = m.group("num")
        # 한자어 수 한 글자는 "오명"처럼 낱말과 겹치므로 띄어 쓴 경우만 보고, "이"는 지시어("이 세 가지", "이 일은")라 보지 않는다.
        # 같은 이유로 단위에서 "세(나이) · 일(날)"을 뺐다
        if num and (len(num) > 1 or (m.group("space") and num != "이")):
            found.append((m.group(0).strip(), _sino_value(num)))
    return found


# ---------------------------------------------------------------- 단위별 대사 목록

def _items(value, key):
    """`value[key]`가 목록이면 그 목록, 아니면 빈 목록. 형식 검사(check_*)가 보지 않는 모양(모델이 더한 키 등)에서 멈추지 않게 한다."""
    items = value.get(key) if isinstance(value, dict) else None
    return items if isinstance(items, list) else []


def _question(item):
    return item.get("q") if isinstance(item, dict) else None


def unit_lines(unit, value, strip_prefix=lambda s: s):
    """블록 값에서 검사할 줄을 (path, text, kind)로 꺼낸다. kind: guide · narrator · prosecutor · defense(화면 대사),
    design(설계도 — 화면에 그대로 나오지 않음), card(공소사실 · 쟁점 카드 글)."""
    lines = []
    if unit == "U0":
        lines += [(f"/issues/{i}", v, "design") for i, v in enumerate(_items(value, "issues")[:3])]
        lines += [(f"/questions/{i}/q", _question(v), "design") for i, v in enumerate(_items(value, "questions")[:3])]
    elif unit == "U1":
        lines += [(f"/lines/{i}", strip_prefix(v) if isinstance(v, str) else None, "guide")
                  for i, v in enumerate(_items(value, "lines")[:5])]
    elif unit == "U2":
        for key, kind in (("indict", "prosecutor"), ("plea", "defense"), ("issues_narr", "narrator")):
            lines += [(f"/{key}/{i}", v, kind) for i, v in enumerate(_items(value, key)[:2])]
        lines += [(f"/questions/{i}/q", _question(v), "prosecutor") for i, v in enumerate(_items(value, "questions")[:2])]
        cards = value.get("cards")
        for card, body in (cards.items() if isinstance(cards, dict) else ()):
            if not isinstance(body, dict):  # check_opening은 indict · plea · issues만 본다 — 모델이 더한 키는 건너뛴다
                continue
            for key, item in body.items():
                if isinstance(item, list):
                    lines += [(f"/cards/{card}/{key}/{i}", v, "card") for i, v in enumerate(item) if isinstance(v, str)]
                elif isinstance(item, str):
                    lines.append((f"/cards/{card}/{key}", item, "card"))
    elif unit == "U4":
        lines += [("/prosecutor", value.get("prosecutor"), "prosecutor"), ("/defense", value.get("defense"), "defense")]
    return lines


def sentences(text):
    """문장 단위로 쪼갠다(문장부호 · 줄바꿈 기준). 섹션 본문 전체와 대사를 비교하면 겹침 비율이 낮아져서다."""
    return [s.strip() for s in re.split(r"(?<=[.!?…])\s+|\n+", text or "") if s.strip()]


# ---------------------------------------------------------------- 줄 검사

def line_issues(unit, path, text, kind, ctx, *, factor=None):
    """줄 하나의 검사 결과. `ctx`: 그 단위가 받은 입력 `source`, 공개 전 정보 `hidden`, 누출로 볼 숫자 `leak_numbers`,
    사실로 적힌 요소 `facts` · 주장으로 적힌 요소 `claims`(FACT_AS_CLAIM 판단용)."""
    factor_id = factor.get("factorId") if factor else None

    def issue(code, message):
        return make_issue(unit, path, code, message, fix=FIX_LINE, factor_id=factor_id)

    source, issues = ctx["source"], []
    if kind in SPOKEN_KINDS:
        rows = [row for row in text.strip().splitlines() if row.strip()]
        chars = sum(len(row) for row in rows)
        if len(rows) > MAX_LINES or chars > MAX_CHARS:
            issues.append(issue("LINE_TOO_LONG", f"{len(rows)}줄 · {chars}자입니다 (최대 {MAX_LINES}줄 · {MAX_CHARS}자)"))
    issues += [issue("BANNED_WORD", f"금지어 포함: {word}") for word, pattern in BANNED_PATTERNS.items()
               if re.search(pattern, text)]

    # 형량 문형: 값이 권고형 · AI 판결 형량과 같으면, 입력에 같은 숫자가 있어도 누출이다(입력에 같은 문형이 있을 때만 통과)
    flat = lambda t: re.sub(r"[\s,]", "", t or "")  # noqa: E731
    leaked_numbers = set()
    for m in PENALTY.finditer(text):
        raw = m.group("n") or m.group("n2")
        n = int(raw.replace(",", ""))
        unit_name = re.sub(r"\s", "", m.group("unit") or m.group("unit2"))
        months = {"년": n * 12, "개월": n}.get(unit_name)
        won = {"만원": n * 10_000, "억원": n * 100_000_000, "원": n}.get(unit_name)
        hit = months in ctx["leak_months"] if months is not None else won in ctx["leak_won"]
        if hit and flat(m.group(0)) not in flat(source):
            issues.append(issue("VERDICT_LEAK", f"판결 · 권고형과 같은 형량 '{m.group(0)}'가 들어갔습니다(판결 입력 전 앵커링)"))
            leaked_numbers.add(raw.replace(",", ""))

    # 숫자: 입력에 없는 숫자. 공개 전 정보(양형기준 · 정리 · 판결 형량)의 숫자면 누출로 본다
    known = _number_tokens(source)
    hidden_numbers = ctx["leak_numbers"] | _number_tokens(ctx["hidden"])
    for number in sorted(_number_tokens(text) - known - leaked_numbers):
        if number in hidden_numbers:
            issues.append(issue("VERDICT_LEAK", f"공개 전 정보(양형기준 · 판결)의 숫자 '{number}'가 들어갔습니다"))
        else:
            issues.append(issue("NUMBER_NOT_IN_SOURCE", f"입력에 없는 숫자 '{number}'가 들어갔습니다"))
    for word, value in korean_numerals(text):
        if value is not None and str(value) in known:
            issues.append(issue("KOREAN_NUMERAL", f"입력의 숫자 {value}를 한글로 풀어 썼습니다: '{word}' — 원문 표기 그대로 씁니다"))

    if kind in HAEYO_KINDS:
        formal = [s for s in re.split(r"(?<=[.!?…])\s+", text.strip()) if _FORMAL_END.search(s.strip())]
        if formal:
            issues.append(issue("FORMAL_ENDING", f"해요체로 끝내야 합니다: '{formal[0].strip()}'"))

    if SENTENCING.search(text):
        issues.append(issue("VERDICT_LEAK", "구형 · 형량이 들어갔습니다(판결 입력 전 앵커링)"))
    leaked = [term for term in LEAK_TERMS if term in text and term not in source]
    if leaked:
        issues.append(issue("VERDICT_LEAK", f"공개 전 정보의 낱말 '{leaked[0]}'가 들어갔습니다"))

    if kind != "design":
        if CLAIM.search(text) and _is_fact_claim(text, ctx, factor, indictment=unit == "U2" and path.startswith("/indict/")):
            issues.append(issue("FACT_AS_CLAIM", "사실로 적힌 내용을 주장 문형으로 소개했습니다 — 사실은 주장으로 낮추지 않습니다"))
        emotions = [w for w in EMOTION_WORDS if w in text and w not in source]
        if emotions:
            issues.append(issue("EMOTION_NOT_IN_SOURCE", f"입력에 없는 감정 어휘: {', '.join(emotions)} — 데이터에 있을 때만 씁니다"))
    return issues


def _is_fact_claim(text, ctx, factor, *, indictment=False):
    if factor is not None:  # U3: 한 줄이 판단 요소 하나를 소개하고, 2막 증거는 모두 개요 · 상세 사실이다
        return factor.get("revealStage") in ("OVERVIEW", "DETAIL")
    claims = [c for c in ctx["claims"] if overlap(c, text) >= 0.6]
    if indictment:
        # 공소사실은 검사가 사실로 말하는 줄이다. 피고인 주장을 옮긴 게 아니면 "주장" 문형 자체가 사실을 낮춘 것이다 —
        # 요소 라벨과 낱말 겹침으로는 바꿔 말한 공소사실을 놓쳤다(PR #352 해송님 리뷰)
        return not claims
    facts = [f for f in ctx["facts"] if overlap(f, text) >= 0.6]
    return bool(facts) and not claims


def repeated_issues(unit, lines):
    """같은 단위 안에서 앞 대사와 낱말이 80% 이상 겹치는(짧은 쪽 기준) 대사가 다시 나오면 뒤 줄에 경고. 짧은 쪽 기준이라
    앞 대사를 그대로 되풀이하고 말을 덧붙인 경우도 잡는다. 낱말이 셋 미만인 짧은 대사는 보지 않는다."""
    issues, seen = [], []
    for path, text, kind in lines:
        if kind not in SPOKEN_KINDS or not isinstance(text, str) or not text.strip():
            continue
        words = stems(text)
        for earlier_path, earlier in seen:
            smaller = min(len(words), len(earlier))
            if smaller >= 3 and len(words & earlier) / smaller >= 0.8:
                issues.append(make_issue(unit, path, "REPEATED_LINE", f"{earlier_path}와 거의 같은 말입니다"))
                break
        seen.append((path, words))
    return issues


# ---------------------------------------------------------------- 입력 (단위가 받은 것)

def _leak_numbers(case, judgment_output):
    """권고형 · AI 판결 형량의 숫자(개월, 12로 나누어지면 년도). 공개된 입력에 같은 숫자가 없으면 대사에 나오는 것 자체가 누출이다."""
    numbers = set()
    months = [(case.get("recommended") or {}).get(k) for k in ("minMonths", "maxMonths")]
    months += [(judgment_output or {}).get(k) for k in ("prisonMonths", "suspensionMonths")]
    for m in months:
        if isinstance(m, int) and m > 0:
            numbers.add(str(m))
            if m % 12 == 0:
                numbers.add(str(m // 12))
    fine = (judgment_output or {}).get("fineAmount")
    if isinstance(fine, int) and fine > 0:
        numbers.add(str(fine))
    return numbers


def _leak_amounts(case, judgment_output):
    """(형량 개월 수 집합, 벌금 원 집합). 권고형 상 · 하한과 AI 판결의 징역 · 집행유예 · 벌금."""
    months = [(case.get("recommended") or {}).get(k) for k in ("minMonths", "maxMonths")]
    months += [(judgment_output or {}).get(k) for k in ("prisonMonths", "suspensionMonths")]
    fine = (judgment_output or {}).get("fineAmount")
    return ({m for m in months if isinstance(m, int) and m > 0},
            {fine} if isinstance(fine, int) and fine > 0 else set())


def build_context(case, judgment_output, source, stages, *, extra_claims=()):
    """단위 하나의 검사 입력. `source`는 그 단위가 실제로 받은 데이터(문자열), `stages`는 그 단위에 공개된 단계.
    주장(`claims`)은 문장 단위로 둔다 — 섹션 본문 전체와 비교하면 겹침 비율이 낮아 주장 소개를 사실로 잘못 본다(PR #352 리뷰)."""
    hidden = [s.get("content") or "" for s in case.get("sections", []) if s.get("stage") in HIDDEN_STAGES]
    hidden += [f.get("label") or "" for f in case.get("factors", []) if f.get("revealStage") in HIDDEN_STAGES]
    public = [f for f in case.get("factors", []) if f.get("revealStage") in stages and f.get("label")]
    claims = [f["label"] for f in public if f.get("revealStage") == "ARGUMENT"]
    if "ARGUMENT" in stages:
        for section in case.get("sections", []):
            if section.get("stage") == "ARGUMENT":
                claims += sentences(section.get("content"))
    leak_months, leak_won = _leak_amounts(case, judgment_output)
    return {
        "source": source, "hidden": " ".join(hidden), "leak_numbers": _leak_numbers(case, judgment_output),
        "leak_months": leak_months, "leak_won": leak_won,
        "facts": [f["label"] for f in public if f.get("revealStage") in ("OVERVIEW", "DETAIL")],
        "claims": claims + list(extra_claims),
    }


# ---------------------------------------------------------------- 단위 검사

def review_unit(unit, value, ctx, *, strip_prefix=lambda s: s):
    """U0 · U1 · U2 · U4 블록 하나의 검사 결과."""
    lines = unit_lines(unit, value, strip_prefix)
    issues = []
    for path, text, kind in lines:
        if not isinstance(text, str) or not text.strip():
            # 형식 검사(check_*)를 통과한 뒤라 여기 오는 것은 번호 · 화자만 있던 줄이다. U0 · U2는 다른 단위가 기대므로 줄만 고친다
            if kind != "card":
                issues.append(make_issue(unit, path, "LINE_MISSING", "줄이 비었습니다",
                                         fix=FIX_LINE if unit in ("U0", "U2") else FIX_UNIT))
            continue
        issues += line_issues(unit, path, text, kind, ctx)
    return issues + repeated_issues(unit, lines)


def review_evidence(evidence, case, judgment_output):
    """U3 증거조사 대사 목록의 검사 결과. 입력은 BE-107 scene_data와 같게 제목 · 개요 · 그 판단 요소다."""
    factors = {f["factorId"]: f for f in case.get("factors", []) if "factorId" in f}
    issues, lines = [], []
    for i, line in enumerate(evidence):
        factor = factors.get(line.get("factorId"), {"factorId": line.get("factorId")})
        path, text = f"/{i}", line.get("text")
        if not text:
            # 끝내 만들지 못한 요소. U3는 요소마다 따로 부르므로 그 줄만 다시 만든다
            issues.append(make_issue("U3", path, "LINE_MISSING", "대사를 만들지 못했습니다", fix=FIX_LINE,
                                     factor_id=factor.get("factorId")))
            continue
        kind = "prosecutor" if line.get("role") == "prosecutor" else "defense"
        source = " ".join(str(v) for v in (case.get("title"), case.get("overview"), factor.get("label")) if v)
        issues += line_issues("U3", path, text, kind, build_context(case, judgment_output, source, ()), factor=factor)
        rows = [row for row in text.strip().splitlines() if row.strip()]
        if len(rows) > 1 and FILLER.search(rows[-1]):
            issues.append(make_issue("U3", path, "EVIDENCE_FILLER", "둘째 줄이 사실 없이 당부만 덧붙입니다",
                                     factor_id=factor.get("factorId")))
        lines.append((path, text, kind))
    return issues + repeated_issues("U3", lines)


def one_sided_issues(value, case, judgment_output, strip_prefix=lambda s: s):
    """U1(1막 해설): 기사에 실리는 개요(OVERVIEW) 사정 중 불리(UP) · 유리(DOWN)가 모두 있는데 해설이 한쪽만 짚었으면 경고
    (BE-119 9 · 10차 시험). 어느 사정을 짚었는지는 낱말 겹침으로 본다. 어느 쪽도 짚지 않았으면 판단하지 않는다."""
    directions = {f.get("factorId"): f.get("direction") for f in (judgment_output or {}).get("factors", [])}
    sides = {"UP": [], "DOWN": []}
    for f in case.get("factors", []):
        direction = directions.get(f.get("factorId"))
        if f.get("revealStage") == "OVERVIEW" and direction in sides and f.get("label"):
            sides[direction].append(f["label"])
    if not sides["UP"] or not sides["DOWN"]:
        return []
    text = " ".join(strip_prefix(v) for v in _items(value, "lines") if isinstance(v, str))
    mentioned = {side for side, labels in sides.items() if any(overlap(label, text) >= 0.5 for label in labels)}
    if len(mentioned) == 1:
        missing = "유리한(감경)" if "UP" in mentioned else "불리한(가중)"
        return [make_issue("U1", "", "ONE_SIDED", f"공개된 사정 중 {missing} 사정을 전하지 않았습니다")]
    return []


def issue_question_issues(design, opening):
    """U2 피고인신문 질문 검사. 쟁점과 질문의 뜻이 맞는지는 낱말로 판단할 수 없어(질문은 쟁점 낱말을 되풀이하지 않는다)
    두 가지만 본다. 뜻의 대응은 관리자가 검수 화면에서 본다.

    - 설계도(U0)가 쟁점에 맞춰 정한 앞 두 질문을 U2가 그대로 옮겼는가 — U2 지시는 "design.questions의 앞 두 개만 쓴다"다.
      옮기지 않으면 쟁점과 이어진 질문이 아니게 된다.
    - 정신상태 · 진단 · 치료 이력을 묻는가 — 쟁점 블록이 막는 질문이다(BE-119, BE-121 시험)."""
    planned = [_question(qa) for qa in _items(design, "questions")[:2]]
    result = []
    for i, qa in enumerate(_items(opening, "questions")[:2]):
        question = _question(qa)
        if not isinstance(question, str):
            continue
        path = f"/questions/{i}/q"
        if i < len(planned) and isinstance(planned[i], str) and overlap(planned[i], question) < 0.5:
            result.append(make_issue("U2", path, "ISSUE_QUESTION_MISMATCH",
                                     f"설계도가 쟁점에 맞춰 정한 질문과 다릅니다(설계도: '{planned[i]}')"))
        elif MENTAL_QUESTION.search(question):
            result.append(make_issue("U2", path, "ISSUE_QUESTION_MISMATCH",
                                     "정신상태 · 진단 · 치료 이력을 묻는 질문입니다 — 신문은 범행 경위 · 범행 뒤 행동 같은 사실만 묻습니다"))
    return result


def review_failed(error):
    """검사 자체가 돌지 못했을 때 남기는 결과 하나. 오류라 대본은 공개할 수 없다 — 검수를 못 한 대본을 내보내지 않는다."""
    return {"unit": "", "path": "", "code": "REVIEW_FAILED", "severity": CODES["REVIEW_FAILED"], "fix": None,
            "message": f"검수를 돌리지 못했습니다({type(error).__name__}: {error}) — 값 모양을 확인한 뒤 다시 검사합니다"}


def unit_context(unit, case, judgment_output, generated):
    """U0 · U1 · U2 · U4 검사 입력(`build_context`). 단위가 실제로 받은 데이터와 비교한다 — 검사(review_script)와 줄 고쳐 쓰기
    (script_repair, BE-122 2차)가 같은 기준을 쓴다."""
    from script_assembler import UNIT_STAGES, public_case  # script_assembler가 이 모듈을 쓰므로 여기서 읽는다

    if unit == "U1":
        # U1은 기사만 받는다. 기사는 개요(OVERVIEW)로 만들고, 기사에 "주장"으로 실린 문장은 해설도 주장으로 옮겨야 맞다
        paper = generated.get("paper") or {}
        # 제목 · 부제는 문장부호 없이 끝나므로 칸(문단)마다 따로 쪼갠다
        pieces = [v for v in paper.values() if isinstance(v, str)]
        pieces += [p for v in paper.values() if isinstance(v, list) for p in v if isinstance(p, str)]
        claims = [s for piece in pieces for s in sentences(piece) if "주장" in s]
        return build_context(case, judgment_output, json.dumps(paper, ensure_ascii=False), ("OVERVIEW",),
                             extra_claims=claims)
    stages = UNIT_STAGES[unit]
    return build_context(case, judgment_output, json.dumps(public_case(case, stages), ensure_ascii=False), stages)


def review_script(case, judgment_output, generated, *, strip_prefix=lambda s: s):
    """생성 결과(`script_assembler.generate_units`의 반환값) 전체를 검사한다. 만들지 못한 단위(값 없음)는 건너뛴다 —
    그건 `pending`이 이미 알린다. 돌려주는 값: 검사 결과 목록(위 형식), 단위 순서(U0 → U4)."""
    units = generated.get("units", {})
    value = lambda unit: (units.get(unit) or {}).get("value")  # noqa: E731
    issues = []
    for unit in ("U0", "U1", "U2"):
        if value(unit):
            issues += review_unit(unit, value(unit), unit_context(unit, case, judgment_output, generated),
                                  strip_prefix=strip_prefix if unit == "U1" else (lambda s: s))
    if value("U1"):
        issues += one_sided_issues(value("U1"), case, judgment_output, strip_prefix)
    if value("U2"):
        issues += issue_question_issues(value("U0"), value("U2"))
    issues += review_evidence(generated.get("evidence", []), case, judgment_output)
    if value("U4"):
        issues += review_unit("U4", value("U4"), unit_context("U4", case, judgment_output, generated))
    return _in_unit_order(issues)


def _in_unit_order(issues):
    """단위 순서(U0 → U4)로. 같은 단위 안에서는 검사한 순서를 지킨다."""
    order = {unit: i for i, unit in enumerate(("U0", "U1", "U2", "U3", "U4"))}
    return sorted(issues, key=lambda issue: order.get(issue["unit"], len(order)))
