# landing

**S-01 랜딩 · 홈** 화면이다.

| 파일 | 내용 |
| --- | --- |
| `landingPage.js` | `renderLandingPage(container, { navigate, section })` — 화면을 그린다 |
| `landing.css` | 랜딩 화면 스타일 |

- "사건 목록 보기" 버튼은 `navigate('list')`로 사건 목록(S-02)으로 이동한다.
- `section` 값(예: `'intro'`)을 받으면 해당 구역(서비스 소개)을 보여 준다. 공통 헤더의 "서비스 소개"가 `navigate('landing', { section: 'intro' })`로 이 값을 넘긴다.
- 공통 헤더 · 푸터는 [`components/siteLayout.js`](../../components/siteLayout.js)를 쓴다.
- 화면 구성과 문구는 [정보 구조](../../../../docs/information-architecture.md)와 [와이어프레임](../../../../docs/wireframe.pdf)을 기준으로 한다.
