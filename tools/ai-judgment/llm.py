"""LLM 공급자 공통 호출 (BE-30).

모델은 `공급자:모델ID` 형식으로 지정한다. 예: `openai:gpt-5`, `anthropic:claude-opus-5-5`, `gemini:gemini-3.7-flash`
- 표준 라이브러리(urllib)로 각 공급자의 HTTP API를 직접 부른다. 이 폴더의 "설치 없이 실행" 원칙을 지킨다.
- API 키는 환경변수로만 읽는다. 코드 · 출력 파일 · 오류 메시지에 키를 남기지 않는다.
- 웹 검색 · 그라운딩 같은 도구는 붙이지 않는다 (실제 판결을 찾아보면 독립 판단 · 사전 학습 점검이 의미 없어진다).
- `manual` 공급자는 API를 부르지 않는다. 채팅 화면에서 받은 응답 파일을 같은 흐름에 넣을 때 쓴다(generate.py import).

공급자를 추가하려면 `_call_<공급자>` 함수를 만들고 PROVIDERS에 등록한다.
함수는 (system, user, model, options) → LLMResult를 돌려준다.
"""

import functools
import json
import os
import random
import socket
import ssl
import time
import urllib.error
import urllib.request
from pathlib import Path

DEFAULT_TIMEOUT = 300  # 초. 사고(reasoning) 모델은 응답이 오래 걸린다
DEFAULT_MAX_TOKENS = 16000
RETRY_STATUSES = (429, 500, 502, 503, 504, 529)
OVERLOAD_STATUSES = (503, 529)

# 재시도 정책 (BE-36). 최대 시도 횟수 · 한 번에 기다릴 최대 시간은 configure_retry로 바꾼다
DEFAULT_MAX_ATTEMPTS = 5
DEFAULT_MAX_WAIT = 120.0  # 초
MAX_ATTEMPTS = DEFAULT_MAX_ATTEMPTS
OVERLOAD_BACKOFF_BASE = 5.0  # 과부하(503 · 529)는 길게 이어지므로 5 · 10 · 20 · 40초로 늘려 간다
BACKOFF_BASE = 2.0  # 그 밖의 서버 오류 · 연결 실패: 2 · 4 · 8 · 16초
RATE_LIMIT_WAIT = 60.0  # 429에 대기 시간 안내가 없을 때: 분당 한도가 풀리도록 1분을 기다린다
RETRY_AFTER_MARGIN = 1.0  # 안내받은 시간 직후에 보내면 아직 막혀 있을 수 있어 조금 더 기다린다

# 기본 인증서 위치에 인증서가 없을 때(예: macOS python.org Python) 찾아볼 시스템 CA 묶음 (BE-34)
SYSTEM_CA_FILES = (
    "/etc/ssl/cert.pem",                       # macOS · BSD
    "/etc/ssl/certs/ca-certificates.crt",      # Debian · Ubuntu
    "/etc/pki/tls/certs/ca-bundle.crt",        # RHEL · CentOS · Fedora
    "/etc/ssl/ca-bundle.pem",                  # openSUSE
    "/opt/homebrew/etc/openssl@3/cert.pem",    # Homebrew (Apple Silicon)
    "/usr/local/etc/openssl@3/cert.pem",       # Homebrew (Intel)
)

# 공급자별 API 키 환경변수 (앞의 것부터 찾는다)
API_KEY_ENVS = {
    "openai": ("OPENAI_API_KEY",),
    "anthropic": ("ANTHROPIC_API_KEY",),
    "gemini": ("GEMINI_API_KEY", "GOOGLE_API_KEY"),
}


FREE_MODELS_FILE = Path(__file__).with_name("free_models.json")  # 무료 등급이 있는 모델 목록 (BE-45)


class LLMError(Exception):
    kind = None  # 실패 원인 구분. 아래 하위 클래스가 채운다 (BE-36)


class LLMOverloadedError(LLMError):
    """서비스 과부하(503 · 529)가 재시도 끝까지 이어졌다."""
    kind = "overloaded"


class LLMRateLimitError(LLMError):
    """분당 호출 한도(429). 기다리면 풀리지만 허용한 대기 시간 안에 풀리지 않았다."""
    kind = "rate_limit"


class LLMQuotaExhaustedError(LLMError):
    """일 한도 · 크레딧 소진(429). 기다려도 곧 풀리지 않으므로 재시도하지 않는다."""
    kind = "daily_quota"


_retry_policy = {"max_attempts": DEFAULT_MAX_ATTEMPTS, "max_wait": DEFAULT_MAX_WAIT}


def check_retry(max_attempts=None, max_wait=None):
    """재시도 설정 값이 올바른지만 확인한다 (적용하지 않는다). 잘못됐으면 LLMError."""
    if max_attempts is not None and (not isinstance(max_attempts, int) or isinstance(max_attempts, bool)
                                     or max_attempts < 1):
        raise LLMError(f"재시도 횟수(maxAttempts)는 1 이상의 정수입니다 (첫 시도 포함): {max_attempts!r}")
    if max_wait is not None and (not isinstance(max_wait, (int, float)) or isinstance(max_wait, bool) or max_wait < 0):
        raise LLMError(f"최대 대기 시간(maxWait)은 0 이상의 숫자(초)입니다: {max_wait!r}")


def configure_retry(max_attempts=None, max_wait=None):
    """재시도 횟수(첫 시도 포함) · 한 번에 기다릴 최대 시간(초)을 바꾼다. None이면 현재 값을 유지한다.

    파이프라인(run_pipeline) · generate.py 옵션이 시작할 때 한 번 부른다. 429의 대기 안내가 max_wait보다 길면
    기다려도 소용이 없으므로 기다리지 않고 한도 초과로 끝낸다.
    """
    check_retry(max_attempts, max_wait)
    if max_attempts is not None:
        _retry_policy["max_attempts"] = max_attempts
    if max_wait is not None:
        _retry_policy["max_wait"] = float(max_wait)
    return dict(_retry_policy)


def reset_retry():
    _retry_policy.update(max_attempts=DEFAULT_MAX_ATTEMPTS, max_wait=DEFAULT_MAX_WAIT)


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


@functools.lru_cache(maxsize=1)
def ssl_context():
    """HTTPS 인증서 검증용 SSL 컨텍스트 (BE-34). **검증은 항상 켠 상태**다.

    macOS python.org Python처럼 기본 인증서 위치가 비어 있으면 모든 호출이 CERTIFICATE_VERIFY_FAILED로 실패한다.
    그래서 기본 위치(SSL_CERT_FILE · SSL_CERT_DIR 환경변수 포함)에 인증서가 하나도 없을 때만 시스템 CA 묶음을 찾아 추가한다.
    """
    context = ssl.create_default_context()
    if context.cert_store_stats().get("x509_ca", 0) == 0:
        for path in SYSTEM_CA_FILES:
            if os.path.isfile(path):
                try:
                    context.load_verify_locations(cafile=path)
                except (ssl.SSLError, OSError):
                    continue
                if context.cert_store_stats().get("x509_ca", 0) > 0:
                    break
    return context


SSL_HELP = ("HTTPS 인증서를 확인하지 못했습니다. 시스템 인증서 묶음 경로를 환경변수 SSL_CERT_FILE로 지정하세요 "
            "(macOS 예: SSL_CERT_FILE=/etc/ssl/cert.pem). python.org Python이면 "
            "'/Applications/Python 3.x/Install Certificates.command'를 한 번 실행해도 됩니다. "
            "인증서 검증을 끄는 방법은 지원하지 않습니다")


def free_tier_models():
    """무료 등급이 있는 모델 목록(`공급자:모델ID` 집합). free_models.json이 없거나 깨졌으면 빈 집합이다.

    '무료 등급이 있다'는 뜻이지 지금 키가 무료라는 뜻이 아니다 (결제를 연결한 프로젝트의 키는 같은 모델도 유료).
    그래서 실제 판결문을 보내는 단계의 안내 · 한도 대응 안내에만 쓰고, 호출을 막는 데는 쓰지 않는다.
    """
    try:
        data = json.loads(FREE_MODELS_FILE.read_text(encoding="utf-8"))
    except (OSError, ValueError):
        return set()
    if not isinstance(data, dict):
        return set()
    return {f"{provider}:{model}" for provider, entry in data.items() if isinstance(entry, dict)
            and isinstance(entry.get("models"), list) for model in entry["models"] if isinstance(model, str)}


def free_tier_model_list():
    """무료 등급이 있는 모델을 free_models.json에 적힌 순서대로 (`공급자:모델ID` 목록). free_tier_models와 같은 규칙이다."""
    try:
        data = json.loads(FREE_MODELS_FILE.read_text(encoding="utf-8"))
    except (OSError, ValueError):
        return []
    if not isinstance(data, dict):
        return []
    specs = []
    for provider, entry in data.items():
        if isinstance(entry, dict) and isinstance(entry.get("models"), list):
            for model in entry["models"]:
                if isinstance(model, str) and f"{provider}:{model}" not in specs:
                    specs.append(f"{provider}:{model}")
    return specs


def has_free_tier(spec):
    """모델 지정(`공급자:모델ID`)에 무료 등급이 있는지. 공급자가 없는 이름(`claude-…`)은 아니다."""
    if not isinstance(spec, str) or ":" not in spec:
        return False
    provider, _, model = spec.partition(":")
    return f"{provider.strip().lower()}:{model.strip()}" in free_tier_models()


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
    """JSON POST. 재시도할 만한 오류(과부하 · 한도 · 서버 오류)는 원인에 맞게 기다렸다가 다시 보낸다 (BE-36).

    - 503 · 529(과부하): 지수 백오프(5 · 10 · 20 · 40초…) + 지터. 최대 대기 시간을 넘지 않는다
    - 429(한도): 응답이 안내한 시간(Retry-After 헤더 · 본문 retryDelay)만큼, 안내가 없으면 1분 기다린다.
      일 한도 · 크레딧 소진이면 기다려도 풀리지 않으므로 바로 멈추고, 안내가 최대 대기보다 길어도 멈춘다
    - 그 밖의 5xx · 연결 실패: 2 · 4 · 8초 백오프
    """
    data = json.dumps(body).encode("utf-8")
    max_attempts, max_wait = _retry_policy["max_attempts"], _retry_policy["max_wait"]
    for attempt in range(1, max_attempts + 1):
        request = urllib.request.Request(url, data=data, method="POST",
                                         headers={"Content-Type": "application/json", **headers})
        try:
            with urllib.request.urlopen(request, timeout=timeout, context=ssl_context()) as response:
                raw = response.read()
        except urllib.error.HTTPError as e:
            info = _error_info(e)
            kind = _failure_kind(e.code, info)
            if kind == "daily_quota":
                raise LLMQuotaExhaustedError(_final_message(kind, e.code, info, attempt, max_wait)) from None
            if kind is None:
                raise LLMError(f"API 오류 ({e.code}): {info['detail']}") from None
            wait = _retry_wait(kind, info, attempt, max_wait)
            if attempt < max_attempts and wait is not None:
                time.sleep(wait)
                continue
            message = _final_message(kind, e.code, info, attempt, max_wait, hint_too_long=wait is None)
            raise _FAILURE_ERRORS.get(kind, LLMError)(message) from None
        except urllib.error.URLError as e:
            if isinstance(e.reason, ssl.SSLCertVerificationError):
                raise LLMError(f"{SSL_HELP} ({e.reason})") from None  # 다시 보내도 같으므로 재시도하지 않는다
            # Python 3.9 이하는 연결 단계 timeout이 URLError(reason=socket.timeout)로 온다. 읽기 timeout과 같게 다시 보내지 않는다
            if isinstance(e.reason, (TimeoutError, socket.timeout)):
                raise LLMError(f"응답 대기 시간({timeout}초)을 넘었습니다") from None
            if attempt < max_attempts:
                time.sleep(_backoff(attempt, BACKOFF_BASE, max_wait))
                continue
            raise LLMError(f"API에 연결하지 못했습니다 ({attempt}번 시도): {e.reason}") from None
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


_FAILURE_ERRORS = {"overloaded": LLMOverloadedError, "rate_limit": LLMRateLimitError}


def _backoff(attempt, base, max_wait):
    """지수 백오프(base · 2배 · 4배…)에 ±25% 지터를 더하고 최대 대기 시간으로 자른다."""
    return min(max_wait, base * 2 ** (attempt - 1) * random.uniform(0.75, 1.25))


def _failure_kind(status, info):
    """HTTP 오류 → 실패 원인. 재시도 대상이 아니면 None."""
    if status == 429:
        return "daily_quota" if info["exhausted"] else "rate_limit"
    if status in OVERLOAD_STATUSES:
        return "overloaded"
    if status in RETRY_STATUSES:
        return "server"
    return None


def _retry_wait(kind, info, attempt, max_wait):
    """다음 시도 전 기다릴 초. 기다려도 소용없으면(안내 시간이 최대 대기보다 길면) None."""
    if kind == "rate_limit":
        if info["retry_after"] is None:
            return min(RATE_LIMIT_WAIT, max_wait)
        wait = info["retry_after"] + RETRY_AFTER_MARGIN
        return wait if wait <= max_wait else None
    return _backoff(attempt, OVERLOAD_BACKOFF_BASE if kind == "overloaded" else BACKOFF_BASE, max_wait)


def _final_message(kind, status, info, attempts, max_wait, hint_too_long=False):
    """최종 실패 안내. 원인(과부하 / 분당 한도 / 일 한도)과 다음에 할 일을 알려 준다."""
    detail = info["detail"]
    if kind == "daily_quota":
        return (f"호출 한도를 모두 썼습니다 (일 한도 · 크레딧 소진, {status}): {detail}\n"
                "→ 한도가 초기화될 때까지 이 모델은 쓸 수 없습니다. 내일 다시 하거나 다른 모델 · 요금제를 쓰세요")
    if kind == "rate_limit":
        if hint_too_long:
            return (f"분당 호출 한도 초과 ({status}): {info['retry_after']:.0f}초 뒤에 풀린다고 하지만 최대 대기 시간"
                    f"({max_wait:.0f}초)을 넘습니다: {detail}\n→ 최대 대기(maxWait)를 늘리거나 나중에 다시 하세요")
        return (f"분당 호출 한도 초과 ({status}): {attempts}번 시도해도 풀리지 않았습니다: {detail}\n"
                "→ 호출 사이 대기(delay)를 늘리거나 재시도 횟수(maxAttempts)를 늘리세요")
    if kind == "overloaded":
        return (f"서비스 과부하 ({status}): {attempts}번 시도했지만 계속 바쁩니다: {detail}\n"
                "→ 잠시 뒤 다시 하거나, 재시도 횟수(maxAttempts) · 최대 대기(maxWait)를 늘리거나 다른 모델을 쓰세요")
    return f"API 오류 ({status}): {attempts}번 시도했지만 실패했습니다: {detail}"


def _error_info(error):
    """HTTP 오류 응답에서 메시지 · 대기 안내 · 한도 소진 여부를 꺼낸다. 요청 헤더(키)는 오류에 들어가지 않는다."""
    try:
        body = json.loads(error.read().decode("utf-8"))
    except Exception:
        body = None
    message = body.get("error", body) if isinstance(body, dict) else None
    details = message.get("details") if isinstance(message, dict) else None
    details = [d for d in details if isinstance(d, dict)] if isinstance(details, list) else []
    if isinstance(message, dict):
        text = message.get("message", message)
    else:
        text = message
    detail = str(text)[:500] if text else str(error.reason)
    return {
        "detail": detail,
        "retry_after": _header_retry_after(error) or _body_retry_delay(details),
        "exhausted": _quota_exhausted(message, details),
    }


def _header_retry_after(error):
    value = error.headers.get("retry-after") if error.headers else None
    try:
        seconds = float(value)
    except (TypeError, ValueError):  # HTTP 날짜 형식은 쓰지 않는다
        return None
    return seconds if seconds >= 0 else None


def _body_retry_delay(details):
    """Gemini 429는 대기 시간을 본문 RetryInfo.retryDelay("41s")로 준다."""
    for item in details:
        delay = item.get("retryDelay")
        if str(item.get("@type", "")).endswith("RetryInfo") and isinstance(delay, str) and delay.endswith("s"):
            try:
                return max(float(delay[:-1]), 0.0)
            except ValueError:
                return None
    return None


def _quota_exhausted(message, details):
    """429가 일 한도(Gemini QuotaFailure ...PerDay...) · 크레딧 소진(OpenAI insufficient_quota)인지."""
    if isinstance(message, dict) and message.get("code") == "insufficient_quota":
        return True
    for item in details:
        for violation in item.get("violations") or []:
            if isinstance(violation, dict) and "perday" in f"{violation.get('quotaId', '')}".lower():
                return True
    return False


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
