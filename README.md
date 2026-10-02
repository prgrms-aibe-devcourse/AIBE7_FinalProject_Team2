# 내Law남불

> 당신이 판사라면, 어떤 판결을 내리겠습니까?

사용자가 사건을 단계별로 읽고 **직접 판결을 내린 뒤**, AI 판결과 실제 판결을 **같은 판단 요소**로 비교해 보는 서비스다. AIBE7 최종 프로젝트 Team2가 만든다.

MVP는 로그인 없이, 대표 사건 1건으로 아래 흐름을 끝까지 체험하는 것이다. ([MVP 정의서](docs/mvp-definition.md))

```
사전 판단 → 단계별 사건 확인 → 직접 판결 → AI 판결 → 실제 판결 → 세 판결 비교
```

## 기술 스택

| 구분 | 내용 |
| --- | --- |
| Backend | Java 17, Spring Boot 4.1.1 (Web MVC · Data JPA · Validation), Gradle |
| DB | PostgreSQL 17, Flyway(스키마 마이그레이션) |
| Frontend | HTML + JavaScript, Vite |
| CI | GitHub Actions |

자세한 구성과 선택 이유는 [기술 스택 정리](docs/tech-stack.md)를 본다.

## 폴더 구조

| 폴더 | 내용 |
| --- | --- |
| `backend/` | Spring Boot API 서버, 로컬 개발용 PostgreSQL(`docker-compose.yml`), DB 마이그레이션 · 개발용 시드 |
| `frontend/` | 화면 (자세한 내용은 [frontend/README.md](frontend/README.md)) |
| `docs/` | 기획 · 설계 문서 ([문서 목차](docs/README.md)) |
| `tools/ai-judgment/` | AI 판결 오프라인 생성 · 검수 도구 ([README](tools/ai-judgment/README.md)) |
| `.github/workflows/` | CI 워크플로우 ([README](.github/workflows/README.md)) |

## 로컬 실행

### 백엔드

JDK 17이 필요하다. 기본 JDK가 17이 아니면 `JAVA_HOME`을 17로 지정한 뒤 실행한다.

```bash
cd backend
docker compose up -d      # PostgreSQL 컨테이너(lawnambul-postgres, 5432) 실행
./gradlew bootRun         # 서버 실행 (기본 8080, 시작할 때 Flyway가 스키마를 만든다)
```

개발용 가상 사건 시드까지 넣으려면 아래처럼 실행한다. ([시드 설명](backend/src/main/resources/db/seed/README.md))

```bash
FLYWAY_LOCATIONS=classpath:db/migration,classpath:db/seed ./gradlew bootRun
```

실제 대표 사건 데이터는 공개 저장소에 두지 않고 **비공개 저장소를 서브모듈(`backend/private-seed`)로 연결**해 쓴다(BE-17). 접근 권한이 있는 팀원만 받을 수 있고, 권한이 없으면 빈 폴더로 남으며 가상 시드로 그대로 개발할 수 있다.

```bash
# 저장소 루트에서 (처음 한 번, 권한 필요)
git submodule update --init backend/private-seed
# backend 폴더로 이동해 실제 사건까지 넣어 실행
cd backend
FLYWAY_LOCATIONS=classpath:db/migration,filesystem:./private-seed/seed ./gradlew bootRun
```

- 서브모듈 안의 파일을 공개 저장소의 다른 위치(`db/seed` 등)로 **복사하지 않는다.** 공개 저장소의 커밋 메시지 · PR · Jira 댓글에도 사건 내용(형량 · 판단 요소 · 사실관계)을 쓰지 않는다.
- 서브모듈은 `src/main/resources` 밖에 있어 jar에 들어가지 않고, `backend/.dockerignore`로 Docker 빌드에서도 뺀다.

로컬 DB를 비우고 처음부터 다시 만들려면 `docker compose down -v && docker compose up -d`를 실행한다. (볼륨이 삭제된다)

테스트는 실제 PostgreSQL에 연결하므로 DB를 먼저 띄운 뒤 실행한다.

```bash
./gradlew test
```

접속 정보는 환경변수로 바꾼다. 값이 없으면 로컬 `docker-compose.yml` 기본값을 쓴다. (`backend/src/main/resources/application.yml`)

| 이름 | 기본값 | 설명 |
| --- | --- | --- |
| `DB_URL` | `jdbc:postgresql://localhost:5432/lawnambul` | DB 접속 주소 |
| `DB_USERNAME` | `lawnambul` | DB 계정 (로컬 전용 값) |
| `DB_PASSWORD` | `lawnambul` | DB 비밀번호 (로컬 전용 값) |
| `DDL_AUTO` | `validate` | Hibernate 스키마 처리 방식 (스키마는 Flyway로만 바꾼다) |
| `FLYWAY_LOCATIONS` | `classpath:db/migration` | 마이그레이션 위치. 가상 시드는 `,classpath:db/seed`, 실제 사건은 `,filesystem:./private-seed/seed`를 덧붙여 켠다 |
| `SWAGGER_ENABLED` | `false` | Swagger UI · OpenAPI 문서 노출. 로컬 · 개발에서만 `true`로 켠다 (`/swagger-ui/index.html`, `/v3/api-docs`) |

운영 DB 계정은 이 저장소에 적지 않고 실행할 때 환경변수로만 넘긴다. 시드는 운영에 넣지 않는다.

### 프론트엔드

```bash
cd frontend
npm install
npm run dev     # http://localhost:5173 (/api 요청은 localhost:8080 백엔드로 프록시)
```

API 없이 화면만 확인하려면 개발 서버를 켠 뒤 `http://localhost:5173/preview.html`을 연다. 자세한 내용은 [frontend/README.md](frontend/README.md)와 [목 API 설명](frontend/src/mocks/README.md)을 본다.

## 문서

| 문서 | 내용 |
| --- | --- |
| [문서 목차](docs/README.md) | 모든 문서의 역할과 읽는 순서 |
| [API 명세서](docs/api-specification.md) | 경로 · 요청 · 응답 · 에러 코드 |
| [ERD](docs/erd.md) | 테이블 · 제약 · 예시 데이터 |
| [정보 구조](docs/information-architecture.md) | 화면 목록과 흐름 |

## 협업 규칙

작업은 **Jira 이슈**로 시작하고, 브랜치 · 커밋 · PR 이름에 이슈 키(`BE-15`, `FE-8` 등)를 넣는다. 자세한 규칙은 [Git 컨벤션](docs/conventions/GIT_CONVENTIONS.md), 코드 규칙은 [코드 컨벤션](docs/conventions/CODE_CONVENTIONS.md)을 따른다.

| 항목 | 형식 | 예 |
| --- | --- | --- |
| 브랜치 | `feature/{ISSUE-KEY}-{작업내용}` | `feature/BE-15-login-api` |
| 커밋 | `영어카테고리 : ISSUE-KEY 한글 설명` | `feat : BE-15 로그인 API 구현` |
| PR 제목 | `[ISSUE-KEY] 작업 내용` | `[BE-15] 로그인 API 구현` |
