# **기술 스택 정리**

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

이런 제약은 JPA 자동 생성(`ddl-auto`)으로 만들 수 없으므로 Flyway를 사용해 Migration 파일로 관리한다. 운영 환경은 `ddl-auto: validate`로 두고 스키마는 Migration 파일로만 변경한다.

```
V1__create_legal_case.sql
V2__create_factor_and_penalty_rule.sql
V3__create_experience.sql
V4__create_judgment.sql
V5__insert_sample_case.sql
...
```

MVP에는 관리자 화면이 없으므로 대표 사건 데이터와 검수된 AI·재판부 판결은 SQL 스크립트로 넣는다.

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

익명 ID는 추측하기 어려운 무작위 UUID로 발급한다. 쿠키를 지우면 진행 기록을 이어갈 수 없다는 점은 서비스에서 안내한다.

원본 판결문, 사건번호 등 내부 정보는 사용자 API에서 절대 조회하지 않는다. AI 판결과 실제 판결은 진행 상태가 해당 단계에 도달했을 때만 응답에 포함한다.

회원가입 / 로그인은 MVP 이후 기능이며, 도입 시 기존 익명 체험을 회원 기록으로 연결한다.

---

## **4. API**

| **기술** | **사용 목적** |
| --- | --- |
| REST API | 프론트엔드와 백엔드 간 HTTP 기반 데이터 통신 |
| 공통 응답 형식 | 성공/실패 응답 구조 통일 |
| Global Exception Handler | 애플리케이션에서 발생하는 예외 응답 형식 통일 |
| Swagger / OpenAPI | API 명세 문서화 및 API 테스트 |
| CORS | 프론트엔드와 백엔드 간 요청 허용 범위 설정 |

API 경로는 `/api/v1`로 시작한다. 체험 진행과 관련된 주요 요청은 다음과 같다. (예시이며, 최종 경로는 API 명세서에서 확정한다.)

```
POST /api/v1/experiences                         체험 시작
POST /api/v1/experiences/{id}/pre-judgment       사전 판단 제출
POST /api/v1/experiences/{id}/reviews            사건 정보 섹션 확인
POST /api/v1/experiences/{id}/verdict            판결 확정
POST /api/v1/experiences/{id}/court-reveal       실제 판결 공개
POST /api/v1/experiences/{id}/complete           세 판결 비교로 이동
GET  /api/v1/experiences/{id}/comparison         세 판결 비교 조회
```

상태를 바꾸는 요청은 POST로 보내고, 조회(GET)는 상태를 바꾸지 않는다.

- 사전 판단 · 판결 제출은 **한 번만** 성공한다. 다시 보내면 거절하고 현재 상태를 돌려준다.
- 결과 공개 요청은 **다시 보내도 결과가 같다.** 새로고침이나 결과 화면 사이 이동으로 요청이 반복되어도 오류가 나지 않게 하기 위해서다.

API 응답은 공통 형식(`code`, `message`, `data`)을 사용한다. 오류 응답 예시는 다음과 같다.

```json
{
  "code": "VERDICT_OUT_OF_RANGE",
  "message": "선고할 수 있는 범위를 벗어났습니다."
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
Frontend/src
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
체험 상태 조회

↓
이 화면에 들어올 수 있는 상태인가?

↓ 예                         ↓ 아니오
화면 그리기                  현재 상태에 맞는 화면으로 이동
```

API 호출은 공통 함수 하나를 거친다. 공통 응답 형식(`code`, `message`, `data`)을 한 곳에서 처리하고, 익명 ID 쿠키가 요청에 함께 전달되도록 한다.

```
화면 모듈

↓
api 공통 함수 (기본 경로 /api/v1, 쿠키 포함)

↓
성공 → data 반환
실패 → code에 맞는 안내 표시 (예: VERDICT_OUT_OF_RANGE → 경고 박스)
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
| LLM API | AI 판결 생성 및 세 판결 비교 분석 |
| 퓨샷 프롬프트 | 유사 판례, 양형기준 자료를 프롬프트에 넣어 판결 생성 |
| 구조화 출력 (JSON) | AI 결과를 형벌, 형량, 판단 요소, 방향 형태로 받아 검증 |
| RAG | (이후) 유사 판례와 양형기준을 검색해 AI 판결 근거로 활용 |
| pgvector | (이후) 임베딩 저장 및 Vector 유사도 검색 |

AI 판결은 사용자가 요청할 때 만들지 않는다. 사건을 등록할 때 미리 생성하고 팀이 검수한 결과만 저장해, 모든 사용자에게 같은 AI 판결을 보여 준다.

```
사건 등록

↓
사건 정보 + 죄명·법조문·법정형(고정값) + 판단 요소 목록 + 유사 판례 + 양형기준
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

세 판결 비교 분석(확장 단계)은 사용자 판결마다 달라지므로 체험별로 실시간 생성한다. 사용자를 기다리게 하지 않도록 실제 판결을 읽는 동안 비동기로 만든다. MVP에서는 AI 없이 규칙 기반 문장으로 공통점·차이점을 보여 준다.

```
실제 판결 공개

↓
비교 분석 생성 시작 (비동기)

↓
LLM → 결과 검증 (판단 요소 ID, 금지 표현, 형식)

↓
통과하면 분석 표시 / 실패하면 규칙 기반 문장 유지
```

MVP에서는 퓨샷 방식으로 먼저 전체 흐름을 검증하고, RAG는 이후 단계에서 적용한다. RAG 전환 시에는 별도 벡터 DB 없이 PostgreSQL의 pgvector로 유사 판례와 양형기준을 검색한다. 이때 대상 사건 자체는 검색 결과에서 제외하고, 양형기준은 사건 발생 시점의 버전만 검색한다.

LLM 제공사는 AI 기능 구현 단계에서 결정한다. API 키는 DB 접속 정보와 같이 환경변수로 주입한다.

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
| AWS EC2 | 백엔드 애플리케이션 서버 운영 |
| Docker | EC2에서 백엔드 컨테이너 실행 |

```
PR Merge

↓
Docker 이미지 빌드

↓
AWS EC2에서 컨테이너 실행 (환경변수로 접속 정보 주입)

↓
PostgreSQL / Redis 연결
```

배포 자동화(CD)는 개발이 어느 정도 완료된 이후 추가한다.

---

## **9. Collaboration / AI Development Tools**

| **기술** | **사용 목적** |
| --- | --- |
| Jira | 작업 이슈 관리 (Backend `BE`, Frontend `FE`) |
| GitHub | Repository, Pull Request 기반 팀 협업 |
| Notion | 기획서, 요구사항, 기능 명세, ERD, 시퀀스 다이어그램 등 문서 관리 |
| Claude Code | 코드 작성, 분석, 수정 및 테스트 지원 |
| Atlassian MCP | Claude Code에서 Jira 이슈 생성·상태 전환 연동 |
| `CLAUDE.md` / `AGENTS.md` | AI 코딩 도구가 따를 프로젝트 개발 규칙 관리 |

작업은 Jira 이슈를 기준으로 진행하고, 브랜치·커밋·PR에 이슈 키를 포함해 연결한다.

```
Jira 이슈 생성

↓
feature/BE-15-experience-start-api (Branch 생성) → Jira: 진행 중

↓
feat : BE-15 체험 시작 API 구현 (Commit)

↓
[BE-15] 체험 시작 API 구현 (Pull Request) → Jira: 검토 중

↓
팀원 Code Review → CI 통과 → Merge

↓
Jira: 완료
```

AI 코딩 도구는 사람의 코드 리뷰를 대체하지 않고 개발 보조 역할로 사용한다.

구체적인 브랜치, 커밋, PR 규칙은 `docs/conventions/GIT_CONVENTIONS.md`, 코드 규칙은 `docs/conventions/CODE_CONVENTIONS.md`에서 관리한다.