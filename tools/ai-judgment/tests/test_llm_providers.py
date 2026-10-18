"""LLM 공급자 호출 테스트 (BE-84: Groq · NVIDIA NIM 추가). 네트워크를 쓰지 않는다.

실행: tools/ai-judgment에서 `python3 -m unittest discover tests`
"""

import json
import sys
import unittest
from pathlib import Path
from unittest import mock

TOOL_DIR = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(TOOL_DIR))

import llm  # noqa: E402

KEYS = {"OPENAI_API_KEY": "k-openai", "GROQ_API_KEY": "k-groq", "NVIDIA_API_KEY": "k-nvidia"}

CHAT_RESPONSE = {
    "model": "served-model-id",
    "choices": [{"message": {"content": '{"ok": true}'}, "finish_reason": "stop"}],
    "usage": {"prompt_tokens": 11, "completion_tokens": 22, "total_tokens": 33},
}


class FakeHttp:
    """options["http"]에 넣어 요청을 기록하고 정해 둔 응답을 돌려준다."""

    def __init__(self, response=None, error=None):
        self.response = response if response is not None else CHAT_RESPONSE
        self.error = error
        self.calls = []

    def __call__(self, url, headers, body, timeout):
        self.calls.append({"url": url, "headers": headers, "body": body, "timeout": timeout})
        if self.error is not None:
            raise self.error
        return self.response


def http_error(status, body, headers=None):
    """urllib.error.HTTPError를 본문과 함께 만든다 (_error_info가 읽는 모양 그대로)."""
    import io
    import urllib.error
    return urllib.error.HTTPError(
        url="https://example.invalid", code=status, msg="err",
        hdrs=mock.Mock(**{"get.side_effect": (headers or {}).get}),
        fp=io.BytesIO(json.dumps(body).encode("utf-8")))


class ProviderRegistrationTest(unittest.TestCase):
    def test_groq_and_nim_are_supported_providers(self):
        self.assertIn("groq", llm.PROVIDERS)
        self.assertIn("nim", llm.PROVIDERS)
        self.assertEqual(llm.parse_model_spec("groq:llama-3.3-70b-versatile"), ("groq", "llama-3.3-70b-versatile"))
        # NIM 모델 ID에는 `/`가 들어간다. 공급자 구분은 첫 `:`에서만 나눈다
        self.assertEqual(llm.parse_model_spec("nim:nvidia/nemotron-3-super-120b-a12b"),
                         ("nim", "nvidia/nemotron-3-super-120b-a12b"))
        self.assertEqual(llm.model_provider("groq:openai/gpt-oss-20b"), "groq")

    def test_unknown_provider_lists_the_new_ones(self):
        with self.assertRaises(llm.LLMError) as caught:
            llm.parse_model_spec("vertex:some-model")
        self.assertIn("groq", str(caught.exception))
        self.assertIn("nim", str(caught.exception))

    def test_api_key_env_names(self):
        with mock.patch.dict("os.environ", {"GROQ_API_KEY": "k"}, clear=True):
            self.assertEqual(llm.api_key("groq"), "k")
        with mock.patch.dict("os.environ", {"NVIDIA_NIM_API_KEY": "k2"}, clear=True):
            self.assertEqual(llm.api_key("nim"), "k2")  # 두 번째 이름도 읽는다
        with mock.patch.dict("os.environ", {}, clear=True):
            with self.assertRaises(llm.LLMError) as caught:
                llm.api_key("nim")
        self.assertIn("NVIDIA_API_KEY", str(caught.exception))
        self.assertNotIn("k2", str(caught.exception))  # 키 값은 오류에 남기지 않는다


class OpenAiCompatibleCallTest(unittest.TestCase):
    def call(self, spec, http, **kwargs):
        with mock.patch.dict("os.environ", KEYS, clear=True):
            return llm.call(spec, "지시", "사건", http=http, **kwargs)

    def test_groq_request_shape(self):
        http = FakeHttp()
        result = self.call("groq:llama-3.3-70b-versatile", http)
        call = http.calls[0]
        self.assertEqual(call["url"], "https://api.groq.com/openai/v1/chat/completions")
        self.assertEqual(call["headers"]["Authorization"], "Bearer k-groq")
        self.assertEqual(call["body"]["model"], "llama-3.3-70b-versatile")
        # Groq는 max_tokens가 deprecated라 max_completion_tokens를 보낸다
        self.assertIn("max_completion_tokens", call["body"])
        self.assertNotIn("max_tokens", call["body"])
        self.assertEqual(call["body"]["response_format"], {"type": "json_object"})
        self.assertEqual(call["body"]["messages"],
                         [{"role": "system", "content": "지시"}, {"role": "user", "content": "사건"}])
        self.assertEqual(result.provider, "groq")
        self.assertEqual(result.served_model, "served-model-id")
        self.assertEqual(result.usage["inputTokens"], 11)
        self.assertEqual(result.usage["totalTokens"], 33)
        self.assertEqual(result.stop_reason, "stop")

    def test_nim_request_shape(self):
        http = FakeHttp()
        self.call("nim:nvidia/nemotron-3-super-120b-a12b", http)
        call = http.calls[0]
        self.assertEqual(call["url"], "https://integrate.api.nvidia.com/v1/chat/completions")
        self.assertEqual(call["headers"]["Authorization"], "Bearer k-nvidia")
        self.assertEqual(call["body"]["model"], "nvidia/nemotron-3-super-120b-a12b")
        # NIM은 max_completion_tokens를 받지 않는다
        self.assertIn("max_tokens", call["body"])
        self.assertNotIn("max_completion_tokens", call["body"])

    def test_openai_still_uses_its_own_url_and_field(self):
        http = FakeHttp()
        self.call("openai:gpt-5", http)
        call = http.calls[0]
        self.assertEqual(call["url"], "https://api.openai.com/v1/chat/completions")
        self.assertIn("max_completion_tokens", call["body"])

    def test_base_url_env_overrides_per_provider(self):
        http = FakeHttp()
        with mock.patch.dict("os.environ", {**KEYS, "GROQ_BASE_URL": "https://proxy.example/v1/"}, clear=True):
            llm.call("groq:llama-3.1-8b-instant", "", "사건", http=http)
        self.assertEqual(http.calls[0]["url"], "https://proxy.example/v1/chat/completions")
        # system이 비면 시스템 메시지를 넣지 않는다
        self.assertEqual(http.calls[0]["body"]["messages"], [{"role": "user", "content": "사건"}])

    def test_temperature_and_json_output_are_optional(self):
        http = FakeHttp()
        self.call("nim:openai/gpt-oss-20b", http, json_output=False)
        self.assertNotIn("response_format", http.calls[0]["body"])
        self.assertNotIn("temperature", http.calls[0]["body"])
        http = FakeHttp()
        self.call("nim:openai/gpt-oss-20b", http, temperature=0.4)
        self.assertEqual(http.calls[0]["body"]["temperature"], 0.4)

    def test_nim_json_mode_400_gets_a_hint(self):
        http = FakeHttp(error=llm.LLMError("API 오류 (400): response_format not supported", 400))
        with self.assertRaises(llm.LLMError) as caught:
            self.call("nim:some/model", http)
        self.assertIn("JSON 모드", str(caught.exception))

    def test_groq_400_gets_no_json_hint(self):
        """JSON 모드를 모든 모델이 지원하는 공급자에는 안내를 붙이지 않는다."""
        http = FakeHttp(error=llm.LLMError("API 오류 (400): bad request", 400))
        with self.assertRaises(llm.LLMError) as caught:
            self.call("groq:llama-3.3-70b-versatile", http)
        self.assertNotIn("JSON 모드", str(caught.exception))

    def test_quota_error_keeps_its_kind_through_the_hint(self):
        """한도 오류는 그대로 올라가야 대체 체인 · 재시도 판단(kind)이 바뀌지 않는다."""
        http = FakeHttp(error=llm.LLMQuotaExhaustedError("일 한도", 429))
        with self.assertRaises(llm.LLMQuotaExhaustedError) as caught:
            self.call("nim:some/model", http)
        self.assertEqual(caught.exception.kind, "daily_quota")

    def test_refusal_is_an_error(self):
        http = FakeHttp({"choices": [{"message": {"refusal": "거절합니다"}}]})
        with self.assertRaises(llm.LLMError) as caught:
            self.call("groq:llama-3.3-70b-versatile", http)
        self.assertIn("거절", str(caught.exception))


class ErrorClassificationTest(unittest.TestCase):
    def info(self, status, body, headers=None):
        return llm._error_info(http_error(status, body, headers))

    def test_groq_daily_limit_is_quota_exhausted(self):
        body = {"error": {"message": "Rate limit reached for model `llama-3.3-70b-versatile` in organization "
                                     "`org_x` on requests per day (RPD): Limit 1000, Used 1000, Requested 1.",
                          "type": "requests", "code": "rate_limit_exceeded"}}
        info = self.info(429, body, {"retry-after": "60"})
        self.assertTrue(info["exhausted"])
        self.assertEqual(llm._failure_kind(429, info), "daily_quota")

    def test_groq_per_minute_limit_is_retried(self):
        body = {"error": {"message": "Rate limit reached for model `llama-3.3-70b-versatile` in organization "
                                     "`org_x` on tokens per minute (TPM): Limit 6000, Used 6000.",
                          "code": "rate_limit_exceeded"}}
        info = self.info(429, body, {"retry-after": "7.5"})
        self.assertFalse(info["exhausted"])
        self.assertEqual(llm._failure_kind(429, info), "rate_limit")
        self.assertEqual(info["retry_after"], 7.5)  # Groq는 retry-after 헤더(초)를 준다

    def test_nim_problem_detail_body_is_readable(self):
        """NIM은 RFC 7807 모양으로 detail · title에 사유를 담는다."""
        info = self.info(429, {"title": "Too Many Requests", "detail": "Rate limit exceeded", "status": 429})
        self.assertEqual(info["detail"], "Rate limit exceeded")
        self.assertFalse(info["exhausted"])

    def test_credit_exhaustion_text_is_quota_exhausted(self):
        info = self.info(429, {"detail": "You are out of credits for this trial API."})
        self.assertTrue(info["exhausted"])

    def test_gemini_per_day_violation_still_detected(self):
        body = {"error": {"message": "Quota exceeded", "details": [
            {"@type": "type.googleapis.com/google.rpc.QuotaFailure",
             "violations": [{"quotaId": "GenerateRequestsPerDayPerProjectPerModel"}]}]}}
        self.assertTrue(self.info(429, body)["exhausted"])

    def test_plain_server_error_is_not_quota(self):
        self.assertFalse(self.info(503, {"error": {"message": "service overloaded, try again"}})["exhausted"])


class ListModelsTest(unittest.TestCase):
    def test_returns_ids_in_response_order(self):
        def http(url, headers, timeout):
            self.assertEqual(url, "https://api.groq.com/openai/v1/models")
            self.assertEqual(headers["Authorization"], "Bearer k-groq")
            return {"object": "list", "data": [{"id": "llama-3.3-70b-versatile"}, {"id": "openai/gpt-oss-20b"},
                                               {"no-id": True}, "not-a-dict"]}

        with mock.patch.dict("os.environ", KEYS, clear=True):
            self.assertEqual(llm.list_models("groq", http=http),
                             ["llama-3.3-70b-versatile", "openai/gpt-oss-20b"])

    def test_raw_returns_whole_response(self):
        payload = {"object": "list", "data": [{"id": "m", "owned_by": "x"}]}
        with mock.patch.dict("os.environ", KEYS, clear=True):
            self.assertEqual(llm.list_models("nim", raw=True, http=lambda *a: payload), payload)

    def test_non_openai_compatible_provider_is_rejected(self):
        with self.assertRaises(llm.LLMError) as caught:
            llm.list_models("gemini")
        self.assertIn("gemini", str(caught.exception))

    def test_missing_data_array_is_an_error(self):
        with mock.patch.dict("os.environ", KEYS, clear=True):
            with self.assertRaises(llm.LLMError):
                llm.list_models("groq", http=lambda *a: {"object": "list"})


class FreeModelsFileTest(unittest.TestCase):
    def setUp(self):
        self.data = json.loads(llm.FREE_MODELS_FILE.read_text(encoding="utf-8"))

    def test_new_providers_are_listed_with_sources(self):
        for provider in ("groq", "nim"):
            with self.subTest(provider=provider):
                entry = self.data[provider]
                self.assertTrue(entry["models"], "free:<공급자> 자동 선택이 쓸 모델이 하나는 있어야 한다")
                for key in ("source", "termsSource", "dataUse", "limits"):
                    self.assertTrue(entry.get(key), f"{provider}.{key}가 비어 있다")

    def test_every_listed_provider_is_callable(self):
        """free_models.json의 공급자는 모두 llm이 부를 수 있어야 한다 (free:<공급자>가 동작하도록)."""
        for spec in llm.free_tier_model_list():
            provider = spec.partition(":")[0]
            self.assertIn(provider, llm.PROVIDERS, f"{spec}: 지원하지 않는 공급자")
            self.assertIn(provider, llm.API_KEY_ENVS, f"{spec}: API 키 환경변수가 없다")

    def test_free_tier_lookup_covers_new_providers(self):
        self.assertTrue(llm.has_free_tier("groq:llama-3.3-70b-versatile"))
        self.assertTrue(llm.has_free_tier("nim:nvidia/nemotron-3-super-120b-a12b"))
        self.assertFalse(llm.has_free_tier("groq:made-up-model"))

    def test_preview_models_are_not_auto_selected(self):
        """예고 없이 내려갈 수 있는 preview 모델은 free:<공급자> 자동 선택에 들어가지 않는다."""
        for model in self.data["groq"].get("previewModels", []):
            self.assertNotIn(f"groq:{model}", llm.free_tier_models())


if __name__ == "__main__":
    unittest.main()
