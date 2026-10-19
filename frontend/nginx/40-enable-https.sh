#!/bin/sh
# HTTPS 켜기 (BE-75): 인증서가 있을 때만 https server를 만든다
#
# 공식 nginx 이미지는 시작할 때 /docker-entrypoint.d/의 실행 파일을 이름 순서로 실행한 뒤 Nginx를 띄운다.
# 이 스크립트는 그 단계에서 아래를 확인한다.
#   1) SERVER_DOMAIN 환경변수가 있는가 (deploy/.env → docker-compose.prod.yml)
#   2) /etc/letsencrypt/live/<도메인>/에 인증서 · 개인키가 있는가 (서버 호스트의 certbot이 발급)
#   3) 만든 설정으로 nginx -t가 통과하는가 (쌍 불일치 · 깨진 파일이면 실패 → 생성 파일을 지운다)
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

log "✅ https 활성화: $DOMAIN, www.$DOMAIN (인증서 $CERT_DIR)"
