# caseList

**S-02 사건 목록** 화면이다.

| 파일 | 내용 |
| --- | --- |
| `caseListPage.js` | `renderCaseListPage(container, { api, navigate })` — 목록을 그리고 체험 시작을 처리한다 |
| `caseList.css` | 사건 목록 화면 스타일 |

## 사용하는 API

`api` 객체로 주입받는다. (지금은 [`mocks/`](../../mocks/README.md)의 목 구현)

| 호출 | API | 동작 |
| --- | --- | --- |
| `api.getCases(crimeType)` | API 1 `GET /cases` | 사건 목록. 범죄 유형 칩(`MURDER` · `FRAUD` · `INJURY`)으로 필터 |
| `api.startExperience(caseId)` | API 2 `POST /cases/{caseId}/experience` | 체험 시작. 응답의 `status`로 이어서 볼 화면을 정해 이동 |

- 체험 시작 뒤 이동할 화면은 [`utils/screenByStatus.js`](../../utils/README.md)로 정한다. 이미 체험이 있는 사건이면 그 상태의 화면으로 간다.
- 카드의 분류명은 응답의 `crimeCategoryLabel`을 그대로 쓰고, 이미지가 없으면(`thumbnailUrl`이 `null`) 분류명으로 대신한다.
- 공통 헤더 · 푸터는 [`components/siteLayout.js`](../../components/siteLayout.js)를 쓴다.
- 요청 · 응답 형식은 [API 명세서](../../../../docs/api-specification.md) API 1 · 2를 따른다.
