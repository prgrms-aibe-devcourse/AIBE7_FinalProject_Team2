#!/bin/sh
# HTTPS 켜기 (BE-75): 인증서가 있을 때만 https server를 만든다
#
# 공식 nginx 이미지는 시작할 때 /docker-entrypoint.d/의 실행 파일을 이름 순서로 실행한 뒤 Nginx를 띄운다.
# 이 스크립트는 그 단계에서 아래를 확인한다.
#   1) SERVER_DOMAIN 환경변수가 있는가 (deploy/.env → docker-compose.prod.yml)
#   2) /etc/letsencrypt/live/<도메인>/에 인증서 · 개인키가 있는가 (서버 호스트의 certbot이 발급)
# 둘 다 맞으면 https.conf.template으로 /etc/nginx/conf.d/https.conf를 만든다 → 443 + 도메인 http → https 이동.
# 하나라도 아니면 아무것도 만들지 않는다 → 지금처럼 http(80)만 뜬다 (CI · 로컬 · 인증서 발급 전 서버).
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
if [ ! -s "$CERT_DIR/fullchain.pem" ] || [ ! -s "$CERT_DIR/privkey.pem" ]; then
  log "⚠ $CERT_DIR 에 인증서가 없어 http만 연다 (발급 전이면 정상. 발급 절차: deploy/README.md \"HTTPS\")"
  exit 0
fi

sed "s/__SERVER_DOMAIN__/$DOMAIN/g" "$TEMPLATE" > "$OUTPUT"
log "✅ https 활성화: $DOMAIN, www.$DOMAIN (인증서 $CERT_DIR)"
