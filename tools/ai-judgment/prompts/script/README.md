# 대본 생성 프롬프트 블록 (BE-119)

> 블록 본문은 모델이 그대로 읽으므로(`common.read_prompt`) 메타 정보(적용 조건 · 넣는 단위 · 충돌 규칙)는 블록 파일에 두지 않고 이 색인에 둔다. 조립기를 만들 때 이 표가 매핑 상수가 된다(후속). 근거는 `docs/decisions/BE-119.md`.
> 원칙: 블록에는 사실(숫자 · 금액 · 날짜 · 사건 내용)을 쓰지 않는다. 사실은 `scene_data`로만 들어간다.
> 단위: U0 설계도 · U1 1막 해설 · U2 재판 도입부(모두진술 · 쟁점 정리 · 피고인신문) · U3 증거조사 · U4 3막(검사 의견 · 최종변론 · 최후진술)

## 블록 형식

- 블록은 `## 공통`과 단위별 절(`## U0` ~ `## U4`)로 나눌 수 있다. 조립기는 **공통 절 + 지금 만드는 단위의 절만** 넣고, `##` 제목 줄은 넣지 않는다. 절이 없는 블록(분위기 · 민감 가드)은 전체가 공통이다.
- 이유: 블록 하나에 여러 단위용 지시가 섞여 있으면 모델이 조립된 단위와 상관없이 따라 한다(시험에서 쟁점 정리 지시가 3막 검사 의견으로 샜다, `docs/decisions/BE-119.md`).
- 1막 해설(U1) · 6막처럼 법정 밖 안내는 `../script_role_guide.md`(안내 배역)를 ROLE로 쓴다.

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
| `guide/issue/admitted.md` | 축 C ① 인정 | U0 · U2 · U4 | 신문 마지막 질문 고정 |
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

안내 문구(지문 · 알아두기 공통): `이 사건에는 {조각1}, {조각2}과(와) 관련된 내용이 나와요.` — 안내 대상 플래그의 조각을 쉼표로 잇는다. (조사 처리 · 문장 다듬기는 코드로 옮길 때 확정)

## 공통 GUARDRAILS

플래그 없이 모든 사건에 적용하는 가드는 `../script_guardrails.md`에 있다(BE-119에서 주장 · 사실 구분, 의사 대변 금지, 피해자 탓하기 경계, 낙인 금지, 법률 자문 표현 금지, 없는 고유명 생성 금지를 더했다).
