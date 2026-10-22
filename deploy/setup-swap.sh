#!/usr/bin/env bash
# EC2 swap 설정 (COMMON-54, deploy/README.md "Swap")
#
#   sudo ./setup-swap.sh           # 없으면 만들고 켠다. 이미 맞게 돼 있으면 아무것도 바꾸지 않는다(다시 실행해도 안전)
#   ./setup-swap.sh --check        # 바꾸지 않고 지금 상태만 본다 (root 불필요)
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
  if command -v docker >/dev/null 2>&1; then
    if docker info 2>&1 | grep -qi "no swap limit support"; then
      echo "--- ⚠️ Docker: swap 상한 미지원(memswap_limit이 무시된다) — deploy/README.md \"Swap\" 참고"
    else
      echo "--- Docker: swap 상한 지원"
    fi
  fi
}

if [[ "${1:-}" == "--check" ]]; then
  status
  exit 0
fi

[[ $EUID -eq 0 ]] || { log "❌ root로 실행한다: sudo $0 (상태만 보려면 $0 --check)"; exit 2; }

# 1) 이미 켜진 swap
if [[ -n "$(swapon --noheadings --show=NAME 2>/dev/null)" ]]; then
  log "이미 켜진 swap이 있어 새로 만들지 않는다: $(swapon --noheadings --show=NAME,SIZE | tr '\n' ' ')"
else
  if [[ -e "$SWAP_FILE" ]]; then
    # 꺼진 채 남은 파일(예: fstab만 지운 경우). 크기가 다르면 사람이 판단한다 — 함부로 지우지 않는다
    size_gb=$(( $(stat -c %s "$SWAP_FILE") / 1024 / 1024 / 1024 ))
    if [[ "$size_gb" -ne "$SWAP_SIZE_GB" ]]; then
      log "❌ $SWAP_FILE이 이미 있고 크기(${size_gb}GB)가 요청(${SWAP_SIZE_GB}GB)과 다르다. 확인 후 지우고 다시 실행한다"
      exit 3
    fi
    log "$SWAP_FILE(${size_gb}GB)이 있어 그대로 켠다"
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
if [[ -f "$SWAP_FILE" ]] && ! grep -qE "^[[:space:]]*${SWAP_FILE}[[:space:]]" /etc/fstab; then
  cp -p /etc/fstab "/etc/fstab.bak-$(date +%Y%m%d%H%M%S)"   # fstab이 깨지면 부팅이 막힌다 — 되돌릴 사본
  echo "$SWAP_FILE none swap sw 0 0" >> /etc/fstab
  log "/etc/fstab에 추가 (사본: /etc/fstab.bak-*)"
fi

# 5) swappiness — 평소에는 swap을 쓰지 않고, 메모리가 정말 모자랄 때만
echo "vm.swappiness=$SWAPPINESS" > "$SYSCTL_FILE"
sysctl -q -w "vm.swappiness=$SWAPPINESS"
log "vm.swappiness=$SWAPPINESS ($SYSCTL_FILE)"

status
