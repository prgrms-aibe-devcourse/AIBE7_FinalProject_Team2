# 개발용 시드 (`db/seed`)

로컬과 CI에서 화면 · API를 확인하려고 넣는 **가상 살인 사건 1건**이다. 설명용으로 지어낸 사건이며 실제 판례가 아니다.

## 파일

| 파일 | 내용 |
| --- | --- |
| `R__seed_sample_case.sql` | 반복 마이그레이션(`R__`). 양형기준 버전, 사건, 사건 섹션, 형벌 규칙, 판단 요소 11개, 원본 판결문(`SAMPLE-0001`), AI · 재판부 판단을 넣는다 |

사전 판단 형량 구간(`sentence_range_option`)은 시드가 아니라 `V1` 마이그레이션이 넣는 고정 데이터다.

## 실행 방법

Flyway는 기본적으로 `db/migration`만 읽는다(`application.yml`의 `FLYWAY_LOCATIONS`). 로컬 실행 `./gradlew bootRun`은 이 시드 위치를 **자동으로 추가**한다(BE-28, `backend/build.gradle`). 기동 로그의 `[bootRun] Flyway 위치(...)` 줄에서 확인할 수 있다.

```bash
# backend 폴더에서
./gradlew bootRun
```

CI(`backend-ci.yml`)도 같은 값으로 시드를 켜서, 시드가 스키마 제약을 깨지 않는지 확인한다.

## 지켜야 할 것

- **운영 DB에는 넣지 않는다.** 운영에서는 `FLYWAY_LOCATIONS`를 지정하지 않아 `db/migration`만 읽는다.
- 실제 사건 데이터는 저장소가 공개이므로 여기에 넣지 않는다. 비공개 저장소 서브모듈(`backend/private-seed`)에 두고 Flyway `filesystem:` 위치로 주입한다. 로컬 `bootRun`은 서브모듈에 SQL이 있으면 자동으로 넣는다(BE-17 · BE-28, [루트 README](../../../../../../README.md)). 서브모듈의 파일을 이 폴더로 복사하지 않는다.
- 같은 제목의 사건이 이미 있으면 아무것도 하지 않아서, 여러 번 실행해도 중복으로 들어가지 않는다.
- 파일 내용을 바꾸면 Flyway가 다시 실행하지만, 이미 사건이 있으면 건너뛴다. **값을 바꿔 다시 넣으려면 로컬 DB를 비운다.**

```bash
# backend 폴더에서
docker compose down -v && docker compose up -d
```

## 관련 문서

- [ERD](../../../../../../docs/erd.md) 6장: 시드와 같은 값의 예시 데이터
- [API 명세서](../../../../../../docs/api-specification.md): 응답 예시
- [루트 README](../../../../../../README.md): 로컬 실행 방법
