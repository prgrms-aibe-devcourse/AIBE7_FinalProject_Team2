# AI 판결 RAG 양형기준 저장소 도구 (BE-52)

양형위원회 사이트의 양형기준을 버전 단위로 받아 RAG 조각으로 나누고, 임베딩해 `rag_guideline` 적재 SQL을 만든다.
결정 · 출처 · 구조는 [`docs/sentencing-guideline-rag.md`](../../docs/sentencing-guideline-rag.md)를 따른다. `tools/ai-judgment`와 같이 **Python 3.8 이상 표준 라이브러리만** 쓴다(임베딩은 `tools/ai-judgment/llm.py`).

## 파일

| 파일 | 역할 |
| --- | --- |
| `fetch.py` | 범죄 하나의 모든 버전(시행 중 + 과거) · 하위 페이지를 받아 `raw/<범죄>/`에 UTF-8로 저장 (`manifest.json` 포함) |
| `parse.py` | HTML → 버전 · 페이지 · 탭 · 블록(표 · 목록 · 문단) 구조 `out/<범죄>/guideline.json` |
| `chunk.py` | 구조 → RAG 조각 `chunks.jsonl` · 버전 목록 `versions.json` · 읽기용 `md/` |
| `store.py` | `embed`: 조각 임베딩(`embeddings.jsonl`, 바뀐 것만) / `sql`: R__41 시드 SQL |
| `tests/` | 도구 테스트 (네트워크 · DB 없이). `python3 -m unittest discover tests` |

테이블 · 함수는 `backend/src/main/resources/db/migration/V13__create_rag_guideline.sql`에 있다.

`raw/` · `out/`은 다시 만들 수 있는 중간 결과라 git에 올리지 않는다.

## 순서

```bash
cd tools/sentencing-guideline
python3 fetch.py 살인                 # 범죄명 = 사이트 메뉴의 "<범죄명>범죄"
python3 parse.py 살인
python3 chunk.py 살인                 # 버전 · 시행일 · 조각 수 출력. 시행일 경고는 docs 3장 참고
GEMINI_API_KEY=... python3 store.py embed 살인
python3 store.py sql --output ../../backend/private-seed/seed/R__41_rag_guideline.sql   # out/ 아래 모든 범죄를 합친다. 비공개 시드에 둔다(판례 R__40과 같은 경로)
```

## 주의

- 사이트는 EUC-KR이다. `fetch.py`는 시스템 `curl`로 받고 cp949로 읽는다(파이썬 번들 인증서로는 검증 실패, 검증은 끄지 않는다).
- 시행일 정본은 시행 중 페이지의 개정 이력이다. 개정 이력 개수와 버전 수가 다르면 `chunk.py`가 멈춘다.
- `sentencing_guideline.version_name`은 기존 시드(R__10)와 같은 규칙 `<의결 연도> 수정 (<의결일> 의결)`이다. 이름이 바뀌면 판례 쪽 매핑도 같이 바꾼다.
- R__41은 `rag_guideline`을 통째로 지우고 다시 넣는다. 손으로 고치지 않는다.
- V13이 `backend/.../db/migration`에 들어간 뒤에 R__41을 비공개 시드에 넣는다. 순서가 바뀌면 로컬 `bootRun`의 Flyway가 실패한다.
