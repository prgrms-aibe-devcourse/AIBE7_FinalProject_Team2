# 대본 생성 프롬프트 블록 (BE-119)

> 블록 본문은 모델이 그대로 읽으므로(`common.read_prompt`) 메타 정보(적용 조건 · 넣는 단위 · 충돌 규칙)는 블록 파일에 두지 않고 이 색인에 둔다. 이 표는 `../../script_assembler.py`의 상수(`GUIDE_UNITS` · `SENSITIVE_FLAGS` · `LAST_QUESTION`)로 옮겼다(BE-121) — 표를 고치면 상수도 함께 고친다. 근거는 `docs/decisions/BE-119.md`.
> 사건 분류(어느 블록을 넣을지)는 설정으로 넣거나 파이프라인 `classify` 단계(`../../case_classify.py`, BE-123)가 정한다.
> 원칙: 블록에는 사실(숫자 · 금액 · 날짜 · 사건 내용)을 쓰지 않는다. 사실은 `scene_data`로만 들어간다.
> 단위: PAPER 1막 신문 기사 · U0 설계도 · U1 1막 해설 · U2 재판 도입부(모두진술 · 쟁점 정리 · 피고인신문) · U3 증거조사 · U4 3막(검사 의견 · 최종변론)

## 블록 형식

- 블록은 `## 공통`과 단위별 절(`## U0` ~ `## U4`)로 나눌 수 있다. 조립기는 **공통 절 + 지금 만드는 단위의 절만** 넣고, `##` 제목 줄은 넣지 않는다. 절이 없는 블록(분위기 · 민감 가드)은 전체가 공통이다.
- 이유: 블록 하나에 여러 단위용 지시가 섞여 있으면 모델이 조립된 단위와 상관없이 따라 한다(시험에서 쟁점 정리 지시가 3막 검사 의견으로 샜다, `docs/decisions/BE-119.md`).
- 1막 신문 기사(PAPER)는 `../script_role_paper.md`(편집자)를 ROLE로 쓰고, 페르소나 · 분위기 블록 없이 공통 가드(대사 길이 줄 제외) · 민감 가드만 넣는다(기사는 중립).
- 1막 해설(U1)처럼 법정 밖 안내는 `../script_role_guide.md`(안내 배역)를, 2막 진행 해설(쟁점 정리)과 설계도(U0)는 `../script_role_narrator.md`(진행 해설)를 ROLE로 쓴다.

## 조립 단계에서 붙이는 고정 문구 · 정규화

모델에게 맡기면 흔들리는 정형 요소는 생성하지 않고 조립 단계에서 붙이거나 다듬는다(BE-119 시험에서 흔들림 확인).

| 대상 | 규칙 |
| --- | --- |
| 피고인신문 마지막 질문 | 쟁점 유형별 고정: ①인정 · ③부인 · ④정신상태 → "공소사실을 인정합니까?", ②고의 다툼 · ⑤방어 주장 → "행위 사실을 인정합니까?". 답은 사건 데이터의 인정 여부로 "…네, 인정합니다." / "…아닙니다." |
| 피고인신문 답(A씨 역) | "예 · 네"로 시작하면 "…네.", "아니요 · 아닙니다"면 "…아닙니다."로 정규화(최소 응답) |
| 최후진술 | 두지 않는다(10/28 결정: "…이상입니다." 한마디를 위해 의상을 바꾸고 가운데로 나오는 장면이 어색함). 3막 절차는 검사 의견 · 최종변론 · 판결 |

## 검수 조건 (`../../script_review.py`, BE-122)

생성이 끝난 단위 블록(U0 ~ U4)을 아래 표로 보고 결과 목록을 대본의 `review`로 남긴다. 오류가 하나라도 있으면 대본은 공개할 수 없다(`publishable: false`).

| 검사 | `code` | 결과 | 보는 단위 · 기준 |
| --- | --- | --- | --- |
| 대사 길이 100자 · 두 줄 초과 | `LINE_TOO_LONG` | 오류 | 화면 대사(설계도 · 카드 제외) |
| 세계관 금지어 | `BANNED_WORD` | 오류 | BE-107 `BANNED_PATTERNS` |
| 입력에 없는 숫자 | `NUMBER_NOT_IN_SOURCE` | 오류 | 그 단위가 받은 데이터와 비교(U1은 기사, U3는 제목 · 개요 · 그 판단 요소) |
| 한글 수사 | `KOREAN_NUMERAL` | 오류 | 입력의 아라비아 숫자를 한글로 풀었을 때만("12번" → "열두 번"). "한 번 더", "두 사람"처럼 수를 옮긴 게 아닌 말은 보지 않는다 |
| 안내 · 해설 단위의 "~습니다 · ~입니다" 끝맺음 | `FORMAL_ENDING` | 오류 | U1 해설 · U2 진행 해설(`issues_narr`) |
| 요청 줄 수 미달 | `LINE_MISSING` | 오류 | 번호 · 화자만 있던 줄, 만들지 못한 증거조사 대사 |
| 사실로 적힌 요소(개요 · 상세)를 "~라고 주장합니다"로 소개 | `FACT_AS_CLAIM` | 경고 | U3는 요소의 공개 단계로, 나머지는 낱말 겹침으로. 주장(양측 주장 섹션)은 문장 단위로 비교하고, U1은 기사에 "주장"으로 실린 문장을 주장으로 본다 |
| 입력에 없는 피해자 · 유족 감정 어휘 | `EMOTION_NOT_IN_SOURCE` | 경고 | 상처 · 고통 · 슬픔 등, 입력에 같은 낱말이 있으면 통과 |
| 입력에 유리 · 불리 사정이 둘 다 있는데 한쪽만 | `ONE_SIDED` | 경고 | U1. 개요 사정의 방향(AI 판결)과 낱말 겹침으로 |
| 판결 정보 · `LAW`(권고형 숫자) 누출 | `VERDICT_LEAK` | 오류 | 구형 문형, 양형기준 낱말, 권고형 · AI 판결 형량 · 양형기준 섹션의 숫자. 형량 문형("징역 2년")은 값이 권고형 · AI 판결 형량과 같으면 입력에 같은 숫자가 있어도 누출이다(입력에 같은 문형이 있으면 통과 — 전과 사실) |
| 쟁점 ↔ 신문 질문 대응 | `ISSUE_QUESTION_MISMATCH` | 경고 | U2 질문이 설계도(쟁점에 맞춘 질문)와 다르거나 병력(정신과 · 진단 · 치료 · 우울 등)을 물을 때. 뜻의 대응은 관리자가 본다 |
| 증거조사 둘째 줄 군더더기 | `EVIDENCE_FILLER` | 경고 | "살펴 주시기 바랍니다"처럼 당부만 붙인 둘째 줄 |
| 같은 말 되풀이 | `REPEATED_LINE` | 경고 | 같은 단위 안에서 앞 대사와 낱말 80% 이상 겹침 |
| 검사 자체가 돌지 못함 | `REVIEW_FAILED` | 오류 | 값 모양이 어긋나 검사가 멈춘 경우. 생성 결과는 남기고 공개만 막는다(검수 못 한 대본은 내보내지 않는다) |

재생성 범위: 실패한 줄만 고쳐 쓰기 → 그 단위 재생성 → 대본 전체는 다시 만들지 않는다(U2는 줄 단위만).

### 고쳐 쓰기 (`../../script_repair.py`, BE-122 2차)

- 파이프라인이 생성 · 검사 뒤 **오류만** 고친다(`stages.script.repairRetries`, 기본 2, 0이면 고치지 않음). 경고는 문장 비교라 고쳐 쓰게 하면 맞는 대사를 바꿀 수 있어 그대로 둔다.
- 줄 고쳐 쓰기(`fix: line`): 그 줄만 지금 줄 · 앞뒤 줄 · 고칠 점과 함께 보내 다시 쓰게 한다. 블록(페르소나 · ROLE · CASE GUIDE · 가드)과 사건 데이터는 단위를 만들 때와 같다(`unit_prompt`). 고친 줄은 다시 검사해 그 줄의 오류가 없고, 단위의 다른 오류가 늘지 않고, 단위 형식(`check_*`)이 그대로일 때만 받는다. 받으면 그 단위의 경고(`warnings`)도 다시 계산한다. 받지 않은 줄과 이유는 다음 요청에 함께 보낸다. U3는 BE-107 프롬프트에 지난 대사 · 고칠 점을 덧붙여 대사 텍스트로 받는다.
- 단위 재생성: 줄을 `repairRetries`번 고쳐도 남거나 검사가 `fix: unit`을 줬으면 **U1 · U4만** 한 번 다시 만든다. 새 값의 오류가 줄었을 때만 바꾼다. U0(설계도)는 U2 · U4가 기대고, U2는 다시 만들면 U4까지 어긋나므로 다시 만들지 않는다.
- 다 고친 뒤 대본 전체를 다시 검사해 `review`를 바꾼다. 끝내 남은 오류는 그대로 두어 공개를 막는다 — 관리자가 검수 화면에서 고친다(BE-129).
- 한 일은 대본의 `repairs`에 남는다.
  - 줄: `{"unit", "path", "codes", "action": "line", "attempts", "ok", "before", "after", "lastRejected", "affects"}`(U3는 `factorId`). `before` · `after`는 고치기 전 · 받은 줄(관리자가 원문을 본다), `lastRejected`는 받지 않은 마지막 줄(못 고쳤을 때 어디서 막혔는지).
  - 단위: `{"unit", "path": "", "codes", "action": "unit", "attempts", "ok", "errorsBefore", "errorsAfter", "affects"}`.
  - `affects`: 값이 바뀌어 낡았을 수 있는 뒤 단위(BE-129 연동 표). U0 → `["U2", "U4"]`, U2 → `["U4"]`, 나머지 → `[]`. 값이 바뀌지 않았으면 `[]`.

## 검수 결과 형식 (BE-122 · BE-129 계약)

검사 결과 하나는 아래 객체이고, 대본의 `review`는 이 객체의 목록이다(단위 순서 U0 → U4). BE-129 재생성 연결과 관리자 검수 화면이 이 형식을 읽는다.

```json
{"unit": "U2", "path": "/issues_narr/1", "code": "FORMAL_ENDING", "severity": "error", "fix": "line",
 "message": "해요체로 끝내야 합니다: '…'"}
```

| 키 | 값 |
| --- | --- |
| `unit` | 블록 `U0` ~ `U4`. `REVIEW_FAILED`만 `""`(대본 전체) |
| `path` | 블록 값 안의 위치(JSON Pointer). 예: U1 `/lines/4`, U2 `/questions/0/q` · `/cards/issues/ul/1`, U4 `/defense`, U3 `/{순번}`(블록 값이 증거조사 대사 목록). 블록 전체에 걸린 결과는 `""` |
| `code` | 위 표의 검사 종류. 화면 · 집계는 이 값으로 고른다(`message`는 바뀔 수 있다) |
| `severity` | `error`(이대로 내보낼 수 없다) · `warning`(관리자 확인, 공개는 막지 않는다) |
| `fix` | 오류를 고치는 범위: `line`(그 줄만 고쳐 쓴다) · `unit`(그 단위를 다시 만든다). 경고는 `null`. 줄 수 미달은 U1 · U4가 `unit`, U0 · U2 · U3는 `line`(U2는 늘 줄 단위, U3는 요소마다 따로 부른다) |
| `message` | 사람이 읽는 설명(한국어) |
| `factorId` | U3 결과에만. 그 대사가 소개하는 판단 요소 |

## 출력 형식

- `../script_guardrails.md`의 `# 출력 형식`(대사 한 줄 또는 두 줄)은 판단 요소 하나당 대사 하나를 받는 U3 기준이다. BE-107이 그 파일을 그대로 읽으므로 파일은 두고, 조립기는 U0 · U1 · U2 · U4에 GUARDRAILS 규칙만 넣고 `# 출력 형식` 절은 빼고 단위별 출력 지시(U0 설계도 `{plea, issues, questions, tone}`, U1 `{"lines": […]}`, U2 `{indict, plea, issues_narr, questions, cards}`, U4 `{prosecutor, defense}`)를 넣는다(`script_assembler.py`의 `*_request`). JSON 배열 하나만 요청하면 첫 항목만 오는 공급자가 있어 늘 객체로 받는다. 단위별 출력 지시가 우선한다.
- 4 ~ 6막 · 인정신문 · 절차 안내는 생성하지 않는다(고정 · API 치환, `docs/decisions/BE-119.md`).

## 조립 순서와 충돌

BASE PERSONA → ROLE → CASE GUIDE(축 A · B · C · D) → SCENE CONTEXT → GUARDRAILS(공통) → 민감 가드(플래그별)

충돌 시: GUARDRAILS · 민감 가드 > SCENE CONTEXT > ROLE > CASE GUIDE > BASE PERSONA

## CASE GUIDE

| 파일 | 분류 값 | 넣는 단위 | 비고 |
| --- | --- | --- | --- |
| `guide/mood/tragic.md` | 축 A 비극·연민 | U0 · U1 · U2 · U4 | |
| `guide/mood/impulsive.md` | 축 A 격분·충동 | U0 · U1 · U2 · U4 | |
| `guide/mood/cold.md` | 축 A 냉혹·계획 | U0 · U1 · U2 · U4 | |
| `guide/mood/betrayal.md` | 축 A 신뢰 배반 | U0 · U1 · U2 · U4 | |
| `guide/mood/calculated.md` | 축 A 건조·계산 | U0 · U1 · U2 · U4 | |
| `guide/mood/tense.md` | 축 A 위협·긴장 | U0 · U1 · U2 · U4 | |
| `guide/issue/admitted.md` | 축 C ① 인정 | U0 · U2 · U4 | 신문 질문 규칙은 U0(설계도)에도 둔다 — 설계도가 질문을 정하기 때문 |
| `guide/issue/intent_disputed.md` | 축 C ② 고의 다툼 | U0 · U2 · U4 | |
| `guide/issue/denied.md` | 축 C ③ 일부 · 전면 부인 | U0 · U2 · U4 | |
| `guide/issue/mental_state.md` | 축 C ④ 정신상태 | U0 · U2 · U4 | 신문에 정신상태 질문 금지. `sensitive/mental_health`와 함께 켜지는 경우가 많다 |
| `guide/issue/defense_claim.md` | 축 C ⑤ 방어 주장 | U0 · U2 · U4 | |
| `guide/victim/punish.md` | 축 B 엄벌 | U0 · U3 · U4 | |
| `guide/victim/forgive.md` | 축 B 불원 · 합의 · 선처 | U0 · U3 · U4 | |
| `guide/victim/mixed.md` | 축 B 혼재 | U0 · U3 · U4 | |
| `guide/victim/unknown.md` | 축 B 불명 | U0 · U3 · U4 | |
| `guide/extra/third_party.md` | 축 D 공범 · 목격자 · 제3자 있음 | U0 · U2 · U3 | 축 D가 "없음"이면 넣지 않는다 |

U3(증거조사)에는 축 A를 넣지 않는다 — 증거 소개 한 줄은 어조보다 사실 전달이 중요하고, BE-107 프롬프트를 크게 바꾸지 않기 위해서다.

## 민감 가드 (`legal_case.sensitive_flags`)

모든 단위(U0 ~ U4)에 플래그가 켜진 블록을 넣는다.

| 파일 | 플래그 | 사용자 안내 | 안내 문구 조각 |
| --- | --- | --- | --- |
| `sensitive/vulnerable_victim.md` | `VULNERABLE_VICTIM` | 대상 | 어린 · 고령 · 장애 피해자 |
| `sensitive/mental_health.md` | `MENTAL_HEALTH` | 대상 | 정신건강 |
| `sensitive/self_harm.md` | `SELF_HARM` | 대상 | 자해 · 자살 |
| `sensitive/profanity_quoted.md` | `PROFANITY_QUOTED` | 아님 | — |
| `sensitive/nationality_religion.md` | `NATIONALITY_RELIGION` | 아님 | — |
| `sensitive/overseas.md` | `OVERSEAS` | 아님 | — |
| `sensitive/sexual_offense.md` | `SEXUAL_OFFENSE` | 대상 | 성범죄 |
| `sensitive/domestic_violence.md` | `DOMESTIC_VIOLENCE` | 대상 | 가정폭력 · 아동학대 · 스토킹 |
| `sensitive/juvenile_defendant.md` | `JUVENILE_DEFENDANT` | 아님 | — |
| `sensitive/substance.md` | `SUBSTANCE` | 아님 | — |
| `sensitive/hate_crime.md` | `HATE_CRIME` | 대상 | 혐오 · 차별 |
| `sensitive/capital_penalty.md` | `CAPITAL_PENALTY` | 아님 | — |

안내 문구(지문 · 알아두기 공통): `이 사건에는 {조각1}, {조각2}과(와) 관련된 내용이 나와요.` — 안내 대상 플래그의 조각을 쉼표로 잇고, 마지막 조각의 받침으로 "과 · 와"를 고른다(`script_assembler.sensitive_notice`).

## 공통 GUARDRAILS

플래그 없이 모든 사건에 적용하는 가드는 `../script_guardrails.md`에 있다(BE-119에서 주장 · 사실 구분, 의사 대변 금지, 피해자 탓하기 경계, 낙인 금지, 법률 자문 표현 금지, 없는 고유명 생성 금지를 더했다).
