"""판단 요소 가치관 축 분류 투표 테스트 (BE-49). 네트워크를 쓰지 않는다.

실행: tools/ai-judgment에서 `python3 -m unittest discover tests`
"""

import json
import sys
import unittest
from pathlib import Path

TOOL_DIR = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(TOOL_DIR))

from axis_vote import AxisVoteError, aggregate_votes, apply_votes, build_axis_prompt, parse_axis_answer  # noqa: E402
from common import load_json  # noqa: E402

IDS = [1, 2, 3]


def answer(*axes):
    return json.dumps({"factors": [{"factorId": i, "valueAxis": a} for i, a in zip(IDS, axes)]})


class AxisVoteTest(unittest.TestCase):
    def test_build_axis_prompt_sendsOverviewAndFactorsOnly(self):
        case = load_json(TOOL_DIR / "examples" / "case_input.json")
        system, user = build_axis_prompt(case)
        self.assertIn("APOLOGY_SINCERITY", system)
        self.assertIn(case["overview"], user)
        for factor in case["factors"]:
            self.assertIn(f"- {factor['factorId']}. {factor['label']}", user)
        # 형량 · 형벌 규칙 · 권고 범위는 보내지 않는다 (축 분류에 필요 없다)
        self.assertNotIn(case["statutoryPenaltyText"], user)
        self.assertNotIn("선고할 수 있는 범위", user)

    def test_parse_axis_answer_valid(self):
        self.assertEqual(parse_axis_answer(answer("FAULT_STANDARD", None, "ORDER_OPPORTUNITY"), IDS),
                         {1: "FAULT_STANDARD", 2: None, 3: "ORDER_OPPORTUNITY"})
        fenced = "```json\n" + answer("FAULT_STANDARD", None, None) + "\n```"
        self.assertEqual(parse_axis_answer(fenced, IDS)[1], "FAULT_STANDARD")

    def test_parse_axis_answer_invalid_isError(self):
        cases = [
            ("읽을 수 없음", "축은 1번이 FAULT"),
            ("factors 목록", json.dumps({"axes": []})),
            ("답하지 않은 요소: 3", json.dumps({"factors": [{"factorId": 1, "valueAxis": None},
                                                         {"factorId": 2, "valueAxis": None}]})),
            ("두 번", json.dumps({"factors": [{"factorId": i, "valueAxis": None} for i in (1, 1, 2, 3)]})),
            ("목록에 없는 요소 번호: 9", json.dumps({"factors": [{"factorId": i, "valueAxis": None} for i in (1, 2, 3, 9)]})),
            ("목록에 없는 요소 번호: True", json.dumps({"factors": [{"factorId": True, "valueAxis": None},
                                                                {"factorId": 2, "valueAxis": None},
                                                                {"factorId": 3, "valueAxis": None}]})),
            ("허용하지 않는 축 EMBEDDING", answer("EMBEDDING", None, None)),
            ("valueAxis가 없습니다", json.dumps({"factors": [{"factorId": 1}, {"factorId": 2, "valueAxis": None},
                                                         {"factorId": 3, "valueAxis": None}]})),
        ]
        for message, text in cases:
            with self.subTest(message):
                with self.assertRaises(AxisVoteError) as ctx:
                    parse_axis_answer(text, IDS)
                self.assertIn(message, str(ctx.exception))

    def test_parse_axis_answer_trueIsNotFactorOne(self):
        # True == 1이지만 요소 1에 답한 것으로 보지 않는다
        text = json.dumps({"factors": [{"factorId": True, "valueAxis": None},
                                       {"factorId": 2, "valueAxis": None}, {"factorId": 3, "valueAxis": None}]})
        with self.assertRaises(AxisVoteError) as ctx:
            parse_axis_answer(text, IDS)
        self.assertIn("답하지 않은 요소: 1", str(ctx.exception))

    def test_aggregate_votes_majorityAndReview(self):
        answers = [parse_axis_answer(answer(*axes), IDS) for axes in (
            ("FAULT_STANDARD", None, "ORDER_OPPORTUNITY"),
            ("FAULT_STANDARD", None, "PRINCIPLE_RELATION"),
            ("FAULT_STANDARD", "APOLOGY_SINCERITY", "ORDER_OPPORTUNITY"),
            ("PRINCIPLE_RELATION", None, "PRINCIPLE_RELATION"),
            ("FAULT_STANDARD", "APOLOGY_SINCERITY", "APOLOGY_SINCERITY"),
        )]
        votes = aggregate_votes(answers, IDS)
        # 4 : 1 → 과반, 확인 필요 아님
        self.assertEqual(votes[1], {"valueAxis": "FAULT_STANDARD", "valueAxisVotes": {
            "runs": 5, "counts": {"FAULT_STANDARD": 4, "PRINCIPLE_RELATION": 1}, "needsReview": False}})
        # NULL 3 : 2 → 과반이면 NULL도 기본값이 된다. NULL 표는 NONE 키
        self.assertEqual(votes[2]["valueAxis"], None)
        self.assertEqual(votes[2]["valueAxisVotes"]["counts"], {"APOLOGY_SINCERITY": 2, "NONE": 3})
        self.assertFalse(votes[2]["valueAxisVotes"]["needsReview"])
        # 2 : 2 : 1 → 과반 아님 · 동률 → 확인 필요, 축 순서상 앞의 것(PRINCIPLE_RELATION ③ < ORDER_OPPORTUNITY ④)
        self.assertEqual(votes[3]["valueAxis"], "PRINCIPLE_RELATION")
        self.assertTrue(votes[3]["valueAxisVotes"]["needsReview"])

    def test_aggregate_votes_tiePrefersExtractorValue(self):
        answers = [parse_axis_answer(answer(*axes), IDS) for axes in (
            ("FAULT_STANDARD", None, None), ("ORDER_OPPORTUNITY", None, None))]
        votes = aggregate_votes(answers, IDS, preferred={1: "ORDER_OPPORTUNITY"})
        self.assertEqual(votes[1]["valueAxis"], "ORDER_OPPORTUNITY")  # 1 : 1 동률이면 추출기 값
        self.assertTrue(votes[1]["valueAxisVotes"]["needsReview"])  # 절반은 과반이 아니다
        # 추출기 값이 최다표 안에 없으면 그 값을 쓰지 않는다
        self.assertEqual(aggregate_votes(answers, IDS, preferred={1: "APOLOGY_SINCERITY"})[1]["valueAxis"], "FAULT_STANDARD")

    def test_aggregate_votes_fewValidAnswers_needsReview(self):
        # 5회 중 1개만 유효하면 표가 갈리지 않아도 근거가 약하므로 확인 필요 (과반은 요청 횟수 기준, 리뷰 반영)
        one = [parse_axis_answer(answer("FAULT_STANDARD", None, None), IDS)]
        votes = aggregate_votes(one, IDS, requested_runs=5)
        self.assertEqual(votes[1]["valueAxisVotes"],
                         {"runs": 1, "counts": {"FAULT_STANDARD": 1}, "needsReview": True, "requestedRuns": 5})
        # 5회 중 3개가 유효하고 3표가 모이면 과반 (3 > 5 / 2)
        three = one * 3
        self.assertFalse(aggregate_votes(three, IDS, requested_runs=5)[1]["valueAxisVotes"]["needsReview"])
        # 요청 횟수를 주지 않으면 유효 응답 수 기준이고 requestedRuns를 남기지 않는다
        self.assertFalse(aggregate_votes(one, IDS)[1]["valueAxisVotes"]["needsReview"])
        self.assertNotIn("requestedRuns", aggregate_votes(one, IDS)[1]["valueAxisVotes"])

    def test_aggregate_votes_emptyIsError(self):
        with self.assertRaises(AxisVoteError):
            aggregate_votes([], IDS)

    def test_apply_votes_overwritesCopy(self):
        report = {"factorExtras": [{"factorId": 1, "summaryTag": "a", "valueAxis": "FAULT_STANDARD"},
                                   {"factorId": 2, "summaryTag": "b", "valueAxis": None}]}
        votes = [{"factorId": 2, "valueAxis": "APOLOGY_SINCERITY",
                  "valueAxisVotes": {"runs": 1, "counts": {"APOLOGY_SINCERITY": 1}, "needsReview": False}}]
        merged = apply_votes(report, votes)
        self.assertEqual(merged["factorExtras"][1]["valueAxis"], "APOLOGY_SINCERITY")
        self.assertEqual(merged["factorExtras"][1]["valueAxisVotes"]["runs"], 1)
        self.assertEqual(merged["factorExtras"][0], report["factorExtras"][0])  # 투표가 없는 요소는 그대로
        self.assertIsNone(report["factorExtras"][1]["valueAxis"])  # 원본은 바꾸지 않는다


if __name__ == "__main__":
    unittest.main()
