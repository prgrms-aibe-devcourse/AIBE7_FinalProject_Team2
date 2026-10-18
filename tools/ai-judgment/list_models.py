"""공급자에게 지금 쓸 수 있는 모델 ID를 물어본다 (BE-84).

사용법:
    python3 list_models.py groq                 # 모델 ID를 한 줄에 하나씩
    python3 list_models.py nim --grep nemotron  # 이름에 nemotron이 든 것만
    python3 list_models.py groq --json          # 공급자 응답 그대로 (owned_by 등 함께)

왜 있나
- `free_models.json`의 무료 모델 목록은 공급자가 자주 바꾼다. 문서 · 블로그를 보고 옮겨 적으면 틀리기 쉬우므로
  공급자에게 직접 물어 확인하고 고쳐 넣는다.
- **조회만 한다.** 받은 목록을 free_models.json에 자동으로 넣지 않는다. 응답에는 임베딩 · 음성 · 코드 · 비전 ·
  안전 분류 모델이 섞여 있어서, 그대로 넣으면 `free:<공급자>` 자동 선택이 판결 생성에 쓸 수 없는 모델을 고른다.
  무엇을 넣을지는 사람이 고른다.

OpenAI 호환 공급자(openai · groq · nim)만 된다. Gemini는 목록 API가 달라 지원하지 않는다(문서에서 확인한다).
API 키는 환경변수로만 읽는다 (llm.API_KEY_ENVS).
"""

import argparse
import json
import sys

import llm


def main():
    parser = argparse.ArgumentParser(description="공급자의 모델 ID 목록을 조회한다")
    parser.add_argument("provider", choices=sorted(llm.OPENAI_COMPATIBLE), help="조회할 공급자")
    parser.add_argument("--grep", help="모델 ID에 이 문자열이 든 것만 (대소문자 구분 없음)")
    parser.add_argument("--json", action="store_true", help="공급자 응답을 그대로 출력한다")
    parser.add_argument("--timeout", type=float, default=60.0, help="응답 대기 시간(초). 기본 60")
    args = parser.parse_args()

    try:
        if args.json:
            print(json.dumps(llm.list_models(args.provider, raw=True, timeout=args.timeout),
                             ensure_ascii=False, indent=2))
            return 0
        models = llm.list_models(args.provider, timeout=args.timeout)
    except llm.LLMError as e:
        print(f"[오류] {e}", file=sys.stderr)
        return 1

    if args.grep:
        needle = args.grep.lower()
        models = [m for m in models if needle in m.lower()]
    for model in sorted(models):
        print(model)
    print(f"({len(models)}개)", file=sys.stderr)
    return 0


if __name__ == "__main__":
    sys.exit(main())
