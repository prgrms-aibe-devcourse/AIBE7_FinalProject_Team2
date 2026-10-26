# components

여러 화면이 함께 쓰는 공통 UI 모음이다. 프레임워크 없이 DOM을 직접 만든다.

| 파일 | 내용 | 쓰는 곳 |
| --- | --- | --- |
| `siteLayout.js` | DOM 헬퍼 `element(tag, className, text)` · `button(text, action, className)`, 공통 헤더 `renderSiteHeader`(라우터가 모든 화면 위에 붙인다, 마이페이지 · 관리자 제외 — FE-56), 공통 푸터 `renderSiteFooter` (법적 고지 포함, REQ-071), 목데이터 표기 `mockTag` | 전 화면 |
| `newspaperFront.js` | 신문 지면 공통 짜임: 제호 `newspaperMasthead`(S-02와 공용), 사건 기사 지면 `caseNewspaper`(1막 무대의 장면, 빈 자리 data-slot acts · board). 스타일은 caseList.css | S-02 · S-03 · S-20 |
| `folderParts.js` · `folderParts.css` | 서류철 부품(FE-96): 종이 귀 `paperFold({ flipped })`(접혀 들린 모서리 그림, 3초 일렁임 · 호버 들림 · 눌림. 버튼은 쓰는 곳이 만들고 크기는 `--fold-size`), 진행 책갈피 `folderBookmark()`(리본 + 마이페이지에서 고른 얼굴, 길이는 `--bookmark-length` · `--bookmark-hang`) | 판결 체험 파일철 · 성향 결과지 |
| `toleranceScale.js` | `renderToleranceScale(level)` — 관용 지수(0 ~ 4)를 "엄격함 ↔ 관대함" 양 끝 눈금 막대 위 점으로 보여 준다(FR-8-4, 숫자 "n / 4" 없음) | S-16 · S-17 |
| `lawInfoBox.js` | `renderLawInfoBox` — 적용 법률 · 법정형 · 선택한 형벌의 선고 가능 범위 박스 | S-04 · S-05 |
| `recommendedRangeBar.js` | `renderRecommendedRangeBar`, `formatMonths` — 양형기준 권고 범위와 선고 가능 범위, 입력한 형량을 막대로 보여 준다. `formatMonths`는 개월 수를 "2년 6개월"로 바꾼다 | S-04 · S-05 (`formatMonths`는 S-05) |

## 참고

- 형량은 서버에서 **개월**(징역) · **원**(벌금) 단위로 받는다. 화면에서 "년 · 개월"로 바꿔 보여 준다. (API 명세서 1-1)
- 이 폴더의 컴포넌트는 `container`와 옵션 객체를 받아 DOM을 붙이고, 값은 `textContent`로 넣는다.
