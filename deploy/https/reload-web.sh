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
# - 직접 실행해 확인할 수도 있다: sudo /etc/letsencrypt/renewal-hooks/deploy/lawnambul-reload-web.sh

set -euo pipefail

ids="$(docker ps -q \
  --filter label=com.docker.compose.project=lawnambul \
  --filter label=com.docker.compose.service=web)"

if [[ -z "$ids" ]]; then
  # 웹이 떠 있지 않으면 다음에 뜰 때 새 인증서를 읽으므로 할 일이 없다
  echo "[reload-web] 웹 컨테이너가 없어 건너뛴다"
  exit 0
fi

for id in $ids; do
  # 설정 검사를 먼저 해서, 깨진 상태로 reload하지 않는다 (실패하면 기존 설정으로 계속 동작)
  docker exec "$id" nginx -t
  docker exec "$id" nginx -s reload
  echo "[reload-web] Nginx 다시 읽음: $id"
done
