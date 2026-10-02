# api

화면이 쓰는 API를 만드는 곳이다. 목(`src/mocks/`)과 실제 백엔드가 **같은 함수 이름 · 인자 · 응답 형식 · 에러 형식**을 가져서, 화면은 어느 쪽인지 몰라도 된다.

| 파일 | 내용 |
| --- | --- |
| `config.js` | `API_BASE_URL`, `API_MODE` (환경변수 `VITE_API_BASE_URL`, `VITE_API_MODE`) |
| `client.js` | `fetch` 공통 함수. 성공은 응답 본문 그대로, 실패는 에러 본문을 `throw` |
| `realApi.js` | 실제 백엔드 API 함수 13개 (`docs/api-specification.md`) |
| `index.js` | `loadApi()` — 모드에 맞는 api 객체를 돌려준다 (기본 `mock`) |

## 규칙

- 요청은 `/api/v1` 아래로 보내고, 익명 ID 쿠키를 함께 보낸다 (`credentials: 'include'`). 쿠키가 `SameSite=Lax`라서 프론트와 API는 **같은 도메인**이어야 한다 (개발 서버 프록시, 운영 Nginx 프록시).
- 성공 응답은 감싸지 않는다 (API 명세 1-4). 실패하면 아래 형식을 `throw`한다.

```js
{ code: 'INVALID_STATE', message: '...', currentStatus: 'PRE_JUDGED', details: null, status: 409 }
```

- 서버가 에러 본문을 주지 못하면 같은 형식을 직접 만든다: 연결 실패 · 본문 없는 5xx(백엔드 미실행으로 프록시가 실패한 경우 등)는 `NETWORK_ERROR`, 그 밖은 `INTERNAL_ERROR`. 백엔드가 준 `INTERNAL_ERROR`(본문 있음)와는 구분된다.
- 함수를 추가할 때는 목과 실제를 **같은 이름 · 같은 인자**로 만든다.

## 함수

| 함수 | API |
| --- | --- |
| `getCases(crimeType)` | 1 사건 목록 |
| `startExperience(caseId)` | 2 체험 시작 |
| `getOverview(caseId)` · `postPreJudgment(caseId, body)` | 4 · 5 개요 · 사전 판단 |
| `getReview(caseId)` · `postReviewStep(caseId, step)` | 6 · 7 사건 정보 · 섹션 확인 |
| `getVerdictForm(caseId)` · `postVerdict(caseId, body)` | 8 · 9 판결 입력 · 제출 |
| `getAiJudgment(caseId)` | 10 AI 판결 |
| `postCourtReveal(caseId)` · `getCourtJudgment(caseId)` | 11 · 12 실제 판결 |
| `postComparisonReveal(caseId)` · `getComparison(caseId)` | 13 · 14 세 판결 비교 |

API 10 ~ 14는 백엔드 구현 전에는 404가 온다. 구현되면 그대로 동작한다.
