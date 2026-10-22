#!/usr/bin/env bash
# 공용 개발 DB(Supabase)에 붙여 로컬 백엔드를 띄운다 (COMMON-59, backend/SHARED-DEV-DB.md)
#
#   ./run-shared-db.sh             # 팀원: Flyway를 끄고 접속 (공용 DB의 스키마 · 데이터를 바꾸지 않는다)
#   ./run-shared-db.sh --migrate   # 담당자: develop 최신에서만, 새 마이그레이션 · 시드를 공용 DB에 적용
#   ./run-shared-db.sh --check     # 띄우지 않고 설정 검사만
#
# 실행 전에 막는 실수
#   - 접속 파일이 없거나 예시 값(PROJECT_REF · CHANGE_ME · 꺾쇠)이 남아 있음
#   - 트랜잭션 풀러(6543) · 직접 연결(db.<ref>.supabase.co, IPv6 전용) 주소 — 세션 풀러(5432)만 쓴다
#   - 팀원 실행인데 Flyway가 켜짐 → false로 고정 / 관리자 초기 계정 값이 들어 있음 → 중단
#   - --migrate인데 develop 최신이 아님(브랜치 작업 중인 마이그레이션이 공용 DB에 들어가는 것을 막는다)
#
# macOS 기본 bash 3.2에서도 돈다. 값(비밀번호)은 출력하지 않는다.
set -euo pipefail

cd "$(dirname "$0")"

MODE=member
case "${1:-}" in
  "")        ;;
  --migrate) MODE=migrate ;;
  --check)   MODE=check ;;
  -h|--help) sed -n '2,13p' "$0"; exit 0 ;;
  *) echo "❌ 알 수 없는 옵션: $1 (--migrate · --check)"; exit 2 ;;
esac

fail() { echo "❌ $*"; echo "   절차: backend/SHARED-DEV-DB.md"; exit 1; }
info() { echo "[공용 DB] $*"; }

# ---------- 1) 접속 파일 ----------
# 예시 파일(커밋됨)에 실제 값을 채우는 실수를 막는다 — 그대로 커밋되면 DB 비밀번호가 저장소에 올라간다
if [ -f .env.supabase.example ] && ! grep -qE '^DB_PASSWORD=CHANGE_ME[[:space:]]*$' .env.supabase.example; then
  fail ".env.supabase.example(커밋되는 예시 파일)에 실제 값이 들어 있다. 그 파일을 .env.supabase로 옮기고(mv) 예시 파일은 git checkout으로 되돌린다"
fi
[ -f .env.local ]    || fail ".env.local이 없다 (JWT_SECRET 등 로컬 실행 값 — README \"로컬 실행\")"
[ -f .env.supabase ] || fail ".env.supabase가 없다. cp .env.supabase.example .env.supabase 후 세 값(DB_URL · DB_USERNAME · DB_PASSWORD)을 채운다"

set -a
# shellcheck disable=SC1091
. ./.env.local
# shellcheck disable=SC1091
. ./.env.supabase
set +a

# ---------- 2) 값 검사 ----------
for v in DB_URL DB_USERNAME DB_PASSWORD; do
  eval "val=\${$v:-}"
  # shellcheck disable=SC2154
  [ -n "$val" ] || fail "${v}가 비어 있다"
  case "$val" in
    *PROJECT_REF*|*CHANGE_ME*|*'<'*|*'>'*) fail "${v}에 예시 값이 남아 있다 — Supabase 대시보드 값으로 바꾼다" ;;
  esac
done

case "$DB_URL" in
  *@*)                     fail "DB_URL에 사용자명 · 비밀번호(…@)가 들어 있다. Supabase의 postgresql://… 문자열을 그대로 붙이지 말고, 호스트만 jdbc:postgresql://<호스트>:5432/postgres?sslmode=require 형식으로 넣는다(사용자명 · 비밀번호는 DB_USERNAME · DB_PASSWORD에)" ;;
  *:6543*)                 fail "포트 6543은 트랜잭션 풀러다(prepared statement 미지원 → JPA · Flyway 오류). Session pooler 주소(:5432)를 쓴다" ;;
  *//db.*.supabase.co*)    fail "직접 연결 주소(db.<ref>.supabase.co)는 IPv6 전용이라 대부분 붙지 않는다. Session pooler 주소를 쓴다" ;;
  *pooler.supabase.com:5432/*) ;;
  *) fail "DB_URL이 Supabase 세션 풀러 주소가 아니다 (…pooler.supabase.com:5432/postgres). 로컬 DB로 띄우려면 이 스크립트 대신 ./gradlew bootRun" ;;
esac

case "$DB_USERNAME" in
  postgres.*) ;;
  *) fail "DB_USERNAME은 postgres.<프로젝트 ref> 형식이다 (세션 풀러는 ref가 붙는다)" ;;
esac

# ---------- 3) 모드별 ----------
if [ "$MODE" = migrate ]; then
  branch=$(git rev-parse --abbrev-ref HEAD 2>/dev/null) || fail "--migrate는 저장소 안(backend 폴더)에서 실행한다"
  [ "$branch" = develop ] || fail "--migrate는 develop에서만 실행한다 (지금: $branch). 브랜치의 마이그레이션은 develop 머지 후 적용한다"
  git fetch -q origin develop
  [ "$(git rev-parse HEAD)" = "$(git rev-parse origin/develop)" ] || fail "develop이 origin/develop과 다르다. git pull 후 다시 실행한다"
  [ -z "$(git status --porcelain -- src/main/resources/db)" ] || fail "마이그레이션 · 시드 폴더에 커밋하지 않은 변경이 있다"
  # 실제 사건 시드 서브모듈이 공개 저장소가 가리키는 커밋과 다르면 예전 시드가 들어간다(README "로컬 실행" BE-29)
  case "$(git submodule status private-seed 2>/dev/null | cut -c1)" in
    +) info "⚠️ private-seed가 저장소가 가리키는 커밋과 다르다 — 저장소 루트에서 git submodule update --init backend/private-seed" ;;
    -) info "실제 사건 시드 서브모듈이 비어 있다 — 가상 사건 시드만 적용된다(실데이터는 팀 합의 후)" ;;
  esac
  export SPRING_FLYWAY_ENABLED=true
  info "담당자 모드 — develop $(git rev-parse --short HEAD) 기준으로 공용 DB에 마이그레이션 · 시드를 적용한다"
  if [ -n "${ADMIN_BOOTSTRAP_EMAIL:-}" ]; then
    info "관리자 초기 계정 값이 있다 — 계정이 없으면 이번에 만든다. 만든 뒤 .env.supabase에서 지운다"
  fi
else
  if [ -n "${ADMIN_BOOTSTRAP_EMAIL:-}" ] || [ -n "${ADMIN_BOOTSTRAP_PASSWORD:-}" ]; then
    fail "관리자 초기 계정(ADMIN_BOOTSTRAP_*)은 담당자만 --migrate와 함께 쓴다. .env.supabase에서 비운다"
  fi
  if [ "${SPRING_FLYWAY_ENABLED:-false}" != false ]; then
    info "SPRING_FLYWAY_ENABLED=${SPRING_FLYWAY_ENABLED} → false로 고정한다 (팀원은 공용 DB 스키마를 바꾸지 않는다)"
  fi
  export SPRING_FLYWAY_ENABLED=false
fi

# 출력용 호스트: 사용자 정보(…@)가 있어도 절대 찍지 않는다
host=${DB_URL#*//}; host=${host%%/*}; host=${host##*@}
info "접속: ${host} · 사용자 ${DB_USERNAME%%.*}.*** · Flyway ${SPRING_FLYWAY_ENABLED} · 연결 수 최대 ${SPRING_DATASOURCE_HIKARI_MAXIMUM_POOL_SIZE:-기본}"

if [ "$MODE" = check ]; then
  info "설정 검사 통과 (띄우지 않음)"
  exit 0
fi

# ---------- 4) Redis는 로컬 (postgres는 띄우지 않는다) ----------
if command -v docker >/dev/null 2>&1; then
  if docker compose up -d redis >/dev/null 2>&1; then
    info "로컬 Redis 실행 확인"
  else
    info "⚠️ 로컬 Redis를 띄우지 못했다 — docker compose up -d redis를 직접 실행한다"
  fi
else
  info "⚠️ docker가 없다 — 로컬 Redis(127.0.0.1:6379)가 떠 있어야 한다"
fi

exec ./gradlew bootRun
