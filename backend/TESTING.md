# 테스트 전용 PostgreSQL · Redis

## 실행 (backend 디렉터리)

아래 명령은 환경 구성 후 사용자가 실행한다. Docker Desktop과 Java 17이 필요하다.
개발용 `docker-compose.yml`과 병합하지 않는다. 포트 55432 · 56379가 비어 있는지 먼저 확인한다.

```powershell
docker compose -f docker-compose.test.yml -p lawnambul-test up -d --wait
.\gradlew.bat cleanTest test
```

Linux/macOS에서는 `./gradlew cleanTest test`를 사용한다. IDE 테스트 실행도 Gradle에 위임한다.
`test` 프로필을 IDE에서 직접 켜는 것만으로는 Gradle의 연결 차단 장치가 적용되지 않는다.
한 테스트 DB에 여러 Gradle 실행을 동시에 연결하지 않는다. 테스트 내부 동시성 검증은 그대로 실행된다.

| 항목 | 개발 | 테스트 (로컬/CI 공통) |
| --- | --- | --- |
| PostgreSQL 호스트 포트 | 5432 | 55432 |
| DB / 계정 | lawnambul | lawnambul_test |
| Redis 호스트 포트 | 6379 | 56379 |
| Redis 논리 DB | 캐시 0 / 토큰 1 | 캐시 0 / 토큰 1 (별도 서버) |
| Compose 프로젝트 | 기존 개발 프로젝트 | lawnambul-test |
| 볼륨 | 기존 개발 볼륨 | lawnambul-test_test-pgdata / lawnambul-test_test-redisdata |

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
  클래스패스의 application.yml/application-test.yml만 읽는다. 향후 테스트에서 접속 속성을
  `@SpringBootTest(properties=...)`, `@DynamicPropertySource` 등으로 덮어쓰지 않는다.
- Flyway와 JPA 모두 같은 전용 DB로 고정한다. 기존 `db/migration`과 공개 가상 사건 `db/seed`만 적용한다.
  비공개 시드는 읽지 않는다. 기존 pgvector 마이그레이션과 Hibernate `validate`, Flyway clean 차단을 유지한다.
- 관리자 자동 생성은 빈 이메일/비밀번호로 비활성화한다. 부트스트랩 자체의 단위/통합 테스트 호출은 유지한다.

## 정리

컨테이너만 중지/제거하며 테스트 데이터 볼륨은 남긴다.

```powershell
docker compose -f docker-compose.test.yml -p lawnambul-test down
```

테스트 데이터까지 초기화하기로 결정한 경우에만 아래 명령을 사용한다.
이 명령은 위 전용 프로젝트의 테스트 볼륨만 제거한다. 개발/운영 Compose 또는 `docker volume prune`을 사용하지 않는다.

```powershell
docker compose -f docker-compose.test.yml -p lawnambul-test down --volumes
```

최초 실행 전 이미지/포트/프로젝트명을 확인한다. 55432/56379를 개발·운영 서비스로 재매핑하지 않는다.
기존 테스트는 데이터를 삽입/삭제하므로 테스트 전용 서비스에서만 실행한다.
설정 작성 단계에서는 DB 접속·Compose 실행·통합 테스트를 수행하지 않았다. 전체 테스트 통과 여부는 별도 실행으로 확인한다.
