"""판결 체험 대본 — 블록 조립기 · 단위별 생성 (BE-121) 테스트. 모델은 호출하지 않는다(가짜 함수).

실행: tools/ai-judgment에서 `python3 -m unittest discover tests`
"""

import json
import sys
import unittest
from pathlib import Path

TOOL_DIR = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(TOOL_DIR))

import llm  # noqa: E402
import script_assembler as sa  # noqa: E402

CASE = {
    "caseId": 1, "title": "빌린 돈 문제로 찾아온 지인을 살해한 사건", "chargeName": "살인",
    "appliedLaw": "형법 제250조 제1항 살인", "overview": "말다툼 끝에 흉기로 지인을 살해했다.",
    "sections": [
        {"stage": "DETAIL", "sectionType": "FACTS", "title": "주요 사실관계", "content": "말다툼 끝에 흉기를 집어 들었다."},
        {"stage": "ARGUMENT", "sectionType": "DEFENSE", "title": "변호인 주장", "content": "우발적 범행이라고 주장한다."},
        {"stage": "LAW", "sectionType": "LAW_TERM", "title": "양형기준", "content": "권고 범위 7년 ~ 12년"},
        {"stage": "SUMMARY", "sectionType": "SUMMARY", "title": "정리", "content": "최종 정리 요약"},
    ],
    "factors": [
        {"factorId": 2, "label": "다투던 중 집에 있던 흉기를 집어 들었다", "revealStage": "OVERVIEW"},
        {"factorId": 3, "label": "범행 뒤 구호 조치 없이 현장을 떠났다", "revealStage": "OVERVIEW"},
        {"factorId": 7, "label": "수사 초기부터 범행을 인정했다", "revealStage": "DETAIL"},
        {"factorId": 10, "label": "피고인은 우발적 범행이라고 주장한다", "revealStage": "ARGUMENT"},
        {"factorId": 12, "label": "양형기준 감경영역에 해당한다", "revealStage": "LAW"},
    ],
    "recommended": {"minMonths": 84, "maxMonths": 144},
}
JUDGMENT_OUTPUT = {"factors": [{"factorId": 2, "direction": "UP"}, {"factorId": 3, "direction": "UP"},
                               {"factorId": 7, "direction": "DOWN"}]}
CAST = {"prosecutor": "naero", "defense": "nambul"}
CLS = {"mood": "impulsive", "issue": "admitted", "victim": "punish", "thirdParty": False, "sensitiveFlags": []}
PAPER = {"headline": "빌린 돈 갚으라며 찾아온 지인, 자택서 숨져", "deck": "말다툼 끝 흉기", "article": ["문단1", "문단2"]}

DESIGN = {"plea": "인정", "issues": ["범행 경위", "범행 뒤 행동", "피해 회복"],
          "questions": [{"q": "미리 해칠 생각이 있었습니까?", "a": "아니요"},
                        {"q": "구호 조치 없이 집을 나갔습니까?", "a": "예"},
                        {"q": "공소사실을 인정합니까?", "a": "예"}], "tone": "무겁게"}
INTRO = {"lines": ["①남불: 기사 제목부터 같이 봐요.", "둘째 줄이에요.", "셋째 줄이에요.", "넷째 줄이에요.", "서두르지 말고 생각해 봐요."]}
OPENING = {"indict": ["공소사실을 말씀드리겠습니다. 피고인은 오래 다퉈 왔습니다.", "적용 법조는 형법 제250조 제1항입니다."],
           "plea": ["피고인은 공소사실을 인정합니다.", "우발적인 일이라는 점을 살펴 주십시오."],
           "issues_narr": ["남은 쟁점은 형량이에요.", "범행 경위와 범행 뒤 행동을 눈여겨보세요."],
           "questions": [{"q": "미리 해칠 생각이 있었습니까?", "a": "아니요, 아닙니다."},
                         {"q": "구호 조치 없이 집을 나갔습니까?", "a": "네, 맞습니다."}],
           "cards": {"indict": {"ul": ["오래 다퉜다", "흉기로 살해했다"], "law": "형법 제250조 제1항"},
                     "plea": {"b": "공소사실을 인정한다.", "ul": ["우발적 범행이다."]},
                     "issues": {"p": "남은 쟁점은 형량이다.", "ul": ["경위 — 우발", "행동 — 구호 없음", "회복 — 반성"]}}}
EVIDENCE = ["검사 측 증거입니다. 흉기를 집어 든 사실입니다.", "구호 조치 없이 떠난 사실입니다.",
            "변호인 측 증거입니다. 수사 초기부터 인정한 기록입니다."]
CLOSING = {"prosecutor": "검사 측 의견입니다. 엄한 처벌을 구합니다.", "defense": "변호인 측 최종변론입니다. 살펴 주십시오."}


class FakeCaller:
    """순서대로 응답을 내주는 가짜 모델 호출. dict는 JSON 문자열로 바꾼다."""

    def __init__(self, responses):
        self.responses, self.calls = list(responses), []

    def __call__(self, model, system, user, **kwargs):
        self.calls.append({"system": system, "user": user, **kwargs})
        response = self.responses.pop(0)
        if isinstance(response, Exception):
            raise response
        text = json.dumps(response, ensure_ascii=False) if isinstance(response, dict) else response
        return llm.LLMResult(text, "openai", model)


def answers(paper=True):
    return [DESIGN, *([INTRO] if paper else []), OPENING, *EVIDENCE, CLOSING]


class BlockTest(unittest.TestCase):
    def test_block_for_unit_keeps_common_and_unit_section_only(self):
        text = "# 제목\n\n## 공통\n\n- 공통 규칙\n\n## U2\n\n- U2 규칙\n\n## U4\n\n- U4 규칙\n"
        self.assertEqual(sa.block_for_unit(text, "U2"), "# 제목\n- 공통 규칙\n- U2 규칙")
        self.assertNotIn("U2 규칙", sa.block_for_unit(text, "U4"))
        self.assertEqual(sa.block_for_unit("# 민감\n\n- 전체 규칙\n", "U3"), "# 민감\n\n- 전체 규칙")

    def test_system_uses_unit_sections_and_drops_common_output_format(self):
        system = sa.build_system("U2", CLS, ["prosecutor", "defense", "narrator"])
        issue = sa.read_prompt("script/guide/issue/admitted.md")
        self.assertIn(sa.block_for_unit(issue, "U2"), system)
        self.assertNotIn(sa.OUTPUT_HEADING, system)  # 출력 형식(대사 한 줄)은 U3 기준이라 빠진다
        self.assertIn("진행 해설", system)
        # 조립 순서: PERSONA → ROLE → CASE GUIDE → GUARDRAILS
        self.assertLess(system.index("BASE PERSONA"), system.index("ROLE — 검사"))
        self.assertLess(system.index("ROLE — 검사"), system.index("CASE GUIDE"))
        self.assertLess(system.index("CASE GUIDE"), system.index("GUARDRAILS"))

    def test_guide_blocks_follow_unit_mapping(self):
        with_extra = {**CLS, "thirdParty": True}
        titles = lambda unit, cls=CLS: [b.splitlines()[0] for b in sa.guide_blocks(cls, unit)]  # noqa: E731
        self.assertEqual(len(titles("U1")), 1)  # 분위기만
        self.assertEqual(len(titles("U2")), 2)  # 분위기 · 쟁점 (피해자 측 없음)
        self.assertEqual(len(titles("U2", with_extra)), 3)  # + 부가 인물
        self.assertEqual(len(titles("U3")), 1)  # 피해자 측만 (분위기는 U3에 넣지 않는다)
        self.assertEqual(len(titles("U4")), 3)  # 분위기 · 쟁점 · 피해자 측

    def test_sensitive_blocks_go_last(self):
        cls = {**CLS, "sensitiveFlags": ["MENTAL_HEALTH"]}
        system = sa.build_system("U0", cls, ["narrator"])
        sensitive = sa.read_prompt("script/sensitive/mental_health.md").strip()
        self.assertTrue(system.endswith(sensitive))
        guide, extra = sa.evidence_blocks(cls)
        self.assertEqual(extra, sensitive)
        self.assertIn("CASE GUIDE", guide)
        self.assertEqual(sa.evidence_blocks(CLS)[1], None)

    def test_public_case_excludes_law_summary_and_judgment(self):
        data = sa.public_case(CASE, sa.UNIT_STAGES["U2"])
        dumped = json.dumps(data, ensure_ascii=False)
        self.assertNotIn("권고 범위", dumped)
        self.assertNotIn("최종 정리", dumped)
        self.assertNotIn("감경영역", dumped)
        self.assertNotIn("recommended", dumped)
        self.assertIn("우발적 범행이라고 주장한다", dumped)


class NormalizeTest(unittest.TestCase):
    def test_normalize_answer(self):
        self.assertEqual(sa.normalize_answer("네, 맞습니다."), "…네.")
        self.assertEqual(sa.normalize_answer("…예"), "…네.")
        self.assertEqual(sa.normalize_answer("아니요, 아닙니다."), "…아닙니다.")
        self.assertEqual(sa.normalize_answer("잘 모르겠습니다."), "…잘 모르겠습니다.")

    def test_strip_line_prefix_only_with_separator(self):
        self.assertEqual(sa.strip_line_prefix("①남불: 기사 제목부터 봐요."), "기사 제목부터 봐요.")
        self.assertEqual(sa.strip_line_prefix("2. 내로 - 그날이에요."), "그날이에요.")
        # 숫자로 시작하는 정상 대사는 자르지 않는다(소수 · 금액)
        self.assertEqual(sa.strip_line_prefix("3.5억 원을 받았다고 해요."), "3.5억 원을 받았다고 해요.")
        self.assertEqual(sa.strip_line_prefix("2)5천만 원이에요."), "2)5천만 원이에요.")
        self.assertEqual(sa.strip_line_prefix("내로 생각엔 이래요."), "내로 생각엔 이래요.")

    def test_sensitive_notice_joins_notice_flags_only(self):
        self.assertIsNone(sa.sensitive_notice({"sensitiveFlags": ["PROFANITY_QUOTED"]}))
        self.assertEqual(sa.sensitive_notice({"sensitiveFlags": ["SEXUAL_OFFENSE"]}),
                         "이 사건에는 성범죄와 관련된 내용이 나와요.")
        self.assertEqual(sa.sensitive_notice({"sensitiveFlags": ["VULNERABLE_VICTIM", "OVERSEAS", "SELF_HARM"]}),
                         "이 사건에는 어린 · 고령 · 장애 피해자, 자해 · 자살과 관련된 내용이 나와요.")

    def test_classification_errors(self):
        self.assertEqual(sa.classification_errors(CLS), [])
        errors = sa.classification_errors({"mood": "x", "issue": "admitted", "victim": "punish",
                                           "thirdParty": "no", "sensitiveFlags": ["NOPE"]})
        self.assertEqual(len(errors), 3)
        self.assertTrue(sa.classification_errors(None))


class GenerateTest(unittest.TestCase):
    def generate(self, responses, cast=CAST, cls=CLS, paper=PAPER):
        caller = FakeCaller(responses)
        generated = sa.generate_units(CASE, JUDGMENT_OUTPUT, cast, cls, model="openai:a", paper=paper,
                                      caller=caller, max_retries=1)
        return caller, generated, sa.build_script(CASE, cast, cls, generated, model="openai:a")

    def test_full_script_in_case1_shape(self):
        caller, generated, script = self.generate(answers())
        self.assertFalse(caller.responses)
        self.assertEqual(script["pending"], [])
        self.assertTrue(script["publishable"])
        self.assertEqual(sorted(script["acts"]), ["1a", "2", "3"])
        intro = script["acts"]["1a"]["steps"]
        self.assertEqual(intro[0]["text"], "기사 제목부터 같이 봐요.")  # 번호 · 화자 접두어를 뗐다
        self.assertEqual([s["source"]["type"] for s in intro], ["ai", "fixed", "ai", "ai", "ai", "fixed", "ai"])
        trial = script["acts"]["2"]["steps"]
        texts = [s.get("text") for s in trial]
        self.assertIn("…아닙니다.", texts)  # 최소 응답 정규화
        self.assertIn("…네.", texts)
        self.assertEqual(texts[-5:-2], ["공소사실을 인정합니까?", "…네, 인정합니다.", "이상으로 피고인 신문을 마치겠습니다."])
        evidence_steps = [s for s in trial if s.get("card") == "evidence"]
        self.assertEqual([s["say"] for s in evidence_steps], ["naero", "naero", "nambul"])
        self.assertEqual(evidence_steps[-1]["confirm"], 2)
        self.assertEqual(script["evidence"][0], {"side": "검사", "text": "다투던 중 집에 있던 흉기를 집어 들었다"})
        verdict = script["acts"]["3"]
        self.assertEqual(verdict["procedures"], ["검사 의견", "최종변론", "판결"])  # 최후진술 없음
        self.assertNotIn("A씨 역", [s.get("role") for s in verdict["steps"]])
        self.assertEqual(script["cards"]["indict"]["p"][0]["t"], "형법 제250조 제1항")
        # U3는 BE-107 프롬프트에 피해자 측 블록을 더했다
        self.assertIn("엄벌", caller.calls[3]["system"])
        self.assertTrue(all(call["json_output"] for i, call in enumerate(caller.calls) if i not in (3, 4, 5)))

    def test_swapped_cast_keeps_bundle_seats(self):
        cast = {"prosecutor": "nambul", "defense": "naero"}
        _, _, script = self.generate(answers(), cast=cast)
        trial = script["acts"]["2"]
        self.assertEqual(trial["look"], {"nambul": "role:prosecutor", "naero": "role:defense"})
        self.assertEqual(trial["slots"]["nambul"]["slot"], sa.SLOT["A"])  # 검사(묶음 A)는 늘 왼쪽
        stand = next(s for s in trial["steps"] if s.get("tags"))
        self.assertEqual(stand["tags"], {"naero": "A씨 역"})
        self.assertIn("내로가 변호인석에서 일어나", stand["text"])
        self.assertIn("검사 남불이 질문해요", stand["text"])
        self.assertEqual(script["acts"]["3"]["slots"]["nambul"]["slot"], sa.SLOT["A"])

    def test_issue_type_sets_last_question_and_notice(self):
        cls = {**CLS, "issue": "intent_disputed", "sensitiveFlags": ["MENTAL_HEALTH"]}
        design = {**DESIGN, "questions": [*DESIGN["questions"][:2], {"q": "행위 사실을 인정합니까?", "a": "아니요"}]}
        responses = answers()
        responses[0] = design
        _, _, script = self.generate(responses, cls=cls)
        steps = script["acts"]["2"]["steps"]
        self.assertEqual(steps[0], {"system": "이 사건에는 정신건강과 관련된 내용이 나와요.", "hideCast": True, "proc": 0,
                                    "source": {"type": "fixed", "basis": ["sensitiveFlags"]}})
        texts = [s.get("text") for s in steps]
        index = texts.index("행위 사실을 인정합니까?")
        self.assertEqual(texts[index + 1], "…아닙니다.")

    def test_generates_paper_first_when_not_given(self):
        generated_paper = {"headline": " 말다툼 끝 지인 숨져 ", "deck": "", "caption": "재연 일러스트",
                           "article": [" 문단1 ", "문단2"]}
        caller, generated, script = self.generate([generated_paper, *answers()], paper=None)
        self.assertFalse(caller.responses)
        self.assertEqual(script["pending"], [])
        self.assertTrue(script["publishable"])
        self.assertEqual(script["paper"], {"desk": "사회부", "headline": "말다툼 끝 지인 숨져", "deck": None,
                                           "caption": "재연 일러스트", "article": ["문단1", "문단2"]})
        # 1막 해설은 방금 만든 기사를 짚는다
        self.assertIn("말다툼 끝 지인 숨져", caller.calls[2]["user"])

    def test_paper_prompt_is_neutral_and_overview_only(self):
        cls = {**CLS, "sensitiveFlags": ["MENTAL_HEALTH"]}
        system, user = sa.paper_request(CASE, cls)
        self.assertNotIn("BASE PERSONA", system)
        self.assertNotIn("CASE GUIDE", system)  # 분위기 블록을 넣지 않는다(기사는 중립)
        self.assertNotIn("100자 이내", system)  # 대사 길이 줄은 기사와 맞지 않아 뺀다
        self.assertIn("GUARDRAILS", system)
        self.assertTrue(system.endswith(sa.read_prompt("script/sensitive/mental_health.md").strip()))
        self.assertIn("흉기를 집어 들었다", user)  # OVERVIEW
        for hidden in ("수사 초기부터", "우발적 범행이라고", "감경영역", "권고 범위", "말다툼 끝에 흉기를 집어 들었다."):
            self.assertNotIn(hidden, user)  # DETAIL · ARGUMENT · LAW · 섹션은 1막 기사에 넣지 않는다

    def test_paper_failure_skips_intro_and_is_not_publishable(self):
        bad = {"headline": "", "article": ["하나"]}
        caller, generated, script = self.generate([bad, bad, DESIGN, OPENING, *EVIDENCE, CLOSING], paper=None)
        self.assertEqual(generated["units"]["PAPER"]["attempts"], 2)
        self.assertEqual(script["pending"], ["PAPER", "U1"])
        self.assertIsNone(script["paper"])
        self.assertNotIn("1a", script["acts"])
        self.assertIn("2", script["acts"])
        self.assertFalse(script["publishable"])

    def test_bad_format_is_retried(self):
        bad_opening = {**OPENING, "questions": OPENING["questions"][:1]}
        # U2가 실패해도 증거조사(U3)는 돈다 — evidence.json은 BE-107 계약 그대로 따로 쓰인다
        caller, generated, script = self.generate([DESIGN, INTRO, "JSON 아님", bad_opening, *EVIDENCE])
        self.assertEqual(generated["units"]["U2"]["attempts"], 2)
        self.assertIsNone(generated["units"]["U2"]["value"])
        self.assertIn("U2", script["pending"])
        self.assertIn("U4", script["pending"])  # U2 입장이 없으면 U4를 만들지 않는다
        self.assertNotIn("2", script["acts"])
        self.assertEqual(len(generated["evidence"]), 3)
        self.assertFalse(script["publishable"])

    def test_long_line_is_retried(self):
        long_closing = {"prosecutor": "가" * 120, "defense": "변호인 측 최종변론입니다."}
        _, generated, _ = self.generate([*answers()[:-1], long_closing, CLOSING])
        self.assertEqual(generated["units"]["U4"]["attempts"], 2)
        self.assertEqual(generated["units"]["U4"]["value"], CLOSING)

    def test_quota_stops_remaining_units(self):
        caller, generated, script = self.generate([DESIGN, INTRO, llm.LLMQuotaExhaustedError("일 한도")])
        self.assertTrue(generated["stopped"])
        self.assertEqual(script["pending"], ["U2", "U4"])
        self.assertEqual(generated["evidence"], [])
        self.assertFalse(script["publishable"])


if __name__ == "__main__":
    unittest.main()
