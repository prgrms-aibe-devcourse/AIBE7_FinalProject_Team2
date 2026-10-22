# 테스트 전용 PostgreSQL · Redis

## 실행 (backend 디렉터리)

아래 명령은 환경 구성 후 사용자가 실행한다. Docker Desktop과 Java 17이 필요하다.
개발용 `docker-compose.yml`과 병합하지 않는다. 포트 55432 · 56379가 비어 있는지 먼저 확인한다.

macOS / Linux:

```bash
docker compose -f docker-compose.test.yml -p lawnambul-test up -d --wait
./gradlew cleanTest test
```

Windows PowerShell에서는 같은 Compose 명령 후 `.\gradlew.bat cleanTest test`를 실행한다.
한 테스트 DB에 여러 Gradle 실행을 동시에 연결하지 않는다. 테스트 내부 동시성 검증은 그대로 실행된다.

| 항목 | 개발 | 테스트 (로컬/CI 공통) |
| --- | --- | --- |
| PostgreSQL 호스트 포트 | 5432 | 55432 |
| DB / 계정 | lawnambul | lawnambul_test |
| Redis 호스트 포트 | 6379 | 56379 |
| Redis 논리 DB | 캐시 0 / 토큰 1 | 캐시 0 / 토큰 1 (별도 서버) |
| Compose 프로젝트 | 기존 개발 프로젝트 | lawnambul-test |
| 볼륨 | 기존 개발 볼륨 | lawnambul-test_test-pgdata / lawnambul-test_test-redisdata |

팀 공용 개발 DB(Supabase, [SHARED-DEV-DB.md](SHARED-DEV-DB.md), COMMON-59)에 붙어 개발하는 중이어도 테스트는 위 테스트 전용 DB만 쓴다 — `.env.supabase`의 `DB_*` · `SPRING_*` 값은 테스트 실행에 넘어가지 않는다(아래 "연결 차단과 설정").

CI는 기존 서비스 컨테이너를 사용하므로 Compose를 실행하지 않는다. 실행마다 서비스가 새로 생성되며,
로컬은 테스트 전용 볼륨을 유지한다. CI Redis는 기존대로 기본 정책을 사용하고, 로컬은 AOF/noeviction을 적용한다.

## 연결 차단과 설정

- Gradle의 모든 `Test` 작업은 `test` 프로필과 검증된 연결 설정을 JVM 시스템 속성으로 주입한다.
- `TEST_DB_URL`은 `jdbc:postgresql://127.0.0.1:55432/lawnambul_test` 또는 호스트만 `localhost`인 값만 허용한다.
  DB명, 포트, 호스트, URL 옵션이 다르면 테스트 JVM 실행 전에 실패한다. 계정도 `lawnambul_test`만 허용한다.
- 선택 변수: `TEST_DB_USERNAME`, `TEST_DB_PASSWORD`, `TEST_REDIS_HOST`, `TEST_REDIS_PORT`.
  기본값은 Compose와 같다. Redis는 localhost/127.0.0.1:56379만 허용하며 비밀번호는 없다.
- 기존 `DB_*`, `SPRING_*`(특히 `SPRING_DATASOURCE_URL`, `SPRING_APPLICATION_JSON`, `SPRING_CONFIG_*`),
  `REDIS_*`, `FLYWAY_*`, `DDL_AUTO`, `ADMIN_BOOTSTRAP_*`는 테스트 자식 프로세스에 전달하지 않는다.
  `JAVA_TOOL_OPTIONS`, `JDK_JAVA_OPTIONS`, `_JAVA_OPTIONS`도 전달하지 않으므로 필요한 JVM 옵션은 별도로 검토한다.
- Gradle `jvmArgs`의 Spring/Redis/관리자 부트스트랩 속성 재지정은 거절한다. 외부 설정 파일 대신
  클래스패스의 application.yml/application-test.yml만 읽는다. RAG 속성 재지정도 거절한다.
- Flyway와 JPA 모두 같은 전용 DB로 고정한다. 기존 `db/migration`과 공개 가상 사건 `db/seed`만 적용한다.
  비공개 시드는 읽지 않는다. 기존 pgvector 마이그레이션과 Hibernate `validate`, Flyway clean 차단을 유지한다.
- 관리자 자동 생성은 빈 이메일/비밀번호로 비활성화한다. 부트스트랩 자체의 단위/통합 테스트 호출은 유지한다.

## IDE 실행과 보호 범위

IntelliJ의 Settings > Build, Execution, Deployment > Build Tools > Gradle에서
**Run tests using: Gradle**을 권장한다. 이 경우 CLI/CI와 동일하게 JVM 시작 전 검증과 환경변수 제거가 적용된다.

IntelliJ IDEA 자체 실행기를 쓰려면 실행 구성에서 `test` 프로필(`-Dspring.profiles.active=test`)을 지정하고
테스트 전용 서비스를 먼저 준비한다. 테스트 리소스의 `META-INF/spring.factories`가
`TestEnvironmentGuard`를 자동 등록하므로 현재 `@SpringBootTest`들도 Gradle과 무관하게 보호된다.
프로필을 빠뜨리거나 개발 DB로 덮어쓰면 빈 생성 전에 실패한다. 값을 조용히 개발 DB에서 테스트 DB로 바꾸지는 않는다.

| 실행 방식 | JVM 시작 전 Gradle 검증/환경 정리 | Spring 컨텍스트 시작 차단 |
| --- | --- | --- |
| Gradle CLI · CI · IntelliJ Gradle 위임 | 적용 | 적용 |
| IntelliJ 자체 실행의 `@SpringBootTest` | 미적용 | 적용 (`test` 프로필 직접 지정 필요) |
| Spring 없는 단위 테스트 | Gradle 실행일 때만 적용 | 컨텍스트가 없어 미적용 |
| 직접 만든 일반 Spring 컨텍스트 / JDBC / HTTP 클라이언트 | Gradle 실행일 때만 적용 | 자동 등록 대상 아님 |

초기화기는 유효한 DataSource·Flyway·Hikari URL/계정, Redis 호스트/포트, 공개 마이그레이션 위치,
Hibernate validate, Flyway clean 차단, 관리자 자동 생성 비활성화를 검사한다.
초기화 이후 테스트 속성이 바뀌는 경우도 빈 생성 전 다시 검사한다.
일반 Spring 컨텍스트는 초기화기를 명시 적용해야 하며, 직접 생성한 DataSource·임의 BeanFactoryPostProcessor·
직접 JDBC/HTTP 호출까지 봉쇄하는 샌드박스는 아니다. 테스트에서 접속 설정을 별도 객체로 우회하지 않는다.
보호 코드는 테스트 소스/리소스에만 있어 운영 jar에는 포함되지 않는다.

## 외부 API 키

- Gradle은 `*_API_KEY` 및 `RAG_*` 환경변수를 테스트 JVM에 전달하지 않고 `rag.embedding.api-key`를 빈 값으로 고정한다.
- `test` 프로필도 키를 비우며, 초기화기는 환경변수·시스템 속성·JSON 설정보다 우선하는 빈 값을 주입한다.
  IntelliJ 자체 실행에서는 프로세스 환경변수 자체를 제거할 수 없지만 현재 실제 RAG 클라이언트는 비활성화된다.
- `GeminiQueryEmbeddingClientTest`는 직접 구성한 가짜 키와 `MockRestServiceServer`, `RagSearchServiceTest`는
  가짜 임베딩 빈을 사용하므로 정상적인 모의 호출 검증은 유지한다.
- 모든 HTTP 통신을 차단하는 방화벽은 아니다. 향후 외부 클라이언트를 추가하거나 `System.getenv()`로 키를 직접 읽는
  테스트를 만들 때는 별도 모의 클라이언트와 차단 정책을 검토한다. 실제 키를 테스트에 입력하지 않는다.

## 브랜치 전환 후 Flyway 체크섬 충돌

테스트 볼륨은 반복 실행 속도와 실패 시 조사 가능성을 위해 유지한다. 서로 다른 브랜치가 같은 버전의 마이그레이션을
다른 내용으로 가지고 있으면 이전 이력과 체크섬이 달라질 수 있다. 먼저 실패한 버전과 현재 브랜치의 공개 마이그레이션을
비교한다. 이미 공유된 마이그레이션을 수정하거나 `repair`·검증 비활성화로 불일치를 숨기지 않는다.

현재 데이터와 볼륨을 보존해야 하면 다음처럼 **다른 테스트 프로젝트의 새 볼륨**을 사용한다.
`lawnambul-test-branch-check`는 예시 이름이며, 이전에 사용하지 않은 프로젝트명을 고른다.
같은 포트를 사용하므로 기존 테스트 프로젝트를 먼저 중지한다(삭제하지 않음).

```bash
docker compose -f docker-compose.test.yml -p lawnambul-test stop
docker compose -f docker-compose.test.yml -p lawnambul-test-branch-check up -d --wait
```

macOS/Linux는 `./gradlew cleanTest test`, Windows는 `.\gradlew.bat cleanTest test`로 검증한다.
원래 브랜치·데이터로 돌아갈 때는 새 프로젝트를 중지한 뒤 원래 프로젝트를 `start`한다.
이 절차는 기존 볼륨을 삭제·초기화하지 않는다. Redis도 프로젝트별 별도 볼륨을 사용한다.
새 DB에서도 마이그레이션이 실패하면 환경 초기화 문제가 아니라 코드/마이그레이션 문제인지 확인한다.

## 정리

컨테이너만 중지/제거하며 테스트 데이터 볼륨은 남긴다.

```bash
docker compose -f docker-compose.test.yml -p lawnambul-test down
```

테스트 데이터까지 초기화하기로 결정한 경우에만 아래 명령을 사용한다.
이 명령은 위 전용 프로젝트의 테스트 볼륨만 제거한다. 개발/운영 Compose 또는 `docker volume prune`을 사용하지 않는다.

```bash
docker compose -f docker-compose.test.yml -p lawnambul-test down --volumes
```

최초 실행 전 이미지/포트/프로젝트명을 확인한다. 55432/56379를 개발·운영 서비스로 재매핑하지 않는다.
기존 테스트는 데이터를 삽입/삭제하므로 테스트 전용 서비스에서만 실행한다.
