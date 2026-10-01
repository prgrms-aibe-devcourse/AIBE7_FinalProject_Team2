# AI 판결 오프라인 생성 · 검수 도구 (BE-15)

MVP의 AI 판결은 서비스 안에서 만들지 않고, **팀이 오프라인에서 생성 · 검수한 결과를 DB에 적재**한다(요구사항 FR-4-2, REQ-046). 이 폴더는 그 과정에 쓰는 프롬프트와 검증 스크립트다. 서비스 코드(`backend/`)와 분리된 운영 도구이며, 설치 없이 돌도록 **Python 3.8 이상 표준 라이브러리만** 쓴다.

LLM 호출은 이 도구가 하지 않는다. 프롬프트를 만들어 주면, 팀이 정한 LLM에 넣고 응답을 파일로 저장해 검증한다(Claude 앱으로 할 때의 주의 사항은 아래 "Claude 앱으로 생성할 때").

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

`examples/case_input.json`과 같은 형식으로 대표 사건 파일을 `cases/`에 만든다(BE-13 사건 가공 결과, `docs/cases/`의 사건 파일). 판결문에서 초안을 만들려면 `case-extractor/`를 쓴다.

- `caseId` · `factors[].factorId`는 DB의 `legal_case.id` · `factor.id`와 **같은 값**이어야 한다. 시드는 ID를 지정하지 않고 넣으므로(ERD 7장 마이그레이션 공통 규칙) ID는 적재 후에 정해진다. 그래서 **BE-16 사건 시드를 먼저 적재한 뒤**, DB에서 조회한 값을 입력 파일에 적는다.

  ```sql
  SELECT id FROM legal_case WHERE title = '대표 사건 제목';
  SELECT id, display_order, label FROM factor WHERE case_id = :caseId ORDER BY display_order;
  ```

  적재 SQL은 판단 요소가 모두 이 사건 소속인지 먼저 확인하고, 아니면 오류를 내고 멈춘다(6단계).
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
python3 to_seed_sql.py case.json out/ai_output.json \
    --model-name "사용한 모델" --reviewed-by "검수자" [--accept-warnings] > out/ai_judgment.sql
```

- `--model-name` · `--reviewed-by`는 50자 이내 (`ai_generation` 컬럼 길이)
- 감경해 형벌 종류가 바뀐 판결은 `judgment.reduced_to`에 함께 들어간다

- 검증을 다시 돌려 오류가 있으면 SQL을 만들지 않는다. 경고가 있으면 검수했다는 뜻으로 `--accept-warnings`가 필요하다.
- 판단 요소 id가 모두 이 사건(`caseId`) 소속인지 먼저 확인하고, 아니면 `RAISE EXCEPTION`으로 멈춘다(트랜잭션 전체 취소).
- 기존 공개 AI 판결은 비공개로 바꾸고 새 판결을 공개한다. 기존 행은 지우지 않는다(REQ-079).
- `ai_generation`에 입력 프롬프트 전체(`input_snapshot`) · 원본 출력(`raw_output`) · 프롬프트 버전 · 검수자를 남긴다.
- 만든 SQL은 BE-16 시드 스크립트에 넣거나 따로 실행한다.

## Claude 앱으로 생성할 때

2단계(사전 학습 점검)와 3단계(생성)를 Claude 앱(claude.ai)에서 할 때 지킨다.

| 설정 | 이유 |
| --- | --- |
| **웹 검색을 끈다** | 실제 판결을 찾아볼 수 있으면 독립 판단과 사전 학습 점검이 둘 다 의미가 없어진다 |
| **임시 채팅(incognito)을 쓰고, 매번 새 대화를 연다** | 메모리 · 이전 대화 참조가 이 사건에 대한 예전 대화를 섞을 수 있다. 점검 3회는 각각 새 대화에서 받는다 |
| **파일을 올리지 않는다** | 판결문 · `court_judgment_internal.json` · 프로젝트 지식 파일은 넣지 않고, 도구가 만든 프롬프트 텍스트만 붙여 넣는다 |
| **모델 하나를 정하고 이름을 적는다** | 생성과 점검에 같은 모델을 쓰고, `--model-name`에 그대로 적는다 (예: `Claude Opus 5.5 (Claude 앱)`) |

- 일반 대화에는 시스템 프롬프트 칸이 없으므로 `out/prompt.md` **전체**(`# [SYSTEM]` ~ `# [USER]` 끝)를 한 메시지로 붙여 넣는다.
- 응답에서 **JSON만** 복사해 저장한다. 앞뒤 설명이나 코드 블록 표시만 지우고, **값은 손으로 고치지 않는다.** 검증 오류가 나면 같은 대화에서 고쳐 달라고 하지 말고 새 대화에서 다시 생성한다.
- 사건 정보는 비식별화된 값만 붙여 넣는다(외부 서비스로 보내는 것이다).

## 프롬프트를 고칠 때

`common.py`의 `PROMPT_VERSION`(사전 학습 점검은 `CONTAMINATION_PROMPT_VERSION`)을 올린다. 적재 SQL의 `ai_generation.prompt_version`에 그대로 기록된다.

## 테스트

```bash
python3 -m unittest discover tests
```

## 참고

- 확장 단계의 서비스 안 생성 파이프라인(REQ-040 ~ 045)에서는 이 프롬프트의 "판단 순서"를 Agent 2(유사 판례) · Agent 3(양형기준) · Agent 4(최종 판결)로 나누고, 자료 검색은 RAG를 기본으로 한다(요구사항 FR-4-2 · 4-3).
- 예시 파일은 설명용 **가상 사건**이다(ERD 6장 · API 명세 v0.5와 같은 값). 법정형 · 선고 가능 범위는 형법 조문 그대로이고, 사실관계 · 권고 범위 · 양형기준 · 유사 판례 · 실제 판결은 지어낸 값이다.
