# COMMON-67 의사결정 기록

> 담당 정민석 (D) · 관련: BE-75(도메인 · HTTPS), BE-58(Nginx 배포), BE-57(보안 헤더 설계, docs/tech-stack.md 3장)

## 문제 / 배경
- 2026-10-29 운영 점검: `/api` 응답에는 Spring Security가 붙인 보안 헤더(HSTS · `X-Frame-Options` · `X-Content-Type-Options` · `Referrer-Policy`)가 있지만, Nginx가 직접 내려주는 화면(SPA HTML) · 정적 파일에는 하나도 없었다. 화면이 다른 사이트 iframe에 들어가는 것(클릭재킹)을 막지 못한다.
- 같은 점검에서 **HSTS가 이미 1년으로 켜져 있음**을 확인했다. BE-75는 "인증서 갱신이 한 번 이상 문제없이 도는 것을 본 뒤 짧은 `max-age`부터 켠다"고 Nginx에서 일부러 꺼 두었는데, 백엔드는 Spring Security 기본값(`max-age=31536000 ; includeSubDomains`)을 https 요청(`X-Forwarded-Proto: https`)마다 보내고 있었다. API를 한 번이라도 부른 브라우저는 1년 동안 https만 쓰도록 기억하고 있다. 첫 인증서 자동 갱신(발급 후 약 60일, 12월 중순)은 아직 돌지 않았다.

## 검토한 대안

### 1. 보안 헤더를 어디에 둘까
- 대안 A: 443 server 블록에 `add_header`. Nginx 1.28은 location에 `add_header`가 하나라도 있으면 server의 것을 물려받지 않아, `Cache-Control`을 붙이는 화면(`/`) · `/assets/` · `/version.json`에서 빠진다. `/api`에는 백엔드 헤더와 겹쳐 두 번 나간다.
- **대안 B(선택): 공통 파일(`snippets/security-headers.conf`)을 Nginx가 직접 응답하는 location마다 include.** `/api` location에는 넣지 않는다(백엔드가 같은 헤더를 붙인다). http · https가 같은 app.conf를 쓰므로 두 프로토콜에 똑같이 적용된다.

### 2. HSTS (팀 결정: 짧게 통일)
- 대안 A: 백엔드와 맞춰 Nginx도 1년. 인증서가 깨지면 1년간 접속 불가 위험이 그대로다.
- 대안 B: 이번에는 빼고 BE-75 보류 유지. 백엔드의 1년 HSTS가 계속 나간다(보류가 실제로는 지켜지지 않는 상태).
- **대안 C(선택): Nginx · 백엔드를 1일(`max-age=86400`)로 통일.** 브라우저는 새 HSTS 헤더를 받으면 기억한 기간을 덮어쓰므로, 이미 1년을 기억한 브라우저도 다음 https 응답부터 1일이 된다. 인증서가 깨져도 접속 불가가 하루로 줄고, BE-75의 "짧게 시작" 취지와 맞는다.

### 3. HSTS를 어디서 붙일까
- **Nginx 한 곳.** docs/tech-stack.md 3장(BE-57)이 처음부터 "HSTS는 TLS를 끝내는 Nginx가 맡는다"로 정해 두었던 설계로 맞춘다. 실제로는 Nginx가 `X-Forwarded-Proto: https`를 넘기고 백엔드가 `forward-headers-strategy: native`라 Spring이 요청을 https로 보아 기본값(1년)을 붙이고 있었다.
- 백엔드는 `SecurityHeaders.apply`에서 HSTS를 끄고(사용자 · 관리자 · 카카오 로그인 체인 공통), Nginx가 화면과 `/api`에 같은 값을 붙인다. 기간을 늘릴 때 한 곳만 고친다.
- `/api`는 Nginx가 백엔드 응답의 HSTS를 `proxy_hide_header`로 한 번 더 지운다. 웹 · 백엔드 이미지가 따로 배포 · 되돌림되는 동안 옛 백엔드(1년)와 새 Nginx(1일)가 함께 나가면 브라우저는 처음 헤더만 쓰기 때문이다(RFC 6797 8.1).
- 값은 `default.conf`의 `map $scheme $hsts_header`로 https일 때만 채운다. http 응답에서는 값이 비어 Nginx가 헤더를 보내지 않는다(localhost · 탄력적 IP의 http 스모크 테스트, 인증서 발급 전 서버).

## 선택한 방법과 이유
- `snippets/security-headers.conf`: `X-Content-Type-Options: nosniff` · `X-Frame-Options: DENY` · `Referrer-Policy: strict-origin-when-cross-origin` · `Strict-Transport-Security: $hsts_header`(모두 `always`). 값은 백엔드와 같다. 화면 안에서 iframe을 쓰는 곳은 없다(`DENY` 영향 없음).
- `snippets/api-proxy.conf`: `Strict-Transport-Security: $hsts_header`만 붙인다.
- 백엔드: `SecurityHeaders.apply`(Referrer-Policy + HSTS 끄기)를 사용자 · 관리자 · 카카오 로그인 체인이 함께 쓴다. 카카오 설정이 `SecurityConfig`에 기대지 않게 공통 클래스로 뺐다.
- `includeSubDomains` · `preload`는 붙이지 않는다. 늘릴 때 함께 판단한다.
- 스모크 테스트에 "[보안 헤더]" 검사를 더했다. 서버 되돌림(deploy.sh)은 이 새 스크립트로 이전 이미지도 검사하므로 기본은 경고만, CI(`deploy-images.yml`)는 `SMOKE_REQUIRE_SECURITY_HEADERS=1`로 실패 처리한다(BE-75의 ACME 검사와 같은 방식).

## 확인
- Nginx 1.28 이미지 · 자체 서명 인증서로 http · https 양쪽을 띄워 확인: https 화면 · 정적 파일 · `/api`에 `strict-transport-security: max-age=86400` 한 번, 화면 · 정적 파일 · 404 · `version.json`에 보안 헤더 3개, http에는 HSTS 없음, `nginx -t` 통과.
- `https://www` → 루트 301에도 HSTS가 한 번 붙고, `http://www`에는 붙지 않는다(자체 서명 인증서로 템플릿을 띄워 확인, PR #337 리뷰).
- 백엔드: https 요청에 HSTS가 없고 다른 보안 헤더는 그대로 — 사용자 · 관리자 체인은 `UserSecurityChainTest`, 카카오 로그인 체인은 `KakaoOAuthFlowTest`(client-id가 있어야 켜지는 체인이라 따로 확인, PR #337 리뷰).

## 이번에 하지 않은 것
- **CSP(Content-Security-Policy)**: docs/tech-stack.md 3장은 CSP도 Nginx에서 정하기로 했지만 이번에는 넣지 않았다. 화면이 쓰는 외부 출처(카카오 로그인 · 글꼴 · 이미지) 목록과 인라인 스크립트 여부를 먼저 정리해야 하고, 잘못 넣으면 화면이 깨진다. `Content-Security-Policy-Report-Only`로 먼저 모은 뒤 켜는 별도 작업으로 둔다. → COMMON-70에서 1단계(Report-Only)를 넣었다(`docs/decisions/COMMON-70.md`).

## 추후 재검토가 필요하면
- **첫 인증서 자동 갱신이 문제없이 돈 뒤**(`certbot certificates`의 만료일이 늘었는지) `$hsts_header`를 지금 값 1일 → 1주 → 1년으로 차례로 늘린다(deploy/README.md "HTTPS로 풀리는 것 · 남은 것"). 1년으로 늘릴 때 `includeSubDomains`를 판단한다.
- **www 보호 공백**: 예전 백엔드 HSTS(1년 · `includeSubDomains`)를 받은 브라우저는 루트의 새 값으로 정책이 바뀌어 www 보호가 빠진다. `https://www` 301에도 HSTS를 붙여, https://www를 한 번 지난 브라우저는 다시 보호된다(PR #337 리뷰). 다만 https://www를 거치지 않은 브라우저의 첫 `http://www` 요청은 보호되지 않는다 — 이를 막으려면 루트에 `includeSubDomains`가 필요하므로, 1년으로 늘릴 때 함께 판단한다(그때 http만 쓰는 하위 도메인이 없는지 확인).
- **`/api`에서 Nginx가 직접 만드는 502 · 504 응답**에는 HSTS만 붙고 nosniff · DENY · Referrer-Policy는 붙지 않는다(`api-proxy.conf`는 HSTS만 받는다). 화면이 아닌 오류 응답이라 이번에는 두고, 오류 응답을 화면으로 보여 주게 되면 `error_page` location에 `security-headers.conf`를 include한다 (PR #337 리뷰).
- 화면에서 iframe(예: 외부 임베드)을 쓰게 되면 `X-Frame-Options`를 `SAMEORIGIN` 또는 CSP `frame-ancestors`로 바꾼다.
