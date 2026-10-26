#!/usr/bin/env bash
# 운영 서버 배포 (BE-60, WBS 7.1.3): 지정한 sha 이미지로 바꾸고, 실패하면 직전 이미지로 되돌린다
#
# 사용법 (서버의 deploy 폴더에서):
#   ./deploy.sh sha-1a2b3c4     # 그 커밋의 이미지로 배포
#   ./deploy.sh --rollback      # 직전에 배포했던 이미지로 되돌린다
#   ./deploy.sh --reapply       # 이미지는 그대로, .env 설정(외부 DB 접속 등)만 다시 적용한다 (BE-59)
# 보통은 사람이 부르지 않고 cd-agent.sh(systemd 타이머)가 main의 새 이미지를 보고 부른다.
#
# 순서: 이미지 받기 → .env의 IMAGE_TAG 교체 → up -d → 백엔드 준비 대기 → 스모크 테스트
#       → 실패하면 직전 IMAGE_TAG로 되돌려 같은 확인을 한 번 더 한다
#
# 되돌리지 않는 것: DB 스키마(Flyway). 새 이미지가 마이그레이션을 적용한 뒤 실패하면 앱만 되돌리고 경고를 남긴다.
#   이전 앱은 자기가 모르는 새 마이그레이션을 무시하고 뜬다(Flyway 기본 ignoreMigrationPatterns "*:future").
#   스키마를 되돌리는 것은 데이터 손상 위험이 있어 사람이 판단한다 (deploy/README.md "자동 배포(CD)")
#
# --reapply: cd-agent는 이미지 태그가 바뀔 때만 배포하므로, .env만 바꾼 변경(외부 DB 전환 · 되돌리기 등)은 이걸로 적용한다.
#   지금 IMAGE_TAG 그대로 같은 확인(백엔드 준비 · 스모크 테스트)을 한다. 실패해도 자동으로 되돌리지 않는다 —
#   되돌릴 대상이 이미지가 아니라 사람이 바꾼 .env이기 때문이다(고치기 전 사본으로 되돌린 뒤 다시 --reapply)
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
  # pipeline은 오버레이를 켠 서버에만 있다. 없으면 docker images가 아무것도 내지 않아 그냥 넘어간다 (COMMON-50)
  for name in web backend pipeline; do
    docker images --format '{{.Repository}}:{{.Tag}}' "$prefix-$name" 2>/dev/null | while read -r ref; do
      [[ "$ref" =~ :sha-[0-9a-f]{7}$ ]] || continue
      [[ "$ref" == *":$keep_a" || "$ref" == *":$keep_b" ]] && continue
      docker rmi "$ref" >/dev/null 2>&1 && log "정리: $ref"
    done
  done
  docker image prune -f >/dev/null 2>&1
}

COMPOSE=(docker compose -f "$DEPLOY_DIR/docker-compose.prod.yml")
# 외부 DB 모드 (BE-59): .env에 EXTERNAL_DB_URL이 있으면 postgres 컨테이너 대신 외부 PostgreSQL(Supabase → RDS)에 붙는다.
# 오버레이를 빼먹으면 backend가 다시 같은 서버의 postgres로 붙어 "어느 DB에 썼는지"가 조용히 바뀌므로, 플래그로 자동으로 겹친다
EXTERNAL_DB_URL_VALUE="$(env_value EXTERNAL_DB_URL | tr -d "\"' \r")"
# 실제 사건 시드 경로: 아래 외부 DB 검사와 오버레이 판정이 같은 값(따옴표 · 공백 · CR 정리)을 보게 한 번만 읽는다.
# 다르게 읽으면 REAL_SEED_DIR="" 같은 줄이 검사는 통과하고 오버레이만 붙는다 (BE-59)
REAL_SEED_DIR_VALUE="$(env_value REAL_SEED_DIR | tr -d "\"' \r")"
# 검사한 경로를 Compose도 그대로 쓰게 한다 (PR #281 CodeRabbit 리뷰). Compose는 셸에 export된 값을 .env보다
# 우선 적용하므로, 셸에 다른 REAL_SEED_DIR이 남아 있으면 검사하지 않은 폴더가 /real-seed에 붙을 수 있다.
# 이 스크립트 프로세스에서만 덮어쓴다(호출한 셸에는 영향 없음) — 모니터링 값 unset과 같은 원칙
export REAL_SEED_DIR="$REAL_SEED_DIR_VALUE"
EXTERNAL_DB=0
if [[ -n "$EXTERNAL_DB_URL_VALUE" ]]; then
  # 두 값이 없으면 compose가 뒤늦게 다른 이유처럼 실패하므로 먼저 막는다 (값은 출력하지 않는다)
  for required in EXTERNAL_DB_USERNAME EXTERNAL_DB_PASSWORD; do
    [[ -n "$(env_value "$required")" ]] || {
      log "❌ EXTERNAL_DB_URL이 있는데 .env에 $required 가 없다 (deploy/docker-compose.external-db.yml)"
      exit 4
    }
  done
  # 외부 연결이라 TLS를 강제한다 — sslmode가 없거나 disable · allow · prefer면 암호화 없이 붙을 수 있다
  case "$EXTERNAL_DB_URL_VALUE" in
    *[?\&]sslmode=require|*[?\&]sslmode=require\&*|*[?\&]sslmode=verify-ca*|*[?\&]sslmode=verify-full*) ;;
    *) log "❌ EXTERNAL_DB_URL에 sslmode=require(또는 verify-ca · verify-full)가 없다 — 주소 끝에 ?sslmode=require를 붙인다"; exit 4 ;;
  esac
  # 외부 DB(Supabase → RDS)에 실제 사건 시드는 팀 합의 표시가 있을 때만 올린다 (backend/SHARED-DEV-DB.md 규칙 6).
  # 경로만 넣은 실수로 비공개 데이터가 외부 DB에 올라가지 않게, 사람이 EXTERNAL_DB_ALLOWS_REAL_SEED=1을 따로 넣어야 한다
  if [[ -n "$REAL_SEED_DIR_VALUE" ]]; then
    if [[ "$(env_value EXTERNAL_DB_ALLOWS_REAL_SEED | tr -d "\"' \r")" != "1" ]]; then
      log "❌ 외부 DB 모드에서 실제 사건 시드(REAL_SEED_DIR)는 팀 합의 후 EXTERNAL_DB_ALLOWS_REAL_SEED=1을 넣었을 때만 쓴다. 합의 전이면 .env에서 REAL_SEED_DIR을 비운다"
      exit 4
    fi
    # 폴더가 비어 있거나 경로가 틀리면 Flyway가 아무것도 넣지 않고 성공으로 끝나 알아차리기 어렵다 — 먼저 막는다.
    # 이름만 *.sql인 폴더는 세지 않고, 읽을 수 있는 일반 파일이 하나 이상 있어야 통과한다 (PR #281 CodeRabbit 리뷰)
    seed_sql_found=0
    for seed_sql in "$REAL_SEED_DIR_VALUE"/*.sql; do
      [[ -f "$seed_sql" && -r "$seed_sql" ]] && { seed_sql_found=1; break; }
    done
    if (( ! seed_sql_found )); then
      log "❌ REAL_SEED_DIR(${REAL_SEED_DIR_VALUE})에 SQL 파일이 없다 — 서버에 비공개 시드를 먼저 받는다 (deploy/README.md \"운영 데이터\")"
      exit 4
    fi
    # 사용 불가 데이터(LBox Open, 라이선스상 사용 불가 — 10/12)는 합의와 별개로 외부 DB에 올리지 않는다.
    # 지금 시드에는 없다(BE-54에서 삭제). 다시 들어오면 여기서 막는다 (backend/run-shared-db.sh와 같은 검사)
    if grep -qils "'LBOX'" "$REAL_SEED_DIR_VALUE"/*.sql; then
      log "❌ 실제 사건 시드에 LBox 행('LBOX')이 있다 — 사용 불가 데이터라 외부 DB에 올리지 않는다 (SHARED-DEV-DB.md 규칙 6)"
      exit 4
    fi
    log "외부 DB에 실제 사건 시드를 함께 적용한다 (팀 합의 표시 EXTERNAL_DB_ALLOWS_REAL_SEED=1)"
  fi
  # 오버레이의 depends_on: !override는 Compose 2.24.4부터 안다. 모르는 버전은 postgres 의존을 그대로 두거나 거부한다
  compose_version="$(docker compose version --short 2>/dev/null | sed 's/^v//')"
  if [[ -z "$compose_version" ]] || [[ "$(printf '%s\n' 2.24.4 "$compose_version" | sort -V | head -1)" != "2.24.4" ]]; then
    log "❌ 외부 DB 모드에는 Docker Compose 2.24.4 이상이 필요하다 (지금: ${compose_version:-확인 불가})"
    exit 4
  fi
  COMPOSE+=(-f "$DEPLOY_DIR/docker-compose.external-db.yml")
  EXTERNAL_DB=1
fi
# 실제 사건 시드를 겹쳐 띄우는 서버면 같은 구성으로 다시 띄운다 (README "실제 사건 시드")
if [[ -n "$REAL_SEED_DIR_VALUE" ]]; then
  COMPOSE+=(-f "$DEPLOY_DIR/docker-compose.real-seed.yml")
fi
# 모니터링 에이전트를 올린 서버면 그 오버레이도 함께 쓴다 (COMMON-43, PR #168 리뷰 반영).
# 빼먹으면 자동 배포가 backend를 다시 만들 때 로그 볼륨이 사라져 **로그 수집이 조용히 끊긴다**
# — 에이전트 컨테이너는 그대로 떠 있어서 알아차리기 어렵다 (docker-compose.monitoring-agent.yml 머리말)
# 따옴표 · 캐리지리턴 · 공백을 지우고 판정한다: .env에 MONITORING_AGENT="1"처럼 따옴표를 쓰면
# env_value가 "1"을 그대로 돌려줘 == "1" 비교에 걸리지 않고, 이 분기가 막으려던 "조용히 빠짐"이 재발한다 (PR #168 리뷰 반영)
MONITORING_AGENT_VALUE="$(env_value MONITORING_AGENT | tr -d "\"' \r")"
MONITORING_ENABLED=0
case "$MONITORING_AGENT_VALUE" in
  1)
    # 검사한 값(.env)과 Compose가 실제로 쓰는 값을 같게 만든다 (PR #268 CodeRabbit 리뷰).
    # Compose는 셸에 export된 변수를 .env보다 우선 적용하므로, 셸에 http 주소 등이 남아 있으면 아래 검사는
    # 통과하고 Alloy에는 다른 값이 넘어가 basic_auth 토큰이 평문으로 나갈 수 있다. 이 스크립트 프로세스에서만
    # 지운다(호출한 셸에는 영향 없음) — Grafana Cloud 값은 deploy/.env 하나에서만 온다
    unset GRAFANA_CLOUD_PROM_URL GRAFANA_CLOUD_PROM_USERNAME GRAFANA_CLOUD_PROM_API_KEY \
          GRAFANA_CLOUD_LOKI_URL GRAFANA_CLOUD_LOKI_USERNAME GRAFANA_CLOUD_LOKI_API_KEY MONITORING_ENV
    # Grafana Cloud 주소 · 인증 값(COMMON-64)이 없으면 compose가 뒤늦게 다른 이유처럼 실패하거나, Alloy가 설정 오류로
    # 시작하지 못해 지표 · 로그가 함께 끊긴다. 배포를 시작하기 전에 먼저 막는다.
    # GRAFANA_CLOUD_LOKI_API_KEY는 비워도 된다 — 비우면 Alloy가 지표 토큰을 같이 쓴다(alloy-agent.alloy)
    for required in GRAFANA_CLOUD_PROM_URL GRAFANA_CLOUD_PROM_USERNAME GRAFANA_CLOUD_PROM_API_KEY \
                    GRAFANA_CLOUD_LOKI_URL GRAFANA_CLOUD_LOKI_USERNAME; do
      # 따옴표 · CR · 공백을 지우고 본다 (PR #268 리뷰, 백승호) — MONITORING_AGENT · EXTERNAL_DB_URL 판정과 같은 기준.
      # KEY=""는 Compose가 빈 값으로 보고 :? 오류로 up 도중에 실패하므로, 배포를 시작하기 전에 여기서 막는다
      [[ -n "$(env_value "$required" | tr -d "\"' \r")" ]] || {
        log "❌ MONITORING_AGENT=1인데 .env에 ${required} 가 없다 (deploy/docker-compose.monitoring-agent.yml)"
        exit 4
      }
    done
    # 토큰이 basic_auth로 실려 나가므로 주소는 https만 받는다 (http면 토큰이 평문으로 나간다)
    for url_key in GRAFANA_CLOUD_PROM_URL GRAFANA_CLOUD_LOKI_URL; do
      [[ "$(env_value "$url_key" | tr -d "\"' \r")" == https://* ]] || {
        log "❌ ${url_key}는 https:// 로 시작해야 한다 (토큰이 평문으로 나가지 않게)"
        exit 4
      }
    done
    COMPOSE+=(-f "$DEPLOY_DIR/docker-compose.monitoring-agent.yml")
    MONITORING_ENABLED=1
    ;;
  ""|0) ;;
  *) log "❌ MONITORING_AGENT는 1 또는 빈 값이어야 한다: $MONITORING_AGENT_VALUE"; exit 4 ;;
esac
# AI 판결 파이프라인 서비스를 올린 서버면 그 오버레이도 함께 쓴다 (COMMON-50).
# 빼먹으면 backend를 다시 만들 때 PIPELINE_SERVICE_URL이 사라져 사건 등록(A-14)만 조용히 안 되므로
# 모니터링 오버레이와 같은 방식으로 .env 플래그를 본다. 켜기 전 메모리 확인은
# deploy/docker-compose.pipeline.yml 머리말 참고 (t3.small 여유가 적다)
PIPELINE_SERVICE_VALUE="$(env_value PIPELINE_SERVICE | tr -d "\"' \r")"
PIPELINE_ENABLED=0
case "$PIPELINE_SERVICE_VALUE" in
  1)
    # 키가 하나도 없으면 모든 단계가 실패한다. 판결문 원문을 보내는 extract · court가 쓰는 키를 먼저 본다
    [[ -n "$(env_value ANTHROPIC_API_KEY)" || -n "$(env_value GEMINI_API_KEY)" || -n "$(env_value OPENAI_API_KEY)" ]] || {
      log "❌ PIPELINE_SERVICE=1인데 .env에 LLM 키가 하나도 없다 (ANTHROPIC_API_KEY · GEMINI_API_KEY · OPENAI_API_KEY 중 하나)"
      exit 4
    }
    # 메모리 가드 (PR #219 리뷰 반영). postgres 컨테이너가 아직 있는 서버 = 7.1.2 RDS 전환 전이고,
    # 그때 base 구성이 이미 1.75GB를 잡아 실측 여유가 약 250MB뿐이다. 여기에 이 컨테이너가 붙으면
    # 커널 OOM이 **가장 큰 프로세스(backend)를 먼저 죽일 수 있다.** 문서 경고만으로는 실수를 막지 못하므로
    # 사람이 docker stats로 확인하고 PIPELINE_MEMORY_CHECKED=1을 넣었을 때만 켠다
    # (외부 DB 모드(BE-59)에서는 postgres 컨테이너를 띄우지 않아 이 분기를 타지 않는다 — 옛 컨테이너는 README대로 멈춘다)
    if (( ! EXTERNAL_DB )) \
       && grep -qE '^[[:space:]]+postgres:[[:space:]]*$' "$DEPLOY_DIR/docker-compose.prod.yml" \
       && [[ "$(env_value PIPELINE_MEMORY_CHECKED | tr -d "\"' \r")" != "1" ]]; then
      log "❌ postgres 컨테이너가 있는 서버(RDS 전환 전)에서 파이프라인을 켜려 한다. 메모리 여유가 약 250MB뿐이라"
      log "   backend가 OOM으로 죽을 수 있다. docker stats로 확인한 뒤 .env에 PIPELINE_MEMORY_CHECKED=1을 넣는다"
      log "   (deploy/README.md \"AI 판결 파이프라인 서비스\" 참고)"
      exit 4
    fi
    COMPOSE+=(-f "$DEPLOY_DIR/docker-compose.pipeline.yml")
    PIPELINE_ENABLED=1
    ;;
  ""|0) ;;
  *) log "❌ PIPELINE_SERVICE는 1 또는 빈 값이어야 한다: $PIPELINE_SERVICE_VALUE"; exit 4 ;;
esac
WEB_PORT="$(env_value WEB_PORT)"; WEB_PORT="${WEB_PORT:-80}"
BASE_URL="http://localhost:$WEB_PORT"

# Flyway가 적용한 마지막 번호. postgres 서비스가 없으면(RDS 전환 후) 빈 값 → 스키마 비교를 건너뛴다
# 작은따옴표는 일부러다: $POSTGRES_USER · $POSTGRES_DB는 이 셸이 아니라 postgres 컨테이너 안에서 풀린다(비밀값을 여기서 읽지 않음)
# shellcheck disable=SC2016
schema_rank() {
  # 외부 DB 모드(BE-59)에서는 건너뛴다 — 전환 직후에는 옛 postgres 컨테이너가 아직 떠 있어 엉뚱한 DB의 번호를 읽게 된다.
  # 이 값은 실패 시 "마이그레이션이 적용됐다" 경고에만 쓴다. 외부 DB의 적용 이력은 backend 로그(Flyway)로 본다
  (( EXTERNAL_DB )) && return 0
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

# 파이프라인 서비스를 지금 태그로 바꿔 띄운다 (COMMON-50, PIPELINE_SERVICE=1인 서버만).
# **실패해도 배포를 되돌리지 않고 경고만 남긴다.** 이 컨테이너는 관리자 전용 사건 등록(A-14)에만 쓰여서
# 사용자 화면 · API와 무관하다. 정상인 web · backend 배포를 이것 때문에 되돌리면 손해가 더 크다.
# 대신 조용히 넘어가지는 않는다 — 안 떴으면 로그에 남겨 사람이 보게 한다
start_pipeline() {
  (( PIPELINE_ENABLED )) || return 0
  if ! "${COMPOSE[@]}" up -d --no-build pipeline; then
    log "⚠ 파이프라인 서비스를 띄우지 못했다. 사건 등록(A-14)만 안 되고 나머지는 정상 — 사람이 확인"
    return 0
  fi
  log "파이프라인 서비스 반영됨"
}

# 모니터링 에이전트(Alloy · node-exporter)를 띄운다 (COMMON-64, MONITORING_AGENT=1인 서버만).
# 위 start_and_check는 backend · web만 이름으로 올리므로, 오버레이를 붙여도 이 둘은 따로 올리지 않으면 뜨지 않는다
# (COMMON-64 운영 반영 때 실제로 발견 — 오버레이 · 볼륨은 붙었는데 에이전트 컨테이너가 없었다).
# --no-deps: backend · web을 다시 건드리지 않는다. 설정(alloy-agent.alloy) · 이미지가 바뀌었을 때만 다시 만들어진다.
# 파이프라인과 같은 기준으로 **실패해도 배포를 되돌리지 않고 경고만 남긴다** — 사용자 화면 · API와 무관하고,
# Alloy가 멈추면 Grafana Cloud 경보(no data · BackendScrapeTargetMissing)가 따로 알린다
start_monitoring_agent() {
  (( MONITORING_ENABLED )) || return 0
  if ! "${COMPOSE[@]}" up -d --no-build --no-deps alloy node-exporter; then
    log "⚠ 모니터링 에이전트(alloy · node-exporter)를 띄우지 못했다. 서비스는 정상 — 사람이 확인 (deploy/README.md \"모니터링 에이전트\")"
    return 0
  fi
  log "모니터링 에이전트 반영됨"
}

# 지정한 태그로 띄우고 확인한다. 성공 0 · 실패 1
# 순서가 중요하다: 백엔드를 먼저 바꾸고, 준비된 뒤에 웹을 바꾼다.
#   한 번에 up 하면 Compose가 웹을 먼저 내려 두고 새 백엔드를 기다려서(depends_on: service_healthy), 배포 중에는 화면까지 안 열린다.
#   이 순서면 그동안 기존 웹이 화면을 계속 내주고, API만 백엔드가 다시 뜨는 동안(20 ~ 40초) 잠깐 끊긴다.
#   새 백엔드가 실패하면 웹은 바뀌지 않은 채로 되돌림이 시작된다 (10/16 로컬 실측, docs/decisions/BE-60.md)
start_and_check() {
  local tag="$1"
  set_image_tag "$tag" || { log "❌ .env의 IMAGE_TAG를 바꾸지 못했다"; return 1; }
  # DB · Redis는 이미지가 그대로면 보통 다시 만들지 않는다. 다만 Compose는 이미지가 아니라 "설정"을 보고 다시 만들고,
  # backend의 depends_on(postgres · redis)도 함께 본다 — mem_limit · memswap_limit · 환경변수 같은 설정이 바뀐 배포에서는
  # 여기서 postgres · redis도 다시 만들어진다(COMMON-54, 로컬 실측). 그때는 DB 재기동 + healthcheck만큼 평소보다 오래 끊긴다.
  # 데이터는 볼륨(pgdata · redisdata)에 있어 그대로다. --remove-orphans는 쓰지 않는다(겹쳐 띄운 서비스를 지우지 않게)
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
  # 사용자 경로(web · backend)가 확인된 뒤에 올린다. 되돌림 때도 같은 함수를 타므로 파이프라인도 함께 직전 태그로 돌아간다
  start_pipeline
  start_monitoring_agent
  return 0
}

show_failure_logs() {
  log "---- 실패 원인 확인용 상태 · 로그 (비밀값은 출력되지 않는다) ----"
  "${COMPOSE[@]}" ps
  "${COMPOSE[@]}" logs --no-color --tail=80 backend web
}

# ---------- 인자 ----------
CURRENT="$(env_value IMAGE_TAG)"
REAPPLY=0
case "${1:-}" in
  --reapply)
    [[ -n "$CURRENT" ]] || { log "❌ .env에 IMAGE_TAG가 없다 — 다시 적용할 배포가 없다 (먼저 sha 태그로 배포한다)"; exit 4; }
    REAPPLY=1
    TARGET="$CURRENT"
    ;;
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
    echo "사용법: $0 sha-<커밋7자리> | --rollback | --reapply"; exit 4 ;;
esac

if (( REAPPLY )); then
  log "설정 다시 적용 시작: $CURRENT (이미지 그대로$( (( EXTERNAL_DB )) && echo ", 외부 DB 모드" ))"
  if start_and_check "$CURRENT"; then
    log "✅ 설정 다시 적용 성공: $CURRENT"
    exit 0
  fi
  show_failure_logs
  log "❌ 설정 다시 적용 실패. 자동으로 되돌리지 않는다 — .env를 고치기 전 사본으로 되돌린 뒤 ./deploy.sh --reapply (deploy/README.md \"외부 DB 모드\")"
  exit 3
fi

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
  # 파이프라인 이미지는 따로, 실패를 허용하고 받는다 (COMMON-50). 아직 올라오지 않은 태그라도
  # web · backend 배포까지 막지는 않는다 — 못 받으면 파이프라인만 이전 이미지로 계속 돈다
  if (( PIPELINE_ENABLED )) && ! IMAGE_TAG="$TARGET" "${COMPOSE[@]}" pull --quiet pipeline; then
    log "⚠ 파이프라인 이미지를 받지 못했다 ($TARGET). 이전 이미지로 계속 돈다 — 사람이 확인"
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
