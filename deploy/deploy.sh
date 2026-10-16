#!/usr/bin/env bash
# 운영 서버 배포 (BE-60, WBS 7.1.3): 지정한 sha 이미지로 바꾸고, 실패하면 직전 이미지로 되돌린다
#
# 사용법 (서버의 deploy 폴더에서):
#   ./deploy.sh sha-1a2b3c4     # 그 커밋의 이미지로 배포
#   ./deploy.sh --rollback      # 직전에 배포했던 이미지로 되돌린다
# 보통은 사람이 부르지 않고 cd-agent.sh(systemd 타이머)가 main의 새 이미지를 보고 부른다.
#
# 순서: 이미지 받기 → .env의 IMAGE_TAG 교체 → up -d → 백엔드 준비 대기 → 스모크 테스트
#       → 실패하면 직전 IMAGE_TAG로 되돌려 같은 확인을 한 번 더 한다
#
# 되돌리지 않는 것: DB 스키마(Flyway). 새 이미지가 마이그레이션을 적용한 뒤 실패하면 앱만 되돌리고 경고를 남긴다.
#   이전 앱은 자기가 모르는 새 마이그레이션을 무시하고 뜬다(Flyway 기본 ignoreMigrationPatterns "*:future").
#   스키마를 되돌리는 것은 데이터 손상 위험이 있어 사람이 판단한다 (deploy/README.md "자동 배포(CD)")
#
# 종료 코드: 0 배포 성공(또는 이미 그 버전) · 1 배포 실패 → 되돌림 성공 · 2 이미지 받기 실패(아무것도 안 바꿈)
#            3 되돌림까지 실패(사람이 확인) · 4 사용법 · 잠금 · 설정 오류
#
# 테스트 전용: DEPLOY_SKIP_PULL=1이면 이미지를 받지 않는다(로컬에서 만든 이미지로 확인할 때). 서버에서는 쓰지 않는다

set -uo pipefail

DEPLOY_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ENV_FILE="$DEPLOY_DIR/.env"
STATE_DIR="$DEPLOY_DIR/.deploy"          # 직전 태그 · 잠금 파일 (커밋 안 함, .gitignore)
READY_TIMEOUT="${DEPLOY_READY_TIMEOUT:-180}"   # 백엔드 준비 대기 상한(초). t3.small 기동은 20 ~ 40초 (10/16 실측 22초)

log() { echo "[deploy $(date '+%F %T')] $*"; }

# ---------- 준비 ----------
[[ -f "$ENV_FILE" ]] || { log "❌ $ENV_FILE 이 없다 (서버 설정 파일, 지우면 DB 접속 불가)"; exit 4; }
mkdir -p "$STATE_DIR"

# 동시에 두 배포가 돌지 않게 잠근다 (타이머 · 사람이 겹쳐 부를 때). 서버(Ubuntu)는 flock,
# flock이 없는 macOS(로컬 확인용)는 폴더 만들기로 대신한다 — 끝나면(성공 · 실패 · 중단) 지운다
if command -v flock >/dev/null 2>&1; then
  exec 9>"$STATE_DIR/lock"
  flock -n 9 || { log "다른 배포가 진행 중이라 이번에는 건너뛴다"; exit 4; }
else
  mkdir "$STATE_DIR/lock.d" 2>/dev/null || { log "다른 배포가 진행 중이라 이번에는 건너뛴다 ($STATE_DIR/lock.d)"; exit 4; }
  trap 'rmdir "$STATE_DIR/lock.d" 2>/dev/null' EXIT
fi

# .env에서 값 하나를 읽는다 (source하지 않는다: 비밀값이 셸 변수 · 하위 프로세스로 퍼지지 않게)
env_value() { grep -E "^$1=" "$ENV_FILE" | tail -1 | cut -d= -f2-; }

# .env의 IMAGE_TAG만 바꾼다. 같은 폴더의 임시 파일에 쓰고 권한(600)을 맞춘 뒤 한 번에 바꿔 끼운다(중간에 끊겨도 .env가 깨지지 않게)
# 디스크가 꽉 차 임시 파일이 덜 써지면 원본을 덮지 않는다: 줄 수가 "IMAGE_TAG를 뺀 원본 + 1"인지 확인한다(리뷰 반영)
set_image_tag() {
  local tag="$1" tmp expected
  tmp="$(mktemp "$ENV_FILE.XXXXXX")" || return 1
  expected=$(( $(grep -cvE '^IMAGE_TAG=' "$ENV_FILE") + 1 ))
  { grep -vE '^IMAGE_TAG=' "$ENV_FILE"; echo "IMAGE_TAG=$tag"; } > "$tmp"
  if [[ "$(wc -l < "$tmp" | tr -d ' ')" != "$expected" ]] || ! grep -qx "IMAGE_TAG=$tag" "$tmp"; then
    rm -f "$tmp"
    return 1
  fi
  chmod 600 "$tmp" && mv "$tmp" "$ENV_FILE"
}

# 성공한 뒤 디스크 정리: 이 서버의 sha 이미지 중 지금 배포한 것과 직전 것(되돌릴 때 필요)만 남긴다.
# 배포마다 새 이미지(web + backend 약 700MB)를 받으므로 지우지 않으면 30GB 디스크가 수십 번 배포 뒤 찬다(리뷰 반영).
# 이름 없는 이미지(main 태그가 새 이미지로 옮겨 가며 남은 것 등)도 지운다. 실패해도 배포 결과에는 영향이 없다
cleanup_old_images() {
  local keep_a="$1" keep_b="$2" prefix ref
  prefix="$(env_value IMAGE_PREFIX)"
  [[ -n "$prefix" ]] || return 0
  for name in web backend; do
    docker images --format '{{.Repository}}:{{.Tag}}' "$prefix-$name" 2>/dev/null | while read -r ref; do
      [[ "$ref" =~ :sha-[0-9a-f]{7}$ ]] || continue
      [[ "$ref" == *":$keep_a" || "$ref" == *":$keep_b" ]] && continue
      docker rmi "$ref" >/dev/null 2>&1 && log "정리: $ref"
    done
  done
  docker image prune -f >/dev/null 2>&1
}

COMPOSE=(docker compose -f "$DEPLOY_DIR/docker-compose.prod.yml")
# 실제 사건 시드를 겹쳐 띄우는 서버면 같은 구성으로 다시 띄운다 (README "실제 사건 시드")
if [[ -n "$(env_value REAL_SEED_DIR)" ]]; then
  COMPOSE+=(-f "$DEPLOY_DIR/docker-compose.real-seed.yml")
fi
# 모니터링 에이전트를 올린 서버면 그 오버레이도 함께 쓴다 (COMMON-43, PR #168 리뷰 반영).
# 빼먹으면 자동 배포가 backend를 다시 만들 때 로그 볼륨과 8081 포트가 사라져 **수집이 조용히 끊긴다**
# — 에이전트 컨테이너는 그대로 떠 있어서 알아차리기 어렵다 (docker-compose.monitoring-agent.yml 머리말)
if [[ "$(env_value MONITORING_AGENT)" == "1" ]]; then
  # 오버레이가 요구하는 두 값(사설 IP · Loki 주소)이 없으면 compose가 뒤늦게 다른 이유처럼 실패하므로 먼저 막는다
  for required in MAIN_PRIVATE_IP LOKI_URL; do
    [[ -n "$(env_value "$required")" ]] || {
      log "❌ MONITORING_AGENT=1인데 .env에 $required 가 없다 (deploy/docker-compose.monitoring-agent.yml)"
      exit 4
    }
  done
  COMPOSE+=(-f "$DEPLOY_DIR/docker-compose.monitoring-agent.yml")
fi
WEB_PORT="$(env_value WEB_PORT)"; WEB_PORT="${WEB_PORT:-80}"
BASE_URL="http://localhost:$WEB_PORT"

# Flyway가 적용한 마지막 번호. postgres 서비스가 없으면(RDS 전환 후) 빈 값 → 스키마 비교를 건너뛴다
# 작은따옴표는 일부러다: $POSTGRES_USER · $POSTGRES_DB는 이 셸이 아니라 postgres 컨테이너 안에서 풀린다(비밀값을 여기서 읽지 않음)
# shellcheck disable=SC2016
schema_rank() {
  "${COMPOSE[@]}" exec -T postgres sh -c \
    'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -Atc "select coalesce(max(installed_rank), 0) from flyway_schema_history"' \
    2>/dev/null | tr -d '[:space:]'
}

# 백엔드가 /api로 응답할 때까지 기다린다 (Flyway 마이그레이션 포함). 502(아직 안 뜸) · 000(연결 실패)이면 계속 기다린다
wait_ready() {
  local waited=0 code
  while (( waited < READY_TIMEOUT )); do
    code="$(curl -s --max-time 5 -o /dev/null -w '%{http_code}' "$BASE_URL/api/v1/cases")"
    if [[ "$code" == "200" ]]; then
      log "백엔드 준비됨 (${waited}초)"
      return 0
    fi
    sleep 3; waited=$(( waited + 3 ))
  done
  log "❌ 백엔드가 ${READY_TIMEOUT}초 안에 준비되지 않았다 (마지막 응답 ${code:-없음})"
  return 1
}

# 백엔드 컨테이너의 healthcheck 상태 (healthy · unhealthy · starting, 컨테이너가 없으면 빈 값)
backend_health() {
  local id
  id="$("${COMPOSE[@]}" ps -q backend 2>/dev/null)"
  [[ -n "$id" ]] && docker inspect -f '{{if .State.Health}}{{.State.Health.Status}}{{end}}' "$id" 2>/dev/null
}

# 백엔드가 healthy가 될 때까지 기다린다. unhealthy로 판정되면(기동 유예 120초 뒤 연속 실패) 바로 실패로 본다
wait_backend_healthy() {
  local waited=0 status=""
  while (( waited < READY_TIMEOUT )); do
    status="$(backend_health)"
    case "$status" in
      healthy)   log "백엔드 healthy (${waited}초)"; return 0 ;;
      unhealthy) log "❌ 백엔드가 unhealthy로 판정됐다 (${waited}초)"; return 1 ;;
    esac
    sleep 3; waited=$(( waited + 3 ))
  done
  log "❌ 백엔드가 ${READY_TIMEOUT}초 안에 healthy가 되지 않았다 (마지막 상태 ${status:-없음})"
  return 1
}

# 지정한 태그로 띄우고 확인한다. 성공 0 · 실패 1
# 순서가 중요하다: 백엔드를 먼저 바꾸고, 준비된 뒤에 웹을 바꾼다.
#   한 번에 up 하면 Compose가 웹을 먼저 내려 두고 새 백엔드를 기다려서(depends_on: service_healthy), 배포 중에는 화면까지 안 열린다.
#   이 순서면 그동안 기존 웹이 화면을 계속 내주고, API만 백엔드가 다시 뜨는 동안(20 ~ 40초) 잠깐 끊긴다.
#   새 백엔드가 실패하면 웹은 바뀌지 않은 채로 되돌림이 시작된다 (10/16 로컬 실측, docs/decisions/BE-60.md)
start_and_check() {
  local tag="$1"
  set_image_tag "$tag" || { log "❌ .env의 IMAGE_TAG를 바꾸지 못했다"; return 1; }
  # DB · Redis는 이미지가 그대로라 다시 만들지 않는다. --remove-orphans는 쓰지 않는다(겹쳐 띄운 서비스를 지우지 않게)
  if ! "${COMPOSE[@]}" up -d --no-build backend; then
    log "❌ 백엔드를 띄우지 못했다 ($tag)"
    return 1
  fi
  wait_backend_healthy || return 1
  if ! "${COMPOSE[@]}" up -d --no-build web; then
    log "❌ 웹을 띄우지 못했다 ($tag)"
    return 1
  fi
  wait_ready || return 1
  if ! "$DEPLOY_DIR/smoke-test.sh" "$BASE_URL"; then
    log "❌ 스모크 테스트 실패 ($tag)"
    return 1
  fi
  return 0
}

show_failure_logs() {
  log "---- 실패 원인 확인용 상태 · 로그 (비밀값은 출력되지 않는다) ----"
  "${COMPOSE[@]}" ps
  "${COMPOSE[@]}" logs --no-color --tail=80 backend web
}

# ---------- 인자 ----------
CURRENT="$(env_value IMAGE_TAG)"
case "${1:-}" in
  --rollback)
    TARGET="$(cat "$STATE_DIR/previous_tag" 2>/dev/null || true)"
    [[ -n "$TARGET" ]] || { log "❌ 되돌릴 직전 태그 기록이 없다 ($STATE_DIR/previous_tag)"; exit 4; }
    ;;
  sha-*)
    # 커밋 7자리 태그만 받는다(deploy-images.yml이 올리는 형식). develop · main 같은 움직이는 태그는 받지 않는다
    [[ "$1" =~ ^sha-[0-9a-f]{7}$ ]] || { log "❌ 태그 형식이 아니다: $1 (예: sha-1a2b3c4)"; exit 4; }
    TARGET="$1"
    ;;
  *)
    echo "사용법: $0 sha-<커밋7자리> | --rollback"; exit 4 ;;
esac

if [[ "$TARGET" == "$CURRENT" ]]; then
  log "이미 $TARGET 이 배포돼 있다. 할 일 없음"
  exit 0
fi

log "배포 시작: ${CURRENT:-없음} → $TARGET"

# ---------- 1. 이미지 받기 (실패하면 아무것도 바꾸지 않는다) ----------
if [[ "${DEPLOY_SKIP_PULL:-}" != "1" ]]; then
  if ! IMAGE_TAG="$TARGET" "${COMPOSE[@]}" pull --quiet web backend; then
    log "❌ 이미지를 받지 못했다 ($TARGET). GHCR 로그인 토큰 만료(11/14경)인지 확인: docker login ghcr.io"
    exit 2
  fi
fi

# ---------- 2. 바꿔 띄우고 확인 ----------
SCHEMA_BEFORE="$(schema_rank)"
if start_and_check "$TARGET"; then
  [[ -n "$CURRENT" ]] && echo "$CURRENT" > "$STATE_DIR/previous_tag"
  log "✅ 배포 성공: $TARGET"
  cleanup_old_images "$TARGET" "$CURRENT"
  exit 0
fi

# ---------- 3. 실패 → 직전 태그로 되돌림 ----------
show_failure_logs
SCHEMA_AFTER="$(schema_rank)"
if [[ -n "$SCHEMA_BEFORE" && -n "$SCHEMA_AFTER" && "$SCHEMA_BEFORE" != "$SCHEMA_AFTER" ]]; then
  log "⚠ 새 이미지가 DB 마이그레이션을 적용했다 (installed_rank $SCHEMA_BEFORE → $SCHEMA_AFTER). 스키마는 되돌리지 않는다 — 사람이 확인"
fi

if [[ -z "$CURRENT" ]]; then
  log "❌ 되돌릴 직전 태그가 없다 (.env에 IMAGE_TAG가 없었음). 사람이 확인"
  exit 3
fi

log "되돌림 시작: $TARGET → $CURRENT"
if start_and_check "$CURRENT"; then
  log "↩ 되돌림 성공: $CURRENT 로 복구 (실패한 배포: $TARGET)"
  exit 1
fi
show_failure_logs
log "❌ 되돌림도 실패했다. 서비스가 내려가 있을 수 있다 — 사람이 바로 확인 (deploy/README.md \"자동 배포(CD)\")"
exit 3
