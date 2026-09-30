**작성 이력**

| 버전 | 날짜 | 내용 |
| --- | --- | --- |
| v0.1 | 2026-09-28 | 초안<br>• 사건 등록 · AI 판결 사전 생성, 체험 시작, 사전 판단, 사건 정보 확인, 판결 확정, 결과 공개 · 비교 6개 흐름과 (확장) 비교 분석 생성 시점 2안, API 후보 목록 |
| **v0.2** | **2026-09-28** | 비교 분석 생성 시점 확정<br>• 안 B(실시간 생성) 채택<br>• 8장을 안 B 흐름(S-08 진입 시 비동기 생성, S-09는 규칙 문장 먼저 표시)과 안전장치 6개로 교체<br>• API 후보 15번(비교 분석 조회) 추가 |
| **v0.4** | **2026-09-29** | **문서 정합성 점검 결정 반영 (COMMON-4)**<br>• 체험 ID를 주고받는 표현을 "사건 ID + 익명 ID 쿠키"로 정정(API 1-3)<br>• 화면 진입 시 상태를 먼저 조회하지 않고 각 API의 `INVALID_STATE`로 이동(API 6장 #1 확정, 3장 비고)<br>• 무죄 선택지 MVP 제외(6장)<br>• AI 판결 생성은 RAG 우선, 안 되면 퓨샷(1 · 2장)<br>• S-09 "내 판결" 한 줄 요약: MVP는 요약 태그 규칙 문장, 확장은 비교 분석의 AI 요약으로 교체(7 · 8장)<br>• (낮음 항목) 안전장치 #3의 톤 규칙 참조를 요구사항 11장 · FR-6-3으로 정정 |
| v0.3 | 2026-09-28 | API 명세서 v0.2와 맞춤<br>• 체험 시작 후 REVIEWED는 S-05로, 섹션 확인 분기(이미 확인한 섹션은 현재 상태 반환 · 건너뛰면 거절 · 확정 후는 거절), 사전 판단 시 last_reviewed_step = 1<br>• S-05도 사건 정보 조회<br>• 7장을 공개(POST) · 조회(GET) 두 단계로 다시 그림<br>• 집행유예 검사 조건 보완<br>• 사건 등록 대상 테이블 보완<br>• 후보 8 · 9 허용 상태 정정 |
| v0.5 | 2026-09-30 | 무기징역 · 사형 형벌 추가 (BE-2) — 사전 판단 형량 구간 표기(7개, 살인 9개) |

---

## 1. 등장 요소

| 이름 | 설명 |
| --- | --- |
| 사용자 | 체험하는 사람 (로그인 없음, MVP) |
| 화면 | 프론트엔드 (S-01 ~ S-09) |
| 서버 | Spring Boot API 서버. 모든 순서·수정 불가·범위 규칙은 여기서 검사한다 |
| DB | ERD의 테이블 |
| 팀 (운영) | 사건 데이터와 AI 판결을 준비하는 우리 팀. MVP에서는 별도 관리자 화면 없이 스크립트 · SQL로 넣는다 |
| AI | LLM (요구사항 FR-4-2 · 4-3). 유사 판례 · 양형기준은 **RAG로 검색해 넣고, RAG를 적용할 수 없으면 자료를 프롬프트에 직접 넣는 퓨샷 방식**을 쓴다. AI 판결은 사용자 요청 중에 호출하지 않는다. (확장) 비교 분석만 체험별로 호출한다(8장) |

**AI 판결 생성 시점은 이미 확정돼 있다.** 요구사항 FR-4-6에 따라 AI 판결은 사건 등록 단계에서 미리 만들고 팀이 검수한 뒤 공개한다. 모든 사용자가 같은 AI 판결을 보며, 사용자 요청 중에 AI를 부르지 않는다. 예외는 **세 판결 비교 분석(FR-6-3, 확장)** 으로, 체험별로 실시간 생성한다(8장, v0.2 확정).

---

## 2. 사건 등록 · AI 판결 사전 생성 (운영, 체험 전)

```mermaid
sequenceDiagram
    actor T as 팀 (운영)
    participant D as DB
    participant A as AI
 
    T->>D: 사건 등록 (legal_case, case_section, penalty_rule, factor, case_source, 양형기준 버전 · 형량 구간 선택지)
    T->>D: 실제 판결 등록 (judgment: COURT, FINAL + judgment_factor)
    T->>A: AI 판결 생성 요청 (사건 정보 + 고정 입력값 + 판단 요소 목록 + RAG로 찾은 유사 판례 · 양형기준, 안 되면 퓨샷 자료)
    A-->>T: 형벌 · 형량 · 판단 요소(↑/↓) · 판결 이유
    T->>T: 검수 (선고 가능 범위 안인가, 목록 밖 요소 · 없는 근거가 없는가)
    alt 검수 통과
        T->>D: AI 판결 저장 (judgment: AI, FINAL, is_published = true) + ai_generation 기록
    else 검수 실패
        T->>A: 프롬프트 · 자료 보완 후 다시 생성
    end
```

- AI에는 실제 판결 결과를 주지 않는다. 유사 판례 자료에서도 대상 사건은 뺀다(FR-4-2).
- 공개 AI 판결은 사건당 1개(`is_published = true`). 다시 만들면 새 행을 만들고 공개 대상을 교체한다(FR-4-6, ERD `ai_generation`).

---

## 3. 체험 시작 (S-02 → S-03)

```mermaid
sequenceDiagram
    actor U as 사용자
    participant F as 화면
    participant S as 서버
    participant D as DB
 
    U->>F: 사건 카드에서 체험 시작
    F->>S: 체험 시작 (사건 ID, 익명 ID 쿠키가 있으면 함께)
    alt 익명 ID가 없거나 DB에 없음
        S->>D: anonymous_user 생성
        S-->>F: 새 익명 ID를 쿠키로 내려줌
    end
    S->>D: 체험 조회 (익명 ID + 사건 ID + attempt_no 1)
    alt 체험이 없음
        S->>D: experience 생성 (STARTED, attempt_no 1)
    end
    S-->>F: 현재 상태 + last_reviewed_step (체험 ID는 내려주지 않음)
    alt STARTED
        F-->>U: S-03 사건 개요 · 사전 판단
    else PRE_JUDGED 또는 REVIEWING
        F-->>U: S-04 (확인한 섹션까지 펼친 상태)
    else REVIEWED
        F-->>U: S-05 판결 전 최종 정리
    else VERDICT_CONFIRMED 또는 AI_REVEALED
        F-->>U: 공개가 진행된 마지막 결과 화면
    else COMPLETED
        F-->>U: S-09 결과 다시 보기
    end
```

- 사건 ID가 없거나 공개되지 않은 사건이면 404 → S-14.
- 같은 사건을 두 번 눌러도 체험은 하나만 생긴다. DB 유니크 제약(`anonymous_user_id`, `case_id`, `attempt_no`)으로 동시 요청까지 막는다.
- 이 응답의 "현재 상태 → 화면" 규칙은 **모든 화면 진입 시 공통**이다. 화면은 진입할 때 상태를 따로 조회하지 않고 그 화면의 API를 바로 부른다. 허용되지 않은 화면이면 API가 `409 INVALID_STATE` + `currentStatus`를 돌려주고, 화면은 이 규칙대로 보낸다(API 1-6 · 6장 #1 확정, IA 5장).
- 이후 요청은 모두 **사건 ID + 익명 ID 쿠키**로 체험을 찾는다. 화면은 체험 ID를 들고 다니지 않는다(API 1-3).

---

## 4. 사전 판단 (S-03)

```mermaid
sequenceDiagram
    actor U as 사용자
    participant F as 화면
    participant S as 서버
    participant D as DB
 
    F->>S: 사건 개요 · 사전 판단 선택지 조회 (API 4)
    S-->>F: 개요(legal_case.overview) · 형량 구간(7개, 살인 9개) · OVERVIEW 요소(확장)
    U->>F: 형량 구간 선택 (+ 작용 요소 1~2개, 확장) → 제출
    F->>S: 사전 판단 제출 (사건 ID + 쿠키, 구간 ID, 요소 ID 목록)
    S->>D: 체험 상태 조회
    alt 상태가 STARTED가 아님
        S-->>F: 거절 (이미 제출함) → 현재 상태의 화면으로 이동
    else 요소가 OVERVIEW가 아니거나 3개 이상
        S-->>F: 거절 (잘못된 요청)
    else 정상
        S->>D: judgment 저장 (USER, PRE) + judgment_factor + 상태를 PRE_JUDGED로 + last_reviewed_step = 1 (한 트랜잭션)
        S-->>F: 성공
        F-->>U: S-04 사건 정보 확인
    end
```

- 상태 변경은 조건부로 한다: "상태가 `STARTED`일 때만 `PRE_JUDGED`로 바꾼다". 두 번 눌러 요청이 동시에 와도 하나만 성공한다.
- S-03에서 받는 데이터에는 법정형 · 권고 범위 · 실제 판결이 없다.

---

## 5. 사건 정보 확인 (S-04)

```mermaid
sequenceDiagram
    actor U as 사용자
    participant F as 화면
    participant S as 서버
    participant D as DB
 
    F->>S: 사건 정보 조회 (사건 ID + 쿠키)
    S->>D: 체험 상태 + last_reviewed_step 조회
    S-->>F: 섹션 본문 (확인한 다음 섹션까지만) + (④가 열렸을 때만) 법률 · 선고 가능 범위 · 권고 범위
    U->>F: 섹션 확인 (읽었습니다 · 다음 단계 열기)
    F->>S: 섹션 확인 기록 (사건 ID + 쿠키, 섹션 번호)
    alt 상태가 STARTED이거나 VERDICT_CONFIRMED 이상
        S-->>F: 거절 (INVALID_STATE) → 현재 상태의 화면으로
    else 섹션 번호 ≤ last_reviewed_step (이미 확인함, REVIEWED 포함)
        S-->>F: 현재 상태 그대로 반환 (두 번 누름 대비)
    else 섹션 번호 > last_reviewed_step + 1
        S-->>F: 거절 (STEP_OUT_OF_ORDER, 순서 건너뜀)
    else 마지막 섹션(④)
        S->>D: last_reviewed_step = 4, 상태를 REVIEWED로
        S-->>F: 성공 → 판결 전 최종 정리로 버튼 표시
    else 그 밖의 섹션
        S->>D: last_reviewed_step + 1, 상태를 REVIEWING으로
        S-->>F: 성공 → 다음 섹션 열림
    end
```

- 새로고침하면 첫 조회 결과의 `last_reviewed_step`으로 펼침 상태를 복원한다(FR-2-11).
- 섹션 ①(개요)은 S-03에서 이미 봤으므로 확인 완료로 시작한다. 번호는 ① = 1 ~ ④ = 4이고, 사전 판단을 제출하면 `last_reviewed_step`이 1이 된다(API 5).
- 잠긴 섹션 본문은 응답에 넣지 않는다. 화면에서만 숨기면 개발자 도구로 볼 수 있다.
- S-05 판결 전 최종 정리도 사건 정보 조회(API 6)를 부른다. `REVIEWED`이면 S-05용 핵심 사실 요약(`SUMMARY`)이 함께 내려온다. 새로고침해도 같은 요청으로 다시 그린다.

---

## 6. 판결 확정 (S-06 → S-07)

```mermaid
sequenceDiagram
    actor U as 사용자
    participant F as 화면
    participant S as 서버
    participant D as DB
 
    F->>S: 판결 입력 정보 조회 (허용 형벌, 법정형 · 선고 가능 범위, 권고 범위, 판단 요소 전체)
    S-->>F: 입력 정보
    U->>F: 형벌 · 형량 · 집행유예 · 판단 요소(↑/↓) 입력
    F->>F: 선고 가능 범위 밖이면 확정 버튼 비활성 (S-06c)
    U->>F: 판결 확정하기 → 확인 박스 → 확정하기
    F->>S: 판결 제출 (사건 ID + 쿠키, 형벌, 형량, 집행유예, 요소 · 방향 목록)
    S->>D: 체험 상태 · penalty_rule 조회
    alt 상태가 REVIEWED가 아님
        S-->>F: 거절 (이미 확정했거나 순서가 맞지 않음) → 현재 상태 화면으로
    else 이 사건에서 허용되지 않는 형벌
        S-->>F: 거절 (잘못된 형벌)
    else 선고 가능 범위 밖 (allowed_min ~ allowed_max)
        S-->>F: 거절 (범위 이탈)
    else 집행유예 조건 위반 (허용하지 않는 형벌 · 형량이거나 기간이 1~5년 밖)
        S-->>F: 거절 (잘못된 요청)
    else 사건 판단 요소 목록에 없는 요소
        S-->>F: 거절 (잘못된 요소)
    else 정상
        S->>D: judgment 저장 (USER, FINAL) + judgment_factor + 상태를 VERDICT_CONFIRMED로 (한 트랜잭션)
        S-->>F: 성공
        F->>S: AI 판결 조회 (사건 ID + 쿠키)
        S->>D: 상태 확인 + 공개 AI 판결 조회 (AI, is_published)
        S-->>F: AI 판결 + 내 판결과의 차이
        F-->>U: S-07 AI 판결
    end
```

- **AI는 여기서 호출하지 않는다.** 2장에서 미리 만든 판결을 DB에서 읽기만 한다. 응답이 빠르고 모든 사용자에게 같다.
- 화면의 버튼 비활성은 편의 기능이고, 진짜 검사는 서버에서 한다(FR-3-3).
- 무죄는 MVP 선택지에서 뺐다(요구사항 15장, v0.4). 무죄가 오면 `penalty_rule`에 없는 형벌이라 "허용되지 않는 형벌"로 거절된다.
- "내 판결과의 차이" 문구 생성 로직은 미정(요구사항 15장). MVP는 형량 차이만 계산해 돌려준다.

---

## 7. 결과 공개 · 비교 (S-07 → S-08 → S-09)

상태를 바꾸는 **공개**(POST)와 결과를 읽는 **조회**(GET)를 나눈다(API 11 ~ 14). 결과 화면을 새로고침하면 조회만 다시 부른다.

```mermaid
sequenceDiagram
    actor U as 사용자
    participant F as 화면
    participant S as 서버
    participant D as DB
 
    U->>F: 실제 판결 확인하기 (S-07)
    F->>S: 실제 판결 공개 (API 11, POST)
    alt 상태가 VERDICT_CONFIRMED 전
        S-->>F: 거절 (INVALID_STATE)
    else VERDICT_CONFIRMED
        S->>D: 상태를 AI_REVEALED로
        S-->>F: 성공
    else AI_REVEALED 이상 (이미 공개)
        S-->>F: 성공 (상태 그대로)
    end
    F->>S: 실제 판결 조회 (API 12, GET)
    S->>D: 실제 판결 조회 (COURT, is_published)
    S-->>F: 실제 판결 + 판결문 발췌
    F-->>U: S-08 실제 판결
    U->>F: 세 판결 비교 보기 (S-08)
    F->>S: 비교 공개 (API 13, POST)
    alt 상태가 AI_REVEALED 전
        S-->>F: 거절 (INVALID_STATE)
    else AI_REVEALED
        S->>D: 상태를 COMPLETED로
        S-->>F: 성공
    else COMPLETED (이미 공개)
        S-->>F: 성공 (상태 그대로)
    end
    F->>S: 비교 결과 조회 (API 14, GET)
    S->>D: 사전 판단 · 내 판결 · AI · 실제 판결과 요소 기록 조회
    S->>S: 요소별 분류 태그 · 변화 유형 계산 + 규칙 기반 공통점 · 차이점 문장 + 내 판결 한 줄 요약(요약 태그 규칙 문장)
    S-->>F: 처음 판단 → 직접 판결 + 3열 카드 + 매트릭스 + 문장
    F-->>U: S-09 세 판결 비교
```

- 공개 요청은 **다시 보내도 결과가 같아야 한다**(이미 공개된 상태면 상태는 그대로 두고 결과만 돌려준다). 결과 화면끼리 오가거나 새로고침해도 거절되지 않는다.
- 사전 판단은 이 응답에서 처음 내려간다. 그 전의 어떤 응답에도 넣지 않는다(IA 결정 2).
- MVP의 공통점 · 차이점은 매트릭스를 바탕으로 한 규칙 문장이라 AI를 부르지 않는다(IA S-09).
- 3열 판결 카드의 한 줄 요약: AI · 재판부는 팀이 등록한 `judgment.summary`를 읽고, 내 판결은 사용자가 고른 요소의 요약 태그(`factor.summary_tag`)를 방향별로 모아 서버가 규칙 문장으로 만든다(ERD 6장, API 판결 응답 공통 형식). MVP에서는 AI를 부르지 않는다.

---

## 8. (확장) 세 판결 비교 분석 — 실시간 생성 (안 B 확정)

비교 분석(FR-6-3)은 **사용자 판결이 사람마다 달라서 사건 등록 때 전부 미리 만들 수 없다.** 두 안(A: AI ↔ 실제 분석만 미리 만들고 내 판결 부분은 규칙 문장 / B: 체험별 실시간 생성)을 비교한 뒤, 팀 논의로 **안 B를 채택했다**(v0.2).

안 B는 검수 없이 문장이 나가므로 FR-4-6(검수한 결과만 공개)의 예외가 된다. 그래서 아래 **안전장치를 함께 둔다.**

### 흐름

```mermaid
sequenceDiagram
  actor U as 사용자
  participant F as 화면
  participant S as 서버
  participant D as DB
  participant A as AI

  U->>F: 실제 판결 확인하기 (S-07)
  F->>S: 실제 판결 공개 (API 11)
  S->>D: 상태를 AI_REVEALED로
  S->>D: 비교 분석 행 생성 (PENDING, 체험당 1개)
  S-->>F: 성공 (실제 판결은 API 12로 조회)
  S->>A: 비교 분석 생성 시작 (비동기, 구조화된 판결 기록만 전달)
  F-->>U: S-08 실제 판결 (읽는 동안 생성 진행)
  A-->>S: 분석 JSON
  S->>S: 검증 (요소 ID가 목록 안인가, 금지 표현이 없는가, 형식이 맞는가)
  alt 검증 통과
    S->>D: 분석 저장 (DONE)
  else 검증 실패 · 오류 · 시간 초과
    S->>D: FAILED 기록 (규칙 문장으로 대체)
  end
  U->>F: 세 판결 비교 보기 (S-08)
  F->>S: 비교 공개 (API 13)
  S-->>F: 성공 (COMPLETED)
  F->>S: 비교 결과 조회 (API 14)
  S-->>F: 3열 카드 + 매트릭스 + 규칙 문장 (즉시)
  F-->>U: S-09 표시
  F->>S: 비교 분석 조회 (API 15)
  alt DONE
    S-->>F: AI 분석
    F-->>U: 공통점 · 차이점 영역과 내 판결 카드 한 줄 요약을 AI 분석으로 교체
  else PENDING
    S-->>F: 생성 중
    F-->>U: 생성 중 표시 후 잠시 뒤 다시 조회
  else FAILED
    S-->>F: 분석 없음
    F-->>U: 규칙 문장 그대로 유지
  end
```

- **생성 시작은 S-08 진입 시점**(`AI_REVEALED`)이다. 사용자가 실제 판결을 읽는 동안 만들어 두므로, S-09를 열 때 기다리는 시간이 거의 없다.
- **S-09는 AI를 기다리지 않는다.** 매트릭스와 MVP의 규칙 문장을 먼저 보여 주고, 분석이 준비되면 공통점 · 차이점 영역만 바꾼다. AI가 실패해도 화면은 MVP와 같게 동작한다.
- 같은 체험의 분석은 **한 번만 만든다.** 결과 화면을 다시 열거나 새로고침해도 저장된 분석을 보여 준다(재생성 없음).
- **AI "내 판결" 요약(확장, REQ-062)**: 분석 결과의 `perspectives.USER`가 사용자가 입력한 형벌 · 형량 · 판단 요소 · 방향을 바탕으로 만든 내 판결 요약이다. 같은 생성 호출 · 같은 안전장치를 쓰고, `DONE`이면 S-09 내 판결 카드의 한 줄 요약(MVP 규칙 문장)을 이 문장으로 바꾼다. 실패하거나 생성 중이면 규칙 문장을 그대로 둔다.

### 안전장치

| # | 장치 | 내용 |
| --- | --- | --- |
| 1 | 입력 제한 | 세 판결의 형벌 · 형량 · 판단 요소 기록(요소 ID · 문구 · 방향), 사전 판단 구간, 요소의 공개 단계만 넣는다. 사건 원문과 실제 판결문 전문은 넣지 않는다. (확장) 사용자의 추가 판단 이유(자유 입력)는 넣지 않거나, 넣을 때는 지시문으로 해석되지 않도록 분리한다 |
| 2 | 출력 형식 고정 | 항목마다 근거가 된 요소 ID를 붙인 JSON으로 받는다. 서버가 사건 판단 요소 목록에 없는 ID를 거절한다 → 없는 사실 · 근거를 만들기 어렵게 한다 |
| 3 | 표현 검사 | "정답", "옳다 · 틀렸다", "이중 잣대", 점수 · 순위 표현이 있으면 실패로 처리한다(요구사항 11장 톤 원칙 · FR-6-3) |
| 4 | 대체 문장 | 검증 실패 · AI 오류 · 시간 초과(기준은 기술 설계에서 정함) 시 MVP의 규칙 문장을 그대로 쓴다 |
| 5 | 한 번만 생성 | 체험당 분석 1개(유니크 제약). 동시 요청이 와도 하나만 생성한다 |
| 6 | 기록 · 사후 점검 | 입력 · 원본 출력 · 모델 · 프롬프트 버전을 저장하고, 팀이 주기적으로 샘플을 점검한다. 문제가 반복되면 프롬프트를 고치고 버전을 올린다 |