# caseReview

**S-09 리뷰 페이지(독자 엽서)** 화면이다 (FE-55, 화면 ID는 BE-82). 지금 주소는 `/cases/:caseId/opinion`이고 `/letters`로 옮기는 일은 FE-58이다. 6막(대본 8막) 마지막 대사창의 "엽서 쓰기", 완료 사건의 사건 디테일 · 마이페이지 "독자 엽서 쓰기"로 들어온다.

| 파일 | 내용 |
| --- | --- |
| `caseReviewPage.js` | `renderCaseReviewPage(container, { caseId, api, navigate })` |
| `caseReview.css` | 엽서(항공우편 줄무늬) · 별점 · 접수 도장 스타일 |

## 구성

- 공통 헤더 → 제목 · 평균 별점 → **내 엽서**(별점 + 내 의견 + 보내기) → 다른 독자의 엽서 목록.
- 별점은 라디오 5개(키보드 방향키 · 스크린리더 "N점 …"). 내 의견은 1 ~ 200자.
- 사건마다 엽서는 한 장이고 보낸 뒤에는 바꿀 수 없다(사전 판단 엽서와 같은 규칙). 보내면 접수 도장이 찍히고, 다시 들어오면 보낸 엽서가 보인다.
- **리뷰 API가 명세에 아직 없다.** 목(`mocks/caseReviewMockApi.js`: `getCaseReviews` · `postCaseReview`)으로만 동작하고, 실제 모드에는 `api.getCaseReviews`가 없어 "준비 중"으로 안내한다. API가 생기면 `realApi.js`에 같은 이름 · 응답 모양(`{ count, averageRating, mine, reviews }`)으로 붙인다.
