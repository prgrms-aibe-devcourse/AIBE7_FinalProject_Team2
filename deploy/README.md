# 배포 (BE-58, WBS 7.1.1)

운영은 서버 한 대에 컨테이너 세 개를 띄운다. 밖으로 여는 포트는 웹 80 하나뿐이다.

```text
브라우저 ──▶ web (Nginx :80) ──┬─ /            → 정적 파일 (Vite 빌드, 실제 모드)
                               ├─ /cases/...   → index.html (SPA fallback, 라우터가 화면 선택)
                               └─ /api/...     → backend (Spring Boot :8080) ──▶ postgres (:5432)
```

| 파일 | 내용 |
| --- | --- |
| `docker-compose.prod.yml` | 운영 구성 (web · backend · postgres). 기본은 스키마만 |
| `docker-compose.real-seed.yml` | 서버 전용 덧붙임 파일. 실제 사건 seed 폴더를 백엔드에 읽기 전용으로 연결 |
| `.env.example` | 환경변수 예시. `.env`로 복사해 쓴다 (`.env`는 커밋하지 않음) |
| `smoke-test.sh` | `/` · 화면 주소 · `/api` · 캐시 정책 확인. CI와 서버에서 같은 스크립트를 쓴다 |
| `../frontend/Dockerfile` | 웹 이미지 (Vite 빌드 → Nginx) |
| `../frontend/nginx/default.conf` | Nginx 설정 (`/api` 프록시 · SPA fallback · 캐시) |
| `../.github/workflows/deploy-images.yml` | 이미지 빌드 → 스모크 테스트 → GHCR 업로드 |

## 이미지가 만들어지는 흐름

1. `develop`으로 가는 PR: Actions가 이미지를 빌드해 이 구성으로 띄우고 `smoke-test.sh`를 실행한다. **업로드하지 않는다.**
2. `develop` push(머지): 스모크 테스트를 통과한 이미지만 GHCR에 올린다.
   - `ghcr.io/prgrms-aibe-devcourse/lawnambul-web` · `ghcr.io/prgrms-aibe-devcourse/lawnambul-backend`
   - 태그: `develop`(최신), `sha-커밋7자리`(되돌릴 때)
3. 서버는 이미지를 받아서 실행만 한다. 서버에서 Gradle · npm 빌드를 하지 않는다 (메모리 절약).
4. 서버 자동 반영은 7.1.3 CD에서 붙인다. 그 전까지는 아래 "서버에서 실행"을 손으로 한다.

## 로컬에서 운영 구성 확인

```bash
cd deploy
cp .env.example .env
# .env에서 POSTGRES_PASSWORD를 아무 값으로 채운다. 80번 포트가 쓰이고 있으면 WEB_PORT=8081
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

되돌리기: `.env`의 `IMAGE_TAG`를 이전 `sha-xxxxxxx`로 바꾸고 `pull` → `up -d --no-build`.

## HTTPS 전 제한 (도메인 확보 후 해결)

- 익명 ID 쿠키 `NLNB_AID`는 항상 `Secure`다(`AnonymousIdCookie.java`, API 명세 1-2). 브라우저는 **http에서 `Secure` 쿠키를 저장하지 않는다** (`localhost`만 예외).
- 그래서 IP · http로 열면 `/`와 조회 API(사건 목록 등)는 동작하지만, **체험 시작 이후 흐름(익명 ID가 필요한 API)은 동작하지 않는다.** 관리자 세션 쿠키(`SESSION_COOKIE_SECURE`)도 같다.
- 도메인을 정하면 인증서(Let's Encrypt)를 붙여 HTTPS로 연다. 그 전까지 체험 흐름 확인은 로컬(`localhost`)에서 한다.

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
| 7.1.2 RDS 전환 · 시크릿 | `postgres` 서비스를 빼고 `DB_URL`을 RDS 주소로. 비밀값은 서버 환경변수 · 시크릿 저장소로 |
| 7.1.3 CD | `deploy-images.yml` 뒤에 서버 반영 단계 추가 (`pull` → `up -d --no-build` → `smoke-test.sh`) |
| 7.1.4 Redis | `redis` 서비스 추가 (캐시 DB · 토큰 저장소 DB 분리, 영속화) |
| 7.4.6 Rate limit | `../frontend/nginx/default.conf`에 `limit_req` 추가 |
| 도메인 확보 후 | `default.conf`에 443 · 인증서 · http → https 이동 추가 |
