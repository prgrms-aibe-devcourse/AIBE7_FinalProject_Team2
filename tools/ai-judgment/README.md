# AI 판결 오프라인 생성 · 검수 도구 (BE-15)

MVP의 AI 판결은 서비스 안에서 만들지 않고, **팀이 오프라인에서 생성 · 검수한 결과를 DB에 적재**한다(요구사항 FR-4-2, REQ-046). 이 폴더는 그 과정에 쓰는 프롬프트와 검증 스크립트다. 서비스 코드(`backend/`)와 분리된 운영 도구이며, 설치 없이 돌도록 **Python 3.8 이상 표준 라이브러리만** 쓴다.

AI 판결 생성(3단계)은 두 가지 방법 중 하나로 한다. 외부 LLM 서비스에서 할 때의 주의 사항은 아래 "외부 LLM으로 생성할 때"를 따른다.

- **API로 생성 (BE-30)**: `generate.py`가 공급자(OpenAI · Anthropic · Gemini) API를 직접 불러 여러 모델 · 여러 회차로 생성하고 검증까지 기록한다. 모델은 옵션으로 갈아끼우고, `compare.py`가 모델별 비교표를 만든다. 이것도 표준 라이브러리만 쓴다.
- **채팅 화면에서 생성**: `build_prompt.py`로 프롬프트를 만들어 LLM 서비스에 붙여 넣고 응답을 파일로 저장한다. 저장한 응답은 `generate.py import`로 같은 기록에 넣어 비교할 수 있다.

**형벌 종류**: 사형(`DEATH`) · 무기징역(`LIFE`) · 징역(`PRISON`) · 벌금(`FINE`)과, 사형 · 무기를 감경해 다른 형벌로 선고하는 경우(`reducedTo`)를 다룬다(ERD v1.4 · API 명세 9). 무죄는 MVP에서 뺐다.

## 파일

| 파일 | 역할 |
| --- | --- |
| `prompts/judgment_system.md` | AI 판결 생성 시스템 프롬프트 (원칙 · 판단 순서 · 출력 JSON 형식) |
| `prompts/judgment_user.md` | 사건별 사용자 프롬프트 틀 |
| `prompts/contamination_check.md` | 사전 학습 점검 프롬프트 틀 (개요 · 죄명만) |
| `build_prompt.py` | 사건 입력 JSON → AI 판결 생성 프롬프트 |
| `validate_output.py` | 모델 응답 JSON 검증 (오류 · 경고) |
| `check_contamination.py` | 사전 학습 점검 프롬프트 생성 · 응답 판정 (REQ-102) |
| `to_seed_sql.py` | 검수 통과본 → 적재 SQL (`judgment` · `judgment_factor` · `ai_generation`) |
| `llm.py` | LLM 공급자 공통 호출 (OpenAI · Anthropic · Gemini, 표준 라이브러리 HTTP). API 키는 환경변수로만 읽는다 (BE-30) |
| `generate.py` | 여러 모델 · 여러 회차 AI 판결 생성 → 검증 → 실행 기록 (`out/runs/`). 채팅 응답 파일 넣기(`import`) (BE-30) |
| `compare.py` | 실행 기록 → 모델별 비교표 (Markdown · CSV) (BE-30) |
| `examples/` | 예시 사건(ERD 6장 가상 살인 사건 "빌린 돈 문제로 찾아온 지인을 살해한 사건") 입력 · AI 출력 · 실제 판결(내부 전용) |
| `cases/` | **실제 대표 사건** 입력 · 실제 판결을 두는 곳. git에 올리지 않는다(`.gitignore`) |
| `tests/` | 단위 테스트 |
| `case-extractor/` | 판결문(PDF · txt) → 비식별화한 사건 입력 JSON 초안 (BE-20). Claude API를 쓰므로 패키지 설치가 필요하다. [README](case-extractor/README.md) |

## 작업 순서

```bash
cd tools/ai-judgment
mkdir -p out cases   # out/ · cases/는 git에 올리지 않는다
```

아래 명령의 `case.json` · `court_judgment_internal.json`은 `cases/` 안의 실제 대표 사건 파일이다(예: `cases/case.json`). 연습할 때는 `examples/`의 가상 사건 파일을 쓴다.

### 1. 사건 입력 파일 준비

`examples/case_input.json`과 같은 형식으로 대표 사건 파일을 `cases/`에 만든다. 판결문 원본(비공개 저장소 `source/`, 공개 저장소의 `backend/private-seed` 서브모듈)에서 `case-extractor/`로 초안을 만들고 팀이 검수한다. 사건 파일 작성 규칙은 [`docs/cases/README.md`](../../docs/cases/README.md)를 따른다.

- `factors[].factorId`는 사건 파일 안에서 요소 번호(1부터)로, **사건 시드 SQL의 `factor.display_order`와 같은 값**을 쓴다(시드 작성 규칙 — 사건 파일 6장 번호 = `display_order`). `caseId`는 파일 구분용 값일 뿐 DB와 맞출 필요가 없다. `to_seed_sql.py`가 만드는 SQL은 `legal_case.title`로 사건을, `factor.display_order`로 요소를 **실행 시점에** 찾으므로, DB의 auto-increment id가 환경마다 달라도(로컬은 가상 시드가 먼저 들어가 번호가 밀린다) **같은 SQL 파일 하나를 로컬 · 운영에 그대로 쓸 수 있다.** 시드 적재 후 ID를 조회해 입력 파일에 옮겨 적는 과정이 필요 없다.

  적재 SQL은 판단 요소(`display_order` · `label`)가 모두 이 사건(`title`) 소속인지 먼저 확인하고, 아니면 오류를 내고 멈춘다(6단계). `display_order` 숫자만이 아니라 `label`까지 맞는지 보므로, 시드에서 요소 순서가 바뀌었는데 사건 파일을 갱신하지 않은 경우도 걸러진다. `legal_case.title`과 `factor(case_id, display_order)`에는 DB 유니크 제약이 있다(V7 마이그레이션).
- `penaltyRules`에는 사건의 `penalty_rule` 행을 그대로 넣는다(법정형에 있는 형벌만, 무죄 제외).
  - `DEATH` · `LIFE`의 `allowedMin` ~ `allowedMax`는 **작량감경해 징역으로 선고할 때의 범위**(개월)다. 예: 무기 120 ~ 600, 사형 240 ~ 600 (ERD 3-1 `penalty_rule`).
  - `DEATH` · `LIFE`는 `suspensionAllowed: false`여야 한다(감경해도 징역 10년 이상).
- `references`에는 양형기준(사건 발생 시점 버전)과 **대상 사건을 뺀** 유사 판례만 넣는다(FR-4-2).
- **실제 판결 · 사용자 판결 · 사건번호 · 법원명 · 판결문 원문은 넣지 않는다.** 들어 있으면 `build_prompt.py`가 멈춘다(FR-4-1, REQ-041).

### 2. 사전 학습 점검 (REQ-102) — 생성 전에 먼저

```bash
python3 check_contamination.py prompt case.json > out/contamination_prompt.md
# LLM에 새 대화로 3회 이상 넣고, 응답을 out/run1.json, run2.json, run3.json으로 저장
python3 check_contamination.py judge court_judgment_internal.json out/run1.json out/run2.json out/run3.json
```

| 판정 | 뜻 | 조치 |
| --- | --- | --- |
| `CONTAMINATED` | 사건을 안다고 답했거나 형벌 · 형량 · 집행유예를 정확히 맞힘 | 사건을 빼거나 다시 가공한다 |
| `SUSPECT` | 형벌 종류 · 집행유예 여부가 같고 형량이 매우 가까움 (징역 ±2개월, 벌금 ±10%), 또는 실제 판결이 사형 · 무기인데 형벌 종류를 맞힘 (형량 값이 없어 우연히 맞을 수 있음) | 팀이 검토해 판단한다 |
| `CLEAN` | 문제없음 | 3단계로 간다 |

- 형벌은 **최종 선고 형벌**로 비교한다. 실제 판결 파일은 `reducedTo`가 있으면 그 값으로 본다(예: 무기징역을 감경해 징역 15년이면 `PRISON` 180).
- 실제 판결 파일은 **이 점검에만** 쓰는 내부 자료다. 3단계 프롬프트에는 절대 넣지 않는다.

### 3. AI 판결 생성

```bash
python3 build_prompt.py case.json > out/prompt.md
```

`out/prompt.md`의 `[SYSTEM]` 부분을 시스템 프롬프트로, `[USER]` 부분을 사용자 메시지로 LLM에 넣고, 응답 JSON을 `out/ai_output.json`으로 저장한다. 쓴 모델 이름을 적어 둔다.

#### API로 생성하고 모델끼리 비교하기 (BE-30)

```bash
export OPENAI_API_KEY=...        # 쓰는 공급자의 키만 설정한다 (ANTHROPIC_API_KEY, GEMINI_API_KEY 또는 GOOGLE_API_KEY)

# 모델은 '공급자:모델ID'. --model을 여러 번 주면 모델마다 --runs회씩 생성한다
python3 generate.py run case.json --model openai:<모델ID> --model gemini:<모델ID> --runs 5

# 채팅 화면에서 받은 응답도 같은 묶음에 넣을 수 있다 (공급자는 manual, 이름은 자유)
python3 generate.py import case.json --model manual:gemini-app out/answer1.json out/answer2.json

# 모델별 비교표
python3 compare.py out/runs/case --case case.json --out out/runs/case/comparison.md --csv out/runs/case/comparison.csv
```

| 공급자 | 호출 API | 키 환경변수 | JSON 출력 강제 |
| --- | --- | --- | --- |
| `openai` | Chat Completions (`OPENAI_BASE_URL`로 호환 엔드포인트 지정 가능) | `OPENAI_API_KEY` | `response_format: json_object` |
| `anthropic` | Messages | `ANTHROPIC_API_KEY` | 프롬프트로만 (응답이 코드 블록이어도 받아 준다) |
| `gemini` | `generateContent` | `GEMINI_API_KEY` 또는 `GOOGLE_API_KEY` | `responseMimeType: application/json` |
| `manual` | 부르지 않음 (`import` 전용) | — | — |

- 모델 ID는 공급자가 정한 정확한 ID를 그대로 쓴다. 도구에 기본 모델은 없다(모델이 자주 바뀌므로 매번 명시한다).
- 검색 · 그라운딩 같은 도구는 붙이지 않는다. 매 회차가 독립 요청이라 이전 회차 응답이 섞이지 않는다("외부 LLM으로 생성할 때" 원칙과 같다).
- 옵션: `--temperature`(주지 않으면 공급자 기본값. 사고 모델 중에는 받지 않는 것이 있다), `--max-tokens`(기본 16000), `--timeout`(초, 기본 300), `--delay`(요청 사이 쉬는 초, 무료 등급 분당 한도용), `--batch`(묶음 이름, 기본은 사건 파일 이름).
- 한도 초과(429) · 서버 오류(5xx)는 최대 3번까지 자동으로 다시 보낸다. 그래도 실패하면 그 회차는 "호출 실패"로 기록하고 다음 회차로 넘어간다. API 키가 없으면 아무것도 기록하지 않고 바로 멈춘다.
- 공급자를 추가하려면 `llm.py`에 `_call_<공급자>` 형태의 함수를 만들고 `PROVIDERS` · `API_KEY_ENVS`에 등록한다.

**결과 폴더** (`out/runs/<batch>/`, git 제외)

| 파일 | 내용 |
| --- | --- |
| `batch.json` | 사건 제목 · 프롬프트 버전 · 프롬프트 해시. 사건이나 프롬프트가 바뀌었는데 같은 묶음에 넣으려 하면 멈춘다(비교가 섞이지 않게). 새 이름은 `--batch`로 정한다 |
| `prompt.md` | 보낸 프롬프트 |
| `<공급자>__<모델>/run-NNN.json` | 실행 한 건: 모델 · 실제 응답 모델 · 토큰 · 소요 시간 · 원문 응답 · 파싱 결과 · 검증 오류/경고. 회차 번호는 이어서 붙는다 |
| `<공급자>__<모델>/run-NNN.output.json` | 파싱한 판결 JSON만. 검수할 회차를 골라 4 ~ 6단계(`validate_output.py` · `to_seed_sql.py`)에 그대로 넣는다 |

생성할 때 검증(4단계)도 함께 돌려 기록한다. 그래도 검수 대상으로 고른 회차는 4 ~ 5단계를 그대로 거친다. `to_seed_sql.py`의 `--model-name`에는 `run-NNN.json`의 `meta.servedModel`(실제 응답한 모델 ID)을 적는다.

**비교표 항목** (`compare.py`)

| 항목 | 뜻 |
| --- | --- |
| 실행 · 호출 실패 · 파싱 실패 · 검증 통과 | 모델별 회차 수. 파싱 실패는 응답이 JSON이 아닌 경우 |
| 평균 오류 · 평균 경고 | 파싱된 회차의 `validate_output.py` 오류 · 경고 수 평균 |
| 최종 형벌 분포 · 징역 최소 / 중앙값 / 최대 · 집행유예 · 권고 범위 안 | 검증 통과 회차만 센다. 권고 범위는 `--case`를 줘야 계산한다 |
| 요소 방향 일관성 | 같은 모델의 회차끼리 판단 요소마다 다수 의견과 같은 비율의 평균. 1에 가까울수록 매번 같은 방향으로 판단한다(고르지 않은 요소도 "선택 안 함"으로 센다). 검증 통과 회차가 2개 이상일 때만 |
| 전체 다수 의견 일치율 | 모든 모델 · 회차를 합친 요소별 다수 의견과 같은 비율. 다른 모델들과 얼마나 비슷하게 판단하는지 |
| 평균 입력 · 출력 · 합계 토큰 | 공급자가 준 값을 그대로 쓴다. 사고 토큰 셈법이 공급자마다 달라 **합계끼리** 비교한다(아래 "토큰 사용 기록") |
| 평균 소요(초) | 요청 한 건의 응답 시간 |
| 회당 비용(USD) | `--price 공급자:모델=입력단가,출력단가`(100만 토큰당 USD)를 준 모델만 계산한다. 단가는 공급자 가격표에서 확인해 넣는다 |
| 실제 판결 형벌 일치율 · 징역 차이 평균 | `--court court_judgment_internal.json`을 줄 때만. **내부 전용** — 이 칸이 있는 비교표는 공유 · 커밋하지 않는다 |

- ⚠️ 모델을 여러 개 돌리면 비식별화한 사건 내용이 그 공급자 모두에게 전송된다. 공급자마다 데이터 사용 약관을 확인하고 팀이 정한 공급자만 쓴다("외부 LLM으로 생성할 때").
- 비교에 쓴 모델 중 실제로 적재할 모델은 **그 모델로** 2단계 사전 학습 점검을 거쳐야 한다(점검 결과는 모델마다 다르다).

비교표는 모델 선택 참고용이다. 어느 모델이 실제 판결에 가까운지가 "좋은 판결"의 기준은 아니다(AI 판결은 독립 판단이 목적이다). 검증 통과율 · 일관성 · 비용을 주로 보고, 판결 이유의 질은 팀 검수(5단계)로 본다.

### 4. 검증

```bash
python3 validate_output.py case.json out/ai_output.json
```

**오류(ERROR) — 하나라도 있으면 다시 생성한다**

- 허용되지 않은 형벌 종류, 허용되지 않은 감경 조합(`reducedTo`: 사형 → 무기 · 징역, 무기 → 징역만)
- 선고 가능 범위 밖 형량 (감경해 징역으로 선고하면 고른 형벌 — 사형 · 무기 — 항목의 범위로 검사)
- 최종 선고 형벌과 맞지 않는 값 (사형 · 무기인데 형량 · 집행유예가 있음, 징역인데 벌금 값이 있음 등)
- 집행유예 조건 위반 (허용 안 된 형벌, 사형 · 무기를 고른 경우(감경해도 불가), 징역 3년 초과 · 벌금 500만 원 초과, 기간 12 ~ 60개월 밖)
- 목록에 없는 판단 요소, 중복 요소, 방향이 `UP` / `DOWN`이 아님
- 판결 이유 · 한 줄 요약이 비었거나 요약이 100자 초과, 평가 표현("정답", "틀렸다", "이중 잣대" 등)
- 참고 자료 태그 형식 오류

**경고(WARN) — 팀 검수에서 반드시 확인한다 (REQ-078)**

- 권고 형량 범위 밖, 또는 최종 선고 형벌이 사형 · 무기 (판결 이유에 까닭이 있는지)
- 판결 이유에 **입력에 없는 숫자 표현**(금액 · 인원 · 횟수 · 기간)이 나옴 → 없는 사실을 만들었는지
- 고려한 요소가 없음, 요소 이유가 비어 있음

### 5. 팀 검수

스크립트가 잡지 못하는 부분은 사람이 본다(REQ-078).

- [ ] 판결 이유가 입력에 없는 사실 · 근거를 쓰지 않았는가
- [ ] 판단 요소 방향과 판결 이유가 서로 맞는가
- [ ] 참고 자료 태그가 실제로 준 자료와 맞는가
- [ ] 한 줄 요약이 중립적인가 (S-09 판결 카드에 그대로 보인다)
- [ ] 경고 항목을 모두 확인했는가

### 6. 적재 SQL 만들기

```bash
# 사람이 psql에 바로 붙여 넣을 때 (기본값, BEGIN ~ COMMIT으로 감싼다)
python3 to_seed_sql.py case.json out/ai_output.json \
    --model-name "사용한 모델" --reviewed-by "검수자" --reviewed-at "2026-10-06T00:00:00+09:00" \
    [--accept-warnings] > out/ai_judgment.sql

# 실제 사건 비공개 저장소(backend/private-seed)의 Flyway R__ 반복 마이그레이션에 그대로 쓸 때
python3 to_seed_sql.py case.json out/ai_output.json \
    --model-name "사용한 모델" --reviewed-by "검수자" --reviewed-at "2026-10-06T00:00:00+09:00" \
    --flyway > R__30_ai_judgment.sql
```

- `--model-name` · `--reviewed-by`는 50자 이내 (`ai_generation` 컬럼 길이)
- `--reviewed-at`은 **팀 검수를 마친 시각**(ISO 8601, 시간대 필수, 예: `2026-10-06T00:00:00+09:00`)이다. 시간대 오프셋은 실제 범위(UTC−12:00 ~ UTC+14:00) 안이어야 한다(PostgreSQL이 받지 못하는 값을 SQL 만들기 전에 막는다). `ai_generation.reviewed_at`에 고정값으로 들어간다. 빼면 `now()`가 들어가 SQL이 **실행된 시각**(운영 첫 배포일, 로컬에서 띄운 날 등)이 검수 시각으로 남는다.
  - **`--flyway`에는 필수다.** Flyway 파일은 로컬 · 개발 · 운영에서 각각 다른 시각에 실행되므로, 빼면 환경마다 검수 시각이 달라진다. 빼면 오류로 멈춘다.
  - 기본(psql 수동 실행)은 빼도 SQL을 만들지만 경고를 낸다. 현재보다 미래 시각을 넣으면 입력 실수일 수 있어 경고한다.
  - 고정해도 **SQL이 다시 실행되면 `judgment` · `ai_generation` 행은 새로 생긴다**(기존 공개 AI 판결은 비공개로 남음, REQ-079). 고정하면 그 행들의 검수 시각이 실행 날짜와 관계없이 같은 값(실제 검수 시각)으로 남는다.
  - `created_at`(적재 시각)은 `now()` 그대로다.
- `--flyway`는 `BEGIN;` · `COMMIT;`을 빼고 출력한다. Flyway는 마이그레이션마다 자체 트랜잭션으로 감싸므로, 파일 안에 또 `BEGIN`/`COMMIT`이 있으면 그 트랜잭션이 중간에 끝나 적용 기록이 어긋날 수 있다. 붙이지 않으면(기본값) 지금처럼 `BEGIN`/`COMMIT`을 포함해 수동 `psql` 실행에 바로 쓸 수 있다.
- 감경해 형벌 종류가 바뀐 판결은 `judgment.reduced_to`에 함께 들어간다

- 검증을 다시 돌려 오류가 있으면 SQL을 만들지 않는다. 경고가 있으면 검수했다는 뜻으로 `--accept-warnings`가 필요하다.
- 사건은 `legal_case.title`로, 판단 요소는 `factor.display_order` · `label`로 **실행 시점에 조회**해 쓴다. 제목 조회는 STRICT라 제목이 없으면(`NO_DATA_FOUND`), 중복돼 있으면(`TOO_MANY_ROWS`) 각각 다른 메시지로 멈춘다. 요소는 번호(`display_order`)와 라벨(`label`)이 모두 DB와 맞는지 확인하고, 아니면 `RAISE EXCEPTION`으로 멈춘다(트랜잭션 전체 취소) — 번호만 보면 시드의 요소 순서가 바뀌었을 때(요소 추가 · 삭제 · 순서 변경) 엉뚱한 요소에 방향이 붙어도 걸러지지 않기 때문이다. 그래서 로컬 · 운영처럼 auto-increment id가 다른 환경에도 같은 SQL을 쓸 수 있다.
- `legal_case.title` · `factor(case_id, display_order)`의 유일성은 DB 유니크 제약(V7 마이그레이션)으로도 막는다. 입력에 `$…$` 형태의 문자열이 있어도(드물게 reasoning · summary 등에 `$sql_seed$` 같은 문자열이 그대로 들어오는 경우) `DO` 블록의 dollar-quote 구분자가 깨지지 않도록, 구분자는 내용과 겹치지 않는 값을 자동으로 고른다.
- 기존 공개 AI 판결은 비공개로 바꾸고 새 판결을 공개한다. 기존 행은 지우지 않는다(REQ-079).
- `ai_generation`에 입력 프롬프트 전체(`input_snapshot`) · 원본 출력(`raw_output`) · 프롬프트 버전 · 검수자를 남긴다.
- 만든 SQL은 실제 대표 사건 비공개 저장소(`backend/private-seed`, BE-17)의 `seed/` 폴더에 `R__30_...` 파일로 넣는다. 공개 저장소의 BE-16 시드 파일(`db/seed/`)에는 실제 사건 내용을 넣지 않는다.

## 외부 LLM으로 생성할 때

2단계(사전 학습 점검)와 3단계(생성)는 팀이 정한 LLM 서비스(Claude 앱, Google AI Studio · Gemini, 각 API 등)에서 한다. 모델은 바뀔 수 있으므로 아래 원칙은 서비스와 관계없이 지킨다. **모델을 바꾸면 2 ~ 6단계를 처음부터 다시 한다**(점검 결과는 모델마다 다르다).

### 공통 원칙

| 설정 | 이유 |
| --- | --- |
| **웹 검색 · 그라운딩(검색 연동)을 끈다** | 실제 판결을 찾아볼 수 있으면 독립 판단과 사전 학습 점검이 둘 다 의미가 없어진다 |
| **메모리 · 저장된 정보 · 이전 대화 참조를 끄고, 매번 새 대화를 연다** | 이 사건에 대한 예전 대화가 섞일 수 있다. 점검 3회는 각각 새 대화에서 받는다 |
| **파일을 올리지 않는다** | 판결문 · `court_judgment_internal.json` · 프로젝트 지식 파일은 넣지 않고, 도구가 만든 프롬프트 텍스트만 붙여 넣는다 |
| **모델 하나를 정하고 정확한 이름을 적는다** | 생성과 점검에 같은 모델을 쓰고, `--model-name`에 화면 표시 이름이 아닌 모델 ID까지 적는다(50자 이내, 예: `Claude Opus 5.5 (Claude 앱)`, `<Gemini 모델 ID> (AI Studio)`) |
| **사건 정보는 비식별화된 값만 보낸다** | 외부 서비스로 보내는 것이다. 무료 등급은 입력 · 응답이 서비스 개선에 쓰이거나 사람이 검토할 수 있는 조건이 많으므로, 쓰기 전에 약관을 확인하고 팀이 알고 정한다 |

- 시스템 프롬프트 칸이 있으면 `out/prompt.md`의 `[SYSTEM]` 부분을 그 칸에, `[USER]` 부분을 메시지로 넣는다. 칸이 없는 일반 대화는 `out/prompt.md` **전체**(`# [SYSTEM]` ~ `# [USER]` 끝)를 한 메시지로 붙여 넣는다.
- 응답에서 **JSON만** 복사해 저장한다. 앞뒤 설명이나 코드 블록 표시만 지우고(코드 블록으로 감싸 와도 검증 스크립트가 받아 준다), **값은 손으로 고치지 않는다.** 검증 오류가 나면 같은 대화에서 고쳐 달라고 하지 말고 새 대화에서 다시 생성한다.
- 온도 등 생성 설정을 바꿨다면 기본값이 아닌 값을 기록해 둔다.

### 서비스별 메모

- **Claude 앱(claude.ai)**: 임시 채팅(incognito)을 쓰면 메모리 · 이전 대화 참조가 꺼진다. 일반 대화에는 시스템 프롬프트 칸이 없다.
- **Google AI Studio · Gemini**: AI Studio에는 시스템 지시 칸과 토큰 수 표시가 있어 기록하기 쉽다. 검색 연동(Grounding with Google Search) 같은 도구는 끈다. Gemini 앱으로 할 때는 저장된 정보 · 이전 대화 참조 기능을 끄고 새 대화를 쓴다.
- **API로 호출할 때**: `generate.py`를 쓰면 사용량이 실행 기록(`run-NNN.json`의 `meta.usage`)에 자동으로 남고 `compare.py` 비교표에 모인다. 직접 호출했다면 응답의 사용량 정보(Gemini는 `usage_metadata`, Claude는 `usage`)를 아래 "토큰 사용 기록"의 칸 대응표대로 옮겨 적는다. 서비스마다 사고 토큰을 세는 방식이 달라 합계를 직접 더하면 중복될 수 있다.

### 토큰 사용 기록

실행마다 `out/usage_log.md`(git 제외)에 한 줄씩 남긴다. 모델을 바꾸거나 재생성할 때 비용 · 한도를 비교하기 위해서다. 사건 내용은 적지 않는다.

| 날짜 | 단계 | 모델(정확한 ID) | 입력 토큰 | 출력 토큰 | 사고 토큰 | 합계 | 결과 | 메모 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| 예: 2026-10-02 | 사전 학습 점검 1 | | | | | | `CLEAN` | 새 대화 · 검색 끔 |

- 결과 칸: 점검은 `CLEAN` / `SUSPECT` / `CONTAMINATED`, 생성은 `validate_output.py` 결과(오류 · 경고 수)
- 무료 등급은 분당 · 일당 요청 수와 토큰 한도가 있다. 한도에 걸려 실패한 시도도 적어 둔다.

**칸 대응표** (API 사용량 정보 → 기록 칸, `llm.py`도 같은 규칙으로 옮긴다)

| 기록 칸 | Gemini (`usage_metadata`) | Claude (`usage`) | OpenAI (`usage`) |
| --- | --- | --- | --- |
| 입력 토큰 | `prompt_token_count` | `input_tokens` | `prompt_tokens` |
| 출력 토큰 | `candidates_token_count` | `output_tokens` (사고 토큰이 이미 포함됨) | `completion_tokens` (사고 토큰이 이미 포함됨) |
| 사고 토큰 | `thoughts_token_count` | 적지 않는다(`—`). 사고 토큰은 `output_tokens`에 들어 있다 | 적지 않는다(`—`). `completion_tokens_details.reasoning_tokens`는 출력에 이미 들어 있다 |
| 합계 | `total_token_count`를 **그대로** 옮긴다. 사고 토큰을 다시 더하지 않는다 | 입력 + 출력 | `total_tokens` |

- 화면(AI Studio 등)에 합계만 보이면 합계 칸에만 적고 나머지 칸은 비워 둔다(`—`). 칸을 추측해 나누지 않는다.
- 모델 · 서비스를 비교할 때는 같은 기준(합계)끼리 비교한다. 사고 토큰을 따로 적은 기록과 출력에 포함된 기록을 그대로 더하거나 비교하지 않는다.

## 프롬프트를 고칠 때

`common.py`의 `PROMPT_VERSION`(사전 학습 점검은 `CONTAMINATION_PROMPT_VERSION`)을 올린다. 적재 SQL의 `ai_generation.prompt_version`에 그대로 기록된다.

## 테스트

```bash
python3 -m unittest discover tests
```

## 참고

- 확장 단계의 서비스 안 생성 파이프라인(REQ-040 ~ 045)에서는 이 프롬프트의 "판단 순서"를 Agent 2(유사 판례) · Agent 3(양형기준) · Agent 4(최종 판결)로 나누고, 자료 검색은 RAG를 기본으로 한다(요구사항 FR-4-2 · 4-3).
- 예시 파일은 설명용 **가상 사건**이다(ERD 6장 · API 명세 v0.5와 같은 값). 법정형 · 선고 가능 범위는 형법 조문 그대로이고, 사실관계 · 권고 범위 · 양형기준 · 유사 판례 · 실제 판결은 지어낸 값이다.
