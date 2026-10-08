"""LLM 공급자 호출 · 다중 모델 생성 · 비교표 테스트 (BE-30). 네트워크를 쓰지 않는다.

실행: tools/ai-judgment에서 `python3 -m unittest discover tests`
"""

import copy
import io
import json
import os
import socket
import sys
import tempfile
import unittest
import urllib.error
from pathlib import Path
from unittest import mock

TOOL_DIR = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(TOOL_DIR))

import llm  # noqa: E402
from common import load_json  # noqa: E402
from compare import CompareError, check_court_case, load_runs, parse_prices, render_csv, render_markdown, summarize  # noqa: E402
from generate import GenerateError, describe, generate_runs, import_runs, main as generate_main, model_slug  # noqa: E402
from compare import main as compare_main  # noqa: E402

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

    def test_post_json_rejects_non_object_body(self):
        for body in (b"<html>gateway</html>", b"[1, 2]", b"\xff\xfe"):
            with mock.patch("urllib.request.urlopen", return_value=io.BytesIO(body)):
                with self.assertRaises(llm.LLMError):
                    llm._post_json("https://example.invalid", {}, {}, 1)
        with mock.patch("urllib.request.urlopen", return_value=io.BytesIO(b'{"ok": true}')):
            self.assertEqual(llm._post_json("https://example.invalid", {}, {}, 1), {"ok": True})

    def test_ssl_context_falls_back_to_system_ca(self):
        class FakeContext:
            def __init__(self, count):
                self.count, self.loaded = count, []

            def cert_store_stats(self):
                return {"x509_ca": self.count}

            def load_verify_locations(self, cafile=None):
                self.loaded.append(cafile)
                self.count = 100

        llm.ssl_context.cache_clear()
        self.addCleanup(llm.ssl_context.cache_clear)
        # 기본 위치에 인증서가 없으면 존재하는 첫 시스템 CA 묶음을 추가한다
        empty = FakeContext(0)
        with mock.patch("ssl.create_default_context", return_value=empty), \
                mock.patch("os.path.isfile", side_effect=lambda p: p == llm.SYSTEM_CA_FILES[1]):
            self.assertIs(llm.ssl_context(), empty)
        self.assertEqual(empty.loaded, [llm.SYSTEM_CA_FILES[1]])
        # 기본 위치에 인증서가 이미 있으면 건드리지 않는다
        llm.ssl_context.cache_clear()
        loaded = FakeContext(50)
        with mock.patch("ssl.create_default_context", return_value=loaded):
            llm.ssl_context()
        self.assertEqual(loaded.loaded, [])

    def test_ssl_context_keeps_verification_on(self):
        llm.ssl_context.cache_clear()
        self.addCleanup(llm.ssl_context.cache_clear)
        import ssl
        context = llm.ssl_context()
        self.assertEqual(context.verify_mode, ssl.CERT_REQUIRED)
        self.assertTrue(context.check_hostname)

    def test_post_json_uses_ssl_context(self):
        with mock.patch("urllib.request.urlopen", return_value=io.BytesIO(b'{"ok": true}')) as urlopen:
            llm._post_json("https://example.invalid", {}, {}, 1)
        self.assertIs(urlopen.call_args.kwargs["context"], llm.ssl_context())

    def test_post_json_cert_error_not_retried_with_help(self):
        import ssl
        error = urllib.error.URLError(ssl.SSLCertVerificationError(1, "unable to get local issuer certificate"))
        with mock.patch("urllib.request.urlopen", side_effect=error) as urlopen, mock.patch("time.sleep"):
            with self.assertRaises(llm.LLMError) as ctx:
                llm._post_json("https://example.invalid", {}, {}, 1)
        self.assertEqual(urlopen.call_count, 1)
        self.assertIn("SSL_CERT_FILE", str(ctx.exception))

    def test_post_json_connect_timeout_not_retried(self):
        error = urllib.error.URLError(socket.timeout("timed out"))
        with mock.patch("urllib.request.urlopen", side_effect=error) as urlopen, mock.patch("time.sleep"):
            with self.assertRaises(llm.LLMError) as ctx:
                llm._post_json("https://example.invalid", {}, {}, 7)
        self.assertEqual(urlopen.call_count, 1)
        self.assertIn("7초", str(ctx.exception))

    def test_post_json_connection_error_retried(self):
        error = urllib.error.URLError("connection refused")
        with mock.patch("urllib.request.urlopen", side_effect=error) as urlopen, mock.patch("time.sleep"):
            with self.assertRaises(llm.LLMError):
                llm._post_json("https://example.invalid", {}, {}, 1)
        self.assertEqual(urlopen.call_count, llm.MAX_ATTEMPTS)

    def test_free_models_file_is_valid(self):
        data = load_json(TOOL_DIR / "free_models.json")
        self.assertRegex(data["checkedAt"], r"^\d{4}-\d{2}-\d{2}$")
        for provider, entry in data.items():
            if provider.startswith("_") or provider == "checkedAt":
                continue
            self.assertIn(provider, llm.PROVIDERS)
            self.assertTrue(entry["source"].startswith("https://"))
            self.assertTrue(entry["models"])
            self.assertEqual(len(set(entry["models"])), len(entry["models"]))
            self.assertFalse(set(entry["models"]) & set(entry.get("notWithFreeTier", [])))  # 무료 · 비무료 겹침 없음

    def test_has_free_tier(self):
        free = llm.free_tier_models()
        self.assertIn("gemini:gemini-2.5-flash", free)
        self.assertTrue(llm.has_free_tier("gemini:gemini-2.5-flash"))
        self.assertTrue(llm.has_free_tier(" Gemini : gemini-2.5-flash "))
        self.assertFalse(llm.has_free_tier("gemini:gemini-3.1-pro-preview"))  # 무료 등급이 없는 모델
        self.assertFalse(llm.has_free_tier("claude-opus-5-5"))  # 공급자 없는 이름
        self.assertFalse(llm.has_free_tier("openai:gpt-x"))
        self.assertFalse(llm.has_free_tier(None))

    def test_free_models_file_missing_or_broken_is_empty(self):
        for content in (None, "{not json", "[1, 2]", '{"gemini": {"models": "x"}}'):
            with tempfile.TemporaryDirectory() as tmp:
                path = Path(tmp) / "free_models.json"
                if content is not None:
                    path.write_text(content, encoding="utf-8")
                with mock.patch.object(llm, "FREE_MODELS_FILE", path):
                    self.assertEqual(llm.free_tier_models(), set(), msg=str(content))

    def test_model_provider(self):
        self.assertEqual(llm.model_provider("claude-opus-5-5"), "anthropic")  # 공급자 없는 이름은 Claude SDK
        self.assertEqual(llm.model_provider("anthropic:claude-x"), "anthropic")
        self.assertEqual(llm.model_provider("openai:gpt-x"), "openai")
        self.assertEqual(llm.model_provider("gemini:g:x"), "gemini")
        with self.assertRaises(llm.LLMError):
            llm.model_provider("unknown:x")

    def test_manual_cannot_call(self):
        with self.assertRaises(llm.LLMError):
            llm.call("manual:app", "S", "U", http=FakeHttp({}))


def http_error(code, body=None, headers=None):
    data = json.dumps(body).encode("utf-8") if body is not None else b""
    return urllib.error.HTTPError("https://example.invalid", code, "err", headers or {}, io.BytesIO(data))


def gemini_429(retry_delay=None, per_day=False):
    """Gemini 무료 등급 429 본문: 대기 시간은 RetryInfo, 일 한도는 QuotaFailure(...PerDay...)로 온다."""
    details = []
    if per_day:
        details.append({"@type": "type.googleapis.com/google.rpc.QuotaFailure", "violations": [
            {"quotaMetric": "generativelanguage.googleapis.com/generate_content_free_tier_requests",
             "quotaId": "GenerateRequestsPerDayPerProjectPerModel-FreeTier"}]})
    if retry_delay:
        details.append({"@type": "type.googleapis.com/google.rpc.RetryInfo", "retryDelay": retry_delay})
    return http_error(429, {"error": {"code": 429, "message": "You exceeded your current quota",
                                      "status": "RESOURCE_EXHAUSTED", "details": details}})


OK_BODY = b'{"ok": true}'


def repeat(factory, count):
    """오류 응답은 본문을 한 번만 읽을 수 있어 매번 새로 만든다."""
    return [factory() for _ in range(count)]


class RetryPolicyTest(unittest.TestCase):
    """재시도 정책 (BE-36). 네트워크 · 실제 대기 없이 time.sleep · random을 가짜로 바꿔 확인한다."""

    def setUp(self):
        llm.reset_retry()
        self.addCleanup(llm.reset_retry)
        self.sleep = mock.patch("time.sleep").start()
        mock.patch("random.uniform", return_value=1.0).start()  # 지터를 없앤다
        self.addCleanup(mock.patch.stopall)

    def post(self, *responses):
        """응답을 차례로 주는 urlopen으로 _post_json을 부른다. (결과, urlopen 호출 수)"""
        with mock.patch("urllib.request.urlopen", side_effect=list(responses)) as urlopen:
            try:
                return llm._post_json("https://example.invalid", {}, {}, 1), urlopen.call_count
            except llm.LLMError as e:
                e.calls = urlopen.call_count
                raise

    def waits(self):
        return [call.args[0] for call in self.sleep.call_args_list]

    def test_overload_backs_off_exponentially_then_succeeds(self):
        result, calls = self.post(http_error(503), http_error(529), http_error(503), io.BytesIO(OK_BODY))
        self.assertEqual(result, {"ok": True})
        self.assertEqual(calls, 4)
        self.assertEqual(self.waits(), [5.0, 10.0, 20.0])

    def test_overload_wait_is_capped_by_max_wait(self):
        llm.configure_retry(max_attempts=6, max_wait=12)
        self.post(*repeat(lambda: http_error(503), 5), io.BytesIO(OK_BODY))
        self.assertEqual(self.waits(), [5.0, 10.0, 12.0, 12.0, 12.0])

    def test_overload_exhausted_raises_overloaded_error(self):
        with self.assertRaises(llm.LLMOverloadedError) as ctx:
            self.post(*repeat(lambda: http_error(503, {"error": {"message": "high demand"}}), 5))
        self.assertEqual((ctx.exception.kind, ctx.exception.calls), ("overloaded", llm.DEFAULT_MAX_ATTEMPTS))
        self.assertIn("과부하", str(ctx.exception))
        self.assertIn("high demand", str(ctx.exception))
        self.assertEqual(len(self.waits()), llm.DEFAULT_MAX_ATTEMPTS - 1)

    def test_rate_limit_follows_retry_after_header(self):
        self.post(http_error(429, headers={"retry-after": "7"}), io.BytesIO(OK_BODY))
        self.assertEqual(self.waits(), [8.0])  # 안내 + 여유 1초

    def test_rate_limit_follows_gemini_retry_info_in_body(self):
        self.post(gemini_429(retry_delay="41.5s"), io.BytesIO(OK_BODY))
        self.assertEqual(self.waits(), [42.5])

    def test_rate_limit_without_hint_waits_a_minute(self):
        self.post(http_error(429), io.BytesIO(OK_BODY))
        self.assertEqual(self.waits(), [60.0])
        self.sleep.reset_mock()
        llm.configure_retry(max_wait=30)
        self.post(http_error(429), io.BytesIO(OK_BODY))
        self.assertEqual(self.waits(), [30.0])

    def test_rate_limit_hint_longer_than_max_wait_is_not_waited(self):
        with self.assertRaises(llm.LLMRateLimitError) as ctx:
            self.post(http_error(429, headers={"retry-after": "600"}))
        self.assertEqual(ctx.exception.calls, 1)
        self.sleep.assert_not_called()
        self.assertIn("maxWait", str(ctx.exception))

    def test_rate_limit_exhausted_raises_rate_limit_error(self):
        with self.assertRaises(llm.LLMRateLimitError) as ctx:
            self.post(*repeat(lambda: http_error(429, headers={"retry-after": "1"}), 5))
        self.assertEqual((ctx.exception.kind, ctx.exception.calls), ("rate_limit", 5))
        self.assertIn("분당", str(ctx.exception))

    def test_daily_quota_is_not_retried(self):
        with self.assertRaises(llm.LLMQuotaExhaustedError) as ctx:
            self.post(gemini_429(retry_delay="30s", per_day=True))
        self.assertEqual((ctx.exception.kind, ctx.exception.calls), ("daily_quota", 1))
        self.sleep.assert_not_called()
        self.assertIn("한도를 모두 썼습니다", str(ctx.exception))

    def test_openai_insufficient_quota_is_not_retried(self):
        error = http_error(429, {"error": {"message": "You exceeded your current quota", "code": "insufficient_quota"}})
        with self.assertRaises(llm.LLMQuotaExhaustedError) as ctx:
            self.post(error)
        self.assertEqual(ctx.exception.calls, 1)

    def test_other_server_error_uses_short_backoff(self):
        self.post(http_error(500), http_error(502), io.BytesIO(OK_BODY))
        self.assertEqual(self.waits(), [2.0, 4.0])

    def test_client_error_is_not_retried(self):
        with self.assertRaises(llm.LLMError) as ctx:
            self.post(http_error(400, {"error": {"message": "bad request"}}))
        self.assertIs(type(ctx.exception), llm.LLMError)  # 원인 구분 하위 클래스가 아니다
        self.assertEqual(ctx.exception.calls, 1)
        self.assertIn("bad request", str(ctx.exception))

    def test_jitter_stays_within_max_wait(self):
        llm.configure_retry(max_attempts=3, max_wait=6)
        with mock.patch("random.uniform", return_value=1.25):
            self.post(http_error(503), http_error(503), io.BytesIO(OK_BODY))
        self.assertEqual(self.waits(), [6.0, 6.0])  # 6.25 · 12.5 → 6으로 자른다

    def test_configure_retry(self):
        self.assertEqual(llm.configure_retry(max_attempts=2, max_wait=3),
                         {"max_attempts": 2, "max_wait": 3.0})
        with self.assertRaises(llm.LLMOverloadedError) as ctx:
            self.post(http_error(503), http_error(503), http_error(503))
        self.assertEqual(ctx.exception.calls, 2)
        for bad in ({"max_attempts": 0}, {"max_attempts": True}, {"max_attempts": 1.5},
                    {"max_wait": -1}, {"max_wait": "60"}):
            with self.assertRaises(llm.LLMError, msg=bad):
                llm.configure_retry(**bad)
        llm.reset_retry()
        self.assertEqual(llm.configure_retry(), {"max_attempts": llm.DEFAULT_MAX_ATTEMPTS,
                                                 "max_wait": llm.DEFAULT_MAX_WAIT})

    def test_failure_kinds_are_llm_errors(self):
        for error, kind in ((llm.LLMOverloadedError, "overloaded"), (llm.LLMRateLimitError, "rate_limit"),
                            (llm.LLMQuotaExhaustedError, "daily_quota")):
            self.assertTrue(issubclass(error, llm.LLMError))
            self.assertEqual(error.kind, kind)
        self.assertIsNone(llm.LLMError.kind)


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


@mock.patch.dict(os.environ, FAKE_KEYS)  # 생성 전 키 확인은 caller와 관계없이 항상 한다
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
            "openai:gpt-x": [self.output, self.variant(prisonMonths=132), "설명만 있고 JSON이 아님", "[1, 2, 3]"],
            "gemini:gem-x": [self.variant(penaltyType="DEATH"), llm.LLMError("API 오류 (429): quota")],
        })
        quiet = lambda *_: None  # noqa: E731
        return (generate_runs(self.case, ["openai:gpt-x"], 4, self.batch_dir, caller=caller, log=quiet)
                + generate_runs(self.case, ["gemini:gem-x"], 2, self.batch_dir, caller=caller, log=quiet))

    def test_generate_records_files(self):
        records = self.run_batch()
        self.assertEqual(len(records), 6)
        model_dir = self.batch_dir / model_slug("openai:gpt-x")
        self.assertTrue((self.batch_dir / "batch.json").exists())
        self.assertTrue((self.batch_dir / "prompt.md").exists())
        self.assertEqual(sorted(p.name for p in model_dir.iterdir()),
                         ["run-001.factor-labels.json", "run-001.json", "run-001.output.json",
                          "run-002.factor-labels.json", "run-002.json", "run-002.output.json",
                          "run-003.json", "run-004.json"])
        self.assertEqual(load_json(model_dir / "run-001.output.json"), self.output)
        # 생성 시점 사건 파일의 요소 라벨 스냅샷도 함께 남겨, 나중에 사건 파일이 바뀌어도
        # to_seed_sql.py --factor-labels로 드리프트를 확인할 수 있게 한다
        self.assertEqual(load_json(model_dir / "run-001.factor-labels.json"), {
            "caseTitle": self.case["title"],
            "labels": {str(f["factorId"]): f["label"] for f in self.case["factors"]},
        })
        statuses = [(r["callError"] is not None, r["parseError"] is not None, r["validation"]["ok"]) for r in records]
        self.assertEqual(statuses, [(False, False, True), (False, False, True), (False, True, False),
                                    (False, True, False), (False, False, False), (True, False, False)])
        # JSON 배열은 판결 객체가 아니므로 파싱 실패로 기록하고, 요약 문구도 깨지지 않는다
        self.assertIsNone(records[3]["output"])
        self.assertIn("JSON 객체가 아닙니다", describe(records[3]))
        # 사형인데 징역 개월이 있어 검증 실패 (gemini 첫 회차)
        self.assertTrue(records[4]["validation"]["errors"])

    def test_run_index_continues(self):
        self.run_batch()
        caller = fake_caller({"openai:gpt-x": [self.output]})
        records = generate_runs(self.case, ["openai:gpt-x"], 1, self.batch_dir, caller=caller, log=lambda *_: None)
        self.assertEqual(records[0]["runIndex"], 5)

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

    def test_wrapped_caller_still_checks_keys(self):
        def wrapper(spec, system, user, **kwargs):  # BE-31처럼 call을 감싼 호출 함수
            raise AssertionError("키가 없으면 호출 전에 멈춰야 한다")

        with mock.patch.dict(os.environ, {}, clear=True):
            with self.assertRaises(llm.LLMError):
                generate_runs(self.case, ["openai:gpt-x"], 1, self.batch_dir, caller=wrapper, log=lambda *_: None)
        self.assertFalse(self.batch_dir.exists())

    def test_quota_exhausted_stops_remaining_runs(self):
        caller = fake_caller({"openai:gpt-x": [self.output, llm.LLMQuotaExhaustedError("한도 소진"), self.output],
                              "gemini:gem-x": [self.output] * 3})
        logs = []
        records = generate_runs(self.case, ["openai:gpt-x", "gemini:gem-x"], 3, self.batch_dir, caller=caller,
                                log=logs.append)
        # openai는 2번째에서 멈춘다(3번째 응답은 꺼내지 않는다). 다음 모델은 정상으로 돈다
        self.assertEqual([r["callErrorKind"] for r in records if r["modelSpec"] == "openai:gpt-x"],
                         [None, "daily_quota"])
        self.assertEqual(len([r for r in records if r["modelSpec"] == "gemini:gem-x"]), 3)
        self.assertTrue(any("남은 1회" in line for line in logs))

    def test_call_error_kind_recorded(self):
        caller = fake_caller({"openai:gpt-x": [llm.LLMOverloadedError("busy"), llm.LLMError("other")]})
        records = generate_runs(self.case, ["openai:gpt-x"], 2, self.batch_dir, caller=caller, log=lambda *_: None)
        self.assertEqual([r["callErrorKind"] for r in records], ["overloaded", None])

    def test_cli_retry_options(self):
        case_path = Path(self.tmp.name) / "case.json"
        case_path.write_text(json.dumps(self.case, ensure_ascii=False), encoding="utf-8")
        self.addCleanup(llm.reset_retry)
        argv = ["generate.py", "run", str(case_path), "--model", "openai:gpt-x", "--runs-dir", self.tmp.name,
                "--max-attempts", "7", "--max-wait", "30"]
        with mock.patch.object(sys, "argv", argv), mock.patch("generate.generate_runs", return_value=[]), \
                mock.patch("sys.stdout", new_callable=io.StringIO):
            generate_main()
        self.assertEqual(llm.configure_retry(), {"max_attempts": 7, "max_wait": 30.0})
        argv[argv.index("7")] = "0"
        with mock.patch.object(sys, "argv", argv), mock.patch("sys.stderr", new_callable=io.StringIO) as err:
            self.assertEqual(generate_main(), 1)
        self.assertIn("maxAttempts", err.getvalue())

    def test_cli_missing_files_print_error(self):
        missing = str(Path(self.tmp.name) / "missing.json")
        with mock.patch.object(sys, "argv", ["generate.py", "run", missing, "--model", "openai:gpt-x"]), \
                mock.patch("sys.stderr", new_callable=io.StringIO) as err:
            self.assertEqual(generate_main(), 1)
        self.assertIn("[오류]", err.getvalue())
        self.run_batch()
        with mock.patch.object(sys, "argv", ["compare.py", str(self.batch_dir), "--court", missing]), \
                mock.patch("sys.stderr", new_callable=io.StringIO) as err:
            self.assertEqual(compare_main(), 1)
        self.assertIn("[오류]", err.getvalue())

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

    def test_import_missing_file_records_nothing(self):
        response = Path(self.tmp.name) / "answer.json"
        response.write_text(json.dumps(self.output, ensure_ascii=False), encoding="utf-8")
        with self.assertRaises(GenerateError) as ctx:
            import_runs(self.case, "manual:app", [response, Path(self.tmp.name) / "none.json"], self.batch_dir,
                        log=lambda *_: None)
        self.assertIn("none.json", str(ctx.exception))
        self.assertFalse(self.batch_dir.exists())

    def test_court_case_must_match_batch(self):
        batch = {"caseTitle": self.case["title"]}
        check_court_case(self.court, batch)  # 예시 파일은 같은 사건
        for court in ({**self.court, "caseTitle": "다른 사건"}, {k: v for k, v in self.court.items() if k != "caseTitle"}):
            with self.assertRaises(CompareError):
                check_court_case(court, batch)

    def test_compare_summary(self):
        self.run_batch()
        batch, groups = load_runs(self.batch_dir)
        rows, details, consensus = summarize(self.case, groups, self.court,
                                             parse_prices(["openai:gpt-x=2,10"]))
        by_model = {row["model"]: row for row in rows}
        openai_row, gemini_row = by_model["openai:gpt-x"], by_model["gemini:gem-x"]
        self.assertEqual((openai_row["runs"], openai_row["valid"], openai_row["parseFailed"]), (4, 2, 2))
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
        self.assertEqual(len(details), 6)
        self.assertEqual(set(consensus.values()) - {"UP", "DOWN", None}, set())
        self.assertIsNone(consensus[1])  # 예시 응답은 1번 요소를 고르지 않는다

        markdown = render_markdown(batch, rows, details, consensus, self.case, with_court=True)
        self.assertIn("| openai:gpt-x | 4 |", markdown)
        self.assertIn("선택 안 함", markdown)
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
