# utils

화면 여러 곳에서 쓰는 작은 유틸 모음이다.

| 파일 | 내용 |
| --- | --- |
| `contentLines.js` | 섹션 본문을 그린다. 줄바꿈이 있으면 줄마다 점 목록, 없으면 문단 하나 (FE-14, S-04) |
| `scriptReady.js` | `isScriptReady(item, localScript)`: 체험할 수 있는 사건인가 (FE-125). 서버 `scriptReady`(API 1 · 16)가 있으면 그 값, 없으면 로컬 대본 유무. 사건 목록 · S-20 · 커뮤니티 메인이 같은 기준을 쓴다 |
| `screenByStatus.js` | 체험 상태(`status`)에 따라 **이어서 볼 화면**을 정하는 표. API 명세서 1-6 "체험 상태 → 화면"과 같은 값이다 |
| `newspaper.js` | 신문 콘셉트 화면(S-01 · S-02)이 함께 쓰는 지면 날짜 줄 `todayLabel`과 유형별 대체 이미지 `fallbackImages` (7.7.1) |
| `personalityAxes.js` | 가치관 4축(키 · 라벨 · 엄격 / 관대 글자). S-15 ~ S-17 화면과 성향 목 채점이 쓴다 |
| `motion.js` | 연출 켜기 · 끄기 (FE-50). `reducedMotion()`은 헤더의 "연출" 스위치(이 브라우저에 저장)나 기기의 동작 줄이기가 켜져 있으면 참이다. 모든 JS 연출이 이것을 본다. 스위치를 끄면 `<html data-motion="off">`가 되고 `style.css`가 CSS 애니메이션 · 전환 길이를 0으로 만든다 |
| `session.js` | 로그인한 사용자 (FE-52). 공통 헤더가 바로 읽도록 닉네임만 이 브라우저(`nlnb-session`)에 둔다. 로그인 · 로그아웃 자체는 `api.login` · `api.logout`(지금은 목업만)이 하고, 성공하면 화면이 `saveUser` · `clearUser`를 부른다 |
| `pageTurn.js` | 신문 한 장 넘기기(FE-32, 2.5D). 지금 지면을 본뜬 한 장이 왼쪽 가장자리를 축으로 넘어가고, 그 밑에 `draw()`가 다음 면을 그린다. `back`이면 거꾸로 앞 면이 왼쪽에서 넘어와 덮는다(FE-118). S-02 면 넘기기 전용 |
| `articleZoom.js` | 기사 → 사건 정보 제목 확대(FE-118, 공유 요소 전환). S-02가 `liftArticle`로 지면을 본떠 띄우고, S-20이 `landArticle(제목)`으로 받으면 원래 제목 자리에서 새 제목 자리로 글자가 날아간다. 받지 않으면 1.5초 뒤 스스로 걷힌다 |
| `newspaperFaces.js` | S-02 면 넘기기 · 빈 칸 계산(FE-118): 앞 · 뒤 면 `faceIndexAfter`, 손가락 밀기 판정 `swipeStep`, "이번 호 다른 사건" 빈 칸 수 `emptySlots`, 보여 줄 면 `visibleFaces(faces, summary, selected)`(면 정의의 `hideWhenEmpty` · `count`를 따른다, FE-123 — 면 정의는 [`pages/caseList/faces.js`](../pages/caseList/faces.js)). DOM을 쓰지 않아 node 테스트로 확인한다 |
| `koreanParticle.js` | 한글 단어 뒤 목적격 조사(을 · 를) 판정. 백엔드 `KoreanParticle`과 같은 규칙 (FE-15, S-08) |

## screenByStatus

| 체험 상태 | 이동 화면 이름 |
| --- | --- |
| `STARTED` | `overview` (S-03) |
| `PRE_JUDGED` · `REVIEWING` | `review` (S-04) |
| `REVIEWED` | `verdict` (S-05) |
| `VERDICT_CONFIRMED` | `ai` (S-06) |
| `AI_REVEALED` | `court` (S-07) |
| `COMPLETED` | `comparison` (S-08) |

- 사건 목록에서 체험을 시작할 때(API 2 응답의 `status`)와, 공통 래퍼(`app/errorRedirect.js`)가 `INVALID_STATE`를 받았을 때 이 표로 화면을 정한다. 각 화면은 상태 불일치로 직접 이동하지 않는다.
- API 명세서 1-6이 바뀌면 이 표도 함께 바꾼다.
