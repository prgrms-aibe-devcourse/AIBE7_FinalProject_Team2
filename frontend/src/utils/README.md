# utils

화면 여러 곳에서 쓰는 작은 유틸 모음이다.

| 파일 | 내용 |
| --- | --- |
| `contentLines.js` | 섹션 본문을 그린다. 줄바꿈이 있으면 줄마다 점 목록, 없으면 문단 하나 (FE-14, S-04 · S-05) |
| `screenByStatus.js` | 체험 상태(`status`)에 따라 **이어서 볼 화면**을 정하는 표. API 명세서 1-6 "체험 상태 → 화면"과 같은 값이다 |
| `newspaper.js` | 신문 콘셉트 화면(S-01 · S-02)이 함께 쓰는 지면 날짜 줄 `todayLabel`과 유형별 대체 이미지 `fallbackImages` (7.7.1) |
| `personalityAxes.js` | 가치관 4축(키 · 라벨 · 엄격 / 관대 글자). S-15 ~ S-17 화면과 성향 목 채점이 쓴다 |
| `motion.js` | 연출 켜기 · 끄기 (FE-50). `reducedMotion()`은 헤더의 "연출" 스위치(이 브라우저에 저장)나 기기의 동작 줄이기가 켜져 있으면 참이다. 모든 JS 연출이 이것을 본다. 스위치를 끄면 `<html data-motion="off">`가 되고 `style.css`가 CSS 애니메이션 · 전환 길이를 0으로 만든다 |
| `session.js` | 로그인한 사용자 (FE-52). 공통 헤더가 바로 읽도록 닉네임만 이 브라우저(`nlnb-session`)에 둔다. 로그인 · 로그아웃 자체는 `api.login` · `api.logout`(지금은 목업만)이 하고, 성공하면 화면이 `saveUser` · `clearUser`를 부른다 |
| `koreanParticle.js` | 한글 단어 뒤 목적격 조사(을 · 를) 판정. 백엔드 `KoreanParticle`과 같은 규칙 (FE-15, S-09) |

## screenByStatus

| 체험 상태 | 이동 화면 이름 |
| --- | --- |
| `STARTED` | `overview` (S-03) |
| `PRE_JUDGED` · `REVIEWING` | `review` (S-04) |
| `REVIEWED` | `summary` (S-05) |
| `VERDICT_CONFIRMED` | `ai` |
| `AI_REVEALED` | `court` |
| `COMPLETED` | `comparison` |

- 사건 목록에서 체험을 시작할 때(API 2 응답의 `status`)와, 공통 래퍼(`app/errorRedirect.js`)가 `INVALID_STATE`를 받았을 때 이 표로 화면을 정한다. 각 화면은 상태 불일치로 직접 이동하지 않는다.
- `ai` · `court` · `comparison` 화면 파일은 아직 없다. 해당 화면을 만들 때 이 이름에 맞춘다.
- API 명세서 1-6이 바뀌면 이 표도 함께 바꾼다.
