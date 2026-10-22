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
set -euo pipefail

DEPLOY_DIR="$(cd "$(dirname "$0")" && pwd)"
BACKUP_DIR="${BACKUP_DIR:-$HOME/backups}"
KEEP_DAYS="${KEEP_DAYS:-7}"
LABEL="${1:-}"

log() { echo "[backup $(date '+%F %T')] $*"; }
compose() { docker compose -f "$DEPLOY_DIR/docker-compose.prod.yml" "$@"; }

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

mv "$tmp" "$final"
trap - EXIT
find "$BACKUP_DIR" -maxdepth 1 -name 'lawnambul-*.dump' -mtime +"$KEEP_DAYS" -delete
log "✅ $final ($(du -h "$final" | cut -f1))"
