# GitHub Actions 워크플로우

| 파일 | 대상 | 이름 |
| --- | --- | --- |
| `backend-ci.yml` | `backend/` · `frontend/og/` · `frontend/public/images/` · `tools/ai-judgment/contracts/` | backend CI (백엔드 밖 세 폴더는 백엔드 테스트가 같은지 확인하는 입력 — 성향 OG BE-73 · 일러스트 목록 BE-142 · 대본 블록 계약 BE-126) |
| `frontend-ci.yml` | `frontend/` | frontend CI |
| `deploy-images.yml` | `frontend/` · `backend/` · `deploy/` · `tools/ai-judgment/` | deploy images (운영 이미지 web · backend · pipeline 빌드 · 스모크 테스트 · GHCR 업로드 · 운영 배포 확인) |

## 실행 조건 (공통)

- backend CI · frontend CI: `develop`으로 가는 **Pull Request**와 `develop`에 대한 **push**에서 실행한다.
- deploy images: `develop` · `main`으로 가는 **Pull Request**와 두 브랜치에 대한 **push**에서 실행한다(아래 "deploy images").
- 위 표의 대상 폴더와 자기 워크플로우 파일이 바뀐 경우에만 실행한다.
- Actions 탭에서 **수동 실행**(`workflow_dispatch`)도 가능하다.
- 같은 PR에 새 push가 오면 이전 실행은 취소된다. (`concurrency`, CI 전용 설정)
- 권한은 `contents: read`만 쓴다(예외: deploy images의 `publish` job).

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

운영 이미지를 만들고, 운영 구성 그대로 띄워 확인한 뒤에만 올린다 (BE-58). 운영 반영은 서버가 스스로 한다 (BE-60). 자세한 내용은 [`deploy/README.md`](../../deploy/README.md), 전체 흐름은 [`docs/architecture.md`](../../docs/architecture.md).

| 이벤트 | 실행하는 job | 결과 |
| --- | --- | --- |
| PR (`develop` · `main` 대상) | `build` | 이미지 빌드 · 스모크 테스트만. 올리지 않는다 |
| `develop` push (머지) | `build` → `publish` | GHCR에 `develop` · `sha-커밋7자리` 태그로 올린다. **운영에는 배포하지 않는다** |
| `main` push (develop → main 머지) | `build` → `publish` → `verify-deploy` | GHCR에 `main` · `sha-커밋7자리` 태그로 올리고, 서버가 배포한 뒤 운영 주소로 확인한다 |
| 수동 실행(`workflow_dispatch`) | `develop`에서: `build` → `publish` / `main`에서: `build` → `publish` → `verify-deploy` | 경로 필터가 없다. push와 같은 조건(`PUBLISH`)이라 `develop` · `main`에서 돌리면 push와 똑같이 GHCR에 올라가고, `main`이면 운영 배포 확인까지 한다 |

1. **`build` job (읽기 권한만):** `frontend/Dockerfile`(Vite 실제 모드 빌드 → Nginx) · `backend/Dockerfile` · `tools/ai-judgment/Dockerfile`(파이프라인 서비스, COMMON-50)로 이미지 세 개를 빌드한다.
2. `deploy/docker-compose.prod.yml`로 web · backend · postgres · redis를 띄운다. DB · Redis 비밀번호는 실행마다 무작위 값을 만들고 로그에서 가린다.
3. `deploy/smoke-test.sh`로 `/` · 화면 주소(SPA fallback) · `/api`(없는 주소도 JSON) · 캐시 정책 · 보안 헤더를 확인한다.
4. 파이프라인은 운영 스택에 없어 위 스모크 테스트가 건드리지 않는다. 대신 이미지 안에서 작업 관리 단위 테스트(`tools/ai-judgment/service/tests`)를 돌리고, 컨테이너를 띄워 `/health`가 200인지 본다. LLM은 부르지 않는다.
5. **push일 때만** 통과한 이미지를 파일로 넘기고(아티팩트, 하루 뒤 삭제), **`publish` job**이 그 이미지를 그대로 GHCR(`ghcr.io/prgrms-aibe-devcourse/lawnambul-web` · `-backend` · `-pipeline`)에 올린다. 다시 빌드하지 않으므로 올라가는 이미지는 테스트한 이미지와 같다.
6. **`verify-deploy` job (`main` push만):** 서버의 `cd-agent`(2분마다 GHCR `main` 확인)가 배포하기를 기다린다. 운영 `/version.json`의 `revision`이 이 커밋이 될 때까지 최대 60회(매회 `curl` 최대 5초 + 15초 대기, 응답이 느리면 최악 약 20분까지)까지 확인한 뒤, 운영 주소로 `smoke-test.sh`를 돌린다(job 제한 20분). 저장소 변수 `DEPLOY_URL`이 없으면 건너뛴다. 실패하면 커밋한 사람에게 GitHub 알림이 간다.

- 공통 규칙의 예외: 업로드 때문에 **`publish` job만** `packages: write` 권한을 쓴다. PR에서 도는 `build` job은 읽기 권한뿐이고, checkout 토큰도 git 설정에 남기지 않는다(`persist-credentials: false`). 이미지 업로드는 중간에 끊기지 않도록 push 실행은 취소하지 않는다(PR 실행만 취소).
- Actions는 서버에 들어가지 않는다(SSH · AWS 권한 없음). 서버가 GHCR을 보고 스스로 배포하고, 실패하면 직전 이미지로 되돌린다(`deploy/cd-agent.sh` · `deploy/deploy.sh`).

## 수정할 때

- 비밀값은 워크플로우 파일에 적지 않고 GitHub Secrets를 쓴다.
- 이 폴더에는 `README.md`를 둬도 되지만, **`.github/README.md`는 만들지 않는다.** GitHub이 저장소 첫 화면에 루트 `README.md`보다 먼저 보여 준다.
