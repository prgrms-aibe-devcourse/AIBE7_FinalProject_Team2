# components

여러 화면이 함께 쓰는 공통 UI 모음이다. 프레임워크 없이 DOM을 직접 만든다.

| 파일 | 내용 | 쓰는 곳 |
| --- | --- | --- |
| `siteLayout.js` | DOM 헬퍼 `element(tag, className, text)` · `button(text, action, className)`, 공통 헤더 `renderSiteHeader`, 공통 푸터 `renderSiteFooter` (법적 고지 포함, REQ-071) | S-01 · S-02 |
| `lawInfoBox.js` | `renderLawInfoBox` — 적용 법률 · 법정형 · 선택한 형벌의 선고 가능 범위 박스 | S-04 · S-05 · S-06 |
| `recommendedRangeBar.js` | `renderRecommendedRangeBar`, `formatMonths` — 양형기준 권고 범위와 선고 가능 범위, 입력한 형량을 막대로 보여 준다. `formatMonths`는 개월 수를 "2년 6개월"로 바꾼다 | S-04 · S-05 · S-06 (`formatMonths`는 S-06) |

## 참고

- 형량은 서버에서 **개월**(징역) · **원**(벌금) 단위로 받는다. 화면에서 "년 · 개월"로 바꿔 보여 준다. (API 명세서 1-1)
- 이 폴더의 컴포넌트는 `container`와 옵션 객체를 받아 DOM을 붙이고, 값은 `textContent`로 넣는다.
