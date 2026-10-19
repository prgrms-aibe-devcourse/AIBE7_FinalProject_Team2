#!/bin/sh
# HTTPS 켜기 (BE-75): 인증서가 있을 때만 https server를 만든다
#
# 공식 nginx 이미지는 시작할 때 /docker-entrypoint.d/의 실행 파일을 이름 순서로 실행한 뒤 Nginx를 띄운다.
# 이 스크립트는 그 단계에서 아래를 확인한다.
#   1) SERVER_DOMAIN 환경변수가 있는가 (deploy/.env → docker-compose.prod.yml)
#   2) /etc/letsencrypt/live/<도메인>/에 인증서 · 개인키가 있는가 (서버 호스트의 certbot이 발급)
#   3) 만든 설정으로 nginx -t가 통과하는가 (쌍 불일치 · 깨진 파일이면 실패 → 생성 파일을 지운다)
#   4) 인증서 이름(SAN)에 도메인이 있는가 (없으면 지운다). www가 없으면 켜되 경고를 남긴다
# 모두 맞으면 https.conf.template으로 만든 /etc/nginx/conf.d/https.conf가 남는다 → 443 + 도메인 http → https 이동.
# 하나라도 아니면 https 설정을 남기지 않는다 → 지금처럼 http(80)만 뜬다 (CI · 로컬 · 인증서 발급 전 서버 · 인증서 이상).
#
# 어떤 경우에도 0으로 끝낸다: 여기서 실패하면 컨테이너가 뜨지 않아 http 서비스까지 내려가고,
# 자동 배포(deploy.sh)가 되돌림을 반복한다. 대신 경고를 로그에 남긴다(docker compose logs web).
# https가 실제로 켜졌는지는 deploy/README.md "HTTPS" 확인 명령으로 본다.

set -eu

TEMPLATE=/etc/nginx/lawnambul/https.conf.template
OUTPUT=/etc/nginx/conf.d/https.conf
DOMAIN="${SERVER_DOMAIN:-}"

log() { echo "[40-enable-https] $*"; }

# 컨테이너를 다시 시작(restart)해도 이전에 만든 파일이 남아 있지 않게 매번 지우고 새로 판단한다
rm -f "$OUTPUT"

if [ -z "$DOMAIN" ]; then
  log "SERVER_DOMAIN이 없어 http만 연다"
  exit 0
fi

# 도메인 값 검사: 소문자 · 숫자 · 점 · 하이픈만. 이 값이 Nginx 설정 파일에 그대로 들어가므로
# 공백 · 세미콜론 같은 문자가 섞이면 설정이 깨지거나 다른 지시어가 끼어들 수 있다
case "$DOMAIN" in
  ""|.*|*..*|*[!a-z0-9.-]*)
    log "⚠ SERVER_DOMAIN 값이 도메인 형식이 아니다: '$DOMAIN' (소문자 · 숫자 · 점 · 하이픈만). http만 연다"
    exit 0
    ;;
esac

CERT_DIR="/etc/letsencrypt/live/$DOMAIN"
# -s: 파일이 있고 비어 있지 않음 (live/의 링크가 가리키는 archive/ 파일까지 따라가서 확인한다)
# 여기서는 "있는가"만 본다. 쌍이 맞는지 · 깨지지 않았는지는 아래 nginx -t가 실제로 읽어 보고 판단한다
if [ ! -s "$CERT_DIR/fullchain.pem" ] || [ ! -s "$CERT_DIR/privkey.pem" ]; then
  log "⚠ $CERT_DIR 에 인증서가 없어 http만 연다 (발급 전이면 정상. 발급 절차: deploy/README.md \"HTTPS\")"
  exit 0
fi

if ! sed "s/__SERVER_DOMAIN__/$DOMAIN/g" "$TEMPLATE" > "$OUTPUT"; then
  rm -f "$OUTPUT"
  log "⚠ https 설정 파일을 만들지 못해 http만 연다 ($TEMPLATE)"
  exit 0
fi

# 생성한 설정으로 Nginx가 실제로 뜰 수 있는지 검사한다 (CodeRabbit 리뷰 반영)
# - 인증서 · 개인키 쌍이 맞지 않거나, 파일이 깨졌거나, 읽을 수 없으면 Nginx가 시작하지 못한다.
#   그대로 두면 https뿐 아니라 기본 http server까지 내려가고, 자동 배포(deploy.sh)는 되돌림을 반복한다
# - 실패하면 생성 파일을 지우고 http만으로 시작한다. 원인은 nginx -t 출력으로 로그에 남긴다
if ! TEST_OUTPUT="$(nginx -t 2>&1)"; then
  rm -f "$OUTPUT"
  log "⚠ https 설정 검사(nginx -t) 실패 — 생성 파일을 지우고 http만 연다. 인증서 · 개인키 쌍과 파일을 확인한다 ($CERT_DIR)"
  echo "$TEST_OUTPUT" | sed 's/^/[40-enable-https]   /'
  # https를 빼고도 실패하면 원인은 기본 설정 쪽이다. 여기서 더 할 수 있는 일은 없어 로그만 남긴다(Nginx 시작 단계에서 다시 실패한다)
  if ! nginx -t >/dev/null 2>&1; then
    log "❌ https 설정을 지운 뒤에도 nginx -t가 실패한다 — 기본 설정(default.conf · snippets) 문제"
  fi
  exit 0
fi

# ---------- 인증서가 이 도메인을 덮는지 (SAN) 확인 (PR #181 리뷰 반영) ----------
# 파일 검사 · nginx -t는 "읽을 수 있는가"만 본다. certbot을 -d www... 없이 재발급하면 둘 다 통과하지만
# https://www 접속 때 브라우저가 이름 불일치 경고를 띄우고, 어떤 로그에도 드러나지 않는다.
# - 루트 도메인이 없으면: https를 켜지 않는다. 켜면 http 요청이 모두 깨진 https로 이동해 사이트 전체가 경고 화면이 된다
# - www만 없으면: 루트 https는 켠다(http://www는 루트 https로 이동해 정상). https://www 직접 접속만 경고 → 로그로 알린다
# - openssl이 없거나 인증서를 못 읽으면(이미지 문제) 확인을 건너뛰고 알린다. nginx -t는 이미 통과했으므로 https는 켠다
WWW_MISSING=0
if ! command -v openssl >/dev/null 2>&1; then
  log "⚠ openssl이 없어 인증서 이름(SAN) 확인을 건너뛴다 — 이미지에 openssl이 있어야 한다 (frontend/Dockerfile)"
elif ! SAN_RAW="$(openssl x509 -in "$CERT_DIR/fullchain.pem" -noout -ext subjectAltName 2>/dev/null)"; then
  log "⚠ 인증서 이름(SAN)을 읽지 못해 확인을 건너뛴다 ($CERT_DIR/fullchain.pem)"
else
  # 출력 예: "X509v3 Subject Alternative Name: \n    DNS:a.com, DNS:www.a.com" → 한 줄에 하나씩 "DNS:a.com"
  SANS="$(printf '%s\n' "$SAN_RAW" | tail -n +2 | tr ',' '\n' | sed 's/^[[:space:]]*//; s/[[:space:]]*$//')"
  # -F(문자 그대로) -x(줄 전체 일치): 도메인의 점이 정규식 "아무 문자"로 해석되지 않게
  if ! printf '%s\n' "$SANS" | grep -Fxq "DNS:$DOMAIN"; then
    rm -f "$OUTPUT"
    log "⚠ 인증서에 $DOMAIN 이 없어 https를 켜지 않고 http만 연다 — 인증서 이름: $(printf '%s' "$SANS" | tr '\n' ' ')"
    log "   다시 발급: certbot certonly --webroot ... -d $DOMAIN -d www.$DOMAIN (deploy/README.md \"HTTPS\")"
    exit 0
  fi
  # www는 정확한 이름 또는 와일드카드(*.도메인)면 덮인다
  if ! printf '%s\n' "$SANS" | grep -Fxq "DNS:www.$DOMAIN" && ! printf '%s\n' "$SANS" | grep -Fxq "DNS:*.$DOMAIN"; then
    WWW_MISSING=1
  fi
fi

if [ "$WWW_MISSING" = "1" ]; then
  log "✅ https 활성화: $DOMAIN (인증서 $CERT_DIR)"
  log "⚠ 인증서에 www.$DOMAIN 이 없다 — https://www.$DOMAIN 직접 접속 시 브라우저 경고 (http://www는 정상 이동)."
  log "   다시 발급: certbot certonly --webroot ... -d $DOMAIN -d www.$DOMAIN (deploy/README.md \"HTTPS\")"
else
  log "✅ https 활성화: $DOMAIN, www.$DOMAIN (인증서 $CERT_DIR)"
fi
