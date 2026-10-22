#!/usr/bin/env bash
# 운영 DB 백업 (COMMON-17, deploy/README.md "운영 DB 백업")
#
#   ./backup-db.sh                    # 매일 백업 (cron) → ~/backups/lawnambul-YYYY-MM-DD.dump
#   ./backup-db.sh before-real-seed   # 작업 직전 수동 백업 → ~/backups/lawnambul-YYYY-MM-DD-HHMM-before-real-seed.dump
#
# - 임시 파일에 먼저 만들고, pg_dump가 성공하고 pg_restore --list로 형식을 읽을 수 있을 때만 최종 이름으로 옮긴다.
#   실패하면 임시 파일을 지우고 0이 아닌 값으로 끝나므로, 반쯤 만들어진 파일이 백업처럼 남지 않는다.
#   (1차 판정은 pg_dump 종료 코드다. --list는 목차만 읽으므로 형식이 깨졌는지를 한 번 더 보는 용도다)
# - 파일 권한은 600(umask 077). 관리자 비밀번호 해시 등 운영 데이터가 들어 있다. 서버 밖으로 옮기지 않는다.
# - KEEP_DAYS(기본 7일)보다 오래된 lawnambul-*.dump는 지운다.
# - 외부 DB 모드(BE-59, .env에 EXTERNAL_DB_URL)면 postgres 컨테이너가 아니라 외부 DB를 덤프한다.
#   서버에 pg_dump가 없으므로 postgres:17 클라이언트 컨테이너로 붙는다(처음 한 번 이미지를 받는다). 비밀번호는 환경변수로만 넘긴다.
#   우리 데이터가 있는 public 스키마만 뜬다 — Supabase는 auth · storage 같은 자체 스키마를 함께 두기 때문이다.
#   pgvector 확장 자체는 스키마 밖이라 빠지므로, 다른 DB에 복원할 때는 먼저 CREATE EXTENSION vector를 한다(README "외부 DB 모드").
set -euo pipefail

DEPLOY_DIR="$(cd "$(dirname "$0")" && pwd)"
BACKUP_DIR="${BACKUP_DIR:-$HOME/backups}"
KEEP_DAYS="${KEEP_DAYS:-7}"
LABEL="${1:-}"

log() { echo "[backup $(date '+%F %T')] $*"; }
compose() { docker compose -f "$DEPLOY_DIR/docker-compose.prod.yml" "$@"; }
ENV_FILE="$DEPLOY_DIR/.env"
# 값이 없을 때도 성공으로 끝낸다 — set -e · pipefail에서 grep의 "못 찾음"(종료 코드 1)이 그대로 올라오면
# 외부 DB 값이 없는 서버(같은 서버 postgres)에서 스크립트가 아무 메시지 없이 끝나 백업이 만들어지지 않는다 (10/22 운영에서 발견)
env_value() { grep -E "^$1=" "$ENV_FILE" 2>/dev/null | tail -1 | cut -d= -f2- || true; }
PG_CLIENT_IMAGE="${PG_CLIENT_IMAGE:-postgres:17-alpine}"

# 외부 DB 접속 값(JDBC 주소 → host · port · db · sslmode). 값은 출력하지 않는다
EXTERNAL_DB_URL_VALUE="$(env_value EXTERNAL_DB_URL | tr -d "\"' \r")"
if [[ -n "$EXTERNAL_DB_URL_VALUE" ]]; then
  addr="${EXTERNAL_DB_URL_VALUE#jdbc:postgresql://}"
  hostport="${addr%%/*}"; rest="${addr#*/}"
  EXT_HOST="${hostport%:*}"; EXT_PORT="${hostport##*:}"; [[ "$EXT_PORT" == "$hostport" ]] && EXT_PORT=5432
  EXT_DB="${rest%%\?*}"
  EXT_SSLMODE="$(sed -nE 's/.*[?&]sslmode=([a-z-]+).*/\1/p' <<<"$rest")"
  EXT_USER="$(env_value EXTERNAL_DB_USERNAME | tr -d "\"' \r")"
  EXT_PASSWORD="$(env_value EXTERNAL_DB_PASSWORD | tr -d "\"'\r")"
  [[ -n "$EXT_HOST" && -n "$EXT_DB" && -n "$EXT_USER" && -n "$EXT_PASSWORD" ]] || { log "❌ 외부 DB 접속 값(EXTERNAL_DB_*)이 비어 있다"; exit 2; }
  [[ "$EXT_SSLMODE" =~ ^(require|verify-ca|verify-full)$ ]] || { log "❌ EXTERNAL_DB_URL에 sslmode=require 이상이 없다"; exit 2; }
fi
# 외부 DB용 pg 클라이언트 (비밀번호는 -e 이름만 넘겨 프로세스 목록 · 로그에 값이 남지 않게 한다)
ext_pg() {
  PGPASSWORD="$EXT_PASSWORD" docker run --rm -i -e PGPASSWORD \
    -e PGHOST="$EXT_HOST" -e PGPORT="$EXT_PORT" -e PGDATABASE="$EXT_DB" -e PGUSER="$EXT_USER" -e PGSSLMODE="$EXT_SSLMODE" \
    "$PG_CLIENT_IMAGE" "$@"
}

if [[ -n "$LABEL" && ! "$LABEL" =~ ^[a-z0-9-]+$ ]]; then
  log "❌ 이름은 소문자 · 숫자 · 하이픈만: $LABEL"
  exit 2
fi

umask 077
mkdir -p "$BACKUP_DIR"
if [[ -n "$LABEL" ]]; then
  final="$BACKUP_DIR/lawnambul-$(date +%F-%H%M)-$LABEL.dump"
else
  final="$BACKUP_DIR/lawnambul-$(date +%F).dump"
fi
tmp="$(mktemp "$BACKUP_DIR/.lawnambul-XXXXXX.tmp")"
trap 'rm -f "$tmp"' EXIT

if [[ -n "$EXTERNAL_DB_URL_VALUE" ]]; then
  log "외부 DB 모드 — ${EXT_HOST} 의 public 스키마를 덤프한다"
  if ! ext_pg pg_dump --schema=public --no-owner --no-privileges -Fc > "$tmp"; then
    log "❌ pg_dump 실패(외부 DB) — 백업을 만들지 않았다"
    exit 1
  fi
  if ! ext_pg pg_restore --list < "$tmp" > /dev/null; then
    log "❌ 덤프 형식 확인(pg_restore --list) 실패 — 백업을 만들지 않았다"
    exit 1
  fi
else
  # 변수는 컨테이너 안에서 풀려야 하므로 작은따옴표로 넘긴다
  # shellcheck disable=SC2016
  if ! compose exec -T postgres sh -c 'pg_dump -U "$POSTGRES_USER" -d "$POSTGRES_DB" -Fc' > "$tmp"; then
    log "❌ pg_dump 실패 — 백업을 만들지 않았다"
    exit 1
  fi
  if ! compose exec -T postgres pg_restore --list < "$tmp" > /dev/null; then
    log "❌ 덤프 형식 확인(pg_restore --list) 실패 — 백업을 만들지 않았다"
    exit 1
  fi
fi

mv "$tmp" "$final"
trap - EXIT
find "$BACKUP_DIR" -maxdepth 1 -name 'lawnambul-*.dump' -mtime +"$KEEP_DAYS" -delete
log "✅ $final ($(du -h "$final" | cut -f1))"
