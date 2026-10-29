#!/usr/bin/env bash
# 배포 스모크 테스트 (BE-58, WBS 7.1.1 완료 기준 "EC2에서 / 와 /api 동작")
# 사용법: ./deploy/smoke-test.sh [기준 주소]   예) ./deploy/smoke-test.sh http://localhost
#         EC2에서는 ./deploy/smoke-test.sh http://<탄력적-IP>
# GitHub Actions(deploy-images.yml)도 이미지를 올리기 전에 이 스크립트를 실행한다.
#
# 확인하는 것
# 1) / 와 화면 주소(/cases/1/trial)가 index.html(SPA)을 돌려주는지
# 2) /api 가 백엔드 JSON을 돌려주는지 — 없는 API 주소도 HTML이 아니라 JSON이어야 한다
#    (Nginx가 /api를 index.html로 떨어뜨리면 프론트가 "서버 응답을 해석할 수 없어요"를 띄운다, FE-2 리뷰)
# 3) 정적 파일 캐시 정책 — assets는 오래 캐시, index.html은 캐시 안 함
# 4) 화면 응답에 보안 헤더가 붙는지 (COMMON-67)
# 5) Let's Encrypt 확인 경로가 SPA fallback으로 새지 않는지 (BE-75) — index.html이 나가면 인증서 발급 · 갱신이 실패한다

set -uo pipefail

BASE_URL="${1:-http://localhost}"
FAILED=0

# 응답을 담을 임시 폴더. 끝나면(성공 · 실패 · 중단 모두) 지운다
WORK_DIR="$(mktemp -d)"
trap 'rm -rf "$WORK_DIR"' EXIT
BODY_FILE="$WORK_DIR/body"
HEADER_FILE="$WORK_DIR/headers"

pass() { echo "  ✅ $1"; }
fail() { echo "  ❌ $1"; FAILED=1; }

# 요청 한 번으로 상태 코드 · Content-Type · Cache-Control · 본문을 받는다 (이전 요청 결과는 덮어쓴다)
# 연결 5초 · 전체 15초 제한. 넘으면 curl이 상태 코드 000을 돌려주고, 그 검사만 실패로 기록한 뒤 다음 검사를 계속한다
request() {
  local target="$1"
  # 시간 초과 · 연결 실패면 curl이 파일을 새로 쓰지 않을 수 있어, 이전 요청 결과가 남지 않게 먼저 비운다
  : > "$BODY_FILE"
  : > "$HEADER_FILE"
  STATUS="$(curl -s --connect-timeout 5 --max-time 15 -o "$BODY_FILE" -D "$HEADER_FILE" -w '%{http_code}' "$BASE_URL$target")"
  CONTENT_TYPE="$(grep -i '^content-type:' "$HEADER_FILE" | tail -1 | cut -d' ' -f2- | tr -d '\r')"
  CACHE_CONTROL="$(grep -i '^cache-control:' "$HEADER_FILE" | tail -1 | cut -d' ' -f2- | tr -d '\r')"
}

# 화면 주소: 200 + HTML + 앱 루트(div#app)
expect_spa() {
  local target="$1"
  request "$target"
  if [[ "$STATUS" == "200" && "$CONTENT_TYPE" == text/html* ]] && grep -q 'id="app"' "$BODY_FILE"; then
    pass "$target → 200 index.html"
  else
    fail "$target → $STATUS ($CONTENT_TYPE), index.html이 아님"
  fi
}

# API 주소: 기대한 상태 코드 + JSON (HTML이면 /api가 SPA fallback으로 샌 것)
expect_api_json() {
  local target="$1" expected="$2"
  request "$target"
  if [[ "$STATUS" == "$expected" && "$CONTENT_TYPE" == application/json* ]]; then
    pass "$target → $STATUS JSON"
  elif [[ "$CONTENT_TYPE" == text/html* ]]; then
    fail "$target → $STATUS HTML (백엔드가 아니라 index.html · Nginx 페이지가 응답함)"
  else
    fail "$target → $STATUS ($CONTENT_TYPE), 기대: $expected JSON"
  fi
}

echo "스모크 테스트: $BASE_URL"

echo "[화면]"
expect_spa "/"
expect_spa "/cases/1/trial"    # 실제 파일이 없는 화면 주소 → SPA fallback (6막 주소, FE-58)
expect_spa "/cases/1/review"   # 옛 주소도 SPA로 열려야 라우터가 새 주소로 옮긴다 (legacyPaths.js)

echo "[API]"
expect_api_json "/api/v1/cases" 200
expect_api_json "/api/v1/smoke-test-not-found" 404

echo "[캐시]"
request "/"
ASSET_PATH="$(grep -o '/assets/[^"]*\.js' "$BODY_FILE" | head -1)"
if [[ -z "$ASSET_PATH" ]]; then
  fail "index.html에서 /assets/*.js를 찾지 못함"
else
  request "$ASSET_PATH"
  if [[ "$STATUS" == "200" && "$CACHE_CONTROL" == *immutable* ]]; then
    pass "$ASSET_PATH → 200, 오래 캐시 ($CACHE_CONTROL)"
  else
    fail "$ASSET_PATH → $STATUS, Cache-Control: ${CACHE_CONTROL:-없음}"
  fi
fi
request "/"
if [[ "$CACHE_CONTROL" == *no-cache* ]]; then
  pass "index.html → 캐시 안 함 ($CACHE_CONTROL)"
else
  fail "index.html Cache-Control: ${CACHE_CONTROL:-없음} (no-cache여야 새 배포가 바로 보인다)"
fi

echo "[보안 헤더]"
# Nginx가 직접 내려주는 화면(index.html)에 보안 헤더가 붙는지 (COMMON-67, snippets/security-headers.conf).
# /api 응답은 백엔드(Spring Security)가 붙이므로 여기서는 화면만 본다
# - 기본은 경고만 남긴다: deploy.sh는 되돌릴 때 이 새 스크립트로 COMMON-67 이전 이미지(헤더 없음)를 검사하므로,
#   실패로 치면 되돌림까지 실패한다. CI(deploy-images.yml)는 SMOKE_REQUIRE_SECURITY_HEADERS=1로 새 이미지에 대해 실패로 친다
request "/"
for expected in "x-content-type-options: nosniff" "x-frame-options: DENY" "referrer-policy: strict-origin-when-cross-origin"; do
  if tr -d '\r' < "$HEADER_FILE" | grep -qi "^${expected}$"; then
    pass "/ → ${expected}"
  elif [[ "${SMOKE_REQUIRE_SECURITY_HEADERS:-}" == "1" ]]; then
    fail "/ → '${expected}' 헤더가 없음 (snippets/security-headers.conf include 확인)"
  else
    echo "  ⚠ / → '${expected}' 헤더가 없다 — COMMON-67 이전 이미지면 정상, 아니면 security-headers.conf include를 확인한다"
  fi
done

echo "[HTTPS 준비]"
# 없는 확인 파일은 404여야 한다. 200 + HTML이면 SPA fallback이 이 경로를 가로챈 것 (snippets/acme-challenge.conf)
# - https 주소로 실행하면(도메인 server) 이 경로는 http에만 있으므로 건너뛴다
# - 기본은 경고만 남긴다: deploy.sh는 되돌릴 때 이 새 스크립트로 BE-75 이전 이미지(이 경로가 없음)를 검사하므로,
#   실패로 치면 되돌림까지 실패한다. CI(deploy-images.yml)는 SMOKE_REQUIRE_ACME=1로 새 이미지에 대해 실패로 친다
if [[ "$BASE_URL" == https://* ]]; then
  pass "https 주소라 확인 경로 검사를 건너뜀 (http 전용 경로)"
else
  request "/.well-known/acme-challenge/smoke-test-not-found"
  if [[ "$STATUS" == "404" ]]; then
    pass "/.well-known/acme-challenge/ → 404 (SPA로 새지 않음)"
  elif [[ "${SMOKE_REQUIRE_ACME:-}" == "1" ]]; then
    fail "/.well-known/acme-challenge/ → $STATUS ($CONTENT_TYPE), 404여야 인증서 발급이 된다"
  else
    echo "  ⚠ /.well-known/acme-challenge/ → $STATUS ($CONTENT_TYPE), 404가 아니다 — BE-75 이전 이미지면 정상, 아니면 인증서 발급 · 갱신이 실패한다"
  fi
fi

if [[ "$FAILED" == "0" ]]; then
  echo "결과: 모두 통과"
else
  echo "결과: 실패한 항목이 있다"
fi
exit "$FAILED"
