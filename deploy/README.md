# 배포 (BE-58, WBS 7.1.1 · Redis BE-61, WBS 7.1.4)

운영은 서버 한 대에 컨테이너 네 개를 띄운다. 밖으로 여는 포트는 웹 80 하나뿐이다.

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

되돌리기: `.env`의 `IMAGE_TAG`를 이전 `sha-xxxxxxx`로 바꾸고 `pull` → `up -d --no-build`.

## HTTPS 전 제한 (도메인 확보 후 해결)

- 익명 ID 쿠키 `NLNB_AID`는 항상 `Secure`다(`AnonymousIdCookie.java`, API 명세 1-2). 브라우저는 **http에서 `Secure` 쿠키를 저장하지 않는다** (`localhost`만 예외).
- 그래서 IP · http로 열면 `/`와 조회 API(사건 목록 등)는 동작하지만, **체험 시작 이후 흐름(익명 ID가 필요한 API)은 동작하지 않는다.** 관리자 세션 쿠키(`SESSION_COOKIE_SECURE`)도 같다.
- 도메인을 정하면 인증서(Let's Encrypt)를 붙여 HTTPS로 연다. 그 전까지 체험 흐름 확인은 로컬(`localhost`)에서 한다.

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
| 7.1.2 RDS 전환 · 시크릿 | `postgres` 서비스를 빼고 `DB_URL`을 RDS 주소로. 비밀값은 서버 환경변수 · 시크릿 저장소로 |
| 7.1.3 CD | `deploy-images.yml` 뒤에 서버 반영 단계 추가 (`pull` → `up -d --no-build` → `smoke-test.sh`) |
| 7.4.3 JWT (C) | 토큰은 `@Qualifier(RedisConfig.TOKEN)` 템플릿(DB 1)에 TTL과 함께 저장 |
| 백엔드 healthcheck 추가 (BE-66 이후) | Actuator(관리 포트 8081)에는 Redis 헬스체크가 자동으로 붙고, 커넥션 팩토리 두 개가 `redis` 아래 묶인다. **실측(BE-61, Boot 4.1.1)**: Redis를 끄면 `/actuator/health`는 **DOWN(503)**, `/actuator/health/liveness` · `/readiness`는 **UP**, API는 정상. liveness · readiness는 추가 설정 없이 이미 열려 있다. 그래서 컨테이너 healthcheck · 재시작 조건에는 **`/actuator/health`가 아니라 `/actuator/health/liveness`** 를 쓴다(전체 상태를 쓰면 Redis 장애 때 백엔드까지 재시작된다) |
| 7.4.6 Rate limit | `../frontend/nginx/default.conf`에 `limit_req` 추가 |
| 도메인 확보 후 | `default.conf`에 443 · 인증서 · http → https 이동 추가 |
