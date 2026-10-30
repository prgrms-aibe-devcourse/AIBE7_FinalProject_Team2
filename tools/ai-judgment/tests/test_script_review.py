"""판결 체험 대본 — 검수 조건 (BE-122) 테스트. 모델은 호출하지 않는다.

실행: tools/ai-judgment에서 `python3 -m unittest discover tests`
"""

import sys
import unittest
from pathlib import Path

TOOL_DIR = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(TOOL_DIR))
sys.path.insert(0, str(Path(__file__).resolve().parent))

import script_assembler as sa  # noqa: E402
import script_review as sr  # noqa: E402
from test_script_assembler import (CASE, CAST, CLOSING, CLS, DESIGN, EVIDENCE, INTRO, JUDGMENT_OUTPUT,  # noqa: E402
                                   OPENING, PAPER, FakeCaller, answers)

CASE_WITH_NUMBERS = {
    **CASE,
    "overview": "피고인은 3년 동안 12번 돈을 빌렸고, 그중 500만 원을 갚지 않았다.",
    "factors": [*CASE["factors"], {"factorId": 20, "label": "피해자에게 500만 원을 갚지 않았다", "revealStage": "OVERVIEW"}],
}


def generated(units=None, evidence=None, paper=PAPER):
    """generate_units 반환값 모양. units는 {단위: 값}."""
    values = {"U0": DESIGN, "U1": INTRO, "U2": OPENING, "U4": CLOSING, **(units or {})}
    lines = evidence if evidence is not None else [
        {"factorId": f, "role": r, "text": t} for f, r, t in zip((2, 3, 7), ("prosecutor", "prosecutor", "defense"), EVIDENCE)]
    return {"units": {u: {"value": v} for u, v in values.items()}, "paper": paper, "evidence": lines,
            "stopped": False, "pending": []}


def review(case=CASE, judgment=JUDGMENT_OUTPUT, **kwargs):
    return sr.review_script(case, judgment, generated(**kwargs), strip_prefix=sa.strip_line_prefix)


def codes(issues, severity=None):
    return [(i["unit"], i["path"], i["code"]) for i in issues if severity is None or i["severity"] == severity]


class FormatTest(unittest.TestCase):
    def test_issue_shape_is_the_contract(self):
        """BE-129 · 관리자 화면이 읽는 형식: unit · path(JSON Pointer) · code · severity · fix · message (U3는 factorId)."""
        issues = review(units={"U1": {"lines": [*INTRO["lines"][:4], "서두르지 말고 생각해 보시기 바랍니다."]}})
        self.assertEqual(issues, [{"unit": "U1", "path": "/lines/4", "code": "FORMAL_ENDING", "severity": "error",
                                   "fix": "line", "message": "해요체로 끝내야 합니다: '서두르지 말고 생각해 보시기 바랍니다.'"}])
        bad_evidence = [{"factorId": 2, "role": "prosecutor", "text": None}]
        issue = review(evidence=bad_evidence)[0]
        self.assertEqual({k: issue[k] for k in ("unit", "path", "code", "fix", "factorId")},
                         {"unit": "U3", "path": "/0", "code": "LINE_MISSING", "fix": "line", "factorId": 2})

    def test_every_code_has_a_severity_and_warnings_have_no_fix(self):
        self.assertTrue(set(sr.CODES.values()) <= {sr.ERROR, sr.WARNING})
        warning = sr.make_issue("U1", "", "ONE_SIDED", "…", fix=sr.FIX_LINE)
        self.assertIsNone(warning["fix"])
        self.assertFalse(sr.has_errors([warning]))

    def test_fixture_script_has_no_errors(self):
        """정상 대본(test_script_assembler 고정 값)은 오류가 없다 — 검사가 정상 대사를 막지 않는다."""
        self.assertEqual(codes(review(), sr.ERROR), [])


class LineCheckTest(unittest.TestCase):
    def test_length(self):
        issues = review(units={"U4": {"prosecutor": "검사 측 의견입니다. " + "가" * 100, "defense": CLOSING["defense"]}})
        self.assertIn(("U4", "/prosecutor", "LINE_TOO_LONG"), codes(issues))
        three_rows = {**OPENING, "plea": ["입장입니다.\n둘째 줄입니다.\n셋째 줄입니다.", OPENING["plea"][1]]}
        self.assertIn(("U2", "/plea/0", "LINE_TOO_LONG"), codes(review(units={"U2": three_rows})))

    def test_number_not_in_source_and_leak(self):
        """입력에 없는 숫자는 오류. 권고형 · AI 판결 형량 · 양형기준 섹션의 숫자면 누출로 본다."""
        made_up = {**CLOSING, "defense": "변호인 측 최종변론입니다. 피고인은 3번이나 사과했습니다."}
        self.assertIn(("U4", "/defense", "NUMBER_NOT_IN_SOURCE"), codes(review(units={"U4": made_up})))
        # 권고 범위 84 ~ 144개월(7 ~ 12년)
        leak = {**OPENING, "issues_narr": ["쟁점은 7년 안팎의 형이 맞는지예요.", OPENING["issues_narr"][1]]}
        self.assertIn(("U2", "/issues_narr/0", "VERDICT_LEAK"), codes(review(units={"U2": leak})))
        judged = {**JUDGMENT_OUTPUT, "prisonMonths": 150}
        leak = {**CLOSING, "prosecutor": "검사 측 의견입니다. 150개월은 되어야 합니다."}
        self.assertIn(("U4", "/prosecutor", "VERDICT_LEAK"), codes(review(judgment=judged, units={"U4": leak})))

    def test_numbers_from_source_pass(self):
        ok = {**CLOSING, "prosecutor": "검사 측 의견입니다. 피고인은 500만 원을 갚지 않았습니다."}
        self.assertEqual(codes(review(case=CASE_WITH_NUMBERS, units={"U4": ok}), sr.ERROR), [])

    def test_korean_numeral_only_when_it_spells_a_source_number(self):
        """원문 숫자를 한글로 풀었을 때만 오류(BE-119 6차 시험). 수를 옮긴 게 아닌 말("한 번", "두 사람", "사건")은 막지 않는다."""
        for text, word in (("피고인은 열두 번 돈을 빌렸습니다.", "열두 번"), ("삼 년 동안 빌렸습니다.", "삼 년"),
                           ("오백만 원을 갚지 않았습니다.", "오백만 원")):
            line = {**CLOSING, "prosecutor": "검사 측 의견입니다. " + text}
            issues = review(case=CASE_WITH_NUMBERS, units={"U4": line})
            self.assertIn(("U4", "/prosecutor", "KOREAN_NUMERAL"), codes(issues), text)
            self.assertIn(word, next(i["message"] for i in issues if i["code"] == "KOREAN_NUMERAL"))
        for text in ("한 번 더 살펴 주십시오.", "두 사람은 오래 알고 지냈습니다.", "이 사건의 경위를 보십시오.",
                     "두 번째 사정입니다.", "다섯 번 찾아갔습니다."):  # 5는 입력에 없다 — 숫자를 옮긴 것이 아니다
            line = {**CLOSING, "prosecutor": "검사 측 의견입니다. " + text}
            self.assertNotIn("KOREAN_NUMERAL", [c for _, _, c in codes(review(case=CASE_WITH_NUMBERS, units={"U4": line}))],
                             text)

    def test_korean_numeral_values(self):
        self.assertEqual(sr.korean_numerals("석 달 뒤"), [("석 달", 3)])
        self.assertEqual(sr.korean_numerals("스물다섯 살"), [("스물다섯 살", 25)])
        self.assertEqual(sr.korean_numerals("삼백오십만 원"), [("삼백오십만 원", 350)])
        self.assertEqual(sr.korean_numerals("사건이 오명을 남겼다"), [])
        # 사건 1 수작업 대본의 "이 세 가지"(지시어 + 수)는 수를 옮긴 말이 아니다
        self.assertEqual(sr.korean_numerals("이 세 가지를 눈여겨보세요. 이 일은 이 명의로 했다."), [])

    def test_formal_ending_only_for_guide_and_narrator(self):
        """안내(U1) · 진행 해설(U2 issues_narr)만 해요체. 검사 · 변호인 대사의 "~습니다"는 맞다."""
        narr = {**OPENING, "issues_narr": ["쟁점은 범행 경위입니다.", "그러니까 경위를 눈여겨보세요."]}
        self.assertEqual(codes(review(units={"U2": narr}), sr.ERROR), [("U2", "/issues_narr/0", "FORMAL_ENDING")])
        self.assertEqual(codes(review(units={"U4": CLOSING}), sr.ERROR), [])  # "…살펴 주십시오." 등 검사 · 변호인
        question = {**INTRO, "lines": [*INTRO["lines"][:4], "어떻게 생각합니까?"]}
        self.assertIn(("U1", "/lines/4", "FORMAL_ENDING"), codes(review(units={"U1": question})))

    def test_line_missing_after_prefix(self):
        """번호 · 화자만 있던 줄은 비었다. U1은 단위를 다시 만들고(다른 단위와 연결이 약함), U2는 줄만 고친다(BE-119)."""
        empty_intro = {"lines": [*INTRO["lines"][:4], "⑤남불:"]}
        issue = next(i for i in review(units={"U1": empty_intro}) if i["code"] == "LINE_MISSING")
        self.assertEqual((issue["path"], issue["fix"]), ("/lines/4", "unit"))
        empty_plea = {**OPENING, "plea": [" ", OPENING["plea"][1]]}
        issue = next(i for i in review(units={"U2": empty_plea}) if i["code"] == "LINE_MISSING")
        self.assertEqual((issue["path"], issue["fix"]), ("/plea/0", "line"))

    def test_sentencing_and_leak_terms(self):
        demand = {**OPENING, "plea": ["피고인은 공소사실을 인정합니다.", "집행유예를 선고해 주십시오."]}
        self.assertIn(("U2", "/plea/1", "VERDICT_LEAK"), codes(review(units={"U2": demand})))
        guide = {**INTRO, "lines": [*INTRO["lines"][:4], "양형기준도 함께 생각해 봐요."]}
        self.assertIn(("U1", "/lines/4", "VERDICT_LEAK"), codes(review(units={"U1": guide})))
        # 입력에 있는 낱말(전과 사실의 집행유예)과 부인 사건의 "무죄 주장"은 누출이 아니다
        case = {**CASE, "overview": CASE["overview"] + " 집행유예 기간 중이었다."}
        ok = {**CLOSING, "defense": "변호인 측 최종변론입니다. 집행유예 기간이었던 점은 다투지 않습니다."}
        self.assertEqual(codes(review(case=case, units={"U4": ok}), sr.ERROR), [])
        denied = {**CLOSING, "defense": "변호인 측 최종변론입니다. 피고인은 무죄를 주장합니다."}
        self.assertEqual(codes(review(units={"U4": denied}), sr.ERROR), [])

    def test_banned_word(self):
        line = {**CLOSING, "defense": "변호인 측 최종변론입니다. 정령이 지켜봅니다."}
        self.assertIn(("U4", "/defense", "BANNED_WORD"), codes(review(units={"U4": line})))


class WarningCheckTest(unittest.TestCase):
    def test_fact_as_claim(self):
        """사실로 적힌 요소(개요 · 상세)를 주장으로 낮추면 경고. 주장 단계(ARGUMENT) 내용은 주장 문형이 맞다."""
        evidence = [{"factorId": 2, "role": "prosecutor", "text": "검사 측 증거입니다. 흉기를 집어 들었다고 주장합니다."}]
        self.assertIn(("U3", "/0", "FACT_AS_CLAIM"), codes(review(evidence=evidence), sr.WARNING))
        fact = {**OPENING, "plea": ["피고인은 다투던 중 집에 있던 흉기를 집어 들었다고 주장합니다.", OPENING["plea"][1]]}
        self.assertIn(("U2", "/plea/0", "FACT_AS_CLAIM"), codes(review(units={"U2": fact})))
        claim = {**OPENING, "plea": ["피고인은 우발적 범행이라고 주장합니다.", OPENING["plea"][1]]}
        self.assertNotIn("FACT_AS_CLAIM", [c for _, _, c in codes(review(units={"U2": claim}))])

    def test_emotion_not_in_source(self):
        line = {**CLOSING, "prosecutor": "검사 측 의견입니다. 유족이 받은 상처가 큽니다."}
        issues = review(units={"U4": line})
        self.assertIn(("U4", "/prosecutor", "EMOTION_NOT_IN_SOURCE"), codes(issues, sr.WARNING))
        case = {**CASE, "overview": CASE["overview"] + " 유족은 깊은 상처를 호소했다."}
        self.assertEqual(codes(review(case=case, units={"U4": line})), [])

    def test_one_sided_intro(self):
        """기사에 실리는 개요 사정에 불리(UP) · 유리(DOWN)가 다 있는데 1막 해설이 한쪽만 짚으면 경고(BE-119 9차 시험)."""
        case = {**CASE, "factors": [*CASE["factors"],
                                    {"factorId": 30, "label": "피해자 유족과 합의했다", "revealStage": "OVERVIEW"}]}
        judged = {"factors": [*JUDGMENT_OUTPUT["factors"], {"factorId": 30, "direction": "DOWN"}]}
        one_side = {"lines": ["기사 제목부터 봐요.", "다투던 중 집에 있던 흉기를 집어 들었대요.", "셋째 줄이에요.",
                              "넷째 줄이에요.", "서두르지 말고 생각해 봐요."]}
        self.assertIn(("U1", "", "ONE_SIDED"), codes(review(case=case, judgment=judged, units={"U1": one_side})))
        both = {"lines": [*one_side["lines"][:2], "피해자 유족과 합의했대요.", *one_side["lines"][3:]]}
        self.assertNotIn("ONE_SIDED", [c for _, _, c in codes(review(case=case, judgment=judged, units={"U1": both}))])
        self.assertNotIn("ONE_SIDED", [c for _, _, c in codes(review(case=case, judgment=judged))])  # 어느 쪽도 짚지 않음

    def test_issue_question_mismatch(self):
        """U2 질문은 설계도가 쟁점에 맞춰 정한 앞 두 질문이어야 하고, 정신상태 · 진단 · 치료 이력은 묻지 않는다."""
        self.assertNotIn("ISSUE_QUESTION_MISMATCH", [c for _, _, c in codes(review())])  # 설계도 질문 그대로
        drifted = {**OPENING, "questions": [{"q": "아이를 혼자 키웠습니까?", "a": "예"}, OPENING["questions"][1]]}
        self.assertEqual(codes(review(units={"U2": drifted})), [("U2", "/questions/0/q", "ISSUE_QUESTION_MISMATCH")])
        design = {**DESIGN, "questions": [{"q": "범행 전에 우울증 치료를 받았습니까?", "a": "예"}, *DESIGN["questions"][1:]]}
        mental = {**OPENING, "questions": [{"q": "범행 전에 우울증 치료를 받았습니까?", "a": "예"}, OPENING["questions"][1]]}
        issues = review(units={"U0": design, "U2": mental})
        self.assertEqual(codes(issues), [("U2", "/questions/0/q", "ISSUE_QUESTION_MISMATCH")])
        self.assertIn("정신상태", issues[0]["message"])

    def test_evidence_filler_and_repeat(self):
        evidence = [{"factorId": 2, "role": "prosecutor", "text": "검사 측 증거입니다. 흉기를 집어 들었습니다.\n이 사정도 함께 살펴 주시기 바랍니다."},
                    {"factorId": 3, "role": "prosecutor", "text": "검사 측 증거입니다. 흉기를 집어 들었습니다."}]
        found = codes(review(evidence=evidence), sr.WARNING)
        self.assertIn(("U3", "/0", "EVIDENCE_FILLER"), found)
        self.assertIn(("U3", "/1", "REPEATED_LINE"), found)


class AssemblerIntegrationTest(unittest.TestCase):
    def test_review_errors_block_publishing(self):
        """생성이 끝나면 검수 결과가 대본(script.json)의 review로 남고, 오류가 있으면 공개할 수 없다."""
        responses = answers()
        responses[-1] = {**CLOSING, "defense": "변호인 측 최종변론입니다. 피고인은 3번이나 사과했습니다."}
        caller = FakeCaller(responses)
        generated_ = sa.generate_units(CASE, JUDGMENT_OUTPUT, CAST, CLS, model="openai:a", paper=PAPER, caller=caller,
                                       max_retries=0)
        script = sa.build_script(CASE, CAST, CLS, generated_, model="openai:a")
        self.assertEqual(script["pending"], [])
        self.assertEqual(codes(script["review"], sr.ERROR), [("U4", "/defense", "NUMBER_NOT_IN_SOURCE")])
        self.assertFalse(script["publishable"])

    def test_clean_script_stays_publishable(self):
        caller = FakeCaller(answers())
        generated_ = sa.generate_units(CASE, JUDGMENT_OUTPUT, CAST, CLS, model="openai:a", paper=PAPER, caller=caller)
        script = sa.build_script(CASE, CAST, CLS, generated_, model="openai:a")
        self.assertTrue(script["publishable"])
        self.assertFalse(sr.has_errors(script["review"]))


if __name__ == "__main__":
    unittest.main()
