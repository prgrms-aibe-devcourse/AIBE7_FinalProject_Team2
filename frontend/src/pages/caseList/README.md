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
| `api.getCases(crimeType)` | API 1 `GET /cases` | 사건 목록. 범죄 유형 탭(`MURDER` · `FRAUD` · `INJURY`)으로 필터 |
| `api.startExperience(caseId)` | API 2 `POST /cases/{caseId}/experience` | 체험 시작. 응답의 `status`로 이어서 볼 화면을 정해 이동 |

- 체험 시작 뒤 이동할 화면은 [`utils/screenByStatus.js`](../../utils/README.md)로 정한다. 이미 체험이 있는 사건이면 그 상태의 화면으로 간다.
- 신문 1면 구성(FE-18, 시안 v12): 참여 많은 순으로 첫 사건이 머리기사(배너 제목 · 메인 사진 · 오른쪽 머리기사 단), 다음 2건이 추천 사건, 나머지는 "이번 호 다른 사건" 띠로 간다. 사건 수가 적거나 유형 탭으로 거르면 있는 만큼만 그린다.
- **공개 범위**: 목록은 처음 판단(S-03) 전 화면이라 사건 정보는 제목 · `shortIntro` · `keywords` · `crimeCategoryLabel` · `participantCount`만 쓴다. 상세 사실 · 판단 요소 · 양측 주장 · 형량은 쓰지 않는다. 머리기사 안내 글과 추천 사건의 [알아두기]는 사건과 무관한 고정 문구(`LEAD_GUIDE` · `TYPE_NOTES`).
- 기사 제목 · 부제 · 본문은 `headline` · `deck` · `article`(문자열 배열)을 쓴다. **API 1에 아직 없는 제안 필드**라 지금은 목 데이터에만 있고, 값이 없으면 사건 제목 · `shortIntro`와 고정 문구로 채운다. 본문은 S-03 사건 개요 수준의 사실만 쓴다.
- 이미지가 없으면(`thumbnailUrl`이 `null`) 유형별 연출 이미지(`public/images/case-*.svg`)를 쓴다. 난이도 · 예상 시간은 표시하지 않는다.
- 공통 헤더 대신 신문 제호를 그리고, 법적 고지는 공통 푸터([`components/siteLayout.js`](../../components/siteLayout.js))를 지면 맨 아래에 붙인다.
- 요청 · 응답 형식은 [API 명세서](../../../../docs/api-specification.md) API 1 · 2를 따른다.
