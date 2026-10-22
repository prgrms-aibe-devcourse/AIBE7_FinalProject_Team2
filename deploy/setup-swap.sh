#!/usr/bin/env bash
# EC2 swap 설정 (COMMON-54, deploy/README.md "Swap")
#
#   sudo ./setup-swap.sh           # 없으면 만들고 켠다. 이미 맞게 돼 있으면 아무것도 바꾸지 않는다(다시 실행해도 안전)
#   ./setup-swap.sh --check        # 바꾸지 않고 지금 상태만 본다 (root 불필요)
#   그 밖의 인자는 받지 않는다(실행하지 않고 멈춘다)
#
# 왜: t3.small 2GB에 base 구성이 1.75GB를 할당한다(backend 1g · postgres 512m · redis 256m). swap이 없으면 순간 급증
#     (파이프라인 extract · 이미지 교체 중 컨테이너 두 벌 · apt 등) 때 커널 OOM이 가장 큰 프로세스(backend)를 바로 죽인다.
#     swap은 그때 "죽는 대신 잠깐 느려지는" 안전망이다 — 용량을 늘리는 수단이 아니다(평소에는 쓰지 않게 swappiness를 낮춘다).
#
# 하는 일
#   1) 이미 켜진 swap이 있으면 만들지 않는다(크기만 보여 준다)
#   2) 디스크 여유를 본다 — swap 크기 + 여유분(MIN_FREE_AFTER_GB) 이상이어야 만든다(이미지 · DB · 백업이 같은 30GB를 쓴다)
#   3) SWAP_FILE(기본 /swapfile)을 SWAP_SIZE_GB(기본 2)로 만들고 600 권한 → mkswap → swapon
#   4) 재부팅 뒤에도 켜지게 /etc/fstab에 한 줄(없을 때만)
#   5) vm.swappiness를 SWAPPINESS(기본 10)로 — /etc/sysctl.d/99-lawnambul-swap.conf (재부팅 뒤에도 유지)
#
# 컨테이너는 이 swap을 쓰지 않게 막아 둔다: docker-compose.prod.yml의 backend · postgres · redis에
# memswap_limit = mem_limit (지금과 동작이 같다 — JVM 힙 · Redis가 swap으로 밀리면 장애보다 나쁜 "느린 서버"가 된다).
set -euo pipefail

SWAP_FILE="${SWAP_FILE:-/swapfile}"
SWAP_SIZE_GB="${SWAP_SIZE_GB:-2}"
SWAPPINESS="${SWAPPINESS:-10}"
MIN_FREE_AFTER_GB="${MIN_FREE_AFTER_GB:-5}"
SYSCTL_FILE=/etc/sysctl.d/99-lawnambul-swap.conf

log() { echo "[swap $(date '+%F %T')] $*"; }

for v in SWAP_SIZE_GB SWAPPINESS MIN_FREE_AFTER_GB; do
  [[ "${!v}" =~ ^[0-9]+$ ]] || { log "❌ $v는 0 이상의 정수여야 한다: ${!v}"; exit 2; }
done
[[ "$SWAP_SIZE_GB" -ge 1 ]] || { log "❌ SWAP_SIZE_GB는 1 이상이어야 한다"; exit 2; }
[[ "$SWAPPINESS" -le 100 ]] || { log "❌ SWAPPINESS는 0 ~ 100이어야 한다"; exit 2; }

status() {
  echo "--- 메모리 ---"; free -h
  echo "--- 켜진 swap ---"; swapon --show 2>/dev/null || true
  echo "--- vm.swappiness: $(cat /proc/sys/vm/swappiness)"
  echo "--- fstab: $(grep -E "^[^#]*[[:space:]]swap[[:space:]]" /etc/fstab 2>/dev/null || echo '(없음)')"
  echo "--- 디스크(/) ---"; df -h /
  # Docker가 컨테이너 swap 상한을 지원하는지 (지원하지 않으면 memswap_limit이 무시된다)
  # docker info가 실패하면(데몬 꺼짐 · docker 그룹이 아닌 사용자 등) "지원"으로 보이지 않게 "확인 불가"로 둔다 (PR #247 리뷰)
  if command -v docker >/dev/null 2>&1; then
    if ! docker_info=$(docker info 2>&1); then
      echo "--- Docker: swap 상한 지원 여부 확인 불가 (docker info 실패 — sudo로 다시 보거나 docker 그룹 권한 확인)"
    elif grep -qi "no swap limit support" <<<"$docker_info"; then
      echo "--- ⚠️ Docker: swap 상한 미지원(memswap_limit이 무시된다) — deploy/README.md \"Swap\" 참고"
    else
      echo "--- Docker: swap 상한 지원"
    fi
  else
    echo "--- Docker: 설치돼 있지 않아 확인 불가"
  fi
}

# 모르는 인자는 실행으로 떨어지지 않게 멈춘다 — root로 운영 서버에서 돌리므로 --dry-run · 오타(--chek)도 "진짜 실행"이 되면 안 된다 (PR #247 리뷰)
case "${1:-}" in
  --check) status; exit 0 ;;
  "")      ;;   # 인자 없음 = 설정 실행
  *)       log "❌ 모르는 인자: $1 (사용법: sudo $0 · 상태만 보기: $0 --check)"; exit 2 ;;
esac

[[ $EUID -eq 0 ]] || { log "❌ root로 실행한다: sudo $0 (상태만 보려면 $0 --check)"; exit 2; }

# 0) 이 파일의 기존 fstab 줄에 noauto가 있으면 재부팅 뒤 swap이 켜지지 않는다.
#    fstab이 깨지면 서버가 부팅하지 못하므로 스크립트가 고쳐 쓰지 않고, 아무것도 바꾸기 전에 멈춰 사람이 고치게 한다 (PR #247 리뷰)
if awk -v f="$SWAP_FILE" '$1 == f && $3 == "swap" && $4 ~ /(^|,)noauto(,|$)/ { found = 1 } END { exit found ? 0 : 1 }' /etc/fstab; then
  log "❌ /etc/fstab의 $SWAP_FILE 줄에 noauto가 있다 — 재부팅 뒤 swap이 켜지지 않는다."
  log "   사본을 만든 뒤 그 줄의 옵션을 sw로 직접 고치고 다시 실행한다: sudo cp -p /etc/fstab /etc/fstab.bak-수동 && sudo nano /etc/fstab"
  exit 3
fi

# 1) 이미 켜진 swap
if [[ -n "$(swapon --noheadings --show=NAME 2>/dev/null)" ]]; then
  log "이미 켜진 swap이 있어 새로 만들지 않는다: $(swapon --noheadings --show=NAME,SIZE | tr '\n' ' ')"
else
  if [[ -e "$SWAP_FILE" ]]; then
    # 꺼진 채 남은 파일(예: fstab만 지운 경우). 크기가 다르면 사람이 판단한다 — 함부로 지우지 않는다
    # 바이트로 비교한다 — GB로 나누면 2.5GB 파일도 2GB로 보여 통과한다 (PR #247 리뷰)
    size_bytes=$(stat -c %s "$SWAP_FILE")
    want_bytes=$(( SWAP_SIZE_GB * 1024 * 1024 * 1024 ))
    if [[ "$size_bytes" -ne "$want_bytes" ]]; then
      log "❌ $SWAP_FILE이 이미 있고 크기(${size_bytes}B)가 요청(${SWAP_SIZE_GB}GB = ${want_bytes}B)과 다르다. 확인 후 지우고 다시 실행한다"
      exit 3
    fi
    log "$SWAP_FILE(${SWAP_SIZE_GB}GB)이 있어 그대로 켠다"
  else
    # 2) 디스크 여유
    avail_gb=$(( $(df --output=avail -k "$(dirname "$SWAP_FILE")" | tail -1) / 1024 / 1024 ))
    need_gb=$(( SWAP_SIZE_GB + MIN_FREE_AFTER_GB ))
    if [[ "$avail_gb" -lt "$need_gb" ]]; then
      log "❌ 디스크 여유 ${avail_gb}GB < 필요 ${need_gb}GB (swap ${SWAP_SIZE_GB}GB + 남길 여유 ${MIN_FREE_AFTER_GB}GB). 옛 이미지 · 백업을 먼저 정리한다"
      exit 3
    fi
    # 3) 파일 생성. fallocate가 안 되는 파일 시스템이면 dd로 채운다(swapon은 구멍 난 파일을 거부한다)
    log "$SWAP_FILE ${SWAP_SIZE_GB}GB 생성 (디스크 여유 ${avail_gb}GB)"
    if ! fallocate -l "${SWAP_SIZE_GB}G" "$SWAP_FILE" 2>/dev/null; then
      log "fallocate 실패 → dd로 생성 (1 ~ 2분 걸린다)"
      dd if=/dev/zero of="$SWAP_FILE" bs=1M count=$(( SWAP_SIZE_GB * 1024 )) status=none
    fi
  fi
  chmod 600 "$SWAP_FILE"   # 다른 사용자가 swap에 내려간 메모리(비밀값 포함 가능)를 읽지 못하게
  mkswap "$SWAP_FILE" >/dev/null
  swapon "$SWAP_FILE"
  log "swap 켬: $(swapon --noheadings --show=NAME,SIZE | tr '\n' ' ')"
fi

# 4) 재부팅 뒤에도 켜지게 (이미 같은 파일 줄이 있으면 두지 않는다)
#    지금 실제로 켜져 있는 파일만 등록한다 — 다른 swap이 이미 켜져 있어 위에서 검증 · swapon을 건너뛴 경우,
#    꺼진 채 남은 SWAP_FILE을 fstab에 넣으면 다음 부팅에서 검증 안 된 파일을 켜려 한다 (PR #247 리뷰)
swap_file_active=false
# 출력을 변수로 받은 뒤 찾는다 — 파이프로 grep -q에 넘기면 pipefail에서 swapon이 SIGPIPE(141)를 받아
# 켜져 있는데도 false로 남고 fstab 등록이 빠질 수 있다 (PR #247 리뷰)
active_swaps=$(swapon --noheadings --show=NAME 2>/dev/null || true)
grep -qxF "$SWAP_FILE" <<<"$active_swaps" && swap_file_active=true
if [[ "$swap_file_active" = false ]] && [[ -e "$SWAP_FILE" ]]; then
  log "$SWAP_FILE은 켜져 있지 않아 /etc/fstab에 등록하지 않는다 (다른 swap 사용 중)"
fi
if [[ "$swap_file_active" = true ]] && ! grep -qE "^[[:space:]]*${SWAP_FILE}[[:space:]]" /etc/fstab; then
  cp -p /etc/fstab "/etc/fstab.bak-$(date +%Y%m%d%H%M%S)"   # fstab이 깨지면 부팅이 막힌다 — 되돌릴 사본
  echo "$SWAP_FILE none swap sw 0 0" >> /etc/fstab
  log "/etc/fstab에 추가 (사본: /etc/fstab.bak-*)"
fi

# 5) swappiness — 평소에는 swap을 쓰지 않고, 메모리가 정말 모자랄 때만
echo "vm.swappiness=$SWAPPINESS" > "$SYSCTL_FILE"
sysctl -q -w "vm.swappiness=$SWAPPINESS"
log "vm.swappiness=$SWAPPINESS ($SYSCTL_FILE)"

status
