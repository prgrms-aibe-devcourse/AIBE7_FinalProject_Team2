# BE-3 의사결정 기록

> MVP 단계(WBS 1.2) · PR #36 · 담당 정민석

## 문제 / 배경
- BE-2가 만든 13개 테이블에 대해 JPA 엔티티와 목적별 조회 Repository(ERD 7장)를 추가해야 했다.
- `ddl-auto: validate`가 전제라, 엔티티 매핑이 스키마와 정확히 일치해야 하고 스키마를 엔티티 쪽에서 끌고 가면 안 됐다.
- 서비스 계층(BE-4, BE-6~9)이 나오기 전에 "엔티티가 직접 막을 규칙"과 "서비스가 막을 규칙"의 경계를 정해야 했다.

## 검토한 대안
- N+1 처리 범위: `countCompletedFirstAttempts`가 사건 1건용 쿼리라, 목록 API(BE-6)에서 여러 사건을 한 번에 세는 메서드를 이번에 같이 만들지, BE-6에서 필요할 때 추가할지 검토했다 (PR "리뷰 참고").
- 엔티티에 컬럼 제약(`@Column(nullable, length)`)을 둘지: 스키마를 Flyway가 관리하고 `ddl-auto: validate`라서 엔티티에 중복으로 선언할 필요가 있는지 검토했다.
- `CaseSection.data`의 타입: 섹션마다 원소가 객체 또는 문자열로 달라, 구체적인 DTO 목록 대신 `List<Object>`로 둘지 검토했다.
- Flyway 반복 마이그레이션(`R__`) 설정: 시드(R__)를 한 번 적용한 DB에서 `FLYWAY_LOCATIONS` 없이 재기동하면 "Detected applied migration not resolved locally"로 서버가 뜨지 않는 문제를 BE-16 머지 후 점검 중 발견했다. `ignore-migration-patterns`로 반복 마이그레이션 검증만 느슨하게 할지, 다른 방식으로 해결할지 검토했다.

## 선택한 방법과 이유
- N+1은 이번 PR 범위에서 고치지 않고 "리뷰 참고"에 남겨, BE-6에서 GROUP BY 기반 메서드를 추가하도록 넘겼다. 지금 당장 쓰이지 않는 조회 패턴을 먼저 만들면 실제 사용처 없이 추측성 구현이 된다.
- 엔티티에 `@Column(nullable, length)`를 두지 않기로 했다. 스키마는 Flyway가 단일 소스로 관리하고 `ddl-auto: validate`가 둘의 불일치를 기동 시점에 바로 드러내므로, 같은 제약을 엔티티에 중복 선언하는 것은 둘 중 하나만 바뀌었을 때 혼란을 만든다.
- `CaseSection.data`는 `List<Object>`로 두었다. ERD 형식상 섹션마다 원소가 객체 또는 문자열이라 고정된 하나의 DTO로 표현할 수 없었다.
- (리뷰 반영) `spring.flyway.ignore-migration-patterns`에 `*:future,repeatable:missing`을 추가했다. 적용된 반복 마이그레이션이 로컬에 없어도 검증을 통과시키되, 버전 파일(V__) 검증은 그대로 유지해 실제 스키마 변경 누락은 계속 잡는다. 운영은 시드를 적용하지 않으므로 영향이 없다.
- 엔티티 규칙은 `@Getter` + `@NoArgsConstructor(PROTECTED)`, `@Setter` 없음, 불리언은 `is` 접두사, ID는 `GenerationType.IDENTITY`(익명 사용자만 UUID 직접 발급), 시각은 `Instant`(+09:00 변환은 응답 DTO에서), jsonb는 `@JdbcTypeCode(SqlTypes.JSON)`, 연관관계는 `@ManyToOne(LAZY)` 단방향만 — 이는 팀 컨벤션으로 이번 PR에서 처음 정리했다.

## 고려한 제약 사항
- 트랜잭션 경계: 연관관계가 모두 LAZY이고 `open-in-view: false`라, 서비스에서 `@Transactional(readOnly = true)` 안에서 DTO 변환까지 끝내는 것을 전제로 설계했다. 이 경계를 벗어나는 지연 로딩은 BE-4 이후 서비스 계층에서 책임진다.
- 엔티티가 막지 않고 서비스가 막아야 하는 검증(형량 구간·판단 요소의 사건 소속, 사전 판단은 OVERVIEW 요소만, 선고 가능 범위, 집행유예 법정 조건, 공개 사건 여부)을 PR 본문에 명시적으로 정리해, 이후 서비스 구현 담당자(BE-4, BE-6~9)가 경계를 다시 찾지 않아도 되게 했다.
- DTO 분리: 엔티티는 API로 직접 반환하지 않는다. `CaseSource`는 `sourceOrg`만 응답에 쓴다.
- (리뷰 반영) 사형·무기 + 집행유예는 감경 여부와 관계없이 `INVALID_SUSPENSION`이 되도록, 집행유예 검사를 형벌별 값 검사보다 앞에 두었다 (PR #36 리뷰, peanutboi710).

## 관련 문서
- `docs/erd.md` (7장 jsonb 형식 공통 규칙)
- `docs/tech-stack.md`
