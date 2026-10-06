# app

화면들을 하나의 앱으로 묶는 부분이다: 주소(라우터), 에러 시 화면 이동, 사건 제목, 공통 푸터. 동작은 `http://localhost:5173/sample.html`(연동 확인 페이지)에서 눌러 보며 확인할 수 있다.

| 파일 | 내용 |
| --- | --- |
| `routes.js` | **화면 표.** 이름 · 주소(IA 4장) · 진입 함수 · 푸터 · 사건 상단 바 필요 여부. 새 화면은 여기에 한 줄 추가한다 |
| `router.js` | History API 라우터. `navigate(이름, { caseId, ... })`, 새로고침 · 뒤로 가기 처리 |
| `errorRedirect.js` | api를 감싸서 에러 코드에 따라 화면을 이동시킨다 (API 명세 1-5 · 1-6) |
| `caseHeader.js` | 사건 상단 바의 제목 · 죄명을 공급한다 (정합성 점검 D-01 결정 전 임시 방식) |
| `appApi.js` | 목 또는 실제 api에 "사건 제목 기억"과 "에러 시 이동"을 입혀 화면에 주입할 api를 만든다 |
| `styles.js` | 화면별 CSS를 한 번에 불러온다 |
| `notFoundPage.js` | S-14 404 · 오류 화면. 알 수 없는 주소 · 없는 사건(`CASE_NOT_FOUND`)에서 보여 준다. 사이트 헤더 · 안내 · 이동 버튼 2개, 푸터는 라우터가 붙인다 |

## 화면이 지켜야 하는 형태

```js
export async function renderXxxPage(container, { caseId, caseHeader, api, navigate, section }) {
  const data = await api.getXxx(caseId);   // 목이든 실제든 같은 함수 · 같은 응답
  navigate('review');                       // 화면 이름으로 이동, caseId를 생략하면 지금 사건
}
```

- 화면은 `container.replaceChildren(root)`로 그린다. 푸터는 라우터가 화면 아래에 붙인다 (화면이 직접 그리면 `ownsFooter: true`).
- 같은 화면(주소 · 값이 같음)으로의 이동은 무시된다. 에러 이동과 화면 자체 이동이 겹쳐도 한 번만 그려진다.
- 화면이 이동한 뒤에도 이전 화면의 비동기 작업이 끝날 수 있으니, 그리기 전에 `container.contains(root)`로 아직 보이는 화면인지 확인한다 (기존 화면의 `active()` 방식).

## 화면 주소

| 이름 | 주소 | 화면 |
| --- | --- | --- |
| `landing` | `/` | S-01 |
| `list` | `/cases` | S-02 |
| `overview` | `/cases/{caseId}/start` | S-03 |
| `review` | `/cases/{caseId}/review` | S-04 |
| `summary` | `/cases/{caseId}/summary` | S-05 |
| `verdict` | `/cases/{caseId}/verdict` | S-06 |
| `ai` · `court` · `comparison` | `/cases/{caseId}/result/ai` · `court` · `compare` | S-07 · S-08 · S-09 |
| `notFound` | 주소 없음 | S-14 |

## 에러 코드와 이동

| 에러 | 이동 |
| --- | --- |
| `INVALID_STATE` | 에러의 `currentStatus`에 맞는 화면 (`utils/screenByStatus.js`) |
| `EXPERIENCE_NOT_FOUND` | 사건 목록 (S-02) |
| `CASE_NOT_FOUND` | 404 · 오류 화면 (S-14). 단, 사건 목록의 `startExperience`는 화면이 직접 안내한다 |
| 그 밖 | 이동하지 않는다. 화면이 코드별로 안내한다 |

- 이동은 `replace`라서 뒤로 가기를 눌러도 같은 에러로 되돌아오지 않는다.
- **요청을 시작한 화면이 아직 보일 때만 이동한다.** 요청 중에 다른 화면으로 이동했다면(예: `← 목록`) 그 요청의 에러가 새 화면을 덮어쓰지 않는다. 요청 시작 시점의 이동 번호(`navigationToken`)를 저장했다가 에러가 왔을 때 지금 번호와 비교한다. 같은 화면에서 동시에 보낸 요청이 여러 개 실패해도 처음 실패한 요청만 이동시킨다.
- 에러는 이동한 뒤에도 그대로 다시 던진다. 화면이 자기 안내를 그려도 이미 화면에서 떼어진 뒤라 보이지 않는다.

## 사건 제목 (D-01 임시 방식)

S-04 이후 화면의 API에는 사건 제목이 없다. 목록 응답(API 1)에서 `caseId`로 찾고, 사건 개요(API 4)를 지나면 정확한 죄명으로 갱신한다. API가 제목을 주도록 정해지면 `caseHeader.js`의 `loadCaseHeader`만 바꾼다.
