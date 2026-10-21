#!/bin/sh
# 429 전용 로그 크기 상한 (BE-63)
#
# default.conf가 막힌 요청(429)만 /var/log/nginx-ratelimit/rate-limited.log에 JSON 한 줄씩 남긴다.
# 공격을 받으면 이 파일이 끝없이 커질 수 있어, 1분마다 크기를 보고 상한을 넘으면 비운다(truncate).
# - 비워도 Nginx는 같은 파일에 계속 이어 쓴다(append로 열려 있다). 파일을 지우거나 바꾸지 않으므로 Nginx를 다시 읽힐 필요가 없다
# - Alloy(loki.source.file)는 파일이 작아진 것을 보고 처음부터 다시 읽는다. 비우기 직전 1분 안의 일부 줄은 빠질 수 있다
# - 상한은 RATE_LIMIT_LOG_MAX_BYTES(기본 10MB ≈ 4만 줄). 평소에는 하루 몇 줄이라 닿지 않는다
#
# 공식 nginx 이미지는 /docker-entrypoint.d/의 실행 파일을 이름 순서로 실행한 뒤 Nginx를 띄운다.
# 여기서는 감시 루프만 뒤로 띄우고 바로 끝낸다. 어떤 경우에도 0으로 끝낸다 — 실패해도 웹이 떠야 한다(40-enable-https.sh와 같은 기준)

LOG_DIR=/var/log/nginx-ratelimit
LOG_FILE="$LOG_DIR/rate-limited.log"
MAX_BYTES="${RATE_LIMIT_LOG_MAX_BYTES:-10485760}"

log() { echo "[41-trim-rate-limit-log] $*"; }

mkdir -p "$LOG_DIR" 2>/dev/null || { log "경고: $LOG_DIR를 만들지 못해 429 로그 크기 관리를 건너뜀"; exit 0; }

case "$MAX_BYTES" in
  ''|*[!0-9]*) log "경고: RATE_LIMIT_LOG_MAX_BYTES가 숫자가 아니라 기본값 10MB를 씀"; MAX_BYTES=10485760 ;;
esac

(
  while true; do
    sleep 60
    if [ -f "$LOG_FILE" ]; then
      size=$(wc -c < "$LOG_FILE" 2>/dev/null || echo 0)
      if [ "$size" -gt "$MAX_BYTES" ]; then
        : > "$LOG_FILE" && log "429 로그가 상한(${MAX_BYTES}B)을 넘어 비움 (${size}B)"
      fi
    fi
  done
) &

log "429 로그 크기 관리 시작 (상한 ${MAX_BYTES}B)"
exit 0
