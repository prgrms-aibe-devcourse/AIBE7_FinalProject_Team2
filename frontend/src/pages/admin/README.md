# admin

**S-19 관리자 검수** 화면이다 (FE-16, WBS 7.5.7). 자동 적재한 사건(`DRAFT`) · 판결 후보(`PENDING`)를 검수해 공개하고(FE-16), 원본 판결문으로 사건을 등록한다(FE-45). API는 [API 명세 8장](../../../../docs/api-specification.md) A-1 ~ A-15.

| 파일 | 내용 |
| --- | --- |
| `adminShell.js` | 화면 틀. 로그인 확인(A-4) → 로그인 폼(A-1 · A-2) · 로그아웃(A-3) · 세션 만료 시 다시 로그인 |
| `adminListPage.js` | `/admin` 검수 사건 목록(A-5). 검수할 사건 · 공개 사건의 새 후보 · 공개한 사건 탭, 20건씩 |
| `adminCasePage.js` | `/admin/cases/{caseId}` 상세(A-6). 공개 준비 · 체크리스트(A-13) → 판결 후보 자세히(생성 정보: 회차 선택 근거 · 사전 학습 점검 판정 · 검증 경고 · 참고 자료 검색 · AI 입력) · 승인 · 반려(A-8 ~ A-10) → 판단 요소 가치관 축 확정(A-12) → 사건 내용 수정(A-11) → 원본 판결문(A-7) |
| `adminRegisterPage.js` | `/admin/register` 사건 등록(A-14 · A-15). 원본 판결문(최대 3건, 심급별)을 파일(PDF · txt) 업로드 · 붙여넣기 · 직접 입력으로 넣으면 원문에서 항목이 자동으로 채워지고 → 파이프라인 단계별 진행 → 완료되면 검수 화면으로 |
| `adminPdf.js` · `adminPdfLoader.js` | PDF에서 글자 뽑기(pdf.js legacy 빌드). 브라우저에서만 읽고 PDF 파일은 서버로 보내지 않는다. 로더는 PDF를 처음 고를 때 pdf.js를 불러온다 |
| `adminJudgmentParse.js` | 원문에서 사건번호 · 법원명 · 선고일 · 심급 · 범죄 유형을 읽어 폼을 채운다 |
| `samples/sample-judgment.pdf` | 가상 판결문 예시 PDF(2쪽). 등록 화면의 "예시 PDF 내려받기"와 PDF 읽기 테스트가 쓴다. 실제 판결문이 아니다 |
| `adminFormat.js` | 요약어 후보(A-6)와 생성 정보(A-8 `generation`)를 화면용으로 푸는 순수 함수. 테스트 있음 |
| `adminMenu.js` · `adminKit.js` | 왼쪽 메뉴 목록 · 운영 메뉴 화면이 함께 쓰는 부품(검색줄 · 표 · 쪽 나누기 · 사유 폼 · 목록 불러오기) |
| `adminDashboardPage.js` · `adminMembersPage.js` · `adminPostsPage.js` · `adminReportsPage.js` · `adminAccountsPage.js` · `adminNoticesPage.js` | 운영 메뉴(A-16 ~ A-41 초안): 대시보드 · 회원 · 게시글 · 댓글 · 신고 · 관리자 계정 · 감사 로그 · 공지 · 금칙어. **서버 API가 없어 목으로만 동작** |
| `adminClient.js` | 관리자 API 전용 fetch. 세션 쿠키 · CSRF 헤더(`X-XSRF-TOKEN`) · 403이면 토큰을 새로 받아 한 번 재시도. 공용 `src/api/client.js`는 쓰지 않는다 |
| `adminRealApi.js` · `adminMockApi.js` (운영 메뉴는 `adminOps*Api.js`를 다시 내보낸다) | 실제 · 목 API (함수 이름 · 응답 형식 같음). `adminApi.js`가 `VITE_API_MODE`로 고른다 |
| `adminLabels.js` | 상태 · 사유 코드 → 화면 문구, 에러 문구 |
| `admin.css` | 관리자 화면 스타일. `adminShell.js`가 불러와 관리자 청크에만 들어간다 |

## 원칙

- **사용자 화면과 분리한다.** 사용자 api(`src/api`)와 에러 이동(`app/errorRedirect.js`)을 쓰지 않는다. 관리자 코드 · CSS는 `/admin`으로 들어올 때만 불러온다(`routes.js` 동적 import).
- **내부 정보를 밖에 남기지 않는다.** 원본 판결문 · 생성 정보는 화면에만 그리고 브라우저 저장소에 넣지 않는다. 요청은 `cache: 'no-store'`. 목도 상태를 메모리에만 두고 로그인 여부만 `sessionStorage`에 둔다.
- 값은 모두 `textContent`로 넣는다(원문 · 모델 출력에 HTML이 섞여도 실행되지 않음).
- 상태를 바꾼 뒤에는 A-6을 다시 불러와 전체를 다시 그린다(사건 상태 · 공개 조건이 함께 바뀜).
- 사건 등록(FE-45)은 파이프라인을 서버가 실행하는 방식이다. 판단 요소 구성 · 형벌 규칙은 여전히 파이프라인 · SQL 경로이고 이 화면은 검수 · 수정 · 공개 · 등록 시작만 한다.
- 요약어 후보는 **제안만** 보여 준다. 요약어 수정 API가 없어서 바꾸는 버튼은 없다.

## 목으로 확인

`npm run dev` → `http://localhost:5173/admin`. 아무 이메일 · 비밀번호로 로그인된다(비밀번호 `wrong`이면 실패). 사건 등록은 `/admin/register`(목록의 "+ 사건 등록")에서 해 볼 수 있고, 원문에 `[실패]`를 넣으면 실패 화면을 볼 수 있다. "예시 PDF 내려받기"로 받은 PDF를 다시 넣으면 원문과 항목이 자동으로 채워진다. 사건 #12(적재됨) · #13(공개됨, 새 AI 후보 1건)이 있고, 새로고침하면 처음 상태로 돌아간다.

## 실제 API

A-1 ~ A-13은 BE-39 · BE-41 ~ BE-43으로 구현돼 있다. `VITE_API_MODE=real`로 연결해 확인한다. **A-14 · A-15(사건 등록)와 A-16 ~ A-41(운영 메뉴)은 초안 계약이라 서버 구현 전에는 목으로만 동작한다.** 서버를 만드는 쪽은 [API 명세 8-4](../../../../docs/api-specification.md)를 기준으로 한다.
