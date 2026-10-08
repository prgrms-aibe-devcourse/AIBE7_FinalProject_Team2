"""LLM 공급자 공통 호출 (BE-30).

모델은 `공급자:모델ID` 형식으로 지정한다. 예: `openai:gpt-5`, `anthropic:claude-opus-5-5`, `gemini:gemini-3.7-flash`
- 표준 라이브러리(urllib)로 각 공급자의 HTTP API를 직접 부른다. 이 폴더의 "설치 없이 실행" 원칙을 지킨다.
- API 키는 환경변수로만 읽는다. 코드 · 출력 파일 · 오류 메시지에 키를 남기지 않는다.
- 웹 검색 · 그라운딩 같은 도구는 붙이지 않는다 (실제 판결을 찾아보면 독립 판단 · 사전 학습 점검이 의미 없어진다).
- `manual` 공급자는 API를 부르지 않는다. 채팅 화면에서 받은 응답 파일을 같은 흐름에 넣을 때 쓴다(generate.py import).

공급자를 추가하려면 `_call_<공급자>` 함수를 만들고 PROVIDERS에 등록한다.
함수는 (system, user, model, options) → LLMResult를 돌려준다.
"""

import json
import os
import socket
import time
import urllib.error
import urllib.request

DEFAULT_TIMEOUT = 300  # 초. 사고(reasoning) 모델은 응답이 오래 걸린다
DEFAULT_MAX_TOKENS = 16000
RETRY_STATUSES = (429, 500, 502, 503, 504, 529)
MAX_ATTEMPTS = 3

# 공급자별 API 키 환경변수 (앞의 것부터 찾는다)
API_KEY_ENVS = {
    "openai": ("OPENAI_API_KEY",),
    "anthropic": ("ANTHROPIC_API_KEY",),
    "gemini": ("GEMINI_API_KEY", "GOOGLE_API_KEY"),
}


class LLMError(Exception):
    pass


class LLMResult:
    """응답 한 건. usage는 README "토큰 사용 기록" 칸 기준으로 맞춘다.

    - inputTokens · outputTokens · totalTokens: 공급자가 준 값을 그대로 옮긴다 (없으면 None)
    - thinkingTokens: 공급자가 따로 세어 줄 때만 넣는다. OpenAI · Anthropic은 출력 토큰에 이미 들어 있으므로
      합계를 직접 더하지 않는다 (Gemini는 total에 사고 토큰이 들어 있다)
    """

    def __init__(self, text, provider, model, served_model=None, usage=None, latency_seconds=None, stop_reason=None):
        self.text = text
        self.provider = provider
        self.model = model
        self.served_model = served_model or model
        self.usage = usage or {}
        self.latency_seconds = latency_seconds
        self.stop_reason = stop_reason

    def meta(self):
        return {
            "provider": self.provider,
            "model": self.model,
            "servedModel": self.served_model,
            "usage": self.usage,
            "latencySeconds": self.latency_seconds,
            "stopReason": self.stop_reason,
        }


def parse_model_spec(spec):
    """`공급자:모델ID` → (공급자, 모델ID). 모델 ID에 `:`가 들어가도 첫 번째 `:`에서만 나눈다."""
    provider, sep, model = spec.partition(":")
    provider = provider.strip().lower()
    model = model.strip()
    if not sep or not model:
        raise LLMError(f"모델은 '공급자:모델ID' 형식으로 적습니다: {spec!r} (예: openai:gpt-5)")
    if provider not in PROVIDERS and provider != "manual":
        raise LLMError(f"지원하지 않는 공급자입니다: {provider!r} (가능: {', '.join(sorted(PROVIDERS))}, manual)")
    return provider, model


def model_provider(spec):
    """모델 지정 → 실제로 데이터를 받는 공급자 이름 (BE-35).

    `공급자:모델ID`는 그 공급자, 공급자가 없는 이름(예: `claude-opus-5-5`)은 Claude SDK로 부르므로 `anthropic`이다.
    비식별화(extract_case.split_model)와 파이프라인의 외부 전송 안내가 이 함수 하나를 써서, 공급자 규칙이 바뀌어도
    안내 문구와 실제 호출 대상이 어긋나지 않게 한다. 표준 라이브러리만 쓰는 모듈이라 pipeline.py가 바로 불러도 된다.
    """
    if not isinstance(spec, str) or ":" not in spec:
        return "anthropic"
    return parse_model_spec(spec)[0]


def api_key(provider):
    for env in API_KEY_ENVS[provider]:
        value = os.environ.get(env)
        if value:
            return value
    raise LLMError(f"{provider} API 키가 없습니다. 환경변수 {' 또는 '.join(API_KEY_ENVS[provider])}를 설정하세요")


def call(spec, system, user, *, temperature=None, max_tokens=DEFAULT_MAX_TOKENS, timeout=DEFAULT_TIMEOUT,
         json_output=True, http=None):
    """모델을 한 번 부르고 LLMResult를 돌려준다. http는 테스트에서 네트워크 대신 넣는 함수다."""
    provider, model = parse_model_spec(spec)
    if provider == "manual":
        raise LLMError("manual 공급자는 API를 부르지 않습니다. 응답 파일을 generate.py import로 넣으세요")
    options = {
        "temperature": temperature,
        "max_tokens": max_tokens,
        "timeout": timeout,
        "json_output": json_output,
        "http": http or _post_json,
    }
    started = time.monotonic()
    result = PROVIDERS[provider](system, user, model, options)
    result.latency_seconds = round(time.monotonic() - started, 2)
    return result


def _post_json(url, headers, body, timeout):
    """JSON POST. 재시도할 만한 오류(한도 · 서버 오류)는 잠깐 쉬었다가 다시 보낸다."""
    data = json.dumps(body).encode("utf-8")
    for attempt in range(1, MAX_ATTEMPTS + 1):
        request = urllib.request.Request(url, data=data, method="POST",
                                         headers={"Content-Type": "application/json", **headers})
        try:
            with urllib.request.urlopen(request, timeout=timeout) as response:
                raw = response.read()
        except urllib.error.HTTPError as e:
            detail = _error_detail(e)
            if e.code in RETRY_STATUSES and attempt < MAX_ATTEMPTS:
                time.sleep(_retry_delay(e, attempt))
                continue
            raise LLMError(f"API 오류 ({e.code}): {detail}") from None
        except urllib.error.URLError as e:
            # Python 3.9 이하는 연결 단계 timeout이 URLError(reason=socket.timeout)로 온다. 읽기 timeout과 같게 다시 보내지 않는다
            if isinstance(e.reason, (TimeoutError, socket.timeout)):
                raise LLMError(f"응답 대기 시간({timeout}초)을 넘었습니다") from None
            if attempt < MAX_ATTEMPTS:
                time.sleep(2 ** attempt)
                continue
            raise LLMError(f"API에 연결하지 못했습니다: {e.reason}") from None
        except (TimeoutError, socket.timeout):  # Python 3.9 이하는 socket.timeout이 따로 있다
            raise LLMError(f"응답 대기 시간({timeout}초)을 넘었습니다") from None
        # 프록시 · 호환 엔드포인트가 200에 HTML 등을 보내도 회차 실패로 기록되게 LLMError로 바꾼다
        try:
            payload = json.loads(raw.decode("utf-8"))
        except (UnicodeDecodeError, json.JSONDecodeError):
            raise LLMError("API 응답이 JSON이 아닙니다") from None
        if not isinstance(payload, dict):
            raise LLMError("API 응답 형식이 올바르지 않습니다 (JSON 객체가 아님)")
        return payload
    raise LLMError("API 호출에 실패했습니다")  # 도달하지 않음


def _error_detail(error):
    """오류 본문에서 메시지만 꺼낸다. 요청 헤더(키)는 오류에 들어가지 않는다."""
    try:
        body = json.loads(error.read().decode("utf-8"))
    except Exception:
        return error.reason
    message = body.get("error", body)
    if isinstance(message, dict):
        message = message.get("message", message)
    return str(message)[:500]


def _retry_delay(error, attempt):
    retry_after = error.headers.get("retry-after") if error.headers else None
    try:
        return min(float(retry_after), 60.0)
    except (TypeError, ValueError):
        return float(2 ** attempt)


def _openai(system, user, model, options):
    """OpenAI Chat Completions. OPENAI_BASE_URL로 호환 엔드포인트를 쓸 수 있다."""
    base_url = os.environ.get("OPENAI_BASE_URL", "https://api.openai.com/v1").rstrip("/")
    body = {
        "model": model,
        # system이 비었으면(사전 학습 점검처럼 사용자 메시지 하나만 보낼 때) 시스템 메시지를 넣지 않는다
        "messages": ([{"role": "system", "content": system}] if system else []) + [{"role": "user", "content": user}],
        # 사고 모델은 max_tokens 대신 max_completion_tokens만 받는다
        "max_completion_tokens": options["max_tokens"],
    }
    if options["json_output"]:
        body["response_format"] = {"type": "json_object"}
    if options["temperature"] is not None:
        # 사고 모델 중에는 temperature를 받지 않는 것이 있어 지정했을 때만 보낸다
        body["temperature"] = options["temperature"]
    response = options["http"](f"{base_url}/chat/completions", {"Authorization": f"Bearer {api_key('openai')}"},
                               body, options["timeout"])
    choice = (response.get("choices") or [{}])[0]
    message = choice.get("message") or {}
    if message.get("refusal"):
        raise LLMError(f"모델이 요청을 거절했습니다: {message['refusal']}")
    usage = response.get("usage") or {}
    details = usage.get("completion_tokens_details") or {}
    return LLMResult(
        text=message.get("content") or "",
        provider="openai",
        model=model,
        served_model=response.get("model"),
        usage={
            "inputTokens": usage.get("prompt_tokens"),
            "outputTokens": usage.get("completion_tokens"),
            # 출력 토큰에 이미 들어 있다. 참고용으로만 남긴다
            "thinkingTokensIncludedInOutput": details.get("reasoning_tokens"),
            "totalTokens": usage.get("total_tokens"),
        },
        stop_reason=choice.get("finish_reason"),
    )


def _anthropic(system, user, model, options):
    """Anthropic Messages API."""
    body = {
        "model": model,
        "max_tokens": options["max_tokens"],
        "messages": [{"role": "user", "content": user}],
    }
    if system:
        body["system"] = system
    if options["temperature"] is not None:
        body["temperature"] = options["temperature"]
    response = options["http"](
        "https://api.anthropic.com/v1/messages",
        {"x-api-key": api_key("anthropic"), "anthropic-version": "2023-06-01"},
        body,
        options["timeout"],
    )
    if response.get("stop_reason") == "refusal":
        raise LLMError("모델이 요청을 거절했습니다")
    text = "".join(block.get("text", "") for block in response.get("content", []) if block.get("type") == "text")
    usage = response.get("usage") or {}
    input_tokens, output_tokens = usage.get("input_tokens"), usage.get("output_tokens")
    return LLMResult(
        text=text,
        provider="anthropic",
        model=model,
        served_model=response.get("model"),
        usage={
            "inputTokens": input_tokens,
            "outputTokens": output_tokens,  # 사고 토큰 포함
            "totalTokens": input_tokens + output_tokens if input_tokens is not None and output_tokens is not None
            else None,
        },
        stop_reason=response.get("stop_reason"),
    )


def _gemini(system, user, model, options):
    """Gemini generateContent. 검색 연동(그라운딩) 도구는 붙이지 않는다."""
    generation_config = {"maxOutputTokens": options["max_tokens"]}
    if options["json_output"]:
        generation_config["responseMimeType"] = "application/json"
    if options["temperature"] is not None:
        generation_config["temperature"] = options["temperature"]
    body = {
        "contents": [{"role": "user", "parts": [{"text": user}]}],
        "generationConfig": generation_config,
    }
    if system:
        body["systemInstruction"] = {"parts": [{"text": system}]}
    response = options["http"](
        f"https://generativelanguage.googleapis.com/v1beta/models/{model}:generateContent",
        {"x-goog-api-key": api_key("gemini")},
        body,
        options["timeout"],
    )
    candidates = response.get("candidates") or []
    if not candidates:
        reason = (response.get("promptFeedback") or {}).get("blockReason", "알 수 없음")
        raise LLMError(f"응답 후보가 없습니다 (차단 사유: {reason})")
    candidate = candidates[0]
    parts = (candidate.get("content") or {}).get("parts") or []
    # 사고 요약(thought: true) 파트는 본문에서 뺀다
    text = "".join(part.get("text", "") for part in parts if not part.get("thought"))
    usage = response.get("usageMetadata") or {}
    return LLMResult(
        text=text,
        provider="gemini",
        model=model,
        served_model=response.get("modelVersion"),
        usage={
            "inputTokens": usage.get("promptTokenCount"),
            "outputTokens": usage.get("candidatesTokenCount"),
            "thinkingTokens": usage.get("thoughtsTokenCount"),
            "totalTokens": usage.get("totalTokenCount"),  # 사고 토큰 포함. 다시 더하지 않는다
        },
        stop_reason=candidate.get("finishReason"),
    )


PROVIDERS = {
    "openai": _openai,
    "anthropic": _anthropic,
    "gemini": _gemini,
}
