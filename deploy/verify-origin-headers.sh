#!/usr/bin/env bash
# BE-95: 배포 전 Origin 검증(OriginVerificationFilter) 전제조건 확인
# 사용법: ./deploy/verify-origin-headers.sh https://<배포 도메인>
#         예) ./deploy/verify-origin-headers.sh https://lawnambul.example.com
#
# 언제 돌리나 — BASE_URL에 이미 떠 있는 배포를 검사한다. 운영 도메인(naelawnambul.today)에서 돌리면
# "지금 떠 있는" 배포만 확인하므로, 다음 배포에서 Nginx · application.yml 설정이 바뀌면 이 스크립트를
# 다시 돌리기 전까지는 알 수 없다 — 그래서 배포 "직후"마다(smoke-test.sh와 같은 자리) 돌린다.
# 같은 TLS 종료 구성의 스테이징이 있으면 그 환경에서는 배포 "전"에도 미리 돌려볼 수 있다.
#
# 확인하는 것 — forward-headers-strategy: native(Tomcat RemoteIpValve) · Nginx의 X-Forwarded-Proto
# 전달이 운영(또는 동일 TLS 종료 구성의 스테이징)에 실제로 적용됐는지, 재발급 · 로그아웃 API로 검증한다
# (BE-95 완료 조건 중 코드 리뷰로 볼 수 없는 마지막 한 줄).
#
# 실제 로그인(NLNB_RT 쿠키)이 없어도 검증할 수 있다 — OriginVerificationFilter는 쿠키를 보기 전에
# Origin부터 검사하므로(코드 순서), 상태 코드만으로 이 필터가 "통과시켰는지"를 구분할 수 있다.
#   - 필터를 통과했다면: 로그아웃은 쿠키가 없어도 204(멱등), 재발급은 401 TOKEN_INVALID(쿠키 없음)
#   - 필터가 막았다면(Nginx가 X-Forwarded-Proto를 안 보내거나 forward-headers-strategy가 꺼져 있어
#     request.getScheme()이 http로 보임 → 브라우저가 보낸 https Origin과 불일치): 403 FORBIDDEN
# 즉 403이 나오면 이 이슈가 막으려던 바로 그 장애(재발급 · 로그아웃이 전부 막히는 상황)가 재현된 것이다.
#
# 대조군으로 scheme이 일부러 다른 Origin(http://)도 보내 403이 나오는지 확인한다 — 필터 자체가
# 꺼져있어서 전부 통과(204/401)하는 "거짓 양성"이 아님을 함께 확인하기 위해서다.

set -uo pipefail

BASE_URL="${1:?사용법: $0 https://<배포 도메인>}"
# BASE_URL에서 scheme://host만 뽑는다 (포트가 붙어 있으면 그대로 둔다 — Origin 헤더는 포트도 포함)
ORIGIN="${BASE_URL%/}"

# TLS 종료(Nginx) 뒤의 scheme 전달을 확인하는 스크립트라 https 주소만 받는다 — http 주소를 넣으면
# 대조군("scheme이 다른 Origin")이 http:// → http://로 바뀔 게 없어 정상 Origin과 같아지고, TLS 없는
# 환경에서 정상 검사도 대조군도 둘 다 통과해 "필터가 아예 통과시키고 있을 수 있다"는 잘못된 실패가 난다
# (jms0326 PR #316 리뷰).
if [[ "$ORIGIN" != https://* ]]; then
  echo "❌ https:// 주소만 받습니다: $ORIGIN"
  exit 2
fi

FAILED=0
pass() { echo "  ✅ $1"; }
fail() { echo "  ❌ $1"; FAILED=1; }

WORK_DIR="$(mktemp -d)"
trap 'rm -rf "$WORK_DIR"' EXIT
BODY_FILE="$WORK_DIR/body"

# POST 한 번: Origin 헤더를 주고 상태 코드만 받는다 (쿠키 없음 → 재발급 · 로그아웃 둘 다 로그인 없이 호출)
request() {
  local path="$1" origin_header="$2"
  : > "$BODY_FILE"
  curl -s --connect-timeout 5 --max-time 15 -o "$BODY_FILE" -w '%{http_code}' \
    -X POST -H "Origin: $origin_header" "$BASE_URL$path"
}

echo "Origin 검증 확인: $BASE_URL (Origin: $ORIGIN)"

echo "[정상 Origin — forward-headers-strategy가 켜져 있다면 필터를 통과해야 한다]"

STATUS="$(request /api/v1/auth/logout "$ORIGIN")"
if [[ "$STATUS" == "204" ]]; then
  pass "로그아웃(쿠키 없음) → 204 — Origin 검증 통과"
elif [[ "$STATUS" == "403" ]]; then
  fail "로그아웃(쿠키 없음) → 403 FORBIDDEN — Origin 검증이 막았다. forward-headers-strategy: native 또는 Nginx X-Forwarded-Proto 전달을 확인하라 (BE-95)"
else
  fail "로그아웃(쿠키 없음) → $STATUS (예상: 204). 서버 응답을 확인하라: $(cat "$BODY_FILE")"
fi

STATUS="$(request /api/v1/auth/token/reissue "$ORIGIN")"
if [[ "$STATUS" == "401" ]]; then
  pass "재발급(쿠키 없음) → 401 TOKEN_INVALID — Origin 검증 통과"
elif [[ "$STATUS" == "403" ]]; then
  fail "재발급(쿠키 없음) → 403 FORBIDDEN — Origin 검증이 막았다. forward-headers-strategy: native 또는 Nginx X-Forwarded-Proto 전달을 확인하라 (BE-95)"
else
  fail "재발급(쿠키 없음) → $STATUS (예상: 401). 서버 응답을 확인하라: $(cat "$BODY_FILE")"
fi

echo "[대조군 — scheme이 다른 Origin은 여전히 막혀야 한다 (필터 자체가 꺼진 거짓 양성 배제)]"

# ORIGIN이 위에서 https:// 임을 이미 확인했으므로 단순 치환으로 충분하다
WRONG_ORIGIN="http://${ORIGIN#https://}"
STATUS="$(request /api/v1/auth/logout "$WRONG_ORIGIN")"
if [[ "$STATUS" == "403" ]]; then
  pass "scheme이 다른 Origin($WRONG_ORIGIN) → 403 — 필터가 실제로 동작 중"
else
  fail "scheme이 다른 Origin($WRONG_ORIGIN) → $STATUS (예상: 403). 필터가 아예 통과시키고 있을 수 있다 — SecurityConfig의 protectedRequests 설정을 확인하라"
fi

echo
if [[ "$FAILED" -eq 0 ]]; then
  echo "모두 통과 — BE-95 마지막 완료 조건 충족"
else
  echo "실패 있음 — BE-95 완료 조건 미충족. 위 메시지를 따라 Nginx · application.yml 설정을 확인하라"
fi
exit "$FAILED"
