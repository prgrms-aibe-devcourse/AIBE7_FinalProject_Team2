""".env 파일에서 API 키 등 환경변수를 읽어 os.environ에 채운다 (BE-86).

`python-dotenv` 같은 외부 패키지를 쓰지 않는다 (이 폴더의 "설치 없이 실행" 원칙). 지원하는 문법:

    KEY=VALUE
    export KEY=VALUE          # export 접두어는 무시한다
    KEY="값에 공백이 있어도 됨"  # 큰따옴표 · 작은따옴표로 감싼 값은 벗긴다 (이스케이프 처리는 하지 않는다)
    # 주석                     # #으로 시작하는 줄은 건너뛴다

빈 줄과 '='가 없는 줄은 건너뛴다(그 줄만 경고하고 나머지는 계속 읽는다).

**우선순위**: 이미 설정된 환경변수는 .env 값으로 **덮지 않는다**. `GROQ_API_KEY=... python3 ...`처럼
한 번만 다른 키로 돌릴 때 그 값이 이기게 하기 위해서다.

**찾는 위치** (둘 다 있으면 둘 다 읽되, 같은 키는 먼저 읽은 쪽이 이긴다): `tools/ai-judgment/.env` →
저장소 루트 `.env`. 도구만 따로 옮겨 쓸 때와 저장소 전체를 쓸 때를 모두 맞춘다.

**값은 어디에도 남기지 않는다.** 불러온 변수는 안내에 **이름만** 적는다(llm.py의 "코드 · 출력 파일 · 오류
메시지에 키를 남기지 않는다" 원칙과 같다).

`llm.py`를 불러오는 시점(모듈 최상위)에 한 번 적용된다. Claude SDK(`anthropic.Anthropic()`)는 자기 환경변수를
직접 읽어 `llm.api_key()`를 거치지 않으므로, `api_key()`가 아니라 **모듈을 불러오는 시점**에 적용해야
Claude 경로도 빠짐없이 덮는다.
"""

import os
import stat
import sys
from pathlib import Path

TOOL_DIR = Path(__file__).resolve().parent
REPO_ROOT = TOOL_DIR.parent.parent  # tools/ai-judgment -> tools -> 저장소 루트
DEFAULT_ENV_PATHS = (TOOL_DIR / ".env", REPO_ROOT / ".env")

_loaded = False  # 프로세스마다 한 번만 적용한다 (ensure_loaded가 여러 번 불려도 다시 읽지 않는다)


def _notice(message, log):
    (log or (lambda m: print(m, file=sys.stderr)))(message)


def parse_env_text(text):
    """.env 파일 내용(문자열) → {키: 값} dict. 잘못된 줄은 건너뛴다(경고 없이, 순수 파싱용).

    줄 단위 경고가 필요하면 parse_env_file을 쓴다. 이 함수는 테스트 · 간단한 쓰임을 위한 것이다.
    """
    values = {}
    for raw_line in text.splitlines():
        line = raw_line.strip()
        if not line or line.startswith("#"):
            continue
        if line.startswith("export "):
            line = line[len("export "):].strip()
        key, sep, value = line.partition("=")
        key = key.strip()
        if not sep or not key:
            continue
        value = value.strip()
        if len(value) >= 2 and value[0] == value[-1] and value[0] in "\"'":
            value = value[1:-1]
        values[key] = value
    return values


def _check_permissions(path, log):
    """파일 권한이 느슨하면(그룹 · 전체 읽기 가능) 경고한다. API 키가 든 파일이라 유출 위험이 있다."""
    if os.name != "posix":
        return  # Windows ACL은 st_mode로 보이지 않는다. 검사하지 않는다
    try:
        mode = path.stat().st_mode
    except OSError:
        return
    if mode & (stat.S_IRGRP | stat.S_IROTH):
        _notice(f"⚠️  {path}의 권한이 느슨합니다 (다른 사용자도 읽을 수 있음). "
               f"API 키가 들어 있다면 'chmod 600 {path}'로 좁히세요", log)


def parse_env_file(path, log=None):
    """파일 하나를 읽어 dict로 돌려준다. 없으면 빈 dict. 읽기 오류 · 잘못된 줄은 경고만 남기고 계속한다."""
    try:
        text = path.read_text(encoding="utf-8")
    except FileNotFoundError:
        return {}
    except OSError as e:
        _notice(f"⚠️  {path}를 읽지 못했습니다: {e}", log)
        return {}
    _check_permissions(path, log)
    values = {}
    for lineno, raw_line in enumerate(text.splitlines(), start=1):
        line = raw_line.strip()
        if not line or line.startswith("#"):
            continue
        if line.startswith("export "):
            line = line[len("export "):].strip()
        key, sep, value = line.partition("=")
        key = key.strip()
        if not sep or not key:
            _notice(f"⚠️  {path}:{lineno}: 올바른 KEY=VALUE 줄이 아닙니다, 건너뜁니다: {line!r}", log)
            continue
        value = value.strip()
        if len(value) >= 2 and value[0] == value[-1] and value[0] in "\"'":
            value = value[1:-1]
        values[key] = value
    return values


def apply_env_files(paths=None, log=None):
    """paths(기본 DEFAULT_ENV_PATHS)를 순서대로 읽어 **아직 설정되지 않은** 환경변수만 os.environ에 채운다.

    우선순위: 이미 설정된 환경변수 > 먼저 읽은 파일의 값(tools/ai-judgment/.env > 저장소 루트 .env).
    값은 안내에 남기지 않고 불러온 변수 **이름만** 남긴다. 돌려주는 값은 새로 설정한 키 이름 목록(정렬)이다.
    """
    applied = []
    for path in (paths if paths is not None else DEFAULT_ENV_PATHS):
        for key, value in parse_env_file(path, log=log).items():
            if key not in os.environ:
                os.environ[key] = value
                applied.append(key)
    if applied:
        names = ", ".join(sorted(set(applied)))
        _notice(f".env에서 환경변수를 읽었습니다: {names}", log)
    return sorted(set(applied))


def ensure_loaded(log=None):
    """프로세스에서 한 번만 .env를 적용한다. llm.py를 불러오는 시점에 자동으로 불린다."""
    global _loaded
    if _loaded:
        return []
    _loaded = True
    return apply_env_files(log=log)


def reset_loaded():
    """테스트 전용: ensure_loaded가 다시 동작하도록 되돌린다."""
    global _loaded
    _loaded = False
