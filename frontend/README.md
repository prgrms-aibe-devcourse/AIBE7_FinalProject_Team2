# 내Law남불 Frontend

HTML + JavaScript (개발 서버와 빌드는 Vite 사용). 프레임워크 없이 화면 단위 모듈로 나눠 만든다.

## 실행

```bash
npm install
npm run dev     # http://localhost:5173 (/api 요청은 localhost:8080 백엔드로 프록시)
npm run build   # dist/ 에 배포용 파일 생성
```

## API 모드

화면이 쓰는 API를 환경변수로 고른다. 화면 코드는 어느 쪽인지 몰라도 된다.

| 모드 | 실행 | 설명 |
| --- | --- | --- |
| `mock` (기본) | `npm run dev` | HTTP 요청 없이 목 데이터로 동작. 백엔드 없이 화면 확인 |
| `real` | `VITE_API_MODE=real npm run dev` | 실제 백엔드로 요청. 백엔드를 `8080`에서 먼저 실행 |

- 실제 모드에서 개발 서버가 `/api`를 백엔드로 넘기므로 CORS 설정이 필요 없고, 익명 ID 쿠키(`NLNB_AID`)도 그대로 붙는다.
- 실제 백엔드의 사건 ID는 시드 적재 순서에 따라 `1`이 아닐 수 있다. 사건 목록(API 1)에서 확인한다.
- 백엔드에 아직 없는 API(10 ~ 14)는 실제 모드에서 AI 판결 · 실제 판결 · 비교 화면이 오류 안내를 보여 준다.

## 환경변수

`.env.example`을 복사해 `.env.local`로 사용한다. `.env.local`은 커밋되지 않는다.

| 변수 | 기본값 | 설명 |
| --- | --- | --- |
| `VITE_API_BASE_URL` | `/api/v1` | API 기본 경로 |
| `VITE_API_MODE` | `mock` | `mock` 또는 `real` |

## 폴더

| 폴더 | 내용 |
| --- | --- |
| [`src/app/`](src/app/README.md) | 라우터 · 화면 표 · 에러 시 화면 이동 · 사건 제목 · 앱 api 조립 |
| [`src/api/`](src/api/README.md) | `fetch` 공통 함수와 실제 API, 목 ↔ 실제 선택 |
| `src/pages/` | 화면 (S-01 ~ S-09). S-14 오류 화면은 사건 맥락이 없어 `src/app/notFoundPage.js`에 둔다 |
| [`src/components/`](src/components/README.md) | 여러 화면이 쓰는 공통 UI |
| [`src/mocks/`](src/mocks/README.md) | 백엔드 없이 화면을 만들 때 쓰는 목 API · 데이터 |
| [`src/utils/`](src/utils/README.md) | 작은 유틸 |

## 반응형 기준

- MVP는 **모바일에서 깨지지 않는 수준**만 대응한다(REQ-004 기본). 390px · 360px에서 가로 스크롤이 생기지 않고 내용이 겹치지 않으면 된다. 390px 전용 레이아웃은 확장 단계에서 한다.
- 화면별 조정은 각 화면 CSS의 `@media`에, 여러 화면이 함께 쓰는 규칙은 `src/style.css`에 둔다.

## 가이드 · 미리보기

- `http://localhost:5173/sample.html` — **연동 확인 페이지.** 사건을 골라 화면 주소를 열어 보고, API를 직접 호출해 응답 · 에러를 확인하고, 목 모드에서 체험 상태를 바꿔 화면 이동을 확인한다
- `http://localhost:5173/preview.html` — 화면별 개발용 미리보기 (팀원 화면 작업용, 라우터 없이 화면만 확인)
