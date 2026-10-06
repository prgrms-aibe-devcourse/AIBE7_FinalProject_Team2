# overview

**S-03 사건 개요 · 사전 판단** 화면이다. 사용자는 사건 개요를 읽고 형량 구간 하나를 골라 사전 판단을 제출한다.

| 파일 | 내용 |
| --- | --- |
| `overviewPage.js` | `renderOverviewPage(container, { caseId, api, navigate })` — 화면을 그린다 |
| `overview.css` | 개요 · 사전 판단 화면 스타일 |

## 사용하는 API

| 호출 | API | 동작 |
| --- | --- | --- |
| `api.getOverview(caseId)` | API 4 `GET /cases/{caseId}/experience/overview` | 사건 개요와 형량 구간 선택지 |
| `api.postPreJudgment(caseId, { rangeOptionId })` | API 5 `POST /cases/{caseId}/experience/pre-judgment` | 사전 판단 제출. 성공하면 `review`(S-04)로 이동 |

## 동작 규칙

- 진입할 때 상태를 따로 조회하지 않고 API 4를 바로 부른다. `STARTED`가 아니면 `INVALID_STATE`와 `currentStatus`가 오고, 공통 래퍼([`app/errorRedirect.js`](../../app/README.md))가 [`utils/screenByStatus.js`](../../utils/README.md)에 따라 그 상태의 화면으로 이동시킨다. 화면은 이동하지 않는다.
- 사건이 없으면(`CASE_NOT_FOUND`) 안내와 "사건 목록으로" 버튼을, 래퍼가 이동하지 못한 상태 불일치(`INVALID_STATE`)는 "지금 단계에서는 이 화면을 볼 수 없어요" 안내와 "사건 목록으로" 버튼을, 그 밖의 오류는 "다시 시도" 버튼을 보여 준다.
- 법정형 · 선고 가능 범위 · 권고 범위 · 실제 판결은 API 4 응답에 없으므로 이 화면에도 보이지 않는다. (요구사항 FR-2-8)
- 요청 · 응답 형식은 [API 명세서](../../../../docs/api-specification.md) API 4 · 5를 따른다.
