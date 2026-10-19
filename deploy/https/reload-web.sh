#!/usr/bin/env bash
# 인증서 갱신 뒤 웹(Nginx) 다시 읽기 (BE-75)
#
# certbot은 인증서를 갱신하면 /etc/letsencrypt/renewal-hooks/deploy/의 실행 파일을 부른다.
# 이 파일을 거기에 복사해 두면(deploy/README.md "HTTPS" 설치 단계) 갱신 직후 Nginx가 새 인증서를 읽는다.
# 이게 없으면 파일은 갱신돼도 Nginx는 시작할 때 읽은 옛 인증서를 계속 써서, 90일 뒤 브라우저에 만료 경고가 뜬다.
#
# - 컨테이너는 Compose 라벨(프로젝트 lawnambul · 서비스 web)로 찾는다. 컨테이너 이름 · compose 파일 조합에 기대지 않는다
#   (deploy.sh는 .env에 따라 overlay 파일을 겹쳐 쓰므로, 여기서 compose 명령을 다시 조립하면 어긋날 수 있다)
# - reload는 연결을 끊지 않는다(새 설정 · 인증서로 새 worker를 띄우고 기존 연결은 마저 처리). 컨테이너를 다시 만들지 않는다
# - 컨테이너마다 따로 처리한다 (PR #181 리뷰): 배포 중 컨테이너 교체와 갱신이 겹쳐 대상이 둘 이상이고 하나가 실패해도
#   (예: 막 사라져 "No such container") 나머지는 계속 reload한다. 실패한 컨테이너는 끝에 모아서 알리고 1로 끝낸다
#   → certbot 로그(/var/log/letsencrypt/letsencrypt.log)에 훅 실패와 어느 컨테이너인지가 남는다
# - 직접 실행해 확인할 수도 있다: sudo /etc/letsencrypt/renewal-hooks/deploy/lawnambul-reload-web.sh

# -e는 쓰지 않는다: 한 컨테이너의 실패가 루프 전체를 끝내지 않게 실패를 직접 판단한다
set -uo pipefail

log() { echo "[reload-web] $*"; }

if ! ids="$(docker ps -q \
    --filter label=com.docker.compose.project=lawnambul \
    --filter label=com.docker.compose.service=web)"; then
  log "❌ docker ps 실패 (Docker 데몬 · 권한 확인)"
  exit 1
fi

if [[ -z "$ids" ]]; then
  # 웹이 떠 있지 않으면 다음에 뜰 때 새 인증서를 읽으므로 할 일이 없다
  log "웹 컨테이너가 없어 건너뛴다"
  exit 0
fi

ok=()
failed=()
for id in $ids; do
  # 설정 검사를 먼저 해서, 깨진 상태로 reload하지 않는다 (실패하면 그 컨테이너는 기존 설정 · 인증서로 계속 동작)
  if ! docker exec "$id" nginx -t; then
    log "⚠ 설정 검사(nginx -t) 실패 — reload하지 않음: $id"
    failed+=("$id")
    continue
  fi
  if ! docker exec "$id" nginx -s reload; then
    log "⚠ reload 실패: $id"
    failed+=("$id")
    continue
  fi
  log "Nginx 다시 읽음: $id"
  ok+=("$id")
done

if (( ${#failed[@]} > 0 )); then
  log "❌ ${#failed[@]}개 실패 (${failed[*]}), ${#ok[@]}개 성공 — 실패한 컨테이너는 옛 인증서를 쓰고 있을 수 있다. 'docker compose ... restart web'으로 다시 시작하면 새 인증서를 읽는다"
  exit 1
fi
log "✅ ${#ok[@]}개 모두 다시 읽음"
