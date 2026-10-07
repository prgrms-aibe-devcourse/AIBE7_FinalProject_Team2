"""LLM 공급자 호출 · 다중 모델 생성 · 비교표 테스트 (BE-30). 네트워크를 쓰지 않는다.

실행: tools/ai-judgment에서 `python3 -m unittest discover tests`
"""

import copy
import json
import os
import sys
import tempfile
import unittest
from pathlib import Path
from unittest import mock

TOOL_DIR = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(TOOL_DIR))

import llm  # noqa: E402
from common import load_json  # noqa: E402
from compare import CompareError, load_runs, parse_prices, render_csv, render_markdown, summarize  # noqa: E402
from generate import GenerateError, generate_runs, import_runs, model_slug  # noqa: E402

EXAMPLES = TOOL_DIR / "examples"
FAKE_KEYS = {"OPENAI_API_KEY": "sk-test-openai", "ANTHROPIC_API_KEY": "sk-ant-test", "GEMINI_API_KEY": "gm-test"}


class FakeHttp:
    """공급자 HTTP 응답을 흉내 낸다. 받은 요청을 기록한다."""

    def __init__(self, response):
        self.response = response
        self.requests = []

    def __call__(self, url, headers, body, timeout):
        self.requests.append({"url": url, "headers": headers, "body": body, "timeout": timeout})
        return self.response


@mock.patch.dict(os.environ, FAKE_KEYS)
class ProviderTest(unittest.TestCase):
    def test_parse_model_spec(self):
        self.assertEqual(llm.parse_model_spec("openai:gpt-5"), ("openai", "gpt-5"))
        self.assertEqual(llm.parse_model_spec("Gemini:models/x:y"), ("gemini", "models/x:y"))
        self.assertEqual(llm.parse_model_spec("manual:gemini-app"), ("manual", "gemini-app"))
        for bad in ("gpt-5", "openai:", "unknown:model"):
            with self.assertRaises(llm.LLMError):
                llm.parse_model_spec(bad)

    def test_openai_request_and_usage(self):
        http = FakeHttp({
            "model": "gpt-x-2026",
            "choices": [{"message": {"content": '{"a": 1}'}, "finish_reason": "stop"}],
            "usage": {"prompt_tokens": 100, "completion_tokens": 50, "total_tokens": 150,
                      "completion_tokens_details": {"reasoning_tokens": 30}},
        })
        result = llm.call("openai:gpt-x", "SYS", "USER", http=http)
        request = http.requests[0]
        self.assertTrue(request["url"].endswith("/chat/completions"))
        self.assertEqual(request["headers"]["Authorization"], "Bearer sk-test-openai")
        self.assertEqual(request["body"]["messages"][0], {"role": "system", "content": "SYS"})
        self.assertEqual(request["body"]["response_format"], {"type": "json_object"})
        self.assertNotIn("temperature", request["body"])  # 지정하지 않으면 보내지 않는다
        self.assertNotIn("tools", request["body"])
        self.assertEqual(result.text, '{"a": 1}')
        self.assertEqual(result.served_model, "gpt-x-2026")
        self.assertEqual(result.usage["totalTokens"], 150)
        self.assertEqual(result.usage["thinkingTokensIncludedInOutput"], 30)
        self.assertIsNotNone(result.latency_seconds)

    def test_openai_refusal(self):
        http = FakeHttp({"choices": [{"message": {"content": None, "refusal": "no"}}]})
        with self.assertRaises(llm.LLMError):
            llm.call("openai:gpt-x", "S", "U", http=http)

    def test_anthropic_request_and_usage(self):
        http = FakeHttp({
            "model": "claude-x",
            "content": [{"type": "thinking", "thinking": "..."}, {"type": "text", "text": "{}"}],
            "usage": {"input_tokens": 10, "output_tokens": 20},
            "stop_reason": "end_turn",
        })
        result = llm.call("anthropic:claude-x", "SYS", "USER", temperature=0.2, http=http)
        request = http.requests[0]
        self.assertEqual(request["headers"]["x-api-key"], "sk-ant-test")
        self.assertEqual(request["body"]["system"], "SYS")
        self.assertEqual(request["body"]["temperature"], 0.2)
        self.assertEqual(result.text, "{}")
        self.assertEqual(result.usage, {"inputTokens": 10, "outputTokens": 20, "totalTokens": 30})

    def test_gemini_request_and_usage(self):
        http = FakeHttp({
            "modelVersion": "gemini-x-001",
            "candidates": [{"content": {"parts": [{"text": "생각", "thought": True}, {"text": "{}"}]},
                            "finishReason": "STOP"}],
            "usageMetadata": {"promptTokenCount": 5, "candidatesTokenCount": 7, "thoughtsTokenCount": 3,
                              "totalTokenCount": 15},
        })
        result = llm.call("gemini:gemini-x", "SYS", "USER", http=http)
        request = http.requests[0]
        self.assertIn("/models/gemini-x:generateContent", request["url"])
        self.assertEqual(request["headers"]["x-goog-api-key"], "gm-test")
        self.assertNotIn("tools", request["body"])  # 검색 연동을 붙이지 않는다
        self.assertEqual(request["body"]["generationConfig"]["responseMimeType"], "application/json")
        self.assertEqual(result.text, "{}")  # 사고 파트는 뺀다
        self.assertEqual(result.usage["totalTokens"], 15)
        self.assertEqual(result.usage["thinkingTokens"], 3)

    def test_gemini_blocked(self):
        with self.assertRaises(llm.LLMError):
            llm.call("gemini:x", "S", "U", http=FakeHttp({"promptFeedback": {"blockReason": "SAFETY"}}))

    def test_missing_key(self):
        with mock.patch.dict(os.environ, {}, clear=True):
            with self.assertRaises(llm.LLMError) as ctx:
                llm.call("openai:gpt-x", "S", "U", http=FakeHttp({}))
        self.assertIn("OPENAI_API_KEY", str(ctx.exception))

    def test_manual_cannot_call(self):
        with self.assertRaises(llm.LLMError):
            llm.call("manual:app", "S", "U", http=FakeHttp({}))


def fake_caller(outputs):
    """모델별로 정해 둔 응답을 차례로 돌려주는 호출 함수. 값이 Exception이면 호출 실패로 만든다."""
    queues = {spec: list(items) for spec, items in outputs.items()}

    def caller(spec, system, user, **kwargs):
        item = queues[spec].pop(0)
        if isinstance(item, Exception):
            raise item
        provider, model = llm.parse_model_spec(spec)
        text = item if isinstance(item, str) else json.dumps(item, ensure_ascii=False)
        return llm.LLMResult(text, provider, model, usage={"inputTokens": 1000, "outputTokens": 500,
                                                           "totalTokens": 1500}, latency_seconds=2.0)

    return caller


class GenerateCompareTest(unittest.TestCase):
    def setUp(self):
        self.case = load_json(EXAMPLES / "case_input.json")
        self.output = load_json(EXAMPLES / "ai_output_sample.json")
        self.court = load_json(EXAMPLES / "court_judgment_internal.json")
        self.tmp = tempfile.TemporaryDirectory()
        self.batch_dir = Path(self.tmp.name) / "case"

    def tearDown(self):
        self.tmp.cleanup()

    def variant(self, **changes):
        output = copy.deepcopy(self.output)
        output.update(changes)
        return output

    def run_batch(self):
        caller = fake_caller({
            "openai:gpt-x": [self.output, self.variant(prisonMonths=132), "설명만 있고 JSON이 아님"],
            "gemini:gem-x": [self.variant(penaltyType="DEATH"), llm.LLMError("API 오류 (429): quota")],
        })
        quiet = lambda *_: None  # noqa: E731
        return (generate_runs(self.case, ["openai:gpt-x"], 3, self.batch_dir, caller=caller, log=quiet)
                + generate_runs(self.case, ["gemini:gem-x"], 2, self.batch_dir, caller=caller, log=quiet))

    def test_generate_records_files(self):
        records = self.run_batch()
        self.assertEqual(len(records), 5)
        model_dir = self.batch_dir / model_slug("openai:gpt-x")
        self.assertTrue((self.batch_dir / "batch.json").exists())
        self.assertTrue((self.batch_dir / "prompt.md").exists())
        self.assertEqual(sorted(p.name for p in model_dir.iterdir()),
                         ["run-001.json", "run-001.output.json", "run-002.json", "run-002.output.json",
                          "run-003.json"])
        self.assertEqual(load_json(model_dir / "run-001.output.json"), self.output)
        statuses = [(r["callError"] is not None, r["parseError"] is not None, r["validation"]["ok"]) for r in records]
        self.assertEqual(statuses, [(False, False, True), (False, False, True), (False, True, False),
                                    (False, False, False), (True, False, False)])
        # 사형인데 징역 개월이 있어 검증 실패 (gemini 첫 회차)
        self.assertTrue(records[3]["validation"]["errors"])

    def test_run_index_continues(self):
        self.run_batch()
        caller = fake_caller({"openai:gpt-x": [self.output]})
        records = generate_runs(self.case, ["openai:gpt-x"], 1, self.batch_dir, caller=caller, log=lambda *_: None)
        self.assertEqual(records[0]["runIndex"], 4)

    def test_batch_rejects_other_prompt(self):
        self.run_batch()
        other = copy.deepcopy(self.case)
        other["overview"] += " 추가"
        with self.assertRaises(GenerateError):
            generate_runs(other, ["openai:gpt-x"], 1, self.batch_dir, caller=fake_caller({}), log=lambda *_: None)

    def test_missing_key_stops_before_runs(self):
        with mock.patch.dict(os.environ, {}, clear=True):
            with self.assertRaises(llm.LLMError):
                generate_runs(self.case, ["openai:gpt-x"], 3, self.batch_dir, log=lambda *_: None)
        self.assertFalse(self.batch_dir.exists())

    def test_manual_rules(self):
        with self.assertRaises(GenerateError):
            generate_runs(self.case, ["manual:app"], 1, self.batch_dir, caller=fake_caller({}), log=lambda *_: None)
        response = Path(self.tmp.name) / "answer.json"
        response.write_text("```json\n" + json.dumps(self.output, ensure_ascii=False) + "\n```", encoding="utf-8")
        records = import_runs(self.case, "manual:gemini-app", [response], self.batch_dir, log=lambda *_: None)
        self.assertTrue(records[0]["validation"]["ok"])
        self.assertEqual(records[0]["meta"]["source"], "answer.json")
        with self.assertRaises(GenerateError):
            import_runs(self.case, "openai:gpt-x", [response], self.batch_dir, log=lambda *_: None)

    def test_compare_summary(self):
        self.run_batch()
        batch, groups = load_runs(self.batch_dir)
        rows, details, consensus = summarize(self.case, groups, self.court,
                                             parse_prices(["openai:gpt-x=2,10"]))
        by_model = {row["model"]: row for row in rows}
        openai_row, gemini_row = by_model["openai:gpt-x"], by_model["gemini:gem-x"]
        self.assertEqual((openai_row["runs"], openai_row["valid"], openai_row["parseFailed"]), (3, 2, 1))
        self.assertEqual(openai_row["penalties"], "PRISON×2")
        self.assertEqual((openai_row["prisonMin"], openai_row["prisonMedian"], openai_row["prisonMax"]),
                         (132, 138, 144))
        self.assertEqual(openai_row["inRecommended"], 2)  # 권고 84 ~ 144개월
        self.assertEqual(openai_row["factorConsistency"], 1.0)  # 요소 방향은 두 회차가 같다
        self.assertEqual(openai_row["courtPenaltyMatch"], 1.0)
        self.assertEqual(openai_row["courtPrisonDiffAvg"], 18)  # |144-120|, |132-120| 평균
        self.assertAlmostEqual(openai_row["avgCostUsd"], (1000 * 2 + 500 * 10) / 1_000_000)
        self.assertEqual((gemini_row["callFailed"], gemini_row["valid"]), (1, 0))
        self.assertIsNone(gemini_row["factorConsistency"])
        self.assertIsNone(gemini_row["avgCostUsd"])
        self.assertEqual(len(details), 5)
        self.assertEqual({d for d in consensus.values()} - {"UP", "DOWN", "-"}, set())

        markdown = render_markdown(batch, rows, details, consensus, self.case, with_court=True)
        self.assertIn("| openai:gpt-x | 3 |", markdown)
        self.assertIn("## 토큰 · 속도 · 비용", markdown)
        self.assertIn("내부 전용", markdown)
        csv_text = render_csv(rows, with_court=False)
        self.assertNotIn("실제 판결", csv_text)
        self.assertEqual(len(csv_text.strip().splitlines()), 3)

    def test_compare_requires_batch(self):
        with self.assertRaises(CompareError):
            load_runs(Path(self.tmp.name) / "none")
        with self.assertRaises(CompareError):
            parse_prices(["openai:gpt-x=abc"])

    def test_no_key_in_records(self):
        with mock.patch.dict(os.environ, FAKE_KEYS):
            self.run_batch()
        for path in self.batch_dir.rglob("*"):
            if path.is_file():
                text = path.read_text(encoding="utf-8")
                for key in FAKE_KEYS.values():
                    self.assertNotIn(key, text)


if __name__ == "__main__":
    unittest.main()
