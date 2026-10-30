# COMMON-70 의사결정 기록

> 담당 정민석 (D) · 관련: COMMON-67(보안 헤더, CSP를 후속으로 뺌), BE-57(보안 헤더 설계, docs/tech-stack.md 3장)

## 문제 / 배경
- COMMON-67에서 Nginx 화면 · 정적 파일 응답에 보안 헤더 4종을 넣고, CSP는 "출처 목록 · 인라인 사용 여부를 먼저 정리해야 하고 잘못 넣으면 화면이 깨진다"는 이유로 뺐다.
- CSP는 브라우저가 허용한 출처의 스크립트 · 스타일 · 글꼴 · 이미지만 불러오게 해, 페이지에 스크립트가 끼어들어도(XSS) 실행을 막는 2차 방어선이다. 지금도 사용자 입력은 `textContent`로만 그려 위험은 낮다 — 급한 작업은 아니다.
- **main 머지 = 운영 자동 배포**라, 막는 정책을 바로 넣으면 놓친 출처 하나로 운영 화면이 깨진다.

## 검토한 대안

### 1. 어떻게 켤까
- 대안 A: `Content-Security-Policy`를 바로 넣는다. 로컬에서 본 화면만으로는 API 데이터가 있어야 나오는 출처(관리자가 만든 사건 사진 등)를 다 확인할 수 없다.
- **대안 B(선택): 두 단계.** 1단계는 `Content-Security-Policy-Report-Only`(막지 않고 위반만 콘솔에 남김)로 운영에 내보내 주요 화면을 돌며 위반을 모으고, 위반이 없으면 2단계에서 헤더 이름만 `Content-Security-Policy`로 바꾼다.

### 2. 위반을 어디서 모을까
- 대안 A: `report-to` / `report-uri` 수집 엔드포인트를 백엔드에 만든다. 엔드포인트 · 저장 · 남용 제한이 새로 필요하다.
- **대안 B(선택): 엔드포인트 없이 브라우저 콘솔.** 1단계는 팀이 운영 화면을 직접 돌며 확인하는 기간이라 콘솔이면 충분하다. 사용자 브라우저에서 생기는 위반까지 모아야 하면 그때 엔드포인트를 검토한다.

### 3. style-src에 'unsafe-inline'이 필요한가
- JS에서 요소 스타일을 지정하는 곳이 100곳 이상이지만 모두 `el.style.x = …`(CSSOM)이라 CSP의 인라인 스타일 제한 대상이 아니다. `style="…"` 속성 · `setAttribute('style')` · `<style>` 요소는 화면 코드에 없다(innerHTML로 넣는 SVG 5곳에도 없음).
- **선택: 'unsafe-inline' 없이 시작한다.** 1단계에서 위반이 나오면 그때 원인을 보고 판단한다.

## 정책 (1단계, `frontend/nginx/snippets/security-headers.conf`)

| 지시어 | 값 | 이유 |
| --- | --- | --- |
| `default-src` | `'self'` | 아래에 없는 종류는 같은 출처만 |
| `script-src` | `'self'` | 빌드된 `index.html`은 모듈 스크립트 파일만 부른다. 인라인 `<script>` · `eval` · `new Function` 없음(번들 확인). pdf.js도 `isEvalSupported: false` |
| `style-src` | `'self'` `https://fonts.googleapis.com` `https://cdn.jsdelivr.net` | 글꼴 CSS — Google Fonts(나눔명조 · 나눔펜 · Noto Serif KR), Pretendard(jsDelivr) |
| `font-src` | `'self'` `https://fonts.gstatic.com` `https://cdn.jsdelivr.net` | 위 글꼴 CSS가 부르는 글꼴 파일 |
| `img-src` | `'self'` `https://*.s3.ap-northeast-2.amazonaws.com` | 정적 이미지(`/images`) + 관리자가 만든 사건 사진(S3 서울 리전, `CasePhotoService` → `S3Client.utilities().getUrl()`의 버킷 주소). 버킷 이름은 서버 `.env`에만 있어 와일드카드로 둔다. `data:` · `blob:` 이미지는 쓰지 않는다 |
| `connect-src` | `'self'` | API는 같은 출처 `/api/v1`(`VITE_API_BASE_URL` 기본값) |
| `worker-src` | `'self'` | 관리자 사건 등록 PDF 읽기 — pdf.js 워커 파일(빌드 산출물, `adminPdfLoader.js`) |
| `object-src` | `'none'` | 플러그인 콘텐츠 없음 |
| `base-uri` | `'self'` | `<base>` 바꿔치기 방지 |
| `form-action` | `'self'` | 폼은 모두 JS로 제출(같은 출처 API). 카카오 로그인은 폼이 아니라 같은 출처 링크 → 서버 리다이렉트라 영향 없음 |
| `frame-ancestors` | `'none'` | `X-Frame-Options: DENY`와 같은 뜻 |

- 붙는 곳은 `security-headers.conf`를 include하는 응답 전부다: 화면(`/`) · `/assets/` · `/version.json` · Nginx가 직접 만드는 429 JSON(`@api_rate_limited`). 백엔드가 내려주는 `/api` 응답에는 붙이지 않는다(JSON 응답이라 의미가 없고, 기존 보안 헤더처럼 백엔드 영역이다). 429 JSON에 붙는 것은 문서로 읽히지 않아 영향이 없다.
- S3 주소는 SDK 기본값인 가상 호스트 방식(`https://<버킷>.s3.ap-northeast-2.amazonaws.com/…`)만 허용했다. 버킷 이름에 점(`.`)이 있거나 경로 방식(`forcePathStyle`)으로 바꾸면 `https://s3.ap-northeast-2.amazonaws.com`이 필요하다.
- 오디오 · 영상 · iframe · 웹 매니페스트는 쓰지 않아 `default-src 'self'`로 둔다.

## 확인
- 로컬에서 web 이미지를 빌드해 띄움: `nginx -t` 통과, `/` · `/assets/` 응답에 `Content-Security-Policy-Report-Only` 한 번, 스모크 테스트 `SMOKE_REQUIRE_SECURITY_HEADERS=1`로 통과.
- 같은 이미지로 메인 · 사건 목록 · 사건 상세 · 성향 테스트(일반 · 신문) · 로그인 · 가입 · 커뮤니티 · 마이페이지 · 관리자 로그인 화면을 열어 콘솔에 CSP 위반이 없음을 확인했다. 정책에 없는 외부 이미지를 일부러 불러 위반이 콘솔에 남는 것도 확인했다(감지가 동작함).
- 백엔드 없이 띄워 API 데이터가 있어야 나오는 것(S3 사건 사진 · 체험 무대 · 관리자 PDF 워커)은 확인하지 못했다 → 1단계 운영 확인에서 본다.

## 다음 단계 (2단계 전환 조건)
1. 이 PR이 main으로 나간 뒤 운영에서 주요 화면을 돌며 콘솔의 `Content Security Policy` 위반을 모은다: 메인 · 사건 목록/상세 · 체험 전 단계(1 ~ 6막) · 성향 테스트 · 마이페이지 · 커뮤니티(사진 포함) · 로그인/가입 · 카카오 로그인 · 관리자(사건 검수 · 사건 등록 PDF 읽기 · 사진 생성).
2. 위반이 있으면 정책을 보정하고 이 표를 고친다.
3. 위반이 없으면 `add_header Content-Security-Policy-Report-Only` → `Content-Security-Policy`로 이름만 바꾼다(스모크 테스트는 두 이름 모두 통과).

## 추후 재검토가 필요하면
- 외부 스크립트(분석 도구 · 지도 · 카카오 SDK 등)나 외부 이미지 출처를 새로 쓰게 되면 이 표와 `security-headers.conf`를 함께 고친다. 2단계 뒤에는 고치지 않으면 화면에서 막힌다.
- 사진을 CloudFront 등 다른 주소로 내려주게 되면 `img-src`를 바꾼다.
- 사용자 브라우저의 위반까지 모아야 하면 `report-to` 수집 엔드포인트를 검토한다.
