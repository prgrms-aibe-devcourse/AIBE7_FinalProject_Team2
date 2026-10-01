# 문서 (`docs/`)

내Law남불의 기획 · 설계 문서 모음이다. 구현은 이 문서들을 기준으로 한다.

> 각 문서는 상단에 **작성 이력 표**가 있다. 내용을 바꾸면 이력 표에 행을 추가한다.

## 문서 목록

### 기획 · 요구사항

| 문서 | 내용 |
| --- | --- |
| [final-planning.md](final-planning.md) | 기획서. 서비스 개요, 차별화 포지셔닝, 기능 방향 |
| [requirements-specification.md](requirements-specification.md) | 요구사항 정의서. `REQ-` 번호의 기준 |
| [functional-specification.md](functional-specification.md) | 기능 명세서. 요구사항별 기능 · 우선순위 |
| [mvp-definition.md](mvp-definition.md) | MVP 정의서. MVP 범위와 콘텐츠 기준 |

### 화면 · 설계

| 문서 | 내용 |
| --- | --- |
| [information-architecture.md](information-architecture.md) | 정보 구조. 화면 목록(`S-01` ~), 화면 흐름, 체험 상태 |
| [wireframe.pdf](wireframe.pdf) | 와이어프레임 |
| [erd.md](erd.md) | ERD. 테이블 · 컬럼 · 제약, 예시 데이터 |
| [api-specification.md](api-specification.md) | API 명세서. 경로 · 요청 · 응답 · 에러 코드 · 상태별 호출 가능 API |
| [sequence-diagram.md](sequence-diagram.md) | 시퀀스 다이어그램. 사건 등록 · 체험 · 판결 · 비교 흐름 |
| [tech-stack.md](tech-stack.md) | 기술 스택 정리 |

### 일정 · 협업

| 문서 | 내용 |
| --- | --- |
| [wbs.md](wbs.md) | WBS. 작업 분해, 일정, 업무 분장 |
| [conventions/GIT_CONVENTIONS.md](conventions/GIT_CONVENTIONS.md) | Git 컨벤션. 브랜치 · 커밋 · PR |
| [conventions/CODE_CONVENTIONS.md](conventions/CODE_CONVENTIONS.md) | 코드 컨벤션 |

### 점검 기록 · 사건 자료

| 문서 | 내용 |
| --- | --- |
| [document-consistency-report.md](document-consistency-report.md) | 문서 정합성 점검 보고서. 문서 사이 불일치와 결정 기록 |
| [cases/](cases/README.md) | 사건 후보 · 확정 사건의 조사 기록 폴더. 사건 파일은 커밋하지 않는다 |

## 읽는 순서 (처음 보는 사람)

1. 기획서 → 요구사항 정의서 → MVP 정의서: 무엇을 만드는지, MVP 범위는 어디까지인지
2. 정보 구조 · 와이어프레임: 화면과 흐름
3. ERD → API 명세서 → 시퀀스 다이어그램: 데이터와 API
4. 기술 스택 · WBS · 컨벤션: 개발 환경과 일정, 작업 규칙
