#!/usr/bin/env bash
# 공용 개발 DB(Supabase)에 붙여 로컬 백엔드를 띄운다 (COMMON-59, backend/SHARED-DEV-DB.md)
#
#   ./run-shared-db.sh                                # 팀원: Flyway를 끄고 접속 (공용 DB의 스키마 · 데이터를 바꾸지 않는다)
#   ./run-shared-db.sh --migrate                      # 담당자: develop 최신에서만, 마이그레이션 + 가상 사건 시드 적용
#   ./run-shared-db.sh --migrate --with-private-seed  # 담당자: 팀 합의 후에만, 실제 사건 시드(private-seed)까지 적용
#   ./run-shared-db.sh --check                        # 띄우지 않고 설정 검사만
#
# 실행 전에 막는 실수
#   - 접속 파일이 없거나 예시 값(PROJECT_REF · CHANGE_ME · 꺾쇠)이 남아 있음 / 예시 파일(커밋됨)에 실제 값
#   - DB_URL에 사용자 정보(…@) · 트랜잭션 풀러(6543) · 직접 연결(IPv6 전용) · sslmode 없음 또는 비암호화
#   - DB_URL 대신 다른 DB를 가리킬 수 있는 환경변수(SPRING_DATASOURCE_* · SPRING_APPLICATION_JSON 등)
#   - 팀원 실행인데 Flyway가 켜짐 → false로 고정 / 관리자 초기 계정 값이 들어 있음 → 중단
#   - --migrate인데 develop 최신이 아님 · 실제 사건 시드에 사용 불가 데이터(LBox)가 있음
#   - DDL_AUTO는 모든 모드에서 validate로 고정 (스키마는 Flyway로만 바뀐다)
#
# macOS 기본 bash 3.2에서도 돈다. 값(비밀번호)은 출력하지 않는다.
set -euo pipefail

cd "$(dirname "$0")"

MODE=member
PRIVATE_SEED=false
for arg in "$@"; do
  case "$arg" in
    --migrate)           MODE=migrate ;;
    --with-private-seed) PRIVATE_SEED=true ;;
    --check)             MODE=check ;;
    -h|--help)           sed -n '2,17p' "$0"; exit 0 ;;
    *) echo "❌ 알 수 없는 옵션: $arg (--migrate · --with-private-seed · --check)"; exit 2 ;;
  esac
done

fail() { echo "❌ $*"; echo "   절차: backend/SHARED-DEV-DB.md"; exit 1; }
info() { echo "[공용 DB] $*"; }

if [ "$PRIVATE_SEED" = true ] && [ "$MODE" != migrate ]; then
  fail "--with-private-seed는 --migrate와 함께만 쓴다 (담당자 · 팀 합의 후)"
fi

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

# ---------- 2) 다른 DB를 가리킬 수 있는 설정 차단 ----------
# Spring은 SPRING_DATASOURCE_URL · SPRING_APPLICATION_JSON 등을 application.yml의 DB_URL보다 먼저 쓴다.
# 그대로 두면 아래에서 검사한 DB_URL과 다른 DB로 붙거나(--migrate면 그 DB에 적용) 스키마가 바뀔 수 있다.
# 연결 풀 크기 두 개만 허용한다. 부모 셸 · .env.local · .env.supabase 어디서 왔든 같은 기준으로 본다.
for name in $(compgen -e); do
  case "$name" in
    # 연결 풀 크기 — 공식 형식(하이픈 제거)과 밑줄 형식 모두 Spring Boot 4.1에서 적용된다(로컬 실측: 기본 10 → 둘 다 3)
    SPRING_DATASOURCE_HIKARI_MAXIMUMPOOLSIZE|SPRING_DATASOURCE_HIKARI_MINIMUMIDLE) ;;
    SPRING_DATASOURCE_HIKARI_MAXIMUM_POOL_SIZE|SPRING_DATASOURCE_HIKARI_MINIMUM_IDLE) ;;
    SPRING_DATASOURCE_*|SPRING_APPLICATION_JSON|SPRING_CONFIG_*|SPRING_PROFILES_*|SPRING_JPA_HIBERNATE_DDL*)
      fail "환경변수 $name 이 있으면 DB_URL과 다른 DB · 설정이 쓰일 수 있다. 공용 DB 실행 전에 지운다(셸 export · .env.local · .env.supabase 확인)" ;;
    SPRING_FLYWAY_ENABLED) ;;
    SPRING_FLYWAY_*)
      fail "환경변수 $name 이 있으면 Flyway가 다른 DB · 위치를 쓸 수 있다. 지운다" ;;
    JAVA_TOOL_OPTIONS|JDK_JAVA_OPTIONS|_JAVA_OPTIONS)
      eval "opts=\${$name:-}"
      # shellcheck disable=SC2154
      case "$opts" in *-Dspring.*|*-Dflyway.*) fail "$name 에 -Dspring.* · -Dflyway.* 설정이 있다. 공용 DB 실행 전에 지운다" ;; esac ;;
  esac
done

# ---------- 3) 값 검사 ----------
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

# 암호화(TLS)를 반드시 쓴다 — 비밀번호가 오가는 외부 연결이다. require는 암호화만 하고 서버 인증서는 확인하지 않는다(문서 참고)
case "$DB_URL" in
  *[?\&]sslmode=require|*[?\&]sslmode=require\&*|*[?\&]sslmode=verify-ca*|*[?\&]sslmode=verify-full*) ;;
  *) fail "DB_URL에 sslmode=require(또는 verify-ca · verify-full)가 없다. 없거나 disable · allow · prefer면 암호화 없이 붙을 수 있다 — 주소 끝에 ?sslmode=require를 붙인다" ;;
esac

case "$DB_USERNAME" in
  postgres.*) ;;
  *) fail "DB_USERNAME은 postgres.<프로젝트 ref> 형식이다 (세션 풀러는 ref가 붙는다)" ;;
esac

# 스키마는 Flyway로만 바꾼다 — .env.local 등에 DDL_AUTO=update가 있어도 공용 스키마를 Hibernate가 건드리지 않게 고정
if [ "${DDL_AUTO:-validate}" != validate ]; then
  info "DDL_AUTO=${DDL_AUTO} → validate로 고정한다 (공용 DB 스키마는 Flyway로만 바뀐다)"
fi
export DDL_AUTO=validate

# ---------- 4) 모드별 ----------
if [ "$MODE" = migrate ]; then
  branch=$(git rev-parse --abbrev-ref HEAD 2>/dev/null) || fail "--migrate는 저장소 안(backend 폴더)에서 실행한다"
  [ "$branch" = develop ] || fail "--migrate는 develop에서만 실행한다 (지금: $branch). 브랜치의 마이그레이션은 develop 머지 후 적용한다"
  git fetch -q origin develop
  [ "$(git rev-parse HEAD)" = "$(git rev-parse origin/develop)" ] || fail "develop이 origin/develop과 다르다. git pull 후 다시 실행한다"
  [ -z "$(git status --porcelain -- src/main/resources/db)" ] || fail "마이그레이션 · 시드 폴더에 커밋하지 않은 변경이 있다"

  # Flyway 위치는 여기서 정한다 — 셸 · .env.local의 FLYWAY_LOCATIONS(검증 안 한 로컬 SQL일 수 있다)는 쓰지 않는다
  if [ -n "${FLYWAY_LOCATIONS:-}" ]; then
    info "FLYWAY_LOCATIONS 값은 무시한다 (--migrate는 저장소 마이그레이션 · 시드만 적용)"
  fi
  locations="classpath:db/migration,classpath:db/seed"
  seed_label="가상 사건 시드만"
  if [ "$PRIVATE_SEED" = true ]; then
    # 실제 사건 시드는 팀 합의 후에만, 공개 저장소가 가리키는 커밋 그대로일 때만 올린다
    case "$(git submodule status private-seed 2>/dev/null | cut -c1)" in
      -|"") fail "private-seed 서브모듈이 비어 있다 — 저장소 루트에서 git submodule update --init backend/private-seed" ;;
      +)    fail "private-seed가 저장소가 가리키는 커밋과 다르다 — 저장소 루트에서 git submodule update --init backend/private-seed" ;;
    esac
    ls private-seed/seed/*.sql >/dev/null 2>&1 || fail "private-seed/seed에 SQL이 없다"
    # 사용 불가 데이터(LBox Open — 라이선스상 사용 불가, 10/12)는 합의와 별개로 외부 서비스에 올리지 않는다.
    # 지금 시드에는 없다(BE-54에서 삭제). 다시 들어오면 여기서 막는다
    # grep 종료 코드: 0 = LBox 있음, 1 = 없음, 2 이상 = 읽기 오류 → 1일 때만 통과한다.
    # 읽기 오류를 "없음"으로 보면 검사하지 못한 파일이 올라간다 (PR #281 리뷰, deploy/deploy.sh와 같은 검사)
    lbox_rc=0
    grep -qil "'LBOX'" private-seed/seed/*.sql || lbox_rc=$?
    case "$lbox_rc" in
      1) ;;
      0) fail "실제 사건 시드에 LBox 행('LBOX')이 있다 — 사용 불가 데이터라 공용 DB(외부 서비스)에 올리지 않는다(규칙 6)" ;;
      *) fail "실제 사건 시드의 LBox 검사를 끝내지 못했다(grep 종료 코드 ${lbox_rc}) — private-seed/seed 파일을 읽을 수 있는지 확인한다" ;;
    esac
    locations="${locations},filesystem:$(pwd)/private-seed/seed"
    seed_label="가상 + 실제 사건 시드 (팀 합의 후)"
  fi
  export FLYWAY_LOCATIONS="$locations"
  export SPRING_FLYWAY_ENABLED=true
  info "담당자 모드 — develop $(git rev-parse --short HEAD) 기준으로 공용 DB에 적용한다 (시드: ${seed_label})"
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
info "접속: ${host} · 사용자 ${DB_USERNAME%%.*}.*** · Flyway ${SPRING_FLYWAY_ENABLED} · DDL validate · 연결 수 최대 ${SPRING_DATASOURCE_HIKARI_MAXIMUMPOOLSIZE:-${SPRING_DATASOURCE_HIKARI_MAXIMUM_POOL_SIZE:-기본(10)}}"

if [ "$MODE" = check ]; then
  info "설정 검사 통과 (띄우지 않음)"
  exit 0
fi

# ---------- 5) Redis는 로컬 (postgres는 띄우지 않는다) ----------
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
