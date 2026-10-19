# 배포 (BE-58, WBS 7.1.1 · Redis BE-61, WBS 7.1.4 · 자동 배포 BE-60, WBS 7.1.3)

운영은 서버 한 대에 컨테이너 네 개를 띄운다. 밖으로 여는 포트는 웹 80 · 443뿐이다 (443은 인증서가 있을 때만 열린다, 아래 "HTTPS").

```text
브라우저 ──▶ web (Nginx :80) ──┬─ /            → 정적 파일 (Vite 빌드, 실제 모드)
                               ├─ /cases/...   → index.html (SPA fallback, 라우터가 화면 선택)
                               └─ /api/...     → backend (Spring Boot :8080) ──┬─▶ postgres (:5432)
                                                                               └─▶ redis (:6379, DB 0 캐시 · DB 1 토큰)
```

| 파일 | 내용 |
| --- | --- |
| `docker-compose.prod.yml` | 운영 구성 (web · backend · postgres · redis). 기본은 스키마만 |
| `docker-compose.real-seed.yml` | 서버 전용 덧붙임 파일. 실제 사건 seed 폴더를 백엔드에 읽기 전용으로 연결 |
| `docker-compose.monitoring-agent.yml` | 서버 전용 덧붙임 파일. 모니터링 에이전트(Alloy · node_exporter)와 backend의 로그 볼륨 · 8081 포트 (COMMON-43). `.env`의 `MONITORING_AGENT=1`이면 `deploy.sh`가 자동으로 함께 쓴다 |
| `alloy-agent.alloy` | 위 Alloy 설정. 백엔드 로그를 모니터링 호스트의 Loki로 보낸다 (COMMON-43) |
| `.env.example` | 환경변수 예시. `.env`로 복사해 쓴다 (`.env`는 커밋하지 않음) |
| `smoke-test.sh` | `/` · 화면 주소 · `/api` · 캐시 정책 · Let's Encrypt 확인 경로 확인. CI와 서버에서 같은 스크립트를 쓴다 |
| `deploy.sh` | 서버 배포 한 번: sha 이미지로 교체 → 준비 대기 → 스모크 테스트 → 실패하면 직전 이미지로 되돌림 (BE-60) |
| `cd-agent.sh` | 자동 배포 확인: GHCR의 `main` 이미지가 바뀌었으면 `deploy.sh` 실행 (BE-60) |
| `cd/lawnambul-cd.service` · `.timer` | `cd-agent.sh`를 2분마다 부르는 systemd 설정 (서버에 한 번 설치) |
| `../frontend/Dockerfile` | 웹 이미지 (Vite 빌드 → Nginx). 커밋 해시를 `/version.json`에 넣는다 |
| `../frontend/nginx/default.conf` | Nginx 설정: Rate limit zone · 기본 http server (localhost · IP · 발급 전 도메인) |
| `../frontend/nginx/snippets/app.conf` | 앱 location (`/api` 프록시 · Rate limit · SPA fallback · 캐시 · `/version.json`). http · https server가 함께 include |
| `../frontend/nginx/snippets/acme-challenge.conf` | Let's Encrypt 도메인 확인 경로 (http 전용) |
| `../frontend/nginx/https.conf.template` · `40-enable-https.sh` | 인증서가 있을 때만 컨테이너 시작 시 https server를 만든다: 도메인 · www를 `https://<도메인>` 하나로 이동, 모르는 이름의 TLS는 거절 (BE-75) |
| `https/reload-web.sh` | 인증서 갱신 뒤 Nginx 다시 읽기 (certbot 갱신 훅, 서버에 한 번 설치) |
| `../.github/workflows/deploy-images.yml` | 이미지 빌드 → 스모크 테스트 → GHCR 업로드 → (main) 운영 배포 확인 |

## 이미지가 만들어지는 흐름

1. `develop` · `main`으로 가는 PR: Actions가 이미지를 빌드해 이 구성으로 띄우고 `smoke-test.sh`를 실행한다. **업로드하지 않는다.**
2. `develop` push(머지): 스모크 테스트를 통과한 이미지를 GHCR에 올린다. **운영에는 배포하지 않는다.**
3. `main` push(`develop → main` PR 머지): 같은 방식으로 올리고, **운영 서버가 2분 안에 스스로 받아 배포한다** (아래 "자동 배포(CD)").
   - `ghcr.io/prgrms-aibe-devcourse/lawnambul-web` · `ghcr.io/prgrms-aibe-devcourse/lawnambul-backend`
   - 태그: `sha-커밋7자리`(배포 · 되돌릴 때 쓰는 고정 태그) + 브랜치 이름 `develop` · `main`(그 브랜치의 최신)
   - 이미지 라벨 `org.opencontainers.image.revision`에 커밋 전체 해시가 붙는다 (서버가 배포할 sha 태그를 이걸로 정한다)
4. 서버는 이미지를 받아서 실행만 한다. 서버에서 Gradle · npm 빌드를 하지 않는다 (메모리 절약).

## 로컬에서 운영 구성 확인

```bash
cd deploy
cp .env.example .env
# .env에서 POSTGRES_PASSWORD · REDIS_PASSWORD를 아무 값으로 채운다. 80번 포트가 쓰이고 있으면 WEB_PORT=8081
docker compose -f docker-compose.prod.yml build
docker compose -f docker-compose.prod.yml up -d
# 백엔드가 뜰 때까지 10 ~ 20초 정도 걸린다. 바로 실행해 /api가 실패하면 잠시 뒤 다시 실행한다
./smoke-test.sh http://localhost          # 기본 WEB_PORT=80
# ./smoke-test.sh http://localhost:8081   # .env에서 WEB_PORT=8081로 바꾼 경우
docker compose -f docker-compose.prod.yml down      # 데이터까지 지우려면 down -v
```

## 서버(EC2)에서 실행

준비: Docker Engine · Compose 플러그인 설치, 보안 그룹은 22(내 IP) · 80 · 443만 연다.

```bash
# 저장소에서 deploy 폴더만 있으면 된다 (compose 파일 · 스모크 테스트)
cd deploy
cp .env.example .env
# .env 수정
#   IMAGE_PREFIX=ghcr.io/prgrms-aibe-devcourse/lawnambul
#   IMAGE_TAG=develop
#   POSTGRES_PASSWORD=$(openssl rand -base64 24) 로 만든 값
#   REDIS_PASSWORD=$(openssl rand -hex 24) 로 만든 값 (16진수: 값에 `$`가 있으면 Compose가 변수로 읽어 비밀번호가 잘린다)
chmod 600 .env

# 패키지가 비공개면 먼저 로그인한다 (read:packages 권한 토큰)
# echo <토큰> | docker login ghcr.io -u <GitHub 아이디> --password-stdin

docker compose -f docker-compose.prod.yml pull
docker compose -f docker-compose.prod.yml up -d --no-build

# 백엔드 기동(Flyway 마이그레이션 포함)을 기다린 뒤 스모크 테스트한다 (CI와 같은 방식, 최대 2분)
for i in $(seq 1 60); do
  [ "$(curl -s --max-time 5 -o /dev/null -w '%{http_code}' http://localhost/api/v1/cases)" = "200" ] && echo "ready after $((i * 2))s" && break
  sleep 2
done
./smoke-test.sh http://localhost
```

되돌리기: `./deploy.sh --rollback` (직전 배포 이미지로, 자동 배포가 켜져 있으면 타이머를 먼저 멈춘다 — 아래 "운영 중 쓰는 명령"). 자동 배포를 켠 뒤에는 `.env`의 `IMAGE_TAG`를 손으로 고치지 않는다 — `deploy.sh`가 관리한다.

## 자동 배포(CD) (BE-60, WBS 7.1.3)

운영 서버는 **`main`에 머지된 것만** 배포한다. GitHub Actions가 서버에 들어가지 않고, **서버가 2분마다 GHCR을 확인**해 스스로 배포한다.
22번(SSH)은 배포 담당 IP만 열려 있고 IAM(Session Manager)을 만들 권한이 없어서, 새로 여는 포트 · AWS 권한 없이 하는 방식을 골랐다 ([결정 기록](../docs/decisions/BE-60.md)).

```text
develop → main PR 머지
  └▶ Actions: 빌드 · 스모크 테스트 → GHCR (sha-xxxxxxx, main)
       └▶ 서버 cd-agent.sh (2분마다): web:main의 커밋 라벨이 지금 배포(.env IMAGE_TAG)와 다르면
            1) 서버 저장소 사본을 그 커밋으로 옮김 (Compose · 스크립트도 같은 버전)
            2) deploy.sh sha-xxxxxxx: 이미지 받기 → 교체 → 백엔드 준비 대기 → 스모크 테스트
            3) 실패하면 직전 이미지로 되돌리고, 그 커밋은 다시 시도하지 않는다 (고친 커밋이 올라오면 그때 배포)
  └▶ Actions verify-deploy: 운영 /version.json이 이 커밋이 될 때까지 기다림(최대 15분) → 운영 주소로 스모크 테스트
```

### 처음 한 번: 서버에 설치

BE-60이 `develop`에 머지된 뒤 서버에서 한다. 설치해도 `main` 이미지가 생기기 전까지는 아무것도 바꾸지 않는다.

```bash
cd /opt/lawnambul/app && git pull -q          # BE-60 파일(deploy.sh · cd-agent.sh · cd/) 받기
sudo cp deploy/cd/lawnambul-cd.service deploy/cd/lawnambul-cd.timer /etc/systemd/system/
sudo systemctl daemon-reload
sudo systemctl enable --now lawnambul-cd.timer
systemctl list-timers lawnambul-cd.timer      # 다음 확인 시각이 보이면 정상

# 바로 한 번 확인 (main 이미지가 아직 없으면 출력 없이 끝난다)
sudo systemctl start lawnambul-cd.service; journalctl -u lawnambul-cd -n 20 --no-pager
```

- Actions 쪽 확인을 켜려면 저장소 **Settings → Secrets and variables → Actions → Variables**에 `DEPLOY_URL`을 넣는다(저장소 관리자). HTTPS 적용 후에는 `https://naelawnambul.today` (`http://<도메인>`은 https로 이동해 확인이 실패한다, 아래 "HTTPS"). 없으면 확인 job이 건너뛰기만 하고, 배포는 서버가 그대로 한다.

### 운영 중 쓰는 명령

```bash
journalctl -u lawnambul-cd -n 50 --no-pager   # 자동 배포 기록 (배포한 때만 남는다. 바뀐 게 없으면 출력 없음)
curl -s http://localhost/version.json         # 지금 떠 있는 커밋
cd /opt/lawnambul/app/deploy
sudo systemctl stop lawnambul-cd.timer        # 자동 배포 잠시 멈춤 (점검 · 장애 대응 중). 다시 켜기: start
./deploy.sh --rollback                        # 직전 이미지로 되돌리기 — 먼저 위처럼 타이머를 멈춘다
./deploy.sh sha-1a2b3c4                       # 특정 커밋 이미지로 (develop 이미지를 시험 삼아 올릴 때도) — 역시 타이머를 먼저 멈춘다
rm .deploy/failed_rev                         # 실패해서 건너뛰는 커밋을 다시 시도하게 할 때
```

- **손으로 되돌리거나 다른 sha를 올릴 때는 타이머를 먼저 멈춘다.** 켜 둔 채면 2분 뒤 `cd-agent.sh`가 "지금 배포가 `main`과 다르다"고 보고 다시 `main`으로 배포한다. 원인을 고친 커밋이 `main`에 올라가면 타이머를 다시 켠다(`start`).
- 예외: `main` 이미지가 아직 없을 때(첫 `develop → main` PR 전)는 `cd-agent.sh`가 아무것도 하지 않으므로 타이머를 켜 둔 채 시험해도 된다.

### 실패했을 때

| 기록 (`journalctl`) | 뜻 · 할 일 |
| --- | --- |
| `↩ 되돌림 성공` | 새 이미지가 준비 · 스모크 테스트를 통과하지 못해 직전 이미지로 돌아왔다. 서비스는 정상. 위에 찍힌 backend · web 로그로 원인을 보고, 고친 커밋을 `main`에 올린다 |
| `⚠ … DB 마이그레이션을 적용했다` | 앱은 되돌렸지만 **스키마는 새 버전 그대로**다. 이전 앱은 모르는 새 마이그레이션을 무시하고 뜬다(Flyway 기본값). 스키마를 되돌릴지는 사람이 판단한다 (데이터 손상 위험). 아래 "마이그레이션 규칙"을 지켰다면 이전 앱이 그대로 뜬다 |
| `❌ 되돌림도 실패` | 서비스가 내려가 있을 수 있다. 바로 `docker compose -f docker-compose.prod.yml ps` · `logs`로 확인 |
| `❌ … 받지 못했다 … 토큰 만료` | GHCR 로그인 토큰이 만료됐다(지금 토큰은 **11/14경**, 최종 제출 11/04 이후). `docker login ghcr.io`를 새 토큰으로 다시 한다. 다음 확인 때 자동으로 다시 시도한다 |
| Actions `verify-deploy` 실패 (15분) | 서버가 배포하지 못했다는 뜻. 위 기록을 본다 |

**마이그레이션 규칙 (main으로 가는 Flyway, #161 리뷰)**: 되돌림은 앱만 직전 이미지로 바꾸고 스키마는 그대로 둔다. 백엔드는 `ddl-auto: validate`라 이전 앱이 새 스키마에서 뜨려면 **새 마이그레이션은 추가만** 해야 한다(테이블 · 컬럼 추가, 기본값 있는 컬럼).
- 컬럼 · 테이블 **지우기 · 이름 바꾸기**, 기본값 없는 `NOT NULL` 추가는 **한 배포에 같이 넣지 않는다**: 먼저 새 것을 추가하고 코드를 옮긴 배포가 안정된 뒤, 다음 배포에서 지운다.
- 이 규칙을 어기면 새 앱이 실패했을 때 이전 앱도 검증에서 떨어져 **되돌림까지 실패**(`❌ 되돌림도 실패`)한다.

주의
- **배포 중 API가 20 ~ 40초 끊긴다** (서버 한 대라 무중단이 아니다). 화면은 기존 웹이 계속 내주지만, 그동안 API를 부르면 502라 프론트는 "서버에 연결할 수 없어요"를 보여 준다(`api/client.js`의 5xx 안내). 시연 · 발표 중에는 `main`에 머지하지 않는다.
- **되돌릴 때 Compose · 스크립트는 새 커밋 것을 그대로 쓴다** (이미지 태그만 되돌린다). Compose를 크게 바꾸는 PR(예: RDS 전환)은 머지 직후 한 번 지켜본다.
- 서버는 이미지 라벨의 커밋이 **지금 `main`의 최신 커밋일 때만** 배포한다(그 커밋의 스크립트를 서버가 실행하므로). `main`이 더 앞서 있으면 새 이미지가 올라올 때까지 건너뛴다 — 연달아 머지하면 마지막 커밋만 배포될 수 있다.
- 배포에 성공하면 지금 · 직전 sha 이미지만 남기고 옛 이미지를 지운다(디스크 30GB). 더 옛날로 되돌리려면 `./deploy.sh sha-xxxxxxx`가 GHCR에서 다시 받는다.
- 배포 실패는 서버 로그(journald)와 Actions `verify-deploy` 실패(커밋한 사람에게 GitHub 알림)로만 보인다. Grafana · Loki에는 아직 안 잡힌다(Alloy가 백엔드 로그만 수집, 필요하면 C 후속).
- `develop` 머지는 운영에 나가지 않는다. 운영에 올리려면 `develop → main` PR을 올린다.
- 켠 뒤에는 서버에서 `git pull` · 파일 직접 수정을 하지 않는다. 저장소 사본은 `cd-agent.sh`가 배포하는 커밋으로 옮긴다(브랜치가 아니라 커밋에 고정, detached). 고칠 게 있으면 PR로 `main`에 올린다.
- 반영까지 최대 2 ~ 3분(확인 주기) + 기동 시간이 걸린다.

## HTTPS (BE-75)

도메인 `naelawnambul.today`(www 포함)에 Let's Encrypt 인증서를 붙여 https로 연다. 왜 이렇게 나눴는지는 [결정 기록](../docs/decisions/BE-75.md).

```text
http://localhost · http://<탄력적 IP>          → 기본 http server: 그대로 응답 (deploy.sh · CI 스모크 테스트용)
http://naelawnambul.today · http://www.       → 301 → https://naelawnambul.today (인증서가 있을 때만)
https://www.naelawnambul.today                → 301 → https://naelawnambul.today (주소를 하나로: 쿠키가 호스트 단위)
https://naelawnambul.today                    → 앱 (http와 같은 snippets/app.conf: /api 프록시 · Rate limit · SPA)
https://<탄력적 IP> · 다른 이름                 → TLS 연결 거절 (인증서를 보내지 않음)
http://<도메인>/.well-known/acme-challenge/  → 인증서 확인 파일 (이동하지 않음, 발급 · 갱신에 필요)
```

- **인증서는 서버 호스트의 certbot이 발급 · 갱신한다.** 웹 컨테이너는 `/etc/letsencrypt`의 `live/` · `archive/`만 읽기 전용으로 붙여 읽기만 한다(certbot 계정 키가 있는 `accounts/`는 넣지 않는다).
- **https는 컨테이너가 시작할 때 켜진다.** `40-enable-https.sh`가 `.env`의 `SERVER_DOMAIN`과 인증서 파일을 확인해, 둘 다 있으면 https server를 만들고 아니면 http만 띄운다. 그래서 CI · 로컬 · 발급 전 서버는 지금과 똑같이 동작한다.
- **인증서가 없어도 배포가 실패하지 않는다.** https만 안 켜지고, 로그에 `⚠ … 인증서가 없어 http만 연다`가 남는다.

### 처음 한 번: 서버에서 발급

BE-75가 `main`에 배포된 뒤 서버에서 한다. 준비: DNS A 레코드(`@` · `www` → 탄력적 IP), 보안 그룹 80 · 443 열림.

```bash
cd /opt/lawnambul/app/deploy

# 1) .env에 네 값 추가 (IMAGE_TAG처럼 deploy.sh가 관리하는 값이 아니므로 손으로 넣어도 된다)
#    SERVER_DOMAIN=naelawnambul.today
#    LETSENCRYPT_LIVE_DIR=/etc/letsencrypt/live
#    LETSENCRYPT_ARCHIVE_DIR=/etc/letsencrypt/archive
#    CERTBOT_WEBROOT=/var/www/certbot
# 폴더를 먼저 만들어 둔다 (certbot 설치 전이라 아직 없다. 없는 경로를 붙이면 Docker가 대신 만들지만, 오타를 바로 알아채도록 직접 만든다)
sudo mkdir -p /var/www/certbot /etc/letsencrypt/live /etc/letsencrypt/archive

# 2) 웹만 다시 만들어 위 폴더를 붙인다. --no-deps: backend를 건드리지 않는다
#    (overlay 파일은 backend만 바꾸므로 웹은 base 파일만으로 다시 만들어도 같다)
docker compose -f docker-compose.prod.yml up -d --no-build --no-deps web

# 3) 확인 경로가 열렸는지: 404가 나와야 한다 (200이면 SPA로 새는 중 → 이미지가 BE-75 이전인지 /version.json 확인)
curl -s -o /dev/null -w '%{http_code}\n' http://naelawnambul.today/.well-known/acme-challenge/check

# 4) certbot 설치 → 시험 발급(횟수 제한 없음) → 실제 발급
sudo apt-get update && sudo apt-get install -y certbot
sudo certbot certonly --webroot -w /var/www/certbot \
  -d naelawnambul.today -d www.naelawnambul.today \
  --email <팀 메일> --agree-tos --no-eff-email --dry-run
sudo certbot certonly --webroot -w /var/www/certbot \
  -d naelawnambul.today -d www.naelawnambul.today \
  --email <팀 메일> --agree-tos --no-eff-email

# 5) 웹을 다시 시작해 https를 켠다 → 로그에 "✅ https 활성화"가 보여야 한다
docker compose -f docker-compose.prod.yml restart web
docker compose -f docker-compose.prod.yml logs --tail 20 web | grep 40-enable-https

# 6) 갱신 훅 설치: 갱신 직후 Nginx가 새 인증서를 읽게 한다 (없으면 90일 뒤 만료 경고)
sudo install -m 755 https/reload-web.sh /etc/letsencrypt/renewal-hooks/deploy/lawnambul-reload-web.sh
systemctl list-timers certbot.timer          # apt의 certbot이 하루 두 번 갱신을 확인한다 (만료 30일 전부터 실제 갱신)
sudo certbot renew --dry-run                 # 갱신 시험
```

- 저장소 변수 `DEPLOY_URL`을 `https://naelawnambul.today`로 바꾼다(저장소 관리자, 위 "자동 배포(CD)").

### 확인

```bash
curl -sI http://naelawnambul.today | head -3             # 301 + Location: https://naelawnambul.today/
curl -sI https://naelawnambul.today | head -1            # 200
curl -sI https://www.naelawnambul.today | grep -i '^location'   # 301 → https://naelawnambul.today/
curl -sk --max-time 5 https://<탄력적 IP>/ ; echo "exit=$?"     # 연결 거절(exit 35)이 정상: 모르는 이름은 TLS를 끊는다
curl -s http://localhost/version.json                    # 서버 안: 이동 없이 그대로 (deploy.sh 스모크 테스트 경로)
./smoke-test.sh https://naelawnambul.today               # https로 전체 스모크 테스트
echo | openssl s_client -connect naelawnambul.today:443 -servername naelawnambul.today 2>/dev/null \
  | openssl x509 -noout -issuer -subject -ext subjectAltName -dates   # 발급자 Let's Encrypt · www 포함 · 만료일
sudo certbot certificates                                # 서버의 인증서 목록 · 남은 기간
```

### 문제가 생겼을 때

| 증상 | 원인 · 할 일 |
| --- | --- |
| 로그 `⚠ … 인증서가 없어 http만 연다` | 발급 전이면 정상. 발급했다면 `.env`의 `LETSENCRYPT_LIVE_DIR` · `LETSENCRYPT_ARCHIVE_DIR`가 빠져 빈 볼륨이 붙은 것 → 값을 넣고 위 2) 명령 (둘 중 archive만 빠져도 링크가 끊겨 "없음"으로 보인다) |
| 로그 `SERVER_DOMAIN이 없어 http만 연다` | `.env`에 `SERVER_DOMAIN`이 없다 |
| 로그 `⚠ https 설정 검사(nginx -t) 실패` | 인증서는 있지만 Nginx가 읽지 못했다(인증서 · 개인키 쌍 불일치, 깨진 파일 등). **http는 정상으로 뜬다.** 바로 아래 줄의 nginx 오류 메시지를 보고 `sudo certbot certificates`로 인증서 상태를 확인한 뒤, 필요하면 다시 발급하고 `restart web` |
| certbot `Timeout during connect` | 보안 그룹 80이 닫혀 있거나 DNS가 다른 IP를 가리킨다 (`dig +short naelawnambul.today`) |
| certbot `unauthorized` · `404` | `CERTBOT_WEBROOT`가 빠져 확인 파일이 컨테이너에 안 보인다 → 위 2) · 3) 다시 |
| certbot `too many failed authorizations` | 실패를 반복해 1시간 제한. 이후에는 `--dry-run`으로 먼저 확인한다 |
| https 접속 거절 · http만 됨 | 위 5) 로그 확인. 인증서를 발급한 뒤 웹을 다시 시작하지 않았거나 2) 전에 발급했다 |
| 90일 뒤 브라우저 만료 경고 | 갱신 훅(6) 누락. `sudo certbot renew` 후 `sudo /etc/letsencrypt/renewal-hooks/deploy/lawnambul-reload-web.sh` |

### HTTPS로 풀리는 것 · 남은 것

- 익명 ID 쿠키 `NLNB_AID`는 항상 `Secure`다(`AnonymousIdCookie.java`, API 명세 1-2). 브라우저는 **http에서 `Secure` 쿠키를 저장하지 않는다** (`localhost`만 예외). 그래서 **https로 열어야 체험 시작 이후 흐름(익명 ID가 필요한 API)이 동작한다.** 관리자 세션 쿠키(`SESSION_COOKIE_SECURE`)도 같다.
- IP · http(`http://<탄력적 IP>`)로는 여전히 위 흐름이 동작하지 않는다. 사용자에게는 도메인 주소만 안내한다.
- **HSTS는 아직 켜지 않았다.** 갱신이 한 번 이상 문제없이 도는 것을 본 뒤 짧은 `max-age`부터 켠다 (결정 기록 "추후 재검토").
- 카카오 로그인(BE-62) 운영 앱의 Redirect URI는 `https://naelawnambul.today/...`로 등록한다.

## Rate limit (BE-63, WBS 7.4.6 1차)

한 IP가 `/api`를 짧은 시간에 너무 많이 부르면 **Nginx가 백엔드 앞에서 막는다**(t3.small 한 대 보호). zone은 `../frontend/nginx/default.conf`, 적용(`limit_req`)은 `../frontend/nginx/snippets/app.conf`에 있고 웹 이미지에 함께 들어간다.

| 설정 | 값 | 이유 |
| --- | --- | --- |
| 범위 | `/api`만 | 화면 · 정적 파일은 Nginx가 바로 주는 거라 부담이 거의 없다 |
| 기준 | IP (`$binary_remote_addr`) | 로그인 전 서비스라 사용자를 구분할 다른 값이 없다 |
| 속도 | 평균 초당 20번, 순간 40번까지 기다림 없이 처리 | 혼자 쓰면 초당 1 ~ 3번(동시 호출 · 폴링 없음). 발표장처럼 여러 명이 같은 와이파이(같은 IP)를 써도 막히지 않게 넉넉히 |
| 넘으면 | 429 + JSON `{"code":"TOO_MANY_REQUESTS", ...}` + `Retry-After: 1` | API 명세 1-4 형식. Nginx 기본 429(HTML)는 프론트가 "서버 오류"로 잘못 보여 준다 |

```bash
# 막힌 기록 보기 (zone api_per_ip, client가 막힌 IP)
docker compose -f docker-compose.prod.yml logs web 2>&1 | grep "limiting requests" | tail
```

- **값 조정**: 정상 사용자가 막히는 기록이 보이면 `default.conf`의 `rate` · `burst`를 고쳐 PR → 새 웹 이미지로 반영한다. 서버에서 컨테이너 안 파일을 직접 고치지 않는다(다음 배포 때 사라진다).
- 백엔드 지표(Prometheus)에는 429가 잡히지 않는다. 백엔드까지 가지 않고 Nginx에서 끝나기 때문이다. 확인은 위 로그로 한다. 로그 수집(`monitoring/alloy-config.alloy`)도 지금은 **백엔드 로그만** 모아서 Loki · Grafana에서는 보이지 않는다 — 막히는 빈도를 대시보드 · 경보로 보려면 웹 컨테이너 로그도 수집하도록 추가한다(C 후속 검토).

### 서버에서 꼭 확인: Nginx가 사용자 IP를 보는가

Rate limit은 Nginx가 **실제 사용자 IP**를 본다는 전제로 동작한다. 서버 구성에 따라 모든 요청이 Docker 내부 IP(`172.x`) 하나로 보이면, **모든 사용자가 초당 20번을 나눠 쓰게** 된다. 배포한 서버에서 한 번 확인한다.

```bash
# 내 PC에서 서버로 요청을 하나 보낸 뒤, 서버에서 마지막 접속 기록의 첫 칸(IP)을 본다
docker compose -f docker-compose.prod.yml logs --tail 3 web
# 첫 칸이 내 PC의 공인 IP(내 PC에서 curl ifconfig.me 결과)와 같으면 정상, 172.x면 문제 → 이 문서 담당(D)에게 알린다
```

- 앞에 로드 밸런서 · CDN을 두면 그 장비의 IP로 묶인다. 그때는 `set_real_ip_from`(그 장비 대역) + `real_ip_header X-Forwarded-For`를 같이 설정한다.
- HTTPS(BE-75)의 443 server도 http와 같은 `snippets/app.conf`를 include하므로 `/api`에 같은 `limit_req` · `error_page 429`가 걸린다. 앱 location은 이 파일에서만 고친다. zone도 하나라 한 IP의 http · https 요청을 합쳐 센다.
- **백엔드가 보는 IP(관리자 감사 로그)**는 아직 Nginx 컨테이너 IP다. 위 확인이 끝나면 `server.forward-headers-strategy=native`를 켠다(기술 스택 3장 "감사 로그", D). Nginx가 사용자 IP를 못 보는 상태에서 켜면 위조된 `X-Forwarded-For`가 그대로 기록될 수 있어 순서를 지킨다.

## Redis (BE-61, docs/tech-stack.md 2장 "Redis 운영 구성")

한 인스턴스를 논리 DB 두 개로 나눠 쓴다. **DB 0은 캐시**(지워져도 다시 만든다), **DB 1은 토큰 저장소**(지워지면 로그인이 풀린다).

| 설정 | 값 | 이유 |
| --- | --- | --- |
| 영속화 | AOF `everysec` | 재시작 · 배포 뒤에도 토큰이 남는다 (최대 1초 유실) |
| 메모리 | `maxmemory 200mb`, 컨테이너 256m | t3.small 2GB를 백엔드 · PostgreSQL과 나눈다. 남는 56MB는 AOF 재작성 여유분이라, **`used_memory`가 150MB를 넘으면 점검**한다(재작성 중 컨테이너가 메모리 초과로 죽을 수 있다) |
| 축출 | `noeviction` | 꽉 차도 토큰을 지우지 않는다. **대신 모든 키에 TTL이 있어야 한다** |
| 접근 | `requirepass`, 포트를 밖에 열지 않음 | 컨테이너 네트워크 안에서만 쓴다 |

**비밀번호가 보이는 곳**: 실행 명령 · 서버 `ps`에는 보이지 않지만, `docker inspect`의 환경변수로는 보인다(PostgreSQL 비밀번호도 같다). docker 명령을 쓸 수 있는 계정은 root와 같으므로 **`.env`는 `chmod 600`, docker 그룹에는 배포 담당만** 넣는다.

```bash
# 정책 · 메모리 확인 (REDISCLI_AUTH가 컨테이너에 있어 비밀번호를 입력하지 않는다)
docker compose -f docker-compose.prod.yml exec redis redis-cli CONFIG GET maxmemory-policy   # noeviction
docker compose -f docker-compose.prod.yml exec redis redis-cli INFO persistence | grep aof_enabled   # 1
docker compose -f docker-compose.prod.yml exec redis redis-cli INFO memory | grep used_memory_human

# DB별 키 수 · TTL 없는 키 찾기 (noeviction이라 TTL 없는 키가 쌓이면 메모리가 찬다)
docker compose -f docker-compose.prod.yml exec redis redis-cli INFO keyspace
docker compose -f docker-compose.prod.yml exec redis redis-cli -n 0 --scan | head   # 캐시 키 일부 확인 (KEYS * 는 운영에서 쓰지 않는다)
```

- **캐시만 비울 때**: `redis-cli -n 0 FLUSHDB`. 다른 DB는 그대로다. **`FLUSHALL`은 토큰까지 지우므로 쓰지 않는다.**
- **메모리가 찼을 때**(쓰기가 `OOM command not allowed`로 실패): 먼저 TTL 없는 캐시 키를 찾아 원인 코드를 고치고, 급하면 DB 0만 비운다.
- **데이터 위치**: 볼륨 `redisdata`. `docker compose down -v`는 이 볼륨도 지워 **모든 사용자가 로그아웃**되니 운영에서 쓰지 않는다.

## 운영 데이터 (docs/tech-stack.md 2장)

- 기본 실행(`docker-compose.prod.yml`만)은 **스키마만** 넣는다(`FLYWAY_LOCATIONS=classpath:db/migration`, `.env`로 바꿀 수 없게 고정). 처음 띄우면 사건 목록이 비어 있다("사건 0건").
- **개발용 가상 사건 시드(`classpath:db/seed`)는 운영(EC2)에 넣지 않는다.**
- 실제 사건은 비공개 저장소(`backend/private-seed` 서브모듈의 원본)의 `seed/` 폴더를 서버에 따로 받아 두고, 컨테이너에 **읽기 전용**으로 붙여 넣는다. 공개 저장소 · 이미지 · CI에는 들어가지 않는다(BE-17).

```bash
# 서버에서: 비공개 저장소를 읽기 전용 권한으로 받아 둔다 (권한은 저장소 관리자에게 받는다)
# .env에 REAL_SEED_DIR=/opt/lawnambul/private-seed/seed
docker compose -f docker-compose.prod.yml -f docker-compose.real-seed.yml up -d --no-build
```

- 시드는 반복 마이그레이션(`R__`)이라 같은 제목의 사건이 있으면 건너뛴다. 실제 사건 폴더를 붙였다가 빼고 실행해도 기동된다(`ignore-migration-patterns`).

## 다음 작업에서 바꿀 곳

| 작업 | 바꿀 곳 |
| --- | --- |
| 7.1.2 RDS 전환 · 시크릿 | `postgres` 서비스를 빼고 `DB_URL`을 RDS 주소로. 비밀값은 서버 환경변수 · 시크릿 저장소로. `backend`의 `depends_on: postgres`도 함께 뺀다. `deploy.sh`의 마이그레이션 비교는 `postgres` 서비스가 없으면 건너뛴다(RDS면 비교 방법을 다시 정한다). 자동 배포가 켜져 있으니 머지 직후 `journalctl -u lawnambul-cd`로 지켜본다 |
| 7.4.3 JWT (C) | 토큰은 `@Qualifier(RedisConfig.TOKEN)` 템플릿(DB 1)에 TTL과 함께 저장 |
| 백엔드 healthcheck 추가 (BE-66 이후) | Actuator(관리 포트 8081)에는 Redis 헬스체크가 자동으로 붙고, 커넥션 팩토리 두 개가 `redis` 아래 묶인다. **실측(BE-61, Boot 4.1.1)**: Redis를 끄면 `/actuator/health`는 **DOWN(503)**, `/actuator/health/liveness` · `/readiness`는 **UP**, API는 정상. liveness · readiness는 추가 설정 없이 이미 열려 있다. 그래서 컨테이너 healthcheck · 재시작 조건에는 **`/actuator/health`가 아니라 `/actuator/health/liveness`** 를 쓴다(전체 상태를 쓰면 Redis 장애 때 백엔드까지 재시작된다) |
| 7.4.6 Rate limit 2차 (10/23) | 로그인 · 토큰 재발급 엔드포인트에 더 엄격한 zone을 `default.conf`에 추가 (1차는 위 "Rate limit") |
| 서버 IP 확인 후 (D) | `server.forward-headers-strategy=native`로 백엔드가 사용자 IP를 받게 함 (위 "서버에서 꼭 확인" 다음에) |
| HTTPS 안정화 후 (BE-75 후속) | 갱신이 한 번 이상 정상으로 돈 뒤 `https.conf.template`에 HSTS를 짧은 `max-age`부터 추가 |
