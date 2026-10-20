**작성 이력**

| 버전 | 날짜 | 내용 |
| --- | --- | --- |
| v0.1 | 2026-09-28 | 초안 — 공통 규칙(경로 · 익명 ID 쿠키 · 에러 형식 · 에러 코드), MVP API 14개와 확장 API 1개의 요청 · 응답 · 거절 조건, 상태별 호출 가능 API 표 |
| v0.2 | 2026-09-28 | 전체 문서 교차 검토 반영 — API 6 · 7 허용 상태 정정, 예시 요소 ID를 ERD 예시(1 ~ 7)와 통일, 섹션 ① 데이터 출처 명시, 벌금 선고 가능 하한 25,000원(형법 제45조 단서), 무죄 선택지 · `references` 출처 · `perspectives` 근거 요소 · `changeType`(`KEPT` 추가) · 형벌 무게 순서 보완, 사기 양형기준 유형 표기 정정(제1유형), 시퀀스 후보 대응표 |
| **v0.3** | **2026-09-29** | **문서 정합성 점검 결정 반영 (COMMON-4)** — 6장 #1 확정(화면 진입 시 상태 조회 없이 각 API의 `INVALID_STATE`로 이동, 1-6 · 5장), 무죄 선택지 MVP 제외(API 8 · 9 · 14, 6장 #5), 예시 사건을 단일 범행 사건(지인 투자금 편취)으로 교체(경합범 서비스 제외), 판결 카드 한 줄 요약 `summary` 추가(내 판결은 요약 태그 규칙 문장, 확장에서 AI 요약으로 교체), 사건 목록 `crimeCategoryLabel` · `thumbnailUrl`, (확장) 실제 판결 `deidentifiedItems`, 참고 자료 태그 출처 컬럼명 `reference_tags`, (확장) 쿠키 삭제 안내(REQ-108). (낮음 항목) 같은 도메인 배포 확정(1-2 · 6장 #2), 시퀀스 후보 대응표 삭제, 톤 규칙 참조를 요구사항 11장으로 정정 |
| **v0.4** | **2026-09-30** | **대표 사건(살인) 가공 결정 반영 (BE-13)** — 형벌 종류에 사형(`DEATH`) · 무기징역(`LIFE`) 추가(API 6 · 8 · 9, 판결 응답 공통 형식), 판결 제출에 `reducedTo`(감경 후 형벌) 추가, `diffFromMine` 비교 기준을 최종 선고 형벌로 명시, 부가 처분 `CONFISCATION`(몰수) 추가 (ERD v1.4), API 14 형벌 무게 순서에 `LIFE` < `DEATH` 추가, `reducedTo`는 형벌 종류가 바뀌는 감경만 기록한다고 명시, 최종 선고 형벌이 `DEATH` · `LIFE`일 때 형량 값이 있으면 `VALIDATION_ERROR`로 명시 |
| v0.5 | 2026-09-30 | 예시 사건을 가상 살인 사건으로 교체 (COMMON-11) — API 1 · 4 · 6 · 8 · 9 · 10 · 12 · 14 예시를 "빌린 돈 문제로 찾아온 지인을 살해한 사건"(가상)으로 교체, 사전 판단 구간 예시를 살인용 8개(벌금형 제외 · 무기 · 사형 추가)로, 형벌 선택지 예시를 사형 · 무기 · 징역 3종으로 교체 (ERD v1.5) |
| v0.6 | 2026-09-30 | BE-16 시드 반영 — API 4 · 5 · 14 예시의 사전 판단 구간 `rangeOptionId`를 DB 실제 값(살인 8 ~ 15, 예시 선택 11)으로 정정 (ERD v1.6) |
| v0.7 | 2026-09-30 | API 4 · 5 구현 반영 (BE-7) — API 4 형량 구간 개수를 범죄 유형별로 명시(살인 8개, 사기 · 상해 7개), 두 API의 404 · 400 거절 조건 추가, API 5 검사 순서 · 거절 시 저장 없음 · `factorIds` 규칙 순서 명시 |
| v0.8 | 2026-10-01 | BE-8 리뷰 반영 — API 6의 선고 가능 범위는 하한·상한이 모두 있는 규칙만 포함, 범위 표시 문구를 API 8 형식으로 통일, LAW_TERM은 law.terms로만 전달하도록 명시 |
| v0.9 | 2026-10-01 | 후속 정리 (COMMON-14) — 1-1에 숫자 입력 규칙 추가(정수 필드의 소수는 `VALIDATION_ERROR`, 문자열 숫자는 허용, BE-22), API 9에 열거값에 없는 `penaltyType`도 `INVALID_PENALTY_TYPE`으로 거절한다고 명시(1-5 열거값 규칙보다 우선, BE-9) |
| v0.10 | 2026-10-02 | API 10 ~ 13 구현 · 리뷰 반영 (BE-10) — `diffFromMine`은 최종 선고 형벌에 해당하는 값만 내려가고 **집행유예 여부가 다르면 다른 형벌로 본다**고 명시하고 유예 기간 차이 `suspensionMonthsDiff` 추가(API 10), 내 판결 한 줄 요약에서 같은 태그가 ↑ · ↓ 양쪽에 있으면 먼저 고른 방향에만 남기고 조사는 끝의 한글 · 숫자 기준으로 `을` · `를`을 고른다고 명시(판결 응답 공통 형식), 공개 요청(API 11 · 13)은 상태를 옮기기 전에 검수한 AI · 재판부 판결이 등록돼 있는지 먼저 확인한다고 명시, 공개 사건에 그 판결이 없으면 `500 INTERNAL_ERROR`로 둔다고 명시(API 10 · 12) |
| v0.11 | 2026-10-02 | 사건 정보 섹션 표시 방식 팀 결정 반영 (BE-17) — API 6 `content`에는 항목마다 줄바꿈(`\n`)이 들어갈 수 있고 화면은 그대로 보여 준다고 명시, 양측 주장 섹션 위 안내 문구는 응답에 없고 화면 고정 문구라고 명시 (ERD v1.9 · 정보 구조 v1.10) |
| v0.12 | 2026-10-06 | 기획 단계 리뷰 준비 회의 결정 반영 (COMMON-16) — 내 판결 한 줄 요약 문장 틀을 확정(6장 #7, 판결 응답 공통 형식) |
| v0.13 | 2026-10-06 | 판단 요소별 요약어 기준 확정 반영 (BE-26, 구현 BE-11) — `preToFinal.direction` 세부 규칙 확정(6장 #3), `ruleSentences` 문장 틀을 요소별 요약어 기준으로 확정(6장 #4), 내 판결 한 줄 요약 방향별 태그 수 제한 없음으로 정정(6장 #7), API 14 예시 응답의 `judgments.USER.summary` · `matrix`(요소 7 ALL_SAME 추가) · `ruleSentences`를 요약어 기준 · 서로 일치하도록 갱신, 판결 응답 공통 형식의 USER(MVP) 예시 문구도 요약어 기준으로 정정(PR #67 리뷰) |
| v0.14 | 2026-10-08 | 11 · 12차 회의 반영 (COMMON-20) — API 15(AI 비교 분석)를 보류로 표시(REQ-063 확장 범위 제외, 4 · 5장 · 6장 #6), 판결 성향 테스트 · 마이페이지 · 관리자 API는 설계 전이라 7장에 후보만 기록 |
| v0.15 | 2026-10-12 | 관리자 인증 API 추가 (**확장 단계**, BE-39 — 닫힌 #79를 최신 develop에 다시 적용) — 8장 관리자 API(CSRF 토큰 · 로그인 · 로그아웃 · 내 정보), 1-5에 `UNAUTHORIZED` · `INVALID_CREDENTIALS` · `FORBIDDEN` 추가. 7장 후보의 관리자 검수 중 인증 부분 확정. 사용자 API(1 ~ 15)는 변경 없음 |
| v0.16 | 2026-10-12 | 회원 토큰 공통 규칙 추가 (**확장 단계**, BE-57 · WBS 7.4.1) — 1-2에 `Authorization: Bearer` 규칙(없거나 `Bearer`가 아닌 스킴이면 익명, 잘못 · 만료면 401, 재발급 요청에는 붙이지 않음), 1-5에 `TOKEN_INVALID` · `TOKEN_EXPIRED` 추가. 토큰 발급 · 재발급 API는 카카오 로그인 · JWT(7.4.2 · 7.4.3)에서 추가. 지금 사용자 API의 동작은 변경 없음 |
| v0.17 | 2026-10-13 | 관리자 검수 API 계약 초안 (**확장 단계**, WBS 7.4.7 · BE-33 · BE-41 ~ BE-43, R16) — 8-2에 A-5 ~ A-13(검수 사건 목록 · 상세 · 원문, 판결 후보 상세 · 승인 · 반려, 사건 내용 수정, 가치관 축 확정, 사건 공개)과 검수 흐름 · 공개 조건, 8-3 팀 확인 항목, 1-5에 `JUDGMENT_NOT_FOUND` · `FACTOR_NOT_FOUND` · `DUPLICATE_TITLE` · `PUBLISH_NOT_READY` 추가. 경로 · 요청 · 응답 형태만 먼저 정하고 세부 필드는 구현(10/21 ~ 23)하며 맞춘다. 사용자 API는 변경 없음. #111 리뷰 반영(김해송): 상태 변경 시 사건 행 잠금, 승인 순서(해제 먼저 flush) · 유니크 위반 409, 공개한 사건에서도 반려 허용 · `pending` 필터, 검수 정보 최상위, `INVALID_STATE` 사유 코드, PATCH `null` 규칙, 최근 활동 순, 원문 열람 감사 로그 |
| v0.18 | 2026-10-13 | 관리자 로그인 보강 (**확장 단계**, BE-67) — 8-1 · A-2 · A-3: 로그인 성공 시 CSRF 토큰 교체 · 로그아웃 시 삭제(화면은 요청마다 쿠키를 읽는다), 로그인한 뒤 계정이 비활성 · 삭제되면 다음 요청부터 401(CSRF 토큰이 없어도 401). 응답 형식 변경 없음 |
| v0.19 | 2026-10-13 | 8-3 #4 마이그레이션 번호 표기 정리 — V14는 관리자 감사 로그(BE-68)가 써서 BE-42는 머지 직전의 다음 번호를 쓴다. API 변경 없음 |
| v0.20 | 2026-10-14 | **성향 API 계약 (확장 단계, 7.6.4)** — 7장 후보 5개를 9장 P-1 ~ P-5로 확정: 문항 조회 · 응답 제출 · 내 결과 · 공유용 유형(공개) · 마이페이지. 4글자 코드 · 축 글자는 응답에 넣지 않고 유형은 `shareId`로 구분한다(#119). 응답은 5칸 `value`(0 ~ 4)로 받는다. 에러 코드 `PERSONALITY_RESULT_NOT_FOUND` · `PERSONALITY_TYPE_NOT_FOUND` 추가(1-5). 테이블은 ERD 8장(V15, #124) |
| v0.21 | 2026-10-14 | #126 리뷰 반영 — P-5 `judgmentPersonality.type`이 값이 있을 때 유형 공통 형식(9-1)임을 명시, P-2 익명 ID는 요청 검증 통과 뒤에만 발급, 1-5 성향 404 두 행을 `FACTOR_NOT_FOUND` 아래로 이동 |
| v0.22 | 2026-10-14 | #128 리뷰 반영 — P-5 `judgmentPersonality.experienceCount`는 **성향 기여가 저장된 첫 회차 체험 수**(평균을 낸 체험과 같은 집합, 축 자료가 모두 비어도 센다)임을 명시. 형식 변경 없음 |
| v0.23 | 2026-10-14 | 검수 조회 API 구현 반영 (BE-41, V16) — A-8 `reviewStatus` · `reviewedBy` · `reviewedAt` · `reviewNote`의 출처 명시(검수 기록이 없으면 공개 = 승인, 아니면 대기), 8-3 #4 · #5 확정(검수 칸은 `judgment`, 검수자는 관리자 ID). 형식 변경 없음 |
| v0.24 | 2026-10-14 | 승인 · 반려 구현 반영 (BE-42) — A-9: 검수 기록 없이 공개돼 있던 판결은 내릴 때 `APPROVED`로 채운다, AI 후보는 `ai_generation` 검수 칸도 함께 맞춘다. A-10: `reason` 앞뒤 공백 제거, 교체로 내려간 승인 후보도 반려 가능. 형식 변경 없음 |
| v0.25 | 2026-10-14 | 사건 수정 · 축 확정 · 공개 구현 반영 (BE-43) — A-11 본문은 필드 맵으로 받아 "보내지 않음" · `null`을 나눈다, 제목 유니크 위반은 DB에서 나도 `DUPLICATE_TITLE`, A-11 ~ A-13 감사 로그 내용 명시, A-13 공개 조건은 A-6 `publishReadiness`와 같은 판정. #133 리뷰: A-11 문자열 앞뒤 공백 제거, `thumbnailUrl`은 http(s) · 서비스 경로만. 형식 변경 없음 |
| v0.26 | 2026-10-15 | 성향 이미지 번호 01 ~ 32 통일 반영 (COMMON-31) — 9-2 #4: `imageUrl`의 번호는 유형 번호가 아니라 성향 이미지 번호표의 **이미지 번호**(내로 `01 ~ 16` · 남불 `17 ~ 32`, 유형마다 화면에 보이는 기본 캐릭터 하나)다. 기본이 남불인 8개 유형(02 · 05 · 07 · 08 · 10 · 11 · 13 · 15)은 값이 `{유형 번호 + 16}.webp`로 바뀐다(예: ISTJ `02.webp` → `18.webp`). 아직 이미지 · 시드 `image_url`이 없어 공유된 주소는 없다. 형식 변경 없음 |
| v0.27 | 2026-10-15 | 사건 목록 진행 상태 배지 반영 (FE-19, REQ-010 · FR-1-6) — API 1 카드에 **`myStatus`**(`null` / `IN_PROGRESS` / `COMPLETED`)를 제안에서 정식 필드로 올림. 기준은 API 2 · 3과 같은 최신 회차, 쿠키는 읽기만 함(발급 안 함), 캐시 시 제외. 필드 추가만이라 기존 클라이언트와 호환. 리뷰 반영: **HTTP 캐시 규칙** 추가(사용자마다 다른 응답이라 `public` 금지, 켤 때는 `private` + `Vary: Cookie`, 지금은 Spring Security 기본 `no-store`를 테스트로 고정) |
| v0.28 | 2026-10-15 | 성향 이미지 시드 반영 (BE-73) — 9-1 `imageUrl`: 시드가 16유형 모두 `/images/characters/personality/{이미지 번호}.webp`를 넣는다(`null`은 이제 없음). 파일이 아직 없으면 화면이 이름표로 대신한다. 9-2 #5: OG 빌드용 shareId · 이미지 번호 등은 `frontend/og/personality-types.json`에 두고 시드와 같은지 백엔드 테스트가 확인한다. 형식 변경 없음 |
| v0.29 | 2026-10-15 | **API 16 공개 사건 상세 추가** (BE-74, S-20 사건 디테일 · 정보 구조 v1.12, #137 합의) — `GET /cases/{caseId}`: 체험 없이 API 1 카드 값 + `chargeName` · `overview` · `myStatus`. 법정형 · 권고 범위 · 판결 · 섹션 본문은 넣지 않는다(FR-2-8). 쿠키는 읽기만. 2장 목록 · 4장 · 5장 S-20 추가 |
| v0.30 | 2026-10-15 | Rate limit 1차 반영 (BE-63, WBS 7.4.6) — 1-5에 **429 `TOO_MANY_REQUESTS`** 추가. 운영 Nginx가 `/api`의 IP별 요청 속도를 제한하고, 넘으면 1-4와 같은 형식의 JSON과 `Retry-After: 1`을 돌려준다(백엔드는 이 응답을 만들지 않음). 로그인 · 재발급 엔드포인트 제한은 2차(7.4.2 이후) |
| v0.31 | 2026-10-16 | 관리자 사건 상세 A-6 `factors[].summaryTagCandidates` 추가 (BE-80, ERD v1.24) — 요약어 후보(제안만, 없으면 null) |
| v0.32 | 2026-10-18 | API 1 사건 목록 카드에 `chargeName`(죄명) 추가 (BE-79, 정합성 점검 D-01) — 사건 상단 바(S-04~S-09)가 목록에서 제목과 정확한 죄명을 함께 찾는다. 공통 사건 헤더 API는 사건 수가 늘면 재검토(`docs/decisions/BE-79.md`) |
| v0.33 | 2026-10-19 | BE-88 — 관리자 401/403 정책의 인증 영역·쿠키/세션 의미·CSRF 및 계정 상태 검사 우선순위와 공개 인증 경로 예외를 명시. 현행 응답 및 형식 변경 없음. 기존 포괄적인 401 문구의 정합성 확인 사항을 별도 기록 |
| v0.34 | 2026-10-20 | API 13에 판결 성향 기여 저장 추가 (BE-64, WBS 7.6.7) — 처음 `COMPLETED`로 바꿀 때 첫 회차 체험의 `experience_personality`를 같은 트랜잭션에서 저장, 이미 완료면 유지(성향 산식 3-4, PR #109 리뷰에서 "7.6.7 PR에서 구현과 함께 고친다"로 미룬 항목). 7장 성향 갱신 메모를 "제안"에서 확정으로 |
| v0.35 | 2026-10-20 | BE-77 — API 14 `preToCourt.direction` 추가(재판부 기준, SAME은 동일 형벌 종류·구간). API 2·3은 기존 5개 상태 필드를 유지하고 API 3은 사전 판단을 반환하지 않음을 명시. 1막부터 다시 보기 삭제 결정 및 완료 후 독자 의견에서 최초 사전 판단 공개 원칙 반영 (PR #198) |
| v0.36 | 2026-10-20 | 사건 사진 프롬프트 추천 · 업로드 API 신설 (BE-91, COMMON-41 재검토) — Cloudflare Workers AI 직접 생성은 그림 품질 때문에 그만두고, A-13-1(대표사진 프롬프트 추천) · A-13-2(발단 사진 프롬프트 추천) · A-13-3(대표사진 업로드) · A-13-4(발단 사진 업로드) · A-13-5(발단 사진 목록) 추가. 번호는 A-16 ~ A-41이 8-4(운영 메뉴 초안)에 이미 예약돼 있어 A-13-N으로 둠. 에러 코드 `PROMPT_GENERATION_FAILED` · `FILE_UPLOAD_FAILED` · `INVALID_IMAGE_FILE` 추가 |
| v0.37 | 2026-10-20 | 신문 지면 콘텐츠 생성 API 신설 (BE-92, COMMON-41) — A-13-6(생성) · A-13-7(조회) · A-13-8(수정) 추가. `photo`는 `legal_case.thumbnail_url`, `extraPhotos`는 A-13-5(발단 사진)를 그대로 재사용(새로 저장하지 않음, `docs/decisions/BE-92.md`). 에러 코드 `PAPER_NOT_FOUND` 추가(1-5) |

---

## 1. 공통 규칙

### 1-1. 기본

| 항목 | 규칙 |
| --- | --- |
| 기본 경로 | `/api/v1` |
| 형식 | JSON (`Content-Type: application/json`), 문자 UTF-8 |
| 필드 이름 | camelCase |
| 날짜 · 시각 | ISO 8601 (`2026-09-28T15:30:00+09:00`) |
| 형량 단위 | 징역 · 집행유예는 **개월**(int), 벌금은 **원**(long). 화면에서 "2년 6개월"로 바꿔 보여 준다 |
| 숫자 입력 | (v0.9) 정수 필드(형량 개월 · 금액 · `step` · ID 등)에 소수(`36.7`, `36.0`)가 오면 400 `VALIDATION_ERROR`로 거절한다. 소수점을 버려 다른 값으로 저장하지 않기 위해서다. `"36"`처럼 숫자로 읽을 수 있는 문자열은 숫자로 받고, `"삼십육"`처럼 읽을 수 없는 문자열은 400이다. 본문 형식 오류라 사건 · 체험 조회보다 먼저 거절한다 (BE-22) |
| 열거값 | ERD 값을 그대로 쓴다 (`PRISON`, `UP`, `COMPLETED` 등) |
| 인증 | MVP는 로그인 없음. **익명 ID 쿠키**로 사용자를 구분한다 (1-2) |

### 1-2. 익명 ID 쿠키

| 항목 | 값 (제안) |
| --- | --- |
| 이름 | `NLNB_AID` |
| 값 | `anonymous_user.id` (UUID) |
| 발급 시점 | 체험 시작(`POST /cases/{caseId}/experience`) · (확장) 성향 테스트 제출(P-2) 때 쿠키가 없거나 DB에 없는 값이면 새로 발급 |
| 속성 | `HttpOnly`, `Secure`, `SameSite=Lax`, `Path=/` |
| 유효 기간 | 1년 (요구사항 15장 "익명 ID 유지 방식 · 기간" 결정 시 확정) |
- **프론트와 API는 같은 도메인으로 배포한다**(6장 #2 확정, 기술 스택 8장). EC2의 Nginx가 프론트 빌드 파일을 서빙하고 `/api` 요청을 백엔드 컨테이너로 프록시하므로 `SameSite=Lax` 그대로 쓰고 CORS 설정은 필요 없다.
- 요청마다 `anonymous_user.last_seen_at`을 갱신한다.
- (확장, BE-57) **회원 토큰**: 로그인한 회원은 `Authorization: Bearer {액세스 토큰}`을 함께 보낸다. 헤더가 없거나 `Bearer`가 아닌 스킴(`Basic` 등)이면 지금처럼 익명 ID로만 동작한다. `Bearer` 헤더가 있으면 서버가 반드시 검증하고, 만료면 401 `TOKEN_EXPIRED`(화면은 재발급 시도), 그 밖에 잘못됐으면 401 `TOKEN_INVALID`다. 사용자 API 경로에서는 로그인이 필요 없는 API여도 같다. `Authorization` 헤더는 하나만 보내며, 여러 개이거나 토큰에 공백 · 쉼표 등이 섞이면 `TOKEN_INVALID`다. 관리자 API(8장)는 Bearer 토큰을 보지 않는다(세션으로만 인증). 재발급 요청에는 이 헤더를 붙이지 않는다. 발급 · 재발급 API와 익명 ID → 회원 연결은 7.4.2 · 7.4.3에서 정한다.
- (확장, REQ-108) 쿠키를 지우거나 브라우저를 바꾸면 진행 상태를 이어갈 수 없다는 안내를 둔다. 표시 화면과 문구는 미정이다(요구사항 15장). 서버 API 변경은 없다.

### 1-3. "내 체험" 경로

화면 경로가 `/cases/{caseId}/...`(IA 4장)이므로, API도 **사건 ID + 쿠키**로 체험을 찾는다. 체험 ID를 화면이 따로 들고 다닐 필요가 없다.

```
/api/v1/cases/{caseId}/experience/...
```

- 서버는 (쿠키의 익명 ID, `caseId`, 가장 최근 `attempt_no`)로 체험을 찾는다. MVP에서는 `attempt_no`가 항상 1이다.
- 체험이 없거나 쿠키가 없으면 `404 EXPERIENCE_NOT_FOUND`. 다른 사람의 체험은 이 경로로 접근할 수 없다.

### 1-4. 에러 응답

성공 응답은 결과 객체를 그대로 돌려준다(감싸는 객체 없음). 에러는 아래 형식으로 통일한다.

json

```json
{  "code": "INVALID_STATE",  "message": "지금 단계에서는 할 수 없는 요청입니다.",  "currentStatus": "PRE_JUDGED",  "details": null}
```

| 필드 | 설명 |
| --- | --- |
| `code` | 에러 코드 (1-5). 화면 분기는 이 값으로 한다 |
| `message` | 사람이 읽는 설명. 화면에 그대로 띄우지 않는다 |
| `currentStatus` | 체험 관련 에러일 때 현재 체험 상태. 화면은 이 값으로 **이동할 화면**을 정한다 (1-6) |
| `details` | 입력 검증 에러일 때 필드별 사유 배열 `[{ "field": "prisonMonths", "reason": "OUT_OF_ALLOWED_RANGE" }]` |

### 1-5. 에러 코드

| HTTP | code | 언제 | 화면 대응 |
| --- | --- | --- | --- |
| 400 | `VALIDATION_ERROR` | 필수값 누락, 형식 오류 (음수, 잘못된 열거값 등) | 개발 오류. 일반 오류 안내 |
| 404 | `CASE_NOT_FOUND` | 사건이 없거나 `PUBLISHED`가 아님 | S-14 |
| 404 | `EXPERIENCE_NOT_FOUND` | 이 사건의 내 체험이 없음 (쿠키 없음 포함) | S-02로 이동 |
| 409 | `INVALID_STATE` | 지금 상태에서 할 수 없는 요청 (이미 제출 · 확정, 순서가 맞지 않음, 아직 공개 전) | `currentStatus`의 화면으로 이동 |
| 409 | `STEP_OUT_OF_ORDER` | 섹션 확인 순서를 건너뜀 | 현재 섹션 다시 불러오기 |
| 422 | `INVALID_RANGE_OPTION` | 사전 판단 구간이 이 사건 범죄 유형의 선택지가 아님 | 개발 오류 |
| 422 | `INVALID_FACTOR` | 이 사건의 판단 요소가 아님, 사전 판단에 `OVERVIEW`가 아닌 요소, 중복 요소, 방향 누락 | 개발 오류 |
| 422 | `TOO_MANY_FACTORS` | 사전 판단 작용 요소가 2개를 넘음 (확장) | 개발 오류 |
| 422 | `INVALID_PENALTY_TYPE` | 이 사건에서 허용되지 않은 형벌, 허용되지 않은 감경 조합(`reducedTo`) | 개발 오류 |
| 422 | `OUT_OF_ALLOWED_RANGE` | 형량이 선고할 수 있는 범위 밖 | S-06c 경고 유지 (정상 화면이면 버튼이 비활성이라 오지 않음) |
| 422 | `INVALID_SUSPENSION` | 집행유예를 허용하지 않는 형벌 · 형량인데 값이 있음, 기간이 1 ~ 5년 밖 | 입력 안내 |
| 401 | `UNAUTHORIZED` | (확장, 관리자 API) 로그인하지 않음 · 세션 만료 | 관리자 로그인 화면 |
| 401 | `INVALID_CREDENTIALS` | (확장, 관리자 API) 로그인 실패. 계정 없음 · 비활성 · 비밀번호 불일치를 구분하지 않는다 | 로그인 실패 안내 |
| 401 | `TOKEN_EXPIRED` | (확장, 회원 토큰) 액세스 토큰 만료 | 토큰 재발급 후 다시 요청 |
| 401 | `TOKEN_INVALID` | (확장, 회원 토큰) 형식 · 서명 등이 잘못된 토큰, 아직 토큰을 확인할 수 없음 | 토큰을 지우고 다시 로그인 |
| 403 | `FORBIDDEN` | (확장, 관리자 API) 권한 없음, CSRF 토큰 없음 · 불일치 | CSRF 토큰을 다시 받아 재시도 · 일반 오류 안내 |
| 404 | `JUDGMENT_NOT_FOUND` | (확장, 관리자 API) 판결이 없거나 사용자 판결 | 목록 다시 불러오기 |
| 404 | `FACTOR_NOT_FOUND` | (확장, 관리자 API) 판단 요소가 없음 | 상세 다시 불러오기 |
| 404 | `PERSONALITY_RESULT_NOT_FOUND` | (확장, 성향 API) 성향 테스트 결과가 없음 (쿠키 없음 포함) | S-15 테스트 안내 |
| 404 | `PERSONALITY_TYPE_NOT_FOUND` | (확장, 성향 API) 공유 주소의 유형이 없음 | 공유 결과 없음 안내 · 테스트 권유 |
| 409 | `DUPLICATE_TITLE` | (확장, 관리자 API) 사건 제목이 다른 사건과 겹침 | 입력 안내 |
| 409 | `PUBLISH_NOT_READY` | (확장, 관리자 API) 공개 조건 미충족. `details`에 조건 코드 | 남은 조건 안내 (A-13) |
| 502 | `PROMPT_GENERATION_FAILED` | (확장, 관리자 API, BE-91 · BE-92) Gemini 호출 실패, 또는 설정(`GEMINI_API_KEY`) 없음 | 다시 시도 안내 (A-13-1 · A-13-2 · A-13-6) |
| 502 | `FILE_UPLOAD_FAILED` | (확장, 관리자 API, BE-91) S3 업로드 실패, 또는 설정(`S3_BUCKET_NAME`) 없음 | 다시 시도 안내 (A-13-3 · A-13-4) |
| 400 | `INVALID_IMAGE_FILE` | (확장, 관리자 API, BE-91) 사진 파일이 없음 · 비어 있음 · 허용하지 않는 형식 | 입력 안내 (A-13-3 · A-13-4) |
| 404 | `PAPER_NOT_FOUND` | (확장, 관리자 API, BE-92) 신문 지면을 아직 생성한 적 없음 | 먼저 생성하도록 안내 (A-13-7 · A-13-8) |
| 429 | `TOO_MANY_REQUESTS` | (확장, BE-63) 같은 IP에서 `/api` 요청이 너무 많음(평균 초당 20번 · 순간 40번 초과). **백엔드가 아니라 운영 Nginx가 돌려준다** — 형식은 1-4와 같고 `Retry-After: 1` 헤더가 붙는다. 로컬 개발(Vite)에는 Nginx가 없어 나오지 않는다 | 일반 오류 안내. 자동으로 다시 보내지 않는다 |
| 500 | `INTERNAL_ERROR` | 서버 오류 | 일반 오류 안내 |
- "이미 제출함"과 "순서가 맞지 않음"을 따로 나누지 않고 `INVALID_STATE` 하나로 둔다. 화면이 할 일은 둘 다 "`currentStatus`의 화면으로 이동"으로 같기 때문이다.
- 관리자 오류는 쿠키 유무만으로 결정하지 않는다. 먼저 요청을 거절한 보안 필터의 오류를 반환하므로 미인증 POST도 CSRF 검사 실패 시 403일 수 있다. 인증·인가 구분과 응답 우선순위는 **8-1의 BE-88 검증 기준**을 따른다.

### 1-6. 체험 상태 → 화면 (모든 화면 공통)

화면은 진입할 때 상태를 따로 조회하지 않고 **그 화면의 API를 바로 부른다.** 허용되지 않은 화면이면 API가 `409 INVALID_STATE`와 `currentStatus`를 돌려주므로, 화면은 아래 표의 "보낼 화면"으로 이동한다(IA 5장 · 9장, 6장 #1 확정). API 3은 상태만 알아야 하는 경우에 쓴다.

| status | 보낼 화면 | 허용 화면 |
| --- | --- | --- |
| `STARTED` | S-03 | S-03 |
| `PRE_JUDGED`, `REVIEWING` | S-04 | S-04 |
| `REVIEWED` | S-05 | S-04, S-05, S-06 |
| `VERDICT_CONFIRMED` | S-07 | S-07 |
| `AI_REVEALED` | S-08 | S-07, S-08 |
| `COMPLETED` | S-09 | S-07, S-08, S-09 |

---

## 2. API 목록

| # | 메서드 | 경로 | 설명 | 허용 상태 | 상태 변경 | 화면 | 단계 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| 1 | GET | `/cases` | 사건 목록 | — | — | S-02 | MVP |
| 2 | POST | `/cases/{caseId}/experience` | 체험 시작 (있으면 기존 체험) | — | 없으면 → `STARTED` | S-02 | MVP |
| 3 | GET | `/cases/{caseId}/experience` | 내 체험 상태 | 전체 | — | 전체 | MVP |
| 4 | GET | `/cases/{caseId}/experience/overview` | 사건 개요 · 사전 판단 선택지 | `STARTED` | — | S-03 | MVP |
| 5 | POST | `/cases/{caseId}/experience/pre-judgment` | 사전 판단 제출 | `STARTED` | → `PRE_JUDGED` | S-03 | MVP |
| 6 | GET | `/cases/{caseId}/experience/review` | 사건 정보 (열린 섹션까지) | `PRE_JUDGED` ~ `REVIEWED` | — | S-04, S-05 | MVP |
| 7 | POST | `/cases/{caseId}/experience/review-steps` | 섹션 확인 기록 | `PRE_JUDGED` ~ `REVIEWED` (`REVIEWED`는 상태 유지) | → `REVIEWING` / `REVIEWED` | S-04 | MVP |
| 8 | GET | `/cases/{caseId}/experience/verdict-form` | 판결 입력 정보 | `REVIEWED` | — | S-06 | MVP |
| 9 | POST | `/cases/{caseId}/experience/verdict` | 판결 제출 | `REVIEWED` | → `VERDICT_CONFIRMED` | S-06 | MVP |
| 10 | GET | `/cases/{caseId}/experience/judgments/ai` | AI 판결 | `VERDICT_CONFIRMED` 이상 | — | S-07 | MVP |
| 11 | POST | `/cases/{caseId}/experience/court-reveal` | 실제 판결 공개 | `VERDICT_CONFIRMED` 이상 | 처음이면 → `AI_REVEALED` | S-07 | MVP |
| 12 | GET | `/cases/{caseId}/experience/judgments/court` | 실제 판결 | `AI_REVEALED` 이상 | — | S-08 | MVP |
| 13 | POST | `/cases/{caseId}/experience/comparison-reveal` | 비교 공개 | `AI_REVEALED` 이상 | 처음이면 → `COMPLETED` | S-08 | MVP |
| 14 | GET | `/cases/{caseId}/experience/comparison` | 세 판결 비교 | `COMPLETED` | — | S-09 | MVP |
| 15 | GET | `/cases/{caseId}/experience/comparison/analysis` | AI 비교 분석 | `COMPLETED` | — | S-09 | 확장 |
| 16 | GET | `/cases/{caseId}` | 공개 사건 상세 (체험 없이) | — | — | S-20 | 확장 (v0.29) |

**공개(11, 13)와 조회(12, 14)를 나눈 이유**: 상태를 바꾸는 요청은 POST로 두고, 조회는 몇 번을 불러도 상태가 바뀌지 않게 한다. 결과 화면을 새로고침하면 조회만 다시 부르면 된다. 공개 요청은 이미 공개된 상태에서 다시 와도 거절하지 않고 성공으로 돌려준다(시퀀스 7장).

---

## 3. API 상세

### API 1. 사건 목록 — `GET /cases`

**요청**

| 위치 | 이름 | 필수 | 설명 |
| --- | --- | --- | --- |
| query | `crimeType` |  | `MURDER` / `FRAUD` / `INJURY`. 없으면 전체 |

**응답 200**

json

```json
{  "summary": { "total": 9, "byCrimeType": { "MURDER": 3, "FRAUD": 3, "INJURY": 3 } },  "cases": [    {      "caseId": 1,      "title": "빌린 돈 문제로 찾아온 지인을 살해한 사건",      "crimeType": "MURDER",      "crimeCategoryLabel": "생명범죄",      "chargeName": "살인",      "shortIntro": "빌린 돈 문제로 찾아온 지인과 다투다 흉기로 살해한 사건입니다.",      "keywords": ["돈 문제", "집으로 찾아옴"],      "difficulty": "HIGH",      "estimatedMinutes": 15,      "participantCount": 1284,      "thumbnailUrl": null,      "myStatus": "IN_PROGRESS"    }  ]}
```

- `PUBLISHED` 사건만. `summary`는 필터와 관계없이 전체 기준(칩 옆 건수 표시용).
- `crimeCategoryLabel`: `crime_type`별 표시용 분류명(예: `FRAUD` → "재산범죄"). DB 컬럼이 아니라 서버 코드 상수에서 만든다(ERD 3-1).
- `chargeName`: 죄명(`legal_case.charge_name`, 예: "살인"). 사건 상단 바(S-04~S-09)가 `caseId`로 이 목록에서 제목과 함께 찾아 쓴다(BE-79, D-01). 사건 개요(API 4) · API 16의 `chargeName`과 같은 값이다.
- `thumbnailUrl`: `legal_case.thumbnail_url`. 없으면 `null`이고, 화면은 범죄 유형별 기본 이미지를 쓴다.
- `participantCount`: `attempt_no = 1`이고 `COMPLETED`인 체험 수.
- 정렬: `published_at` 최신순 고정 (정렬 선택은 이후 단계, REQ-009).
- (v0.27, FE-19 · REQ-010) `myStatus`: 이 사건에 대한 **내 최신 회차** 체험 상태를 목록 배지용 두 값으로 묶은 것.

  | 값 | 언제 | 화면 (S-02) |
  | --- | --- | --- |
  | `null` | 익명 ID 쿠키가 없음, 쿠키 값이 UUID 형식이 아님(조작 · 손상), DB에 없는 익명 ID, 이 사건 체험 전 | 배지 없음 · "체험 시작" |
  | `IN_PROGRESS` | `STARTED` ~ `AI_REVEALED` | "진행 중" · "이어서 하기" |
  | `COMPLETED` | `COMPLETED` | "완료" · "결과 다시 보기" |

  - 회차 기준은 API 2 · 3과 같은 "사건마다 가장 최근 회차"다. 그래서 배지와, 버튼을 눌러 API 2가 돌려주는 상태로 이동하는 화면(1-6)이 어긋나지 않는다. 세부 상태(7단계)는 목록에 내보내지 않는다.
  - API 1은 쿠키를 **읽기만** 하고 새로 발급하지 않는다(발급은 API 2 · P-2, 1-2).
  - 사용자마다 다른 값이라, 사건 목록을 캐시할 때(6.5.3)는 이 필드를 빼고 공통 부분만 캐시한 뒤 요청마다 붙인다(기술 스택 2장 "Redis 운영 구성").
  - **HTTP 캐시도 같다.** 지금은 Spring Security 기본 헤더(`Cache-Control: no-cache, no-store, max-age=0, must-revalidate`)라 브라우저 · Nginx · CDN 어디에도 저장되지 않는다(`CaseListApiTest`가 고정). API 1에 HTTP 캐시를 켜려면 `public`이 아니라 **`Cache-Control: private` + `Vary: Cookie`**로 두고, Nginx · CDN에서 이 경로를 공유 캐시하지 않는다.

---

### API 2. 체험 시작 — `POST /cases/{caseId}/experience`

**요청**: 본문 없음. 쿠키가 있으면 함께 보낸다.

**응답** — 새로 만들면 `201`, 이미 있으면 `200`. 본문은 API 3과 같다.

json

```json
{  "caseId": 1,  "attemptNo": 1,  "status": "STARTED",  "lastReviewedStep": 0,  "startedAt": "2026-09-28T15:30:00+09:00"}
```

- 쿠키가 없거나 DB에 없는 익명 ID면 `anonymous_user`를 만들고 `Set-Cookie`로 내려준다.
- 이미 체험이 있으면 **새로 만들지 않고** 기존 체험을 돌려준다. 화면은 1-6 표대로 이동한다(완료한 사건이면 S-09, REQ-011 · 012).
- 동시에 두 번 와도 유니크 제약(`anonymous_user_id`, `case_id`, `attempt_no`)으로 하나만 생긴다. 제약 위반이 나면 서버가 기존 체험을 다시 읽어 `200`으로 돌려준다.

| 거절 | 조건 |
| --- | --- |
| 404 `CASE_NOT_FOUND` | 사건 없음 · 비공개 |

---

### API 3. 내 체험 상태 — `GET /cases/{caseId}/experience`

**응답 200**: API 2와 같은 형식. 기존 5개 상태 필드만 반환한다.

- 사전 판단·AI 판결·재판부 판결·비교 결과는 포함하지 않는다. 사전 판단은 체험 완료 후 API 14의 독자 의견에서 처음 다시 확인한다.
- 제출 후 처음 선택한 형량을 진행 중에 다시 조회할 수 없다. 1막부터 다시 보기 기능은 삭제하기로 확정했으며, 프론트 반영은 별도 후속 작업이다(2026-10-20 팀 논의, BE-77).

| 거절 | 조건 |
| --- | --- |
| 404 `CASE_NOT_FOUND` |  |
| 404 `EXPERIENCE_NOT_FOUND` | 체험 없음 (쿠키 없음 포함) → 화면은 S-02로 |

---

### API 4. 사건 개요 · 사전 판단 선택지 — `GET /cases/{caseId}/experience/overview`

**응답 200**

json

```json
{  "case": {    "caseId": 1,    "title": "빌린 돈 문제로 찾아온 지인을 살해한 사건",    "crimeType": "MURDER",    "crimeCategoryLabel": "생명범죄",    "chargeName": "살인",    "overview": "피고인이 빌린 돈을 갚지 못해 오래 다투던 지인이 집으로 찾아오자, 말다툼 끝에 집에 있던 흉기로 피해자를 살해하고 구호 조치 없이 집을 나간 사건이다."  },  "rangeOptions": [    { "rangeOptionId": 8, "label": "징역형 집행유예" },    { "rangeOptionId": 9, "label": "실형 3년 미만" },    { "rangeOptionId": 10, "label": "실형 3년 이상 ~ 5년 미만" },    { "rangeOptionId": 11, "label": "실형 5년 이상 ~ 10년 미만" },    { "rangeOptionId": 12, "label": "실형 10년 이상 ~ 20년 미만" },    { "rangeOptionId": 13, "label": "실형 20년 이상" },    { "rangeOptionId": 14, "label": "무기징역" },    { "rangeOptionId": 15, "label": "사형" }  ],  "preFactors": [    { "factorId": 1, "label": "돈 문제로 오래 다툼이 있었다" },    { "factorId": 2, "label": "다투던 중 흉기를 집어 들었다" },    { "factorId": 3, "label": "범행 뒤 현장을 떠났다" }  ]}
```

- `rangeOptions`: 이 사건 `crime_type`의 `sentence_range_option`을 `display_order` 순으로. 사기 · 상해는 공통 7개, 살인은 벌금형을 빼고 무기징역 · 사형을 더한 8개다.
- `preFactors`: `reveal_stage = OVERVIEW`인 요소의 `pre_label`. **확장(REQ-093)** — MVP 화면은 쓰지 않아도 된다.
- 법정형 · 선고 가능 범위 · 권고 범위 · 실제 판결은 **넣지 않는다**(FR-2-8).

| 거절 | 조건 |
| --- | --- |
| 404 `CASE_NOT_FOUND` | 사건 없음 · 비공개 |
| 404 `EXPERIENCE_NOT_FOUND` | 이 사건의 내 체험이 없음 (쿠키 없음 포함) → 화면은 S-02로 |
| 409 `INVALID_STATE` | `STARTED`가 아님 (이미 제출) → `currentStatus`의 화면 (1-6) |

---

### API 5. 사전 판단 제출 — `POST /cases/{caseId}/experience/pre-judgment`

**요청**

json

```json
{ "rangeOptionId": 11, "factorIds": [1, 2] }
```

| 필드 | 필수 | 규칙 |
| --- | --- | --- |
| `rangeOptionId` | ✓ | 이 사건 범죄 유형의 선택지 |
| `factorIds` |  | 0 ~ 2개, `OVERVIEW` 요소, 중복 없음. **확장** — MVP는 빈 배열이나 생략 |

**응답 200**

json

```json
{ "status": "PRE_JUDGED", "lastReviewedStep": 1 }
```

- 한 트랜잭션: `judgment`(`USER`, `PRE`) + `judgment_factor`(방향 NULL) 저장, 상태 `STARTED` → `PRE_JUDGED`, `last_reviewed_step = 1`(섹션 ① 개요는 S-03에서 본 것으로 처리).
- 상태 변경은 조건부 갱신("`status = STARTED`일 때만")으로 한다. 동시에 두 번 오면 하나만 성공하고 나머지는 `INVALID_STATE`(`currentStatus` 포함). 패자가 판단 저장에서 유니크 제약에 걸려도 같은 응답으로 바꿔 돌려준다.
- 응답에 사전 판단 내용을 되돌려주지 않는다(S-09 전까지 다시 보여 주지 않음).
- 검사 순서: 사건 · 체험(404) → 상태(409) → 형량 구간(422) → 판단 요소(422: 개수 → 유효성). 첫 번째로 걸린 사유 하나로 거절하며, 거절하면 상태 · 판단 · 판단 요소 어느 것도 저장하지 않는다. 요청 형식 오류(400)는 그 앞에서 걸러진다.
- `factorIds`(확장)의 유효성은 "이 사건의 `OVERVIEW` 요소이고 중복이 없다"이다. 하나라도 어긋나면 전체를 `INVALID_FACTOR`로 거절한다.

| 거절 | 조건 |
| --- | --- |
| 400 `VALIDATION_ERROR` | `rangeOptionId` 누락 · 형식 오류, `factorIds` 형식 오류 |
| 404 `CASE_NOT_FOUND` | 사건 없음 · 비공개 |
| 404 `EXPERIENCE_NOT_FOUND` | 이 사건의 내 체험이 없음 (쿠키 없음 포함) → 화면은 S-02로 |
| 409 `INVALID_STATE` | `STARTED`가 아님 |
| 422 `INVALID_RANGE_OPTION` | 없는 구간이거나 이 사건 범죄 유형의 구간이 아님 |
| 422 `INVALID_FACTOR` | 이 사건의 요소가 아님, `OVERVIEW`가 아닌 요소, 중복, 없는 요소 |
| 422 `TOO_MANY_FACTORS` | 2개 초과 (확장) |

---

### API 6. 사건 정보 — `GET /cases/{caseId}/experience/review`

S-04와 S-05가 함께 쓴다.

**섹션 번호**: ① 개요 = 1, ② 상세 사실관계 = 2, ③ 양측 주장 = 3, ④ 법률 · 양형기준 = 4. `lastReviewedStep`은 확인을 마친 마지막 번호다.

**응답 200** (예: 섹션 ②까지 확인, ③이 열린 상태)

json

```json
{  "status": "REVIEWING",  "lastReviewedStep": 2,  "openStep": 3,  "sections": [    { "step": 1, "stage": "OVERVIEW", "confirmed": true,      "items": [ { "sectionType": "OVERVIEW", "title": "사건 개요", "content": "피고인이 빌린 돈을 갚지 못해 오래 다투던 지인이 집으로 찾아오자 …" } ] },    { "step": 2, "stage": "DETAIL", "confirmed": true,      "items": [        { "sectionType": "FACTS", "title": "주요 사실관계", "content": "사건 3개월 전부터 변제 문제로 여러 차례 다툼이 있었고 …" },        { "sectionType": "DAMAGE", "title": "피해 결과", "data": [ { "label": "피해자 수", "value": "1명" }, { "label": "피해 결과", "value": "사망" }, { "label": "피해자와의 관계", "value": "지인 (돈을 빌린 사이)" }, { "label": "범행 도구", "value": "집에 있던 흉기" } ] },        { "sectionType": "DEFENDANT", "title": "피고인 관련 사실", "content": "30대이고 형사처벌을 받은 전력이 없다." },        { "sectionType": "SETTLEMENT", "title": "합의 · 피해 회복", "content": "피해 회복을 위해 5,000만 원을 공탁했으나 합의에 이르지 못했고, 유족은 엄벌을 원한다." }      ] },    { "step": 3, "stage": "ARGUMENT", "confirmed": false,      "items": [        { "sectionType": "PROSECUTOR", "title": "검사", "content": "…" },        { "sectionType": "DEFENSE", "title": "피고인 · 변호인", "content": "…" }      ] }  ],  "lockedSteps": [4],  "law": null,  "summary": null}
```

- step 1의 항목은 `case_section`이 아니라 `legal_case.overview`로 만든다(`sectionType: "OVERVIEW"`는 응답용 값). step 2 ~ 4는 `case_section`의 `stage`별 항목이다.
- **열린 섹션(`lastReviewedStep + 1`)까지만** 본문을 넣는다. 잠긴 섹션은 번호만 `lockedSteps`에 넣는다(개발자 도구로 미리 보기 방지).
- `law`: 섹션 ④가 열리면 채운다.
- 용어 설명(`case_section` `LAW_TERM`)은 섹션 ④ `items`에 넣지 않고 `law.terms`로만 보낸다.
- `content`에는 항목마다 줄바꿈(`\n`)이 들어갈 수 있다. 화면은 줄바꿈을 그대로 보여 준다(v0.11, ERD v1.9).
- 양측 주장(`PROSECUTOR` · `DEFENSE`) 섹션 위 안내 문구는 응답에 넣지 않는다. 화면 고정 문구다(정보 구조 S-04).

json

```json
"law": {  "appliedLaw": "형법 제250조 제1항 살인",  "statutoryPenaltyText": "사형, 무기 또는 5년 이상의 징역",  "allowedRanges": [    { "penaltyType": "DEATH", "allowedMin": 240, "allowedMax": 600, "text": "사형 (감경하면 무기징역 또는 징역 20년 ~ 50년)" },    { "penaltyType": "LIFE", "allowedMin": 120, "allowedMax": 600, "text": "무기징역 (감경하면 징역 10년 ~ 50년)" },    { "penaltyType": "PRISON", "allowedMin": 30, "allowedMax": 360, "text": "징역 2년 6개월 ~ 30년" }  ],  "allowedRangeNote": "감경·가중 사유를 반영해 법률상 선고할 수 있는 가장 넓은 범위예요.",  "recommended": { "minMonths": 84, "maxMonths": 144, "basis": "살인범죄 제2유형(보통 동기 살인), 감경영역. 특별감경인자 1개(실질적 피해 회복), 특별가중인자 없음" },  "terms": [ { "term": "감경영역", "desc": "형을 가볍게 할 특별한 사정이 있어 기본 권고 형량보다 낮은 구간이 적용되는 구간" } ]}
```

- 권고 범위 예시 값(84 ~ 144개월)은 설명용이다. 살인 제2유형(보통 동기 살인) 감경영역(7년 ~ 12년)을 가정한 **가상 값**이며, **대표 판례 등록 시 팀이 양형기준으로 다시 계산한다.** (와이어프레임 v2.1의 예시 값은 예전 사기 예시 사건 기준이라 이 명세와 다르다.)
- `summary`: `REVIEWED`일 때 S-05용 핵심 사실 요약(`case_section` `SUMMARY`)을 채운다.
- `allowedRanges`: `penalty_rule` 중 범위(`allowed_min` · `allowed_max`)가 모두 있는 규칙만 넣는다. 화면에는 징역만 보여 줘도 된다.
- (v0.4) 사형 · 무기가 법정형에 있는 사건은 `DEATH` · `LIFE` 항목도 넣는다. 이때 `allowedMin` ~ `allowedMax`는 작량감경해 징역으로 선고할 때의 범위이고, `text`에 그대로 선고할 수 있다는 내용을 함께 쓴다. 예: `{ "penaltyType": "LIFE", "allowedMin": 120, "allowedMax": 600, "text": "무기징역 (감경하면 징역 10년 ~ 50년)" }`, `{ "penaltyType": "DEATH", "allowedMin": 240, "allowedMax": 600, "text": "사형 (감경하면 무기징역 또는 징역 20년 ~ 50년)" }`
- `allowedRangeNote`는 서버 고정 문구다. `penalty_rule.allowed_basis`(산출 근거)는 내부용이라 응답하지 않는다.
- `allowedRanges.text`는 서버가 만들어 주는 표시 문구다. 화면마다 "1개월 ~ 10년" 표기가 달라지지 않게 하기 위해서다. 형식은 API 8 `options[].text`와 같다.
- 사전 판단은 넣지 않는다.

| 거절 | 조건 |
| --- | --- |
| 409 `INVALID_STATE` | `STARTED` (사전 판단 전) → S-03, `VERDICT_CONFIRMED` 이상 → 결과 화면 (1-6) |

---

### API 7. 섹션 확인 기록 — `POST /cases/{caseId}/experience/review-steps`

**요청**

json

```json
{ "step": 3 }
```

**응답 200**

json

```json
{ "status": "REVIEWING", "lastReviewedStep": 3, "openStep": 4 }
```

| 요청 step | 처리 |
| --- | --- |
| `lastReviewedStep + 1`이고 2 또는 3 | `last_reviewed_step` 갱신, 상태 `REVIEWING` |
| `lastReviewedStep + 1`이고 4 | `last_reviewed_step = 4`, 상태 `REVIEWED`. 응답 `openStep: null` |
| `lastReviewedStep` 이하 | 이미 확인한 섹션. **거절하지 않고** 현재 상태를 그대로 돌려준다 (버튼 두 번 누름 대비) |
| 그 밖 (건너뜀) | 409 `STEP_OUT_OF_ORDER` |
- 확인 후 다음 섹션 본문은 API 6을 다시 불러 받는다.

| 거절 | 조건 |
| --- | --- |
| 409 `INVALID_STATE` | `STARTED`, 또는 `VERDICT_CONFIRMED` 이상 |
| 409 `STEP_OUT_OF_ORDER` |  |
| 400 `VALIDATION_ERROR` | step이 2 ~ 4가 아님 |

`REVIEWED`에서 step 4 이하가 오면 현재 상태를 그대로 돌려준다.

---

### API 8. 판결 입력 정보 — `GET /cases/{caseId}/experience/verdict-form`

**응답 200**

json

```json
{  "penaltyOptions": [    { "penaltyType": "DEATH", "allowedMin": 240, "allowedMax": 600, "text": "사형 (감경하면 무기징역 또는 징역 20년 ~ 50년)", "suspensionAllowed": false, "reducibleTo": ["LIFE", "PRISON"] },    { "penaltyType": "LIFE", "allowedMin": 120, "allowedMax": 600, "text": "무기징역 (감경하면 징역 10년 ~ 50년)", "suspensionAllowed": false, "reducibleTo": ["PRISON"] },    { "penaltyType": "PRISON", "allowedMin": 30, "allowedMax": 360, "text": "징역 2년 6개월 ~ 30년", "suspensionAllowed": true, "reducibleTo": [] }  ],  "statutoryPenaltyText": "사형, 무기 또는 5년 이상의 징역",  "allowedRangeNote": "감경·가중 사유를 반영해 법률상 선고할 수 있는 가장 넓은 범위예요.",  "recommended": { "minMonths": 84, "maxMonths": 144, "basis": "…" },  "suspensionRule": { "maxPrisonMonths": 36, "maxFineAmount": 5000000, "minMonths": 12, "maxMonths": 60 },  "factors": [    { "factorId": 1, "label": "빌린 돈을 갚지 못해 오래 다툼이 있었다" },    { "factorId": 2, "label": "다투던 중 집에 있던 흉기를 집어 들었다" },    { "factorId": 3, "label": "범행 뒤 구호 조치 없이 현장을 떠났다" },    { "factorId": 4, "label": "사건 3개월 전부터 변제 문제로 여러 차례 다퉜다" },    { "factorId": 5, "label": "유족이 엄벌을 원한다" },    { "factorId": 6, "label": "피해자에게는 부양하던 어린 자녀 2명이 있다" },    { "factorId": 7, "label": "수사 초기부터 범행을 인정하고 반성하고 있다" },    { "factorId": 8, "label": "형사처벌 전력이 없다" },    { "factorId": 9, "label": "피해 회복을 위해 5,000만 원을 공탁했다" },    { "factorId": 10, "label": "피고인은 우발적 범행이라고 주장한다" },    { "factorId": 11, "label": "피고인은 오랜 채무로 정신적으로 지쳐 있었다고 주장한다" }  ]}
```

- `penaltyOptions`: `penalty_rule`의 `display_order` 순. 화면은 여기 있는 형벌만 보여 준다(FR-3-1). 버튼 비활성 판단(S-06c)도 이 값으로 한다.
- 무죄는 MVP 선택지에서 뺐다(요구사항 15장, v0.3). `penalty_rule`에도 무죄 행을 두지 않는다.
- (v0.4) 사형 · 무기가 법정형에 있는 사건은 `penaltyOptions`에 `DEATH` · `LIFE`가 들어간다. 항목마다 `reducibleTo`(감경해서 선고할 수 있는 형벌)를 함께 준다: `DEATH` → `["LIFE", "PRISON"]`, `LIFE` → `["PRISON"]`, `PRISON` · `FINE` → `[]`. `allowedMin` ~ `allowedMax`는 감경해 징역으로 선고할 때의 범위이고, `suspensionAllowed`는 `false`다. 예(살인): `{ "penaltyType": "LIFE", "allowedMin": 120, "allowedMax": 600, "text": "무기징역 (감경하면 징역 10년 ~ 50년)", "suspensionAllowed": false, "reducibleTo": ["PRISON"] }`
- (v0.4) 선고할 수 있는 범위 막대(FR-3-2)는 고른 형벌 항목의 `allowedMax`를 상한으로 쓴다. 유기징역이면 30년, 무기 · 사형을 감경하면 50년이다.
- 벌금 선고 가능 하한 25,000원은 형법 제45조 단서(감경 시 5만 원 미만 가능)와 제55조 제1항 제6호(벌금 감경 시 1/2)를 적용한 값이다(ERD 6장).
- `suspensionRule`: 집행유예 가능 조건(코드 상수, ERD `penalty_rule` 비고). 화면에서 집행유예 입력을 보여 줄지 판단한다.
- `factors`: 사건의 판단 요소 전체(`label`). 공개 단계와 관계없이 모두.
- 판결 입력값은 서버에 저장하지 않는다. S-04에 갔다 돌아오면 화면이 메모리에 기억한 값을 복원하고, 새로고침하면 빈 폼이다(임시 저장은 이후 단계, REQ-039).

| 거절 | 조건 |
| --- | --- |
| 409 `INVALID_STATE` | `REVIEWED`가 아님 → `currentStatus`의 화면 (1-6) |

---

### API 9. 판결 제출 — `POST /cases/{caseId}/experience/verdict`

**요청**

json

```json
{  "penaltyType": "PRISON",  "reducedTo": null,  "prisonMonths": 180,  "fineAmount": null,  "suspensionMonths": null,  "factors": [    { "factorId": 2, "direction": "UP" },    { "factorId": 6, "direction": "UP" },    { "factorId": 7, "direction": "DOWN" }  ],  "freeOpinion": null}
```

| 필드 | 필수 | 규칙 |
| --- | --- | --- |
| `penaltyType` | ✓ | 이 사건 `penalty_rule`에 있는 형벌 (법정형에서 고른 형벌: `DEATH` / `LIFE` / `PRISON` / `FINE`) |
| `reducedTo` |  | (v0.4) 감경 후 형벌. `DEATH` → `LIFE` · `PRISON`, `LIFE` → `PRISON`만 가능. 감경하지 않으면 `null`. 그 밖의 조합은 `INVALID_PENALTY_TYPE`. **형벌 종류가 바뀌는 감경만 기록한다.** 유기징역(`PRISON`) 안의 작량감경은 `reducedTo`에 기록하지 않는다 |
| `prisonMonths` | 최종 선고 형벌이 `PRISON`일 때 ✓ | 고른 형벌(`penaltyType`) 항목의 `allowedMin` ~ `allowedMax`. 최종 선고 형벌이 `DEATH` · `LIFE`면 `null` |
| `fineAmount` | `FINE`일 때 ✓ | `allowedMin` ~ `allowedMax` |
| `suspensionMonths` |  | 형벌이 집행유예 허용이고, 징역 36개월 이하 또는 벌금 500만 원 이하일 때만. 12 ~ 60. `DEATH` · `LIFE`를 고르면 감경해도 불가 |
| `factors` |  | 0개 이상, 이 사건 요소, 중복 없음, `direction` 필수(`UP` / `DOWN`) |
| `freeOpinion` |  | 확장(REQ-033). 최대 1,000자. 비교 대상 아님 |
- 선택하지 않은 형벌의 값(`PRISON`인데 `fineAmount`)은 `null`이어야 한다. 값이 있으면 `VALIDATION_ERROR`. 최종 선고 형벌(`reducedTo`가 있으면 그 값)이 `DEATH` · `LIFE`인데 `prisonMonths` · `fineAmount` · `suspensionMonths`에 값이 있어도 `VALIDATION_ERROR`다.
- (v0.4) 최종 선고 형벌은 `reducedTo`가 있으면 그 값, 없으면 `penaltyType`이다. 예: 무기징역 그대로 `{ "penaltyType": "LIFE", "reducedTo": null, "prisonMonths": null }`, 무기징역을 감경해 징역 40년 `{ "penaltyType": "LIFE", "reducedTo": "PRISON", "prisonMonths": 480 }`
- 무죄는 MVP에서 받지 않는다(요구사항 15장, v0.3). `penalty_rule`에 없는 형벌이므로 `INVALID_PENALTY_TYPE`으로 거절된다.
- (v0.9) `penaltyType`이 형벌 열거값(`DEATH` · `LIFE` · `PRISON` · `FINE`)에 없는 문자열(`NOT_GUILTY`, 오타 등)이어도 400이 아니라 422 `INVALID_PENALTY_TYPE`이다. 무죄를 위 규칙대로 거절하기 위해서이며, 1-5의 "잘못된 열거값 → `VALIDATION_ERROR`"보다 이 규칙이 우선한다. 그래서 서버는 `penaltyType` · `reducedTo`를 문자열로 받는다.

**응답 200**

json

```json
{ "status": "VERDICT_CONFIRMED" }
```

- 한 트랜잭션: `judgment`(`USER`, `FINAL`) + `judgment_factor` 저장, 상태 `REVIEWED` → `VERDICT_CONFIRMED`(조건부 갱신).
- 검사 순서: 상태 → 형벌(감경 조합 포함) → 형량 범위 → 집행유예 → 요소. 첫 번째로 걸린 사유 하나로 거절한다.

| 거절 | 조건 |
| --- | --- |
| 409 `INVALID_STATE` | `REVIEWED`가 아님 (이미 확정 포함) |
| 422 `INVALID_PENALTY_TYPE` |  |
| 422 `OUT_OF_ALLOWED_RANGE` | `details`에 `{ "field": "prisonMonths", "reason": "OUT_OF_ALLOWED_RANGE" }` |
| 422 `INVALID_SUSPENSION` |  |
| 422 `INVALID_FACTOR` |  |
| 400 `VALIDATION_ERROR` |  |

---

### 판결 응답 공통 형식 (API 10 · 12 · 14)

사용자 · AI · 실제 판결을 `subjectType` 기준의 **같은 형식**으로 돌려준다(요구사항 16장, DR-1).

json

```json
{  "subjectType": "AI",  "penaltyType": "PRISON",  "reducedTo": null,  "prisonMonths": 144,  "fineAmount": null,  "suspensionMonths": null,  "extraDispositions": [],  "summary": "다투다 벌어진 범행과 공탁 · 반성을 함께 저울질한 판단",  "reasoning": "흉기를 집어 들어 피해자를 공격하고 구호 조치 없이 자리를 떠난 점은 무겁지만, 공탁으로 피해 회복을 시도했고 범행을 인정하며 전력이 없는 점을 고려했다.",  "factors": [    { "factorId": 2, "label": "다투던 중 집에 있던 흉기를 집어 들었다", "direction": "UP", "evidence": null }  ]}
```

| 필드 | USER | AI | COURT |
| --- | --- | --- | --- |
| `summary` | 판단 요소 요약 태그로 만든 규칙 문장 (MVP) | 팀 입력 (`judgment.summary`) | 팀 입력 (`judgment.summary`) |
| `reasoning` | `null` | 판결 이유 | 재판부 판단 근거 요약 |
| `excerpt` · `plainExplanation` | 없음 | 없음 | 판결문 발췌 · 쉽게 말하면 |
| `extraDispositions` | `[]` | `[]` | 부가 처분 (예: 사회봉사 80시간, 몰수). `type`: `COMMUNITY_SERVICE` · `CONFISCATION` (ERD v1.4) |
| `reducedTo` | 제출값 | 등록값 | 등록값. 감경 후 형벌 (v0.4, ERD `judgment.reduced_to`) |
| `factors[].evidence` | `null` | `null` | 판결문 근거 문장 |
- `factors`에는 **고려한 요소만** 들어간다. 없는 요소는 "—"(ERD 결정 #1).
- `summary`는 S-09 판결 카드의 한 줄 요약이다.
  - **USER (MVP)**: 사용자가 고른 요소의 요약 태그(`factor.summary_tag`)를 방향별로 모아 서버가 규칙 문장으로 만든다. AI를 부르지 않는다. 문장 틀 **확정 (COMMON-16, 2026-10-06)**: "`{↑ 태그들}`을 무겁게 보고 `{↓ 태그들}`을 감안한 판단". ↑만 있으면 "`{↑ 태그들}`을 무겁게 본 판단", ↓만 있으면 "`{↓ 태그들}`을 감안한 판단", 고른 요소가 없으면 "판단 요소를 고르지 않은 판단". 같은 태그는 한 번만 쓴다. 같은 태그를 가진 요소를 하나는 ↑, 다른 하나는 ↓로 골랐으면 **먼저 고른(표시 순서가 앞선) 방향에만** 남긴다 — 한 사건 안의 여러 요소가 같은 태그를 가질 수 있어(ERD) 그대로 두면 "A를 무겁게 보고 A를 감안한 판단"처럼 스스로 모순되는 문장이 된다 (BE-10). 태그 뒤 조사는 받침에 따라 `을` · `를`을 고른다. 태그가 괄호 · 문장부호로 끝날 수 있으므로(`반성(자백)`) 끝에서부터 거슬러 올라가 처음 만나는 한글 또는 숫자로 판정한다(숫자는 한글로 읽은 소리 기준, `전과 3` → `을`) (BE-10). 예: 요소 2(`흉기 사용`, ↑) · 6(`피해자의 부양 가족`, ↑) · 7(`범행 인정 · 반성`, ↓) → "흉기 사용 · 피해자의 부양 가족을 무겁게 보고 범행 인정 · 반성을 감안한 판단"
  - **USER (확장, REQ-062)**: AI 비교 분석이 `DONE`이면 `perspectives.USER.text`(API 15)로 교체해 보여 준다. 실패하거나 생성 중이면 MVP 규칙 문장을 그대로 쓴다.
  - **AI · COURT**: 판결을 등록할 때 팀이 입력한 값. 없으면 `null`이고 카드에서 한 줄 요약을 숨긴다.

---

### API 10. AI 판결 — `GET /cases/{caseId}/experience/judgments/ai`

**응답 200**

json

```json
{  "judgment": { "subjectType": "AI", "penaltyType": "PRISON", "prisonMonths": 144, "suspensionMonths": null, "…": "…" },  "myJudgment": { "subjectType": "USER", "penaltyType": "PRISON", "prisonMonths": 180, "suspensionMonths": null, "…": "…" },  "diffFromMine": { "samePenaltyType": true, "prisonMonthsDiff": -36, "fineAmountDiff": null, "suspensionMonthsDiff": null },  "references": ["형법 제250조", "살인범죄 양형기준", "유사 판례 5건"]}
```

- 공개 AI 판결(`AI`, `is_published = true`)을 DB에서 읽기만 한다. **AI를 호출하지 않는다**(시퀀스 6장).
- `diffFromMine`: MVP는 숫자 차이만. 형벌 종류는 **최종 선고 형벌**(`reducedTo`가 있으면 그 값, v0.4)로 비교한다. 형벌 종류가 같아도 그 종류에 해당하는 값만 내려간다 — `PRISON`은 `prisonMonthsDiff`, `FINE`은 `fineAmountDiff`, `DEATH` · `LIFE`는 형량 값이 없어 둘 다 `null`이다(BE-10). **집행유예 여부가 다르면 같은 형벌로 보지 않는다** — 징역 2년 집행유예 3년과 징역 2년 실형은 개월 수가 같아도 같은 판결이 아니다(API 14 형벌 무게 순서 `SUSPENDED` < `PRISON`, BE-10). **둘 다 집행유예면 유예 기간도 `suspensionMonthsDiff`로 비교한다** — 징역 2년 집행유예 1년과 징역 2년 집행유예 3년은 징역 개월 수가 같아도 같은 판결이 아니다. 둘 다 집행유예가 아니면 `null`이다 (BE-10). 형벌 종류가 다르면 `prisonMonthsDiff` · `fineAmountDiff` 모두 `null`이고 `samePenaltyType: false`다. 화면이 "내 판결보다 6개월 짧음"으로 표시한다. 문구 규칙은 미정(요구사항 15장).
- `references`: 참고 자료 태그. `judgment.reference_tags`(AI 판결 등록 시 팀이 입력, ERD v1.3)에서 읽는다. 값이 없으면 빈 배열. (컬럼명은 SQL 예약어 `REFERENCES`를 피해 `reference_tags`로 두고, 응답 필드명은 `references` 그대로 쓴다.)
- 공개 사건인데 검수한 AI 판결(`AI`, `is_published = true`)이 등록돼 있지 않으면 `500 INTERNAL_ERROR`다. 사용자가 고칠 수 없는 데이터 문제라 거절 조건으로 두지 않는다 (BE-10).

| 거절 | 조건 |
| --- | --- |
| 409 `INVALID_STATE` | `VERDICT_CONFIRMED` 전 → `currentStatus`의 화면 (1-6) |

---

### API 11. 실제 판결 공개 — `POST /cases/{caseId}/experience/court-reveal`

**요청**: 본문 없음. **응답 200**: `{ "status": "AI_REVEALED" }`

- `VERDICT_CONFIRMED`면 `AI_REVEALED`로 바꾼다. 이미 `AI_REVEALED` · `COMPLETED`면 상태를 그대로 두고 현재 상태를 돌려준다.
- (확장) 처음 공개할 때 `comparison_analysis`를 `PENDING`으로 만들고 AI 비교 분석 생성을 비동기로 시작한다(시퀀스 8장). 응답은 생성을 기다리지 않는다.
- 상태를 옮기기 전에 **그다음 화면이 읽을 판결(공개 AI · 재판부 판결)이 등록돼 있는지 먼저 확인한다.** 확인하지 않으면 판결이 없는 사건에서 상태만 넘어가고 그 뒤 조회가 매번 500이 되어 되돌릴 방법 없이 결과 화면에 갇힌다 (BE-10).

| 거절 | 조건 |
| --- | --- |
| 409 `INVALID_STATE` | `VERDICT_CONFIRMED` 전 → `currentStatus`의 화면 (1-6) |

---

### API 12. 실제 판결 — `GET /cases/{caseId}/experience/judgments/court`

**응답 200**

json

```json
{  "judgment": {    "subjectType": "COURT",    "penaltyType": "PRISON",    "reducedTo": null,    "prisonMonths": 120,    "suspensionMonths": null,    "extraDispositions": [ { "type": "CONFISCATION", "value": "범행에 사용한 흉기" } ],    "summary": "유족의 처벌 의사를 무겁게 보면서도 공탁과 반성을 감안한 판단",    "reasoning": "…",    "excerpt": "…",    "plainExplanation": "…",    "factors": [ { "factorId": 2, "label": "다투던 중 집에 있던 흉기를 집어 들었다", "direction": "UP", "evidence": "…" } ]  },  "myJudgment": { "subjectType": "USER", "prisonMonths": 180, "suspensionMonths": null, "…": "…" },  "aiJudgment": { "subjectType": "AI", "prisonMonths": 144, "suspensionMonths": null, "…": "…" },  "source": { "sourceOrg": "법원 판결서 인터넷열람 서비스" },  "deidentifiedItems": ["인명", "지명", "사건번호", "날짜"]}
```

- `source`는 확장(REQ-055). `case_source` 중 `is_final = true`인 행의 `source_org`. 사건번호 · 법원명 · 선고일은 **절대 넣지 않는다**(FR-5-3).
- `deidentifiedItems`는 확장(FR-5-2 "이 사건에 대하여"). `legal_case.deidentified_items`에서 읽는다. 비식별화한 **항목 종류**만 내려가고 원래 값은 넣지 않는다. 값이 없으면 빈 배열.
- 공개 사건인데 검수한 재판부 · AI 판결이 등록돼 있지 않으면 `500 INTERNAL_ERROR`다 (API 10과 같은 이유, BE-10).

| 거절 | 조건 |
| --- | --- |
| 409 `INVALID_STATE` | `AI_REVEALED` 전 → `currentStatus`의 화면 (1-6, 보통 S-07) |

---

### API 13. 비교 공개 — `POST /cases/{caseId}/experience/comparison-reveal`

**요청**: 본문 없음. **응답 200**: `{ "status": "COMPLETED" }`

- `AI_REVEALED`면 `COMPLETED`로 바꾼다. 이미 `COMPLETED`면 그대로 돌려준다.
- (확장, v0.34) 처음 `COMPLETED`로 바꿀 때 **첫 회차 체험(`attempt_no = 1`)이면 판결 성향 기여(`experience_personality`)를 계산해 상태 변경과 같은 트랜잭션에서 저장한다.** 이미 `COMPLETED`면 기존 기록을 그대로 둔다(다시 세지 않는다). 저장이 실패하면 상태도 바뀌지 않는다. 계산 · 저장 형식은 [성향 산식](personality-scoring.md) 3장 · 6장 (BE-64, WBS 7.6.7)
- 상태를 옮기기 전에 **그다음 화면이 읽을 판결(공개 AI · 재판부 판결)이 등록돼 있는지 먼저 확인한다.** 확인하지 않으면 판결이 없는 사건에서 상태만 넘어가고 그 뒤 조회가 매번 500이 되어 되돌릴 방법 없이 결과 화면에 갇힌다 (BE-10).

| 거절 | 조건 |
| --- | --- |
| 409 `INVALID_STATE` | `AI_REVEALED` 전 |

---

### API 14. 세 판결 비교 — `GET /cases/{caseId}/experience/comparison`

**응답 200**

json

```json
{
  "preToFinal": {
    "preJudgment": {
      "rangeOptionId": 11,
      "label": "실형 5년 이상 ~ 10년 미만",
      "factorIds": [
        1,
        2
      ]
    },
    "finalJudgmentText": "징역 15년",
    "direction": "HEAVIER",
    "summaryText": "사건을 모두 확인한 뒤, 처음 생각보다 무거운 판결을 내렸어요."
  },
  "preToCourt": {
    "direction": "HEAVIER"
  },
  "judgments": {
    "USER": {
      "subjectType": "USER",
      "summary": "흉기 사용 · 피해자의 부양 가족을 무겁게 보고 범행 인정 · 반성을 감안한 판단",
      "…": "…"
    },
    "AI": {
      "subjectType": "AI",
      "summary": "다투다 벌어진 범행과 공탁 · 반성을 함께 저울질한 판단",
      "…": "…"
    },
    "COURT": {
      "subjectType": "COURT",
      "summary": "유족의 처벌 의사를 무겁게 보면서도 공탁과 반성을 감안한 판단",
      "…": "…"
    }
  },
  "matrix": [
    {
      "factorId": 2,
      "label": "다투던 중 집에 있던 흉기를 집어 들었다",
      "revealStage": "OVERVIEW",
      "user": "UP",
      "ai": "UP",
      "court": "UP",
      "category": "ALL_SAME"
    },
    {
      "factorId": 6,
      "label": "피해자에게는 부양하던 어린 자녀 2명이 있다",
      "revealStage": "DETAIL",
      "user": "UP",
      "ai": null,
      "court": "UP",
      "category": "DIVERGED"
    },
    {
      "factorId": 7,
      "label": "수사 초기부터 범행을 인정하고 반성하고 있다",
      "revealStage": "DETAIL",
      "user": "DOWN",
      "ai": "DOWN",
      "court": "DOWN",
      "category": "ALL_SAME"
    },
    {
      "factorId": 9,
      "label": "피해 회복을 위해 5,000만 원을 공탁했다",
      "revealStage": "DETAIL",
      "user": null,
      "ai": "DOWN",
      "court": "DOWN",
      "category": "ONLY_ME_MISSED"
    }
  ],
  "ruleSentences": {
    "common": [
      "세 판결 모두 흉기 사용을 형량을 높이는 요소로, 범행 인정 · 반성을 형량을 낮추는 요소로 봤어요."
    ],
    "differences": [
      "AI와 재판부는 피해 회복 공탁을 고려했지만, 내 판결에서는 고려하지 않았어요.",
      "피해자의 부양 가족에 대한 판단이 세 판결 사이에서 엇갈렸어요."
    ]
  },
  "analysisAvailable": false
}
```

**`preToFinal.direction`** — 사전 판단 구간과 최종 판결을 비교한다(`sentence_range_option.kind` · 개월 범위 사용). **확정 (BE-11, 2026-10-06)**

| 값 | 기준 |
| --- | --- |
| `HEAVIER` | 최종 판결이 구간보다 무거움 (예: 구간은 집행유예인데 실형, 실형 구간 상한보다 긺) |
| `SAME` | 최종 판결이 구간 안 |
| `LIGHTER` | 최종 판결이 구간보다 가벼움 (예: 구간은 실형인데 집행유예) |
- 형벌 종류의 무게는 `FINE`(벌금, 벌금 집행유예 포함) < `SUSPENDED`(징역 집행유예) < `PRISON`(실형) < `LIFE`(무기징역) < `DEATH`(사형) 순으로 먼저 비교하고(`sentence_range_option.kind` 선언 순서), 같은 종류가 `PRISON`이면 구간의 `min_months`(이상) · `max_months`(미만)로 더 비교한다(하한 미만은 `LIGHTER`, 상한 이상은 `HEAVIER`). 그 외 같은 종류(`FINE` · `SUSPENDED` · `LIFE` · `DEATH`)는 더 세분하지 않고 `SAME`이다. 최종 판결은 `reducedTo`가 있으면 그 값(최종 선고 형벌)으로 비교한다(ERD `sentence_range_option` 무겁기 순서와 같다). 무죄는 MVP에서 뺐으므로 비교 대상이 아니다(v0.3).

**`matrix[].category`** — 와이어프레임 S-09의 분류 태그(FR-6-4).

| 값 | 화면 문구 | 기준 |
| --- | --- | --- |
| `ALL_SAME` | 셋 모두 같게 본 요소 | 세 값이 모두 같음 (셋 다 `null`, 즉 아무도 고려하지 않은 요소는 매트릭스에서 뺀다. ERD 6장과 같음. 단, (확장, REQ-096) 사전 판단에서 고른 요소는 `changeType`을 보여 주기 위해 남기고 `category`는 `null`로 둔다) |
| `ONLY_ME_MISSED` | 나만 고려하지 않은 요소 | 나는 `null`, AI와 재판부는 같은 방향 |
| `DIVERGED` | 판단이 엇갈린 요소 | 그 밖 |
- `ruleSentences`: MVP의 공통점 · 차이점. 매트릭스로 만드는 **규칙 문장**이며 AI를 부르지 않는다. 문장은 `matrix[].label`(완전한 서술문)이 아니라 `factor.summary_tag`(요소별 요약어)로 만든다 — 라벨을 그대로 쓰면 "~다"로 끝나는 서술문 뒤에 조사가 붙어 비문이 되기 때문이다. 문장 틀은 6장 #4 **확정 (BE-26 · BE-11, 2026-10-06)** 참고. "정답 · 틀렸다 · 이중 잣대" 표현은 쓰지 않는다(요구사항 11장 톤 원칙 · FR-6-3).
- `judgments.*.summary`: 판결 카드의 한 줄 요약(판결 응답 공통 형식 참고). `USER`는 MVP 규칙 문장이다.
- `analysisAvailable`: (확장) AI 비교 분석을 API 15로 불러올 수 있는지. MVP에서는 항상 `false`.
- (확장, REQ-096) `matrix[].changeType`에 판단 이유 변화 유형(`NEWLY_LEARNED` 새로 알게 된 요소 / `KEPT` 처음부터 알던 요소 · 판단에 그대로 반영 / `WEIGHT_CHANGED` 이미 알던 요소의 무게가 바뀜 / `null`)을 넣는다. 판별 기준은 요구사항 FR-6-6: `OVERVIEW` 이후 요소가 최종 판결에만 있으면 `NEWLY_LEARNED`, `OVERVIEW` 요소가 사전 · 최종 양쪽에 있으면 `KEPT`, 한쪽에만 있으면 `WEIGHT_CHANGED`. 사전 판단 작용 요소가 있을 때만.
- **사전 판단은 체험 완료 후 이 API에서 처음 내려간다.** 결과 화면의 독자 의견에 처음 선택한 형량을 표시한다. 진행 중에는 선택 구간을 포함한 사전 판단을 다시 조회할 수 없다.
- **`preToCourt.direction`**: `preToFinal.direction`과 같은 계산을 재판부 판결에 적용한다. **재판부가 더 무거우면 `HEAVIER`**다. `LIGHTER`는 재판부가 사전 판단 구간보다 가벼움, `SAME`은 같은 형벌 종류 및 해당 구간이다. `SAME`은 형량의 정확한 일치를 뜻하지 않는다. 감경은 `reducedTo` 우선, 실형 구간은 하한 이상·상한 미만이며 표시 label을 해석하지 않는다.
- `preToCourt`는 API 14 최상위 객체이며 `direction`만 포함한다. 기존 필드·`COMPLETED` 이후 조회 제한은 그대로 유지한다. API 3에서는 이 비교나 AI·재판부 판결을 공개하지 않는다.

| 거절 | 조건 |
| --- | --- |
| 409 `INVALID_STATE` | `COMPLETED` 전 → `currentStatus`의 화면 (1-6, 보통 S-08) |

---

### API 15. (확장) AI 비교 분석 — `GET /cases/{caseId}/experience/comparison/analysis`

> **(v0.14) 보류** — AI 비교 분석(REQ-062 · 063)을 11차 회의에서 확장 범위에서 제외했다. 구현하지 않으며, 아래 정의는 결정 기록으로 남긴다. S-09는 API 14의 규칙 문장 · 한 줄 요약을 계속 쓴다.

**응답 200**

json

```json
{  "status": "DONE",  "analysis": {    "common": [ { "text": "세 판결 모두 피해 금액이 큰 점을 무겁게 봤어요.", "factorIds": [1] } ],    "differences": [ { "text": "재판부는 피해자가 처벌을 원한다는 점을 형량을 높이는 요소로 봤지만, 나와 AI는 고려하지 않았어요.", "factorIds": [5] } ],    "perspectives": {      "USER": { "text": "돈을 받은 방식과 피해 규모를 무겁게 보고, 반성은 조금만 반영한 판단이에요.", "factorIds": [1, 3, 6] },      "AI": { "text": "…", "factorIds": [1, 3, 4] },      "COURT": { "text": "…", "factorIds": [4, 6, 7] }    }  }}
```

| status | analysis | 화면 |
| --- | --- | --- |
| `PENDING` | `null` | "분석 중" 표시, 2 ~ 3초 뒤 다시 조회 (최대 횟수는 기술 설계에서 정함) |
| `DONE` | 분석 | 공통점 · 차이점 영역을 교체 |
| `FAILED` | `null` | 규칙 문장 유지 (`fail_reason`은 응답하지 않음) |
- `perspectives`를 포함해 항목마다 `factorIds`가 있다. 서버 검증(목록 안의 요소인가, 금지 표현이 없는가)을 통과한 것만 `DONE`이다(시퀀스 8장 안전장치).
- `perspectives.USER.text`는 확장 단계의 **AI "내 판결" 요약**이다(REQ-062). `DONE`이면 화면은 S-09 내 판결 카드의 한 줄 요약(API 14 `judgments.USER.summary`, MVP 규칙 문장)을 이 문장으로 바꾼다. `perspectives.USER.factorIds`는 사용자가 고른 요소 안에서만 나와야 한다.

| 거절 | 조건 |
| --- | --- |
| 409 `INVALID_STATE` | `COMPLETED` 전 |
| 404 `EXPERIENCE_NOT_FOUND` |  |

분석 행이 없으면(확장 배포 전에 공개된 체험) `FAILED`로 응답한다.

---

### API 16. (확장) 공개 사건 상세 — `GET /cases/{caseId}`

S-20 사건 디테일(정보 구조 v1.12)이 체험을 만들기 전에 부르는 API다(v0.29, BE-74). 신문 지면에서 기사를 고르거나 링크 · 새로고침으로 `/cases/{caseId}`에 바로 들어와도 이 API 하나로 화면을 그리고 버튼을 나눈다.

**요청**: 본문 없음. 쿠키가 있으면 함께 보낸다.

**응답 200**

```json
{  "caseId": 1,  "title": "빌린 돈 문제로 찾아온 지인을 살해한 사건",  "crimeType": "MURDER",  "crimeCategoryLabel": "생명범죄",  "chargeName": "살인",  "shortIntro": "빌린 돈 문제로 찾아온 지인과 다투다 흉기로 살해한 사건입니다.",  "keywords": ["돈 문제", "지인", "흉기"],  "difficulty": "MID",  "estimatedMinutes": 15,  "participantCount": 42,  "thumbnailUrl": null,  "overview": "피고인이 빌린 돈을 갚지 못해 오래 다투던 지인이 집으로 찾아오자, …",  "myStatus": null}
```

- 필드는 API 1 카드와 같고(`chargeName` 포함, v0.32), `overview`(사건 개요, API 4의 `case.overview`와 같은 값)를 더한다. `participantCount` · `myStatus` 기준도 API 1과 같다.
- **법정형 · 선고 가능 범위 · 권고 범위 · 판결 · 섹션 본문은 넣지 않는다**(FR-2-8, API 4와 같은 기준). 섹션은 체험 중 API 6이 단계별로 연다.
- 쿠키는 **읽기만** 하고 발급하지 않는다(발급은 API 2 · P-2, 1-2). 쿠키가 없거나 형식이 틀리거나 체험 전이면 `myStatus`는 `null`이다.
- 버튼 분기(정보 구조 S-20): `myStatus`가 `null`이면 **체험 시작**(API 2로 체험을 만든 뒤 S-03), `IN_PROGRESS`면 **이어서 하기**, `COMPLETED`면 **결과 다시 보기**. 이어서 하기 · 결과 다시 보기도 API 2를 불러 응답 `status`로 이동한다(1-6). 세부 상태(7단계)는 이 API에 내보내지 않는다.
- 캐시: `myStatus`가 사용자마다 달라 API 1과 같은 규칙이다(지금은 `no-store`, HTTP 캐시를 켤 때는 `private` + `Vary: Cookie`).
- 대본(판결 체험 무대, REQ-089)은 이 API에 넣지 않는다. 서버에서 내려줄지는 별도로 정한다(#138 · #141).

| 거절 | 조건 |
| --- | --- |
| 404 `CASE_NOT_FOUND` | 사건 없음 · 비공개 → 화면은 S-14 |
| 400 `VALIDATION_ERROR` | `caseId`가 숫자가 아님 |

---

## 4. 상태별 호출 가능 API

| status | 호출 가능 (조회) | 호출 가능 (변경) |
| --- | --- | --- |
| `STARTED` | 3, 4 | 5 |
| `PRE_JUDGED` | 3, 6 | 7 |
| `REVIEWING` | 3, 6 | 7 |
| `REVIEWED` | 3, 6, 8 | 7 (그대로 통과), 9 |
| `VERDICT_CONFIRMED` | 3, 10 | 11 |
| `AI_REVEALED` | 3, 10, 12 | 11 (그대로 통과), 13 |
| `COMPLETED` | 3, 10, 12, 14 (15 보류) | 11 · 13 (그대로 통과) |

API 1 · 2 · 16은 상태와 관계없이 부를 수 있다. 표에 없는 조합은 `409 INVALID_STATE`다.

---

## 5. 화면별 호출 순서

| 화면 | 진입 시 | 버튼 |
| --- | --- | --- |
| S-02 | 1 | `체험 시작` → 2 → 응답 status로 이동 (1-6). 신문 지면(확장)에서는 기사를 고르면 S-20 |
| S-20 (확장) | 16 | `체험 시작` · `이어서 하기` · `결과 다시 보기` → 2 → 응답 status로 이동 (1-6) |
| S-03 | 4 | `판단 제출` → 5 → S-04 |
| S-04 | 6 | `읽었습니다` → 7 → 6 다시 조회. 마지막 섹션 후 S-05 |
| S-05 | 6 (summary 포함) | `판결 내리러 가기` → S-06 |
| S-06 | 8 | `확정하기` → 9 → S-07 |
| S-07 | 10 | `실제 판결 확인하기` → 11 → S-08 |
| S-08 | 12 | `세 판결 비교 보기` → 13 → S-09 |
| S-09 | 14 (15는 v0.14 보류) | `다른 사건 체험` → S-02 |

- 모든 화면은 진입 API가 `409 INVALID_STATE`를 돌려주면 `currentStatus`로 1-6 표의 "보낼 화면"으로 이동한다. `404 EXPERIENCE_NOT_FOUND`면 S-02, `404 CASE_NOT_FOUND`면 S-14로 간다. 진입 전에 API 3을 따로 부르지 않는다(6장 #1 확정, 요청 1회 절약).
- S-04 · S-05는 같은 API 6을 쓰므로, `PRE_JUDGED` · `REVIEWING`에서 S-05에 들어오면 API 6이 성공한다. 이때는 응답의 `status`가 `REVIEWED`가 아니면 화면이 S-04로 보낸다.

---

## 6. 결정 필요 사항

| # | 내용 | 제안 | 영향 |
| --- | --- | --- | --- |
| 1 | 체험 상태 확인을 API 3으로 먼저 할지, 각 API의 `INVALID_STATE`로 처리할지 | **확정 (v0.3)**: 각 API의 `INVALID_STATE`로 처리 (요청 수 절약, 1-6 · 5장) | 프론트 라우팅 구조 (기술 스택 5장) |
| 2 | 쿠키 이름 · 기간, 프론트 · API 도메인 구조 | 도메인 구조 **확정 (v0.3)**: 같은 도메인, Nginx `/api` 프록시(기술 스택 8장). 쿠키 `NLNB_AID` · 1년은 제안 유지(요구사항 15장) | CORS · SameSite 설정 |
| 3 | `preToFinal.direction` 세부 비교 규칙 | **확정 (BE-11, 2026-10-06)**: 형벌 종류를 `FINE < SUSPENDED < PRISON < LIFE < DEATH` 순(`sentence_range_option.kind` 선언 순서)으로 먼저 비교한다. 종류가 다르면 그 순서로 `HEAVIER`/`LIGHTER`, 같은 종류가 `PRISON`이면 구간의 `min_months`/`max_months`(상한 미만 기준)로 더 비교하고, 그 외 종류는 세분하지 않고 `SAME`이다. 감경이 있으면 최종 선고 형벌(`reducedTo`)로 비교한다 | S-09 한 줄 요약 문구 |
| 4 | 규칙 문장(`ruleSentences`) 문장 틀 | **확정 (BE-26 · BE-11, 2026-10-06)**: 요소 라벨(완전한 서술문)이 아니라 요약어(`factor.summary_tag`)로 만든다. `ALL_SAME`은 방향별로 요약어를 모아 "세 판결 모두 {↑ 요약어들}을 형량을 높이는 요소로[, {↓ 요약어들}을 형량을 낮추는 요소로] 봤어요.", `ONLY_ME_MISSED`는 "AI와 재판부는 {요약어들}을 고려했지만, 내 판결에서는 고려하지 않았어요.", `DIVERGED`는 "{요약어들}에 대한 판단이 세 판결 사이에서 엇갈렸어요."로 한 문장씩 만든다. 해당 분류가 없으면 각각 "세 판결이 똑같이 본 판단 요소는 없어요." / "세 판결 사이에 판단이 엇갈린 점은 없어요."를 넣는다 | S-09 공통점 · 차이점 |
| 5 | 무죄 선택 처리 (요구사항 15장) | **확정 (v0.3)**: MVP에서 무죄 선택지를 뺀다. 이후 도입 여부는 추후 검토 | API 8 · 9 · 14 |
| 6 | API 15 재조회 간격 · 최대 횟수, AI 생성 시간 초과 기준 | ~~3초 간격, 최대 10회 / 30초~~ — v0.14: API 15 보류로 대상 없음 | 확장 단계 |
| 7 | 내 판결 한 줄 요약(`summary`) 규칙 문장 틀 · 태그 수 제한 | **확정 (COMMON-16 · BE-26, 2026-10-06)**: 423줄 문장 틀(`{↑ 태그들}을 무겁게 보고 {↓ 태그들}을 감안한 판단` 등)을 확정. 방향별 태그 수는 **제한하지 않는다**(BE-26) — 요소마다 서로 다른 요약어가 붙어 많이 고르면 문장이 길어질 수 있지만, S-09에서 실제로 문제가 되면 상한(예: 최대 3개 + "외 N개")을 다시 검토한다 | S-09 판결 카드 |

---

## 7. (확장) 추가될 API 후보 (v0.14)

11 · 12차 회의에서 나온 확장 기능에 필요한 API 후보다. **경로 · 요청 · 응답은 아직 설계하지 않았다.** 흐름은 `user-flow.md` 3장, 데이터는 ERD 8장(제안)을 따른다.

| 후보 | 용도 | 화면 | 관련 REQ |
| --- | --- | --- | --- |
| ~~성향 테스트 문항 조회~~ | 9장 P-1로 확정 (v0.20) | S-15 | 112 |
| ~~성향 테스트 응답 제출~~ | 9장 P-2로 확정 (v0.20) | S-15 → S-16 | 112 · 113 · 117 |
| ~~내 성향 결과 조회~~ | 9장 P-3으로 확정 (v0.20) | S-16 · S-17 | 113 · 127 |
| ~~유형 설명 조회 (공개)~~ | 9장 P-4로 확정 (v0.20) | S-16 공유 | 116 |
| ~~마이페이지 조회~~ | 9장 P-5로 확정 (v0.20) | S-17 | 119 · 127 |
| ~~관리자 검수~~ | 8-2로 옮김 (v0.17) | S-19 | 047 · 133 (BE-33) |

- 판결 체험의 성향 갱신(REQ-118)은 별도 API 없이 API 13(비교 공개)이 `COMPLETED`로 바꿀 때 서버가 함께 처리한다(v0.34 확정, BE-64 — API 13 본문 참고).
- 관리자 인증(CSRF 토큰 · 로그인 · 로그아웃 · 내 정보)은 8장에서 확정했다(v0.15, BE-39). 검수 API 계약은 8-2에 초안으로 적었다(v0.17, BE-33 · BE-41 ~ 43).

## 8. (확장) 관리자 API

관리자 후검수(BE-33)용 API다. **MVP 범위가 아니다.** 경로는 `/api/v1/admin/**`이고 관리자 로그인이 필요하다(로그인 · CSRF 발급 제외). 사용자 API(1장 ~ 6장)는 영향이 없다.

### 8-1. 공통

- **인증**: 세션 쿠키 `NLNB_ADMIN_SESSION`(로그인 API가 발급, HttpOnly · Secure · SameSite=Lax, 기본 30분). 로그인하지 않으면 401 `UNAUTHORIZED`. 로그인한 뒤 계정이 비활성 · 삭제되면 다음 요청부터 401이고 세션이 끝난다(v0.18)
- **CSRF**: 상태를 바꾸는 요청(POST 등, 로그인 포함)은 헤더 `X-XSRF-TOKEN`에 쿠키 `XSRF-TOKEN` 값을 담는다. 없거나 다르면 403 `FORBIDDEN`. 쿠키는 A-1이 내려 준다. **로그인에 성공하면 새 토큰으로 바뀌므로**(A-2 응답의 쿠키) 화면은 값을 저장해 두지 말고 요청할 때마다 쿠키를 읽는다(v0.18)
- 없는 관리자 경로도 로그인하지 않았으면 404가 아니라 401이다(경로 존재 여부를 드러내지 않는다)

#### BE-88 검증 기준 — 현행 인증·인가 및 응답 우선순위

- **인증과 인가**: 관리자 체인이 유효한 인증을 복원하지 못한 경우 401 `UNAUTHORIZED`, 인증된 주체에게 `ROLE_ADMIN`이 없는 경우 403 `FORBIDDEN`이다. 단, 앞선 CSRF 검사나 계정 상태 검사에서 거절되면 그 응답으로 종료한다.
- **쿠키·세션의 의미**: `NLNB_AID`는 익명 체험 식별자이며 관리자 인증이 아니다. 빈 세션이나 `NLNB_ADMIN_SESSION` 쿠키만 있어도 서버가 유효한 인증을 복원하지 못하면 미인증이다. 알 수 없는 세션 ID·만료된 세션에도 같은 기준을 적용한다.
- **인증 영역 분리**: 관리자 요청은 세션 체인만 거치고 사용자 Bearer 검증 체인은 거치지 않는다. 일반 사용자 JWT를 관리자 인증으로 승격시키지 않고 관리자 로그인·계정 활성 상태·세션 폐기를 독립적으로 관리하기 위해서다. 회원 Bearer 토큰만 보낸 관리자 GET은 권한 검사 이전에 관리자 인증이 없어 401이며, 사용자 API의 `TOKEN_INVALID`·`TOKEN_EXPIRED` 판정을 이 경로에 적용하지 않는다.
- **처리 순서**: 세션의 인증 복원 → `AdminAccountStatusFilter` → `CsrfFilter` → 인증·인가 검사 → 컨트롤러. 계정 상태 필터는 복원된 `AdminPrincipal` 계정이 비활성·삭제되었으면 세션과 인증을 폐기하고 보호된 경로를 바로 401로 끝낸다. 일반적인 만료 세션은 인증을 복원하지 못하므로 이 계정 상태 거절 분기를 거치지 않는다.

| 보호된 관리자 API 요청 | 현행 응답 | 판단 근거 |
| --- | --- | --- |
| 미인증 GET (쿠키 없음·빈 세션·익명 ID 쿠키·잘못되거나 만료된 관리자 세션) | 401 `UNAUTHORIZED` | 유효한 관리자 체인 인증 없음 |
| 인증된 주체이나 `ROLE_ADMIN` 없음 (CSRF 비대상 또는 통과) | 403 `FORBIDDEN` | 인증은 있으나 관리자 권한 없음 |
| 회원 Bearer 토큰만 보낸 GET | 401 `UNAUTHORIZED` | 관리자 체인은 해당 토큰을 검증하지 않음 |
| 익명·잘못된 세션·만료된 세션 POST + 정상 CSRF 쿠키/헤더 | 401 `UNAUTHORIZED` | CSRF 통과 후 인증 부재 확인 |
| 익명·잘못된 세션·만료된 세션 POST + CSRF 누락/불일치 | 403 `FORBIDDEN` | 인증·인가 검사 전 CSRF에서 종료 |
| 정상 관리자 세션 POST + CSRF 누락/불일치 | 403 `FORBIDDEN` | CSRF에서 종료 |
| 복원된 관리자 계정이 비활성·삭제됨 | 401 `UNAUTHORIZED` 및 세션 폐기 | 보호된 경로는 CSRF 정상 여부보다 계정 상태 검사가 우선 |

- **공개 인증 경로 예외**: `GET /admin/auth/csrf`는 미인증도 200으로 토큰을 발급한다. `POST /admin/auth/login`은 관리자 인증을 요구하지 않지만 CSRF는 요구하며, CSRF 실패 시 403, 통과 후 잘못된 자격 증명은 401 `INVALID_CREDENTIALS`이다. 두 공개 경로에서 기존 관리자 계정이 비활성·삭제된 경우 계정 상태 필터는 세션을 폐기하되 바로 401을 반환하지 않고 다음 단계로 보낸다. 따라서 로그인 POST는 여전히 CSRF 검사 및 입력/자격 증명 검사를 받는다.
- **기존 문구 정합성 확인 사항**: 위의 기존 “로그인하지 않으면 401”·“없는 관리자 경로도 401” 및 A-3의 문구는 모든 조건에서의 응답 우선순위를 표현하지 못한다. CSRF 검사가 필요한 POST에서는 403이 먼저 나올 수 있다. BE-88은 원래 문구와 응답 코드를 임의로 바꾸지 않고 현행 검증 결과를 구분해 적었으며, 포괄적인 문구 정리는 팀 확인 후 진행한다.
- **검증 한계**: `ROLE_USER` 403은 테스트에서 인증된 SecurityContext를 세션에 인위적으로 저장해 인가 분기만 검증한다. 일반 사용자 로그인이나 JWT가 실제로 관리자 세션을 발급한다는 뜻이 아니다. 실제 HTTP 세션 만료 검증은 정상 관리자 로그인 후 해당 테스트 세션의 유휴 시간을 1초로 줄여 수행하며, 운영 기본 30분 경과나 브라우저 쿠키 정책 전체를 검증하지 않는다. 근거와 후속 검토는 [BE-88 의사결정 기록](decisions/BE-88.md).

### A-1. CSRF 토큰 — `GET /admin/auth/csrf`

로그인 전에 먼저 부른다. 토큰은 쿠키 `XSRF-TOKEN`(HttpOnly 아님, 화면 스크립트가 읽는다)으로 내려간다.

```json
{ "headerName": "X-XSRF-TOKEN" }
```

### A-2. 로그인 — `POST /admin/auth/login`

```json
{ "email": "admin@example.com", "password": "********" }
```

성공 200 (세션 쿠키 발급, 로그인 전에 세션이 있었으면 세션 ID를 바꾼다)

```json
{ "adminId": 1, "email": "admin@example.com", "displayName": "관리자" }
```

| 거절 | 조건 |
| --- | --- |
| 400 `VALIDATION_ERROR` | 이메일 · 비밀번호가 비었거나 너무 김 |
| 401 `INVALID_CREDENTIALS` | 계정 없음 · 비활성 · 비밀번호 불일치 (구분하지 않음) |
| 403 `FORBIDDEN` | CSRF 토큰 없음 · 불일치 |

이메일은 대소문자를 구분하지 않는다. 성공하면 쿠키 `XSRF-TOKEN`도 새 값으로 바뀐다(로그인 전 토큰은 더 쓸 수 없다, v0.18).

### A-3. 로그아웃 — `POST /admin/auth/logout`

세션을 끝낸다. 성공 204. 로그인하지 않았으면 401, CSRF 토큰이 없으면 403. 쿠키 `XSRF-TOKEN`도 지운다(v0.18).

인증 부재와 CSRF 오류가 동시에 있는 경우의 현행 응답, 비활성 관리자 계정의 예외는 8-1의 BE-88 검증 기준을 참고한다.

### A-4. 내 정보 — `GET /admin/auth/me`

로그인한 관리자 정보(A-2 성공 응답과 같은 형식). 로그인하지 않았으면 401 `UNAUTHORIZED`.

### 8-2. 검수 API 계약 (초안, 7.4.7 · BE-33 · BE-41 ~ BE-43)

> **계약 초안이다(v0.17).** 관리자 화면(7.5.7 · FE-16, A)이 목 데이터로 먼저 만들 수 있도록 경로 · 요청 · 응답 형태를 먼저 정한다(WBS R16). 구현(10/21 ~ 23)하면서 필드가 바뀌면 이 절을 고치고 화면 담당에게 알린다. ⚠️ 표시는 팀 확인이 필요한 항목이다(8-3).

**검수 흐름**

```text
파이프라인 적재                      관리자 (S-19)
사건 DRAFT ─┬─ 판단 요소 축 확정 (A-12) ───────────────┐
            ├─ 사건 내용 수정 (A-11) ──────────────────┤ 처음 바꾸는 순간 DRAFT → REVIEW ⚠️
            ├─ AI 판결 후보 승인 · 반려 (A-9 · A-10) ──┤
            └─ 재판부 판결 후보 승인 · 반려 (A-9 · A-10)┘
                                                       ↓
                               공개 (A-13): 조건을 모두 채우면 PUBLISHED
```

- **승인 = 그 사건의 공개 판결로 정한다.** 사건마다 공개 AI 판결 1개 · 공개 재판부 판결 1개(`uk_judgment_published`)라서, 승인하면 같은 주체의 기존 공개 판결은 비공개로 바뀐다(행은 지우지 않는다, REQ-079).
- **공개한 사건은 판결 교체(승인) · 내용 수정을 막는다** ⚠️. 반려는 사용자 화면에 영향이 없으므로 공개한 사건에서도 허용한다(공개 중인 후보만 막는다, A-10). 이미 체험한 사용자의 비교 결과(S-09)가 바뀌지 않게 하기 위해서다(REQ-046 "모든 사용자에게 같은 AI 판결"). 판단 요소의 가치관 축(A-12)은 체험 완료 시점에 고정 저장되므로(성향 산식 3-4) 공개 뒤에도 고칠 수 있다.
- 공개 취소(PUBLISHED → 비공개)는 이번 단계에 두지 않는다 ⚠️.
- 원본 판결문 정보(`case_source`)는 이 API에서만 내려 준다(ERD 5장 원칙, 기술 스택 3장).
- **동시 요청**: 상태를 바꾸는 요청(A-9 ~ A-13)은 트랜잭션을 시작하면 먼저 그 사건의 `legal_case` 행을 잠근다(`SELECT … FOR UPDATE`). 그 뒤에 사건 상태 · 후보 상태를 확인한다. 한 관리자가 "아직 공개 전"을 확인한 사이 다른 관리자가 공개해 버리는 경합을 막는다(사용자 API의 조건부 갱신 원칙과 같은 수준).
- **`INVALID_STATE` 사유**: 관리자 API의 `INVALID_STATE`는 `currentStatus`에 **사건 상태**만 담고, 이유는 `details`의 사유 코드로 구분한다 — `[{ "field": "case", "reason": "CASE_PUBLISHED" }]`. 사유 코드: `CASE_PUBLISHED`(공개한 사건) · `CANDIDATE_REJECTED`(이미 반려한 후보) · `CANDIDATE_PUBLISHED`(공개 중인 후보).
- 상태를 바꾸는 요청(A-9 ~ A-13)과 원문 열람(A-7)은 감사 로그를 남긴다(7.4.5). 원문은 원본 판결문이라 읽기도 남긴다.
- 이 절의 모든 경로는 8-1 공통 규칙(세션 · CSRF)을 따른다.

**API 목록**

| # | 메서드 · 경로 | 용도 | 이슈 |
| --- | --- | --- | --- |
| A-5 | `GET /admin/cases` | 검수 사건 목록 | BE-41 |
| A-6 | `GET /admin/cases/{caseId}` | 사건 검수 상세 (내용 · 원본 정보 · 요소 · 판결 후보 · 공개 가능 여부) | BE-41 |
| A-7 | `GET /admin/cases/{caseId}/sources/{sourceId}` | 원본 판결문 원문 | BE-41 |
| A-8 | `GET /admin/judgments/{judgmentId}` | 판결 후보 상세 (AI 생성 정보 포함) | BE-41 |
| A-9 | `POST /admin/judgments/{judgmentId}/approve` | 판결 후보 승인 (= 공개 판결로 정함) | BE-42 |
| A-10 | `POST /admin/judgments/{judgmentId}/reject` | 판결 후보 반려 | BE-42 |
| A-11 | `PATCH /admin/cases/{caseId}` | 사건 내용 수정 | BE-43 |
| A-12 | `PUT /admin/factors/{factorId}/value-axis` | 판단 요소 가치관 축 확정 | BE-43 · BE-48 |
| A-13 | `POST /admin/cases/{caseId}/publish` | 사건 공개 | BE-43 |
| A-14 | `POST /admin/case-imports` | 사건 등록 시작 (원본 판결문 → 파이프라인 실행) **초안** | 미정 (FE-45 제안) |
| A-15 | `GET /admin/case-imports/{importId}` | 사건 등록 진행 상황 **초안** | 미정 (FE-45 제안) |

### A-5. 검수 사건 목록 — `GET /admin/cases`

| 쿼리 | 기본값 | 설명 |
| --- | --- | --- |
| `status` | `DRAFT,REVIEW` | 쉼표로 여러 개. `DRAFT` · `REVIEW` · `PUBLISHED` |
| `pending` | (없음) | `true`면 검수 대기 후보(`PENDING`)가 있는 사건만. 공개한 사건에 새로 쌓인 후보를 찾을 때 `status=PUBLISHED&pending=true`로 쓴다 |
| `page` | 0 | 0부터 |
| `size` | 20 | 1 ~ 100 |

사용자 API 1과 달리 쪽 나눔을 둔다(파이프라인이 사건을 계속 쌓기 때문). **최근 활동 순** — 사건 수정 시각(`legal_case.updated_at`)과 그 사건 AI · 재판부 후보의 적재 · 검수 시각 중 가장 늦은 값(`lastActivityAt`). 파이프라인이 새 후보만 쌓아도 목록 위로 올라온다. 범죄 유형 필터 · 제목 검색은 사건 수가 적어 두지 않고, 필요하면 추가한다.

```json
{
  "items": [
    {
      "caseId": 12,
      "title": "빌린 돈 문제로 찾아온 지인을 살해한 사건",
      "crimeType": "MURDER",
      "status": "DRAFT",
      "pending": { "aiCandidates": 2, "courtCandidates": 1, "axisNeedsReview": 3 },
      "hasPublishedAi": false,
      "hasPublishedCourt": false,
      "createdAt": "2026-10-20T09:00:00+09:00",
      "updatedAt": "2026-10-20T09:00:00+09:00",
      "lastActivityAt": "2026-10-20T09:00:00+09:00"
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 1
}
```

- `pending.aiCandidates` · `courtCandidates`: 검수 대기(`PENDING`) 판결 후보 수
- `pending.axisNeedsReview`: `value_axis_status = AUTO`인 판단 요소 수

| 거절 | 조건 |
| --- | --- |
| 400 `VALIDATION_ERROR` | 모르는 `status` 값, `pending`이 `true` · `false`가 아님, `page` 음수, `size` 범위 밖 |

### A-6. 사건 검수 상세 — `GET /admin/cases/{caseId}`

```json
{
  "caseId": 12,
  "status": "DRAFT",
  "content": {
    "title": "빌린 돈 문제로 찾아온 지인을 살해한 사건",
    "crimeType": "MURDER",
    "chargeName": "살인",
    "shortIntro": "…",
    "overview": "…",
    "keywords": ["금전 갈등", "지인"],
    "difficulty": "MID",
    "estimatedMinutes": 15,
    "thumbnailUrl": null,
    "appliedLaw": "형법 제250조 제1항",
    "statutoryPenaltyText": "사형, 무기 또는 5년 이상의 징역",
    "recommendedMinMonths": 120,
    "recommendedMaxMonths": 192,
    "recommendedBasis": "…",
    "incidentDate": "2024-03-02",
    "guideline": { "guidelineId": 4, "versionName": "2023 수정 (…)" },
    "deidentifiedItems": ["인명", "지명", "사건번호"]
  },
  "sources": [
    {
      "sourceId": 30,
      "courtLevel": "FIRST",
      "caseNumber": "2099고합000",
      "courtName": "가상지방법원",
      "decidedAt": "2025-01-10",
      "isFinal": true,
      "sourceOrg": "법제처",
      "note": null,
      "hasOriginalText": true
    }
  ],
  "factors": [
    {
      "factorId": 101,
      "displayOrder": 1,
      "label": "빌린 돈을 갚으라는 말에 화가 나 다툼이 시작됐다",
      "preLabel": "금전 문제로 다투다 일어났다",
      "revealStage": "OVERVIEW",
      "summaryTag": "금전 갈등",
      "valueAxis": "FAULT_STANDARD",
      "valueAxisStatus": "AUTO",
      "valueAxisVotes": { "runs": 5, "counts": { "FAULT_STANDARD": 3, "NONE": 2 }, "needsReview": false },
      "summaryTagCandidates": { "candidates": [{ "tag": "채무 다툼", "score": 0.9211 }, { "tag": "반복된 다툼", "score": 0.8735 }], "inVocabulary": false, "ambiguous": false, "suggested": "채무 다툼" }
    }
  ],
  "candidates": {
    "AI": [
      { "judgmentId": 501, "reviewStatus": "PENDING", "isPublished": false, "penaltyLabel": "징역 15년", "modelName": "(모델 이름)", "createdAt": "2026-10-20T09:00:00+09:00" }
    ],
    "COURT": [
      { "judgmentId": 502, "reviewStatus": "PENDING", "isPublished": false, "penaltyLabel": "징역 12년", "modelName": null, "createdAt": "2026-10-20T09:00:00+09:00" }
    ]
  },
  "publishReadiness": { "ready": false, "reasons": ["NO_PUBLISHED_AI", "NO_PUBLISHED_COURT"] }
}
```

- 예시의 사건번호 · 법원명은 가상 값이다. 실제 값은 저장소 문서에 적지 않는다.
- `content`는 A-11에서 고칠 수 있는 필드와 읽기 전용 필드를 함께 담는다(고칠 수 있는 필드는 A-11 표).
- `candidates`는 최신 순. 반려(`REJECTED`)된 후보도 이력으로 함께 내려 준다.
- `penaltyLabel`은 API 14 `preToFinal.finalJudgmentText`(예: "징역 15년")와 같은 규칙으로 만든다.
- `factors[].summaryTagCandidates`(BE-80): 요약어 후보(ERD `factor.summary_tag_candidates`). 후보 계산을 켜지 않고 적재한 요소는 `null`. **제안일 뿐 요약어(`summaryTag`)를 바꾸지 않는다** — `suggested`가 있으면 관리자가 요약어를 그 값으로 맞출지 판단한다(요약어 수정 API는 아직 없음). `ambiguous: true`면 1 · 2순위가 붙어 제안하지 않은 것이다.
- `publishReadiness.reasons`는 A-13의 공개 조건 코드(A-13 표)다. 비어 있으면 `ready = true`.
- 원문(`original_text`)은 크기가 커서 A-7로 따로 받는다.

| 거절 | 조건 |
| --- | --- |
| 404 `CASE_NOT_FOUND` | 사건이 없음 (관리자 API는 상태와 관계없이 찾는다) |

### A-7. 원본 판결문 원문 — `GET /admin/cases/{caseId}/sources/{sourceId}`

```json
{ "sourceId": 30, "originalText": "…" }
```

- 원본 판결문이므로 열람할 때마다 감사 로그(누가 · 언제 · 어느 원문)를 남긴다.

| 거절 | 조건 |
| --- | --- |
| 404 `CASE_NOT_FOUND` | 사건이 없거나, 원문 행이 이 사건의 것이 아님 |

### A-8. 판결 후보 상세 — `GET /admin/judgments/{judgmentId}`

판결 응답 공통 형식(API 10 · 12 · 14)의 판결 필드에 검수용 정보를 더한다.

```json
{
  "judgmentId": 501,
  "caseId": 12,
  "subjectType": "AI",
  "reviewStatus": "PENDING",
  "isPublished": false,
  "reviewedBy": null,
  "reviewedAt": null,
  "reviewNote": null,
  "judgment": { "…": "판결 응답 공통 형식과 같은 필드 (penaltyType · reducedTo · prisonMonths · summary · reasoning · referenceTags · factors 등)" },
  "generation": {
    "modelName": "(모델 이름)",
    "promptVersion": "(프롬프트 버전)",
    "inputSnapshot": { "…": "AI에 넣은 입력 (참고 판례 · 양형기준 포함)" },
    "generationReport": { "…": "검증 경고 · 사전 학습 점검 · 회차 선택 근거" }
  }
}
```

- 검수 정보(`reviewStatus` · `reviewedBy` · `reviewedAt` · `reviewNote`)는 AI · 재판부 후보 모두 최상위에 둔다. `generation`(생성 조건)은 AI 후보일 때만 있고 재판부 후보는 `null`.
- 판단 요소별 방향 · 근거는 `judgment.factors[]`(`direction` · `evidence`)에 있다.
- `raw_output`(모델 원본 출력)은 크기가 커서 내려 주지 않는다 ⚠️.
- `reviewNote`는 반려 사유(A-10). `judgment.review_note`(V16, ERD v1.22)에 둔다.
- `reviewStatus`는 `judgment.review_status`가 있으면 그 값, 없으면 공개 중이면 `APPROVED` · 아니면 `PENDING`이다(시드 · 파이프라인은 검수 칸을 채우지 않는다). A-5 대기 수 · A-6 후보 목록도 같은 규칙이다.
- `reviewedBy`는 관리자 화면에서 검수했으면 그 관리자 이메일, 그 전 시드 · 오프라인 검수는 `ai_generation.reviewed_by`(이름)다. `reviewedAt`도 같은 순서로 고른다.

| 거절 | 조건 |
| --- | --- |
| 404 `JUDGMENT_NOT_FOUND` | 판결이 없거나 사용자 판결(`USER`). 사용자 판결은 관리자 API로 보여 주지 않는다 |

### A-9. 판결 후보 승인 — `POST /admin/judgments/{judgmentId}/approve`

본문 없음. 성공 200, A-8과 같은 형식으로 바뀐 후보를 돌려준다.

한 트랜잭션에서:

0. 사건 행을 잠그고(8-2 동시 요청) 사건 · 후보 상태를 확인한다.
1. 같은 사건 · 같은 주체의 기존 공개 판결을 비공개로 바꾼다(검수 상태는 그대로 둔다). 검수 기록 없이 공개돼 있던 판결(시드 등)은 내린 뒤 "대기"로 보이지 않게 `APPROVED`로 채운다.
2. 이 후보를 `APPROVED` · 공개로 바꾸고 검수자 · 검수 시각을 남긴다. AI 후보는 기존 도구용 `ai_generation.review_status` · `reviewed_at`도 함께 맞춘다(A-10도 같음).

- **1이 2보다 먼저 DB에 반영돼야 한다.** `uk_judgment_published`는 부분 유니크 인덱스라 문장마다 검사하므로, 2가 먼저 실행되면 공개 판결이 잠깐 둘이 되어 위반이 난다. 1은 벌크 UPDATE로 하거나 명시적으로 `flush`한 뒤 2를 실행한다(JPA 변경 감지 순서에 맡기지 않는다).
- 그래도 유니크 위반이 나면(잠금 밖 경로 등) 500이 아니라 409 `INVALID_STATE`로 바꿔 돌려준다.

- 이미 승인돼 공개 중인 후보를 다시 승인하면 아무것도 바꾸지 않고 200(중복 클릭 대비).
- 사건이 `DRAFT`면 `REVIEW`로 바꾼다 ⚠️.

| 거절 | 조건 |
| --- | --- |
| 404 `JUDGMENT_NOT_FOUND` | 판결이 없거나 `USER` |
| 409 `INVALID_STATE` | 사건이 이미 `PUBLISHED`(`CASE_PUBLISHED`, 공개 뒤 교체 금지 ⚠️), 후보가 `REJECTED`(`CANDIDATE_REJECTED`), 동시 승인으로 유니크 위반 |
| 403 `FORBIDDEN` | CSRF 토큰 없음 · 불일치 |

### A-10. 판결 후보 반려 — `POST /admin/judgments/{judgmentId}/reject`

```json
{ "reason": "참고 판례에 다른 죄명 판례가 섞였음" }
```

성공 200, A-8과 같은 형식.

- `reason`은 필수, 앞뒤 공백을 자른 뒤 1 ~ 500자. 자른 값을 저장한다.
- 반려는 되돌리지 않는다. 다시 쓰려면 파이프라인으로 새 후보를 만든다(REQ-079, user-flow 3-6).
- 승인했다가 교체로 비공개가 된 후보(`APPROVED` · 비공개)도 반려할 수 있다. `review_status`는 이력이 아니라 현재 검수 판단이며, 이전 승인은 감사 로그(`JUDGMENT_APPROVE`)로 확인한다.
- **공개한 사건에서도 반려할 수 있다.** 파이프라인을 다시 돌리면 공개한 사건에도 비공개 후보가 쌓이는데(ERD `ai_generation` 아래 설명), 반려까지 막으면 이 후보들이 계속 `PENDING`으로 남는다. 반려는 사용자 화면에 영향이 없다. 공개 중인 후보만 막는다.
- 사건이 `DRAFT`면 `REVIEW`로 바꾼다 ⚠️.

| 거절 | 조건 |
| --- | --- |
| 400 `VALIDATION_ERROR` | `reason`이 비었거나 500자 초과 |
| 404 `JUDGMENT_NOT_FOUND` | 판결이 없거나 `USER` |
| 409 `INVALID_STATE` | 후보가 공개 중(`CANDIDATE_PUBLISHED`, 공개 전 사건이면 먼저 다른 후보를 승인해 교체한다), 이미 `REJECTED`(`CANDIDATE_REJECTED`) |

### A-11. 사건 내용 수정 — `PATCH /admin/cases/{caseId}`

보낸 필드만 바꾼다. 성공 200, A-6과 같은 형식.

- **필드 없음과 `null`을 구분한다.** 본문에 없는 필드는 그대로 두고, `null`을 보낸 필드는 지운다(필수 컬럼은 `null` 불가). 구현은 본문을 필드 맵(`Map`)으로 받아 필드마다 검사한다(BE-43). 숫자 필드는 정수만 받고 문자열 숫자(`"15"`)도 허용한다(1-1). 문자열 필드는 앞뒤 공백을 자른 값으로 검사 · 저장한다(제목 중복 검사 포함).
- 권고 하한 · 상한 중 하나만 보내면 **저장된 다른 값과** 비교한다(하한 ≤ 상한).

```json
{ "shortIntro": "고친 소개 문구", "keywords": ["금전 갈등", "지인", "말다툼"] }
```

| 필드 | 규칙 (ERD `legal_case`) |
| --- | --- |
| `title` | 1 ~ 100자, 다른 사건과 겹치면 안 된다(`uk_legal_case_title`) |
| `shortIntro` | 1 ~ 200자 |
| `overview` | 1자 이상 |
| `keywords` | 문자열 배열. 지울 때는 `[]`(jsonb는 배열로 넣는다, ERD 7장). `null`은 거절 |
| `difficulty` | `LOW` · `MID` · `HIGH` · `null` |
| `estimatedMinutes` | 1 이상 · `null` |
| `thumbnailUrl` | 300자 이하 · `null`. `http(s)://` 주소나 `/`로 시작하는 서비스 경로만(`javascript:` · `data:` · `//host`는 거절) |
| `recommendedMinMonths` · `recommendedMaxMonths` | 1 이상 · `null`. 바꾼 뒤의 하한 · 상한이 둘 다 있으면 하한 ≤ 상한 |
| `recommendedBasis` | 문자열 · `null` |

- 죄명 · 적용 법조문 · 법정형 · 판단 요소 구성 · 형벌 규칙은 이 API로 바꾸지 않는다(파이프라인 · SQL 경로, WBS 7-5 8순위).
- 제목 중복은 저장 전에 확인하고, 그 사이 다른 요청이 같은 제목을 저장해 유니크 위반이 나도 `DUPLICATE_TITLE`로 돌려준다.
- 감사 로그 `CASE_UPDATE`에는 바꾼 필드 이름만 남긴다(`{"fields": ["shortIntro", "keywords"]}`). 값은 사건 본문이라 남기지 않는다.
- 사건이 `DRAFT`면 `REVIEW`로 바꾼다 ⚠️.

| 거절 | 조건 |
| --- | --- |
| 400 `VALIDATION_ERROR` | 위 규칙 위반(필수 필드 · `keywords`에 `null` 포함), 모르는 필드, 빈 본문 |
| 404 `CASE_NOT_FOUND` | 사건이 없음 |
| 409 `INVALID_STATE` | 사건이 `PUBLISHED`(`CASE_PUBLISHED`, 공개 뒤 수정 금지 ⚠️) |
| 409 `DUPLICATE_TITLE` | 제목이 다른 사건과 겹침 |

### A-12. 판단 요소 가치관 축 확정 — `PUT /admin/factors/{factorId}/value-axis`

```json
{ "valueAxis": "PRINCIPLE_RELATION" }
```

- `valueAxis`는 `APOLOGY_SINCERITY` · `FAULT_STANDARD` · `PRINCIPLE_RELATION` · `ORDER_OPPORTUNITY` · `null`(어느 축에도 맞지 않음으로 확정).
- 성공하면 `value_axis_status = CONFIRMED`(ERD v1.15). 투표 기록(`value_axis_votes`)은 그대로 둔다.
- 사건이 `PUBLISHED`여도 고칠 수 있다(이미 끝난 체험의 성향은 바뀌지 않는다, 성향 산식 3-4).
- 감사 로그 `VALUE_AXIS_CONFIRM`에 사건 ID와 이전 · 바꾼 축을 남긴다(`{"caseId": 1, "from": "FAULT_STANDARD", "to": null}`).
- 사건이 `DRAFT`면 `REVIEW`로 바꾼다 ⚠️.

성공 200:

```json
{ "factorId": 101, "valueAxis": "PRINCIPLE_RELATION", "valueAxisStatus": "CONFIRMED" }
```

| 거절 | 조건 |
| --- | --- |
| 400 `VALIDATION_ERROR` | `valueAxis` 키가 없음(값 `null`은 허용), 모르는 축 |
| 404 `FACTOR_NOT_FOUND` | 판단 요소가 없음 |

### A-13. 사건 공개 — `POST /admin/cases/{caseId}/publish`

본문 없음. 공개 조건을 모두 채우면 `PUBLISHED` · `published_at`을 기록하고 200, A-6과 같은 형식.

| 공개 조건 코드 | 조건 |
| --- | --- |
| `NO_PUBLISHED_AI` | 공개 AI 판결이 있어야 한다 (API 10 · 11) |
| `NO_PUBLISHED_COURT` | 공개 재판부 판결이 있어야 한다 (API 12 · 13) |
| `NO_FACTORS` | 판단 요소가 1개 이상 |
| `NO_OVERVIEW_FACTOR` | 사전 판단에 쓸 `OVERVIEW` 요소가 1개 이상 (API 4 · 5) |
| `NO_PENALTY_RULE` | 형벌 규칙(`penalty_rule`)이 1개 이상 (API 8) |

- 가치관 축이 아직 `AUTO`인 요소가 있어도 공개는 막지 않는다(화면이 A-6 `pending.axisNeedsReview`로 알려 준다).
- 공개 조건은 A-6 `publishReadiness`와 같은 코드로 판정한다. 화면을 본 뒤 상태가 바뀌었을 수 있으므로 요청 때 사건 행을 잠근 뒤 다시 판정한다.
- 감사 로그 `CASE_PUBLISH`를 남긴다.

| 거절 | 조건 |
| --- | --- |
| 404 `CASE_NOT_FOUND` | 사건이 없음 |
| 409 `INVALID_STATE` | 이미 `PUBLISHED`(`CASE_PUBLISHED`) |
| 409 `PUBLISH_NOT_READY` | 공개 조건 미충족. `details`에 `[{ "field": "case", "reason": "NO_PUBLISHED_AI" }, …]` |

### A-13-1. 사건 대표사진 프롬프트 추천 — `POST /admin/cases/{caseId}/photos/representative/prompt`

> 번호: A-16 ~ A-41은 8-4(운영 메뉴 API, 초안)가 이미 예약해 둬서, A-13에 소속된 추가 API라는 뜻으로 A-13-N을 쓴다.
> **재검토(v0.36)**: Cloudflare Workers AI로 사진을 직접 생성하는 방식(v0.37 이전 초안)은 그림 품질이 기대에 못 미쳐 그만뒀다. 사진은 사람이 만들어 A-13-3 · A-13-4로 올리고, AI(Gemini)는 그 사람이 참고할 **한글 프롬프트만** 추천한다(결정: `docs/decisions/BE-91.md`).

본문 없음. 사건의 `overview` · `chargeName`을 바탕으로 Gemini가 한글 이미지 생성 프롬프트 한 문단을 지어 돌려준다. 등장인물은 얼굴 없는 회색 엑스트라로, 내로 · 남불은 넣지 말라는 규칙을 프롬프트 지시문에 고정으로 포함한다(COMMON-41).

응답 200:

```json
{ "prompt": "신문 지면에 쓸 삽화를 그려줘. 어두운 거실에서 두 사람이 돈 문제로 다투는 장면을..." }
```

| 거절 | 조건 |
| --- | --- |
| 404 `CASE_NOT_FOUND` | 사건이 없음 |
| 502 `PROMPT_GENERATION_FAILED` | Gemini 호출 실패, 또는 설정 없음(`GEMINI_API_KEY`) |

조회만 하므로 사건 행을 잠그지 않고, 감사 로그도 남기지 않는다(사건 내용을 바꾸지 않음).

### A-13-2. 사건 발단 사진 프롬프트 추천 — `POST /admin/cases/{caseId}/photos/incident/prompt`

사건이 시작된 발단 장면을 그리기 위한 한글 프롬프트를 추천한다. `overview`에 판단 요소(`factor`) 목록을 더해 지시문을 만든다.

```json
{ "hint": "다투는 장면을 더 강조해줘" }
```

| 필드 | 규칙 |
| --- | --- |
| `hint` | 선택, 500자 이하. 특정 장면을 더 짚고 싶을 때만 |

응답 200은 A-13-1과 같은 형식(`{ "prompt": "..." }`).

| 거절 | 조건 |
| --- | --- |
| 400 `VALIDATION_ERROR` | `hint` 500자 초과 |
| 404 `CASE_NOT_FOUND` | 사건이 없음 |
| 502 `PROMPT_GENERATION_FAILED` | A-13-1과 같음 |

### A-13-3. 사건 대표사진 업로드 — `POST /admin/cases/{caseId}/photos/representative` (multipart/form-data)

관리자가 A-13-1 프롬프트를 참고해 직접(또는 다른 생성 도구로) 만든 사진을 올린다. S3에 저장하고, 신문 지면(S-02 등)에 쓰는 `legal_case.thumbnail_url`을 그 URL로 바꾼다. 새 테이블을 두지 않고 A-11이 이미 자유롭게 바꿀 수 있던 칸을 그대로 쓴다. 응답은 A-6과 같은 형식(`AdminCaseDetailResponse`).

| 필드(form) | 규칙 |
| --- | --- |
| `file` | 필수. `image/jpeg` · `image/png` · `image/webp`만 허용, 최대 10MB(8-1 공통 multipart 상한) |

- 공개한 사건은 바꿀 수 없다(A-11과 같은 규칙). 감사 로그 `CASE_PHOTO_GENERATE`(`detail.type = "REPRESENTATIVE"`)를 남긴다.

| 거절 | 조건 |
| --- | --- |
| 400 `INVALID_IMAGE_FILE` | 파일이 없음 · 비어 있음 · 허용하지 않는 형식 |
| 404 `CASE_NOT_FOUND` | 사건이 없음 |
| 409 `INVALID_STATE` | 이미 `PUBLISHED`(`CASE_PUBLISHED`) |
| 502 `FILE_UPLOAD_FAILED` | S3 업로드 실패, 또는 설정 없음(`S3_BUCKET_NAME`) |

### A-13-4. 사건 발단 사진 업로드 — `POST /admin/cases/{caseId}/photos/incident` (multipart/form-data)

사건 안에서 쓰는 발단 사진 한 장을 올려 `case_incident_photo`에 순서대로 쌓는다(여러 번 불러 여러 장 추가). 201과 함께 추가된 사진을 돌려준다.

| 필드(form) | 규칙 |
| --- | --- |
| `file` | 필수. A-13-3과 같은 형식 · 크기 제한 |
| `caption` | 선택, 200자 이하. 화면에 보일 설명 |
| `prompt` | 선택, 자유 길이. 관리자가 이 사진을 만들 때 참고한 프롬프트(A-13-2 추천 그대로 또는 고친 것) — 감사 · 재참고용으로만 남긴다 |

응답 201:

```json
{ "id": 12, "imageUrl": "https://…/case-photos/case-7/incident-0-….jpg", "caption": "다툼이 격해진 순간", "displayOrder": 0, "createdAt": "2026-10-20T09:00:00Z" }
```

- `displayOrder`는 0부터 자동으로 매겨진다(사건마다 이미 있는 발단 사진 수).
- S3 객체 키(`s3_key`)는 내부 기록용이라 응답에 내려주지 않는다. `prompt`는 관리자가 보냈을 때만 들어가고, 응답에는 내려주지 않는다(A-13-5 목록도 마찬가지 — 내부 기록 확인은 DB로 한다).
- 공개한 사건은 추가할 수 없다. 감사 로그 `CASE_PHOTO_GENERATE`(`detail.type = "INCIDENT"`, `detail.photoId`)를 남긴다.

| 거절 | 조건 |
| --- | --- |
| 400 `INVALID_IMAGE_FILE` | A-13-3과 같음 |
| 400 `VALIDATION_ERROR` | `caption` 200자 초과 |
| 404 `CASE_NOT_FOUND` | 사건이 없음 |
| 409 `INVALID_STATE` | 이미 `PUBLISHED`(`CASE_PUBLISHED`) |
| 502 `FILE_UPLOAD_FAILED` | A-13-3과 같음 |

### A-13-5. 사건 발단 사진 목록 — `GET /admin/cases/{caseId}/photos/incident`

사건에 쌓인 발단 사진을 순서대로 돌려준다(A-13-4 응답과 같은 형식의 배열, `prompt` 제외). 공개한 사건도 조회만은 된다.

```json
[
  { "id": 12, "imageUrl": "…", "caption": "다툼이 격해진 순간", "displayOrder": 0, "createdAt": "…" }
]
```

| 거절 | 조건 |
| --- | --- |
| 404 `CASE_NOT_FOUND` | 사건이 없음 |

### A-13-6. 신문 지면 생성 — `POST /admin/cases/{caseId}/paper/generate`

사건 내용(`overview` · `factor` · `chargeName`)을 바탕으로 Gemini가 메인 화면 신문 지면 콘텐츠를 짓는다(BE-92, COMMON-41). 이미 있으면 새로 만들지 않고 내용을 덮어쓴다. 본문 없음.

응답 200:

```json
{
  "desk": "사회면",
  "headline": "빌린 돈 문제로 다투다 벌어진 사건",
  "deck": "오랜 금전 다툼 끝에...",
  "photo": "https://…/case-photos/case-1/representative-….jpg",
  "caption": "다투는 두 사람",
  "article": ["문단 1", "문단 2"],
  "extraPhotos": [
    { "id": 12, "imageUrl": "…", "caption": "다툼이 격해진 순간", "displayOrder": 0, "createdAt": "…" }
  ],
  "updatedAt": "2026-10-20T09:00:00Z"
}
```

- `photo`는 A-13-3으로 올린 사건 대표사진(`legal_case.thumbnail_url`)을 그대로 내려준다. 아직 안 올렸으면 `null`.
- `extraPhotos`는 A-13-5와 같은 배열 — `case_incident_photo`를 그대로 재사용한다(새로 저장하지 않는다, `docs/decisions/BE-92.md`).
- 공개한 사건은 바꿀 수 없다(A-11과 같은 규칙). 감사 로그 `CASE_PAPER_GENERATE`를 남긴다.

| 거절 | 조건 |
| --- | --- |
| 404 `CASE_NOT_FOUND` | 사건이 없음 |
| 409 `INVALID_STATE` | 이미 `PUBLISHED`(`CASE_PUBLISHED`) |
| 502 `PROMPT_GENERATION_FAILED` | Gemini 호출 실패, 응답을 JSON으로 못 읽음, 또는 설정 없음(`GEMINI_API_KEY`) |

### A-13-7. 신문 지면 조회 — `GET /admin/cases/{caseId}/paper`

응답은 A-13-6과 같은 형식. 아직 생성한 적 없으면 404.

| 거절 | 조건 |
| --- | --- |
| 404 `PAPER_NOT_FOUND` | 아직 생성한 적 없음 |

### A-13-8. 신문 지면 수정 — `PATCH /admin/cases/{caseId}/paper`

A-11과 같은 방식(보낸 필드만, `null` = 지움). `headline` · `article`은 필수라 `null` 불가.

```json
{ "headline": "고친 헤드라인", "deck": null }
```

| 필드 | 규칙 |
| --- | --- |
| `headline` | 1 ~ 200자, `null` 불가 |
| `deck` | 0 ~ 300자, `null` 가능(지움) |
| `caption` | 0 ~ 200자, `null` 가능(지움) |
| `article` | 문단 1개 이상의 배열, `null` 불가(빈 배열도 거절) |

응답은 A-13-6과 같은 형식.

| 거절 | 조건 |
| --- | --- |
| 400 `VALIDATION_ERROR` | 위 규칙 위반, 또는 모르는 필드 |
| 404 `CASE_NOT_FOUND` | 사건이 없음 |
| 404 `PAPER_NOT_FOUND` | 아직 생성한 적 없음(먼저 A-13-6으로 생성해야 한다) |
| 409 `INVALID_STATE` | 이미 `PUBLISHED`(`CASE_PUBLISHED`) |

### A-14. 사건 등록 시작 — `POST /admin/case-imports` (초안)

> **초안이다.** 파이프라인(`tools/ai-judgment`)을 자바로 옮긴 뒤 서버가 구현한다. 화면(FE-45)은 이 계약의 목으로 먼저 만들었고, 서버 구현 전에는 404가 온다. 계약이 바뀌면 이 절과 `adminMockApi.js`를 함께 고친다.

원본 판결문을 받아 파이프라인(비식별화 · 구조화 → 재판부 판결 정리 → 사전 학습 점검 → 참고 자료 검색 → AI 판결 생성 → 회차 선택 → 가치관 축 분류 → 적재)을 서버에서 실행한다. 오래 걸리므로 **작업만 만들고 202로 바로 돌려준다.**

```json
{
  "crimeType": "MURDER",
  "sources": [
    {
      "courtLevel": "FIRST",
      "caseNumber": "2099고합000",
      "courtName": "가상지방법원",
      "decidedAt": "2025-01-10",
      "isFinal": true,
      "sourceOrg": "법제처",
      "note": null,
      "originalText": "…"
    }
  ]
}
```

| 필드 | 규칙 |
| --- | --- |
| `crimeType` | 필수. `MURDER` · `FRAUD` · `INJURY` |
| `sources` | 필수. 1 ~ 3건, 심급(`courtLevel`: `FIRST` · `APPEAL` · `SUPREME`)이 서로 달라야 한다 |
| `sources[].caseNumber` | 필수, 50자 이하. 이미 등록된 사건번호면 409 `DUPLICATE_SOURCE` |
| `sources[].originalText` | 필수, 200,000자 이하. 원본 판결문이라 요청 본문 로그에 남기지 않는다 |
| 그 밖 | `courtName` · `decidedAt`(YYYY-MM-DD) · `isFinal` · `sourceOrg` · `note`는 선택. 사건 원본 정보(`case_source`)에 저장한다 |

응답 202: `{ "importId": 1, "status": "QUEUED" }`

| 거절 | 조건 |
| --- | --- |
| 400 `VALIDATION_ERROR` | 위 규칙 위반. `details`에 `[{ "field": "sources[0].originalText", "reason": "INVALID" }]` |
| 409 `DUPLICATE_SOURCE` | 이미 등록한 사건번호 |
| 409 `IMPORT_IN_PROGRESS` | 실행 중인 등록이 있음 (한 번에 하나만. 외부 AI 호출 한도 보호) |

- 감사 로그 `CASE_IMPORT`를 남긴다(원문은 남기지 않고 사건번호 · 심급만).

### A-15. 사건 등록 진행 상황 — `GET /admin/case-imports/{importId}` (초안)

```json
{
  "importId": 1,
  "status": "RUNNING",
  "stages": [
    { "name": "extract", "status": "DONE" },
    { "name": "court", "status": "RUNNING" },
    { "name": "contamination", "status": "PENDING" }
  ],
  "caseId": null,
  "errorCode": null,
  "message": null,
  "createdAt": "2026-10-20T09:00:00+09:00",
  "finishedAt": null
}
```

- `status`: `QUEUED` · `RUNNING` · `SUCCEEDED` · `FAILED`. 화면은 끝날 때까지 2초마다 묻는다.
- `stages[].name`: `extract` · `court` · `contamination` · `rag` · `generate` · `select` · `axis` · `load`. 건너뛴 단계는 `SKIPPED`, 값은 `PENDING` · `RUNNING` · `DONE` · `SKIPPED` · `FAILED`.
- `SUCCEEDED`이면 `caseId`가 채워지고, 사건은 `DRAFT`로 적재된다. A-5 목록 · A-6 상세로 이어서 검수한다.
- `FAILED`이면 `errorCode` · `message`를 내려 준다. `message`에 원문 · 모델 출력을 담지 않는다.

| 거절 | 조건 |
| --- | --- |
| 404 `IMPORT_NOT_FOUND` | 등록 작업이 없음 |

**에러 코드**: 1-5의 `JUDGMENT_NOT_FOUND` · `FACTOR_NOT_FOUND` · `DUPLICATE_TITLE` · `PUBLISH_NOT_READY`(v0.17 추가)와 공통 코드를 쓴다. 사건 등록 초안(A-14 · A-15)은 `DUPLICATE_SOURCE` · `IMPORT_IN_PROGRESS` · `IMPORT_NOT_FOUND`를 더 쓴다(1-5 목록에는 서버 구현 때 추가).

- `CASE_NOT_FOUND`는 관리자 API에서 "사건이 없음"만 뜻한다(사용자 API는 "없거나 `PUBLISHED`가 아님").

### 8-3. 검수 API 확인 항목

| # | 항목 | 이 초안의 제안 | 확인할 사람 |
| --- | --- | --- | --- |
| 1 | 공개 뒤 판결 교체 · 내용 수정 | 막는다(409). 바꿔야 하면 새 사건으로 적재 | 팀 |
| 2 | 공개 취소 | 이번 단계에 두지 않는다 | 팀 |
| 3 | `REVIEW` 상태 | ✅ 관리자가 처음 바꾸는 순간(A-9 ~ A-12) 자동으로 `DRAFT → REVIEW`. "누가 손대기 시작한 사건"을 목록에서 구분하는 용도. `REVIEW → DRAFT`로 되돌리는 경로는 두지 않는다 (#111 리뷰, A 동의) | A 김해송 (S-19 목록) |
| 4 | 재판부 후보 검수 상태 · 반려 사유 저장 | ✅ V16(BE-41)에서 `judgment`에 `review_status` · `reviewed_by_admin_id` · `reviewed_at` · `review_note`를 두어 AI · 재판부를 같은 방식으로 검수한다(ERD v1.22). 응답 필드는 그대로 | B |
| 5 | 검수자 기록 | ✅ 관리자 ID(`judgment.reviewed_by_admin_id`)로 남긴다. `ai_generation.reviewed_by`(이름)는 예전 기록용으로 둔다 | B |
| 6 | 모델 원본 출력(`raw_output`) | ✅ 내려 주지 않는다. 필요하면 A-8에 쿼리 옵션으로 추가 (#111 리뷰, A 동의) | A 김해송 (S-19 상세) |
| 7 | 관리자 OAuth(BE-40, D)와 체인 | 관리자 체인에 `oauth2Login` 자리 연결은 B, 성공 처리 · 허용 목록은 D (#108, WBS v1.9 7-2-1) | D 정민석 |

---

### 8-4. 운영 메뉴 API (초안, A-16 ~ A-41)

> **초안이다.** 관리자 화면(FE-45)의 운영 메뉴(대시보드 · 회원 · 게시글 · 신고 · 관리자 계정 · 공지)를 목으로 먼저 만들면서 정한 계약이다. 회원 · 게시글 · 신고 기능은 서버에 아직 없다(마이그레이션 · 엔티티 포함). 서버를 구현하는 쪽이 이 절을 기준으로 하되, 바꾸면 이 절과 `frontend/src/pages/admin/adminOpsMockApi.js` · `adminOpsRealApi.js`를 함께 고친다.
>
> 공통: 8-1(인증 · CSRF · 401)을 따른다. 목록은 `?page=0&size=20`(size 1 ~ 100)을 받고 `{ items, page, size, totalElements }`로 답한다. 상태를 바꾸는 요청은 감사 로그를 남긴다(`AdminAuditAction`에 행동 추가 필요). 사용자가 쓴 글 · 닉네임은 화면이 `textContent`로만 그리지만 서버도 HTML을 걸러서 저장한다.

| # | 메서드 · 경로 | 용도 |
| --- | --- | --- |
| A-16 | `GET /admin/dashboard` | 대시보드 (처리할 일 건수 · 최근 7일 일별 가입 · 체험 · 최근 관리자 활동 5건) |
| A-17 | `GET /admin/members?keyword&status` | 회원 목록 (닉네임 · 이메일 검색, `status`: `ACTIVE` · `SUSPENDED` · `WITHDRAWN`) |
| A-18 | `GET /admin/members/{memberId}` | 회원 상세 (가입 경로 · 체험 수 · 신고 받은 횟수 · 정지 정보 · 최근 게시글) |
| A-19 | `POST /admin/members/{memberId}/suspend` | 회원 정지. 본문 `{ "reason": "…(1~200자)", "days": 7 }`, `days`가 null이면 직접 해제할 때까지 |
| A-20 | `POST /admin/members/{memberId}/restore` | 정지 해제 |
| A-21 | `GET /admin/posts?keyword&status&reported` | 게시글 목록 (`status`: `VISIBLE` · `HIDDEN` · `DELETED`, `reported=true`면 신고된 글만) |
| A-22 | `GET /admin/posts/{postId}` | 게시글 상세 (본문 · 댓글 포함) |
| A-23 | `POST /admin/posts/{postId}/hide` | 숨김 `{ "reason" }` |
| A-24 | `POST /admin/posts/{postId}/restore` | 숨김 복구 |
| A-25 | `POST /admin/posts/{postId}/delete` | 삭제(소프트 삭제, 되돌릴 수 없음) `{ "reason" }` |
| A-26 | `POST /admin/comments/{commentId}/hide` | 댓글 숨김 |
| A-27 | `POST /admin/comments/{commentId}/restore` | 댓글 숨김 복구 |
| A-28 | `GET /admin/reports?status&targetType` | 신고 목록 (`status`: `PENDING` · `RESOLVED` · `REJECTED`, `targetType`: `POST` · `COMMENT` · `MEMBER`) |
| A-29 | `POST /admin/reports/{reportId}/resolve` | 신고 처리 `{ "action": "HIDE" \| "SUSPEND" \| "WARN", "note": "…(1~300자)" }`. `HIDE`는 게시글 · 댓글, `SUSPEND`는 회원 신고에만. 대상에도 반영한다 |
| A-30 | `POST /admin/reports/{reportId}/reject` | 신고 기각 `{ "note" }` |
| A-31 | `GET /admin/accounts` | 관리자 계정 목록 (`role`: `SUPER` · `REVIEWER`) |
| A-32 | `POST /admin/accounts` | 관리자 추가 `{ "email", "displayName", "role" }`. 비밀번호는 받지 않는다(초대 절차는 서버가 정한다) |
| A-33 | `POST /admin/accounts/{adminId}/activate` | 계정 다시 사용 |
| A-34 | `POST /admin/accounts/{adminId}/deactivate` | 계정 비활성화 (로그인 중이면 다음 요청부터 401, BE-67과 같은 규칙) |
| A-35 | `GET /admin/audit-logs?action` | 감사 로그 열람 (기존 `admin_audit_log`) |
| A-36 | `GET /admin/notices` | 공지 목록 (고정 → 최신 순) |
| A-37 | `POST /admin/notices` | 공지 등록 `{ "title", "body", "pinned", "published" }` |
| A-38 | `PATCH /admin/notices/{noticeId}` | 공지 수정 (바뀐 필드만: `title` · `body` · `pinned` · `published`) |
| A-39 | `GET /admin/banned-words` | 금칙어 목록 |
| A-40 | `POST /admin/banned-words` | 금칙어 추가 `{ "word" }` (30자 이하) |
| A-41 | `DELETE /admin/banned-words/{word}` | 금칙어 삭제 |

- **상태 충돌(409 `INVALID_STATE`)**: `details[0].reason`에 `ALREADY_SUSPENDED` · `MEMBER_WITHDRAWN` · `NOT_SUSPENDED` · `NOT_VISIBLE` · `NOT_HIDDEN` · `ALREADY_DELETED` · `ALREADY_HANDLED` · `SELF_DEACTIVATE`(자기 계정 비활성화 금지)를 쓴다.
- **그 밖 에러**: 404 `MEMBER_NOT_FOUND` · `POST_NOT_FOUND` · `COMMENT_NOT_FOUND` · `REPORT_NOT_FOUND` · `ADMIN_NOT_FOUND` · `NOTICE_NOT_FOUND` · `WORD_NOT_FOUND`, 409 `DUPLICATE_EMAIL` · `DUPLICATE_WORD`, 400 `VALIDATION_ERROR`.
- **권한(미정)**: 화면은 `SUPER` · `REVIEWER`를 구분해 보여 주기만 한다. 메뉴별 접근 제한(예: 관리자 계정은 `SUPER`만)은 서버 구현 때 정하고, 정하면 `GET /admin/auth/me`가 `role`을 내려 줘야 한다.
- 회원 이메일은 화면에 그대로 보인다. 서버가 마스킹해서 내릴지는 개인정보 방침과 함께 정한다.

## 9. (확장) 성향 API (7.6.4)

> 판결 성향 테스트(S-15 · S-16) · 공유 결과 · 마이페이지(S-17)용 API다. 테이블은 [ERD 8장](erd.md)(V15, #124), 채점 · 체험 성향 · 종합은 [성향 산식](personality-scoring.md), 문구는 [성향 테스트 콘텐츠](personality-test-content.md)를 따른다. 9-2 확인 항목은 구현 전에 A와 맞춘다.

### 9-1. 공통

- **익명 ID 기준**(1-2). 응답 제출(P-2)만 쿠키가 없으면 익명 ID를 새로 발급한다. 조회(P-3 · P-5)는 쿠키가 없으면 "결과 없음"으로 답한다.
- **4글자 코드(INTP 등) · 축 글자(E · I 등)는 어떤 응답에도 넣지 않는다**(상표 우려, #119). 유형은 `shareId`(영숫자 6 ~ 16자, 대소문자 구분, 시드로 고정)로 구분한다. 공유 주소는 `/test/types/{shareId}`.
- 축 키는 `factor.value_axis`와 같은 네 값(`APOLOGY_SINCERITY` · `FAULT_STANDARD` · `PRINCIPLE_RELATION` · `ORDER_OPPORTUNITY`)이다. 축 이름 · 양쪽 성향 이름(행동증명형 · 마음신뢰형 등)은 화면 상수로 둔다.
- `axisScores` 값은 **0 ~ 1의 관대 쪽 비중**이다(성향 산식 2-3 · 3-3). 엄격 쪽 비중은 `1 − 값`.

**유형 공통 형식** (P-2 ~ P-5의 `type`)

json

```json
{  "shareId": "Qm7xK2pa",  "name": "그럴 수도 있지형",  "character": "NAERO",  "tolerance": 4,  "oneLiner": "…",  "meme": "\"그럴 수도 있지\"",  "dailyDescription": "…",  "verdictDescription": "…",  "imageUrl": "/images/characters/personality/16.webp"}
```

| 필드 | 설명 |
| --- | --- |
| `character` | `NAERO`(내로) · `NAMBUL`(남불). 화면 이름은 화면 상수 |
| `tolerance` | 관용 지수 0 ~ 4 (높을수록 좋은 것이 아니다, FR-8-4) |
| `dailyDescription` · `verdictDescription` | ① 일상 가치관 · ② 판결 연계 설명 |
| `imageUrl` | 캐릭터 이미지 경로 `/images/characters/personality/{이미지 번호}.webp`(9-2 #4). 시드가 16유형 모두 채운다(v0.28). 파일을 불러오지 못하면 화면은 대체 이미지 |

**결과 공통 형식** (P-2 · P-3 · P-5의 내 결과)

json

```json
{  "type": { "shareId": "Qm7xK2pa", "name": "그럴 수도 있지형", "…": "…" },  "axisScores": { "APOLOGY_SINCERITY": 0.75, "FAULT_STANDARD": 0.625, "PRINCIPLE_RELATION": 1.0, "ORDER_OPPORTUNITY": 0.5 },  "testedAt": "2026-10-14T14:00:00+09:00"}
```

| # | 메서드 | 경로 | 설명 | 화면 |
| --- | --- | --- | --- | --- |
| P-1 | GET | `/personality/questions` | 문항 목록 | S-15 |
| P-2 | POST | `/personality/results` | 응답 제출 → 결과 저장 | S-15 → S-16 |
| P-3 | GET | `/personality/results/me` | 내 현재 결과 | S-16 |
| P-4 | GET | `/personality/types/{shareId}` | 공유용 유형 설명 (공개) | 공유 결과 |
| P-5 | GET | `/my-page` | 마이페이지 (테스트 결과 · 판결 성향 · 체험 목록) | S-17 |

---

### P-1. 문항 목록 — `GET /personality/questions`

**응답 200**

json

```json
{  "questions": [    { "id": 1, "scenario": "FRIEND", "content": "약속을 세 번이나 어긴 친구가 이번엔 진심으로 미안하다며 고개를 숙인다.", "optionA": "다음 약속을 지키는 걸 보고 나서 마음을 풀겠다", "optionB": "진심이 느껴지니 그 말을 믿고 넘어가겠다" }  ]}
```

- 활성 문항(`is_active`)만, 출제 순서(`display_order`)대로 준다.
- 문항마다 5칸(매우 A · 조금 A · 보통 · 조금 B · 매우 B)이고 "매우 · 조금 · 보통"은 화면이 붙인다. **축 · 글자 · 점수는 내려 주지 않는다**(채점은 서버, 9-1).
- `scenario`: `FRIEND` · `FAMILY` · `WORK` · `NEIGHBOR` (화면 칩 표시용).

---

### P-2. 응답 제출 — `POST /personality/results`

**요청**

json

```json
{  "answers": [    { "questionId": 1, "value": 0 },    { "questionId": 2, "value": 3 }  ]}
```

- `value`: 0 매우 A · 1 조금 A · 2 보통 · 3 조금 B · 4 매우 B. 서버는 그 문항의 `display_order = value + 1`인 선택지로 바꿔 채점한다(성향 산식 2장).

**응답 201**: 결과 공통 형식(9-1).

- 쿠키가 없거나 DB에 없는 익명 ID면 `anonymous_user`를 만들고 `Set-Cookie`로 내려준다(API 2와 같음). **요청 검증을 통과한 뒤에만** 발급한다. 400이면 사용자를 만들지 않고 쿠키도 내려 주지 않는다.
- **재응시**: 이전 현재 결과는 이력으로 남기고 새 결과가 현재 결과가 된다(성향 산식 5장). 같은 사용자의 제출이 동시에 와도 차례로 처리되고, 나중에 처리된 것이 현재 결과다.
- 저장하는 것은 유형 · 축 점수 · 답(`questionId` · 선택지 ID)이다. 응답에는 답을 다시 넣지 않는다.

| 거절 | 조건 |
| --- | --- |
| 400 `VALIDATION_ERROR` | `answers` 없음, 활성 문항 중 빠진 문항 · 같은 문항 두 번, 없거나 비활성인 `questionId`, `value`가 0 ~ 4 정수가 아님(소수는 1-1 숫자 규칙대로 거절) |

---

### P-3. 내 현재 결과 — `GET /personality/results/me`

**응답 200**: 결과 공통 형식(9-1).

| 거절 | 조건 |
| --- | --- |
| 404 `PERSONALITY_RESULT_NOT_FOUND` | 테스트한 적 없음 (쿠키 없음 포함) → 화면은 S-15 안내 |

---

### P-4. 공유용 유형 설명 — `GET /personality/types/{shareId}`

**응답 200**: 유형 공통 형식(9-1)만. **개인 응답 · 축 점수 · 체험 기록은 넣지 않는다**(FR-8-6, REQ-116). 쿠키를 읽지 않는다.

| 거절 | 조건 |
| --- | --- |
| 404 `PERSONALITY_TYPE_NOT_FOUND` | 없는 `shareId`. 형식이 맞지 않는 값(영숫자 6 ~ 16자가 아님)도 400이 아니라 404로 같게 답한다 |

---

### P-5. 마이페이지 — `GET /my-page`

**응답 200**

json

```json
{  "personality": { "type": { "shareId": "Qm7xK2pa", "…": "…" }, "axisScores": { "…": "…" }, "testedAt": "2026-10-14T14:00:00+09:00" },  "judgmentPersonality": {    "axisScores": { "APOLOGY_SINCERITY": 0.75, "FAULT_STANDARD": 0.11, "PRINCIPLE_RELATION": 0.83, "ORDER_OPPORTUNITY": null },    "type": null,    "experienceCount": 3  },  "history": [    { "caseId": 1, "title": "빌린 돈 문제로 찾아온 지인을 살해한 사건", "completedAt": "2026-10-14T15:00:00+09:00", "axisScores": { "APOLOGY_SINCERITY": 1.0, "FAULT_STANDARD": 0.0, "PRINCIPLE_RELATION": 1.0, "ORDER_OPPORTUNITY": null } }  ],  "experiences": [    { "caseId": 1, "title": "빌린 돈 문제로 찾아온 지인을 살해한 사건", "crimeType": "MURDER", "status": "COMPLETED", "attemptNo": 1, "updatedAt": "2026-10-14T15:00:00+09:00" }  ]}
```

| 필드 | 설명 |
| --- | --- |
| `personality` | 테스트 현재 결과(결과 공통 형식). 테스트 전이면 `null` → 화면은 테스트 안내. **대표 캐릭터는 이 유형**(성향 산식 4장) |
| `judgmentPersonality` | 종합 판결 성향(성향 산식 4장). 각 사건의 **첫 회차**만, 축마다 체험별 축 점수 평균. 값이 없는 축은 `null`("체험 자료 없음"). `type`은 **네 축 모두 값이 있을 때만** 있고 아니면 `null`. 값이 있으면 **유형 공통 형식(9-1) 그대로**다(`shareId`로 그 유형의 공유 링크로 이어 갈 수 있다). 화면은 "판결에서는 ○○○○"처럼 테스트 유형과 나란히 보여 준다. 완료한 체험이 없거나 네 축 모두 `null`이면 `judgmentPersonality` 자체가 `null`("종합 없음"). `experienceCount`는 **성향 기여(`experience_personality`)가 저장된 첫 회차 체험 수**다. 종합 점수를 낸 체험과 같은 집합이고, 축 자료가 모두 비어 있는 체험도 센다. 7.6.7(체험 완료 시 성향 저장) 전에 완료해 기여가 없는 체험은 세지 않는다 |
| `history` | 성향 변화 흐름. 각 사건의 첫 회차 체험별 축 점수(값이 없는 축은 `null`), 완료 순 |
| `experiences` | 체험한 사건. 사건마다 최근 회차 하나, 최근 순. `status`로 결과 다시 보기(`COMPLETED`) · 이어 하기를 나눈다(1-6) |

- 쿠키가 없거나 기록이 없으면 `404`가 아니라 **빈 마이페이지**(`personality`·`judgmentPersonality` `null`, 목록 빈 배열)를 `200`으로 준다. 마이페이지는 "아직 없음"을 안내하는 화면이기 때문이다.
- `judgmentPersonality` · `history`는 체험 성향 저장(7.6.7, BE-64)이 붙은 뒤부터 채워진다. 그 전에는 각각 `null` · 빈 배열이다.
- 판결 체험 중(S-03 ~ S-08)에 성향을 보여 주지 않는 규칙(FR-8-5)은 화면이 지킨다. 이 API는 마이페이지에서만 부른다.

---

### 9-2. 성향 API 확인 항목

| # | 항목 | 이 계약의 제안 | 확인할 사람 |
| --- | --- | --- | --- |
| 1 | 목 API와 달라지는 점 | 응답에서 `typeCode` 제거(`type.shareId`로 구분), 문항의 `a` · `b`(글자 포함) → `optionA` · `optionB`(문장만), 유형의 `daily` · `verdict` → `dailyDescription` · `verdictDescription`, `character`는 `NAERO` · `NAMBUL` 코드, 결과에 `testedAt` 추가, 내 결과 없음은 `null` 대신 404 | A 김해송 (목 → 실제 API 전환) |
| 2 | 마이페이지 경로 | `GET /my-page` 하나로 묶는다(화면 한 번 진입에 한 번 호출) | A |
| 3 | `experiences` 범위 | 사건마다 최근 회차 하나. 회차별 전체 이력이 필요하면 추가 | A |
| 4 | 이미지 경로 | `/images/characters/personality/{이미지 번호}.webp`, 이미지 번호(`01 ~ 32`)는 성향 이미지 번호표 기준으로 **유형마다 화면에 보이는 이미지 하나**(기본 캐릭터, 예: ISTJ는 남불 `18.webp`). **한 번 정한 번호는 바꾸지 않는다**(공유 미리보기 · 캐시가 깨지지 않게, #121 리뷰) | A |
| 5 | `shareId` 값 | 16개를 시드에 고정값으로 넣는다(바꾸면 공유한 링크가 깨진다). 목 API의 해시 값과는 다르다. v0.28: OG 빌드용 값(shareId · 유형 번호 · 이미지 번호 · 유형명 · `oneLiner`)은 `frontend/og/personality-types.json`에 두고, 시드와 같은지 백엔드 테스트(`PersonalityContentSeedTest`)가 확인한다(BE-73) | B (시드) |
