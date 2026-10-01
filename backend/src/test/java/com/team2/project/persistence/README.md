# Repository 통합 테스트 (`persistence`)

실제 PostgreSQL에 붙어서 엔티티 매핑, Repository 쿼리, DB 제약조건을 확인하는 통합 테스트다. 각 테스트는 트랜잭션 안에서 실행되고 끝나면 롤백되어 DB에 데이터가 남지 않는다.

## 파일

| 파일 | 내용 |
| --- | --- |
| `JpaMappingTest.java` | 엔티티 매핑과 jsonb 컬럼 읽기, 체험 진행 흐름, 판결 값 검증, 최종 확정 판결 유니크, 비교 분석 상태 이동 |
| `RepositoryQueryIntegrationTest.java` | Repository 조회 조건과 정렬, 참여자 수 집계, 유니크 제약 위반 |

### `RepositoryQueryIntegrationTest` 구성

| 구분 | 확인하는 것 |
| --- | --- |
| 사건 조회 | 공개 사건만 최신 공개순으로 조회, 범죄 유형 필터, 비공개 사건 단건 조회 시 빈 결과 |
| 정렬 | 섹션과 판단 요소가 `display_order` 순서로 나옴 |
| 체험 | 가장 최근 회차 조회, 참여자 수는 1회차이면서 완료한 체험만 집계 |
| 비교 분석 | 체험 ID로 조회, 없으면 빈 결과 |
| DB 제약 | 사건당 형벌 규칙 중복, 사용자·사건·회차 중복 체험, 공개된 AI 판결 중복은 거절 |

## 실행 방법

테스트는 `application.yml`의 기본 접속 정보(로컬 docker-compose의 Postgres)를 쓴다. DB를 먼저 띄운다.

```bash
# backend 폴더에서
docker compose up -d
./gradlew test

# 이 패키지의 테스트만
./gradlew test --tests "com.team2.project.persistence.*"
```

DB 접속 정보를 바꾸려면 `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` 환경변수를 지정한다.

## 지켜야 할 것

- **운영 DB를 가리킨 채로 실행하지 않는다.** 롤백되더라도 테스트용 데이터를 운영에 넣게 된다.
- DB에 시드 데이터가 있을 수 있다. 목록 조회를 검증할 때는 테스트가 넣은 사건 ID만 골라서 비교하고, 전체 개수에 기대지 않는다.
- JPA로 저장한 뒤 JDBC로 바로 참조하는 데이터는 먼저 `flush`한다. 하지 않으면 외래키 위반이 난다.
- 제약 위반이 나면 PostgreSQL이 그 트랜잭션을 중단시켜 이후 SQL이 모두 실패한다. 제약 위반은 테스트 하나에서 한 가지만 확인한다.
- 테스트 메서드 이름은 `{메서드명}_{상태/기대결과}` 형태로 짓는다. ([코드 컨벤션](../../../../../../../../docs/conventions/CODE_CONVENTIONS.md) 5장)

## 관련 문서

- [ERD](../../../../../../../../docs/erd.md): 테이블과 제약조건
- [코드 컨벤션](../../../../../../../../docs/conventions/CODE_CONVENTIONS.md): 테스트 규칙
- [루트 README](../../../../../../../../README.md): 로컬 실행 방법
