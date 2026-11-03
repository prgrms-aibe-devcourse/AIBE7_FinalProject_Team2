"""판결 체험 대본 — 블록 재생성 (BE-129 2차) 테스트. 모델은 호출하지 않는다(가짜 함수).

실행: tools/ai-judgment에서 `python3 -m unittest discover tests`
"""

import json
import sys
import unittest
from pathlib import Path

TOOL_DIR = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(TOOL_DIR))
sys.path.insert(0, str(Path(__file__).resolve().parent))

import llm  # noqa: E402
import script_assembler as sa  # noqa: E402
import script_regenerate as rg  # noqa: E402
import script_review as sr  # noqa: E402
from test_script_assembler import (CASE, CAST, CLOSING, CLS, DESIGN, EVIDENCE, INTRO, JUDGMENT_OUTPUT, OPENING, PAPER,  # noqa: E402
                                   FakeCaller)
from test_script_review import generated  # noqa: E402

CONTRACT = json.loads((TOOL_DIR / "contracts" / "script_blocks.json").read_text(encoding="utf-8"))
NEW_CAST = {"prosecutor": "nambul", "defense": "naero"}
NEW_CLOSING = {"prosecutor": "검사 측 의견입니다. 무겁게 살펴 주십시오.", "defense": "변호인 측 최종변론입니다. 반성을 살펴 주십시오."}


def current_blocks(revisions=None, units=sa.BLOCK_UNITS):
    """지금 대본의 블록 (백엔드가 넘기는 `{단위: {content, revision}}`). 내용은 적재 때와 같이 block_contents로 만든다."""
    revisions = revisions or {}
    contents = sa.block_contents(CASE, generated())
    return {unit: {"content": contents[unit], "revision": revisions.get(unit, 1)} for unit in units}


def regenerate(units, responses, blocks=None, cls=CLS, cast=CAST, paper=PAPER, **kwargs):
    caller = FakeCaller(responses)
    logs = []
    result = rg.regenerate_blocks(CASE, JUDGMENT_OUTPUT, cast, cls, current_blocks() if blocks is None else blocks, units,
                                  model="openai:a", paper=paper, caller=caller, max_retries=0, log=logs.append, **kwargs)
    return caller, result, logs


class TargetTest(unittest.TestCase):
    def test_later_blocks_follow_the_basis_table(self):
        everything = current_blocks()
        for units, expected in ((["U0"], ["U0", "U2", "U4"]), (["U1"], ["U1"]), (["U2"], ["U2", "U4"]),
                                (["U3"], ["U3"]), (["U4"], ["U4"]), (["U4", "U1"], ["U1", "U4"])):
            with self.subTest(units):
                self.assertEqual(rg.regeneration_targets(units, everything), expected)

    def test_link_table_matches_decision(self):
        """해송님 연동 표(BE-129): 바꾼 값 → 다시 만들 블록. 그 값을 basis에 가진 블록을 고르면 뒤 블록은 계약의 affects로 따라온다.
        피해자 측(victim "U0 → U3 · U4") · 부가 인물(extra "U0 → U2 · U3")은 표의 "U0 → U2 → U4" 줄과 겹쳐 읽는다 — U0를 다시
        만들면 U0 질문을 받는 U2와 U0 쟁점을 받는 U4도 따라온다(정본 BLOCK_BASIS, docs/decisions/BE-129.md 2차)."""
        table = {"cast": ["U2", "U3", "U4"], "issue": ["U0", "U2", "U4"], "mood": ["U0", "U1", "U2", "U4"],
                 "victim": ["U0", "U2", "U3", "U4"], "thirdParty": ["U0", "U2", "U3", "U4"],
                 "sensitiveFlags": list(sa.BLOCK_UNITS), "paper": ["U1"]}
        for key, expected in table.items():
            with self.subTest(key):
                chosen = [unit for unit, basis in CONTRACT["basis"].items() if key in basis]
                self.assertEqual(rg.regeneration_targets(chosen, current_blocks()), expected)
        for unit, later in CONTRACT["affects"].items():
            with self.subTest(unit):
                self.assertEqual(rg.regeneration_targets([unit], current_blocks()), [unit, *later])

    def test_missing_input_blocks_are_made_too(self):
        """증거조사만 있는 대본(BE-126 V45 이관)에서 U2를 고르면 U2가 기대는 U0도 함께 만든다."""
        only_evidence = current_blocks(units=("U3",))
        self.assertEqual(rg.regeneration_targets(["U2"], only_evidence), ["U0", "U2", "U4"])
        self.assertEqual(rg.regeneration_targets(["U4"], only_evidence), ["U0", "U2", "U4"])
        self.assertEqual(rg.regeneration_targets(["U1"], only_evidence), ["U1"])


class InputTest(unittest.TestCase):
    def test_classification_without_sensitive_flags_is_rejected(self):
        """분류만 넘기고 민감 플래그를 합치지 않으면 거절한다 — 빈 목록으로 읽으면 민감 사건이 가드 없이 다시 만들어진다(PR #370 리뷰)."""
        without = {k: v for k, v in CLS.items() if k != "sensitiveFlags"}
        self.assertEqual(sa.classification_errors(without), [])  # 대본 전체 생성 검사는 통과시킨다 — 그래서 따로 막는다
        with self.assertRaises(rg.RegenerationError) as ctx:
            regenerate(["U4"], [], cls=without)
        self.assertIn("sensitiveFlags", ctx.exception.args[0][0])
        _, result, _ = regenerate(["U4"], [NEW_CLOSING], cls={**CLS, "sensitiveFlags": []})  # 빈 목록은 "민감 요소 없음"
        self.assertEqual(result["regenerated"], ["U4"])

    def test_sensitive_flags_reach_every_regenerated_prompt(self):
        cls = {**CLS, "sensitiveFlags": ["SELF_HARM"]}
        guard = sa.read_prompt("script/sensitive/self_harm.md").strip().splitlines()[0]
        caller, _, _ = regenerate(["U2"], [OPENING, CLOSING], cls=cls)
        self.assertEqual(len(caller.calls), 2)
        for call in caller.calls:
            self.assertIn(guard, call["system"])

    def test_bad_inputs_are_listed(self):
        for units, blocks, paper, message in (
                ([], None, PAPER, "하나 이상"), (["U5"], None, PAPER, "모르는 단위"), (["U4", "U4"], None, PAPER, "두 번"),
                (["U4"], {"U9": {"content": {}, "revision": 1}}, PAPER, "U9"),
                (["U4"], {"U0": {"content": DESIGN, "revision": 0}}, PAPER, "revision"),
                (["U4"], {"U3": {"content": {}, "revision": 1}}, PAPER, "목록"),
                (["U4"], {"U0": {"revision": 1}}, PAPER, "content가 없습니다"),
                (["U1"], None, None, "기사(paper)")):
            with self.subTest(message):
                with self.assertRaises(rg.RegenerationError) as ctx:
                    regenerate(units, [], blocks=blocks, paper=paper)
                self.assertTrue(any(message in error for error in ctx.exception.args[0]), ctx.exception.args[0])


class ReviewScopeTest(unittest.TestCase):
    def test_existing_u1_requires_paper(self):
        """U1 블록이 있는데 기사를 넘기지 않으면 거절한다 — 검수가 U1을 빈 기사와 비교해 숫자가 든 해설에 가짜 오류를 낸다
        (PR #378 리뷰, 실제 대본에서 NUMBER_NOT_IN_SOURCE 5건). U1이 없으면 기사 없이 된다."""
        with self.assertRaises(rg.RegenerationError) as ctx:
            regenerate(["U4"], [], paper=None)
        self.assertIn("기사(paper)", ctx.exception.args[0][0])
        no_u1 = current_blocks(units=("U0", "U2", "U3", "U4"))
        _, result, _ = regenerate(["U4"], [NEW_CLOSING], blocks=no_u1, paper=None)
        self.assertEqual(result["regenerated"], ["U4"])

    def test_numbers_in_existing_u1_are_checked_against_the_paper(self):
        """숫자가 든 지금 U1은 기사와 비교해 검사된다 — 기사에 있는 숫자면 오류가 아니다."""
        paper = {**PAPER, "article": ["피해액은 3천만 원이었다.", "문단2"]}
        blocks = current_blocks()
        blocks["U1"]["content"] = {"lines": ["기사 제목부터 같이 봐요.", "피해액이 3천만 원이었대요.", *INTRO["lines"][2:]]}
        generated_review = []
        original = rg.review_script

        def spy(*args, **kwargs):
            generated_review.extend(original(*args, **kwargs))
            return generated_review

        rg.review_script = spy
        try:
            regenerate(["U4"], [NEW_CLOSING], blocks=blocks, paper=paper)
        finally:
            rg.review_script = original
        self.assertEqual([i for i in generated_review if i["unit"] == "U1" and i["severity"] == sr.ERROR], [])


class CascadeTest(unittest.TestCase):
    def test_cascade_false_makes_only_the_chosen_block(self):
        """세부조정(10/30 결정): U0만 다시 만들고 U2 · U4는 둔다. 둔 블록은 basis가 옛 U0 판이라 백엔드가 낡음으로 본다."""
        self.assertEqual(rg.regeneration_targets(["U0"], current_blocks(), cascade=False), ["U0"])
        blocks = current_blocks({"U0": 3})
        caller, result, _ = regenerate(["U0"], [DESIGN], blocks=blocks, cascade=False)
        self.assertEqual(len(caller.calls), 1)
        self.assertEqual((result["units"], result["regenerated"]), (["U0"], ["U0"]))
        self.assertEqual(result["blocks"]["U0"]["revision"], 4)
        self.assertTrue(all(i["unit"] in ("U0", "") for i in result["review"]))

    def test_cascade_false_still_adds_missing_input_blocks(self):
        only_evidence = current_blocks(units=("U3",))
        self.assertEqual(rg.regeneration_targets(["U4"], only_evidence, cascade=False), ["U0", "U2", "U4"])
        self.assertEqual(rg.regeneration_targets(["U2"], only_evidence, cascade=False), ["U0", "U2"])

    def test_cascade_must_be_bool(self):
        with self.assertRaises(rg.RegenerationError):
            regenerate(["U4"], [], cascade="no")


class RegenerateTest(unittest.TestCase):
    def test_regenerates_only_the_chosen_block_from_current_inputs(self):
        """U4만 고르면 U4만 부른다 — 지금 U0 쟁점 · U2 변호인 입장을 보고, 판은 지금 판 다음 판, basis는 지금 앞 블록 판이다."""
        blocks = current_blocks({"U0": 2, "U2": 1, "U4": 3})
        caller, result, _ = regenerate(["U4"], [NEW_CLOSING], blocks=blocks)
        self.assertEqual(len(caller.calls), 1)
        self.assertEqual(caller.calls[0]["system"], sa.closing_request(CASE, CLS, CAST, DESIGN, OPENING)[0])
        self.assertEqual(caller.calls[0]["user"], sa.closing_request(CASE, CLS, CAST, DESIGN, OPENING)[1])
        self.assertEqual((result["units"], result["regenerated"], result["pending"]), (["U4"], ["U4"], []))
        self.assertEqual(list(result["blocks"]), ["U4"])  # 다시 만들지 않은 블록은 내보내지 않는다
        block = result["blocks"]["U4"]
        self.assertEqual(block["content"], NEW_CLOSING)
        self.assertEqual(block["revision"], 4)
        self.assertEqual({k: block["basis"][k] for k in ("U0", "U2")}, {"U0": 2, "U2": 1})
        self.assertEqual(block["basis"]["cast"], CAST)
        self.assertIsNone(block["basis"]["case"])  # DB 판은 백엔드가 채운다
        self.assertTrue(result["publishable"])
        self.assertEqual(result["meta"], {"cast": CAST, "issue": "admitted", "admits": True})

    def test_design_change_carries_through_later_blocks(self):
        """U0를 다시 만들면 U2 · U4도 새 U0로 만든다. U2의 basis는 새 U0 판, U4는 새 U0 · U2 판을 본다."""
        design = {**DESIGN, "questions": [*DESIGN["questions"][:2], {"q": "공소사실을 인정합니까?", "a": "아니요"}]}
        blocks = current_blocks({"U0": 3, "U2": 2, "U4": 1})
        caller, result, _ = regenerate(["U0"], [design, OPENING, NEW_CLOSING], blocks=blocks)
        self.assertEqual(result["regenerated"], ["U0", "U2", "U4"])
        self.assertIn(json.dumps(design, ensure_ascii=False), caller.calls[1]["user"])  # U2는 새 설계도를 받는다
        revisions = {unit: block["revision"] for unit, block in result["blocks"].items()}
        self.assertEqual(revisions, {"U0": 4, "U2": 3, "U4": 2})
        self.assertEqual(result["blocks"]["U2"]["basis"]["U0"], 4)
        self.assertEqual({k: result["blocks"]["U4"]["basis"][k] for k in ("U0", "U2")}, {"U0": 4, "U2": 3})
        self.assertFalse(result["meta"]["admits"])  # 새 설계도의 마지막 답

    def test_new_cast_is_used_and_recorded(self):
        caller, result, _ = regenerate(["U2", "U3"], [OPENING, *EVIDENCE, NEW_CLOSING], cast=NEW_CAST)
        self.assertEqual(result["regenerated"], ["U2", "U3", "U4"])
        self.assertIn("검사는 남불", caller.calls[0]["user"])
        for unit in ("U2", "U3", "U4"):
            self.assertEqual(result["blocks"][unit]["basis"]["cast"], NEW_CAST)
        self.assertEqual([line["text"] for line in result["blocks"]["U3"]["content"]], EVIDENCE)
        self.assertEqual(result["meta"]["cast"], NEW_CAST)

    def test_failed_input_block_leaves_later_blocks_pending(self):
        """U0를 만들지 못하면 U2 · U4는 옛 U0로 만들지 않는다 — 관리자가 바꾸려던 값이 남기 때문이다."""
        caller, result, _ = regenerate(["U0"], ["JSON 아님"])
        self.assertEqual(len(caller.calls), 1)
        self.assertEqual((result["regenerated"], result["pending"], result["blocks"]), ([], ["U0", "U2", "U4"], {}))
        self.assertFalse(result["publishable"])

    def test_quota_exhaustion_stops_and_keeps_what_was_made(self):
        caller, result, _ = regenerate(["U0"], [DESIGN, llm.LLMQuotaExhaustedError("일 한도")])
        self.assertTrue(result["stopped"])
        self.assertEqual((result["regenerated"], result["pending"]), (["U0"], ["U2", "U4"]))
        self.assertEqual(list(result["blocks"]), ["U0"])
        self.assertFalse(result["publishable"])

    def test_evidence_stopped_midway_keeps_current_block(self):
        caller, result, _ = regenerate(["U3"], [EVIDENCE[0], llm.LLMQuotaExhaustedError("일 한도")])
        self.assertTrue(result["stopped"])
        self.assertEqual((result["regenerated"], result["pending"], result["blocks"]), ([], ["U3"], {}))

    def test_repairs_touch_only_regenerated_blocks(self):
        """지금 U1에 검수 오류가 있어도 U4만 다시 만들면 U1은 고쳐 쓰지 않는다(승인했을 수 있다). 돌려주는 review에도 U1 항목은
        넣지 않는다 — 부르는 쪽이 덮어쓰면 승인된 블록이 "오류 블록"이 된다(PR #378 리뷰)."""
        blocks = current_blocks()
        blocks["U1"]["content"] = {"lines": [*INTRO["lines"][1:4], "서두르지 말고 생각해 보시기 바랍니다.", "다섯째 줄이에요."]}
        bad_closing = {**CLOSING, "defense": "변호인 측 최종변론입니다. 피고인은 3번이나 사과했습니다."}
        fixed_text = "변호인 측 최종변론입니다. 피고인은 여러 차례 사과했습니다."
        caller, result, _ = regenerate(["U4"], [bad_closing, {"text": fixed_text}], blocks=blocks)
        self.assertEqual(len(caller.calls), 2)  # U4 만들기 + U4 줄 고쳐 쓰기. U1은 부르지 않는다
        self.assertEqual(result["blocks"]["U4"]["content"]["defense"], fixed_text)
        self.assertEqual(result["blocks"]["U4"]["revision"], 3)  # 다시 만든 판(2) + 고쳐 쓴 판
        self.assertEqual([r["unit"] for r in result["repairs"]], ["U4"])
        self.assertEqual([i["unit"] for i in result["review"]], [])
        self.assertTrue(result["publishable"])  # 다시 만든 블록에는 오류가 없다
        # 만들지 않은 블록의 경고는 따로 돌려주되 오류는 넣지 않는다(BE-163)
        self.assertEqual(sorted(result["unchangedWarnings"]), ["U0", "U1", "U2", "U3"])
        self.assertTrue(all(i["severity"] == sr.WARNING for items in result["unchangedWarnings"].values() for i in items))

    def test_unchanged_blocks_get_new_warnings(self):
        """U4만 다시 만들어도, 지금 U2에 새 검사(BE-159 카드 목록)로 생긴 경고를 `unchangedWarnings`로 돌려준다(BE-163).
        다시 만든 블록 · 없는 블록은 넣지 않고, 경고가 없는 블록은 빈 목록이다(옛 경고를 지우려면)."""
        blocks = current_blocks(units=("U0", "U1", "U2", "U4"))
        cards = blocks["U2"]["content"]["cards"]
        blocks["U2"]["content"]["cards"] = {**cards, "issues": {**cards["issues"], "ul": []}}
        _, result, _ = regenerate(["U4"], [NEW_CLOSING], blocks=blocks)
        warnings = result["unchangedWarnings"]
        self.assertEqual(sorted(warnings), ["U0", "U1", "U2"])  # U4는 다시 만들었고, U3는 없다
        self.assertEqual([i["code"] for i in warnings["U2"]], ["CARD_LIST_EMPTY"])
        self.assertEqual(warnings["U0"], [])
        self.assertNotIn("CARD_LIST_EMPTY", [i["code"] for i in result["review"]])  # 결과 review는 다시 만든 블록 것만

    def test_review_failure_blocks_publishing(self):
        original = rg.review_script
        rg.review_script = lambda *a, **k: (_ for _ in ()).throw(ValueError("깨짐"))
        try:
            _, result, _ = regenerate(["U4"], [NEW_CLOSING])
        finally:
            rg.review_script = original
        self.assertEqual(result["review"][0]["code"], "REVIEW_FAILED")
        self.assertFalse(result["publishable"])
        self.assertEqual(result["unchangedWarnings"], {})  # 검수가 돌지 못했으면 옛 경고를 둔다(BE-163)


if __name__ == "__main__":
    unittest.main()
