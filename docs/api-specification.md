**작성 이력**

| 버전 | 날짜 | 내용 |
| --- | --- | --- |
| v0.1 | 2026-09-28 | 초안 — 공통 규칙(경로 · 익명 ID 쿠키 · 에러 형식 · 에러 코드), MVP API 14개와 확장 API 1개의 요청 · 응답 · 거절 조건, 상태별 호출 가능 API 표 |
| v0.2 | 2026-09-28 | 전체 문서 교차 검토 반영 — API 6 · 7 허용 상태 정정, 예시 요소 ID를 ERD 예시(1 ~ 7)와 통일, 섹션 ① 데이터 출처 명시, 벌금 선고 가능 하한 25,000원(형법 제45조 단서), 무죄 선택지 · `references` 출처 · `perspectives` 근거 요소 · `changeType`(`KEPT` 추가) · 형벌 무게 순서 보완, 사기 양형기준 유형 표기 정정(제1유형), 시퀀스 후보 대응표 |
| **v0.3** | **2026-09-29** | **문서 정합성 점검 결정 반영 (COMMON-4)** — 6장 #1 확정(화면 진입 시 상태 조회 없이 각 API의 `INVALID_STATE`로 이동, 1-6 · 5장), 무죄 선택지 MVP 제외(API 8 · 9 · 14, 6장 #5), 예시 사건을 단일 범행 사건(지인 투자금 편취)으로 교체(경합범 서비스 제외), 판결 카드 한 줄 요약 `summary` 추가(내 판결은 요약 태그 규칙 문장, 확장에서 AI 요약으로 교체), 사건 목록 `crimeCategoryLabel` · `thumbnailUrl`, (확장) 실제 판결 `deidentifiedItems`, 참고 자료 태그 출처 컬럼명 `reference_tags`, (확장) 쿠키 삭제 안내(REQ-108). (낮음 항목) 같은 도메인 배포 확정(1-2 · 6장 #2), 시퀀스 후보 대응표 삭제, 톤 규칙 참조를 요구사항 11장으로 정정 |
| **v0.4** | **2026-09-30** | **대표 사건(살인) 가공 결정 반영 (BE-13)** — 형벌 종류에 사형(`DEATH`) · 무기징역(`LIFE`) 추가(API 6 · 8 · 9, 판결 응답 공통 형식), 판결 제출에 `reducedTo`(감경 후 형벌) 추가, `diffFromMine` 비교 기준을 최종 선고 형벌로 명시, 부가 처분 `CONFISCATION`(몰수) 추가 (ERD v1.4), API 14 형벌 무게 순서에 `LIFE` < `DEATH` 추가, `reducedTo`는 형벌 종류가 바뀌는 감경만 기록한다고 명시, 최종 선고 형벌이 `DEATH` · `LIFE`일 때 형량 값이 있으면 `VALIDATION_ERROR`로 명시 |
| v0.5 | 2026-09-30 | 예시 사건을 가상 살인 사건으로 교체 (COMMON-11) — API 1 · 4 · 6 · 8 · 9 · 10 · 12 · 14 예시를 "빌린 돈 문제로 찾아온 지인을 살해한 사건"(가상)으로 교체, 사전 판단 구간 예시를 살인용 8개(벌금형 제외 · 무기 · 사형 추가)로, 형벌 선택지 예시를 사형 · 무기 · 징역 3종으로 교체 (ERD v1.5) |
| v0.6 | 2026-09-30 | BE-16 시드 반영 — API 4 · 5 · 14 예시의 사전 판단 구간 `rangeOptionId`를 DB 실제 값(살인 8 ~ 15, 예시 선택 11)으로 정정 (ERD v1.6) |
| v0.7 | 2026-09-30 | API 4 · 5 구현 반영 (BE-7) — API 4 형량 구간 개수를 범죄 유형별로 명시(살인 8개, 사기 · 상해 7개), 두 API의 404 · 400 거절 조건 추가, API 5 검사 순서 · 거절 시 저장 없음 · `factorIds` 규칙 순서 명시 |
| v0.8 | 2026-10-01 | BE-8 리뷰 반영 — API 6의 선고 가능 범위는 하한·상한이 모두 있는 규칙만 포함, 범위 표시 문구를 API 8 형식으로 통일, LAW_TERM은 law.terms로만 전달하도록 명시 |
| v0.9 | 2026-10-01 | 후속 정리 (COMMON-14) — 1-1에 숫자 입력 규칙 추가(정수 필드의 소수는 `VALIDATION_ERROR`, 문자열 숫자는 허용, BE-22), API 9에 열거값에 없는 `penaltyType`도 `INVALID_PENALTY_TYPE`으로 거절한다고 명시(1-5 열거값 규칙보다 우선, BE-9) |
| v0.10 | 2026-10-02 | API 10 ~ 13 구현 · 리뷰 반영 (BE-10) — `diffFromMine`은 최종 선고 형벌에 해당하는 값만 내려가고 **집행유예 여부가 다르면 다른 형벌로 본다**고 명시하고 유예 기간 차이 `suspensionMonthsDiff` 추가(API 10), 내 판결 한 줄 요약에서 같은 태그가 ↑ · ↓ 양쪽에 있으면 먼저 고른 방향에만 남기고 조사는 끝의 한글 · 숫자 기준으로 `을` · `를`을 고른다고 명시(판결 응답 공통 형식), 공개 요청(API 11 · 13)은 상태를 옮기기 전에 검수한 AI · 재판부 판결이 등록돼 있는지 먼저 확인한다고 명시, 공개 사건에 그 판결이 없으면 `500 INTERNAL_ERROR`로 둔다고 명시(API 10 · 12) |
| v0.11 | 2026-10-02 | 사건 정보 섹션 표시 방식 팀 결정 반영 (BE-17) — API 6 `content`에는 항목마다 줄바꿈(`\n`)이 들어갈 수 있고 화면은 그대로 보여 준다고 명시, 양측 주장 섹션 위 안내 문구는 응답에 없고 화면 고정 문구라고 명시 (ERD v1.9 · 정보 구조 v1.10) |
| v0.12 | 2026-10-06 | 기획 단계 리뷰 준비 회의 결정 반영 (COMMON-16) — 내 판결 한 줄 요약 문장 틀을 확정(6장 #7, 판결 응답 공통 형식) |
| v0.13 | 2026-10-06 | 판단 요소별 요약어 기준 확정 반영 (BE-26, 구현 BE-11) — `preToFinal.direction` 세부 규칙 확정(6장 #3), `ruleSentences` 문장 틀을 요소별 요약어 기준으로 확정(6장 #4), 내 판결 한 줄 요약 방향별 태그 수 제한 없음으로 정정(6장 #7), API 14 예시 응답의 `judgments.USER.summary` · `matrix`(요소 7 ALL_SAME 추가) · `ruleSentences`를 요약어 기준 · 서로 일치하도록 갱신, 판결 응답 공통 형식의 USER(MVP) 예시 문구도 요약어 기준으로 정정(PR #67 리뷰) |
| v0.14 | 2026-10-08 | 관리자 인증 API 추가 (**확장 단계**, BE-39) — 7장 관리자 API(CSRF 토큰 · 로그인 · 로그아웃 · 내 정보), 1-5에 `UNAUTHORIZED` · `INVALID_CREDENTIALS` · `FORBIDDEN` 추가. 사용자 API(1 ~ 15)는 변경 없음 |

---

## 1. 공통 규칙

### 1-1. 기본

| 항목 | 규칙 |
| --- | --- |
| 기본 경로 | `/api/v1` |
| 형식 | JSON (`Content-Type: application/json`), 문자 UTF-8 |
| 필드 이름 | camelCase |
| 날짜 · 시각 | ISO 8601 (`2026-09-28T15:30:00+09:00`) |
| 형량 단위 | 징역 · 집행유예는 **개월**(int), 벌금은 **원**(long). 화면에서 "2년 6개월"로 바꿔 보여 준다 |
| 숫자 입력 | (v0.9) 정수 필드(형량 개월 · 금액 · `step` · ID 등)에 소수(`36.7`, `36.0`)가 오면 400 `VALIDATION_ERROR`로 거절한다. 소수점을 버려 다른 값으로 저장하지 않기 위해서다. `"36"`처럼 숫자로 읽을 수 있는 문자열은 숫자로 받고, `"삼십육"`처럼 읽을 수 없는 문자열은 400이다. 본문 형식 오류라 사건 · 체험 조회보다 먼저 거절한다 (BE-22) |
| 열거값 | ERD 값을 그대로 쓴다 (`PRISON`, `UP`, `COMPLETED` 등) |
| 인증 | MVP는 로그인 없음. **익명 ID 쿠키**로 사용자를 구분한다 (1-2) |

### 1-2. 익명 ID 쿠키

| 항목 | 값 (제안) |
| --- | --- |
| 이름 | `NLNB_AID` |
| 값 | `anonymous_user.id` (UUID) |
| 발급 시점 | 체험 시작(`POST /cases/{caseId}/experience`) 때 쿠키가 없거나 DB에 없는 값이면 새로 발급 |
| 속성 | `HttpOnly`, `Secure`, `SameSite=Lax`, `Path=/` |
| 유효 기간 | 1년 (요구사항 15장 "익명 ID 유지 방식 · 기간" 결정 시 확정) |
- **프론트와 API는 같은 도메인으로 배포한다**(6장 #2 확정, 기술 스택 8장). EC2의 Nginx가 프론트 빌드 파일을 서빙하고 `/api` 요청을 백엔드 컨테이너로 프록시하므로 `SameSite=Lax` 그대로 쓰고 CORS 설정은 필요 없다.
- 요청마다 `anonymous_user.last_seen_at`을 갱신한다.
- (확장, REQ-108) 쿠키를 지우거나 브라우저를 바꾸면 진행 상태를 이어갈 수 없다는 안내를 둔다. 표시 화면과 문구는 미정이다(요구사항 15장). 서버 API 변경은 없다.

### 1-3. "내 체험" 경로

화면 경로가 `/cases/{caseId}/...`(IA 4장)이므로, API도 **사건 ID + 쿠키**로 체험을 찾는다. 체험 ID를 화면이 따로 들고 다닐 필요가 없다.

```
/api/v1/cases/{caseId}/experience/...
```

- 서버는 (쿠키의 익명 ID, `caseId`, 가장 최근 `attempt_no`)로 체험을 찾는다. MVP에서는 `attempt_no`가 항상 1이다.
- 체험이 없거나 쿠키가 없으면 `404 EXPERIENCE_NOT_FOUND`. 다른 사람의 체험은 이 경로로 접근할 수 없다.

### 1-4. 에러 응답

성공 응답은 결과 객체를 그대로 돌려준다(감싸는 객체 없음). 에러는 아래 형식으로 통일한다.

json

```json
{  "code": "INVALID_STATE",  "message": "지금 단계에서는 할 수 없는 요청입니다.",  "currentStatus": "PRE_JUDGED",  "details": null}
```

| 필드 | 설명 |
| --- | --- |
| `code` | 에러 코드 (1-5). 화면 분기는 이 값으로 한다 |
| `message` | 사람이 읽는 설명. 화면에 그대로 띄우지 않는다 |
| `currentStatus` | 체험 관련 에러일 때 현재 체험 상태. 화면은 이 값으로 **이동할 화면**을 정한다 (1-6) |
| `details` | 입력 검증 에러일 때 필드별 사유 배열 `[{ "field": "prisonMonths", "reason": "OUT_OF_ALLOWED_RANGE" }]` |

### 1-5. 에러 코드

| HTTP | code | 언제 | 화면 대응 |
| --- | --- | --- | --- |
| 400 | `VALIDATION_ERROR` | 필수값 누락, 형식 오류 (음수, 잘못된 열거값 등) | 개발 오류. 일반 오류 안내 |
| 404 | `CASE_NOT_FOUND` | 사건이 없거나 `PUBLISHED`가 아님 | S-14 |
| 404 | `EXPERIENCE_NOT_FOUND` | 이 사건의 내 체험이 없음 (쿠키 없음 포함) | S-02로 이동 |
| 409 | `INVALID_STATE` | 지금 상태에서 할 수 없는 요청 (이미 제출 · 확정, 순서가 맞지 않음, 아직 공개 전) | `currentStatus`의 화면으로 이동 |
| 409 | `STEP_OUT_OF_ORDER` | 섹션 확인 순서를 건너뜀 | 현재 섹션 다시 불러오기 |
| 422 | `INVALID_RANGE_OPTION` | 사전 판단 구간이 이 사건 범죄 유형의 선택지가 아님 | 개발 오류 |
| 422 | `INVALID_FACTOR` | 이 사건의 판단 요소가 아님, 사전 판단에 `OVERVIEW`가 아닌 요소, 중복 요소, 방향 누락 | 개발 오류 |
| 422 | `TOO_MANY_FACTORS` | 사전 판단 작용 요소가 2개를 넘음 (확장) | 개발 오류 |
| 422 | `INVALID_PENALTY_TYPE` | 이 사건에서 허용되지 않은 형벌, 허용되지 않은 감경 조합(`reducedTo`) | 개발 오류 |
| 422 | `OUT_OF_ALLOWED_RANGE` | 형량이 선고할 수 있는 범위 밖 | S-06c 경고 유지 (정상 화면이면 버튼이 비활성이라 오지 않음) |
| 422 | `INVALID_SUSPENSION` | 집행유예를 허용하지 않는 형벌 · 형량인데 값이 있음, 기간이 1 ~ 5년 밖 | 입력 안내 |
| 401 | `UNAUTHORIZED` | (확장, 관리자 API) 로그인하지 않음 · 세션 만료 | 관리자 로그인 화면 |
| 401 | `INVALID_CREDENTIALS` | (확장, 관리자 API) 로그인 실패. 계정 없음 · 비활성 · 비밀번호 불일치를 구분하지 않는다 | 로그인 실패 안내 |
| 403 | `FORBIDDEN` | (확장, 관리자 API) 권한 없음, CSRF 토큰 없음 · 불일치 | CSRF 토큰을 다시 받아 재시도 · 일반 오류 안내 |
| 500 | `INTERNAL_ERROR` | 서버 오류 | 일반 오류 안내 |
- "이미 제출함"과 "순서가 맞지 않음"을 따로 나누지 않고 `INVALID_STATE` 하나로 둔다. 화면이 할 일은 둘 다 "`currentStatus`의 화면으로 이동"으로 같기 때문이다.

### 1-6. 체험 상태 → 화면 (모든 화면 공통)

화면은 진입할 때 상태를 따로 조회하지 않고 **그 화면의 API를 바로 부른다.** 허용되지 않은 화면이면 API가 `409 INVALID_STATE`와 `currentStatus`를 돌려주므로, 화면은 아래 표의 "보낼 화면"으로 이동한다(IA 5장 · 9장, 6장 #1 확정). API 3은 상태만 알아야 하는 경우에 쓴다.

| status | 보낼 화면 | 허용 화면 |
| --- | --- | --- |
| `STARTED` | S-03 | S-03 |
| `PRE_JUDGED`, `REVIEWING` | S-04 | S-04 |
| `REVIEWED` | S-05 | S-04, S-05, S-06 |
| `VERDICT_CONFIRMED` | S-07 | S-07 |
| `AI_REVEALED` | S-08 | S-07, S-08 |
| `COMPLETED` | S-09 | S-07, S-08, S-09 |

---

## 2. API 목록

| # | 메서드 | 경로 | 설명 | 허용 상태 | 상태 변경 | 화면 | 단계 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| 1 | GET | `/cases` | 사건 목록 | — | — | S-02 | MVP |
| 2 | POST | `/cases/{caseId}/experience` | 체험 시작 (있으면 기존 체험) | — | 없으면 → `STARTED` | S-02 | MVP |
| 3 | GET | `/cases/{caseId}/experience` | 내 체험 상태 | 전체 | — | 전체 | MVP |
| 4 | GET | `/cases/{caseId}/experience/overview` | 사건 개요 · 사전 판단 선택지 | `STARTED` | — | S-03 | MVP |
| 5 | POST | `/cases/{caseId}/experience/pre-judgment` | 사전 판단 제출 | `STARTED` | → `PRE_JUDGED` | S-03 | MVP |
| 6 | GET | `/cases/{caseId}/experience/review` | 사건 정보 (열린 섹션까지) | `PRE_JUDGED` ~ `REVIEWED` | — | S-04, S-05 | MVP |
| 7 | POST | `/cases/{caseId}/experience/review-steps` | 섹션 확인 기록 | `PRE_JUDGED` ~ `REVIEWED` (`REVIEWED`는 상태 유지) | → `REVIEWING` / `REVIEWED` | S-04 | MVP |
| 8 | GET | `/cases/{caseId}/experience/verdict-form` | 판결 입력 정보 | `REVIEWED` | — | S-06 | MVP |
| 9 | POST | `/cases/{caseId}/experience/verdict` | 판결 제출 | `REVIEWED` | → `VERDICT_CONFIRMED` | S-06 | MVP |
| 10 | GET | `/cases/{caseId}/experience/judgments/ai` | AI 판결 | `VERDICT_CONFIRMED` 이상 | — | S-07 | MVP |
| 11 | POST | `/cases/{caseId}/experience/court-reveal` | 실제 판결 공개 | `VERDICT_CONFIRMED` 이상 | 처음이면 → `AI_REVEALED` | S-07 | MVP |
| 12 | GET | `/cases/{caseId}/experience/judgments/court` | 실제 판결 | `AI_REVEALED` 이상 | — | S-08 | MVP |
| 13 | POST | `/cases/{caseId}/experience/comparison-reveal` | 비교 공개 | `AI_REVEALED` 이상 | 처음이면 → `COMPLETED` | S-08 | MVP |
| 14 | GET | `/cases/{caseId}/experience/comparison` | 세 판결 비교 | `COMPLETED` | — | S-09 | MVP |
| 15 | GET | `/cases/{caseId}/experience/comparison/analysis` | AI 비교 분석 | `COMPLETED` | — | S-09 | 확장 |

**공개(11, 13)와 조회(12, 14)를 나눈 이유**: 상태를 바꾸는 요청은 POST로 두고, 조회는 몇 번을 불러도 상태가 바뀌지 않게 한다. 결과 화면을 새로고침하면 조회만 다시 부르면 된다. 공개 요청은 이미 공개된 상태에서 다시 와도 거절하지 않고 성공으로 돌려준다(시퀀스 7장).

---

## 3. API 상세

### API 1. 사건 목록 — `GET /cases`

**요청**

| 위치 | 이름 | 필수 | 설명 |
| --- | --- | --- | --- |
| query | `crimeType` |  | `MURDER` / `FRAUD` / `INJURY`. 없으면 전체 |

**응답 200**

json

```json
{  "summary": { "total": 9, "byCrimeType": { "MURDER": 3, "FRAUD": 3, "INJURY": 3 } },  "cases": [    {      "caseId": 1,      "title": "빌린 돈 문제로 찾아온 지인을 살해한 사건",      "crimeType": "MURDER",      "crimeCategoryLabel": "생명범죄",      "shortIntro": "빌린 돈 문제로 찾아온 지인과 다투다 흉기로 살해한 사건입니다.",      "keywords": ["돈 문제", "집으로 찾아옴"],      "difficulty": "HIGH",      "estimatedMinutes": 15,      "participantCount": 1284,      "thumbnailUrl": null    }  ]}
```

- `PUBLISHED` 사건만. `summary`는 필터와 관계없이 전체 기준(칩 옆 건수 표시용).
- `crimeCategoryLabel`: `crime_type`별 표시용 분류명(예: `FRAUD` → "재산범죄"). DB 컬럼이 아니라 서버 코드 상수에서 만든다(ERD 3-1).
- `thumbnailUrl`: `legal_case.thumbnail_url`. 없으면 `null`이고, 화면은 범죄 유형별 기본 이미지를 쓴다.
- `participantCount`: `attempt_no = 1`이고 `COMPLETED`인 체험 수.
- 정렬: `published_at` 최신순 고정 (정렬 선택은 이후 단계, REQ-009).
- (확장, REQ-010) 쿠키가 있으면 카드마다 `myStatus`(`null` / `IN_PROGRESS` / `COMPLETED`)를 넣어 배지를 표시할 수 있다.

---

### API 2. 체험 시작 — `POST /cases/{caseId}/experience`

**요청**: 본문 없음. 쿠키가 있으면 함께 보낸다.

**응답** — 새로 만들면 `201`, 이미 있으면 `200`. 본문은 API 3과 같다.

json

```json
{  "caseId": 1,  "attemptNo": 1,  "status": "STARTED",  "lastReviewedStep": 0,  "startedAt": "2026-09-28T15:30:00+09:00"}
```

- 쿠키가 없거나 DB에 없는 익명 ID면 `anonymous_user`를 만들고 `Set-Cookie`로 내려준다.
- 이미 체험이 있으면 **새로 만들지 않고** 기존 체험을 돌려준다. 화면은 1-6 표대로 이동한다(완료한 사건이면 S-09, REQ-011 · 012).
- 동시에 두 번 와도 유니크 제약(`anonymous_user_id`, `case_id`, `attempt_no`)으로 하나만 생긴다. 제약 위반이 나면 서버가 기존 체험을 다시 읽어 `200`으로 돌려준다.

| 거절 | 조건 |
| --- | --- |
| 404 `CASE_NOT_FOUND` | 사건 없음 · 비공개 |

---

### API 3. 내 체험 상태 — `GET /cases/{caseId}/experience`

**응답 200**: API 2와 같은 형식.

| 거절 | 조건 |
| --- | --- |
| 404 `CASE_NOT_FOUND` |  |
| 404 `EXPERIENCE_NOT_FOUND` | 체험 없음 (쿠키 없음 포함) → 화면은 S-02로 |

---

### API 4. 사건 개요 · 사전 판단 선택지 — `GET /cases/{caseId}/experience/overview`

**응답 200**

json

```json
{  "case": {    "caseId": 1,    "title": "빌린 돈 문제로 찾아온 지인을 살해한 사건",    "crimeType": "MURDER",    "crimeCategoryLabel": "생명범죄",    "chargeName": "살인",    "overview": "피고인이 빌린 돈을 갚지 못해 오래 다투던 지인이 집으로 찾아오자, 말다툼 끝에 집에 있던 흉기로 피해자를 살해하고 구호 조치 없이 집을 나간 사건이다."  },  "rangeOptions": [    { "rangeOptionId": 8, "label": "징역형 집행유예" },    { "rangeOptionId": 9, "label": "실형 3년 미만" },    { "rangeOptionId": 10, "label": "실형 3년 이상 ~ 5년 미만" },    { "rangeOptionId": 11, "label": "실형 5년 이상 ~ 10년 미만" },    { "rangeOptionId": 12, "label": "실형 10년 이상 ~ 20년 미만" },    { "rangeOptionId": 13, "label": "실형 20년 이상" },    { "rangeOptionId": 14, "label": "무기징역" },    { "rangeOptionId": 15, "label": "사형" }  ],  "preFactors": [    { "factorId": 1, "label": "돈 문제로 오래 다툼이 있었다" },    { "factorId": 2, "label": "다투던 중 흉기를 집어 들었다" },    { "factorId": 3, "label": "범행 뒤 현장을 떠났다" }  ]}
```

- `rangeOptions`: 이 사건 `crime_type`의 `sentence_range_option`을 `display_order` 순으로. 사기 · 상해는 공통 7개, 살인은 벌금형을 빼고 무기징역 · 사형을 더한 8개다.
- `preFactors`: `reveal_stage = OVERVIEW`인 요소의 `pre_label`. **확장(REQ-093)** — MVP 화면은 쓰지 않아도 된다.
- 법정형 · 선고 가능 범위 · 권고 범위 · 실제 판결은 **넣지 않는다**(FR-2-8).

| 거절 | 조건 |
| --- | --- |
| 404 `CASE_NOT_FOUND` | 사건 없음 · 비공개 |
| 404 `EXPERIENCE_NOT_FOUND` | 이 사건의 내 체험이 없음 (쿠키 없음 포함) → 화면은 S-02로 |
| 409 `INVALID_STATE` | `STARTED`가 아님 (이미 제출) → `currentStatus`의 화면 (1-6) |

---

### API 5. 사전 판단 제출 — `POST /cases/{caseId}/experience/pre-judgment`

**요청**

json

```json
{ "rangeOptionId": 11, "factorIds": [1, 2] }
```

| 필드 | 필수 | 규칙 |
| --- | --- | --- |
| `rangeOptionId` | ✓ | 이 사건 범죄 유형의 선택지 |
| `factorIds` |  | 0 ~ 2개, `OVERVIEW` 요소, 중복 없음. **확장** — MVP는 빈 배열이나 생략 |

**응답 200**

json

```json
{ "status": "PRE_JUDGED", "lastReviewedStep": 1 }
```

- 한 트랜잭션: `judgment`(`USER`, `PRE`) + `judgment_factor`(방향 NULL) 저장, 상태 `STARTED` → `PRE_JUDGED`, `last_reviewed_step = 1`(섹션 ① 개요는 S-03에서 본 것으로 처리).
- 상태 변경은 조건부 갱신("`status = STARTED`일 때만")으로 한다. 동시에 두 번 오면 하나만 성공하고 나머지는 `INVALID_STATE`(`currentStatus` 포함). 패자가 판단 저장에서 유니크 제약에 걸려도 같은 응답으로 바꿔 돌려준다.
- 응답에 사전 판단 내용을 되돌려주지 않는다(S-09 전까지 다시 보여 주지 않음).
- 검사 순서: 사건 · 체험(404) → 상태(409) → 형량 구간(422) → 판단 요소(422: 개수 → 유효성). 첫 번째로 걸린 사유 하나로 거절하며, 거절하면 상태 · 판단 · 판단 요소 어느 것도 저장하지 않는다. 요청 형식 오류(400)는 그 앞에서 걸러진다.
- `factorIds`(확장)의 유효성은 "이 사건의 `OVERVIEW` 요소이고 중복이 없다"이다. 하나라도 어긋나면 전체를 `INVALID_FACTOR`로 거절한다.

| 거절 | 조건 |
| --- | --- |
| 400 `VALIDATION_ERROR` | `rangeOptionId` 누락 · 형식 오류, `factorIds` 형식 오류 |
| 404 `CASE_NOT_FOUND` | 사건 없음 · 비공개 |
| 404 `EXPERIENCE_NOT_FOUND` | 이 사건의 내 체험이 없음 (쿠키 없음 포함) → 화면은 S-02로 |
| 409 `INVALID_STATE` | `STARTED`가 아님 |
| 422 `INVALID_RANGE_OPTION` | 없는 구간이거나 이 사건 범죄 유형의 구간이 아님 |
| 422 `INVALID_FACTOR` | 이 사건의 요소가 아님, `OVERVIEW`가 아닌 요소, 중복, 없는 요소 |
| 422 `TOO_MANY_FACTORS` | 2개 초과 (확장) |

---

### API 6. 사건 정보 — `GET /cases/{caseId}/experience/review`

S-04와 S-05가 함께 쓴다.

**섹션 번호**: ① 개요 = 1, ② 상세 사실관계 = 2, ③ 양측 주장 = 3, ④ 법률 · 양형기준 = 4. `lastReviewedStep`은 확인을 마친 마지막 번호다.

**응답 200** (예: 섹션 ②까지 확인, ③이 열린 상태)

json

```json
{  "status": "REVIEWING",  "lastReviewedStep": 2,  "openStep": 3,  "sections": [    { "step": 1, "stage": "OVERVIEW", "confirmed": true,      "items": [ { "sectionType": "OVERVIEW", "title": "사건 개요", "content": "피고인이 빌린 돈을 갚지 못해 오래 다투던 지인이 집으로 찾아오자 …" } ] },    { "step": 2, "stage": "DETAIL", "confirmed": true,      "items": [        { "sectionType": "FACTS", "title": "주요 사실관계", "content": "사건 3개월 전부터 변제 문제로 여러 차례 다툼이 있었고 …" },        { "sectionType": "DAMAGE", "title": "피해 결과", "data": [ { "label": "피해자 수", "value": "1명" }, { "label": "피해 결과", "value": "사망" }, { "label": "피해자와의 관계", "value": "지인 (돈을 빌린 사이)" }, { "label": "범행 도구", "value": "집에 있던 흉기" } ] },        { "sectionType": "DEFENDANT", "title": "피고인 관련 사실", "content": "30대이고 형사처벌을 받은 전력이 없다." },        { "sectionType": "SETTLEMENT", "title": "합의 · 피해 회복", "content": "피해 회복을 위해 5,000만 원을 공탁했으나 합의에 이르지 못했고, 유족은 엄벌을 원한다." }      ] },    { "step": 3, "stage": "ARGUMENT", "confirmed": false,      "items": [        { "sectionType": "PROSECUTOR", "title": "검사", "content": "…" },        { "sectionType": "DEFENSE", "title": "피고인 · 변호인", "content": "…" }      ] }  ],  "lockedSteps": [4],  "law": null,  "summary": null}
```

- step 1의 항목은 `case_section`이 아니라 `legal_case.overview`로 만든다(`sectionType: "OVERVIEW"`는 응답용 값). step 2 ~ 4는 `case_section`의 `stage`별 항목이다.
- **열린 섹션(`lastReviewedStep + 1`)까지만** 본문을 넣는다. 잠긴 섹션은 번호만 `lockedSteps`에 넣는다(개발자 도구로 미리 보기 방지).
- `law`: 섹션 ④가 열리면 채운다.
- 용어 설명(`case_section` `LAW_TERM`)은 섹션 ④ `items`에 넣지 않고 `law.terms`로만 보낸다.
- `content`에는 항목마다 줄바꿈(`\n`)이 들어갈 수 있다. 화면은 줄바꿈을 그대로 보여 준다(v0.11, ERD v1.9).
- 양측 주장(`PROSECUTOR` · `DEFENSE`) 섹션 위 안내 문구는 응답에 넣지 않는다. 화면 고정 문구다(정보 구조 S-04).

json

```json
"law": {  "appliedLaw": "형법 제250조 제1항 살인",  "statutoryPenaltyText": "사형, 무기 또는 5년 이상의 징역",  "allowedRanges": [    { "penaltyType": "DEATH", "allowedMin": 240, "allowedMax": 600, "text": "사형 (감경하면 무기징역 또는 징역 20년 ~ 50년)" },    { "penaltyType": "LIFE", "allowedMin": 120, "allowedMax": 600, "text": "무기징역 (감경하면 징역 10년 ~ 50년)" },    { "penaltyType": "PRISON", "allowedMin": 30, "allowedMax": 360, "text": "징역 2년 6개월 ~ 30년" }  ],  "allowedRangeNote": "감경·가중 사유를 반영해 법률상 선고할 수 있는 가장 넓은 범위예요.",  "recommended": { "minMonths": 84, "maxMonths": 144, "basis": "살인범죄 제2유형(보통 동기 살인), 감경영역. 특별감경인자 1개(실질적 피해 회복), 특별가중인자 없음" },  "terms": [ { "term": "감경영역", "desc": "형을 가볍게 할 특별한 사정이 있어 기본 권고 형량보다 낮은 구간이 적용되는 구간" } ]}
```

- 권고 범위 예시 값(84 ~ 144개월)은 설명용이다. 살인 제2유형(보통 동기 살인) 감경영역(7년 ~ 12년)을 가정한 **가상 값**이며, **대표 판례 등록 시 팀이 양형기준으로 다시 계산한다.** (와이어프레임 v2.1의 예시 값은 예전 사기 예시 사건 기준이라 이 명세와 다르다.)
- `summary`: `REVIEWED`일 때 S-05용 핵심 사실 요약(`case_section` `SUMMARY`)을 채운다.
- `allowedRanges`: `penalty_rule` 중 범위(`allowed_min` · `allowed_max`)가 모두 있는 규칙만 넣는다. 화면에는 징역만 보여 줘도 된다.
- (v0.4) 사형 · 무기가 법정형에 있는 사건은 `DEATH` · `LIFE` 항목도 넣는다. 이때 `allowedMin` ~ `allowedMax`는 작량감경해 징역으로 선고할 때의 범위이고, `text`에 그대로 선고할 수 있다는 내용을 함께 쓴다. 예: `{ "penaltyType": "LIFE", "allowedMin": 120, "allowedMax": 600, "text": "무기징역 (감경하면 징역 10년 ~ 50년)" }`, `{ "penaltyType": "DEATH", "allowedMin": 240, "allowedMax": 600, "text": "사형 (감경하면 무기징역 또는 징역 20년 ~ 50년)" }`
- `allowedRangeNote`는 서버 고정 문구다. `penalty_rule.allowed_basis`(산출 근거)는 내부용이라 응답하지 않는다.
- `allowedRanges.text`는 서버가 만들어 주는 표시 문구다. 화면마다 "1개월 ~ 10년" 표기가 달라지지 않게 하기 위해서다. 형식은 API 8 `options[].text`와 같다.
- 사전 판단은 넣지 않는다.

| 거절 | 조건 |
| --- | --- |
| 409 `INVALID_STATE` | `STARTED` (사전 판단 전) → S-03, `VERDICT_CONFIRMED` 이상 → 결과 화면 (1-6) |

---

### API 7. 섹션 확인 기록 — `POST /cases/{caseId}/experience/review-steps`

**요청**

json

```json
{ "step": 3 }
```

**응답 200**

json

```json
{ "status": "REVIEWING", "lastReviewedStep": 3, "openStep": 4 }
```

| 요청 step | 처리 |
| --- | --- |
| `lastReviewedStep + 1`이고 2 또는 3 | `last_reviewed_step` 갱신, 상태 `REVIEWING` |
| `lastReviewedStep + 1`이고 4 | `last_reviewed_step = 4`, 상태 `REVIEWED`. 응답 `openStep: null` |
| `lastReviewedStep` 이하 | 이미 확인한 섹션. **거절하지 않고** 현재 상태를 그대로 돌려준다 (버튼 두 번 누름 대비) |
| 그 밖 (건너뜀) | 409 `STEP_OUT_OF_ORDER` |
- 확인 후 다음 섹션 본문은 API 6을 다시 불러 받는다.

| 거절 | 조건 |
| --- | --- |
| 409 `INVALID_STATE` | `STARTED`, 또는 `VERDICT_CONFIRMED` 이상 |
| 409 `STEP_OUT_OF_ORDER` |  |
| 400 `VALIDATION_ERROR` | step이 2 ~ 4가 아님 |

`REVIEWED`에서 step 4 이하가 오면 현재 상태를 그대로 돌려준다.

---

### API 8. 판결 입력 정보 — `GET /cases/{caseId}/experience/verdict-form`

**응답 200**

json

```json
{  "penaltyOptions": [    { "penaltyType": "DEATH", "allowedMin": 240, "allowedMax": 600, "text": "사형 (감경하면 무기징역 또는 징역 20년 ~ 50년)", "suspensionAllowed": false, "reducibleTo": ["LIFE", "PRISON"] },    { "penaltyType": "LIFE", "allowedMin": 120, "allowedMax": 600, "text": "무기징역 (감경하면 징역 10년 ~ 50년)", "suspensionAllowed": false, "reducibleTo": ["PRISON"] },    { "penaltyType": "PRISON", "allowedMin": 30, "allowedMax": 360, "text": "징역 2년 6개월 ~ 30년", "suspensionAllowed": true, "reducibleTo": [] }  ],  "statutoryPenaltyText": "사형, 무기 또는 5년 이상의 징역",  "allowedRangeNote": "감경·가중 사유를 반영해 법률상 선고할 수 있는 가장 넓은 범위예요.",  "recommended": { "minMonths": 84, "maxMonths": 144, "basis": "…" },  "suspensionRule": { "maxPrisonMonths": 36, "maxFineAmount": 5000000, "minMonths": 12, "maxMonths": 60 },  "factors": [    { "factorId": 1, "label": "빌린 돈을 갚지 못해 오래 다툼이 있었다" },    { "factorId": 2, "label": "다투던 중 집에 있던 흉기를 집어 들었다" },    { "factorId": 3, "label": "범행 뒤 구호 조치 없이 현장을 떠났다" },    { "factorId": 4, "label": "사건 3개월 전부터 변제 문제로 여러 차례 다퉜다" },    { "factorId": 5, "label": "유족이 엄벌을 원한다" },    { "factorId": 6, "label": "피해자에게는 부양하던 어린 자녀 2명이 있다" },    { "factorId": 7, "label": "수사 초기부터 범행을 인정하고 반성하고 있다" },    { "factorId": 8, "label": "형사처벌 전력이 없다" },    { "factorId": 9, "label": "피해 회복을 위해 5,000만 원을 공탁했다" },    { "factorId": 10, "label": "피고인은 우발적 범행이라고 주장한다" },    { "factorId": 11, "label": "피고인은 오랜 채무로 정신적으로 지쳐 있었다고 주장한다" }  ]}
```

- `penaltyOptions`: `penalty_rule`의 `display_order` 순. 화면은 여기 있는 형벌만 보여 준다(FR-3-1). 버튼 비활성 판단(S-06c)도 이 값으로 한다.
- 무죄는 MVP 선택지에서 뺐다(요구사항 15장, v0.3). `penalty_rule`에도 무죄 행을 두지 않는다.
- (v0.4) 사형 · 무기가 법정형에 있는 사건은 `penaltyOptions`에 `DEATH` · `LIFE`가 들어간다. 항목마다 `reducibleTo`(감경해서 선고할 수 있는 형벌)를 함께 준다: `DEATH` → `["LIFE", "PRISON"]`, `LIFE` → `["PRISON"]`, `PRISON` · `FINE` → `[]`. `allowedMin` ~ `allowedMax`는 감경해 징역으로 선고할 때의 범위이고, `suspensionAllowed`는 `false`다. 예(살인): `{ "penaltyType": "LIFE", "allowedMin": 120, "allowedMax": 600, "text": "무기징역 (감경하면 징역 10년 ~ 50년)", "suspensionAllowed": false, "reducibleTo": ["PRISON"] }`
- (v0.4) 선고할 수 있는 범위 막대(FR-3-2)는 고른 형벌 항목의 `allowedMax`를 상한으로 쓴다. 유기징역이면 30년, 무기 · 사형을 감경하면 50년이다.
- 벌금 선고 가능 하한 25,000원은 형법 제45조 단서(감경 시 5만 원 미만 가능)와 제55조 제1항 제6호(벌금 감경 시 1/2)를 적용한 값이다(ERD 6장).
- `suspensionRule`: 집행유예 가능 조건(코드 상수, ERD `penalty_rule` 비고). 화면에서 집행유예 입력을 보여 줄지 판단한다.
- `factors`: 사건의 판단 요소 전체(`label`). 공개 단계와 관계없이 모두.
- 판결 입력값은 서버에 저장하지 않는다. S-04에 갔다 돌아오면 화면이 메모리에 기억한 값을 복원하고, 새로고침하면 빈 폼이다(임시 저장은 이후 단계, REQ-039).

| 거절 | 조건 |
| --- | --- |
| 409 `INVALID_STATE` | `REVIEWED`가 아님 → `currentStatus`의 화면 (1-6) |

---

### API 9. 판결 제출 — `POST /cases/{caseId}/experience/verdict`

**요청**

json

```json
{  "penaltyType": "PRISON",  "reducedTo": null,  "prisonMonths": 180,  "fineAmount": null,  "suspensionMonths": null,  "factors": [    { "factorId": 2, "direction": "UP" },    { "factorId": 6, "direction": "UP" },    { "factorId": 7, "direction": "DOWN" }  ],  "freeOpinion": null}
```

| 필드 | 필수 | 규칙 |
| --- | --- | --- |
| `penaltyType` | ✓ | 이 사건 `penalty_rule`에 있는 형벌 (법정형에서 고른 형벌: `DEATH` / `LIFE` / `PRISON` / `FINE`) |
| `reducedTo` |  | (v0.4) 감경 후 형벌. `DEATH` → `LIFE` · `PRISON`, `LIFE` → `PRISON`만 가능. 감경하지 않으면 `null`. 그 밖의 조합은 `INVALID_PENALTY_TYPE`. **형벌 종류가 바뀌는 감경만 기록한다.** 유기징역(`PRISON`) 안의 작량감경은 `reducedTo`에 기록하지 않는다 |
| `prisonMonths` | 최종 선고 형벌이 `PRISON`일 때 ✓ | 고른 형벌(`penaltyType`) 항목의 `allowedMin` ~ `allowedMax`. 최종 선고 형벌이 `DEATH` · `LIFE`면 `null` |
| `fineAmount` | `FINE`일 때 ✓ | `allowedMin` ~ `allowedMax` |
| `suspensionMonths` |  | 형벌이 집행유예 허용이고, 징역 36개월 이하 또는 벌금 500만 원 이하일 때만. 12 ~ 60. `DEATH` · `LIFE`를 고르면 감경해도 불가 |
| `factors` |  | 0개 이상, 이 사건 요소, 중복 없음, `direction` 필수(`UP` / `DOWN`) |
| `freeOpinion` |  | 확장(REQ-033). 최대 1,000자. 비교 대상 아님 |
- 선택하지 않은 형벌의 값(`PRISON`인데 `fineAmount`)은 `null`이어야 한다. 값이 있으면 `VALIDATION_ERROR`. 최종 선고 형벌(`reducedTo`가 있으면 그 값)이 `DEATH` · `LIFE`인데 `prisonMonths` · `fineAmount` · `suspensionMonths`에 값이 있어도 `VALIDATION_ERROR`다.
- (v0.4) 최종 선고 형벌은 `reducedTo`가 있으면 그 값, 없으면 `penaltyType`이다. 예: 무기징역 그대로 `{ "penaltyType": "LIFE", "reducedTo": null, "prisonMonths": null }`, 무기징역을 감경해 징역 40년 `{ "penaltyType": "LIFE", "reducedTo": "PRISON", "prisonMonths": 480 }`
- 무죄는 MVP에서 받지 않는다(요구사항 15장, v0.3). `penalty_rule`에 없는 형벌이므로 `INVALID_PENALTY_TYPE`으로 거절된다.
- (v0.9) `penaltyType`이 형벌 열거값(`DEATH` · `LIFE` · `PRISON` · `FINE`)에 없는 문자열(`NOT_GUILTY`, 오타 등)이어도 400이 아니라 422 `INVALID_PENALTY_TYPE`이다. 무죄를 위 규칙대로 거절하기 위해서이며, 1-5의 "잘못된 열거값 → `VALIDATION_ERROR`"보다 이 규칙이 우선한다. 그래서 서버는 `penaltyType` · `reducedTo`를 문자열로 받는다.

**응답 200**

json

```json
{ "status": "VERDICT_CONFIRMED" }
```

- 한 트랜잭션: `judgment`(`USER`, `FINAL`) + `judgment_factor` 저장, 상태 `REVIEWED` → `VERDICT_CONFIRMED`(조건부 갱신).
- 검사 순서: 상태 → 형벌(감경 조합 포함) → 형량 범위 → 집행유예 → 요소. 첫 번째로 걸린 사유 하나로 거절한다.

| 거절 | 조건 |
| --- | --- |
| 409 `INVALID_STATE` | `REVIEWED`가 아님 (이미 확정 포함) |
| 422 `INVALID_PENALTY_TYPE` |  |
| 422 `OUT_OF_ALLOWED_RANGE` | `details`에 `{ "field": "prisonMonths", "reason": "OUT_OF_ALLOWED_RANGE" }` |
| 422 `INVALID_SUSPENSION` |  |
| 422 `INVALID_FACTOR` |  |
| 400 `VALIDATION_ERROR` |  |

---

### 판결 응답 공통 형식 (API 10 · 12 · 14)

사용자 · AI · 실제 판결을 `subjectType` 기준의 **같은 형식**으로 돌려준다(요구사항 16장, DR-1).

json

```json
{  "subjectType": "AI",  "penaltyType": "PRISON",  "reducedTo": null,  "prisonMonths": 144,  "fineAmount": null,  "suspensionMonths": null,  "extraDispositions": [],  "summary": "다투다 벌어진 범행과 공탁 · 반성을 함께 저울질한 판단",  "reasoning": "흉기를 집어 들어 피해자를 공격하고 구호 조치 없이 자리를 떠난 점은 무겁지만, 공탁으로 피해 회복을 시도했고 범행을 인정하며 전력이 없는 점을 고려했다.",  "factors": [    { "factorId": 2, "label": "다투던 중 집에 있던 흉기를 집어 들었다", "direction": "UP", "evidence": null }  ]}
```

| 필드 | USER | AI | COURT |
| --- | --- | --- | --- |
| `summary` | 판단 요소 요약 태그로 만든 규칙 문장 (MVP) | 팀 입력 (`judgment.summary`) | 팀 입력 (`judgment.summary`) |
| `reasoning` | `null` | 판결 이유 | 재판부 판단 근거 요약 |
| `excerpt` · `plainExplanation` | 없음 | 없음 | 판결문 발췌 · 쉽게 말하면 |
| `extraDispositions` | `[]` | `[]` | 부가 처분 (예: 사회봉사 80시간, 몰수). `type`: `COMMUNITY_SERVICE` · `CONFISCATION` (ERD v1.4) |
| `reducedTo` | 제출값 | 등록값 | 등록값. 감경 후 형벌 (v0.4, ERD `judgment.reduced_to`) |
| `factors[].evidence` | `null` | `null` | 판결문 근거 문장 |
- `factors`에는 **고려한 요소만** 들어간다. 없는 요소는 "—"(ERD 결정 #1).
- `summary`는 S-09 판결 카드의 한 줄 요약이다.
  - **USER (MVP)**: 사용자가 고른 요소의 요약 태그(`factor.summary_tag`)를 방향별로 모아 서버가 규칙 문장으로 만든다. AI를 부르지 않는다. 문장 틀 **확정 (COMMON-16, 2026-10-06)**: "`{↑ 태그들}`을 무겁게 보고 `{↓ 태그들}`을 감안한 판단". ↑만 있으면 "`{↑ 태그들}`을 무겁게 본 판단", ↓만 있으면 "`{↓ 태그들}`을 감안한 판단", 고른 요소가 없으면 "판단 요소를 고르지 않은 판단". 같은 태그는 한 번만 쓴다. 같은 태그를 가진 요소를 하나는 ↑, 다른 하나는 ↓로 골랐으면 **먼저 고른(표시 순서가 앞선) 방향에만** 남긴다 — 한 사건 안의 여러 요소가 같은 태그를 가질 수 있어(ERD) 그대로 두면 "A를 무겁게 보고 A를 감안한 판단"처럼 스스로 모순되는 문장이 된다 (BE-10). 태그 뒤 조사는 받침에 따라 `을` · `를`을 고른다. 태그가 괄호 · 문장부호로 끝날 수 있으므로(`반성(자백)`) 끝에서부터 거슬러 올라가 처음 만나는 한글 또는 숫자로 판정한다(숫자는 한글로 읽은 소리 기준, `전과 3` → `을`) (BE-10). 예: 요소 2(`흉기 사용`, ↑) · 6(`피해자의 부양 가족`, ↑) · 7(`범행 인정 · 반성`, ↓) → "흉기 사용 · 피해자의 부양 가족을 무겁게 보고 범행 인정 · 반성을 감안한 판단"
  - **USER (확장, REQ-062)**: AI 비교 분석이 `DONE`이면 `perspectives.USER.text`(API 15)로 교체해 보여 준다. 실패하거나 생성 중이면 MVP 규칙 문장을 그대로 쓴다.
  - **AI · COURT**: 판결을 등록할 때 팀이 입력한 값. 없으면 `null`이고 카드에서 한 줄 요약을 숨긴다.

---

### API 10. AI 판결 — `GET /cases/{caseId}/experience/judgments/ai`

**응답 200**

json

```json
{  "judgment": { "subjectType": "AI", "penaltyType": "PRISON", "prisonMonths": 144, "suspensionMonths": null, "…": "…" },  "myJudgment": { "subjectType": "USER", "penaltyType": "PRISON", "prisonMonths": 180, "suspensionMonths": null, "…": "…" },  "diffFromMine": { "samePenaltyType": true, "prisonMonthsDiff": -36, "fineAmountDiff": null, "suspensionMonthsDiff": null },  "references": ["형법 제250조", "살인범죄 양형기준", "유사 판례 5건"]}
```

- 공개 AI 판결(`AI`, `is_published = true`)을 DB에서 읽기만 한다. **AI를 호출하지 않는다**(시퀀스 6장).
- `diffFromMine`: MVP는 숫자 차이만. 형벌 종류는 **최종 선고 형벌**(`reducedTo`가 있으면 그 값, v0.4)로 비교한다. 형벌 종류가 같아도 그 종류에 해당하는 값만 내려간다 — `PRISON`은 `prisonMonthsDiff`, `FINE`은 `fineAmountDiff`, `DEATH` · `LIFE`는 형량 값이 없어 둘 다 `null`이다(BE-10). **집행유예 여부가 다르면 같은 형벌로 보지 않는다** — 징역 2년 집행유예 3년과 징역 2년 실형은 개월 수가 같아도 같은 판결이 아니다(API 14 형벌 무게 순서 `SUSPENDED` < `PRISON`, BE-10). **둘 다 집행유예면 유예 기간도 `suspensionMonthsDiff`로 비교한다** — 징역 2년 집행유예 1년과 징역 2년 집행유예 3년은 징역 개월 수가 같아도 같은 판결이 아니다. 둘 다 집행유예가 아니면 `null`이다 (BE-10). 형벌 종류가 다르면 `prisonMonthsDiff` · `fineAmountDiff` 모두 `null`이고 `samePenaltyType: false`다. 화면이 "내 판결보다 6개월 짧음"으로 표시한다. 문구 규칙은 미정(요구사항 15장).
- `references`: 참고 자료 태그. `judgment.reference_tags`(AI 판결 등록 시 팀이 입력, ERD v1.3)에서 읽는다. 값이 없으면 빈 배열. (컬럼명은 SQL 예약어 `REFERENCES`를 피해 `reference_tags`로 두고, 응답 필드명은 `references` 그대로 쓴다.)
- 공개 사건인데 검수한 AI 판결(`AI`, `is_published = true`)이 등록돼 있지 않으면 `500 INTERNAL_ERROR`다. 사용자가 고칠 수 없는 데이터 문제라 거절 조건으로 두지 않는다 (BE-10).

| 거절 | 조건 |
| --- | --- |
| 409 `INVALID_STATE` | `VERDICT_CONFIRMED` 전 → `currentStatus`의 화면 (1-6) |

---

### API 11. 실제 판결 공개 — `POST /cases/{caseId}/experience/court-reveal`

**요청**: 본문 없음. **응답 200**: `{ "status": "AI_REVEALED" }`

- `VERDICT_CONFIRMED`면 `AI_REVEALED`로 바꾼다. 이미 `AI_REVEALED` · `COMPLETED`면 상태를 그대로 두고 현재 상태를 돌려준다.
- (확장) 처음 공개할 때 `comparison_analysis`를 `PENDING`으로 만들고 AI 비교 분석 생성을 비동기로 시작한다(시퀀스 8장). 응답은 생성을 기다리지 않는다.
- 상태를 옮기기 전에 **그다음 화면이 읽을 판결(공개 AI · 재판부 판결)이 등록돼 있는지 먼저 확인한다.** 확인하지 않으면 판결이 없는 사건에서 상태만 넘어가고 그 뒤 조회가 매번 500이 되어 되돌릴 방법 없이 결과 화면에 갇힌다 (BE-10).

| 거절 | 조건 |
| --- | --- |
| 409 `INVALID_STATE` | `VERDICT_CONFIRMED` 전 → `currentStatus`의 화면 (1-6) |

---

### API 12. 실제 판결 — `GET /cases/{caseId}/experience/judgments/court`

**응답 200**

json

```json
{  "judgment": {    "subjectType": "COURT",    "penaltyType": "PRISON",    "reducedTo": null,    "prisonMonths": 120,    "suspensionMonths": null,    "extraDispositions": [ { "type": "CONFISCATION", "value": "범행에 사용한 흉기" } ],    "summary": "유족의 처벌 의사를 무겁게 보면서도 공탁과 반성을 감안한 판단",    "reasoning": "…",    "excerpt": "…",    "plainExplanation": "…",    "factors": [ { "factorId": 2, "label": "다투던 중 집에 있던 흉기를 집어 들었다", "direction": "UP", "evidence": "…" } ]  },  "myJudgment": { "subjectType": "USER", "prisonMonths": 180, "suspensionMonths": null, "…": "…" },  "aiJudgment": { "subjectType": "AI", "prisonMonths": 144, "suspensionMonths": null, "…": "…" },  "source": { "sourceOrg": "법원 판결서 인터넷열람 서비스" },  "deidentifiedItems": ["인명", "지명", "사건번호", "날짜"]}
```

- `source`는 확장(REQ-055). `case_source` 중 `is_final = true`인 행의 `source_org`. 사건번호 · 법원명 · 선고일은 **절대 넣지 않는다**(FR-5-3).
- `deidentifiedItems`는 확장(FR-5-2 "이 사건에 대하여"). `legal_case.deidentified_items`에서 읽는다. 비식별화한 **항목 종류**만 내려가고 원래 값은 넣지 않는다. 값이 없으면 빈 배열.
- 공개 사건인데 검수한 재판부 · AI 판결이 등록돼 있지 않으면 `500 INTERNAL_ERROR`다 (API 10과 같은 이유, BE-10).

| 거절 | 조건 |
| --- | --- |
| 409 `INVALID_STATE` | `AI_REVEALED` 전 → `currentStatus`의 화면 (1-6, 보통 S-07) |

---

### API 13. 비교 공개 — `POST /cases/{caseId}/experience/comparison-reveal`

**요청**: 본문 없음. **응답 200**: `{ "status": "COMPLETED" }`

- `AI_REVEALED`면 `COMPLETED`로 바꾼다. 이미 `COMPLETED`면 그대로 돌려준다.
- 상태를 옮기기 전에 **그다음 화면이 읽을 판결(공개 AI · 재판부 판결)이 등록돼 있는지 먼저 확인한다.** 확인하지 않으면 판결이 없는 사건에서 상태만 넘어가고 그 뒤 조회가 매번 500이 되어 되돌릴 방법 없이 결과 화면에 갇힌다 (BE-10).

| 거절 | 조건 |
| --- | --- |
| 409 `INVALID_STATE` | `AI_REVEALED` 전 |

---

### API 14. 세 판결 비교 — `GET /cases/{caseId}/experience/comparison`

**응답 200**

json

```json
{  "preToFinal": {    "preJudgment": { "rangeOptionId": 11, "label": "실형 5년 이상 ~ 10년 미만", "factorIds": [1, 2] },    "finalJudgmentText": "징역 15년",    "direction": "HEAVIER",    "summaryText": "사건을 모두 확인한 뒤, 처음 생각보다 무거운 판결을 내렸어요."  },  "judgments": {    "USER": { "subjectType": "USER", "summary": "흉기 사용 · 피해자의 부양 가족을 무겁게 보고 범행 인정 · 반성을 감안한 판단", "…": "…" },    "AI": { "subjectType": "AI", "summary": "다투다 벌어진 범행과 공탁 · 반성을 함께 저울질한 판단", "…": "…" },    "COURT": { "subjectType": "COURT", "summary": "유족의 처벌 의사를 무겁게 보면서도 공탁과 반성을 감안한 판단", "…": "…" }  },  "matrix": [    { "factorId": 2, "label": "다투던 중 집에 있던 흉기를 집어 들었다", "revealStage": "OVERVIEW",      "user": "UP", "ai": "UP", "court": "UP", "category": "ALL_SAME" },    { "factorId": 6, "label": "피해자에게는 부양하던 어린 자녀 2명이 있다", "revealStage": "DETAIL",      "user": "UP", "ai": null, "court": "UP", "category": "DIVERGED" },    { "factorId": 7, "label": "수사 초기부터 범행을 인정하고 반성하고 있다", "revealStage": "DETAIL",      "user": "DOWN", "ai": "DOWN", "court": "DOWN", "category": "ALL_SAME" },    { "factorId": 9, "label": "피해 회복을 위해 5,000만 원을 공탁했다", "revealStage": "DETAIL",      "user": null, "ai": "DOWN", "court": "DOWN", "category": "ONLY_ME_MISSED" }  ],  "ruleSentences": {    "common": ["세 판결 모두 흉기 사용을 형량을 높이는 요소로, 범행 인정 · 반성을 형량을 낮추는 요소로 봤어요."],    "differences": ["AI와 재판부는 피해 회복 공탁을 고려했지만, 내 판결에서는 고려하지 않았어요.", "피해자의 부양 가족에 대한 판단이 세 판결 사이에서 엇갈렸어요."]  },  "analysisAvailable": false}
```

**`preToFinal.direction`** — 사전 판단 구간과 최종 판결을 비교한다(`sentence_range_option.kind` · 개월 범위 사용). **확정 (BE-11, 2026-10-06)**

| 값 | 기준 |
| --- | --- |
| `HEAVIER` | 최종 판결이 구간보다 무거움 (예: 구간은 집행유예인데 실형, 실형 구간 상한보다 긺) |
| `SAME` | 최종 판결이 구간 안 |
| `LIGHTER` | 최종 판결이 구간보다 가벼움 (예: 구간은 실형인데 집행유예) |
- 형벌 종류의 무게는 `FINE`(벌금, 벌금 집행유예 포함) < `SUSPENDED`(징역 집행유예) < `PRISON`(실형) < `LIFE`(무기징역) < `DEATH`(사형) 순으로 먼저 비교하고(`sentence_range_option.kind` 선언 순서), 같은 종류가 `PRISON`이면 구간의 `min_months`(이상) · `max_months`(미만)로 더 비교한다(하한 미만은 `LIGHTER`, 상한 이상은 `HEAVIER`). 그 외 같은 종류(`FINE` · `SUSPENDED` · `LIFE` · `DEATH`)는 더 세분하지 않고 `SAME`이다. 최종 판결은 `reducedTo`가 있으면 그 값(최종 선고 형벌)으로 비교한다(ERD `sentence_range_option` 무겁기 순서와 같다). 무죄는 MVP에서 뺐으므로 비교 대상이 아니다(v0.3).

**`matrix[].category`** — 와이어프레임 S-09의 분류 태그(FR-6-4).

| 값 | 화면 문구 | 기준 |
| --- | --- | --- |
| `ALL_SAME` | 셋 모두 같게 본 요소 | 세 값이 모두 같음 (셋 다 `null`, 즉 아무도 고려하지 않은 요소는 매트릭스에서 뺀다. ERD 6장과 같음. 단, (확장, REQ-096) 사전 판단에서 고른 요소는 `changeType`을 보여 주기 위해 남기고 `category`는 `null`로 둔다) |
| `ONLY_ME_MISSED` | 나만 고려하지 않은 요소 | 나는 `null`, AI와 재판부는 같은 방향 |
| `DIVERGED` | 판단이 엇갈린 요소 | 그 밖 |
- `ruleSentences`: MVP의 공통점 · 차이점. 매트릭스로 만드는 **규칙 문장**이며 AI를 부르지 않는다. 문장은 `matrix[].label`(완전한 서술문)이 아니라 `factor.summary_tag`(요소별 요약어)로 만든다 — 라벨을 그대로 쓰면 "~다"로 끝나는 서술문 뒤에 조사가 붙어 비문이 되기 때문이다. 문장 틀은 6장 #4 **확정 (BE-26 · BE-11, 2026-10-06)** 참고. "정답 · 틀렸다 · 이중 잣대" 표현은 쓰지 않는다(요구사항 11장 톤 원칙 · FR-6-3).
- `judgments.*.summary`: 판결 카드의 한 줄 요약(판결 응답 공통 형식 참고). `USER`는 MVP 규칙 문장이다.
- `analysisAvailable`: (확장) AI 비교 분석을 API 15로 불러올 수 있는지. MVP에서는 항상 `false`.
- (확장, REQ-096) `matrix[].changeType`에 판단 이유 변화 유형(`NEWLY_LEARNED` 새로 알게 된 요소 / `KEPT` 처음부터 알던 요소 · 판단에 그대로 반영 / `WEIGHT_CHANGED` 이미 알던 요소의 무게가 바뀜 / `null`)을 넣는다. 판별 기준은 요구사항 FR-6-6: `OVERVIEW` 이후 요소가 최종 판결에만 있으면 `NEWLY_LEARNED`, `OVERVIEW` 요소가 사전 · 최종 양쪽에 있으면 `KEPT`, 한쪽에만 있으면 `WEIGHT_CHANGED`. 사전 판단 작용 요소가 있을 때만.
- **사전 판단은 이 API에서 처음 내려간다.**

| 거절 | 조건 |
| --- | --- |
| 409 `INVALID_STATE` | `COMPLETED` 전 → `currentStatus`의 화면 (1-6, 보통 S-08) |

---

### API 15. (확장) AI 비교 분석 — `GET /cases/{caseId}/experience/comparison/analysis`

**응답 200**

json

```json
{  "status": "DONE",  "analysis": {    "common": [ { "text": "세 판결 모두 피해 금액이 큰 점을 무겁게 봤어요.", "factorIds": [1] } ],    "differences": [ { "text": "재판부는 피해자가 처벌을 원한다는 점을 형량을 높이는 요소로 봤지만, 나와 AI는 고려하지 않았어요.", "factorIds": [5] } ],    "perspectives": {      "USER": { "text": "돈을 받은 방식과 피해 규모를 무겁게 보고, 반성은 조금만 반영한 판단이에요.", "factorIds": [1, 3, 6] },      "AI": { "text": "…", "factorIds": [1, 3, 4] },      "COURT": { "text": "…", "factorIds": [4, 6, 7] }    }  }}
```

| status | analysis | 화면 |
| --- | --- | --- |
| `PENDING` | `null` | "분석 중" 표시, 2 ~ 3초 뒤 다시 조회 (최대 횟수는 기술 설계에서 정함) |
| `DONE` | 분석 | 공통점 · 차이점 영역을 교체 |
| `FAILED` | `null` | 규칙 문장 유지 (`fail_reason`은 응답하지 않음) |
- `perspectives`를 포함해 항목마다 `factorIds`가 있다. 서버 검증(목록 안의 요소인가, 금지 표현이 없는가)을 통과한 것만 `DONE`이다(시퀀스 8장 안전장치).
- `perspectives.USER.text`는 확장 단계의 **AI "내 판결" 요약**이다(REQ-062). `DONE`이면 화면은 S-09 내 판결 카드의 한 줄 요약(API 14 `judgments.USER.summary`, MVP 규칙 문장)을 이 문장으로 바꾼다. `perspectives.USER.factorIds`는 사용자가 고른 요소 안에서만 나와야 한다.

| 거절 | 조건 |
| --- | --- |
| 409 `INVALID_STATE` | `COMPLETED` 전 |
| 404 `EXPERIENCE_NOT_FOUND` |  |

분석 행이 없으면(확장 배포 전에 공개된 체험) `FAILED`로 응답한다.

---

## 4. 상태별 호출 가능 API

| status | 호출 가능 (조회) | 호출 가능 (변경) |
| --- | --- | --- |
| `STARTED` | 3, 4 | 5 |
| `PRE_JUDGED` | 3, 6 | 7 |
| `REVIEWING` | 3, 6 | 7 |
| `REVIEWED` | 3, 6, 8 | 7 (그대로 통과), 9 |
| `VERDICT_CONFIRMED` | 3, 10 | 11 |
| `AI_REVEALED` | 3, 10, 12 | 11 (그대로 통과), 13 |
| `COMPLETED` | 3, 10, 12, 14, 15 | 11 · 13 (그대로 통과) |

API 1 · 2는 상태와 관계없이 부를 수 있다. 표에 없는 조합은 `409 INVALID_STATE`다.

---

## 5. 화면별 호출 순서

| 화면 | 진입 시 | 버튼 |
| --- | --- | --- |
| S-02 | 1 | `체험 시작` → 2 → 응답 status로 이동 (1-6) |
| S-03 | 4 | `판단 제출` → 5 → S-04 |
| S-04 | 6 | `읽었습니다` → 7 → 6 다시 조회. 마지막 섹션 후 S-05 |
| S-05 | 6 (summary 포함) | `판결 내리러 가기` → S-06 |
| S-06 | 8 | `확정하기` → 9 → S-07 |
| S-07 | 10 | `실제 판결 확인하기` → 11 → S-08 |
| S-08 | 12 | `세 판결 비교 보기` → 13 → S-09 |
| S-09 | 14 (→ 15, 확장) | `다른 사건 체험` → S-02 |
- 모든 화면은 진입 API가 `409 INVALID_STATE`를 돌려주면 `currentStatus`로 1-6 표의 "보낼 화면"으로 이동한다. `404 EXPERIENCE_NOT_FOUND`면 S-02, `404 CASE_NOT_FOUND`면 S-14로 간다. 진입 전에 API 3을 따로 부르지 않는다(6장 #1 확정, 요청 1회 절약).
- S-04 · S-05는 같은 API 6을 쓰므로, `PRE_JUDGED` · `REVIEWING`에서 S-05에 들어오면 API 6이 성공한다. 이때는 응답의 `status`가 `REVIEWED`가 아니면 화면이 S-04로 보낸다.

---

## 6. 결정 필요 사항

| # | 내용 | 제안 | 영향 |
| --- | --- | --- | --- |
| 1 | 체험 상태 확인을 API 3으로 먼저 할지, 각 API의 `INVALID_STATE`로 처리할지 | **확정 (v0.3)**: 각 API의 `INVALID_STATE`로 처리 (요청 수 절약, 1-6 · 5장) | 프론트 라우팅 구조 (기술 스택 5장) |
| 2 | 쿠키 이름 · 기간, 프론트 · API 도메인 구조 | 도메인 구조 **확정 (v0.3)**: 같은 도메인, Nginx `/api` 프록시(기술 스택 8장). 쿠키 `NLNB_AID` · 1년은 제안 유지(요구사항 15장) | CORS · SameSite 설정 |
| 3 | `preToFinal.direction` 세부 비교 규칙 | **확정 (BE-11, 2026-10-06)**: 형벌 종류를 `FINE < SUSPENDED < PRISON < LIFE < DEATH` 순(`sentence_range_option.kind` 선언 순서)으로 먼저 비교한다. 종류가 다르면 그 순서로 `HEAVIER`/`LIGHTER`, 같은 종류가 `PRISON`이면 구간의 `min_months`/`max_months`(상한 미만 기준)로 더 비교하고, 그 외 종류는 세분하지 않고 `SAME`이다. 감경이 있으면 최종 선고 형벌(`reducedTo`)로 비교한다 | S-09 한 줄 요약 문구 |
| 4 | 규칙 문장(`ruleSentences`) 문장 틀 | **확정 (BE-26 · BE-11, 2026-10-06)**: 요소 라벨(완전한 서술문)이 아니라 요약어(`factor.summary_tag`)로 만든다. `ALL_SAME`은 방향별로 요약어를 모아 "세 판결 모두 {↑ 요약어들}을 형량을 높이는 요소로[, {↓ 요약어들}을 형량을 낮추는 요소로] 봤어요.", `ONLY_ME_MISSED`는 "AI와 재판부는 {요약어들}을 고려했지만, 내 판결에서는 고려하지 않았어요.", `DIVERGED`는 "{요약어들}에 대한 판단이 세 판결 사이에서 엇갈렸어요."로 한 문장씩 만든다. 해당 분류가 없으면 각각 "세 판결이 똑같이 본 판단 요소는 없어요." / "세 판결 사이에 판단이 엇갈린 점은 없어요."를 넣는다 | S-09 공통점 · 차이점 |
| 5 | 무죄 선택 처리 (요구사항 15장) | **확정 (v0.3)**: MVP에서 무죄 선택지를 뺀다. 이후 도입 여부는 추후 검토 | API 8 · 9 · 14 |
| 6 | API 15 재조회 간격 · 최대 횟수, AI 생성 시간 초과 기준 | 3초 간격, 최대 10회 / 30초 | 확장 단계 |
| 7 | 내 판결 한 줄 요약(`summary`) 규칙 문장 틀 · 태그 수 제한 | **확정 (COMMON-16 · BE-26, 2026-10-06)**: 423줄 문장 틀(`{↑ 태그들}을 무겁게 보고 {↓ 태그들}을 감안한 판단` 등)을 확정. 방향별 태그 수는 **제한하지 않는다**(BE-26) — 요소마다 서로 다른 요약어가 붙어 많이 고르면 문장이 길어질 수 있지만, S-09에서 실제로 문제가 되면 상한(예: 최대 3개 + "외 N개")을 다시 검토한다 | S-09 판결 카드 |

---

## 7. (확장) 관리자 API

관리자 후검수(BE-33)용 API다. **MVP 범위가 아니다.** 경로는 `/api/v1/admin/**`이고 관리자 로그인이 필요하다(로그인 · CSRF 발급 제외). 사용자 API(1장 ~ 6장)는 영향이 없다.

### 7-1. 공통

- **인증**: 세션 쿠키 `NLNB_ADMIN_SESSION`(로그인 API가 발급, HttpOnly · Secure · SameSite=Lax, 기본 30분). 로그인하지 않으면 401 `UNAUTHORIZED`
- **CSRF**: 상태를 바꾸는 요청(POST 등, 로그인 포함)은 헤더 `X-XSRF-TOKEN`에 쿠키 `XSRF-TOKEN` 값을 담는다. 없거나 다르면 403 `FORBIDDEN`. 쿠키는 A-1이 내려 준다
- 없는 관리자 경로도 로그인하지 않았으면 404가 아니라 401이다(경로 존재 여부를 드러내지 않는다)

### A-1. CSRF 토큰 — `GET /admin/auth/csrf`

로그인 전에 먼저 부른다. 토큰은 쿠키 `XSRF-TOKEN`(HttpOnly 아님, 화면 스크립트가 읽는다)으로 내려간다.

```json
{ "headerName": "X-XSRF-TOKEN" }
```

### A-2. 로그인 — `POST /admin/auth/login`

```json
{ "email": "admin@example.com", "password": "********" }
```

성공 200 (세션 쿠키 발급, 로그인 전에 세션이 있었으면 세션 ID를 바꾼다)

```json
{ "adminId": 1, "email": "admin@example.com", "displayName": "관리자" }
```

| 거절 | 조건 |
| --- | --- |
| 400 `VALIDATION_ERROR` | 이메일 · 비밀번호가 비었거나 너무 김 |
| 401 `INVALID_CREDENTIALS` | 계정 없음 · 비활성 · 비밀번호 불일치 (구분하지 않음) |
| 403 `FORBIDDEN` | CSRF 토큰 없음 · 불일치 |

이메일은 대소문자를 구분하지 않는다.

### A-3. 로그아웃 — `POST /admin/auth/logout`

세션을 끝낸다. 성공 204. 로그인하지 않았으면 401, CSRF 토큰이 없으면 403.

### A-4. 내 정보 — `GET /admin/auth/me`

로그인한 관리자 정보(A-2 성공 응답과 같은 형식). 로그인하지 않았으면 401 `UNAUTHORIZED`.

검수 대기 목록 · 상세(BE-41), 판결 후보 승인 · 반려(BE-42), 사건 공개 · 수정(BE-43)은 각 이슈에서 추가한다.

