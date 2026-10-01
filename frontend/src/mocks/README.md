# mocks

백엔드 API가 준비되기 전에 화면을 만들고 확인하려고 쓰는 **목(mock) API와 목 데이터**다. HTTP 요청을 보내지 않는다.

## 파일

| 파일 | 대응 API | 내용 |
| --- | --- | --- |
| `caseListMockApi.js` · `caseListMockData.js` | API 1 · 2 | 사건 목록, 체험 시작. `caseId` 1이 가상 살인 사건이고 2 ~ 9는 임의 데이터 |
| `overviewMockApi.js` · `overviewMockData.js` | API 4 · 5 | 사건 개요 · 형량 구간, 사전 판단 제출 |
| `reviewMockApi.js` · `reviewMockData.js` | API 6 · 7 | 사건 정보 섹션, 섹션 확인 기록 |
| `verdictMockApi.js` · `verdictMockData.js` | API 8 · 9 | 판결 입력 정보, 판결 제출 (감경 `reducedTo` 검증 포함) |
| `mockExperienceStore.js` | — | 체험 상태를 브라우저 `localStorage`(`nlnb-mock-review`)에 저장하는 목 전용 저장소 |

`reviewMockApi.js`의 `resetMock()` · `setMockStatus(status)`는 `preview.html`에서 체험 상태를 강제로 바꿔 화면을 확인할 때 쓴다. 실제 API에는 없는 목 전용 함수다.

## 규칙

- 데이터는 [API 명세서](../../../docs/api-specification.md)의 예시 값과 같게 유지한다. 가상 살인 사건 `caseId` 1만 S-03 이후 화면의 목과 연결된다.
- HTTP 상태가 없으므로, 에러는 명세의 에러 본문(`{ code, message, currentStatus, details }`)을 `throw`한다.
- 응답은 실제 네트워크처럼 0.25초 지연시킨다.
- 실제 체험 진행 상태는 서버가 관리한다. `mockExperienceStore.js`와 위 두 함수는 목 전용이다.
- 화면은 `api` 객체로 주입받아 쓰므로, 실제 API로 바꿀 때는 화면 코드가 아니라 이 객체를 교체한다. (`preview.html`이 목 API들을 합쳐서 주입한다)

## 확인 방법

```bash
cd frontend
npm install
npm run dev     # http://localhost:5173/preview.html 에서 화면별로 확인
```
