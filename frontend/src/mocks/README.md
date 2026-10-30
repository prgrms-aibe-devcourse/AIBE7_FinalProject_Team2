# mocks

백엔드 API가 준비되기 전에 화면을 만들고 확인하려고 쓰는 **목(mock) API와 목 데이터**다. HTTP 요청을 보내지 않는다.

## 파일

| 파일 | 대응 API | 내용 |
| --- | --- | --- |
| `caseListMockApi.js` · `caseListMockData.js` | API 1 · 2 · 3 | 사건 목록, 체험 시작, 내 체험 상태(S-09 진입 확인, 기록을 만들지 않는다). `caseId` 1이 가상 살인 사건이고 2 ~ 9는 임의 데이터. `scriptReady`(BE-116, FE-125)는 대본이 있는 사건 1만 `true`. 목록의 `myStatus`(FE-19 배지)는 체험 시작과 같은 저장소에서 계산한다: 실제 API처럼 체험 기록이 없으면 `null`("체험 시작"). `caseId` 1은 `mockExperienceStore`에 값이 생기면(체험 시작 · 다른 목 화면 진입 시 저장) 진행 중, `setMockStatus('COMPLETED')`면 완료. 나머지는 이번 세션에 시작한 사건만 진행 중 |
| `overviewMockApi.js` · `overviewMockData.js` | API 4 · 5 | 사건 개요 · 형량 구간, 사전 판단 제출 |
| `reviewMockApi.js` · `reviewMockData.js` | API 6 · 7 | 사건 정보 섹션, 섹션 확인 기록 |
| `verdictMockApi.js` · `verdictMockData.js` | API 8 · 9 | 판결 입력 정보, 판결 제출 (감경 `reducedTo` 검증 포함) |
| `personalityMockApi.js` · `personalityMockData.js` | 성향 API P-1 ~ P-5 (명세 9장) | 문항 조회 · 응답 제출(채점은 personality-scoring.md 2장) · 내 결과(없으면 404) · 공유용 유형 설명(`shareId`로 조회, 없으면 404) · 마이페이지(한 번 호출, 기록이 없어도 빈 200). 문항 16개 · 유형 16개는 personality-test-content.md에서 옮긴 값이다. 결과는 `localStorage`(`nlnb-mock-personality`)에 둔다 |
| `levelMockApi.js` | L-1 · L-2와 P-5 `level` (BE-106, FE-114) | 서버와 같은 레벨 곡선(k=0.08, 총 10000 XP) · 직업 11등급 · 잠김(409 `JOB_LOCKED`). Lv.7 · 무직에서 시작하고, 개발 전용 `setMockLevel` · `unlockMockNextJob` · `resetMockLevel`은 프로필카드 화면의 "개발용" 버튼이 쓴다. 저장값은 `localStorage`(`nlnb-mock-level`) |
| `communityMockData.js` 글쓴이 | 커뮤니티 글쓴이 · 랭킹 모양 (FE-116) | `level` · `job { code, name }` · `cardLine { line, showLevel }`를 서버 계약 모양으로 둔다. 커뮤니티 API가 생기면 같은 모양으로 오도록 BE에 요청 중. 내 글은 내 레벨 · 직업 · 카드 아래 줄 설정을 쓴다 |
| `profileDecoMockApi.js` · `profileDecoMockData.js` | P-5 꾸미기 부분 · P-6 · P-7 (BE-96 · BE-97, FE-71) | 서버가 하는 일을 흉내 낸다: 출석 기록(하루 한 번, 이전 11일 + 기록한 날), 꾸미기 전체 저장(값 목록 · 대표 이미지 형식 400 → 잠김 409), 마이페이지에 붙는 `profileDeco` · `achievements` · `unlocked`. 도전과제 · 보상 표(서버 쪽 표)는 `profileDecoMockData.js`에 있고 서버 표를 바꾸면 같이 고친다. 수집 도전과제는 서버처럼 `PENDING`이다. 저장값 · 출석 날짜는 `localStorage`(`nlnb-mock-profile-deco` · `nlnb-mock-attendance`) |
| `authMockApi.js` | 사용자 로그인 (아직 API 없음, FE-52) | 개발용 목업 로그인 · 로그아웃. 어떤 아이디 · 비밀번호든 로그인되고 닉네임은 아이디의 @ 앞부분, 카카오는 "카카오 판사". 계정 · 비밀번호는 저장하지 않는다. 로그인 상태(닉네임)는 화면이 `utils/session.js`에 둔다 |
| `communityMockApi.js` · `communityMockData.js` | 커뮤니티 (실제 API는 BE-115 · FE-137, 목은 화면 확인용) | 글 목록(말머리 · 사건 거르기, 최신 · 좋아요 · 댓글순, 쪽마다 6개) · 글 상세 · 글쓰기(사진 최대 3장 · 내 판결 붙이기) · 좋아요 켜고 끄기 · 댓글. 다른 사람 글은 고정 값, 내가 쓴 글 · 댓글 · 좋아요는 `localStorage`(`nlnb-mock-community`). 쓰기 · 댓글 · 좋아요는 목업 로그인이 필요하고(`UNAUTHORIZED`, 서버와 같은 코드), 내 판결은 S-05 판결 확정 뒤에만 붙는다(AI · 실제 판결은 붙지 않는다). 커뮤니티 메인(FE-89): 이야기 많은 사건 셋 · 추천 사건 셋 · 레벨 랭킹 열 명(`getCommunityHome`) |
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
