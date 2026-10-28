#!/usr/bin/env bash
# deploy/.env 값을 한 줄씩 안전하게 바꾼다 (BE-59, deploy/README.md "`.env`를 고칠 때")
#
#   ./env-set.sh KEY 값              # 일반 값 (주소 · 사용자명 · 1 같은 표시값)
#   ./env-set.sh --secret KEY        # 비밀값: 화면에 보이지 않게 입력받는다 (명령줄 · 셸 기록에 남지 않음)
#   ./env-set.sh --clear KEY         # 값 비우기 (KEY= 줄은 남긴다)
#   ./env-set.sh --check             # 점검만: 값은 출력하지 않는다
#
# 10/28 RDS 전환 때 실제로 겪은 문제를 막는다.
# - 붙여 넣을 때 터미널이 보내는 신호(창 전환 ESC[I · ESC[O, 붙여 넣기 ESC[200~ · ESC[201~)가 값에 섞임
#   → 지우고, 그래도 남은 제어 문자가 있으면 저장하지 않는다
# - 비밀값 대신 채팅에서 복사한 명령어가 붙여 넣어짐 → 키별 형식 검사로 거절한다
# - nano로 고치다 같은 키가 두 줄이 됨 → 같은 키는 한 줄만 남긴다(처음 자리)
# - sudo로 고쳐 주인이 root로 바뀜 → 파일 주인과 실행 사용자가 다르면 멈춘다
# 값은 어떤 경우에도 출력하지 않는다(길이만). 파일은 같은 폴더 임시 파일(600)에 쓴 뒤 바꿔치기한다.
set -euo pipefail

ENV_FILE="${ENV_FILE:-$(cd "$(dirname "$0")" && pwd)/.env}"

die() { echo "❌ $*" >&2; exit 1; }

usage() { sed -n '2,7p' "$0" | sed 's/^# \{0,1\}//'; exit 2; }

# 파일 주인 (Linux GNU stat · macOS BSD stat)
file_owner() { stat -c %U "$1" 2>/dev/null || stat -f %Su "$1"; }
file_mode() { stat -c %a "$1" 2>/dev/null || stat -f %Lp "$1"; }

# 이름이 비밀값처럼 보이면 명령줄 입력을 막는다 (셸 기록 · ps에 남는다)
is_secret_name() { [[ "$1" =~ (PASSWORD|_KEY|SECRET|TOKEN)$ ]]; }

# 키별 형식 (값을 넣을 때 검사, --check에서도 검사). 모르는 키 · 패턴이 없는 키는 공통 규칙만 본다
key_pattern() {
  case "$1" in
    EXTERNAL_DB_URL)   echo '^jdbc:postgresql://[^/@[:space:]]+/[^?[:space:]]+\?(.*&)?sslmode=(require|verify-ca|verify-full)(&.*)?$' ;;
    GEMINI_API_KEY)    echo '^(AIza[A-Za-z0-9_-]{35}|AQ\.[A-Za-z0-9._-]{20,})$' ;;   # AQ.는 AI Studio 새 형식
    OPENAI_API_KEY)    echo '^sk-[A-Za-z0-9_-]{20,}$' ;;
    ANTHROPIC_API_KEY) echo '^sk-ant-[A-Za-z0-9_-]{20,}$' ;;
    REDIS_PASSWORD)    echo '^[0-9a-f]{32,}$' ;;                                    # openssl rand -hex 24
    *PASSWORD)         echo '^[A-Za-z0-9!@%^&*()_+=.,:;?/~<>{}|-]{12,128}$' ;;     # $ # 따옴표 \ 공백 금지
    REAL_SEED_DIR)     echo '^/[^[:space:]]+$' ;;
    EXTERNAL_DB_ALLOWS_REAL_SEED|PIPELINE_SERVICE|MONITORING_AGENT) echo '^1$' ;;
    *)                 echo '' ;;
  esac
}

# 공통 규칙: 공백 · 따옴표 · $ · 백슬래시 · 제어 문자 금지 (Compose가 다르게 읽거나 값이 잘린다)
check_value() {
  local key="$1" val="$2" pat
  if printf '%s' "$val" | LC_ALL=C grep -q '[[:cntrl:]]'; then
    echo "제어 문자(ESC 등)가 남아 있다"; return 1
  fi
  case "$val" in
    *[[:space:]]*) echo "공백이 있다"; return 1 ;;
    *\"*|*\'*|*\`*) echo "따옴표가 있다"; return 1 ;;
    *\$*) echo "\$가 있다 (Compose가 변수로 읽는다)"; return 1 ;;
    *\\*) echo "백슬래시가 있다"; return 1 ;;
  esac
  pat="$(key_pattern "$key")"
  if [[ -n "$pat" && ! "$val" =~ $pat ]]; then
    echo "$key 형식이 아니다"; return 1
  fi
  return 0
}

# 터미널이 붙여 넣기 · 창 전환 때 끼워 넣는 신호와 줄 끝 CR을 지운다
strip_terminal_noise() {
  local v="$1"
  v="${v//$'\e[200~'/}"; v="${v//$'\e[201~'/}"
  v="${v//$'\e[I'/}";    v="${v//$'\e[O'/}"
  v="${v//$'\r'/}"
  # 앞뒤 공백만 지운다 (가운데 공백은 check_value가 거절)
  v="${v#"${v%%[![:space:]]*}"}"; v="${v%"${v##*[![:space:]]}"}"
  printf '%s' "$v"
}

preflight() {
  [[ -f "$ENV_FILE" ]] || die "$ENV_FILE 이 없다 (.env.example을 복사해 만든다)"
  [[ -r "$ENV_FILE" && -w "$ENV_FILE" ]] || die "$ENV_FILE 을 읽거나 쓸 수 없다 — 주인 · 권한을 본다 (ls -l)"
  local owner me
  owner="$(file_owner "$ENV_FILE")"; me="$(id -un)"
  [[ "$owner" == "$me" ]] || die ".env 주인($owner)과 실행 사용자($me)가 다르다 — sudo -u $owner $0 … 로 실행한다"
}

# KEY=값 반영: 있으면 그 자리에서 바꾸고, 없으면 끝에 추가, 같은 키의 나머지 줄은 지운다
write_key() {
  local key="$1" val="$2" dir tmp
  dir="$(dirname "$ENV_FILE")"
  tmp="$(umask 077; mktemp "$dir/.env.tmp.XXXXXX")" || die "임시 파일을 만들 수 없다"
  trap 'rm -f "$tmp"' EXIT
  # 값은 환경변수로 넘긴다 (awk -v는 백슬래시를 해석하고, 명령줄은 ps에 보인다)
  if ! V="$val" awk -v k="$key" '
        index($0, k "=") == 1 { if (!done) { print k "=" ENVIRON["V"]; done = 1 }; next }
        { print }
        END { if (!done) print k "=" ENVIRON["V"] }' "$ENV_FILE" > "$tmp"; then
    die "$key 저장 실패 (.env는 그대로)"
  fi
  chmod 600 "$tmp"
  mv "$tmp" "$ENV_FILE"
  trap - EXIT
}

valid_key_name() { [[ "$1" =~ ^[A-Z][A-Z0-9_]*$ ]] || die "키 이름이 이상하다: $1 (대문자 · 숫자 · _만)"; }

cmd_set() {
  local key="$1" val reason
  valid_key_name "$key"
  is_secret_name "$key" && die "$key 는 비밀값이다 — 명령줄에 쓰지 말고 ./env-set.sh --secret $key 로 넣는다"
  val="$(strip_terminal_noise "$2")"
  reason="$(check_value "$key" "$val")" || die "$key 저장 안 함 — $reason"
  preflight
  write_key "$key" "$val"
  echo "✅ $key=$val"
}

cmd_secret() {
  local key="$1" raw val reason
  valid_key_name "$key"
  preflight
  [[ -t 0 ]] || die "--secret은 터미널에서 직접 입력한다 (파이프 입력 불가)"
  printf '\e[?1004l\e[?2004l' > /dev/tty 2>/dev/null || true   # 창 전환 · 붙여 넣기 신호를 끈다
  read -rs -p "$key 붙여 넣기(화면에 안 보임): " raw; echo
  val="$(strip_terminal_noise "$raw")"; raw=""
  [[ -n "$val" ]] || die "$key 빈 값 — 저장 안 함 (비우려면 --clear)"
  reason="$(check_value "$key" "$val")" || die "$key 저장 안 함 — $reason (길이 ${#val}). 채팅에서 명령어를 복사하면 클립보드가 바뀐다 — 값을 다시 복사해 실행"
  write_key "$key" "$val"
  echo "✅ $key 저장 (길이 ${#val})"
}

cmd_clear() {
  local key="$1"
  valid_key_name "$key"
  preflight
  write_key "$key" ""
  echo "✅ $key 비움"
}

cmd_check() {
  [[ -f "$ENV_FILE" ]] || die "$ENV_FILE 이 없다"
  local bad=0 key val n reason
  echo "== 파일: $ENV_FILE"
  local owner me
  owner="$(file_owner "$ENV_FILE")"; me="$(id -un)"
  echo "   주인 $owner · 권한 $(file_mode "$ENV_FILE")"
  # 값을 넣을 때(preflight)와 같은 기준: 주인이 바뀌면(sudo로 고침 등) 백업 · 자동 배포가 읽지 못한다
  [[ "$owner" == "$me" ]] || { echo "❌ .env 주인($owner)과 실행 사용자($me)가 다르다"; bad=1; }
  [[ "$(file_mode "$ENV_FILE")" == "600" ]] || { echo "❌ 권한이 600이 아니다"; bad=1; }
  n="$(LC_ALL=C grep -c $'\e' "$ENV_FILE" || true)"
  [[ "$n" == "0" ]] || { echo "❌ ESC 문자가 있는 줄 ${n}개 (붙여 넣기 신호가 섞임)"; bad=1; }
  n="$(find "$(dirname "$ENV_FILE")" -maxdepth 1 -name '.env.tmp.*' | wc -l | tr -d ' ')"
  [[ "$n" == "0" ]] || { echo "⚠️  남은 임시 파일 ${n}개 (.env.tmp.*)"; }
  echo "== 같은 키가 두 줄 이상"
  n="$( { grep -E '^[A-Z][A-Z0-9_]*=' "$ENV_FILE" || true; } | cut -d= -f1 | sort | uniq -d)"
  if [[ -n "$n" ]]; then echo "$n" | sed 's/^/❌ /'; bad=1; else echo "   없음"; fi
  echo "== 형식 (값은 출력하지 않는다)"
  while IFS= read -r key; do
    # deploy.sh(env_value)처럼 같은 키가 여러 줄이면 마지막 줄이 실제 값이다
    val="$(grep -E "^${key}=" "$ENV_FILE" | tail -1 | cut -d= -f2- || true)"
    [[ -n "$val" ]] || continue
    # 형식 패턴이 없는 키도 공통 규칙($ · 따옴표 · 공백 · 제어 문자)은 본다 — 키별 패턴은 check_value 안에서만 고른다
    if reason="$(check_value "$key" "$val")"; then
      printf '   ✅ %-30s 길이 %s\n' "$key" "${#val}"
    else
      printf '   ❌ %-30s %s\n' "$key" "$reason"; bad=1
    fi
  done < <( { grep -oE '^[A-Z][A-Z0-9_]*=' "$ENV_FILE" || true; } | tr -d '=' | sort -u)
  (( bad == 0 )) && echo "✅ 점검 통과" || { echo "❌ 위 항목을 고친 뒤 --reapply"; return 1; }
}

case "${1:-}" in
  --secret) [[ $# -eq 2 ]] || usage; cmd_secret "$2" ;;
  --clear)  [[ $# -eq 2 ]] || usage; cmd_clear "$2" ;;
  --check)  [[ $# -eq 1 ]] || usage; cmd_check ;;
  -h|--help|"") usage ;;
  -*) usage ;;
  *)        [[ $# -eq 2 ]] || usage; cmd_set "$1" "$2" ;;
esac
