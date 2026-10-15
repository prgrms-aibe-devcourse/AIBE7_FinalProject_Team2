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
| [user-flow.md](user-flow.md) | 유저 흐름도. 구현된 MVP 흐름(분기 · 재진입 · 예외)과 확장 목표 흐름(판사의 책상 · 판결 성향 테스트 · 마이페이지 · 관리자) |
| [wireframe.pdf](wireframe.pdf) | 와이어프레임 |
| [erd.md](erd.md) | ERD. 테이블 · 컬럼 · 제약, 예시 데이터 |
| [api-specification.md](api-specification.md) | API 명세서. 경로 · 요청 · 응답 · 에러 코드 · 상태별 호출 가능 API |
| [sequence-diagram.md](sequence-diagram.md) | 시퀀스 다이어그램. 사건 등록 · 체험 · 판결 · 비교 흐름 |
| [tech-stack.md](tech-stack.md) | 기술 스택 정리 |
| [ai-judgment-pipeline.md](ai-judgment-pipeline.md) | AI 판결 오프라인 파이프라인. 판결문 → 비식별화 → 재판부 초안 → 사전 학습 점검 → 생성 → 선택 → 적재 흐름, 모델 · 재시도 · 무료 등급 정책, 구현 상태 (`tools/ai-judgment`) |
| [rag-precedent-sources.md](rag-precedent-sources.md) | AI 판결 RAG 판례 수집처 조사. 수집처별 약관 · 자동 수집 가능 여부, 살인 판례 건수 · 중복 확인 결과 (BE-50) |
| [rag-handover.md](rag-handover.md) | AI 판결 RAG 판례 · 양형기준 저장소 인수인계(BE-50 · BE-51 · BE-52). 현재 데이터 상태 · 결정 사항 · 남은 일(LBox 삭제 · 인증키 · 판례 확충 · 점수 확정 · BE-54 양형기준 연동 · BE-53 · 55) · 배포 체크리스트 |
| [sentencing-guideline-rag.md](sentencing-guideline-rag.md) | AI 판결 RAG 양형기준 저장소(BE-52). RAG 방식 결정 · 양형위원회 출처 · 살인 6개 버전 시행일 · 조각 규칙 · `rag_guideline` 구조 · 버전 매칭 기준(공소제기일 → 발생일) (`tools/sentencing-guideline`) |

### 일정 · 협업

| 문서 | 내용 |
| --- | --- |
| [wbs.md](wbs.md) | WBS. 작업 분해, 일정, 업무 분장 |
| [conventions/GIT_CONVENTIONS.md](conventions/GIT_CONVENTIONS.md) | Git 컨벤션. 브랜치 · 커밋 · PR |
| [conventions/CODE_CONVENTIONS.md](conventions/CODE_CONVENTIONS.md) | 코드 컨벤션 |
| [personality-test-content.md](personality-test-content.md) | 판결 성향 테스트 콘텐츠(7.6.1). 가치관 4축 · 문항 16개(축 4 × 친구 · 가족 · 직장 · 이웃, 5칸 선택지 · −1 ~ 1점 · 동점 관대 글자) · 16유형 밈형 이름 · 내로 / 남불 캐릭터 배정 · 유형별 상세 설명 2단(일상 가치관 + 판결 연계) · 결과 화면 공통 문구 |
| [character-main-handover.md](character/character-main-handover.md) | 판결 성향 캐릭터 · S-01 메인 인수인계(7.6.8 · 7.7.1). 결정 사항 · 디자인 가이드 요약 · 16종 표(대표색 · 이미지 · 상태) · 다시 그릴 3종 · 생성 방법 · 화면 연결 · 검수 체크리스트. 미리보기 이미지는 [assets/characters/](assets/characters/) |
| [character-image-prompts.md](character/character-image-prompts.md) | 내로 · 남불 · 로 이미지 생성 프롬프트 모음(작업본). 시트 · 스탠딩 · 로 시안 · 데포르메 11종 블록, 문제와 대응 · 검수, **확정 이미지와 다른 점**(비율 · 로 그림체 · 색 · 설정 문구) |
| [character-image-checklist.md](character/character-image-checklist.md) | 캐릭터 이미지 제작 목록. 기준 · 우선 컷 · 판결 결과 · 성향 · 재연 · 시트 · 후순위별 수량 · 형식 · 상태, 추천 순서 |
| [character-image-gallery.html](character/character-image-gallery.html) | 캐릭터 원본 이미지 214장을 이미지 저장소에서 불러와 보는 갤러리 페이지. 캐릭터 · 종류 필터, 검색, 크기 조절, 원본 열기 · URL 복사. 브라우저로 직접 열어서 본다 |
| [character-profile-worldview.md](character/character-profile-worldview.md) | 캐릭터 프로필 · 세계관. 캐릭터를 쓰는 목적 · 법의 정령 내로 · 남불과 상위 정령 로 · 유저 흐름 × 세계관 · 캐릭터 프로필 3종 · 모습 바꾸기 · 역할 순환(중립) · 컬렉션 · IP 확장 방향 |
| [character-asset-architecture.md](character/character-asset-architecture.md) | 캐릭터 에셋 아키텍처. 정체성 → 외형 변형 → 연기 → 장착 → 장면 → 출력 6층 구조 · 캐릭터 시트 체계 · 바리에이션(데포르메 · 자세 · 표정 · 소품 · 역할 의상 · 스킨) · 상황 → 캐릭터 매핑 · 이미지 / 말투 프롬프트 블록 · 파일 · 데이터 구조 · 제작 순서 · 검수 |
| [personality-image-numbers.md](character/personality-image-numbers.md) | 성향 이미지 번호표. 이미지 파일 이름 `01` ~ `32`(내로 `01 ~ 16` · 남불 `17 ~ 32`)와 유형(코드 · 캐릭터 · 유형명) 연결, 화면에 보이는 이미지, 번호 규칙, 기존 동물 이미지 처리 |
| [personality-scoring.md](personality-scoring.md) | 판결 성향 산식(BE-65, 7.6.2). 테스트 채점(5칸 선택지 · 축 점수 · 동점 = 관대 · 관용 지수) · 체험 → 성향(감경 → 관대 글자) · 종합 성향(체험 평균) · 저장 형식 · 팀 확인 항목 |

### 점검 기록 · 사건 자료

| 문서 | 내용 |
| --- | --- |
| [document-consistency-report.md](document-consistency-report.md) | 문서 정합성 점검 보고서. 문서 사이 불일치와 결정 기록 |
| [cases/](cases/README.md) | 사건 파일 작성 가이드와 가상 사건 샘플. 실제 사건 파일은 저장소에 두지 않고, 판결문 원본 · 적재 SQL은 비공개 저장소(`backend/private-seed`)에 둔다 |

## 읽는 순서 (처음 보는 사람)

1. 기획서 → 요구사항 정의서 → MVP 정의서: 무엇을 만드는지, MVP 범위는 어디까지인지
2. 유저 흐름도 → 정보 구조 · 와이어프레임: 사용자가 어디로 가는지, 화면과 상태 규칙
3. ERD → API 명세서 → 시퀀스 다이어그램: 데이터와 API
4. 기술 스택 · WBS · 컨벤션: 개발 환경과 일정, 작업 규칙
