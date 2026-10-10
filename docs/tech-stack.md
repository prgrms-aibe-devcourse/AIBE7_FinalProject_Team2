# **기술 스택 정리**

**작성 이력**

| 버전 | 날짜 | 내용 |
| --- | --- | --- |
| v1 | 2026-09-28 | 초안 — Backend(Spring Boot 4 · JPA · 도메인별 패키지), DB(PostgreSQL · Flyway · Redis 캐시 · pgvector), 익명 ID 쿠키 기반 사용자 식별, REST API 규칙, Vanilla JS + Vite 프론트 구조, AI(사전 생성 · 검수, 비교 분석 실시간 생성), Docker · GitHub Actions CI, EC2 배포, 협업 도구 |
| **v1.1** | **2026-09-29** | **문서 정합성 점검 결정 반영 (COMMON-4)** — API 응답 형식 · 에러 코드 · 경로를 API 명세서 기준으로 정정(4 · 5장), 화면 진입 시 상태 조회 없이 `INVALID_STATE`로 이동(5장), AI 판결은 RAG 우선 · 안 되면 퓨샷(6장), 쿠키 삭제 안내는 확장 기능으로 표시 · 문구 미정(3장), 문서 관리 위치 · 디렉터리명 정정(5 · 9장), Jira 공통 Space `COMMON` 추가(9장), 프론트엔드를 같은 도메인으로 배포(Nginx + `/api` 프록시, 8장), 작성 이력 표 신설 |
| v1.2 | 2026-09-30 | BE-2 진행 반영 — 2장 마이그레이션 파일 목록을 실제 구성(V1 ~ V5)으로 교체, `ddl-auto: validate`를 모든 환경 기준으로 정정 |
| v1.3 | 2026-09-30 | BE-16 반영 — 2장에 개발용 임시 시드(가상 살인 사건, `db/seed/R__seed_sample_case.sql`, 로컬 · CI 전용 · `FLYWAY_LOCATIONS`로 켬) 추가, 실제 사건 데이터는 저장소 밖에서 넣는다는 원칙, DB 운영 단계(개발 Docker PostgreSQL → 최종 AWS RDS, 비용 사유) 신설 |
| v1.4 | 2026-10-01 | BE-3 반영 — 2장에 시드를 켰던 DB에서 꺼도 기동되는 Flyway 설정(`ignore-migration-patterns`) 설명 추가 |
| v1.5 | 2026-10-01 | BE-3 리뷰 반영 — 2장 마이그레이션 파일 목록에 V6(원본 판결문 최종 확정 판결 유니크) 추가, `ignore-migration-patterns` 운영 적용 시 재검토 조건 명시 |
| v1.6 | 2026-10-02 | FE-2 리뷰 반영 — 8장에 Nginx SPA fallback(`try_files $uri $uri/ /index.html`) 요구사항 추가. History API 하위 경로(`/cases/1/review` 등) 새로고침 시 404 방지용 |
| v1.7 | 2026-10-02 | COMMON-15 반영 — 9장 `CLAUDE.md`(규칙 원본) · `AGENTS.md`(다른 에이전트용 안내) 역할 구분 |
| v1.8 | 2026-10-02 | BE-17 반영 — 2장에 실제 사건 데이터 주입 방식 추가(비공개 저장소 `backend/private-seed` 서브모듈 + Flyway `filesystem:` 위치), `FLYWAY_LOCATIONS` 표에 실제 사건 행 추가, `repeatable:missing` 재검토 결과 |
| v1.9 | 2026-10-06 | BE-28 반영 — 2장 `FLYWAY_LOCATIONS` 표: 로컬 `./gradlew bootRun`은 가상 시드 + (서브모듈에 SQL이 있으면) 실제 사건 위치를 `build.gradle`이 자동으로 정함. 환경변수를 주면 그 값 우선, 운영 · 테스트 · CI는 변경 없음 |
| v1.10 | 2026-10-08 | COMMON-19 반영 (AI 판결 파이프라인 BE-30 ~ BE-45) — 2장 마이그레이션 목록에 V7(사건 제목 · 판단 요소 번호 유니크) · V8(`ai_generation.generation_report`) 추가, 6장에 오프라인 생성 도구(`tools/ai-judgment`)의 LLM 공급자 · 호출 안정성(재시도 · 교차 호출 · 대체 체인) · 무료 등급 데이터 정책 · 퓨샷 · RAG 구현 상태 추가, "LLM 제공사는 구현 단계에서 결정" 문구를 오프라인 도구(결정됨)와 서비스 안 파이프라인(미결정)으로 구분 |
| v1.11 | 2026-10-08 | 11 · 12차 회의 반영 (COMMON-20) — 3장 카카오 로그인(OAuth2 · JWT) 검토 표기, 6장 세 판결 AI 비교 분석 제외(REQ-062 · 063) · 임베딩 API 행 추가(모델 미정) · 퓨샷 · RAG · 임베딩 적용 위치가 12차 회의 결정임을 명시 |
| **v1.12** | **2026-10-09** | **COMMON-21 반영** — 6장 퓨샷 · RAG · 임베딩 구현 현황에서 임베딩 모델 · 판례 저장소 범위 확정 표기, 선행 작업을 BE-50 ~ BE-52로 정정 |


## **1. Backend**

| **기술** | **사용 목적** |
| --- | --- |
| Java | 백엔드 애플리케이션 개발 언어 |
| Spring Boot 4 | Spring 기반 애플리케이션 구성 및 실행 |
| Gradle | 프로젝트 빌드 및 의존성 관리 |
| Spring Web MVC | REST API 구현 및 HTTP 요청/응답 처리 |
| Spring Data JPA | Repository 계층 구현 및 데이터 접근 |
| Hibernate | JPA 구현체로서 객체-관계 매핑과 영속성 관리 |
| Bean Validation | API 요청 데이터의 유효성 검증 |
| Lombok | Getter, 생성자 등 반복 코드 생성 |

기본적으로 다음과 같은 구조를 사용한다.

```
Controller
    ↓
Service
    ↓
Repository
    ↓
Entity
```

패키지는 도메인 단위로 나누고, 각 도메인 안에 계층을 둔다.

```
com.team2.project
 ├─ legalcase     (사건, 사건 정보 섹션, 판단 요소, 형벌 규칙)
 ├─ experience    (체험, 진행 상태)
 ├─ judgment      (사전 판단, 사용자·AI·재판부 판결)
 ├─ comparison    (세 판결 비교)
 ├─ common        (공통 응답, 예외 처리)
 └─ config
```

API 요청 및 응답은 DTO로 분리하고, Entity를 API 응답으로 직접 반환하지 않는 것을 기본 원칙으로 한다.

JPA 사용 시 Lazy Loading을 기본으로 하며, `open-in-view`는 끄고 필요한 데이터는 Service 계층에서 조회해 DTO로 변환한다. N+1 문제, Fetch Join, Transaction 범위를 고려한다.

체험 진행 상태, 사전 판단·판결 수정 불가, 선고 가능 범위 검증 등 서비스의 핵심 규칙은 화면이 아니라 **서버에서 강제**한다.

```
화면 요청
    ↓
체험 진행 상태 확인
    ↓
허용된 단계인가? ── 아니오 → 거절 (현재 상태 반환)
    ↓ 예
데이터 저장 + 상태 변경 (한 트랜잭션)
```

---

## **2. Database / Infrastructure**

| **기술** | **사용 목적** |
| --- | --- |
| PostgreSQL | 사건, 판단 요소, 체험 기록, 판결 등 서비스 주요 데이터 저장 |
| **Flyway** | DB 스키마 변경 이력 및 Migration 관리 |
| Redis | 참여자 수, 공개 판결, 사건 목록 등 자주 조회되는 데이터 캐시 |
| pgvector | 유사 판례·양형기준 임베딩 저장 및 유사도 검색. RAG 전환 시 활용 |

사건 정보의 피해 결과 카드, 부가 처분, AI 입력·출력 기록처럼 구조가 사건마다 다른 데이터는 `jsonb` 컬럼에 저장한다.

사전 판단·판결 1회 제한, 사건당 공개 판결 1개 같은 규칙은 서비스 로직과 함께 DB 제약으로도 한 번 더 막는다. PostgreSQL의 부분 유니크 인덱스와 CHECK 제약을 사용한다.

```sql
-- 사건당 공개된 AI / 재판부 판결은 1개만
CREATE UNIQUE INDEX uk_judgment_published
    ON judgment (case_id, subject_type)
    WHERE is_published = TRUE AND subject_type IN ('AI', 'COURT');
```

이런 제약은 JPA 자동 생성(`ddl-auto`)으로 만들 수 없으므로 Flyway를 사용해 Migration 파일로 관리한다. 모든 환경(로컬 · CI · 운영)에서 `ddl-auto: validate`로 두고, 스키마는 Migration 파일로만 변경한다.

```
backend/src/main/resources/db/migration
 ├─ V1__create_reference_tables.sql   -- 양형기준 버전, 사전 판단 형량 구간 (+ 고정값)
 ├─ V2__create_case_content.sql       -- 사건, 사건 정보 섹션, 형벌 규칙, 판단 요소, 원본 판결문
 ├─ V3__create_experience.sql         -- 익명 사용자, 체험
 ├─ V4__create_judgment.sql           -- 판단, 판단 요소 평가 (+ CHECK · 부분 유니크 인덱스)
 ├─ V5__create_extension_tables.sql   -- (확장) AI 판결 생성 기록, 세 판결 비교 분석
 ├─ V6__add_case_source_final_unique.sql   -- 사건마다 최종 확정 판결 1건 (부분 유니크)
 ├─ V7__add_legal_case_title_factor_display_order_unique.sql   -- 사건 제목 · (사건, 판단 요소 번호) 유일 (적재 SQL의 조회 키, BE-15)
 └─ V8__add_ai_generation_generation_report.sql   -- ai_generation.generation_report (자동 생성 정보, BE-31)

backend/src/main/resources/db/seed          -- 로컬 · CI 전용 (운영에는 넣지 않음)
 └─ R__seed_sample_case.sql           -- 개발용 임시 시드: 가상 살인 사건 1건 (ERD 6장 예시)
```

MVP에는 관리자 화면이 없으므로 대표 사건 데이터와 검수된 AI·재판부 판결은 SQL 스크립트로 넣는다.

- **개발용 시드는 운영 DB에 넣지 않는다.** 시드는 스키마(`db/migration`)와 따로 `db/seed`에 두고, Flyway가 읽을 위치를 환경변수 `FLYWAY_LOCATIONS`로 정한다.

  | 환경 | `FLYWAY_LOCATIONS` | 결과 |
  | --- | --- | --- |
  | 운영 (기본값) | 지정하지 않음 → `classpath:db/migration` | 스키마만 |
  | 로컬 (`./gradlew bootRun`) | 지정하지 않음 → `build.gradle`이 자동으로 정함: `classpath:db/migration,classpath:db/seed` + 서브모듈에 SQL이 있으면 `,filesystem:<backend/private-seed/seed 절대 경로>` (BE-28) | 스키마 + 가상 사건 (+ 실제 사건, 비공개 저장소 권한 필요) |
  | CI | `classpath:db/migration,classpath:db/seed` | 스키마 + 가상 사건 1건 |
  | 운영 (최종) | `classpath:db/migration,filesystem:<서버의 실제 데이터 경로>` | 스키마 + 실제 사건 (가상 사건 제외) |

  기본값을 스키마만으로 둬서, 운영에서 환경변수를 빠뜨려도 시드가 들어가지 않는다. 로컬 `bootRun`만 자동 설정을 쓰는 이유는 셸 환경변수가 다른 터미널 · IDE 실행에서 빠지기 쉽고, 빠져도 오류 없이 시드 없이 떠서 알아채기 어렵기 때문이다(BE-28). `FLYWAY_LOCATIONS`를 주면 자동 설정보다 우선하고, 기동 로그에 `[bootRun] Flyway 위치(...)` · `[bootRun] 실제 사건 시드: 포함 / 없음`이 찍힌다. CI(`backend-ci.yml`)는 시드까지 켜서 시드가 스키마 제약을 깨지 않는지 매번 확인한다.
- 시드는 **반복 마이그레이션(`R__`)**이다. 버전 번호가 없어 `db/migration`의 새 버전 파일과 번호가 겹치지 않고, 모든 버전 마이그레이션 다음에 실행된다. 이미 사건이 있으면 아무것도 하지 않는다. 시드 값을 바꿔 다시 넣으려면 로컬 DB를 비운다(`docker compose down -v` 후 다시 `up`).
- 한 번 시드를 켠 DB에서 시드 위치를 빼고 실행해도 기동된다. `spring.flyway.ignore-migration-patterns: "*:future,repeatable:missing"`로 적용된 반복 마이그레이션이 없어도 검증을 통과시킨다 (없으면 `Detected applied migration not resolved locally`로 기동 실패). 운영에도 적용되는 설정이라, 시드 외의 `R__` 파일을 추가할 때는 이 설정을 재검토한다. (BE-17 재검토: 실제 사건 데이터도 위치에 따라 있을 수도 없을 수도 있는 반복 마이그레이션이라 이 설정을 그대로 둔다)
- **실제 사건 데이터는 공개 저장소에 커밋하지 않는다.** 저장소가 공개라 실제 형량 · 재판부 판단 요소 · 판결문 발췌가 그대로 공개되기 때문이다. 실제 사건은 **비공개 저장소를 `backend/private-seed` 서브모듈로 연결**해 넣는다(BE-17).
  - 비공개 저장소 `seed/`의 반복 마이그레이션(`R__10` 콘텐츠, 이후 `R__20` 재판부 판결 · `R__30` AI 판결)을 Flyway `filesystem:` 위치로 읽는다. 여러 위치의 반복 마이그레이션은 이름 순서로 실행된다(`10 ...` → `seed sample case`, 검증함).
  - SQL은 환경마다 다른 숫자 ID 대신 사건 제목 · 표시 순서로 행을 찾고, 같은 제목의 사건이 있으면 건너뛴다.
  - 서브모듈은 `src/main/resources` 밖이라 jar에 들어가지 않고, `backend/.dockerignore`로 Docker 빌드 컨텍스트에서도 뺀다. CI는 서브모듈을 받지 않는다.
  - 운영(EC2)에서는 실제 데이터 폴더를 컨테이너에 읽기 전용으로 붙이고 위 표의 "운영 (최종)" 값으로 실행한다. `classpath:db/seed`는 넣지 않는다.
  - 판결문 원본 보관 원칙은 [`docs/cases/README.md`](cases/README.md)를 따른다.
- 이미 적용된 버전 마이그레이션(`V*`) 파일은 고치지 않는다(Flyway 체크섬). 스키마를 바꿀 때는 새 버전 파일을 추가한다.

### DB 운영 단계

비용 문제로 개발 기간에는 AWS를 쓰지 않고, 최종 단계에서만 AWS RDS로 옮긴다.

| 단계 | DB | 비고 |
| --- | --- | --- |
| 개발 (로컬 · CI) | Docker PostgreSQL (`pgvector/pgvector:pg17`, `backend/docker-compose.yml`) | AWS 키 없이 로컬에서 개발이 끝나도록 컨테이너로 띄운다. CI도 같은 이미지 태그를 쓴다 |
| 최종 (운영) | AWS RDS for PostgreSQL | 이미지 태그를 RDS에서 쓸 PostgreSQL 버전과 맞춘다. 접속 정보는 EC2 실행 시 환경변수로만 넘긴다 |

- 접속 정보는 `DB_URL` · `DB_USERNAME` · `DB_PASSWORD` 환경변수로 받고, 값이 없으면 로컬 Docker 기본값을 쓴다(`application.yml`). 단계를 옮겨도 코드는 바꾸지 않고 환경변수만 바꾼다.
- 운영 계정 · 비밀번호는 저장소 어디에도 적지 않는다.

Redis는 캐시 용도로만 사용한다. 체험 진행 상태나 판결 같은 원본 데이터는 모두 PostgreSQL에 저장하고, Redis가 없어도 서비스가 동작하도록 한다.

```
사건 목록 / 공개 판결 조회
    ↓
Redis에 있음? ── 예 → 바로 반환
    ↓ 아니오
PostgreSQL 조회 → Redis 저장 → 반환
```

캐시는 사건 공개나 AI 판결 교체 시점에 삭제한다.

---

## **3. 사용자 식별 / Security**

| **기술** | **사용 목적** |
| --- | --- |
| 익명 ID (UUID 쿠키) | 로그인 없이 사용자의 체험 기록과 진행 상태 식별 |
| HttpOnly Cookie | 브라우저 스크립트에서 익명 ID를 읽지 못하도록 보호 |
| Spring Security | (이후) 회원 기능 도입 시 인증 및 API 접근 제어 |
| 카카오 로그인 (OAuth2) · JWT | (이후 · 검토, v1.11) 11차 회의 제안. 로그인 시 익명 ID를 회원에 연결(`anonymous_user.member_id`). 관리자 화면(S-19) 인증 방식과 함께 정한다 |

MVP는 회원가입 없이 핵심 체험이 끝까지 동작해야 한다. 사용자를 익명 ID로 구분하고, 같은 브라우저에서는 이어서 체험할 수 있도록 한다.

```
체험 시작

↓
익명 ID 쿠키가 있는가?

↓ 없으면
새 익명 ID 발급 → 쿠키로 저장

↓
익명 ID + 사건으로 기존 체험 조회

↓
진행 중이면 해당 단계로 이동 / 완료했으면 결과 화면 / 없으면 새 체험 생성
```

익명 ID는 추측하기 어려운 무작위 UUID로 발급한다. 쿠키를 지우면 진행 기록을 이어갈 수 없다는 점의 안내는 **확장 단계 기능**(REQ-108)이며, 어느 화면에 어떤 문구로 둘지는 아직 정하지 않았다(요구사항 15장).

원본 판결문, 사건번호 등 내부 정보는 사용자 API에서 절대 조회하지 않는다. AI 판결과 실제 판결은 진행 상태가 해당 단계에 도달했을 때만 응답에 포함한다.

회원가입 / 로그인은 MVP 이후 기능이며, 도입 시 기존 익명 체험을 회원 기록으로 연결한다.

---

## **4. API**

| **기술** | **사용 목적** |
| --- | --- |
| REST API | 프론트엔드와 백엔드 간 HTTP 기반 데이터 통신 |
| 공통 에러 형식 | 실패 응답 구조 통일 (성공 응답은 결과 객체를 그대로 반환) |
| Global Exception Handler | 애플리케이션에서 발생하는 예외 응답 형식 통일 |
| Swagger / OpenAPI | API 명세 문서화 및 API 테스트 |
| CORS | 프론트엔드와 백엔드 간 요청 허용 범위 설정 |

API 경로는 `/api/v1`로 시작한다. 화면 경로가 `/cases/{caseId}/...`이므로, API도 **사건 ID + 익명 ID 쿠키**로 체험을 찾는다. 화면은 체험 ID를 들고 다니지 않는다. 체험 진행과 관련된 주요 요청은 다음과 같다(전체 목록과 요청 · 응답은 `api-specification.md` 기준).

```
POST /api/v1/cases/{caseId}/experience                      체험 시작
POST /api/v1/cases/{caseId}/experience/pre-judgment         사전 판단 제출
POST /api/v1/cases/{caseId}/experience/review-steps         사건 정보 섹션 확인
POST /api/v1/cases/{caseId}/experience/verdict              판결 확정
POST /api/v1/cases/{caseId}/experience/court-reveal         실제 판결 공개
POST /api/v1/cases/{caseId}/experience/comparison-reveal    세 판결 비교 공개
GET  /api/v1/cases/{caseId}/experience/comparison           세 판결 비교 조회
```

상태를 바꾸는 요청은 POST로 보내고, 조회(GET)는 상태를 바꾸지 않는다.

- 사전 판단 · 판결 제출은 **한 번만** 성공한다. 다시 보내면 거절하고 현재 상태를 돌려준다.
- 결과 공개 요청은 **다시 보내도 결과가 같다.** 새로고침이나 결과 화면 사이 이동으로 요청이 반복되어도 오류가 나지 않게 하기 위해서다.

성공 응답은 결과 객체를 **감싸지 않고 그대로** 돌려준다. 오류 응답만 공통 형식(`code`, `message`, `currentStatus`, `details`)으로 통일한다. 에러 코드 목록은 `api-specification.md` 1-5만 기준으로 삼는다. 오류 응답 예시는 다음과 같다.

```json
{
  "code": "OUT_OF_ALLOWED_RANGE",
  "message": "선고할 수 있는 범위를 벗어났습니다.",
  "currentStatus": "REVIEWED",
  "details": [{ "field": "prisonMonths", "reason": "OUT_OF_ALLOWED_RANGE" }]
}
```

로컬 개발 환경에서는 Vite 프록시로 `/api` 요청을 백엔드로 전달해 CORS 설정 없이 연동한다.

---

## **5. Frontend**

| **기술** | **사용 목적** |
| --- | --- |
| HTML5 | 화면 구조 작성. 시맨틱 태그로 의미 있는 마크업 구성 |
| CSS3 | 화면 스타일링. CSS 변수로 색상·간격 등 디자인 기준을 한 곳에서 관리 |
| JavaScript (ES Modules) | 화면 동작, API 연동, 화면 이동 처리 |
| Vite | 개발 서버 실행, 모듈 번들링 및 빌드 |
| Node.js | 프론트엔드 개발 및 빌드 환경 |

별도 UI 프레임워크 없이 HTML · CSS · JavaScript로 구현한다. 화면 수(S-01 ~ S-09)가 많지 않고 화면 대부분이 "조회 → 표시 → 제출" 흐름이라, 프레임워크 없이도 충분히 구성할 수 있다. 대신 코드가 흩어지지 않도록 **역할별로 모듈을 나누는 것**을 기본 원칙으로 한다.

```
frontend/src
 ├─ pages         화면 단위 모듈 (landing, case-list, overview, review, verdict, result ...)
 ├─ components    여러 화면에서 쓰는 UI 조각 (사건 카드, 진행 표시, 경고 박스, 판단 요소 목록)
 ├─ api           fetch 공통 처리 및 API 호출 함수
 ├─ router        화면 이동 및 진입 조건 확인
 ├─ utils         형량 표시 형식, 날짜 등 공통 함수
 └─ styles        공통 스타일과 디자인 변수
```

화면 이동은 History API 기반의 클라이언트 라우터로 처리한다. 주소가 바뀌면 해당 화면 모듈이 화면을 그리고, 새로고침해도 같은 화면으로 돌아온다.

```
주소 변경 (/cases/1/verdict)

↓
라우터가 화면 모듈 선택

↓
그 화면의 API 호출 (상태를 먼저 따로 조회하지 않음)

↓ 성공                       ↓ 409 INVALID_STATE
화면 그리기                  currentStatus에 맞는 화면으로 이동
```

화면 진입 시 체험 상태를 따로 조회하지 않고, 각 화면 API가 돌려주는 `INVALID_STATE`의 `currentStatus`로 보낼 화면을 정한다(API 명세서 1-6 · 6장 #1). `404 EXPERIENCE_NOT_FOUND`면 사건 목록(S-02), `404 CASE_NOT_FOUND`면 오류 화면(S-14)으로 보낸다.

API 호출은 공통 함수 하나를 거친다. 오류 응답(`code`, `message`, `currentStatus`, `details`)을 한 곳에서 처리하고, 익명 ID 쿠키가 요청에 함께 전달되도록 한다.

```
화면 모듈

↓
api 공통 함수 (기본 경로 /api/v1, 쿠키 포함)

↓
성공 → 응답 본문(결과 객체) 그대로 반환
실패 → INVALID_STATE면 currentStatus 화면으로 이동, 그 밖은 code에 맞는 안내 (예: OUT_OF_ALLOWED_RANGE → 경고 박스)
```

스타일은 CSS 변수로 디자인 기준을 정의해 화면마다 색상과 간격이 달라지지 않도록 한다. 와이어프레임 기준 데스크톱(1180px)과 모바일(390px) 두 가지 화면을 미디어 쿼리로 대응한다.

```css
:root {
    --color-primary: #e8391e;   /* 강조 색 (선택된 형벌, 경고) */
    --color-text: #1a1a1a;
    --space-md: 16px;
    --content-width: 1180px;
}
```

화면에서 막는 규칙(버튼 비활성화, 잠긴 섹션 표시)은 사용 편의를 위한 것이고, 실제 검사는 서버에서 한다. 잠긴 섹션이나 아직 공개되지 않은 판결은 서버가 응답에 넣지 않으므로, 화면에서 숨기는 방식에 의존하지 않는다.

세 판결 비교 분석(확장 단계)은 결과가 준비될 때까지 일정 간격으로 다시 조회하고, 준비되면 공통점·차이점 영역만 바꿔 그린다.

HTML·CSS·JavaScript 작성 규칙, 파일 이름 규칙, 포맷터 설정은 프론트엔드 컨벤션 문서에서 별도로 관리한다.

---

## **6. AI**

| **기술** | **사용 목적** |
| --- | --- |
| LLM API | AI 판결 생성 (세 판결 비교 분석은 v1.11에서 제외) |
| 임베딩 API | (확장, v1.11) 요약어 후보 · 유사 판례 · 양형기준 검색 · 회차 선택 유사도. 모델은 미정이며, 바꾸면 저장한 벡터를 모두 다시 만든다 |
| RAG | 유사 판례와 양형기준을 검색해 AI 판결 근거로 활용 (기본 방식) |
| pgvector | RAG용 임베딩 저장 및 Vector 유사도 검색 |
| 퓨샷 프롬프트 | RAG를 적용할 수 없을 때, 유사 판례 · 양형기준 자료를 프롬프트에 직접 넣어 판결 생성 (대체 방식) |
| 구조화 출력 (JSON) | AI 결과를 형벌, 형량, 판단 요소, 방향 형태로 받아 검증 |
| `tools/ai-judgment` (Python 3) | **오프라인** 생성 · 적재 도구. 판결문 → 비식별화 → 재판부 초안 → 사전 학습 점검 → 생성 → 선택 → 적재 SQL. 각 공급자의 HTTP API를 표준 라이브러리로 직접 불러 설치 없이 실행한다 (구현됨, BE-30 ~ BE-45) |

AI 판결은 사용자가 요청할 때 만들지 않는다. 사건을 등록할 때 미리 생성하고 팀이 검수한 결과만 저장해, 모든 사용자에게 같은 AI 판결을 보여 준다. **MVP에서는 오프라인에서 생성 · 검수한 결과를 DB에 적재**하고(REQ-046), 서비스 안의 생성 파이프라인은 확장 단계(REQ-040 ~ 045)에서 만든다.

```
사건 등록

↓
사건 정보 + 죄명·법조문·법정형(고정값) + 판단 요소 목록
+ RAG로 찾은 유사 판례 · 양형기준 (RAG를 적용할 수 없으면 퓨샷 자료)
(실제 판결은 제외)

↓
LLM

↓
AI 판결 (형벌, 형량, 판단 요소, 방향, 판결 이유)

↓
팀 검수

↓
DB 저장 → 서비스 공개
```

AI는 반드시 사건별 판단 요소 목록 안에서만 판단 요소를 선택하도록 하고, 목록에 없는 요소가 나오면 결과를 사용하지 않는다. 또한 AI가 사건 개요만 보고 실제 형량을 맞히는 유명 사건은 서비스에서 제외하거나 다시 가공한다.

> **(v1.11) 아래 세 판결 비교 분석은 11차 회의에서 확장 범위에서 제외했다(REQ-062 · 063).** S-09는 규칙 기반 문장과 요약 태그 규칙 문장을 계속 쓴다. 아래는 결정 기록이다.

세 판결 비교 분석(확장 단계)은 사용자 판결마다 달라지므로 체험별로 실시간 생성한다. 사용자를 기다리게 하지 않도록 실제 판결을 읽는 동안 비동기로 만든다. MVP에서는 AI 없이 규칙 기반 문장으로 공통점·차이점을 보여 주고, S-09 "내 판결" 카드의 한 줄 요약도 판단 요소의 요약 태그로 만든 규칙 문장을 쓴다. 확장 단계에서는 같은 비교 분석 호출이 사용자 입력을 바탕으로 "내 판결" 요약도 만들어 규칙 문장을 바꾼다.

```
실제 판결 공개

↓
비교 분석 생성 시작 (비동기)

↓
LLM → 결과 검증 (판단 요소 ID, 금지 표현, 형식)

↓
통과하면 분석 표시 / 실패하면 규칙 기반 문장 유지
```

AI 판결 생성 파이프라인은 **RAG를 기본으로** 한다. 별도 벡터 DB 없이 PostgreSQL의 pgvector로 유사 판례와 양형기준을 검색한다. 이때 대상 사건 자체는 검색 결과에서 제외하고, 양형기준은 사건 발생 시점의 버전만 검색한다. 판례 수가 적거나 검색 품질이 부족해 **RAG를 적용할 수 없으면**, 필요한 자료를 프롬프트에 직접 넣는 퓨샷 방식으로 생성한다(요구사항 FR-4-2).

**LLM 제공사**: 오프라인 생성 도구(`tools/ai-judgment`)는 Claude(Anthropic) · Gemini · OpenAI를 `공급자:모델ID`로 골라 쓴다(구현됨). 서비스 안의 생성 파이프라인(확장 단계, 비교 분석은 v1.11 제외)의 제공사는 AI 기능 구현 단계에서 결정한다. API 키는 DB 접속 정보와 같이 환경변수로만 주입하고 코드 · 출력 · 오류 메시지에 남기지 않는다.

### 오프라인 생성 도구의 호출 정책 (BE-30 ~ BE-45)

전체 흐름과 단계별 설명은 [AI 판결 오프라인 파이프라인](ai-judgment-pipeline.md), 명령 · 옵션은 [`tools/ai-judgment/README.md`](../tools/ai-judgment/README.md)를 본다. 이 장에는 기술 선택만 적는다.

| 항목 | 내용 |
| --- | --- |
| 재시도 (BE-36) | 과부하(503 · 529)는 지수 백오프 + 지터, 429는 `Retry-After` · 본문 `retryDelay`를 따르고, 일 한도 · 크레딧 소진은 다시 보내지 않는다. 최종 실패는 원인(과부하 / 분당 한도 / 일 한도)을 구분해 알린다 |
| 교차 호출 (BE-44) | 사전 학습 점검 · 생성은 모델을 번갈아 가며 회차 순서로 불러 한 모델의 분당 한도에 연속으로 걸리지 않게 한다. 모델은 독립 투표자라 서로 대신 생성하지 않는다 |
| 대체 체인 (BE-45) | 결과 하나를 내는 비식별화 · 재판부 초안은 모델 목록을 받아, 호출이 막혔을 때만(품질 실패는 제외) 다음 모델로 같은 입력을 처음부터 다시 보낸다 |
| 무료 모델 (BE-45) | `free_models.json`의 무료 등급 모델을 점검 · 생성에서 자동으로 쓰고(동시 `max`개, 일 한도로 막히면 대기 모델이 자리를 채움), 판결문 원문 단계는 유료 키만 쓴다. 팀이 공개 판결문에 한해 허용하면 `allowFreeTierForJudgment`로 마지막 백업에만 쓸 수 있다 |
| HTTPS (BE-34) | 인증서 검증은 항상 켜고, 기본 위치에 인증서가 없으면 시스템 CA 묶음을 찾아 쓴다 |

**무료 등급 데이터 정책**: 무료 등급은 입력을 제품 개선에 쓰고 사람이 검토할 수 있다(Gemini 약관). 정규식 마스킹만 거친 판결문 원문은 결제를 연결한 유료 키로만 보낸다(팀이 "이미 공개된 판결문에 한해" 허용한 `allowFreeTierForJudgment`의 마지막 백업만 예외, [ai-judgment-pipeline.md](ai-judgment-pipeline.md) 5-5). 정규식 마스킹은 법조인 실명 등을 지우지 못하므로 예외 설정은 공개 판결문에만 쓴다.

**퓨샷 · RAG · 임베딩은 아직 구현하지 않았다.** 위 RAG · pgvector · 퓨샷 설명은 서비스 설계(FR-4-2)이고, 오프라인 도구의 참고 자료 중 유사 판례는 비어 있다(BE-25). 적용 위치(비식별화 · 재판부 초안 · 생성 · 회차 선택)만 정해 두었고(12차 회의, v1.11 — 단계별 표는 [ai-judgment-pipeline.md](ai-judgment-pipeline.md) 8장 · [user-flow.md](user-flow.md) 3-6), 임베딩 모델 · 판례 저장소 범위는 COMMON-21에서 정했고(`gemini-embedding-001` 3072차원, 상세는 [ai-judgment-pipeline.md](ai-judgment-pipeline.md) 8-1), 판례 수집 · 저장소 구축 · 양형기준 데이터화(BE-50 ~ BE-52)가 선행 작업이다. 기능마다 설정으로 끌 수 있고, 끄면 지금과 같은 결과가 나와야 한다(비교 기준선).

---

## **7. Development / CI**

| **기술** | **사용 목적** |
| --- | --- |
| Docker | 백엔드 애플리케이션 이미지 생성 |
| Docker Compose | PostgreSQL, Redis 등 로컬 개발 환경을 컨테이너로 구성 |
| GitHub Actions | 테스트, 빌드 등의 CI 자동화 |
| JUnit 5 / Mockito | 단위 테스트 |
| Spring Boot Test | DB 연동 통합 테스트 |

팀원별 로컬 환경 차이를 최소화하기 위해 Docker Compose를 사용한다.

```bash
docker compose up -d
./gradlew bootRun
```

Docker Compose에서는 우선 다음 서비스를 관리한다.

```
PostgreSQL (pgvector 포함 이미지)
Redis
```

DB 접속 정보 등 비밀값은 코드에 적지 않고 환경변수로 주입한다.

코드 포맷은 새로운 도구를 도입하지 않고 IDE 기본 포맷터를 팀 공통으로 맞춘다.

PR 생성 시 GitHub Actions에서 다음 과정을 자동으로 수행한다.

```
Pull Request

↓
Backend : Test → Build → Docker 이미지 빌드 확인
Frontend : Install → Build

↓
성공 여부 확인
```

CI가 실패한 PR은 Merge하지 않는 것을 기본 원칙으로 한다.

상태 전이, 선고 가능 범위 검증, 판결 비교 분류 등 핵심 로직은 반드시 테스트한다. 부분 유니크 인덱스 같은 PostgreSQL 전용 제약은 H2 같은 인메모리 DB로 확인할 수 없으므로, 통합 테스트는 CI에서도 실제 PostgreSQL 컨테이너로 실행한다.

---

## **8. Deployment**

| **기술** | **사용 목적** |
| --- | --- |
| AWS EC2 | 백엔드 · 프론트엔드 서버 운영 |
| Docker | EC2에서 백엔드 컨테이너 실행 |
| Nginx | 프론트엔드 빌드 파일(정적 파일) 서빙, `/api` 요청을 백엔드 컨테이너로 프록시 |

```
PR Merge

↓
Docker 이미지 빌드

↓
AWS EC2에서 컨테이너 실행 (환경변수로 접속 정보 주입)

↓
PostgreSQL / Redis 연결
```

프론트엔드와 API는 **같은 도메인**으로 배포한다. 프론트엔드는 `npm run build` 결과물을 Nginx가 서빙하고, `/api`로 시작하는 요청은 Nginx가 백엔드 컨테이너로 넘긴다. 로컬의 Vite 프록시와 같은 구조라 익명 ID 쿠키를 `SameSite=Lax`로 쓸 수 있고 CORS 설정이 필요 없다(API 명세서 1-2 · 6장 #2).

```
브라우저 → https://{도메인}
    ├─ /        → Nginx → 프론트엔드 빌드 파일
    └─ /api/**  → Nginx → 백엔드 컨테이너 (Spring Boot)
```

프론트는 History API로 `/cases/1/review` 같은 하위 경로를 직접 연다(FE-2). 이런 주소를 새로고침하거나 바로 열면
그 경로의 실제 파일이 없으므로, Nginx가 `index.html`로 넘겨야 한다(SPA fallback). 로컬 `npm run dev`(Vite)는
이 처리를 자동으로 해 주지만 Nginx는 직접 설정해야 한다(FE-2 PR #55 리뷰 반영).

```nginx
location / {
    root /var/www/frontend;        # 프론트엔드 빌드 산출물(dist) 경로
    try_files $uri $uri/ /index.html;
}
```

배포 자동화(CD)는 개발이 어느 정도 완료된 이후 추가한다.

---

## **9. Collaboration / AI Development Tools**

| **기술** | **사용 목적** |
| --- | --- |
| Jira | 작업 이슈 관리 (Backend `BE`, Frontend `FE`, 공통 `COMMON`) |
| GitHub | Repository, Pull Request 기반 팀 협업 |
| GitHub `docs/` | 기획서, 요구사항, 기능 명세, ERD, 시퀀스 다이어그램, API 명세 등 설계 문서 관리 (기준 문서) |
| Notion | 회의록 · 논의 기록 · 조사 자료 보관 (저장소에 없는 참고 문서) |
| Claude Code | 코드 작성, 분석, 수정 및 테스트 지원 |
| Atlassian MCP | Claude Code에서 Jira 이슈 생성·상태 전환 연동 |
| `CLAUDE.md` | AI 코딩 도구가 따를 프로젝트 개발 규칙의 원본. 브랜치 · 커밋 · PR 규칙의 기준 문서 (Jira · Branch · Commit · PR 규칙) |
| `AGENTS.md` | Claude Code 외 에이전트용 안내. 규칙을 복제하지 않고 `CLAUDE.md`를 따르게 하며, 해당 에이전트에만 적용하는 예외만 담는다 |

작업은 Jira 이슈를 기준으로 진행하고, 브랜치·커밋·PR에 이슈 키를 포함해 연결한다.

```
Jira 이슈 생성

↓
feature/BE-15-experience-start-api (Branch 생성) → Jira: 진행 중

↓

개발 · 문서 작업 → 결과 보고 (Commit 여부를 확인받는다)

↓ "커밋해줘" (사용자가 명시적으로 요청할 때만)

feat : BE-15 체험 시작 API 구현 (Commit)

↓ "PR 올려줘" (사용자가 명시적으로 요청할 때만)

[BE-15] 체험 시작 API 구현 (Pull Request) → Jira: 검토 중

↓
팀원 Code Review → CI 통과 → Merge

↓
Jira: 완료
```

AI 코딩 도구는 사람의 코드 리뷰를 대체하지 않고 개발 보조 역할로 사용한다.

브랜치, 커밋, PR 규칙의 기준은 `CLAUDE.md`이며, `docs/conventions/GIT_CONVENTIONS.md`는 이를 사람이 읽기 쉽게 정리한 참고 문서다. 코드 규칙은 `docs/conventions/CODE_CONVENTIONS.md`에서 관리한다.