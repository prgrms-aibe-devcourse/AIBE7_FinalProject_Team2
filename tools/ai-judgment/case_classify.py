"""사건 자동 분류 — 분위기 · 쟁점 유형 · 피해자 측 · 부가 인물 · 민감 플래그 (BE-123).

대본 전체 생성(BE-121, `script_assembler.py`)이 블록을 고르는 `classification`을 만든다. 지금까지는 사람이 설정
(`stages.script.classification`)에 넣었다. 분류 값 목록은 `script_assembler.py`의 상수가 기준이다(블록 파일과 1:1).

방식은 가치관 축 투표(BE-49, `axis_vote.py`)와 같다. 사건 데이터를 같은 모델에 runs회 묻고 축마다 표를 센다.

- 기본값 = 최다표. 최다표가 요청 횟수의 과반이 아니면 그 축은 `needsReview` → 관리자가 사후에 확인한다.
- 쟁점 유형은 모델이 해당하는 것을 모두 고르고, 코드가 회차마다 **하나**로 줄인 뒤 센다. 겹치면 더 구체적인 쪽을 고른다:
  정신상태 · 방어 주장 > 고의 다툼 > 부인 > 인정(`ISSUE_PRIORITY`, BE-119 분류표 10/28 정정).
- 민감 플래그는 규칙(키워드)이나 모델 중 하나라도 잡으면 켜는 쪽이 기본이다. 다만 한두 회차만 잡은 플래그까지 모두
  켜면 회차를 늘릴수록 거짓 플래그가 쌓인다(BE-123 실측). 그래서 플래그를 둘로 나눈다:
  - **사용자 안내 대상**(취약한 피해자 · 정신건강 · 자해 · 성범죄 · 가정폭력 · 혐오): 놓치면 안내문과 가드가 모두 빠지고,
    잘못 켜면 안내문이 잘못 뜬다. 한 표라도 있으면 켜 두고 `needsReview`로 표시해 관리자가 확인한다.
  - **가드 전용**(욕설 · 국적 · 해외 · 미성년 · 약물 · 사형): 모델 표가 과반이거나 규칙이 잡을 때만 켠다. 소수 표만
    있으면 끄고 `needsReview`로 남긴다.
  규칙만 잡은 플래그는 켜 두되 `needsReview`다(규칙은 부정문 · 다른 뜻을 구분하지 못한다). 사형 · 무기형
  (`CAPITAL_PENALTY`)은 법정형 문구로만 정한다 — 모델에 묻지 않는다.

보내는 데이터: 죄명 · 사건 개요 · 섹션 · 판단 요소 문구(공개 단계 `LAW` · `SUMMARY` 제외). 판결 · 형량 · 참고 자료는
보내지 않는다. 배역 배정(내로 · 남불 중 누가 묶음 A인지)은 여기서 정하지 않는다 — 기존 사건과 견줘야 해서 적재 때
백엔드가 정한다(BE-125).
"""

import re

from axis_vote import escape_tag_data
from common import read_prompt
from script_assembler import ISSUES, MOODS, SENSITIVE_FLAGS, VICTIMS
from validate_output import parse_output

NOTICE_FLAGS = tuple(flag for flag, (_, notice) in SENSITIVE_FLAGS.items() if notice)  # 사용자에게 안내하는 플래그

ISSUE_PRIORITY = ("mental_state", "defense_claim", "intent_disputed", "denied", "admitted")
RECOVERY = ("done", "partial", "none", "unknown")
VOTED_AXES = {"mood": MOODS, "issue": ISSUE_PRIORITY, "victim": VICTIMS, "recovery": RECOVERY,
              "thirdParty": (True, False)}  # 값 순서 = 동률일 때 고르는 순서
RULE_ONLY_FLAGS = ("CAPITAL_PENALTY",)
AI_FLAGS = tuple(f for f in SENSITIVE_FLAGS if f not in RULE_ONLY_FLAGS)
HIDDEN_STAGES = ("LAW", "SUMMARY")  # 분류에 쓰지 않는 공개 단계(양형기준 · 최종 정리)

# 규칙(키워드). 규칙만 잡은 플래그는 켜진 채 대본에 쓰이므로(`needsReview`는 관리자 확인용일 뿐) **오탐이 잦은 낱말은 넣지 않고
# 모델에 맡긴다**(PR #331 jms0326 리뷰: 피고인의 "고령", "혐오감", "소년원", "교회 앞 주차장"이 플래그를 잘못 켰다).
# - 문장 단위로 보고, 부정문("…관련이 없다", "정황은 발견되지 않았다")은 건너뛴다(`NEGATION`)
# - 피해자 쪽 속성은 같은 문장에서 "피해" 가까이에 있을 때만 잡는다(`_victim_context`)
_VICTIM_TRAITS = r"아동|영아|유아|발달장애|지적장애|장애인|치매|고령|노인|미성년"


def _victim_context(traits):
    return rf"피해[^.\n]{{0,20}}(?:{traits})|(?:{traits})[^.\n]{{0,20}}피해"


RULE_KEYWORDS = {
    "VULNERABLE_VICTIM": _victim_context(_VICTIM_TRAITS),
    "MENTAL_HEALTH": r"우울증|조현병|정신질환|정신과|정신감정|심신미약|심신상실|환청|망상|치료감호|공황장애",
    "SELF_HARM": r"자살|(?<![가-힣])자해|극단적 선택|목숨을 끊|사망하려|죽으려",  # "빙자해"의 "자해"는 빼려고 앞 글자를 본다
    "PROFANITY_QUOTED": r"욕설|모욕",
    "NATIONALITY_RELIGION": r"국적|외국인|종교|예배|성당|사찰|헌금",  # "교회"는 일반 장소(교회 앞 주차장)에도 걸려 뺀다. 교회 헌금 사건은 "헌금"이 잡는다
    "OVERSEAS": r"해외|국외",
    "SEXUAL_OFFENSE": r"강간|강제추행|성폭력|성범죄|성매매|불법촬영|추행",
    "DOMESTIC_VIOLENCE": r"가정폭력|아동학대|스토킹|데이트폭력",
    "JUVENILE_DEFENDANT": r"소년법|소년부|미성년자인 피고인",  # "소년원 송치 전력"은 미성년 피고인이라는 뜻이 아니다. "10대"는 모델에 맡긴다
    "SUBSTANCE": r"마약|필로폰|대마|향정신성|마약류",
    "HATE_CRIME": r"혐오 범죄|혐오 표현|혐오를 이유|차별을 이유|인종 차별",  # "혐오감을 주는 말"은 잡지 않는다
}
NEGATION = r"없다|없었다|없는|않았다|않는다|아니다|확인되지|발견되지|인정되지|관련이 없|무관"
CAPITAL_PATTERN = r"사형|무기"


class ClassifyError(Exception):
    pass


def case_text_parts(case):
    """분류에 쓰는 사건 데이터: (섹션, 판단 요소). 공개 단계 LAW · SUMMARY는 뺀다."""
    sections = [s for s in case.get("sections", []) if s.get("stage") not in HIDDEN_STAGES and s.get("content")]
    factors = [f for f in case.get("factors", []) if f.get("revealStage") not in HIDDEN_STAGES and f.get("label")]
    return sections, factors


def build_classify_prompt(case):
    """(system, user). 값은 판결문에서 뽑은 데이터라 태그로 감싸고 꺾쇠를 이스케이프한다(axis_vote와 같은 이유)."""
    sections, factors = case_text_parts(case)
    charge = escape_tag_data(case.get("chargeName") or "-")
    lines = ["# 사건", "", f"- 죄명: <charge>{charge}</charge>", "", "## 사건 개요", "",
             "<overview>", escape_tag_data(case.get("overview") or "(없음)"), "</overview>", "", "## 사건 기록", ""]
    for s in sections:
        title = escape_tag_data(s.get("title") or "").replace('"', "&quot;")  # 속성 값이라 따옴표도 바꾼다
        lines += [f"<section title=\"{title}\">", escape_tag_data(s["content"]), "</section>"]
    lines += ["", "## 판단 요소", ""]
    lines += [f"- <factor>{escape_tag_data(f['label'])}</factor>" for f in factors]
    lines += ["", "---", "", "위 사건을 시스템 지시의 분류 축대로 분류해, 시스템 지시의 JSON 형식으로만 답한다.", ""]
    return read_prompt("classify_system.md"), "\n".join(lines)


def pick_issue(issues):
    """해당하는 쟁점 유형 목록 → 가장 구체적인 하나(ISSUE_PRIORITY)."""
    return next(issue for issue in ISSUE_PRIORITY if issue in issues)


def parse_classify_answer(text):
    """응답 → {"mood", "issue", "issues", "victim", "recovery", "thirdParty", "sensitiveFlags"}. 형식이 틀리면 ClassifyError."""
    try:
        output = parse_output(text)
    except ValueError as e:
        raise ClassifyError(f"응답을 읽을 수 없음: {e}")
    if not isinstance(output, dict):
        raise ClassifyError("JSON 객체가 아닙니다")
    errors = []
    mood, issues, victim = output.get("mood"), output.get("issues"), output.get("victimWill")
    recovery, third, flags = output.get("recovery"), output.get("thirdParty"), output.get("sensitiveFlags")
    if mood not in MOODS:
        errors.append(f"mood가 허용 값이 아닙니다: {mood}")
    if not isinstance(issues, list) or not issues or any(i not in ISSUES for i in issues):
        errors.append(f"issues는 {' · '.join(ISSUES)} 중 하나 이상인 목록입니다: {issues}")
    if victim not in VICTIMS:
        errors.append(f"victimWill이 허용 값이 아닙니다: {victim}")
    if recovery not in RECOVERY:
        errors.append(f"recovery가 허용 값이 아닙니다: {recovery}")
    if not isinstance(third, bool):
        errors.append("thirdParty는 true 또는 false입니다")
    if not isinstance(flags, list) or any(f not in AI_FLAGS for f in flags):
        errors.append(f"sensitiveFlags에 허용하지 않는 값: {flags}")
    if errors:
        raise ClassifyError(" · ".join(errors))
    return {"mood": mood, "issue": pick_issue(issues), "issues": sorted(set(issues), key=ISSUES.index),
            "victim": victim, "recovery": recovery, "thirdParty": third, "sensitiveFlags": sorted(set(flags))}


def rule_flags(case):
    """규칙으로 잡은 플래그 → 맞은 낱말 목록. 사형 · 무기형은 법정형 문구로 정한다."""
    sections, factors = case_text_parts(case)
    text = "\n".join([case.get("overview") or "", *(s["content"] for s in sections), *(f["label"] for f in factors)])
    sentences = [x for x in re.split(r"(?<=[.다])\s+|\n", text) if x.strip() and not re.search(NEGATION, x)]
    hits = {}
    for flag, pattern in RULE_KEYWORDS.items():
        found = sorted({m.group(0) for sentence in sentences for m in re.finditer(pattern, sentence)})
        if found:
            hits[flag] = found
    capital = sorted(set(re.findall(CAPITAL_PATTERN, case.get("statutoryPenaltyText") or "")))
    if capital:
        hits["CAPITAL_PENALTY"] = capital
    return hits


def _majority(values, order, quorum):
    counts = {}
    for value in values:
        counts[value] = counts.get(value, 0) + 1
    top = max(counts.values())
    chosen = next(value for value in order if counts.get(value) == top)
    return chosen, {"counts": {str(v).lower() if isinstance(v, bool) else v: counts[v] for v in order if v in counts},
                    "needsReview": top * 2 <= quorum}


def aggregate_classification(answers, rules, requested_runs=None):
    """유효 응답 목록 + 규칙 결과 → 분류 결과.

    돌려주는 값:
    {"classification": {mood, issue, victim, thirdParty, sensitiveFlags} (stages.script.classification 형식),
     "recovery", "votes": {축: {counts, needsReview}}, "flags": {플래그: {ai, rule, on, needsReview}},
     "needsReview": [확인이 필요한 축 · 플래그], "runs", "requestedRuns"}"""
    if not answers:
        raise ClassifyError("집계할 응답이 없습니다")
    runs = len(answers)
    quorum = max(runs, requested_runs or 0)
    chosen, votes = {}, {}
    for axis, order in VOTED_AXES.items():
        chosen[axis], votes[axis] = _majority([a[axis] for a in answers], order, quorum)
    flags = {}
    for flag in SENSITIVE_FLAGS:
        ai = sum(flag in a["sensitiveFlags"] for a in answers)
        rule = rules.get(flag, [])
        if not ai and not rule:
            continue
        majority = ai * 2 > quorum
        on = majority or bool(rule) or (flag in NOTICE_FLAGS and ai > 0)  # 안내 대상은 한 표라도 켠다
        sure = flag in RULE_ONLY_FLAGS or majority  # 모델 과반이 아니면(규칙만 · 소수 표만) 관리자가 확인한다
        flags[flag] = {"ai": ai, "rule": rule, "on": on, "needsReview": not sure}
    needs_review = [axis for axis, v in votes.items() if v["needsReview"]]
    needs_review += [f"sensitiveFlags.{flag}" for flag, v in flags.items() if v["needsReview"]]
    classification = {"mood": chosen["mood"], "issue": chosen["issue"], "victim": chosen["victim"],
                      "thirdParty": chosen["thirdParty"],
                      "sensitiveFlags": [flag for flag in SENSITIVE_FLAGS if flags.get(flag, {}).get("on")]}
    result = {"classification": classification, "recovery": chosen["recovery"], "votes": votes, "flags": flags,
              "needsReview": needs_review, "runs": runs}
    if requested_runs:
        result["requestedRuns"] = quorum
    return result
