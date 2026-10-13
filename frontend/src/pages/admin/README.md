# admin

**S-19 관리자 검수** 화면이다 (FE-16, WBS 7.5.7). 자동 적재한 사건(`DRAFT`) · 판결 후보(`PENDING`)를 검수해 공개한다. API는 [API 명세 8장](../../../../docs/api-specification.md) A-1 ~ A-13.

| 파일 | 내용 |
| --- | --- |
| `adminShell.js` | 화면 틀. 로그인 확인(A-4) → 로그인 폼(A-1 · A-2) · 로그아웃(A-3) · 세션 만료 시 다시 로그인 |
| `adminListPage.js` | `/admin` 검수 사건 목록(A-5). 검수할 사건 · 공개 사건의 새 후보 · 공개한 사건 탭, 20건씩 |
| `adminCasePage.js` | `/admin/cases/{caseId}` 상세(A-6). 공개 준비 · 체크리스트(A-13) → 판결 후보 자세히 · 승인 · 반려(A-8 ~ A-10) → 판단 요소 가치관 축 확정(A-12) → 사건 내용 수정(A-11) → 원본 판결문(A-7) |
| `adminClient.js` | 관리자 API 전용 fetch. 세션 쿠키 · CSRF 헤더(`X-XSRF-TOKEN`) · 403이면 토큰을 새로 받아 한 번 재시도. 공용 `src/api/client.js`는 쓰지 않는다 |
| `adminRealApi.js` · `adminMockApi.js` | 실제 · 목 API (함수 이름 · 응답 형식 같음). `adminApi.js`가 `VITE_API_MODE`로 고른다 |
| `adminLabels.js` | 상태 · 사유 코드 → 화면 문구, 에러 문구 |
| `admin.css` | 관리자 화면 스타일. `adminShell.js`가 불러와 관리자 청크에만 들어간다 |

## 원칙

- **사용자 화면과 분리한다.** 사용자 api(`src/api`)와 에러 이동(`app/errorRedirect.js`)을 쓰지 않는다. 관리자 코드 · CSS는 `/admin`으로 들어올 때만 불러온다(`routes.js` 동적 import).
- **내부 정보를 밖에 남기지 않는다.** 원본 판결문 · 생성 정보는 화면에만 그리고 브라우저 저장소에 넣지 않는다. 요청은 `cache: 'no-store'`. 목도 상태를 메모리에만 두고 로그인 여부만 `sessionStorage`에 둔다.
- 값은 모두 `textContent`로 넣는다(원문 · 모델 출력에 HTML이 섞여도 실행되지 않음).
- 상태를 바꾼 뒤에는 A-6을 다시 불러와 전체를 다시 그린다(사건 상태 · 공개 조건이 함께 바뀜).
- 사건 등록은 없다. 등록은 파이프라인 · SQL 경로(WBS 7-5 8순위), 이 화면은 검수 · 수정 · 공개만 한다.

## 목으로 확인

`npm run dev` → `http://localhost:5173/admin`. 아무 이메일 · 비밀번호로 로그인된다(비밀번호 `wrong`이면 실패). 사건 #12(적재됨) · #13(공개됨, 새 AI 후보 1건)이 있고, 새로고침하면 처음 상태로 돌아간다.

## 실제 API

A-1 ~ A-4는 BE-39로 구현돼 있다. A-5 ~ A-13은 7.4.7(BE-41 ~ BE-43, 10/21 ~ 23) 구현 뒤 `VITE_API_MODE=real`로 연결해 확인한다.
