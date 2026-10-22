# GitHub Actions 워크플로우

| 파일 | 대상 | 이름 |
| --- | --- | --- |
| `backend-ci.yml` | `backend/` · `frontend/og/` | backend CI (`frontend/og/`는 성향 OG 빌드 입력. 시드와 같은지 백엔드 테스트가 확인, BE-73) |
| `frontend-ci.yml` | `frontend/` | frontend CI |
| `deploy-images.yml` | `frontend/` · `backend/` · `deploy/` | deploy images (운영 이미지 빌드 · 스모크 테스트 · GHCR 업로드) |

## 실행 조건 (공통)

- `develop`으로 가는 **Pull Request**와 `develop`에 대한 **push**에서 실행한다.
- 해당 폴더(`backend/**` 또는 `frontend/**`)와 자기 워크플로우 파일이 바뀐 경우에만 실행한다.
- Actions 탭에서 **수동 실행**(`workflow_dispatch`)도 가능하다.
- 같은 PR에 새 push가 오면 이전 실행은 취소된다. (`concurrency`, CI 전용 설정)
- 권한은 `contents: read`만 쓴다.

## backend CI

1. `pgvector/pgvector:pg17` PostgreSQL · `redis:7.4-alpine` Redis 서비스 컨테이너를 띄운다. 로컬 `docker-compose.test.yml`과 같은 이미지·테스트 DB(`lawnambul_test`)·호스트 포트(55432/56379)를 사용한다(BE-87).
2. JDK 17(Temurin)로 `backend`에서 `./gradlew test`를 실행한다.
3. `backend/Dockerfile`이 깨지지 않았는지 이미지만 빌드한다. (push하지 않음)

- DB 접속 값은 `TEST_DB_URL` · `TEST_DB_USERNAME` · `TEST_DB_PASSWORD`, Redis는 `TEST_REDIS_HOST` · `TEST_REDIS_PORT`를 쓴다. Gradle이 검증한 값을 `test` 프로필의 테스트 JVM에 주입한다. 기존 `DB_*`·`REDIS_*`는 상속하지 않는다. 로컬 실행·안전장치 설명은 [backend/TESTING.md](../../backend/TESTING.md).
- Redis 서비스 컨테이너는 실행 옵션을 받지 못해 운영 정책(AOF · `noeviction`)이 없다. CI는 캐시 · 토큰 DB 분리(`RedisConfigTest`)만 확인한다. 정책은 운영 구성(`deploy/docker-compose.prod.yml`)에 있지만 **스모크 테스트도 정책을 검사하지는 않는다**. 정책 확인 명령은 [`deploy/README.md`](../../deploy/README.md) "Redis".
- 테스트 Flyway 위치는 `classpath:db/migration,classpath:db/seed`로 고정해 공개 가상 시드도 검증한다. `FLYWAY_LOCATIONS`는 상속하지 않는다. 운영에는 가상 시드를 넣지 않는다. ([시드 설명](../../backend/src/main/resources/db/seed/README.md))
- **실제 사건 데이터 서브모듈(`backend/private-seed`)은 받지 않는다.** `actions/checkout`의 기본값(`submodules: false`)을 그대로 둔다. CI에 비공개 저장소 접근 토큰을 넣으면 로그 · 산출물로 실제 데이터가 샐 수 있으므로 넣지 않는다(BE-17).

## frontend CI

1. Node 24로 `frontend`에서 `npm ci`로 의존성을 설치한다. (`package-lock.json`이 커밋돼 있어야 한다)
2. `npm run test --if-present`: `package.json`에 `test` 스크립트가 있을 때만 실행한다.
3. `npm run build`: `VITE_API_BASE_URL=/api/v1`을 주입해 빌드한다.

## deploy images

운영 이미지를 만들고, 운영 구성 그대로 띄워 확인한 뒤에만 올린다 (BE-58). 자세한 내용은 [`deploy/README.md`](../../deploy/README.md).

1. **`build` job (읽기 권한만):** `frontend/Dockerfile`(Vite 실제 모드 빌드 → Nginx) · `backend/Dockerfile`로 이미지를 빌드한다.
2. `deploy/docker-compose.prod.yml`로 web · backend · postgres · redis를 띄운다. DB · Redis 비밀번호는 실행마다 무작위 값을 만들고 로그에서 가린다.
3. `deploy/smoke-test.sh`로 `/` · 화면 주소(SPA fallback) · `/api`(없는 주소도 JSON) · 캐시 정책을 확인한다.
4. **`develop` push일 때만** 통과한 이미지를 파일로 저장해 넘기고(아티팩트, 하루 뒤 삭제), **`publish` job**이 그 이미지를 그대로 GHCR(`ghcr.io/prgrms-aibe-devcourse/lawnambul-web` · `-backend`)에 `develop` · `sha-커밋7자리` 태그로 올린다. 다시 빌드하지 않으므로 올라가는 이미지는 테스트한 이미지와 같다. PR에서는 `publish` job이 실행되지 않는다.

- 공통 규칙의 예외: 업로드 때문에 **`publish` job만** `packages: write` 권한을 쓴다. PR에서 도는 `build` job은 읽기 권한뿐이고, checkout 토큰도 git 설정에 남기지 않는다(`persist-credentials: false`). 이미지 업로드는 중간에 끊기지 않도록 `develop` 실행은 취소하지 않는다(PR 실행만 취소).
- 서버 반영(배포 단계)은 7.1.3 CD에서 이 워크플로 뒤에 붙인다.

## 수정할 때

- 비밀값은 워크플로우 파일에 적지 않고 GitHub Secrets를 쓴다.
- 이 폴더에는 `README.md`를 둬도 되지만, **`.github/README.md`는 만들지 않는다.** GitHub이 저장소 첫 화면에 루트 `README.md`보다 먼저 보여 준다.
