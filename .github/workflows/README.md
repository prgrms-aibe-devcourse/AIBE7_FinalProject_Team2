# GitHub Actions 워크플로우

| 파일 | 대상 | 이름 |
| --- | --- | --- |
| `backend-ci.yml` | `backend/` | backend CI |
| `frontend-ci.yml` | `frontend/` | frontend CI |

## 실행 조건 (공통)

- `develop`으로 가는 **Pull Request**와 `develop`에 대한 **push**에서 실행한다.
- 해당 폴더(`backend/**` 또는 `frontend/**`)와 자기 워크플로우 파일이 바뀐 경우에만 실행한다.
- Actions 탭에서 **수동 실행**(`workflow_dispatch`)도 가능하다.
- 같은 PR에 새 push가 오면 이전 실행은 취소된다. (`concurrency`, CI 전용 설정)
- 권한은 `contents: read`만 쓴다.

## backend CI

1. `pgvector/pgvector:pg17` PostgreSQL 서비스 컨테이너를 띄운다. (로컬 `docker-compose.yml`과 같은 이미지)
2. JDK 17(Temurin)로 `backend`에서 `./gradlew test`를 실행한다.
3. `backend/Dockerfile`이 깨지지 않았는지 이미지만 빌드한다. (push하지 않음)

- DB 접속 값(`DB_URL` · `DB_USERNAME` · `DB_PASSWORD`)은 `application.yml`이 읽는 환경변수 이름과 같아야 한다.
- `FLYWAY_LOCATIONS`에 `classpath:db/seed`를 포함해 개발용 시드도 함께 적용한다. 운영에는 넣지 않는다. ([시드 설명](../../backend/src/main/resources/db/seed/README.md))
- **실제 사건 데이터 서브모듈(`backend/private-seed`)은 받지 않는다.** `actions/checkout`의 기본값(`submodules: false`)을 그대로 둔다. CI에 비공개 저장소 접근 토큰을 넣으면 로그 · 산출물로 실제 데이터가 샐 수 있으므로 넣지 않는다(BE-17).

## frontend CI

1. Node 24로 `frontend`에서 `npm ci`로 의존성을 설치한다. (`package-lock.json`이 커밋돼 있어야 한다)
2. `npm run test --if-present`: `package.json`에 `test` 스크립트가 있을 때만 실행한다.
3. `npm run build`: `VITE_API_BASE_URL=/api/v1`을 주입해 빌드한다.

## 수정할 때

- 비밀값은 워크플로우 파일에 적지 않고 GitHub Secrets를 쓴다.
- 이 폴더에는 `README.md`를 둬도 되지만, **`.github/README.md`는 만들지 않는다.** GitHub이 저장소 첫 화면에 루트 `README.md`보다 먼저 보여 준다.
