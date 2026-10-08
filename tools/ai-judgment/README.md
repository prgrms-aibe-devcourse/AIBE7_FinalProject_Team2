# AI 판결 오프라인 생성 · 검수 도구 (BE-15)

MVP의 AI 판결은 서비스 안에서 만들지 않고, **팀이 오프라인에서 생성 · 검수한 결과를 DB에 적재**한다(요구사항 FR-4-2, REQ-046). 이 폴더는 그 과정에 쓰는 프롬프트와 검증 스크립트다. 서비스 코드(`backend/`)와 분리된 운영 도구이며, 설치 없이 돌도록 **Python 3.8 이상 표준 라이브러리만** 쓴다.

AI 판결 생성(3단계)은 두 가지 방법 중 하나로 한다. 외부 LLM 서비스에서 할 때의 주의 사항은 아래 "외부 LLM으로 생성할 때"를 따른다.

- **API로 생성 (BE-30)**: `generate.py`가 공급자(OpenAI · Anthropic · Gemini) API를 직접 불러 여러 모델 · 여러 회차로 생성하고 검증까지 기록한다. 모델은 옵션으로 갈아끼우고, `compare.py`가 모델별 비교표를 만든다. 이것도 표준 라이브러리만 쓴다.
- **채팅 화면에서 생성**: `build_prompt.py`로 프롬프트를 만들어 LLM 서비스에 붙여 넣고 응답을 파일로 저장한다. 저장한 응답은 `generate.py import`로 같은 기록에 넣어 비교할 수 있다.

**전체 자동 실행 (BE-31)**: `pipeline.py`가 판결문 → 비식별화 → (사전 학습 점검) → 생성 → 회차 선택 → 적재를 한 명령으로 이어서 실행한다. 사람 검수 없이 **비공개로 적재하고, 관리자가 나중에 검수 · 공개한다(후검수)**. 아래 "파이프라인 (BE-31)". 아래 "작업 순서" 1 ~ 6단계는 단계별로 직접 돌릴 때의 방법이다.

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
| `pipeline.py` | 판결문 → 적재까지 자동 실행 · 단계 분기 · 이어 하기 (BE-31) |
| `pipeline.example.json` | 파이프라인 설정 예시 (복사해 `cases/`에 두고 고친다) |
| `court_seed_sql.py` | 재판부 판결(BE-14 형식) → **비공개** 적재 SQL (`judgment` COURT · `judgment_factor`) (BE-38) |
| `case_seed_sql.py` | 비식별화한 사건 파일 → 사건 콘텐츠 적재 SQL (`legal_case` DRAFT · 섹션 · 형벌 규칙 · 판단 요소 · 원본 판결문) (BE-31) |

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
# LLM에 새 대화로 5회 이상(권장 10회) 넣고, 응답을 out/run1.json ... 으로 저장
python3 check_contamination.py judge court_judgment_internal.json out/run*.json [--min-answered 5]
```

응답 하나를 먼저 **분류**하고, 회차 묶음을 **비율로 판정**한다(BE-37). 예전 기준(징역 ±2개월, 가장 나쁜 1건)은 형량이 짧은 사건에서 흔한 형량만 말해도 거의 항상 `SUSPECT`가 나와 변별력이 없었다.

| 응답 분류 | 뜻 |
| --- | --- |
| `KNOWS` | 사건을 안다고 답함 |
| `EXACT` | 형벌 · 형량 · 집행유예를 정확히 맞힘 |
| `CLOSE` | 형벌 종류 · 집행유예 여부가 같고 형량이 허용 오차 안. 징역 허용 오차는 **실제 형량의 15%, 단 1 ~ 6개월로 제한**(4개월 → 1개월, 20개월 → 3개월, 40개월 이상 → 6개월), 벌금은 10% |
| `SAME_TYPE` | 실제 판결이 사형 · 무기이고 형벌 종류를 맞힘 (형량 값이 없어 우연히 맞을 수 있음) |
| `INVALID` | 응답 형식이 어긋남 (`knowsCase`가 true / false가 아님, 읽을 수 없음) |
| `FAR` | 그 밖 (형벌 종류 · 집행유예 여부가 다르거나 형량이 멂, 예측 없음) |

| 판정 (위에서부터) | 조건 | 조치 |
| --- | --- | --- |
| `CONTAMINATED` | `KNOWS`가 하나라도 있음 | 사건을 빼거나 다시 가공한다 |
| `CONTAMINATED` | `EXACT` 비율 ≥ 50%. 응답이 최소 개수보다 적으면 최소 개수로 나눈다(빠진 응답을 `FAR`로 쳐도 기준 이상이면 확정, 예: 응답 3개 모두 `EXACT` → 3/5) | 사건을 빼거나 다시 가공한다 |
| `INSUFFICIENT` | 응답이 최소 개수(기본 5)보다 적음(0개 포함) — 호출 실패 · 무료 한도로 일부만 돌아온 경우 판정을 확정하지 않는다 | 점검을 다시 한다 |
| `SUSPECT` | `EXACT`가 하나라도 있음, (`EXACT` + `CLOSE` + `SAME_TYPE`) 비율 ≥ 50%, 또는 `INVALID`가 있음 | 팀이 검토해 판단한다 |
| `CLEAN` | 그 밖 | 3단계로 간다 |

- 기준 값(최소 응답 수 · 허용 오차 비율 · 최소 · 최대 개월 · 벌금 비율 · 판정 비율)은 파이프라인 설정 `stages.contamination.criteria`로 바꾼다(아래 "파이프라인").
- 판정 근거에는 예측 형량 값을 쓰지 않는다(생성 기록 `generation_report`로 이어진다). 응답별 기록(내부 파일)에만 분류 이유가 남는다.
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
- 옵션: `--temperature`(주지 않으면 공급자 기본값. 사고 모델 중에는 받지 않는 것이 있다), `--max-tokens`(기본 16000), `--timeout`(초, 기본 300), `--delay`(요청 사이 쉬는 초, 무료 등급 분당 한도용), `--max-attempts`(요청 한 건을 보내는 최대 횟수, 첫 시도 포함, 기본 5) · `--max-wait`(재시도 사이 한 번에 기다릴 최대 초, 기본 120) — "무료 등급 한도 대응", `--batch`(묶음 이름, 기본은 사건 파일 이름).
- **HTTPS 인증서(BE-34)**: 인증서 검증은 항상 켜 둔다. 기본 인증서 위치에 인증서가 없으면(예: macOS python.org Python) `/etc/ssl/cert.pem` 같은 시스템 CA 묶음을 자동으로 찾아 쓴다. 그래도 `CERTIFICATE_VERIFY_FAILED`가 나면 `SSL_CERT_FILE`에 인증서 묶음 경로를 지정하거나(macOS: `/etc/ssl/cert.pem`) python.org Python의 `Install Certificates.command`를 한 번 실행한다. 인증서 오류는 다시 보내지 않고 바로 해결 방법을 안내한다. 검증을 끄는 옵션은 없다.
- 과부하(503 · 529) · 한도 초과(429) · 서버 오류(5xx)는 원인에 맞게 기다렸다가 자동으로 다시 보낸다(기본 최대 5번, "무료 등급 한도 대응"). 그래도 실패하면 그 회차는 원인(과부하 / 분당 한도 / 일 한도)을 적어 "호출 실패"로 기록하고 다음 회차로 넘어간다. 일 한도 · 크레딧 소진이면 그 모델의 남은 회차는 부르지 않고 건너뛴다. API 키가 없으면 아무것도 기록하지 않고 바로 멈춘다.
- 공급자를 추가하려면 `llm.py`에 `_call_<공급자>` 형태의 함수를 만들고 `PROVIDERS` · `API_KEY_ENVS`에 등록한다.

**결과 폴더** (`out/runs/<batch>/`, git 제외)

| 파일 | 내용 |
| --- | --- |
| `batch.json` | 사건 제목 · 프롬프트 버전 · 프롬프트 해시. 사건이나 프롬프트가 바뀌었는데 같은 묶음에 넣으려 하면 멈춘다(비교가 섞이지 않게). 새 이름은 `--batch`로 정한다 |
| `prompt.md` | 보낸 프롬프트 |
| `<공급자>__<모델>/run-NNN.json` | 실행 한 건: 모델 · 실제 응답 모델 · 토큰 · 소요 시간 · 원문 응답 · 파싱 결과 · 검증 오류/경고. 회차 번호는 이어서 붙는다 |
| `<공급자>__<모델>/run-NNN.output.json` | 파싱한 판결 JSON만. 검수할 회차를 골라 4 ~ 6단계(`validate_output.py` · `to_seed_sql.py`)에 그대로 넣는다 |
| `<공급자>__<모델>/run-NNN.factor-labels.json` | 생성 시점 사건 제목 · 판단 요소 `factorId → label` 스냅샷. 6단계 `to_seed_sql.py --factor-labels`에 넣으면, 이 출력이 고른 요소의 라벨이 그 뒤 바뀐 사건 파일로 적재하려 할 때 멈춘다(출력이 고르지 않은 요소의 라벨 변경 · 새로 추가된 요소는 이 검사로 잡히지 않는다) |

생성할 때 검증(4단계)도 함께 돌려 기록한다. 그래도 검수 대상으로 고른 회차는 4 ~ 5단계를 그대로 거친다. `to_seed_sql.py`의 `--model-name`에는 `run-NNN.json`의 `meta.servedModel`(실제 응답한 모델 ID)을 적는다.

**비교표 항목** (`compare.py`)

| 항목 | 뜻 |
| --- | --- |
| 실행 · 호출 실패 · 파싱 실패 · 검증 통과 | 모델별 회차 수. 파싱 실패는 응답이 JSON이 아니거나, JSON이어도 판결 객체가 아닌 경우(배열 · 숫자 · 문자열) |
| 평균 오류 · 평균 경고 | 파싱된 회차의 `validate_output.py` 오류 · 경고 수 평균 |
| 최종 형벌 분포 · 징역 최소 / 중앙값 / 최대 · 집행유예 · 권고 범위 안 | 검증 통과 회차만 센다. 권고 범위는 `--case`를 줘야 계산한다 |
| 요소 방향 일관성 | 같은 모델의 회차끼리 판단 요소마다 다수 의견과 같은 비율의 평균. 1에 가까울수록 매번 같은 방향으로 판단한다(고르지 않은 요소도 "선택 안 함"으로 센다). 검증 통과 회차가 2개 이상일 때만 |
| 전체 다수 의견 일치율 | 모든 모델 · 회차를 합친 요소별 다수 의견과 같은 비율. 다른 모델들과 얼마나 비슷하게 판단하는지 |
| 평균 입력 · 출력 · 합계 토큰 | 공급자가 준 값을 그대로 쓴다. 사고 토큰 셈법이 공급자마다 달라 **합계끼리** 비교한다(아래 "토큰 사용 기록") |
| 평균 소요(초) | 요청 한 건의 응답 시간 |
| 회당 비용(USD) | `--price 공급자:모델=입력단가,출력단가`(100만 토큰당 USD)를 준 모델만 계산한다. 단가는 공급자 가격표에서 확인해 넣는다 |
| 실제 판결 형벌 일치율 · 징역 차이 평균 | `--court court_judgment_internal.json`을 줄 때만. **내부 전용** — 이 칸이 있는 비교표는 공유 · 커밋하지 않는다. 다른 사건의 판결로 비교하지 않게 판결 파일의 `caseTitle`이 묶음의 사건 제목(`case.json`의 `title`)과 같아야 한다. `case-extractor`로 만든 파일에는 들어 있고, 예전에 만든 파일은 `"caseTitle"`을 직접 넣는다 |

- ⚠️ 모델을 여러 개 돌리면 비식별화한 사건 내용이 그 공급자 모두에게 전송된다. 공급자마다 데이터 사용 약관을 확인하고 팀이 정한 공급자만 쓴다("외부 LLM으로 생성할 때").
- 비교에 쓴 모델 중 실제로 적재할 모델은 **그 모델로** 2단계 사전 학습 점검을 거쳐야 한다(점검 결과는 모델마다 다르다). 파이프라인은 `contamination`을 켜면 생성 모델마다 점검한다.

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

스크립트가 잡지 못하는 부분은 사람이 본다(REQ-078). 파이프라인(BE-31)으로 적재할 때는 이 검수를 관리자 페이지에서 공개 전에 한다(후검수).

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
    --factor-labels out/runs/case/openai__gpt-5/run-001.factor-labels.json \
    [--accept-warnings] > out/ai_judgment.sql

# 실제 사건 비공개 저장소(backend/private-seed)의 Flyway R__ 반복 마이그레이션에 그대로 쓸 때
python3 to_seed_sql.py case.json out/ai_output.json \
    --model-name "사용한 모델" --reviewed-by "검수자" --reviewed-at "2026-10-06T00:00:00+09:00" \
    --flyway > R__30_ai_judgment.sql
```

- `--factor-labels`에 `generate.py`가 남긴 `run-NNN.factor-labels.json`(그 출력이 생성될 때 사건 제목 · `factorId → label` 스냅샷)을 주면, **이 출력이 고른 요소**에 한해 아래를 확인하고 다르면 멈춘다(`--accept-warnings`로 넘길 수 없는 하드 오류). 사건 파일과 DB를 함께 재시딩해 서로는 맞아떨어지는 경우에도, 이 출력이 지금과 다른 사건 · 라벨을 보고 판단했다면 잡아낸다(`validate_output.py`의 `factorId` 검사나 DB 대조만으로는 잡히지 않는다).
  - 스냅샷의 사건 제목이 지금 사건 파일의 제목과 다름
  - 이 출력이 고른 `factorId`가 스냅샷에 없음(다른 회차 · 다른 사건 파일의 스냅샷을 잘못 지정했을 가능성)
  - 이 출력이 고른 `factorId`의 라벨이 스냅샷과 지금 사건 파일에서 다름
  - **범위 밖(이 검사로 잡히지 않음)**: 이 출력이 고르지 않은 요소의 라벨 변경, 생성 뒤 새로 추가된 요소 — 출력이 참조하지 않으므로 적재 결과에 영향이 없다
  - 주지 않으면(기본값) 이 확인을 건너뛴다(기존 동작과 같음). `--flyway`에 주지 않으면 경고를 낸다(필수는 아니다)
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

## 파이프라인 (BE-31)

판결문을 넣으면 적재까지 자동으로 이어서 실행한다. **사람 검수 단계는 없다.** 대신 적재한 결과는 사용자에게 보이지 않고, 관리자가 검수한 뒤 공개한다(후검수, 관리자 페이지 BE-33 · FE-16).

```bash
cd tools/ai-judgment
cp pipeline.example.json cases/my-case.pipeline.json    # cases/는 git 제외. 판결문도 cases/raw/ 등에 둔다
# 설정에서 name · sources · stages.generate.models를 고친다

export ANTHROPIC_API_KEY=...   # extract (비식별화) — Claude를 쓸 때만. Gemini · OpenAI로 하려면 stages.extract.model에 gemini:모델ID · openai:모델ID
export OPENAI_API_KEY=...      # generate · contamination에 쓰는 공급자 키

python3 pipeline.py run cases/my-case.pipeline.json
python3 pipeline.py status cases/my-case.pipeline.json
```

### 단계

| 단계 | 하는 일 | 끄기 · 멈춤 조건 |
| --- | --- | --- |
| `extract` | `case-extractor`로 판결문(여러 개면 1심 · 항소심 함께)을 비식별화 · 구조화한다. 모델은 `stages.extract.model`로 고른다(Claude 모델 ID, `openai:모델ID`, `gemini:모델ID` — BE-35, `maxTokens`는 Claude 외 출력 상한). 목록으로 주면 앞 모델이 과부하 · 한도로 막혔을 때 다음 모델로 넘어간다(BE-45, "무료 등급 한도 대응"). 사건번호 · 법원명 · 선고일은 **마스킹 전에 로컬에서** 꺼내 내부 파일에만 둔다. 목록 카드 칸(소개 · 키워드 · 난이도 · 예상 시간)도 함께 만든다 | 모델이 서비스 대상이 아니라고 판정하면 멈춘다(`requireEligible: false`로 무시). 판정 기준은 `docs/cases/README.md` 1장 선정 조건 |
| `court` | 재판부 판결(COURT) 초안을 만든다(`case-extractor/court_draft.py`, BE-38). 마스킹한 원문을 모델에 보내 요약 · 이유 · 쉬운 설명 · 발췌 · 요소별 방향과 근거를 받고, **발췌 · 근거가 원문을 글자 그대로 인용했는지**(바꾼 식별 정보만 `⟦ ⟧`) · **형량이 최종 판결문 주문과 맞는지** · 개인정보 잔존을 자동으로 검사한다. 모델은 `stages.court.model`(없으면 extract 모델, 목록 가능) | 검사를 통과하지 못하면 **파이프라인이 멈춘다**(오류를 알려 주며 최대 2번 다시 요청한 뒤). 재판부 판결 없이 이어 가려면 `stages.court.enabled: false`로 끄거나 사람이 쓴 BE-14 JSON을 `inputs.courtDraft`로 넣고 `--from contamination`(또는 다음 단계)으로 다시 실행한다 |
| `contamination` | 모델마다 사전 학습 점검을 `runs`회(기본 10) 자동으로 묻고 판정한다(REQ-102). 응답을 분류해 **비율로** 판정한다(위 "2. 사전 학습 점검", BE-37). 호출 실패는 응답 수에 넣지 않는다(모두 실패해도 `INSUFFICIENT`로 판정해 `onInsufficient`를 따르고, 멈출 때도 다른 모델까지 점검한 뒤 멈춘다) | **기본 꺼짐**(`enabled: true`로 켬). `onContaminated`: `stop`(기본) · `exclude`(그 모델만 생성에서 뺌). `onSuspect`: `continue`(기본) · `exclude` · `stop`. `onInsufficient`(응답 부족): `stop`(기본) · `continue` · `exclude`. 기준 값은 `criteria`(예: `{"minAnswered": 5, "closeRatio": 0.15, "closeMinMonths": 1, "closeMaxMonths": 6, "closeFineRatio": 0.1, "exactRatio": 0.5, "suspectRatio": 0.5}`, 빈 값은 기본값). `minAnswered`가 `runs`보다 크면 설정 오류 |
| `generate` | 모델마다 `runs`회 생성 · 검증한다(`generate.py`와 같음). 검증 통과 회차가 없으면 한 번씩 더 생성한다(최대 `maxRetries`회) | 모든 모델에서 통과 회차가 없으면 멈춘다 |
| `select` | 검증을 통과한 회차 중 하나를 고른다 | 아래 "회차 선택" |
| `load` | 사건(DRAFT) + AI 판결(비공개 · PENDING) 적재 SQL을 만들어 보관하고 로컬 DB에 적재한다 | `case: false`면 사건은 빼고 AI 판결만(사건이 이미 DB에 있을 때). `applyToDb: false`면 SQL만 만든다 |

**단계 분기 (명령 옵션)**: 끝난 단계는 다음 실행에서 건너뛰고 이어서 한다(상태: `out/pipeline/<name>/state.json`). 단 앞 단계가 다시 돌면 그 뒤 단계는 끝났어도 함께 다시 돈다(예: `generate`가 실패한 뒤 옵션 없이 다시 `run`하면 `generate → select → load`). 끝날 때 적재까지 했는지(DB 적재 · SQL만 · 적재 안 함)를 구분해 알려 준다.

| 옵션 | 뜻 |
| --- | --- |
| `--from <단계>` | 그 단계부터 끝났어도 다시 돌린다. 앞 단계를 다시 돌리면 뒤 단계 결과는 지운다(낡은 결과로 적재하지 않게) |
| `--until <단계>` | 그 단계까지만 돌린다 (예: `--until select`로 적재 전까지 보고 결정) |
| `--skip <단계>` | 이번 실행에서 뺀다 (여러 번 가능) |
| `--rerun` | 켜진 단계를 모두 다시 돌린다 |

설정 파일의 `retry`(`maxAttempts` · `maxWait`)는 모든 단계의 LLM 호출 재시도에 적용된다("무료 등급 한도 대응"). `stages.<단계>.enabled: false`는 항상 끈다. `extract`를 끄면 `inputs`(case · court · report · source)에 이미 만든 파일을 넣는다.

### 회차 선택 (`stages.select`)

| `strategy` | 고르는 방법 |
| --- | --- |
| `consensus` (기본) | 후보 전체(검증 통과 회차)의 판단 요소별 다수 의견과 가장 많이 일치하는 회차. 같으면 경고가 적은 것, 그다음 앞선 것 |
| `first-valid` | 검증을 통과한 첫 회차 |
| `fewest-warnings` | 경고가 가장 적은 회차 |
| `manual` | `run`에 직접 지정 (예: `"openai__gpt-x/run-003"`, `out/runs/<name>-<해시>/` 아래 경로). 검증을 통과한 회차만 된다 |

- `model`을 주면 그 모델의 회차 안에서만 고른다. 사전 학습 점검으로 뺀 모델은 후보가 아니다.
- 후보 회차는 생성 묶음 `out/runs/<name>-<프롬프트 해시 8자리>/`에 쌓인 모든 실행이다(같은 프롬프트의 이전 실행 포함). 사건 내용이나 프롬프트가 바뀌면(예: `--from extract`로 다시 가공) 새 묶음을 쓰므로 다른 프롬프트의 회차는 섞이지 않는다. 묶음 경로는 `status`의 생성 결과(`state.json`의 `batchDir`)에서 확인한다.
- `compare.py <묶음 경로>`로 비교표를 보고 `manual`로 바꿔 `--from select`로 다시 적재할 수 있다.

### 적재 (`stages.load`)

- **사건**: `legal_case.status='DRAFT'` → 사용자 목록 · 체험에서 보이지 않는다(서버가 `PUBLISHED`만 조회). 섹션 · 형벌 규칙 · 판단 요소 · 원본 판결문(`case_source`)을 함께 넣는다. 사건 발생일은 비식별화 단계가 원문 · 선고일과 대조해 확인한 값(또는 설정 `incidentDate`)을 넣는다(BE-38). 재판부 판결은 아래 "재판부 판결"대로 비공개로 넣는다(BE-38). 양형기준 연결은 넣지 않는다.
- **재판부 판결 (BE-38)**: `court` 단계 초안(또는 `inputs.courtDraft`)이 있으면 `judgment`(COURT · FINAL) · `judgment_factor`를 **비공개**(`is_published=false`)로 넣는다. 기존 공개 재판부 판결은 건드리지 않고, 같은 내용이 이미 있으면 건너뛴다. `load.court: false`면 뺀다. **court 단계를 다시 돌리면** 모델이 다른 글을 만들어 내용이 달라지므로 같은 사건에 비공개 재판부 판결 후보가 하나 더 쌓인다(기존 행은 지우지 않음, REQ-079와 같은 원칙). 관리자 후검수(BE-33)에서 후보 중 하나를 골라 공개한다. 초안은 **실제 판결이 들어 있는 내부 파일**이라 AI 판결 단계(contamination · generate)는 읽지 않는다. 사실 기록이므로 공개 전에 원 판결문과 대조해 검수한다(REQ-075 · 077).
- **공개에 필요한 것**: 사건 `PUBLISHED` + 공개 재판부 판결 + 공개 AI 판결. 셋이 갖춰지면 체험 흐름 전체(사전 판단 → 판결 → AI 판결 → 실제 판결 → 세 판결 비교)가 동작한다(임시 DB로 확인). 양형기준 연결(`guideline_id`)은 서비스 코드가 읽지 않아 비어 있어도 된다(이후 RAG로 연결).
- **AI 판결**: `judgment.is_published=false`, `ai_generation.review_status='PENDING'`(검수자 · 검수 시각 없음). 기존 공개 AI 판결은 그대로 둔다. `ai_generation.generation_report`(V8)에 검증 경고 · 사전 학습 점검 판정 · 회차 선택 이유 · 모델 · 토큰을 남겨 관리자가 검수할 때 본다(실제 판결 값은 넣지 않는다).
- **같은 SQL을 두 번 실행해도 안전하다**: 같은 제목의 DRAFT 사건이 있으면 사건 적재를 건너뛰고, 같은 실행(`runKey`)의 AI 판결이 있으면 건너뛴다(`runKey`는 DB 부분 유니크 인덱스로도 막는다). 판단 요소가 다르면 전체가 취소된다.
- **같은 제목의 공개 · 검토 중 사건이 있으면 멈춘다**(전체 취소). 모델이 만든 중립 제목은 다른 사건과 겹칠 수 있어서다. 이미 공개된 사건에 AI 판결만 넣으려면 `load.case: false`로 사건 SQL을 뺀다.
- **SQL 보관**: `sqlDir`(기본 `backend/private-seed/loads/`)에 `<시각>-<name>.sql`로 남긴다. 원본 판결문이 들어 있으므로 **비공개 저장소에만** 둔다. 보관 폴더의 상위가 실제 git 체크아웃이어야 하고(서브모듈을 받지 않아 빈 `private-seed` 폴더면 멈춘다), 공개 저장소 안이면 `backend/private-seed` 아래만 허용한다. Flyway가 읽는 `seed/`가 아니라서 자동 실행되지 않는다(관리자가 DB에서 바꾼 검수 상태를 R__ 재실행이 덮지 않게).
- **DB 적재는 로컬에만**: `db.mode`가 `docker`(기본, `lawnambul-postgres` 컨테이너. `DOCKER_HOST` · docker context가 원격이면 거부) 또는 `psql`(`DB_URL`. URL 호스트와 쿼리의 `host` · `hostaddr`가 모두 localhost여야 한다. 호스트 없는 URL은 `PGHOST`를 보고, 없으면 유닉스 소켓이라 허용). 운영은 보관한 SQL 파일을 서버에서 실행한다: `psql -v ON_ERROR_STOP=1 -f <파일>`.

### 보안 · 데이터

| 항목 | 처리 |
| --- | --- |
| 외부 전송 | `extract`는 정규식 마스킹을 거친 판결문을 설정한 비식별화 모델의 공급자(기본 Anthropic)로, `contamination` · `generate`는 비식별화한 사건 내용을 설정한 공급자로 보낸다. 실행할 때 전송 대상을 먼저 출력한다 |
| 재판부 판결 초안 | 실제 판결(형량 · 재판부 판단)이 들어 있다. `cases/<name>.court_draft.json`(git 제외) → DB 비공개 행에만 둔다. AI 판결 입력과 분리돼 있다(테스트로 확인) |
| 원본 판결문 정보 | 사건번호 · 법원명 · 선고일 · 원문은 마스킹 전에 로컬에서 꺼내 `cases/<name>.source_internal.json`(git 제외) → DB `case_source`(API는 `source_org`만 노출)에만 둔다. 모델에 보내지 않는다 |
| 비식별화 검수 | 사람 검수 없이 적재하므로 비식별화 누락이 있을 수 있다. **사건은 항상 DRAFT로만 넣고 자동으로 공개하지 않는다.** 관리자 페이지(BE-33 · FE-16)의 공개 승인이 비식별화 검수(REQ-075)를 겸한다 |
| 운영 DB | 파이프라인은 운영 DB에 접속하지 않는다. 운영 계정을 개발 PC에 두지 않는다 |
| 승인 상태 | 후검수 뒤 공개 · 승인 상태는 DB에만 있다(파일에 없음). 운영 DB 백업이 원본이다 |

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

### 무료 등급 한도 대응 (BE-36)

무료 등급은 서버가 바쁘거나(503) 호출 한도(429)에 걸리는 일이 잦다. 파이프라인은 사전 학습 점검 N회 · 생성 N회를 연속으로 부르므로, 실패를 그냥 두면 회차가 실패로 쌓인다. `llm.py`는 원인마다 다르게 대응한다.

| 원인 | 응답 | 대응 |
| --- | --- | --- |
| 서비스 과부하 | 503 · 529 | 5 · 10 · 20 · 40초…로 늘려 가며(±25% 지터) 다시 보낸다. 한 번에 `maxWait`초를 넘게 기다리지 않는다 |
| 분당 한도 | 429 | 응답이 알려 준 시간(`Retry-After` 헤더, Gemini는 본문 `retryDelay`) + 1초를 기다린다. 안내가 없으면 1분(분당 한도가 풀릴 시간)을 기다린다. 안내가 `maxWait`보다 길면 기다려도 소용없으므로 바로 끝낸다 |
| 일 한도 · 크레딧 소진 | 429 (Gemini `...PerDay...`, OpenAI `insufficient_quota`) | **다시 보내지 않는다.** 그 모델의 남은 회차 · 재생성도 건너뛴다 |
| 그 밖의 서버 오류 · 연결 실패 | 500 · 502 · 504 등 | 2 · 4 · 8초…로 다시 보낸다 |

- 최종 실패 시 오류 메시지가 원인(과부하 / 분당 한도 / 일 한도)과 다음에 할 일을 알려 준다. 실행 기록(`run-NNN.json`)의 `callErrorKind`에도 `overloaded` · `rate_limit` · `daily_quota`로 남는다.
- **재시도 설정**: 파이프라인은 설정 파일 최상위 `"retry": {"maxAttempts": 5, "maxWait": 120}`, `generate.py`는 `--max-attempts` · `--max-wait`. `maxAttempts`는 첫 시도를 포함한 횟수, `maxWait`는 한 번에 기다릴 최대 초다. 기본값으로 시작하고, 과부하가 길어지면 `maxAttempts`를 늘린다.
- **호출 사이 대기(`delay`) 권장**: 무료 등급은 분당 요청 수 한도가 있으므로 재시도에만 기대지 말고 `stages.contamination.delay` · `stages.generate.delay`(`generate.py`는 `--delay`)로 호출 사이를 띄운다. 값은 **60 ÷ (모델의 분당 요청 한도)초 이상**(여유를 조금 더 둔다)으로 잡는다. 예: 분당 5회면 13초 이상. 한도는 모델마다 · 시기마다 다르므로 서비스 화면(Google AI Studio의 요금 · 한도 표시 등)에서 확인한다. 점검은 `runs`번 연속이라 `delay: 0`이면 첫 분에 한도에 걸리기 쉽다.
- **모델 교차 호출 (BE-44)**: 사전 학습 점검 · 생성(`generate.py`와 파이프라인 모두)은 모델을 번갈아 가며 회차 순서로 부른다(`m1 1회, m2 1회, m1 2회 …`). 한 모델이 분당 한도에 연속으로 걸리지 않는다. 같은 모델의 호출 간격은 (모델 수 × 호출 사이 대기)가 되므로, 모델을 여러 개 쓰면 `delay`를 그만큼 줄여도 된다. 일 한도가 소진된 모델은 그 뒤 순서에서 빠지고 다른 모델은 계속 돈다. 단 `모델 수 × delay` 간격은 모든 모델이 호출되는 동안에만 성립한다. 모델이 빠지면 남은 모델의 호출 간격은 `delay`로 줄어드므로, 줄인 `delay`로는 분당 한도에 다시 걸릴 수 있다. 일 한도 소진까지 염두에 두면 `delay`를 줄이지 말고 `60 ÷ 분당 한도`초 이상으로 유지하는 쪽이 안전하다. 기록 · 회차 번호 · 판정은 모델마다 독립이다.
- **비식별화 · 재판부 초안 모델 대체 체인 (BE-45)**: 이 두 단계는 결과 하나만 내므로 `stages.extract.model` · `stages.court.model`에 모델 **목록**(예: `["gemini:<모델ID>", "openai:<모델ID>"]`)을 줄 수 있다. 앞 모델의 호출이 과부하 · 한도로 막히면(BE-36 재시도를 다 쓴 뒤, 일 한도 · 크레딧 소진은 바로) 다음 모델이 **같은 입력으로 처음부터** 다시 요청한다. 스키마 불일치 · 인용 검사 실패 · 서비스 대상 아님 같은 **품질 문제는 대체하지 않고** 그대로 멈춘다. 대체 모델에도 판결문이 전송되므로 전송 안내에 목록의 공급자가 모두 나오고, 모든 모델의 키를 시작 전에 확인한다. 응답한 모델과 건너뛴 모델 · 원인은 `report.json`(`court_report.json`)과 단계 결과(`fallbacks`)에 남는다. 문자열 하나로 주면 예전과 같다. 생성(`generate`)의 모델은 서로 독립된 투표자라 대체하지 않는다.
- 하루 한도가 모자라면 `runs`를 줄이거나, 다른 모델을 `models`에 함께 넣거나, 내일 `--from <단계>`로 이어서 한다. 모델은 각각 독립된 점검 · 선택 대상이므로 **한 모델이 막혔다고 다른 모델이 대신 생성하지는 않는다.**
- **API 키를 여러 개 돌려 쓰지 않는다.** 같은 프로젝트의 키는 한도를 함께 쓰고, 계정 · 프로젝트를 나눠 무료 한도를 늘리는 것은 서비스 약관을 어길 수 있다. 공급자마다 키 하나씩 두는 것은 괜찮다(비식별화한 사건이 그만큼 여러 곳으로 나가므로 팀이 정한 공급자만 쓴다).

### 무료 등급이 있는 모델 목록 (BE-45)

목록은 `free_models.json`(확인일 2026-10-08)에 있고, `llm.py`(`has_free_tier`)와 파이프라인 전송 안내가 읽는다. **"무료 등급이 있는 모델"이지 "지금 키가 무료"라는 뜻이 아니다** — 결제를 연결한 프로젝트의 키는 같은 모델도 유료다. 그래서 호출을 막지는 않고 안내만 한다.

**Gemini API** (출처: [가격 문서](https://ai.google.dev/gemini-api/docs/pricing), 확인일 2026-10-08)

| 무료 등급 있음 | 무료 등급 없음 |
| --- | --- |
| `gemini-3.8-flash` · `gemini-3.7-flash` · `gemini-3.6-flash` · `gemini-3.5-flash` · `gemini-3.5-flash-lite` · `gemini-3.1-flash-lite` · `gemini-3-flash-preview` · `gemini-2.5-pro` · `gemini-2.5-flash` · `gemini-2.5-flash-lite` | `gemini-3.1-pro-preview` · `gemini-omni-1.1-flash` |
| 임베딩: `gemini-embedding-2` | |

- **한도(분당 · 일당)는 문서에 표가 없다.** [AI Studio 한도 화면](https://aistudio.google.com/rate-limit)에서 계정별로 확인한다. 한도는 **프로젝트 단위**(API 키 단위가 아님)로 걸리고 일 한도는 **태평양 시간 자정**에 초기화된다(한국 시간으로는 여름 오후 4시 · 겨울 오후 5시경). 위 `delay` 계산(60 ÷ 분당 한도)에 이 값을 쓴다.
- **데이터 사용**: 무료 등급은 입력 · 응답을 제품 개선에 쓰고 사람이 검토할 수 있다. 유료 등급은 쓰지 않는다([약관](https://ai.google.dev/gemini-api/terms)).
- **자동 사용**: 아래 "무료 모델 자동 사용"을 켜면 이 목록의 모델을 파이프라인이 알아서 돌려가며 쓴다.
- **쓰임**: 무료 등급이 있는 모델은 비식별화한 사건 데이터를 보내는 **점검 · 생성**(`stages.contamination.models` · `stages.generate.models`, 교차 호출 BE-44)에서만 무료 키로 쓴다. **판결문 원문을 보내는 비식별화(`extract`) · 재판부 초안(`court`)에는 무료 키를 쓰지 않는다**(위 원칙, 모델 대체 체인에 넣는 모델도 마찬가지). 파이프라인은 시작할 때 이 단계에 무료 등급이 있는 모델이 들어 있으면 경고 안내를 출력한다.
- **다른 공급자(OpenAI · Anthropic)**: 이번에 공식 문서로 확인하지 않았다. 목록이 비어 있다는 것이지 무료 등급이 없다는 보증이 아니다. 확인하면 `free_models.json`에 `"<공급자>": {"source": …, "models": […]}`를 추가한다(공급자 이름은 `llm.py`의 `PROVIDERS`와 같게).
- **목록은 자주 바뀐다.** 쓰기 전에 `source`에서 다시 확인하고 `checkedAt`을 고친다. 이 목록은 공식 문서 요약 도구로 가져온 값이므로, 팀이 쓸 모델은 가격 문서에서 직접 한 번 더 확인한다. 같은 주제를 다룬 블로그 중에는 "Pro 모델은 2026-04-01부터 무료 등급에서 빠졌다"는 글이 있으나, 공식 가격 문서에는 `gemini-2.5-pro`가 무료로 표시되어 있어 공식 문서를 따랐다.

### 무료 모델 자동 사용 (BE-45)

`stages.generate.models`(점검 `stages.contamination.models`도 같음)에 `"free:<공급자>"`를 적으면, 모델 이름을 일일이 적지 않아도 `free_models.json`의 그 공급자 무료 모델을 파이프라인이 알아서 쓴다.

```json
"freeModels": { "max": 3, "include": null, "exclude": ["gemini-2.5-pro"] },
"stages": { "generate": { "models": ["free:gemini"], "runs": 3, "delay": 13 } }
```

| 설정 | 뜻 |
| --- | --- |
| `"free:gemini"` | 목록의 `gemini:` 무료 모델을 `free_models.json` 순서대로 가져온다. `"openai:a"`처럼 직접 적은 모델과 함께 쓸 수 있다(적은 위치 그대로) |
| `freeModels.max` (기본 3) | **동시에 쓰는 무료 모델 수의 상한(안전장치).** 나머지는 대기 목록에 둔다. 모델이 많을수록 무료 한도를 빨리 쓰고 점검 호출도 모델 수 × `runs`만큼 늘어난다 |
| `freeModels.include` | 쓸 모델 ID(접두어 없이) 목록. 적으면 이 모델만 쓰고 **적은 순서가 우선순위**다. 기본은 목록 전체 |
| `freeModels.exclude` | 뺄 모델 ID 목록 (예: 품질이 낮거나 한도가 빨리 닫히는 모델) |

**동작**
1. 실행 시작 때 조건(include · exclude)을 통과한 모델을 앞에서 `max`개 골라 쓰고, 나머지는 **대기**로 둔다. 어떤 모델을 골랐는지 로그와 `state.json`(`freeModels`)에 남는다. 이어 실행하면 같은 목록을 쓰고, 설정이 바뀌거나 `--rerun` · `--from contamination`으로 다시 돌리면 처음부터 다시 고른다. **`--from generate`는 목록을 그대로 둔다** — 점검에서 일 한도로 자리를 내준 모델(점검을 다 받지 못했다)이 생성에 되살아나지 않게 하려는 것이다. 점검이 꺼져 있어도 처음부터 다시 고르려면 `--from contamination`을 쓴다.
2. 사용 중인 무료 모델이 **일 한도 · 크레딧 소진**으로 막히면(BE-36), 대기 모델이 **그 자리를 채운다**(활성 개수를 `max`로 유지). 과부하 · 분당 한도는 BE-36 재시도와 BE-44 교차 호출이 처리하므로 모델을 바꾸지 않는다.
3. **새로 들어온 모델은 새로운 독립 투표자다.** 자기 이름으로 처음부터 `runs`회를 기록하고(재생성 중에 들어와도 `runs`회), 막힌 모델의 남은 회차를 대신 만들지 않는다. 막힌 모델이 이미 끝낸 회차는 그 모델 이름으로 남아 **회차 선택의 후보**에 그대로 들어간다(점검 단계에서 막혔다면 그 모델은 생성에 쓰지 않는다). 그래서 기록의 모델 이름과 실제 모델이 어긋나지 않는다.
4. **대체 모델도 사전 학습 점검을 먼저 통과해야 한다**(점검이 켜져 있으면). 생성 중에 들어오는 모델은 생성 전에 점검을 `runs`회 받는다. 설정(`onContaminated` · `onSuspect` · `onInsufficient`)이 `exclude`이거나 **`stop`이어도**(자동으로 들어온 모델 때문에 파이프라인 전체를 멈추지 않는다) 그 모델만 제외하고 다음 대기 모델을 본다. 설정이 `continue`면 그대로 쓴다. 점검 단계에서 자리를 채운 모델은 그 단계에서 함께 점검받고, 제외되면 그 자리는 **비워 둔다**(다른 대기 모델로 다시 채우지 않는다).
5. 대기 모델이 더 없으면 그 모델만 빠지고 나머지로 계속 진행한다.
6. 결과(`state.json` generate 단계의 `freeModels`)에 쓴 모델과 교체 내역(`replaced`: 어떤 모델이 어떤 모델로, 어느 단계에서)이 남는다.

**제한**
- `free:`는 **점검 · 생성에서만** 쓴다. 판결문 원문을 보내는 `extract` · `court`에는 쓸 수 없다(설정 검증이 거부한다). 거기서는 `["모델A", "모델B"]` 대체 체인을 쓴다(위 BE-45 항목, 유료 키).
- `generate.py`(단독 실행)는 `free:`를 모른다. `--model`에 모델을 직접 적는다.
- 생성 중 대체 모델의 점검이 실패하거나 오류가 나도 그때까지의 생성 요약(`batchDir` · `models`)은 단계 결과에 남는다.
- 목록의 모델이 무료인지는 키에 달려 있다. 결제를 연결한 프로젝트의 키라면 같은 모델도 유료이고 한도도 다르다(위 안내). `max`는 한도를 아끼려는 상한이지 무료를 보장하는 설정이 아니다.
- 무료 한도는 **프로젝트 단위**로 걸리고 한도 값은 모델에 따라 다르다고 공식 문서에 나온다. 같은 프로젝트의 모델들이 서로 한도에 영향을 주는지는 AI Studio 한도 화면에서 확인한다. 한 모델이 막힌 뒤 대기 모델이 같은 프로젝트에서 곧바로 막히면 `max`를 줄이고 `delay`를 늘린다.

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
