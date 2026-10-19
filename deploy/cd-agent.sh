#!/usr/bin/env bash
# 자동 배포 확인 (BE-60, WBS 7.1.3): 서버가 스스로 GHCR의 main 이미지를 보고, 바뀌었으면 deploy.sh로 배포한다
#
# 실행: systemd 타이머(deploy/cd/lawnambul-cd.timer)가 이전 실행이 끝나고 2분 뒤마다 부른다. 사람이 바로 부를 수도 있다
#   ./cd-agent.sh
# 왜 서버가 확인하나: GitHub Actions가 서버에 들어오려면 SSH(22)를 열거나 AWS 권한(IAM · Session Manager)이 필요한데,
#   22번은 배포 담당 IP만 열려 있고 IAM을 만들 권한이 없다. 서버가 밖으로 나가 확인하면 새로 여는 포트가 없다
#   (결정 근거: docs/decisions/BE-60.md)
#
# 순서
# 1. GHCR에서 web:main 이미지를 받아, 이미지에 붙은 커밋 라벨(org.opencontainers.image.revision)을 읽는다
# 2. 지금 배포된 태그(.env의 IMAGE_TAG)와 같으면 아무것도 하지 않는다 (출력도 없음 — 로그가 2분마다 쌓이지 않게)
# 3. 다르면 서버의 저장소 사본을 그 커밋으로 맞추고(Compose · 스크립트가 이미지와 같은 버전이 되게) deploy.sh sha-xxxxxxx 실행
# 4. 배포가 실패해 되돌린 커밋은 기록해 두고, 새 커밋이 올라올 때까지 다시 시도하지 않는다 (2분마다 실패 · 되돌림 반복 방지)
#
# 로그: journalctl -u lawnambul-cd (deploy/README.md "자동 배포(CD)")

set -uo pipefail

DEPLOY_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
APP_DIR="$(dirname "$DEPLOY_DIR")"              # 서버의 저장소 사본 (/opt/lawnambul/app)
ENV_FILE="$DEPLOY_DIR/.env"
STATE_DIR="$DEPLOY_DIR/.deploy"
CHANNEL="${DEPLOY_CHANNEL:-main}"              # 배포할 브랜치의 이미지 태그. 운영은 main만 (develop은 배포하지 않는다)

log() { echo "[cd-agent $(date '+%F %T')] $*"; }
env_value() { grep -E "^$1=" "$ENV_FILE" | tail -1 | cut -d= -f2-; }

[[ -f "$ENV_FILE" ]] || { log "❌ $ENV_FILE 이 없다"; exit 4; }
mkdir -p "$STATE_DIR"

# 확인 자체도 겹치지 않게 잠근다 (deploy.sh의 잠금과는 다른 파일)
if command -v flock >/dev/null 2>&1; then
  exec 8>"$STATE_DIR/agent.lock"
  flock -n 8 || exit 0
else
  mkdir "$STATE_DIR/agent.lock.d" 2>/dev/null || exit 0
  trap 'rmdir "$STATE_DIR/agent.lock.d" 2>/dev/null' EXIT
fi

PREFIX="$(env_value IMAGE_PREFIX)"
[[ -n "$PREFIX" ]] || { log "❌ .env에 IMAGE_PREFIX가 없다"; exit 4; }
WATCH_IMAGE="$PREFIX-web:$CHANNEL"

# ---------- 1. main 이미지 확인 ----------
if ! PULL_OUT="$(docker pull -q "$WATCH_IMAGE" 2>&1)"; then
  if grep -qiE 'manifest unknown|not found' <<<"$PULL_OUT"; then
    # develop → main PR이 아직 한 번도 머지되지 않았으면 main 이미지가 없다. 오류가 아니다
    exit 0
  fi
  log "❌ $WATCH_IMAGE 를 받지 못했다. GHCR 로그인 토큰 만료(11/14경)인지 확인: docker login ghcr.io"
  log "   $(head -c 300 <<<"$PULL_OUT")"
  exit 2
fi

REV="$(docker image inspect --format '{{ index .Config.Labels "org.opencontainers.image.revision" }}' "$WATCH_IMAGE" 2>/dev/null)"
if [[ ! "$REV" =~ ^[0-9a-f]{40}$ ]]; then
  log "❌ $WATCH_IMAGE 에 커밋 라벨(org.opencontainers.image.revision)이 없다: '${REV}'"
  exit 4
fi
TARGET="sha-${REV:0:7}"

# ---------- 2. 바뀌었나 ----------
[[ "$TARGET" == "$(env_value IMAGE_TAG)" ]] && exit 0
[[ "$REV" == "$(cat "$STATE_DIR/failed_rev" 2>/dev/null)" ]] && exit 0

# 서버는 이 커밋의 스크립트(deploy.sh)를 실행하게 되므로, 그 커밋이 지금 저장소의 main 최신 커밋인지 확인한다(리뷰 반영).
# 이미지 라벨만 믿으면, 라벨이 main에 없는 커밋(머지 안 된 PR 등)을 가리킬 때 그 코드가 서버에서 돈다.
# main이 더 앞서 있으면(새 커밋의 이미지를 빌드하는 중) 이번에는 건너뛴다 — 그 이미지가 올라오면 다음 확인 때 배포된다
if [[ "${DEPLOY_SKIP_GIT:-}" != "1" ]]; then
  REMOTE_HEAD="$(git -C "$APP_DIR" ls-remote origin "refs/heads/$CHANNEL" 2>/dev/null | cut -f1)"
  if [[ "$REMOTE_HEAD" != "$REV" ]]; then
    log "이미지 커밋($TARGET)이 지금 $CHANNEL 최신 커밋(${REMOTE_HEAD:0:7})과 달라 이번에는 건너뛴다"
    exit 0
  fi
fi

log "새 $CHANNEL 이미지 발견: $TARGET ($REV)"

# ---------- 3. 저장소 사본을 그 커밋으로 맞춘다 ----------
# 공개 저장소라 인증 없이 받는다. 서버 사본은 --depth 1이라 커밋 하나만 받아 그 커밋으로 옮긴다(.env · .deploy는 git이 건드리지 않는다)
# 테스트 전용: DEPLOY_SKIP_GIT=1이면 건너뛴다(저장소 사본이 없는 로컬 확인용). 서버에서는 쓰지 않는다
if [[ "${DEPLOY_SKIP_GIT:-}" == "1" ]]; then
  :
elif ! git -C "$APP_DIR" fetch -q --depth 1 origin "$REV" || ! git -C "$APP_DIR" checkout -q --detach "$REV"; then
  log "❌ 저장소 사본을 $REV 로 옮기지 못했다 (서버에서 파일을 직접 고쳤다면 git -C $APP_DIR status로 확인)"
  exit 4
fi

# ---------- 4. 배포 ----------
# 방금 받은 커밋의 deploy.sh를 부른다 (스크립트도 이미지와 같은 버전)
"$DEPLOY_DIR/deploy.sh" "$TARGET"
RC=$?
case "$RC" in
  0) rm -f "$STATE_DIR/failed_rev" ;;
  1|3) echo "$REV" > "$STATE_DIR/failed_rev"
       log "이 커밋($TARGET)은 다시 시도하지 않는다. 고친 커밋이 main에 올라오면 그때 배포한다" ;;
  *) ;;  # 2(이미지 받기 실패) · 4(잠금 · 설정): 다음 확인 때 다시 시도
esac
exit "$RC"
