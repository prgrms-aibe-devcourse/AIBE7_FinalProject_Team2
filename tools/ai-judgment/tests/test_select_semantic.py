"""select 판결 이유 임베딩 유사도 (BE-55) 테스트. Gemini는 호출하지 않는다(임베딩은 가짜 함수).

실행: tools/ai-judgment에서 `python3 -m unittest discover tests`
"""

import sys
import unittest
from pathlib import Path
from unittest import mock

TOOL_DIR = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(TOOL_DIR))

import select_semantic  # noqa: E402
from select_semantic import WEIGHT_RANGE, option_errors, reasoning_similarity_scores  # noqa: E402


class FakeEmbed:
    """글자 → 벡터 표. 받은 요청을 기록해 무엇을 보냈는지 확인한다."""

    def __init__(self, table):
        self.table, self.calls = table, []

    def __call__(self, texts):
        self.calls.append(list(texts))
        return [self.table[t] for t in texts]


def record(reasoning=None, index=1):
    output = {"factors": []}
    if reasoning is not None:
        output["reasoning"] = reasoning
    return {"modelSpec": "gemini:a", "runIndex": index, "output": output,
            "validation": {"ok": True, "warnings": []}}


class ReasoningSimilarityScoresTest(unittest.TestCase):
    def test_single_candidate_returns_none(self):
        self.assertIsNone(reasoning_similarity_scores([record("이유1")]))

    def test_empty_list_returns_none(self):
        self.assertIsNone(reasoning_similarity_scores([]))

    def test_missing_reasoning_returns_none(self):
        candidates = [record("이유1"), record(None, index=2)]
        self.assertIsNone(reasoning_similarity_scores(candidates))

    def test_blank_reasoning_returns_none(self):
        candidates = [record("이유1"), record("   ", index=2)]
        self.assertIsNone(reasoning_similarity_scores(candidates))

    def test_scores_favor_the_pair_that_agrees(self):
        """3개 후보 중 2개(이유B · 이유C)가 서로 비슷하고 1개(이유A)만 동떨어진 경우,
        평균 유사도 점수는 B · C가 A보다 높아야 한다(medoid 효과)."""
        a, b, c = record("이유A", 1), record("이유B", 2), record("이유C", 3)
        table = {"이유A": [1.0, 0.0], "이유B": [0.0, 1.0], "이유C": [0.0, 0.9]}
        scores = reasoning_similarity_scores([a, b, c], embed_fn=FakeEmbed(table))
        self.assertLess(scores[id(a)], scores[id(b)])
        self.assertLess(scores[id(a)], scores[id(c)])
        self.assertAlmostEqual(scores[id(b)], scores[id(c)])

    def test_sends_only_reasoning_text(self):
        a, b = record("이유A", 1), record("이유B", 2)
        fake = FakeEmbed({"이유A": [1.0, 0.0], "이유B": [0.0, 1.0]})
        reasoning_similarity_scores([a, b], embed_fn=fake)
        self.assertEqual(fake.calls, [["이유A", "이유B"]])

    def test_vector_count_mismatch_raises(self):
        a, b = record("이유A", 1), record("이유B", 2)

        def bad_embed(texts):
            return [[1.0, 0.0]]  # 2개를 보냈는데 1개만 돌려줌

        with self.assertRaises(ValueError):
            reasoning_similarity_scores([a, b], embed_fn=bad_embed)


class OptionErrorsTest(unittest.TestCase):
    def test_default_weight_is_valid(self):
        self.assertEqual(option_errors({}), [])

    def test_weight_in_range_is_valid(self):
        self.assertEqual(option_errors({"semanticWeight": 0.3}), [])
        self.assertEqual(option_errors({"semanticWeight": WEIGHT_RANGE[0]}), [])
        self.assertEqual(option_errors({"semanticWeight": WEIGHT_RANGE[1]}), [])

    def test_weight_out_of_range_is_invalid(self):
        self.assertTrue(option_errors({"semanticWeight": 1.5}))
        self.assertTrue(option_errors({"semanticWeight": -0.1}))

    def test_weight_wrong_type_is_invalid(self):
        self.assertTrue(option_errors({"semanticWeight": "0.3"}))
        self.assertTrue(option_errors({"semanticWeight": True}))


class DefaultEmbedTest(unittest.TestCase):
    def test_pins_model_and_task_type(self):
        with mock.patch("select_semantic.embed") as mocked:
            mocked.return_value = [[0.1, 0.2]]
            select_semantic.default_embed(["이유1"])
            mocked.assert_called_once_with(["이유1"], model=select_semantic.EMBEDDING_MODEL,
                                            task_type=select_semantic.TASK_TYPE)


if __name__ == "__main__":
    unittest.main()
