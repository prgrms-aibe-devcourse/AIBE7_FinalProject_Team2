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
| v0.35 | 2026-10-20 | BE-77 — API 14 `preToCourt.direction` 추가(재판부 기준, SAME은 동일 형벌 종류·구간). API 2·3은 기존 5개 상태 필드를 유지하고 API 3은 사전 판단을 반환하지 않음을 명시. 기존 체험을 보존하는 읽기 전용 1막부터 다시 보기 유지(진행 중 사전 형량 비노출·완료 후 API 14 표시) 및 완료 후 독자 의견 공개 원칙 반영 (PR #198·#203) |
| v0.36 | 2026-10-20 | 사건 사진 프롬프트 추천 · 업로드 API 신설 (BE-91, COMMON-41 재검토) — Cloudflare Workers AI 직접 생성은 그림 품질 때문에 그만두고, A-13-1(대표사진 프롬프트 추천) · A-13-2(발단 사진 프롬프트 추천) · A-13-3(대표사진 업로드) · A-13-4(발단 사진 업로드) · A-13-5(발단 사진 목록) 추가. 번호는 A-16 ~ A-41이 8-4(운영 메뉴 초안)에 이미 예약돼 있어 A-13-N으로 둠. 에러 코드 `PROMPT_GENERATION_FAILED` · `FILE_UPLOAD_FAILED` · `INVALID_IMAGE_FILE` 추가 |
| v0.37 | 2026-10-20 | 신문 지면 콘텐츠 생성 API 신설 (BE-92, COMMON-41) — A-13-6(생성) · A-13-7(조회) · A-13-8(수정) 추가. `photo`는 `legal_case.thumbnail_url`, `extraPhotos`는 A-13-5(발단 사진)를 그대로 재사용(새로 저장하지 않음, `docs/decisions/BE-92.md`). 에러 코드 `PAPER_NOT_FOUND` 추가(1-5) |
| v0.38 | 2026-10-21 | **BE-82 화면 ID 재배치 (S-05 ~ S-09 뜻 변경)** — 6막 재구성(FE-48)에 맞춰 화면 ID를 막 순서로 다시 매김: S-05 내 판결(옛 S-06) · S-06 AI 판결(옛 S-07) · S-07 실제 판결(옛 S-08) · S-08 세 판결 비교(옛 S-09) · **S-09 리뷰 페이지(새로 추가, `COMPLETED`만)**. 정리 화면(옛 S-05 `/summary`)은 없앰. 1-6 `REVIEWED` 보낼 화면 → S-05(판결 입력), 2장 API 6 화면 S-04만, 3장 API 6 · 5장 갱신. 서버 상태 값 · API 경로 · 응답 형식 변경 없음. 이 버전 전의 이력 행은 옛 ID 그대로다(`docs/decisions/BE-82.md` 매핑 표 참고) S-09는 진입 API가 없어 예외적으로 API 3으로 `COMPLETED`를 확인(5장, PR #214 리뷰) |
| v0.39 | 2026-10-21 | 8-3 #7 관리자 OAuth(BE-40) **제외** — 관리자 로그인은 이메일 · 비밀번호(세션, BE-39 · BE-67 · BE-68)로 유지한다(B 김현중 확인). 일반 사용자는 카카오 로그인(BE-62) |
| v0.40 | 2026-10-21 | API 6 `summary`를 S-05 사건 요약 장에서 쓴다고 명시(FE-58). 형식 변경 없음 |
| v0.41 | 2026-10-21 | **출석 기록 · 출석 일수 추가 (BE-97)** — P-6 `POST /attendance`(쿠키가 있을 때만 하루 한 번 기록, 항상 `204`, 익명 ID를 발급하지 않음) 신설, P-5 응답에 `profile.attendanceDays`(누적 출석 일수, 없으면 `0`) 추가. 날짜는 `Asia/Seoul` · 하루 시작 시각 설정 기준(`docs/decisions/BE-97.md`) |
| v0.42 | 2026-10-21 | **프로필 꾸미기 저장 · 도전과제 판정 추가 (BE-96, FE-68)** — P-7 `PUT /profile-deco`(꾸미기 전체 저장, 형식 · 값 목록 400 → 잠김 409 `DECO_LOCKED`) 신설, P-5 응답에 `profileDeco` · `achievements` · `unlocked` 추가(도전과제 · 보상 표는 서버가 원본, 수집 도전과제는 BE-83 전까지 `PENDING`), 1-5에 `DECO_LOCKED` 추가(`docs/decisions/BE-96.md`) |
| v0.43 | 2026-10-21 | **BE-96 · BE-97 후속 (BE-98, FE-71 리뷰)** — P-7 잠김 검사가 저장값과 같은 칸(저장할 때도 쓰이던 칸)은 다시 거절하지 않도록 보완(표가 바뀌어 저장값이 잠겨도 다른 칸 저장 가능). 익명 ID를 새로 발급하는 요청(API 2 · P-2 · P-7)은 그날 출석도 함께 남긴다(첫 방문날 누락 방지). P-6 · P-7 설명 갱신 |
| v0.44 | 2026-10-21 | **P-5 `judgmentPersonality`를 현재 판결 성향으로 변경 (FE-70)** — 첫 회차 체험 **전체 평균**에서 **최근 첫 회차 체험 3건의 평균**으로 바꾼다(`type` · `null` 규칙은 그대로, `experienceCount`는 평균에 쓴 체험 수 = 최대 3). 마이페이지 성향카드 화면이 "지금"을 이 값으로 보여 준다. 서버 구현(`MyPageService`)은 후속 백엔드 작업 |
| v0.45 | 2026-10-21 | 10장 카카오 로그인 추가 (BE-62, WBS 7.4.2) — M-3 시작 `GET /api/v1/auth/oauth2/authorization/kakao` · M-4 콜백 `GET /api/v1/auth/oauth2/callback/kakao`(브라우저 이동, JSON API 아님). 성공 시 리프레시 토큰 쿠키(`NLNB_RT`)만 심고 화면 경로로 리다이렉트, 토큰은 URL에 싣지 않음. 익명 ID 쿠키 연결 규칙. **10장 번호를 M-N으로 변경**: 재발급 · 로그아웃(BE-94)의 A-16 · A-17이 8-4 운영 메뉴 API 예약 번호(A-16 ~ A-41)와 겹쳐 M-1 · M-2로 |
| v0.46 | 2026-10-21 | **사건 등록 A-14 · A-15 서버 구현 (BE-93 1차)** — "초안" 표시를 떼고 담당을 BE-93으로. **A-14 머리말의 "파이프라인을 자바로 옮긴 뒤 구현한다"는 옛 결정이라 정정**: 파이프라인은 옮기지 않고 같은 서버의 내부 컨테이너로 띄워 백엔드가 내부망으로 부른다(COMMON-50). 에러 코드 `DUPLICATE_SOURCE` · `IMPORT_IN_PROGRESS` · `IMPORT_NOT_FOUND` · `IMPORT_UNAVAILABLE`(신설)을 1-5에 추가. **적재는 BE-93 2차**라 그때까지 A-15는 `SUCCEEDED`여도 `caseId`가 `null`임을 명시(차수별 표로 구분). **A-14의 "파이프라인이 적재까지 한다"도 정정**: 파이프라인의 `load` 단계는 운영 DB에 직접 쓰는 단계라 서비스에서 영구히 막아 두었고(COMMON-50), 1차 · 2차 모두 `SKIPPED`다 — 사건을 만드는 일은 서버가 한다. 항상 `SKIPPED`인 세 단계(`load` · `rag` · `contamination`)가 오류가 아님을 A-15에 명시. 요청 · 응답 형식은 바뀌지 않았다(화면 목 `adminMockApi.js` 그대로) |
| v0.47 | 2026-10-21 | Rate limit 2차 (BE-63, WBS 7.4.6) — 1-5 `TOO_MANY_REQUESTS`에 **전용 제한 3곳** 추가: M-1 재발급 · M-2 로그아웃(IP별 합쳐서 분당 30번 · 순간 30번), A-2 관리자 로그인(IP별 분당 5번 · 순간 5번), M-3 카카오 로그인 시작(BE-62, 이미 적용). `/api` 공통 제한에 더해 걸리고(전용 제한은 POST만 센다) 응답 형식은 같다. `Retry-After`는 경로별(공통 1 · 재발급 · 로그아웃 · 카카오 시작 2 · 관리자 로그인 12초). 재발급이 429여도 로그아웃된 것이 아니다(401만 로그인 종료). 형식 변경 없음 |
| v0.48 | 2026-10-22 | **발단 · 결론 사진 역할 칸 1단계 (BE-78 2차 검토, BE-101)** — A-13-4(사진 업로드)에 `role`(`ORIGIN` · `CONCLUSION`) 필수 필드 추가, 같은 역할로 다시 올리면 기존 사진을 교체(응답은 그대로 200이 아니라 201, id는 유지). A-13-5(목록) 응답에 `role` 추가(1단계 이전 사진은 `null`). **A-13-9(발단 · 결론 사진 삭제) 신설** — `DELETE /admin/cases/{caseId}/photos/incident/{role}`. 에러 코드 `INCIDENT_PHOTO_NOT_FOUND` 추가(1-5). A-13-6 ~ 8(신문 지면)의 `extraPhotos`는 `role`이 있는 사진만 발단 → 결론 순으로 내려준다(`display_order` 약속은 쓰지 않는다, `docs/decisions/BE-78.md`). **PR #243 리뷰 반영**: A-13-5의 "role 없는 행은 A-13-9로 지운다"는 설명이 틀려 있어 정정(A-13-9는 `{role}`로만 찾으므로 지울 수 없음), A-13-6 ~ 8의 `extraPhotos` 정렬이 "1단계는 `display_order`"임을 명시(`docs/decisions/BE-101.md` 참고). **(병합 메모) 이 PR 작업 중 BE-63 PR #236이 먼저 develop에 머지되며 v0.47 번호를 먼저 썼다 — 원래 v0.47로 적었던 걸 v0.48로 다시 매겼다(BE-101.md의 V26 마이그레이션 번호 충돌과 같은 상황)** |
| v0.49 | 2026-10-22 | **P-5 `history[]`에 사건별 유형 `type` 추가 (BE-100, FE-75 리뷰 후속)** — 각 줄에 그 체험 한 건의 유형(유형 공통 형식 9-1)을 넣는다. 규칙은 `judgmentPersonality.type`과 같다(그 체험 하나만으로 낸 종합의 유형: 네 축 모두 값이 있을 때만, 글자는 정확한 분수의 부호 · 동점은 관대 글자). 없으면 `null`. 화면(내 결과 지면)이 축 점수로 유형을 직접 계산하고 P-4로 다시 받아 오던 경로를 없앤다(`docs/decisions/BE-100.md`) |
| v0.50 | 2026-10-22 | **최초 성향 · 성향카드 다시 정하기 추가 (FE-77)** — 화면 이름은 초심(처음 성향) · 재심(다시 정하기) · 최근(현재 판결 성향). 결과 공통 형식에 `completedExperienceCount`(응시 시점에 완료한 사건 수) 추가. P-2 요청에 `baseline`(성향카드 다시 정하기, 사용자당 1회, 이미 썼으면 409 `BASELINE_RETAKE_USED`) 추가. 일반 응시는 그대로 횟수 제한 없음. P-5 응답에 `initialPersonality`(처음 성향: 첫 테스트, 테스트 전이면 첫 판결 체험 3건) · `baselineRetake`(다시 정한 결과 · 가능 여부) 추가. 1-5에 `BASELINE_RETAKE_USED` 추가. 서버 구현은 후속 백엔드 작업 |
| v0.51 | 2026-10-22 | **사건 등록 적재 A-14-1 신설 (BE-93 2차)** — `POST /admin/case-imports/{importId}/load`. 생성 결과를 사건(DRAFT) · 섹션 · 형벌 규칙 · 판단 요소 · 원본 판결문 · AI · 재판부 판결 후보로 만든다. **A-15가 SUCCEEDED가 된 뒤 관리자가 따로 누른다**(번호는 A-16 ~ A-41 예약 때문에 A-14-1) — 2초마다 불리는 조회에서 적재하면 같은 사건이 여러 번 만들어질 수 있다. 적재가 끝나면 A-15의 `caseId`가 채워진다(1차에 비워 두었던 값). 에러 코드 `IMPORT_NOT_READY`(409) · `IMPORT_RESULT_INVALID`(422) 추가. 판결 후보는 **공개하지 않은 상태**로 들어가 A-9 승인을 기다린다. **(병합 메모) 이 줄의 번호는 PR #242 리베이스 도중 두 번 겹쳤다 — 처음 v0.47(BE-63과 겹침) → v0.50(COMMON-60) → 이번에 FE-77과 다시 겹쳐 v0.51로 재조정(COMMON-58, PR #244)** |
| v0.52 | 2026-10-22 | **프로필 선택 설정 저장 P-8 신설 (BE-104, FE-77 후속)** — `PATCH /profile-settings`: 공개 성향카드(`shownCard`) · 프로필카드 아래 성향(`profileCardType`) · 한 줄 소개(`bio`)를 **보낸 칸만** 저장(P-7과 별개, 전체 덮어쓰기 아님). 값은 `initial` · `retake` · `current`, 소개는 앞뒤 공백을 뺀 0 ~ 40자 · 줄바꿈 없음. P-5 응답에 `profileSettings` 추가(저장 전이면 `current` · `current` · `""`). 공개 범위 칸은 두지 않는다(항상 공개, FE-77). ERD v1.37 `profile_setting`(V29). P-2 · P-5의 v0.50 서버 구현도 이 작업에서 했다(V28) **(병합 메모) 작업 중 COMMON-58(PR #244)이 BE-93 줄에 v0.51을 먼저 써서 v0.51 → v0.52로 다시 매겼다** |
| v0.53 | 2026-10-22 | **API 17 공개 신문 지면 신설 (BE-102, BE-78 2차 검토 후속)** — `GET /cases/{caseId}/paper`. 관리자 신문 지면(A-13-6 ~ 8, BE-92)과 발단 · 결론 사진(A-13-4 · 5, BE-91 · BE-101)을 공개용으로 조립, 새로 저장하지 않는다. **`extraPhotos`는 A-13-6 ~ 8과 달리 배열이 아니라 `{ origin, conclusion }` 고정 자리** — 사진 한쪽이 없을 때 다른 쪽이 앞자리로 밀려 역할이 바뀌어 보이는 걸 막는다(FE-64 선행, BE-102 Jira 댓글로 제안 · 협의). 비공개 사건은 `CASE_NOT_FOUND`, 신문 지면 미생성은 `PAPER_NOT_FOUND`(1-5 설명을 공개 API도 쓰도록 정정) |
| v0.54 | 2026-10-27 | **레벨 · 직업 API 신설 (BE-106)** — 11장 L-1 `GET /level`(레벨 · XP · 직업 목록) · L-2 `PUT /level/job`(카드에 보여 줄 직업 선택) · L-3 `POST /level/share-reward`(SNS 공유 보상). P-5 응답에 `level`(L-1과 같은 값) 추가, 1-5에 `JOB_LOCKED`(409) 추가. 레벨은 익명 ID 단위(회원 합산은 후속). 경험치 산식 · 직업 11등급 · 지급 상한은 11장과 [BE-106 결정 기록](decisions/BE-106.md) |
| v0.55 | 2026-10-27 | 범죄 유형 강도 · 절도 추가 (BE-112 · ERD v1.40 · V31) — `crimeType` 값에 `ROBBERY`(강도) · `THEFT`(절도) 추가(API 1 쿼리 · 응답, 사건 등록). 분류명(`crimeCategoryLabel`)은 둘 다 "재산범죄". API 1 `summary.byCrimeType`은 공개 사건이 없어도 다섯 키를 모두 0으로 내려 준다. API 4 `rangeOptions`: 강도는 벌금형을 빼고 무기징역을 더한 7개, 절도는 공통 7개 |
| v0.56 | 2026-10-27 | API 1 `crimeCategoryLabel` 설명에 범죄 유형별 분류명 대응을 모두 적음(생명 · 재산 · 신체범죄, 강도 · 절도는 재산범죄) (BE-111, BE-112 #297 리뷰 요청) |
| v0.57 | 2026-10-27 | **사건 대본 검수 A-13-10 ~ A-13-12 신설 (BE-108)** — `GET /admin/cases/{caseId}/script` · `POST .../script/approve` · `POST .../script/reject`. 적재(A-14-1)가 넣은 증거조사 대본을 관리자가 보고 승인해야 공개된다(공개 서빙은 BE-109). **번호는 운영 메뉴 A-16 ~ A-41 예약 때문에 A-13 하위로 매겼다** — A-14-1과 같은 이유다. 검수 칸(`reviewStatus` · `isPublished` · `reviewedBy` · `reviewedAt` · `reviewNote`)은 판결 후보 A-8과 같은 이름 · 같은 형식이고, 대사마다 판단 요소 문구 `label`을 서버가 붙여 준다. 대사 한 줄 직접 수정 A-13-13(`PATCH .../script/evidence/{factorId}`)도 함께 — 고친 줄은 `validation.manual`로 표시하고 검수 상태를 대기로 되돌린다(해송님 요청). **A-6 응답에 `script` 요약(`exists` · `reviewStatus` · `publishable`) 추가** — 미승인 대본은 공개를 막지 않으므로 `publishReadiness.reasons`가 아니라 별도 칸으로 내려 공개 준비 화면이 경고로 쓴다. 에러 코드 `SCRIPT_NOT_FOUND` · `SCRIPT_LINE_NOT_FOUND`(404) 추가. ERD v1.41 `case_script`(V32) **(병합 메모) 번호를 두 번 옮겼다 — BE-106(V30 · v0.54 · v1.39)과 BE-112(V31 · v0.55 · v1.40) · BE-111(v0.56)이 먼저 머지돼, 이 작업은 V32 · v0.57 · v1.41로 다시 매겼다. 먼저 머지된 쪽이 번호를 갖는다** |
| v0.58 | 2026-10-27 | **아이디 회원가입 M-5 · 아이디 로그인 M-6 신설 (BE-113 · FE-117, ERD v1.42 · V33)** — `POST /auth/signup` · `POST /auth/login`. 둘 다 성공하면 리프레시 토큰 쿠키(`NLNB_RT`)만 심고 `{ nickname }`을 돌려주며, 화면은 M-1 재발급으로 액세스 토큰을 받는다(카카오 로그인과 같은 흐름). Origin 검증 대상에 추가(로그인 CSRF 방지). 1-5에 409 `DUPLICATE_ID` 추가, `INVALID_CREDENTIALS`를 회원 로그인에도 쓴다, `TOO_MANY_REQUESTS` 전용 제한에 M-5 · M-6 추가(`Retry-After` 6 · 3초). 관리자 로그인(A-2)은 바뀌지 않는다 |
| v0.59 | 2026-10-27 | **API 18 공개 사건 대본 신설 (BE-109)** — `GET /cases/{caseId}/script`. A-13-10 ~ 12(BE-108)가 적재 · 검수한 대본에서 **승인된 것만** 공개용으로 가져다 준다(새로 저장하지 않음, API 17과 같은 원칙) — 2막 증거조사 대사(`factorId` · `side` · `character` · `card` · `text`)와 배역(`cast`)만 담는다. **나머지 막(1막 해설 · 공소사실 · 쟁점 정리 · 피고인신문 · 검사의견 · 최종변론 · 최후진술)은 이 API에 넣지 않는다** — 파이프라인이 만들지 않아 BE-107 후속 확장으로 분리했고, 그중 사건별 대사가 필요한 막은 당분간 "증거조사 중심 축약판"으로 둔다(FE-33 담당자 해송님 합의, BE-109 Jira 댓글). `side`는 A-13-10의 `role`을 대문자 코드값(`PROSECUTOR` · `DEFENSE`)으로 바꿔 내려준다 — 다른 공개 API와 형식을 맞추려는 것(화면 한글 변환은 프론트 몫). `card`는 A-13-10의 `label`과 같은 값(판단 요소 문구) — 파이프라인 사건에는 정적 증거 카드가 없어 이 문구가 카드 역할을 한다. 검수용 값(`manual` · `editedAt` · `validation` · `attempts`)은 내려주지 않는다. **배열 순서가 곧 재생 순서다**(별도 정렬 필드 없음). 비공개 사건은 `CASE_NOT_FOUND`, 대본이 없거나 아직 승인 전이면 `SCRIPT_NOT_FOUND`(API 17과 같은 모양 — 빈 배열 대신 404를 쓰는 이유는 "대본 없음"과 "대사 0줄"을 구분하기 위해서다). **(병합 메모) BE-113(PR #306 · v0.58)이 먼저 develop에 머지돼, 이 작업은 v0.59로 다시 매겼다** |
| v0.60 | 2026-10-28 | **API 1 · API 16에 `scriptReady` 추가 (BE-116)** — 그 사건에 **승인된 공개 대본**(API 18이 200을 주는 조건)이 있는가. 목록이 사건마다 API 18을 부르지(N번 요청) 않고 "체험 가능 / 준비 중"을 알 수 있다. 필드 추가만이라 기존 클라이언트와 호환 (`docs/decisions/BE-116.md`) |
| v0.61 | 2026-10-28 | **사건 공개 취소 A-13-14 신설 (BE-118, ERD v1.44 · V34)** — `POST /admin/cases/{caseId}/unpublish`, 사유(`reason`, 1 ~ 200자) 필수. 공개한 사건을 사용자 화면에서 **숨긴다**(`PUBLISHED → REVIEW`, `published_at` 지움). 사건 · 판결 · 사용자 체험 기록은 지우지 않고, 사용자 API는 `PUBLISHED`만 읽으므로 목록 · 상세 · 체험 · 결과에서 바로 사라진다. 고친 뒤 A-13으로 다시 공개한다. 공개 중이 아니면 409 `INVALID_STATE`(사유 코드 `CASE_NOT_PUBLISHED` 추가). 감사 로그 `CASE_UNPUBLISH`(사유 포함). 8-2 "공개 취소 없음" · 8-3 #2를 갱신. 권리 요청에 따른 삭제(파기)는 정책을 정한 뒤 2단계로 둔다 |
| v0.62 | 2026-10-28 | **공개 뒤 사진 교체 절차 확정 (BE-103, BE-78 2차 검토 후속 3번)** — 공개 중 사진만 바꾸는 경로는 **두지 않는다**. A-13-3 · A-13-4 · A-13-9의 409(`CASE_PUBLISHED`)를 그대로 두고, 고쳐야 하면 A-13-14(공개 취소) → 교체 → A-13(다시 공개)으로 한다(BE-118이 사유 필수 · 감사 로그를 갖춘 경로를 이미 제공). 공개가 곧 사진 승인이라는 BE-78 원칙과 8-3 #1(공개 뒤 수정 금지)을 함께 지키려는 선택이다. 절차의 대가(내린 동안 완료 체험 결과도 404, 다시 공개하면 `published_at`이 새로 기록돼 사건 목록 맨 위로)를 A-13-3에 적고 8-3 #9로 남겼다. **API · 스키마 · 운영 코드 변경 없음**(결정이 "현재 동작 유지"라 동작을 고정하는 테스트만 추가했다) (`docs/decisions/BE-103.md`). 같은 김에 A-13-3의 감사 로그 값 `CASE_PHOTO_GENERATE` → `CASE_PHOTO_UPLOAD` 정정(`AdminAuditAction`에 없는 값이었다) |
| v0.63 | 2026-10-29 | **수집 상태 저장 · 도전과제 해금 기록 추가 (BE-83, FE-132 · ERD V35)** — P-9 `POST /collection`(완료한 사건의 일러스트 · 의상 기록, 멱등, 백필 `backfill`) · P-10 `POST /collection/seen`(컬렉션 "봤음") · P-11 `POST /achievements/seen`(도전과제 "봤음") 신설. P-5 응답에 `collection`(모은 수 · 항목 · `isNew`) 추가, `achievements[]`에 `unlockedAt` · `isNew` 추가, **수집 도전과제 `PENDING` 해제**(`collected` = 일러스트 + 의상 수). 한 번 해금된 도전과제는 표가 바뀌어도 `CLEARED` 유지. `level` XP에 모은 항목 1장당 10 추가. 1-5에 409 `COLLECTION_LIMIT_EXCEEDED` 추가(`docs/decisions/BE-83.md`) |
| v0.64 | 2026-10-29 | **요청 오류 에러 코드 구분 (BE-132)** — 없는 주소 · 지원하지 않는 메서드 · Content-Type이 모두 400대 상태는 맞지만 `code`가 `VALIDATION_ERROR`("요청 값이 올바르지 않습니다.")로 내려오던 것을 나눈다: 404 `NOT_FOUND` · 405 `METHOD_NOT_ALLOWED` · 415 `UNSUPPORTED_MEDIA_TYPE`(1-5). HTTP 상태 코드 · 405 `Allow` 헤더는 그대로이고, 입력값 오류(400)와 도메인 404(`CASE_NOT_FOUND` 등)는 바뀌지 않는다 **(병합 메모) v0.63은 BE-83(PR #335)이 쓴다. 이 PR이 먼저 머지되면 번호를 다시 매긴다** |
| v0.65 | 2026-10-29 | **프로필카드 아래 줄 설정 추가 (BE-114, FE-116)** — P-8 요청 · 응답과 P-5 `profileSettings`에 `cardLine`(`type` · `signature` · `job` 중 하나, 기본 `type`: 프로필카드 아래에 보일 줄) · `cardLevel`(boolean, 기본 `false`: 레벨을 그 줄 윗줄에 붙일지) 두 칸 추가. 모르는 값 · 타입이 다르면 기존 칸과 같이 400 `VALIDATION_ERROR`. ERD v1.46 `profile_setting`(V36) |
| v0.66 | 2026-10-29 | **담당 문서 최신화 (B, 7.0.2 준비)** — 8장 머리말을 "계약 초안(v0.17)"에서 "구현됨(BE-41 ~ 43)"으로, 확정된 항목의 ⚠️ 정리(DRAFT → REVIEW는 8-3 #3, 공개 뒤 교체 · 수정 금지는 8-3 #1, `raw_output`은 8-3 #6), 8-3 #1을 ✅ 확정(공개 중 409, 고칠 때는 공개 취소 → 수정 → 다시 공개, BE-103과 같은 절차). 9장 머리말 · 9-2 각 항목을 구현(BE-72) · 화면 연동 기준으로 ✅ 확정. 형식 변경 없음. 8-2 제목의 "초안" 삭제(#334 리뷰) |
| v0.67 | 2026-10-29 | **사건 배역 · 민감 플래그 (BE-125, ERD v1.47 · V37)** — API 16에 `cast`(배역 `{prosecutor, defense}`) · `sensitiveFlags`(사용자 안내 대상 플래그만) 추가. 값이 없으면 **필드를 뺀다**(배정 전 · 안내할 플래그 없음 → 화면 기본 동작). A-6 `content`에 `castRoles` · `sensitiveFlags`(가드 전용 포함 전체), A-11에 같은 두 필드 수정 추가 — 자동 분류 · 자동 배정을 관리자가 바로잡는 경로. 감사 로그 `CASE_UPDATE`에 두 필드는 바꾼 값까지 남긴다. 필드 추가만이라 기존 클라이언트와 호환 (`docs/decisions/BE-125.md`) |
| v0.68 | 2026-10-29 | **커뮤니티 API 신설 (BE-115, FE-47 · FE-88 · FE-89 · FE-116)** — 12장 C-1 `GET /community/posts`(목록 · 많이 본 글) · C-2 `GET /community/posts/{postId}`(상세 + 댓글) · C-3 `POST /community/posts`(글쓰기, 판결 공유) · C-4 `POST …/like`(좋아요 켜고 끄기) · C-5 `POST …/comments`(댓글) · C-6 `GET /community/home`(이야기 많은 사건 · 추천 사건 · 레벨 랭킹) · C-7 `GET /community/shareable-verdict`(붙일 수 있는 내 판결). 읽기는 누구나, 쓰기는 로그인 회원만(401 `UNAUTHORIZED`). 글쓴이 명함에 `level` · `job` · `cardLine`. 글 · 댓글 · 좋아요 때 경험치 지급(11-1 그대로). 에러 코드 `POST_NOT_FOUND` 추가. 사진 올리기 · 글 삭제 · 닉네임 설정은 후속. ERD v1.48(V38) |
| v0.69 | 2026-10-29 | **닉네임 설정 M-7 · 내 회원 정보 M-8 신설 · 닉네임 중복 금지 (BE-139, ERD v1.49 · V39)** — `GET /members/me`(M-8, 닉네임 · 표시 이름 · 로그인 방식 · 아이디 · 가입일 · 최근 로그인) · `PUT /members/me/nickname`: 로그인한 회원(카카오 · 아이디 공통)이 닉네임을 정하거나 바꾼다. 형식은 M-5와 같고, **대소문자를 구분하지 않고 중복을 막는다**(409 `DUPLICATE_NICKNAME`). M-5 가입도 같은 중복 검사를 받는다(M-5의 "중복을 허용한다" 삭제). 변경 횟수 제한 · 금칙어 검사는 아직 없다. 닉네임이 없는 회원의 커뮤니티 이름은 `방청객 {회원 ID}`(BE-115). 에러 코드 `DUPLICATE_NICKNAME` 추가 |
| v0.70 | 2026-10-29 | **로그아웃 시 익명 ID 쿠키 해제 (BE-137)** — M-2: 쿠키의 익명 ID가 회원에 연결되어 있으면 `NLNB_AID`도 `Set-Cookie`(`Max-Age=0`)로 지운다. 공용 PC에서 로그아웃한 뒤 다음 사람의 체험 · 판결이 그 회원 계정으로 쌓이지 않게 한다. 새 익명 ID는 이때 발급하지 않고 다음 행동(API 2 체험 시작 등) 때 발급된다. 연결 안 된(손님) 익명 ID는 지우지 않는다 |
| v0.71 | 2026-10-29 | **사건 사진 주소 규칙 기본값 (BE-141)** — API 1 · 2 · 16 · 17과 커뮤니티 홈(C-6)의 `thumbnailUrl` · 사진 URL이 저장된 값이 없으면 규칙 주소 `/images/cases/case-{caseId}-{reenact\|origin\|conclusion}.webp`로 채워진다(저장된 값이 있으면 그것이 우선, 관리자 사진 업로드 A-13-3 ~ 5는 그대로). API 17 `extraPhotos.origin` · `conclusion`은 사진 행이 없어도 `null`이 아니라 규칙 주소(캡션 `null`)다 |
| v0.72 | 2026-10-29 | **닉네임 후속 (BE-139 리뷰 반영, FE-137)** — M-5 · M-7: `방청객`으로 시작하는 닉네임 금지(자동 이름 흉내 방지). M-5 · M-6 응답 `nickname`은 항상 표시 이름 문자열(닉네임이 없으면 `방청객 {회원 ID}`). 12장 글쓴이 `nickname` 설명에서 "닉네임 설정은 후속" 삭제(M-7로 생김) · M-5 설명의 버전을 v0.69로 정정 |
| v0.73 | 2026-10-29 | **일러스트 총 개수 API 신설 (BE-142, FE-138)** — 13장 I-1 `GET /api/v1/illustrations/summary`(공개). 사건 컷 · 캐릭터(아이콘 포함) 일러스트의 총 장수와 종류별 장수. 로그인 전 마이페이지 안내("일러스트 N장")용. 이미지는 프론트에만 있어 백엔드는 목록 파일(`illustration-images.txt`)로 세고, 테스트가 이미지 폴더와의 어긋남을 막는다(`docs/decisions/BE-142.md`) |
| v0.74 | 2026-10-30 | **아이디 회원가입 약관 동의 서버 검증 · 동의 기록 (BE-133, ERD v1.50 · V40)** — M-5 요청에 `agreed`(필수, `true`만) 추가. 없거나 `null`이면 400 `VALIDATION_ERROR`(`details`: `agreed` · `NotNull`), `false`면 400(`agreed` · `AssertTrue`) — 형식 검사라 409(중복 아이디 · 닉네임)보다 먼저다. 가입하면 서버가 동의 시각 · 약관 버전을 회원에 남긴다(응답은 그대로). 카카오 로그인(M-3 · M-4)으로 처음 가입하는 경로는 이번 범위 밖(결정 기록 BE-133) |

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
| 발급 시점 | 체험 시작(`POST /cases/{caseId}/experience`) · (확장) 성향 테스트 제출(P-2) · 꾸미기 등 행동 API 때 쿠키가 없거나 DB에 없는 값이면 새로 발급. (확장, BE-62) 로그인 때 쿠키 값이 다른 회원에 연결됐거나 없으면 이 회원에 연결된 새 값 |
| 삭제 시점 | (확장, v0.70 · BE-137) 로그아웃(M-2) 때 회원에 연결된 값이면 지운다. 손님 값은 지우지 않는다 |
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
| 404 | `NOT_FOUND` | (v0.64, BE-132) 없는 API 주소. 사건 · 체험처럼 대상이 없는 경우는 각자의 코드(`CASE_NOT_FOUND` 등)를 쓴다 | 개발 오류. 일반 오류 안내 |
| 405 | `METHOD_NOT_ALLOWED` | (v0.64, BE-132) 그 주소가 지원하지 않는 HTTP 메서드. `Allow` 헤더에 쓸 수 있는 메서드가 있다 | 개발 오류 |
| 415 | `UNSUPPORTED_MEDIA_TYPE` | (v0.64, BE-132) 지원하지 않는 `Content-Type`(본문이 있는 API는 `application/json`) | 개발 오류 |
| 404 | `CASE_NOT_FOUND` | 사건이 없거나 `PUBLISHED`가 아님 | S-14 |
| 404 | `EXPERIENCE_NOT_FOUND` | 이 사건의 내 체험이 없음 (쿠키 없음 포함) | S-02로 이동 |
| 409 | `INVALID_STATE` | 지금 상태에서 할 수 없는 요청 (이미 제출 · 확정, 순서가 맞지 않음, 아직 공개 전) | `currentStatus`의 화면으로 이동 |
| 409 | `STEP_OUT_OF_ORDER` | 섹션 확인 순서를 건너뜀 | 현재 섹션 다시 불러오기 |
| 422 | `INVALID_RANGE_OPTION` | 사전 판단 구간이 이 사건 범죄 유형의 선택지가 아님 | 개발 오류 |
| 422 | `INVALID_FACTOR` | 이 사건의 판단 요소가 아님, 사전 판단에 `OVERVIEW`가 아닌 요소, 중복 요소, 방향 누락 | 개발 오류 |
| 422 | `TOO_MANY_FACTORS` | 사전 판단 작용 요소가 2개를 넘음 (확장) | 개발 오류 |
| 422 | `INVALID_PENALTY_TYPE` | 이 사건에서 허용되지 않은 형벌, 허용되지 않은 감경 조합(`reducedTo`) | 개발 오류 |
| 422 | `OUT_OF_ALLOWED_RANGE` | 형량이 선고할 수 있는 범위 밖 | S-05c 경고 유지 (정상 화면이면 버튼이 비활성이라 오지 않음) |
| 422 | `INVALID_SUSPENSION` | 집행유예를 허용하지 않는 형벌 · 형량인데 값이 있음, 기간이 1 ~ 5년 밖 | 입력 안내 |
| 401 | `UNAUTHORIZED` | (확장, 관리자 API · v0.68 커뮤니티 쓰기) 로그인하지 않음 · 세션 만료 | 관리자 로그인 화면 · 커뮤니티는 로그인 안내 |
| 401 | `INVALID_CREDENTIALS` | (확장, 관리자 API · v0.58 회원 M-6) 로그인 실패. 계정 없음 · 비활성 · 비밀번호 불일치를 구분하지 않는다. M-6은 아이디 형식이 틀린 경우도 같은 코드다 | 로그인 실패 안내 |
| 401 | `TOKEN_EXPIRED` | (확장, 회원 토큰) 액세스 토큰 만료 | 토큰 재발급 후 다시 요청 |
| 401 | `TOKEN_INVALID` | (확장, 회원 토큰) 형식 · 서명 등이 잘못된 토큰, 아직 토큰을 확인할 수 없음 | 토큰을 지우고 다시 로그인 |
| 403 | `FORBIDDEN` | (확장, 관리자 API) 권한 없음, CSRF 토큰 없음 · 불일치 | CSRF 토큰을 다시 받아 재시도 · 일반 오류 안내 |
| 404 | `JUDGMENT_NOT_FOUND` | (확장, 관리자 API) 판결이 없거나 사용자 판결 | 목록 다시 불러오기 |
| 404 | `FACTOR_NOT_FOUND` | (확장, 관리자 API) 판단 요소가 없음 | 상세 다시 불러오기 |
| 404 | `PERSONALITY_RESULT_NOT_FOUND` | (확장, 성향 API) 성향 테스트 결과가 없음 (쿠키 없음 포함) | S-15 테스트 안내 |
| 404 | `PERSONALITY_TYPE_NOT_FOUND` | (확장, 성향 API) 공유 주소의 유형이 없음 | 공유 결과 없음 안내 · 테스트 권유 |
| 409 | `DUPLICATE_NICKNAME` | (확장, 회원 M-5 · M-7, v0.69) 이미 쓰는 닉네임(대소문자만 달라도 같다). 동시에 같은 닉네임을 정해도 한 명만 되고 나머지는 이 코드다 | 닉네임 칸에 안내 |
| 409 | `DUPLICATE_ID` | (확장, 회원 M-5, v0.58) 이미 쓰는 아이디(대소문자만 다른 아이디 포함). 동시에 같은 아이디로 가입해도 한 명만 만들어지고 나머지는 이 코드다 | 아이디 칸에 안내. "방금 가입했다면 로그인" 안내도 함께 (가입 직후 서버 오류 뒤 다시 누른 경우) |
| 409 | `DUPLICATE_TITLE` | (확장, 관리자 API) 사건 제목이 다른 사건과 겹침 | 입력 안내 |
| 409 | `PUBLISH_NOT_READY` | (확장, 관리자 API) 공개 조건 미충족. `details`에 조건 코드 | 남은 조건 안내 (A-13) |
| 502 | `PROMPT_GENERATION_FAILED` | (확장, 관리자 API, BE-91 · BE-92) Gemini 호출 실패, 또는 설정(`GEMINI_API_KEY`) 없음 | 다시 시도 안내 (A-13-1 · A-13-2 · A-13-6) |
| 502 | `FILE_UPLOAD_FAILED` | (확장, 관리자 API, BE-91) S3 업로드 실패, 또는 설정(`S3_BUCKET_NAME`) 없음 | 다시 시도 안내 (A-13-3 · A-13-4) |
| 400 | `INVALID_IMAGE_FILE` | (확장, 관리자 API, BE-91) 사진 파일이 없음 · 비어 있음 · 허용하지 않는 형식 | 입력 안내 (A-13-3 · A-13-4) |
| 404 | `PAPER_NOT_FOUND` | (확장, BE-92) 신문 지면을 아직 생성한 적 없음. 관리자 API는 A-13-7 · A-13-8, 공개 API는 API 17(v0.53, BE-102) | 먼저 생성하도록 안내 (A-13-7 · A-13-8). API 17은 S-14로 |
| 404 | `INCIDENT_PHOTO_NOT_FOUND` | (확장, 관리자 API, BE-78 2차 검토 · BE-101) 그 역할의 발단 · 결론 사진이 없음 | 목록 다시 불러오기 (A-13-9) |
| 409 | `DECO_LOCKED` | (확장, BE-96) 아직 열리지 않은 꾸미기를 저장하려 함. `details`에 잠긴 필드(`reason: DECO_LOCKED`) | 화면에서 잠긴 항목은 고를 수 없게 막는다. 오면 마이페이지를 다시 불러온다 (P-7) |
| 409 | `JOB_LOCKED` | (확장, BE-106) 아직 열리지 않은 직업을 고르려 함 | 화면에서 잠긴 직업은 고를 수 없게 막는다. 오면 레벨(L-1)을 다시 불러온다 (L-2) |
| 409 | `COLLECTION_LIMIT_EXCEEDED` | (확장, BE-83, v0.63) 모을 수 있는 수를 넘음 — 일러스트는 한 사건에서 처음 얻은 수 5, 의상은 사용자당 12. `details`에 넘친 종류(`field: items.kind`, `reason: illustration` · `cast`) | 개발 오류(콘텐츠 규칙보다 넉넉한 값이라 정상 화면이면 오지 않음) (P-9) |
| 409 | `DUPLICATE_SOURCE` | (확장, 관리자 API, BE-93) 이미 등록한 사건번호. `details`에 어느 항목인지 담는다 | 입력 안내 (A-14) |
| 409 | `IMPORT_IN_PROGRESS` | (확장, 관리자 API, BE-93) 실행 중인 등록이 있음. 한 번에 하나만 돌린다(외부 AI 호출 한도 보호) | 끝난 뒤 다시 시도 안내 (A-14) |
| 404 | `POST_NOT_FOUND` | (확장, 커뮤니티 BE-115, v0.68) 글이 없음 | 목록으로 돌아가 다시 불러오기 (C-2 · C-4 · C-5) |
| 404 | `IMPORT_NOT_FOUND` | (확장, 관리자 API, BE-93) 등록 작업이 없음 | 목록으로 안내 (A-15) |
| 409 | `IMPORT_NOT_READY` | (확장, 관리자 API, BE-93 2차) 아직 적재할 수 없음 — 생성이 끝나지 않았거나 이미 적재한 등록 | 진행 상황 확인 안내 (A-14-1) |
| 422 | `IMPORT_RESULT_INVALID` | (확장, 관리자 API, BE-93 2차) 생성 결과를 사건으로 만들 수 없음. `details`에 어느 값이 문제인지 담는다(예: `case.factors[2].label`) | 다시 생성하도록 안내 (A-14-1) |
| 502 | `IMPORT_UNAVAILABLE` | (확장, 관리자 API, BE-93) 파이프라인 서비스에 닿지 못함, 또는 설정(`PIPELINE_SERVICE_URL`) 없음. **이 서비스를 켜지 않은 서버에서는 사건 등록만 꺼지고 나머지는 정상이다**(COMMON-50) | 다시 시도 · 관리자 문의 안내 (A-14) |
| 409 | `BASELINE_RETAKE_USED` | (확장, 성향 API, v0.50) 성향카드 다시 정하기를 이미 씀 | 화면은 다 쓰면 버튼을 막는다. 오면 "재심은 이미 한 번 청구했어요" 안내 후 마이페이지를 다시 불러온다 (P-2) |
| 429 | `TOO_MANY_REQUESTS` | (확장, BE-63) 같은 IP에서 `/api` 요청이 너무 많음(평균 초당 20번 · 순간 40번 초과). (v0.47) 아래 경로는 이에 더해 **더 엄격한 전용 제한**을 받는다: M-1 재발급 · M-2 로그아웃(IP별 둘을 합쳐 분당 30번 · 순간 30번), A-2 관리자 로그인(IP별 분당 5번 · 순간 5번), M-3 카카오 로그인 시작(IP별 분당 30번 · 순간 30번 + 전체 합계), (v0.58) M-6 아이디 로그인(IP별 분당 20번 · 순간 20번) · M-5 아이디 회원가입(IP별 분당 10번 · 순간 10번) + 두 경로 전체 합계(초당 10번, 넘으면 순간 20번까지 기다렸다가 처리). 전용 제한은 POST(M-3은 GET)만 센다. **백엔드가 아니라 운영 Nginx가 돌려준다** — 형식은 1-4와 같고 `Retry-After`(초) 헤더가 붙는다: 공통 `1`, M-1 · M-2 · M-3 `2`, A-2 `12`, M-6 `3`, M-5 `6`. M-1이 429여도 리프레시 토큰은 그대로라 로그아웃된 것이 아니다. 로컬 개발(Vite)에는 Nginx가 없어 나오지 않는다 | 일반 오류 안내. 자동으로 다시 보내지 않는다 |
| 500 | `INTERNAL_ERROR` | 서버 오류 | 일반 오류 안내 |
- "이미 제출함"과 "순서가 맞지 않음"을 따로 나누지 않고 `INVALID_STATE` 하나로 둔다. 화면이 할 일은 둘 다 "`currentStatus`의 화면으로 이동"으로 같기 때문이다.
- 관리자 오류는 쿠키 유무만으로 결정하지 않는다. 먼저 요청을 거절한 보안 필터의 오류를 반환하므로 미인증 POST도 CSRF 검사 실패 시 403일 수 있다. 인증·인가 구분과 응답 우선순위는 **8-1의 BE-88 검증 기준**을 따른다.

### 1-6. 체험 상태 → 화면 (모든 화면 공통)

화면은 진입할 때 상태를 따로 조회하지 않고 **그 화면의 API를 바로 부른다.** 허용되지 않은 화면이면 API가 `409 INVALID_STATE`와 `currentStatus`를 돌려주므로, 화면은 아래 표의 "보낼 화면"으로 이동한다(IA 5장 · 9장, 6장 #1 확정). API 3은 상태만 알아야 하는 경우에 쓴다.

| status | 보낼 화면 | 허용 화면 |
| --- | --- | --- |
| `STARTED` | S-03 | S-03 |
| `PRE_JUDGED`, `REVIEWING` | S-04 | S-04 |
| `REVIEWED` | S-05 | S-04, S-05 |
| `VERDICT_CONFIRMED` | S-06 | S-06 |
| `AI_REVEALED` | S-07 | S-06, S-07 |
| `COMPLETED` | S-08 | S-06, S-07, S-08, S-09 |

- (v0.38, BE-82) 화면 ID는 6막 순서다: S-03 1막 첫인상 · S-04 2막 재판 · S-05 3막 내 판결 · S-06 4막 AI 판결 · S-07 5막 실제 판결 · S-08 6막 세 판결 비교 · S-09 리뷰 페이지. 화면 주소는 IA 4장 화면 목록을 따른다. 옛 정리 화면(옛 S-05 `/summary`)은 없앴다.
- S-09 리뷰 페이지(확장)는 `COMPLETED`에서만 보인다(체험 전에는 숨김). 후기 API · DB는 후속 이슈라 지금은 화면이 지키는 규칙이고, **진입 시 API 3으로 상태를 확인**한다(5장). 후기 API를 만들 때 서버도 `COMPLETED`를 확인하고 이 표를 다시 본다.

---

## 2. API 목록

| # | 메서드 | 경로 | 설명 | 허용 상태 | 상태 변경 | 화면 | 단계 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| 1 | GET | `/cases` | 사건 목록 | — | — | S-02 | MVP |
| 2 | POST | `/cases/{caseId}/experience` | 체험 시작 (있으면 기존 체험) | — | 없으면 → `STARTED` | S-02 | MVP |
| 3 | GET | `/cases/{caseId}/experience` | 내 체험 상태 | 전체 | — | 전체 | MVP |
| 4 | GET | `/cases/{caseId}/experience/overview` | 사건 개요 · 사전 판단 선택지 | `STARTED` | — | S-03 | MVP |
| 5 | POST | `/cases/{caseId}/experience/pre-judgment` | 사전 판단 제출 | `STARTED` | → `PRE_JUDGED` | S-03 | MVP |
| 6 | GET | `/cases/{caseId}/experience/review` | 사건 정보 (열린 섹션까지) | `PRE_JUDGED` ~ `REVIEWED` | — | S-04 | MVP |
| 7 | POST | `/cases/{caseId}/experience/review-steps` | 섹션 확인 기록 | `PRE_JUDGED` ~ `REVIEWED` (`REVIEWED`는 상태 유지) | → `REVIEWING` / `REVIEWED` | S-04 | MVP |
| 8 | GET | `/cases/{caseId}/experience/verdict-form` | 판결 입력 정보 | `REVIEWED` | — | S-05 | MVP |
| 9 | POST | `/cases/{caseId}/experience/verdict` | 판결 제출 | `REVIEWED` | → `VERDICT_CONFIRMED` | S-05 | MVP |
| 10 | GET | `/cases/{caseId}/experience/judgments/ai` | AI 판결 | `VERDICT_CONFIRMED` 이상 | — | S-06 | MVP |
| 11 | POST | `/cases/{caseId}/experience/court-reveal` | 실제 판결 공개 | `VERDICT_CONFIRMED` 이상 | 처음이면 → `AI_REVEALED` | S-06 | MVP |
| 12 | GET | `/cases/{caseId}/experience/judgments/court` | 실제 판결 | `AI_REVEALED` 이상 | — | S-07 | MVP |
| 13 | POST | `/cases/{caseId}/experience/comparison-reveal` | 비교 공개 | `AI_REVEALED` 이상 | 처음이면 → `COMPLETED` | S-07 | MVP |
| 14 | GET | `/cases/{caseId}/experience/comparison` | 세 판결 비교 | `COMPLETED` | — | S-08 | MVP |
| 15 | GET | `/cases/{caseId}/experience/comparison/analysis` | AI 비교 분석 | `COMPLETED` | — | S-08 | 확장 |
| 16 | GET | `/cases/{caseId}` | 공개 사건 상세 (체험 없이) | — | — | S-20 | 확장 (v0.29) |
| 17 | GET | `/cases/{caseId}/paper` | 공개 신문 지면 (헤드라인 · 본문 · 발단 · 결론 사진) | — | — | S-20 | 확장 (v0.53) |
| 18 | GET | `/cases/{caseId}/script` | 공개 사건 대본 (승인된 2막 증거조사 대사) | — | — | S-20 | 확장 (v0.59) |

**공개(11, 13)와 조회(12, 14)를 나눈 이유**: 상태를 바꾸는 요청은 POST로 두고, 조회는 몇 번을 불러도 상태가 바뀌지 않게 한다. 결과 화면을 새로고침하면 조회만 다시 부르면 된다. 공개 요청은 이미 공개된 상태에서 다시 와도 거절하지 않고 성공으로 돌려준다(시퀀스 7장).

---

## 3. API 상세

### API 1. 사건 목록 — `GET /cases`

**요청**

| 위치 | 이름 | 필수 | 설명 |
| --- | --- | --- | --- |
| query | `crimeType` |  | `MURDER` / `FRAUD` / `INJURY` / `ROBBERY` / `THEFT` (`ROBBERY` · `THEFT`는 v0.55). 없으면 전체 |

**응답 200**

json

```json
{  "summary": { "total": 9, "byCrimeType": { "MURDER": 3, "FRAUD": 3, "INJURY": 3, "ROBBERY": 0, "THEFT": 0 } },  "cases": [    {      "caseId": 1,      "title": "빌린 돈 문제로 찾아온 지인을 살해한 사건",      "crimeType": "MURDER",      "crimeCategoryLabel": "생명범죄",      "chargeName": "살인",      "shortIntro": "빌린 돈 문제로 찾아온 지인과 다투다 흉기로 살해한 사건입니다.",      "keywords": ["돈 문제", "집으로 찾아옴"],      "difficulty": "HIGH",      "estimatedMinutes": 15,      "participantCount": 1284,      "thumbnailUrl": "/images/cases/case-1-reenact.webp",      "scriptReady": true,      "myStatus": "IN_PROGRESS"    }  ]}
```

- `PUBLISHED` 사건만. `summary`는 필터와 관계없이 전체 기준(칩 옆 건수 표시용). `byCrimeType`은 공개 사건이 없는 유형도 0으로 모든 범죄 유형 키를 내려 준다(v0.55부터 5개).
- `crimeCategoryLabel`: `crime_type`별 표시용 분류명. `MURDER` → "생명범죄", `FRAUD` · `ROBBERY` · `THEFT` → "재산범죄", `INJURY` → "신체범죄"(`ROBBERY` · `THEFT`는 v0.55). DB 컬럼이 아니라 서버 코드 상수(`CrimeType.categoryLabel`)에서 만든다(ERD 3-1).
- `chargeName`: 죄명(`legal_case.charge_name`, 예: "살인"). 사건 상단 바(S-04~S-08)가 `caseId`로 이 목록에서 제목과 함께 찾아 쓴다(BE-79, D-01). 사건 개요(API 4) · API 16의 `chargeName`과 같은 값이다.
- `thumbnailUrl`: `legal_case.thumbnail_url`. **없으면 규칙 주소 `/images/cases/case-{caseId}-reenact.webp`를 내려준다**(BE-141). 그 파일이 없으면 화면이 범죄 유형별 공용 컷으로 바꾼다.
- `participantCount`: `attempt_no = 1`이고 `COMPLETED`인 체험 수.
- (v0.60, BE-116) `scriptReady`: 이 사건에 **승인된 공개 대본**이 있는가(`true` / `false`). 기준은 API 18이 200을 주는 조건과 같다(사건 `PUBLISHED` + 대본 `reviewStatus: APPROVED`) — 대본이 없거나 검수 대기 · 반려면 `false`다. 화면(S-02)은 이 값으로 체험 가능한 사건을 먼저 보이고, `false`는 "준비 중"으로 다룬다. 사용자와 무관한 값이다. **화면 정책이며 서버는 막지 않는다** — API 2(체험 시작)는 `scriptReady`를 보지 않는다. 또 대사를 직접 수정(A-13-13)하면 검수 기록이 지워져 `true → false`로 바뀔 수 있다(이때 API 18도 404).
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
- 이미 체험이 있으면 **새로 만들지 않고** 기존 체험을 돌려준다. 화면은 1-6 표대로 이동한다(완료한 사건이면 S-08, REQ-011 · 012).
- 동시에 두 번 와도 유니크 제약(`anonymous_user_id`, `case_id`, `attempt_no`)으로 하나만 생긴다. 제약 위반이 나면 서버가 기존 체험을 다시 읽어 `200`으로 돌려준다.

| 거절 | 조건 |
| --- | --- |
| 404 `CASE_NOT_FOUND` | 사건 없음 · 비공개 |

---

### API 3. 내 체험 상태 — `GET /cases/{caseId}/experience`

**응답 200**: API 2와 같은 형식. 기존 5개 상태 필드만 반환한다.

- 사전 판단·AI 판결·재판부 판결·비교 결과는 포함하지 않는다. 사전 판단은 체험 완료 후 API 14의 독자 의견에서 처음 다시 확인한다.
- 제출 후 처음 선택한 형량을 진행 중에 다시 조회할 수 없다. 1막부터 다시 보기는 기존 체험 기록을 보존하는 읽기 전용 기능으로 유지하며 초기화·재제출·수정은 허용하지 않는다. 진행 중에는 제출 완료 안내만 표시하고, 완료 후에는 API 14로 최초 사전 판단을 표시할 수 있다(BE-77, PR #198·#203).

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

- `rangeOptions`: 이 사건 `crime_type`의 `sentence_range_option`을 `display_order` 순으로. 사기 · 상해 · 절도는 공통 7개, 살인은 벌금형을 빼고 무기징역 · 사형을 더한 8개, 강도는 벌금형을 빼고 무기징역을 더한 7개다(강도 · 절도는 v0.55).
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
- 응답에 사전 판단 내용을 되돌려주지 않는다(S-08 전까지 다시 보여 주지 않음).
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

S-04가 쓴다. (v0.38, BE-82) 함께 쓰던 정리 화면(옛 S-05)은 없앴다.

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
- `summary`: `REVIEWED`일 때 핵심 사실 요약(`case_section` `SUMMARY`)을 채운다. 이 값을 보여 주던 정리 화면(옛 S-05)은 BE-82에서 없앴다. 필드는 그대로 두며(형식 변경 없음), S-05 내 판결 파일철 첫 장 "사건 요약"의 핵심 사실로 쓴다(FE-58).
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

- `penaltyOptions`: `penalty_rule`의 `display_order` 순. 화면은 여기 있는 형벌만 보여 준다(FR-3-1). 버튼 비활성 판단(S-05c)도 이 값으로 한다.
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
- `summary`는 S-08 판결 카드의 한 줄 요약이다.
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
| 409 `INVALID_STATE` | `AI_REVEALED` 전 → `currentStatus`의 화면 (1-6, 보통 S-06) |

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
      "AI와 재판부는 피해 회복 공탁을 고려했고, 내 판결에는 들어가지 않았어요.",
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

**`matrix[].category`** — 와이어프레임 S-08의 분류 태그(FR-6-4).

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
| 409 `INVALID_STATE` | `COMPLETED` 전 → `currentStatus`의 화면 (1-6, 보통 S-07) |

---

### API 15. (확장) AI 비교 분석 — `GET /cases/{caseId}/experience/comparison/analysis`

> **(v0.14) 보류** — AI 비교 분석(REQ-062 · 063)을 11차 회의에서 확장 범위에서 제외했다. 구현하지 않으며, 아래 정의는 결정 기록으로 남긴다. S-08은 API 14의 규칙 문장 · 한 줄 요약을 계속 쓴다.

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
- `perspectives.USER.text`는 확장 단계의 **AI "내 판결" 요약**이다(REQ-062). `DONE`이면 화면은 S-08 내 판결 카드의 한 줄 요약(API 14 `judgments.USER.summary`, MVP 규칙 문장)을 이 문장으로 바꾼다. `perspectives.USER.factorIds`는 사용자가 고른 요소 안에서만 나와야 한다.

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
{  "caseId": 1,  "title": "빌린 돈 문제로 찾아온 지인을 살해한 사건",  "crimeType": "MURDER",  "crimeCategoryLabel": "생명범죄",  "chargeName": "살인",  "shortIntro": "빌린 돈 문제로 찾아온 지인과 다투다 흉기로 살해한 사건입니다.",  "keywords": ["돈 문제", "지인", "흉기"],  "difficulty": "MID",  "estimatedMinutes": 15,  "participantCount": 42,  "thumbnailUrl": "/images/cases/case-1-reenact.webp",  "overview": "피고인이 빌린 돈을 갚지 못해 오래 다투던 지인이 집으로 찾아오자, …",  "scriptReady": true,  "myStatus": null,  "cast": { "prosecutor": "naero", "defense": "nambul" },  "sensitiveFlags": ["MENTAL_HEALTH"]}
```

- 필드는 API 1 카드와 같고(`chargeName` 포함, v0.32), `overview`(사건 개요, API 4의 `case.overview`와 같은 값)를 더한다. `participantCount` · `myStatus` 기준도 API 1과 같다. (v0.60, BE-116) `scriptReady`도 API 1과 같은 값 · 같은 기준이다 — `false`면 S-20은 시작 대신 "준비 중"을 보인다(API 18을 따로 불러 확인하지 않아도 된다).
- **법정형 · 선고 가능 범위 · 권고 범위 · 판결 · 섹션 본문은 넣지 않는다**(FR-2-8, API 4와 같은 기준). 섹션은 체험 중 API 6이 단계별로 연다.
- 쿠키는 **읽기만** 하고 발급하지 않는다(발급은 API 2 · P-2, 1-2). 쿠키가 없거나 형식이 틀리거나 체험 전이면 `myStatus`는 `null`이다.
- 버튼 분기(정보 구조 S-20): `myStatus`가 `null`이면 **체험 시작**(API 2로 체험을 만든 뒤 S-03), `IN_PROGRESS`면 **이어서 하기**, `COMPLETED`면 **결과 다시 보기**. 이어서 하기 · 결과 다시 보기도 API 2를 불러 응답 `status`로 이동한다(1-6). 세부 상태(7단계)는 이 API에 내보내지 않는다.
- 캐시: `myStatus`가 사용자마다 달라 API 1과 같은 규칙이다(지금은 `no-store`, HTTP 캐시를 켤 때는 `private` + `Vary: Cookie`).
- 대본(판결 체험 무대, REQ-089)은 이 API에 넣지 않는다. 승인된 대본의 증거조사 대사는 API 18(BE-109)에서 따로 준다.
- (v0.67, BE-125) `cast`: 이 사건의 배역(`legal_case.cast_roles`). `prosecutor`(묶음 A, 판사 · 검사) · `defense`(묶음 B, 변호인 · 피고인 역)가 각각 `naero` · `nambul` 중 하나다. 배정 전 사건은 **필드가 없다** — 화면은 기본 배치(내로 판사 · 검사, 남불 변호인 · 피고인)를 쓴다(FE-128). 새로 적재하는 사건은 대본이 함께 오면 대본을 만든 배역을 사건 배역으로 쓰므로 API 18의 `cast`와 같다. 다만 관리자가 A-11로 배역을 바꾸면 이미 만든 대본은 그대로라 둘이 달라질 수 있다(대본 재생성은 BE-129).
- (v0.67, BE-125) `sensitiveFlags`: **사용자에게 안내하는 플래그만** 준다 — `VULNERABLE_VICTIM` · `MENTAL_HEALTH` · `SELF_HARM` · `SEXUAL_OFFENSE` · `DOMESTIC_VIOLENCE` · `HATE_CRIME`. 대본 가드 전용 플래그(`CAPITAL_PENALTY` 등)는 판결 전에 형량을 짐작하게 할 수 있어 내보내지 않는다. 안내할 것이 없으면 **필드가 없다**. 안내 문구는 화면이 정한다(FE-129, 파이프라인 `script_assembler.sensitive_notice`와 같은 규칙).

| 거절 | 조건 |
| --- | --- |
| 404 `CASE_NOT_FOUND` | 사건 없음 · 비공개 → 화면은 S-14 |
| 400 `VALIDATION_ERROR` | `caseId`가 숫자가 아님 |

---

### API 17. (확장) 공개 신문 지면 — `GET /cases/{caseId}/paper`

FE-64가 쓰는 API(S-20 기사 지면 · 증거조사). 관리자 신문 지면 API(A-13-6 ~ 8, BE-92)와 발단 · 결론 사진(A-13-4 · 5, BE-91 · BE-101)을 공개용으로 가져다 조립한다 — 새로 저장하지 않는다(`docs/decisions/BE-92.md`와 같은 원칙). 설계 근거: `docs/decisions/BE-78.md` "확정 사항", BE-102 Jira 댓글.

**요청**: 본문 없음.

**응답 200**

```json
{
  "desk": "사회부",
  "headline": "빌린 돈 문제로 다투다 벌어진 사건",
  "deck": "오랜 금전 다툼 끝에...",
  "photo": "https://…/case-photos/case-1/representative-….jpg",
  "caption": "다투는 두 사람",
  "article": ["문단 1", "문단 2"],
  "extraPhotos": {
    "origin": { "imageUrl": "https://…/incident-origin-….jpg", "caption": "다툼이 격해진 순간" },
    "conclusion": { "imageUrl": "https://…/incident-conclusion-….jpg", "caption": "..." }
  }
}
```

- `photo`는 A-13-3으로 올린 사건 대표사진(`legal_case.thumbnail_url`). 아직 안 올렸으면 규칙 주소 `/images/cases/case-{caseId}-reenact.webp`(BE-141).
- **`extraPhotos`는 배열이 아니라 `{ origin, conclusion }` 고정 자리다.** 그 역할의 사진 행이 없으면 그 자리는 규칙 주소 `/images/cases/case-{caseId}-{origin|conclusion}.webp`에 `caption: null`이다(BE-141, 예: 발단만 올렸으면 `{ "origin": {...}, "conclusion": { "imageUrl": "/images/cases/case-1-conclusion.webp", "caption": null } }`) — 자리가 `null`이 되는 일은 없다 — 배열로 내려주면 한쪽이 비었을 때 다른 쪽이 앞자리로 밀려 역할이 뒤바뀌어 보일 수 있어서다(BE-101에서 역할 칸을 둔 것과 같은 이유). A-13-6 ~ 8의 `extraPhotos`(배열, `role` 포함)와는 형태가 다르다 — 관리자 화면은 목록 · 정리가 필요해 배열로, 공개 화면은 항상 같은 자리만 보면 돼서 고정 자리로 했다.
- `prompt` · `s3Key` · `id` · `displayOrder` 등 내부 기록용 값은 내려주지 않는다.
- 증거조사 `evidencePhotos`(검사 측 = 결론, 변호인 측 = 발단, FE-63)는 이 `extraPhotos.origin` · `.conclusion`을 그대로 연결해 쓴다 — 같은 사진을 두 번 받지 않는다.
- 공용 컷(사진 파일이 없거나 로딩 실패)으로 바꾸는 처리는 프론트가 한다(FE-63). 서버가 내려주는 규칙 주소의 파일이 없으면, Nginx의 화면 주소 fallback(`location /`의 `try_files … /index.html`)이 `index.html`을 **200**으로 돌려준다(404가 아니다). 브라우저가 이를 이미지로 해석하지 못해 `error` 이벤트가 나고, 이때 프론트가 공용 컷으로 바꾼다. 상태 코드로 파일 유무를 확인하는 클라이언트는 이 방식으로는 감지하지 못한다.
- 공개(`PUBLISHED`)된 사건만 응답한다(API 16과 같은 기준).

| 거절 | 조건 |
| --- | --- |
| 404 `CASE_NOT_FOUND` | 사건 없음 · 비공개 |
| 404 `PAPER_NOT_FOUND` | 사건은 공개됐지만 관리자가 신문 지면을 아직 생성한 적 없음 |
| 400 `VALIDATION_ERROR` | `caseId`가 숫자가 아님 |

---

### API 18. (확장) 공개 사건 대본 — `GET /cases/{caseId}/script`

> **신설 (BE-109).** A-13-10 ~ 12(BE-108)가 적재 · 검수한 대본에서 **승인된 것만** 공개용으로 가져다 조립한다 — 새로 저장하지 않는다(API 17과 같은 원칙). 담는 것은 **2막 증거조사 대사뿐**이다 — 나머지 막(1막 해설 · 공소사실 · 쟁점 정리 · 피고인신문 · 검사의견 · 최종변론 · 최후진술)은 파이프라인이 만들지 않아(COMMON-65) 이 API에 넣지 않는다. 그중 사건마다 달라지지 않는 부분만 FE-33이 공통 틀로 채우고, 공소사실 · 피고인신문처럼 사건별 대사가 필요한 막은 당분간 "증거조사 중심 축약판"으로 둔다 — 자세한 내용은 [BE-109 결정 기록](decisions/BE-109.md) 참고. 설계 근거: BE-109 Jira 댓글(승호님 제안 · 해송님 FE-33 담당 합의, 2026-10-27).

**요청**: 본문 없음.

**응답 200**

```json
{
  "evidence": [
    { "factorId": 1, "side": "PROSECUTOR", "character": "naero", "card": "계약서에 서명했다",
      "text": "계약서에 서명한 사실은 분명합니다." },
    { "factorId": 2, "side": "DEFENSE", "character": "nambul", "card": "돈을 돌려주지 않았다",
      "text": "그 돈은 빌린 것이 아니라 받은 것입니다." }
  ],
  "cast": { "prosecutor": "naero", "defense": "nambul" }
}
```

- `character` · `cast`는 저장된 **코드값**(`naero` · `nambul` 등)을 그대로 내려준다. 화면에 보일 한글 이름(나로 · 남불)으로 바꾸는 것은 프론트 몫이다.
- `side`는 A-13-10의 `role`을 **대문자 코드값**(`PROSECUTOR` · `DEFENSE`)으로 바꿔 내려준다 — 다른 공개 API가 모두 코드값이라 맞췄다. 화면 한글(검사 · 변호인)로 바꾸는 것은 프론트 몫이다.
- `card`는 A-13-10의 `label`과 같은 값(= 사건의 판단 요소 문구). 파이프라인 사건에는 정적 증거 카드가 없어 이 문구가 카드 역할을 그대로 한다 — 요소가 지워졌으면 `null`이다.
- **검수용 값(`manual` · `editedAt` · `validation` · `attempts`)은 내려주지 않는다.** 사람이 고친 줄도 같은 응답에 그대로 나오면 충분하다.
- **배열 순서가 곧 재생 순서다.** 별도 정렬 필드를 두지 않는다.
- `cast`는 A-13-10과 같은 값(`{ prosecutor, defense }`).
- (v0.60, BE-116) 이 조건은 API 1 · API 16의 `scriptReady`와 같다 — `scriptReady: true`인 사건만 이 API가 200이다. 목록은 이 API를 사건마다 부르지 말고 `scriptReady`를 본다.
- **승인(`reviewStatus: APPROVED`)한 대본만 내려준다.** 검수 전 · 반려된 대본은 없는 것으로 본다 — 사건이 `PUBLISHED`여도 대본이 미승인이면 `SCRIPT_NOT_FOUND`다(API 16 · 17과 달리 사건 공개 여부만으로는 판단하지 않는다).
- 한 캐릭터가 두 배역을 번갈아 맡는 장면(피고인신문)은 이번 응답에 들어오지 않는다 — 그 장면이 서버 데이터로 들어오는 때에 `cast` 확장이 필요하다(FE-60 "추후 재검토").

| 거절 | 조건 |
| --- | --- |
| 404 `CASE_NOT_FOUND` | 사건 없음 · 비공개 |
| 404 `SCRIPT_NOT_FOUND` | 대본이 없거나, 아직 검수 대기 · 반려 상태(= 승인 전) — 빈 배열 대신 404를 쓰는 이유는 "대본 없음"과 "대사 0줄"을 구분하기 위해서다(A-13-7 `PAPER_NOT_FOUND`와 같은 구분) |
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

API 1 · 2 · 16 · 17은 상태와 관계없이 부를 수 있다. 표에 없는 조합은 `409 INVALID_STATE`다.

---

## 5. 화면별 호출 순서

| 화면 | 진입 시 | 버튼 |
| --- | --- | --- |
| S-02 | 1 | `체험 시작` → 2 → 응답 status로 이동 (1-6). 신문 지면(확장)에서는 기사를 고르면 S-20 |
| S-20 (확장) | 16 | `체험 시작` · `이어서 하기` · `결과 다시 보기` → 2 → 응답 status로 이동 (1-6) |
| S-03 | 4 | `판단 제출` → 5 → S-04 |
| S-04 | 6 | `읽었습니다` → 7 → 6 다시 조회. 마지막 섹션 후 S-05 |
| S-05 | 8 | `확정하기` → 9 → S-06 |
| S-06 | 10 | `실제 판결 확인하기` → 11 → S-07 |
| S-07 | 12 | `세 판결 비교 보기` → 13 → S-08 |
| S-08 | 14 (15는 v0.14 보류) | `다른 사건 체험` → S-02 |
| S-09 (확장) | 3 — 상태가 `COMPLETED`가 아니면 응답 `status`로 1-6 "보낼 화면"으로 이동 (후기 API는 후속 이슈. 그 전까지 후기 내용은 목 데이터) | — |

- 모든 화면은 진입 API가 `409 INVALID_STATE`를 돌려주면 `currentStatus`로 1-6 표의 "보낼 화면"으로 이동한다. `404 EXPERIENCE_NOT_FOUND`면 S-02, `404 CASE_NOT_FOUND`면 S-14로 간다. 진입 전에 API 3을 따로 부르지 않는다(6장 #1 확정, 요청 1회 절약).
- (v0.38, BE-82) API 6을 함께 쓰던 정리 화면(옛 S-05)을 없애 API 6은 S-04만 쓴다. `REVIEWED`면 1-6 표대로 S-05(판결 입력)로 간다.
- (v0.38, BE-82) **S-09는 위 "API 3을 따로 부르지 않는다"의 예외다.** 상태를 확인해 줄 진입 API가 아직 없어서(후기 API는 후속), 주소로 직접 들어오거나 새로고침하면 화면이 완료 여부를 알 수 없다. 그래서 진입 시 API 3을 불러 `status`가 `COMPLETED`일 때만 보여 주고, 아니면 그 `status`로 1-6 표의 "보낼 화면"으로 이동한다(1-6 앞 문단 "API 3은 상태만 알아야 하는 경우에 쓴다"). `404 EXPERIENCE_NOT_FOUND`면 S-02, `404 CASE_NOT_FOUND`면 S-14. 후기 API가 생기면 그 API가 `COMPLETED`가 아니면 `409 INVALID_STATE`를 돌려주게 하고, S-09도 다른 화면처럼 그 API만 부르도록 바꾼다.

---

## 6. 결정 필요 사항

| # | 내용 | 제안 | 영향 |
| --- | --- | --- | --- |
| 1 | 체험 상태 확인을 API 3으로 먼저 할지, 각 API의 `INVALID_STATE`로 처리할지 | **확정 (v0.3)**: 각 API의 `INVALID_STATE`로 처리 (요청 수 절약, 1-6 · 5장) | 프론트 라우팅 구조 (기술 스택 5장) |
| 2 | 쿠키 이름 · 기간, 프론트 · API 도메인 구조 | 도메인 구조 **확정 (v0.3)**: 같은 도메인, Nginx `/api` 프록시(기술 스택 8장). 쿠키 `NLNB_AID` · 1년은 제안 유지(요구사항 15장) | CORS · SameSite 설정 |
| 3 | `preToFinal.direction` 세부 비교 규칙 | **확정 (BE-11, 2026-10-06)**: 형벌 종류를 `FINE < SUSPENDED < PRISON < LIFE < DEATH` 순(`sentence_range_option.kind` 선언 순서)으로 먼저 비교한다. 종류가 다르면 그 순서로 `HEAVIER`/`LIGHTER`, 같은 종류가 `PRISON`이면 구간의 `min_months`/`max_months`(상한 미만 기준)로 더 비교하고, 그 외 종류는 세분하지 않고 `SAME`이다. 감경이 있으면 최종 선고 형벌(`reducedTo`)로 비교한다 | S-08 한 줄 요약 문구 |
| 4 | 규칙 문장(`ruleSentences`) 문장 틀 | **확정 (BE-26 · BE-11, 2026-10-06)**: 요소 라벨(완전한 서술문)이 아니라 요약어(`factor.summary_tag`)로 만든다. `ALL_SAME`은 방향별로 요약어를 모아 "세 판결 모두 {↑ 요약어들}을 형량을 높이는 요소로[, {↓ 요약어들}을 형량을 낮추는 요소로] 봤어요.", `ONLY_ME_MISSED`는 "AI와 재판부는 {요약어들}을 고려했고, 내 판결에는 들어가지 않았어요."(BE-110, 2026-10-29: 사용자를 놓친 쪽으로 읽히지 않도록 문구 변경), `DIVERGED`는 "{요약어들}에 대한 판단이 세 판결 사이에서 엇갈렸어요."로 한 문장씩 만든다. 해당 분류가 없으면 각각 "세 판결이 똑같이 본 판단 요소는 없어요." / "세 판결 사이에 판단이 엇갈린 점은 없어요."를 넣는다 | S-08 공통점 · 차이점 |
| 5 | 무죄 선택 처리 (요구사항 15장) | **확정 (v0.3)**: MVP에서 무죄 선택지를 뺀다. 이후 도입 여부는 추후 검토 | API 8 · 9 · 14 |
| 6 | API 15 재조회 간격 · 최대 횟수, AI 생성 시간 초과 기준 | ~~3초 간격, 최대 10회 / 30초~~ — v0.14: API 15 보류로 대상 없음 | 확장 단계 |
| 7 | 내 판결 한 줄 요약(`summary`) 규칙 문장 틀 · 태그 수 제한 | **확정 (COMMON-16 · BE-26, 2026-10-06)**: 423줄 문장 틀(`{↑ 태그들}을 무겁게 보고 {↓ 태그들}을 감안한 판단` 등)을 확정. 방향별 태그 수는 **제한하지 않는다**(BE-26) — 요소마다 서로 다른 요약어가 붙어 많이 고르면 문장이 길어질 수 있지만, S-08에서 실제로 문제가 되면 상한(예: 최대 3개 + "외 N개")을 다시 검토한다 | S-08 판결 카드 |

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

### 8-2. 검수 API 계약 (7.4.7 · BE-33 · BE-41 ~ BE-43)

> **구현됨 (BE-41 ~ BE-43, 10/14).** 처음에는 관리자 화면(7.5.7 · FE-16, A)이 목 데이터로 먼저 만들 수 있도록 경로 · 요청 · 응답 형태를 먼저 정한 계약 초안이었다(v0.17, WBS R16). 지금은 구현 · 화면 연동까지 끝났고, 필드가 바뀌면 이 절을 고치고 화면 담당에게 알린다. ⚠️ 표시는 아직 팀 확인이 필요한 항목이다(8-3).

**검수 흐름**

```text
사건이 DRAFT로 들어옴                관리자 (S-19)
(오프라인 시드 적재 · 사건 등록 A-14)
사건 DRAFT ─┬─ 판단 요소 축 확정 (A-12) ───────────────┐
            ├─ 사건 내용 수정 (A-11) ──────────────────┤ 처음 바꾸는 순간 DRAFT → REVIEW
            ├─ AI 판결 후보 승인 · 반려 (A-9 · A-10) ──┤
            └─ 재판부 판결 후보 승인 · 반려 (A-9 · A-10)┘
                                                       ↓
                               공개 (A-13): 조건을 모두 채우면 PUBLISHED
                                                       ↓
                               공개 취소 (A-13-14): PUBLISHED → REVIEW (사용자 화면에서 숨김)
```

- **승인 = 그 사건의 공개 판결로 정한다.** 사건마다 공개 AI 판결 1개 · 공개 재판부 판결 1개(`uk_judgment_published`)라서, 승인하면 같은 주체의 기존 공개 판결은 비공개로 바뀐다(행은 지우지 않는다, REQ-079).
- **공개한 사건은 판결 교체(승인) · 내용 수정을 막는다**(8-3 #1). 반려는 사용자 화면에 영향이 없으므로 공개한 사건에서도 허용한다(공개 중인 후보만 막는다, A-10). 이미 체험한 사용자의 비교 결과(S-08)가 바뀌지 않게 하기 위해서다(REQ-046 "모든 사용자에게 같은 AI 판결"). 판단 요소의 가치관 축(A-12)은 체험 완료 시점에 고정 저장되므로(성향 산식 3-4) 공개 뒤에도 고칠 수 있다.
- **공개 취소(A-13-14, BE-118)는 숨김이다.** `PUBLISHED → REVIEW`로 돌려 사용자 화면에서 내린다. 사건 · 판결 · 사용자 체험 기록은 지우지 않는다. 권리 요청에 따른 삭제(파기)는 정책을 정한 뒤 2단계로 둔다 ⚠️.
- 원본 판결문 정보(`case_source`)는 이 API에서만 내려 준다(ERD 5장 원칙, 기술 스택 3장).
- **동시 요청**: 상태를 바꾸는 요청(A-9 ~ A-13 · A-13-14)은 트랜잭션을 시작하면 먼저 그 사건의 `legal_case` 행을 잠근다(`SELECT … FOR UPDATE`). 그 뒤에 사건 상태 · 후보 상태를 확인한다. 한 관리자가 "아직 공개 전"을 확인한 사이 다른 관리자가 공개해 버리는 경합을 막는다(사용자 API의 조건부 갱신 원칙과 같은 수준).
- **`INVALID_STATE` 사유**: 관리자 API의 `INVALID_STATE`는 `currentStatus`에 **사건 상태**만 담고, 이유는 `details`의 사유 코드로 구분한다 — `[{ "field": "case", "reason": "CASE_PUBLISHED" }]`. 사유 코드: `CASE_PUBLISHED`(공개한 사건) · `CASE_NOT_PUBLISHED`(공개 중이 아닌 사건, A-13-14) · `CANDIDATE_REJECTED`(이미 반려한 후보) · `CANDIDATE_PUBLISHED`(공개 중인 후보).
- 상태를 바꾸는 요청(A-9 ~ A-13 · A-13-14)과 원문 열람(A-7)은 감사 로그를 남긴다(7.4.5). 원문은 원본 판결문이라 읽기도 남긴다.
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
| A-13-14 | `POST /admin/cases/{caseId}/unpublish` | 사건 공개 취소 (사용자 화면에서 숨김) | BE-118 |
| A-14 | `POST /admin/case-imports` | 사건 등록 시작 (원본 판결문 → 파이프라인 실행) | BE-93 (적재는 2차) |
| A-15 | `GET /admin/case-imports/{importId}` | 사건 등록 진행 상황 | BE-93 |
| A-14-1 | `POST /admin/case-imports/{importId}/load` | 사건 등록 적재 (생성 결과 → 사건 DRAFT) | BE-93 2차 |

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
    "deidentifiedItems": ["인명", "지명", "사건번호"],
    "castRoles": { "prosecutor": "naero", "defense": "nambul" },
    "sensitiveFlags": ["MENTAL_HEALTH", "CAPITAL_PENALTY"]
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
  "publishReadiness": { "ready": false, "reasons": ["NO_PUBLISHED_AI", "NO_PUBLISHED_COURT"] },
  "script": { "exists": true, "reviewStatus": "PENDING", "publishable": false }
}
```

- 예시의 사건번호 · 법원명은 가상 값이다. 실제 값은 저장소 문서에 적지 않는다.
- `content`는 A-11에서 고칠 수 있는 필드와 읽기 전용 필드를 함께 담는다(고칠 수 있는 필드는 A-11 표).
- `candidates`는 최신 순. 반려(`REJECTED`)된 후보도 이력으로 함께 내려 준다.
- `penaltyLabel`은 API 14 `preToFinal.finalJudgmentText`(예: "징역 15년")와 같은 규칙으로 만든다.
- `factors[].summaryTagCandidates`(BE-80): 요약어 후보(ERD `factor.summary_tag_candidates`). 후보 계산을 켜지 않고 적재한 요소는 `null`. **제안일 뿐 요약어(`summaryTag`)를 바꾸지 않는다** — `suggested`가 있으면 관리자가 요약어를 그 값으로 맞출지 판단한다(요약어 수정 API는 아직 없음). `ambiguous: true`면 1 · 2순위가 붙어 제안하지 않은 것이다.
- `publishReadiness.reasons`는 A-13의 공개 조건 코드(A-13 표)다. 비어 있으면 `ready = true`.
- `script`는 사건 대본 요약이다(BE-108). **공개 조건이 아니라 경고용 값이다** — 미승인 대본은 사건 공개를 막지 않으므로(대본은 사건 내용이 아니라 체험 연출이다) `publishReadiness.reasons`에 넣지 않고 따로 내려준다. 대본이 없으면 `{ "exists": false, "reviewStatus": null, "publishable": null }`이고, 기존 사건은 모두 이 경우다. 내용은 A-13-10으로 본다.
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
- `raw_output`(모델 원본 출력)은 크기가 커서 내려 주지 않는다(8-3 #6).
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
- 사건이 `DRAFT`면 `REVIEW`로 바꾼다(8-3 #3).

| 거절 | 조건 |
| --- | --- |
| 404 `JUDGMENT_NOT_FOUND` | 판결이 없거나 `USER` |
| 409 `INVALID_STATE` | 사건이 이미 `PUBLISHED`(`CASE_PUBLISHED`, 공개 뒤 교체 금지), 후보가 `REJECTED`(`CANDIDATE_REJECTED`), 동시 승인으로 유니크 위반 |
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
- 사건이 `DRAFT`면 `REVIEW`로 바꾼다(8-3 #3).

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
| `castRoles` | (v0.67, BE-125) `{"prosecutor": …, "defense": …}`. 키는 이 둘만, 값은 `naero` · `nambul`을 하나씩. `null`은 거절(배정을 지우면 범죄 유형별 균형 계산에서 빠진다) |
| `sensitiveFlags` | (v0.67, BE-125) 플래그 이름 배열(`SensitiveFlag` 12종). 모르는 이름 · `null` 원소는 거절, 겹치면 하나로 줄인다. 모두 끌 때는 `[]`. `null`은 거절(`null`은 "분류 전"이라 되돌리지 않는다) |

- 죄명 · 적용 법조문 · 법정형 · 판단 요소 구성 · 형벌 규칙은 이 API로 바꾸지 않는다(파이프라인 · SQL 경로, WBS 7-5 8순위).
- 제목 중복은 저장 전에 확인하고, 그 사이 다른 요청이 같은 제목을 저장해 유니크 위반이 나도 `DUPLICATE_TITLE`로 돌려준다.
- 감사 로그 `CASE_UPDATE`에는 바꾼 필드 이름만 남긴다(`{"fields": ["shortIntro", "keywords"]}`). 값은 사건 본문이라 남기지 않는다. 단 `castRoles` · `sensitiveFlags`는 짧고 자동 결과를 사람이 뒤집은 기록이라 바꾼 값까지 남긴다(`{"fields": ["castRoles"], "castRoles": {…}}`, v0.67).
- 배역을 바꿔도 이미 만든 대본(`case_script`)은 다시 만들지 않는다 — 대사에 캐릭터가 들어가 있어 사건 배역과 어긋난다. 대본을 다시 만드는 경로는 BE-129에서 정한다.
- 사건이 `DRAFT`면 `REVIEW`로 바꾼다(8-3 #3).

| 거절 | 조건 |
| --- | --- |
| 400 `VALIDATION_ERROR` | 위 규칙 위반(필수 필드 · `keywords`에 `null` 포함), 모르는 필드, 빈 본문 |
| 404 `CASE_NOT_FOUND` | 사건이 없음 |
| 409 `INVALID_STATE` | 사건이 `PUBLISHED`(`CASE_PUBLISHED`, 공개 뒤 수정 금지) |
| 409 `DUPLICATE_TITLE` | 제목이 다른 사건과 겹침 |

### A-12. 판단 요소 가치관 축 확정 — `PUT /admin/factors/{factorId}/value-axis`

```json
{ "valueAxis": "PRINCIPLE_RELATION" }
```

- `valueAxis`는 `APOLOGY_SINCERITY` · `FAULT_STANDARD` · `PRINCIPLE_RELATION` · `ORDER_OPPORTUNITY` · `null`(어느 축에도 맞지 않음으로 확정).
- 성공하면 `value_axis_status = CONFIRMED`(ERD v1.15). 투표 기록(`value_axis_votes`)은 그대로 둔다.
- 사건이 `PUBLISHED`여도 고칠 수 있다(이미 끝난 체험의 성향은 바뀌지 않는다, 성향 산식 3-4).
- 감사 로그 `VALUE_AXIS_CONFIRM`에 사건 ID와 이전 · 바꾼 축을 남긴다(`{"caseId": 1, "from": "FAULT_STANDARD", "to": null}`).
- 사건이 `DRAFT`면 `REVIEW`로 바꾼다(8-3 #3).

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

- 공개한 사건은 바꿀 수 없다(A-11과 같은 규칙). 감사 로그 `CASE_PHOTO_UPLOAD`(`detail.type = "REPRESENTATIVE"`)를 남긴다(v0.62에서 정정 — `CASE_PHOTO_GENERATE`라는 값은 `AdminAuditAction`에 없다).

| 거절 | 조건 |
| --- | --- |
| 400 `INVALID_IMAGE_FILE` | 파일이 없음 · 비어 있음 · 허용하지 않는 형식 |
| 404 `CASE_NOT_FOUND` | 사건이 없음 |
| 409 `INVALID_STATE` | 이미 `PUBLISHED`(`CASE_PUBLISHED`) |
| 502 `FILE_UPLOAD_FAILED` | S3 업로드 실패, 또는 설정 없음(`S3_BUCKET_NAME`) |

#### 공개 뒤에 사진을 고쳐야 하면 — A-13-14(공개 취소) → 사진 교체 → A-13(다시 공개)

> v0.62, BE-103 결정(`docs/decisions/BE-103.md`). 사진 API 전체(A-13-3 · A-13-4 · A-13-9)에 해당한다.

- **공개 중에 사진만 바꾸는 경로는 두지 않는다.** 공개가 곧 사진 승인이라(BE-78 "승인은 사건 공개로 갈음한다") 그 경로를 열면 검수를 거치지 않은 사진이 바로 사용자에게 보인다. 공개 뒤 내용 수정 · 판결 교체를 막는 8-3 #1과도 같은 기준이다.
- 내리는 동안 그 사건은 목록 · 상세 · 체험에서 사라지고 **이미 끝낸 체험의 결과도 404**가 된다(A-13-14). 교체할 파일을 먼저 준비한 뒤 내리고, 교체가 끝나면 바로 다시 공개한다.
- 다시 공개하면 `published_at`이 새로 기록돼 **사건 목록(API 1, 공개일 내림차순)에서 맨 위로 올라온다.** 사진 한 장 때문에 "새 사건"처럼 보이는 것을 감수하는 절차다.
- 사진은 공개 조건(`publishReadiness`)에 들어가지 않으므로, 사진을 바꾸거나 지운 것 때문에 다시 공개가 막히지는 않는다.
- 공개 취소 사유(`reason`)에 사진 교체 때문임을 적는다(예: `"대표사진 교체"`) — 감사 로그에서 권리 요청으로 내린 사건과 구분된다.
- 내린 김에 판결(A-9) · 내용(A-11)까지 바꾸지 않는다(8-3 #8 운영 규칙). 사진만 바꾼다.

### A-13-4. 사건 발단 · 결론 사진 업로드 — `POST /admin/cases/{caseId}/photos/incident` (multipart/form-data)

> **역할 칸 1단계(v0.48, BE-78 2차 검토 · BE-101)**: 발단 · 결론 사진은 사건마다 역할별로 1장만 둔다
> (`uk_case_incident_photo_case_role`). `role`을 새로 받고, 같은 역할로 다시 올리면 새 행을 만들지 않고
> 기존 사진을 교체한다(id 유지) — "그 사건 사진 개수"로 채번하던 `display_order` 약속은 쓰지 않는다
> (결정: `docs/decisions/BE-78.md`).

사건 안에서 쓰는 발단 또는 결론 사진 한 장을 올려 `case_incident_photo`에 기록한다(역할별 1장, 같은 역할로 다시 올리면 교체). 201과 함께 저장된 사진을 돌려준다.

| 필드(form) | 규칙 |
| --- | --- |
| `file` | 필수. A-13-3과 같은 형식 · 크기 제한 |
| `role` | 필수. `ORIGIN`(발단) 또는 `CONCLUSION`(결론) |
| `caption` | 선택, 200자 이하. 화면에 보일 설명 |
| `prompt` | 선택, 자유 길이. 관리자가 이 사진을 만들 때 참고한 프롬프트(A-13-2 추천 그대로 또는 고친 것) — 감사 · 재참고용으로만 남긴다 |

응답 201:

```json
{ "id": 12, "imageUrl": "https://…/case-photos/case-7/incident-origin-….jpg", "caption": "다툼이 격해진 순간", "role": "ORIGIN", "displayOrder": 0, "createdAt": "2026-10-20T09:00:00Z" }
```

- `displayOrder`는 1단계 동안 `role`과 함께 유지하는 옛 순번 칸이다(`ORIGIN` = 0, `CONCLUSION` = 1). 다음 단계에서 뺀다 — 화면은 `role`을 본다.
- S3 객체 키(`s3_key`)는 내부 기록용이라 응답에 내려주지 않는다. `prompt`는 관리자가 보냈을 때만 들어가고, 응답에는 내려주지 않는다(A-13-5 목록도 마찬가지 — 내부 기록 확인은 DB로 한다).
- 교체 때 이전 S3 객체는 지우지 않는다 — 업로드 키에 시각이 붙어 매번 새 키이므로 캐시로 옛 사진이 다시 보이지 않는다.
- 공개한 사건은 추가 · 교체할 수 없다(고쳐야 하면 A-13-3의 "공개 뒤에 사진을 고쳐야 하면" 절차). 감사 로그 `CASE_PHOTO_UPLOAD`(`detail.type = "INCIDENT"`, `detail.role`, `detail.photoId`)를 남긴다.

| 거절 | 조건 |
| --- | --- |
| 400 `INVALID_IMAGE_FILE` | A-13-3과 같음 |
| 400 `VALIDATION_ERROR` | `role`이 없거나 `ORIGIN` · `CONCLUSION`이 아님, `caption` 200자 초과 |
| 404 `CASE_NOT_FOUND` | 사건이 없음 |
| 409 `INVALID_STATE` | 이미 `PUBLISHED`(`CASE_PUBLISHED`) |
| 502 `FILE_UPLOAD_FAILED` | A-13-3과 같음 |

### A-13-5. 사건 발단 · 결론 사진 목록 — `GET /admin/cases/{caseId}/photos/incident`

사건에 쌓인 발단 · 결론 사진을 돌려준다(A-13-4 응답과 같은 형식의 배열, `prompt` 제외). 공개한 사건도 조회만은 된다.

```json
[
  { "id": 12, "imageUrl": "…", "caption": "다툼이 격해진 순간", "role": "ORIGIN", "displayOrder": 0, "createdAt": "…" }
]
```

- `role`이 `null`인 행은 역할 칸 도입(v0.48) 이전에 사건당 3번째 이상 올라간 사진이다. 자리가 둘(발단 · 결론)뿐이라 역할을 매길 수 없어 신문 지면 · 공개 응답에서는 빠진다 — 이 목록에서만 정리 대상으로 노출된다. **A-13-9는 `{role}`(`ORIGIN` · `CONCLUSION`)로만 사진을 찾으므로 `role: null`인 행은 이 API로 지울 수 없다** — 정리가 필요하면 관리자가 DB에서 직접 확인한다(3단계 전 점검 항목, `docs/decisions/BE-78.md`).

| 거절 | 조건 |
| --- | --- |
| 404 `CASE_NOT_FOUND` | 사건이 없음 |

### A-13-9. 발단 · 결론 사진 삭제 — `DELETE /admin/cases/{caseId}/photos/incident/{role}` (v0.48, BE-78 2차 검토 · BE-101)

> 번호가 A-13-6 ~ 8(신문 지면) 뒤인 이유: 신문 지면 API(BE-92)가 먼저 A-13-6 ~ 8을 썼고, 이 삭제
> API는 그 뒤에 추가됐다. A-13-4 · 5(업로드 · 목록)와 같은 사진 API군이다.

본문 없음. `{role}`(`ORIGIN` 또는 `CONCLUSION`)에 해당하는 사진 한 장을 지운다. 성공하면 204(본문 없음). S3 객체는 지우지 않는다(A-13-4와 같은 원칙 — 재업로드 때 새 키를 쓰므로 운영에 지장 없다, 정리는 후속 과제).

- 공개한 사건은 지울 수 없다(업로드와 같은 규칙, 고쳐야 하면 A-13-3의 "공개 뒤에 사진을 고쳐야 하면" 절차). 감사 로그 `CASE_PHOTO_DELETE`(`detail.role`)를 남긴다.

| 거절 | 조건 |
| --- | --- |
| 400 `VALIDATION_ERROR` | `role`이 `ORIGIN` · `CONCLUSION`이 아님 |
| 404 `CASE_NOT_FOUND` | 사건이 없음 |
| 404 `INCIDENT_PHOTO_NOT_FOUND` | 그 역할의 사진이 없음 |
| 409 `INVALID_STATE` | 이미 `PUBLISHED`(`CASE_PUBLISHED`) |

### A-13-6. 신문 지면 생성 — `POST /admin/cases/{caseId}/paper/generate`

사건 내용(`overview` · `factor` · `chargeName`)을 바탕으로 Gemini가 메인 화면 신문 지면 콘텐츠를 짓는다(BE-92, COMMON-41). 이미 있으면 새로 만들지 않고 내용을 덮어쓴다. 본문 없음.

응답 200:

```json
{
  "desk": "사회부",
  "headline": "빌린 돈 문제로 다투다 벌어진 사건",
  "deck": "오랜 금전 다툼 끝에...",
  "photo": "https://…/case-photos/case-1/representative-….jpg",
  "caption": "다투는 두 사람",
  "article": ["문단 1", "문단 2"],
  "extraPhotos": [
    { "id": 12, "imageUrl": "…", "caption": "다툼이 격해진 순간", "role": "ORIGIN", "displayOrder": 0, "createdAt": "…" }
  ],
  "updatedAt": "2026-10-20T09:00:00Z"
}
```

- `photo`는 A-13-3으로 올린 사건 대표사진(`legal_case.thumbnail_url`)을 그대로 내려준다. 아직 안 올렸으면 `null`.
- `extraPhotos`는 A-13-5와 같은 배열 — `case_incident_photo`를 그대로 재사용한다(새로 저장하지 않는다, `docs/decisions/BE-92.md`). **v0.48부터는 `role`이 있는 사진만 발단(`ORIGIN`) → 결론(`CONCLUSION`) 순으로 내려준다** — 역할 칸 도입 이전에 사건당 3번째 이상 올라간 사진(`role: null`)은 여기서 빠진다(`docs/decisions/BE-78.md`). **정렬은 1단계 동안 `display_order`로 한다**(`ORIGIN` = 0, `CONCLUSION` = 1로 고정 채번되므로 결과적으로 역할 순과 같다) — `display_order`를 빼는 2 · 3단계에서 역할 기준 정렬로 바꾼다.
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
| 404 `CASE_NOT_FOUND` | 사건이 없음 |
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

### A-13-10. 사건 대본 조회 — `GET /admin/cases/{caseId}/script`

> **신설 (BE-108).** 증거조사에서 검사 · 변호인이 주고받는 대사. 파이프라인 `script` 단계(BE-107)가 만들고 적재(A-14-1)가 넣는다. **담는 것은 증거조사 대사뿐이다** — 1막 기사 해설 · 피고인신문 · 최종의견은 파이프라인 범위 밖이다(COMMON-65 · FE-33). 공개 서빙은 API 18(BE-109)이 승인된 대본만 가져다 준다.

```json
{
  "evidence": [
    { "factorId": 1, "label": "계약서에 서명했다", "role": "PROSECUTOR", "character": "naero",
      "text": "계약서에 서명한 사실은 분명합니다.", "attempts": 1, "validation": { "warnings": [] } },
    { "factorId": 2, "label": "돈을 돌려주지 않았다", "role": "DEFENSE", "character": "nambul",
      "text": null, "attempts": 3, "validation": { "warnings": ["증거 카드에 없는 사실을 말했습니다"] } }
  ],
  "cast": { "prosecutor": "naero", "defense": "nambul" },
  "model": "google:gemini-2.5-flash",
  "balanceWarning": null,
  "publishable": false,
  "stopped": false,
  "generatedAt": "2026-10-27T09:00:00+09:00",
  "updatedAt": "2026-10-27T09:12:00+09:00",
  "reviewStatus": "PENDING",
  "isPublished": false,
  "reviewedBy": null,
  "reviewedAt": null,
  "reviewNote": null
}
```

- `evidence[]`는 파이프라인이 만든 모양 그대로이고, **`label`만 서버가 더해 준다** — 어느 사실에 대한 대사인지는 사건의 판단 요소에 있다(파이프라인 `factorId` = `factor.display_order`, ERD v1.37). 화면이 A-6을 다시 불러 번호를 맞추지 않아도 된다. 요소가 지워졌거나 못 찾으면 그 줄의 `label`은 `null`이다.
- **생성에 실패한 줄은 `text`가 `null`**이다. `attempts`는 그 줄을 몇 번 만들어 봤는지, `validation.warnings`는 그 줄에 대한 경고다(어느 대사의 경고인지 줄 안에 있어 따로 연결할 필요가 없다).
- `publishable`이 `false`면 승인할 수 없다(A-13-11). 이유는 둘이고 둘 다 내려 준다 — 끝내 생성하지 못한 줄이 있거나(`text: null`), 호출 한도로 중간에 멈췄거나(`stopped: true`). 화면이 "다시 생성"과 "이어서 생성"을 구분해 안내할 수 있다.
- `generatedAt`은 파이프라인이 만든 시각이고 `updatedAt`은 적재 · 검수 시각이다. 시각은 모두 KST 오프셋.
- 검수 칸 다섯 개는 **A-8과 같은 이름 · 같은 형식**이다(검수 기록이 없으면 `reviewStatus: "PENDING"`, `reviewedBy`는 관리자 이메일). 화면이 판결 후보 검수 영역과 같은 부품을 쓸 수 있게 맞췄다.
- **`isPublished`는 사건 공개 여부와 별개다.** 공개한 사건에 대본만 늦게 들어와 승인될 수도 있고, 공개 전에 대본만 승인돼 있을 수도 있다.

| 거절 | 조건 |
| --- | --- |
| 404 `CASE_NOT_FOUND` | 사건이 없음 |
| 404 `SCRIPT_NOT_FOUND` | 사건은 있는데 대본이 아직 없음 (A-13-7의 `PAPER_NOT_FOUND`와 같은 구분) |

### A-13-11. 사건 대본 승인 — `POST /admin/cases/{caseId}/script/approve`

승인 = 이 대본을 공개한다. 요청 본문 없음. 응답은 A-13-10과 같은 형식(`reviewStatus: "APPROVED"` · `isPublished: true`).

- **`publishable`이 `false`면 거절한다.** 증거 카드와 대사가 1:1로 맞지 않는 상태로 공개하면 체험이 깨진다(BE-107 계약). DB에도 같은 제약을 두었다(`chk_case_script_publishable_approved`).
- **사건 상태(`legal_case.status`)는 바꾸지 않는다.** 판결 후보 승인(A-9)은 DRAFT → REVIEW로 올리지만, 대본은 사건 내용이 아니라 체험 연출이라 사건 공개 흐름과 따로 둔다.
- 감사 로그 `CASE_SCRIPT_APPROVE`.

| 거절 | 조건 |
| --- | --- |
| 404 `CASE_NOT_FOUND` · `SCRIPT_NOT_FOUND` | A-13-10과 같음 |
| 409 `INVALID_STATE` | `publishable: false` — 대본을 다시 생성해야 한다 |

### A-13-12. 사건 대본 반려 — `POST /admin/cases/{caseId}/script/reject`

```json
{ "reason": "검사 쪽 대사가 변호인보다 두 배 깁니다." }
```

| 필드 | 규칙 |
| --- | --- |
| `reason` | 필수, 앞뒤 공백을 잘라 1 ~ 500자 (A-10과 같다) |

응답은 A-13-10과 같은 형식(`reviewStatus: "REJECTED"` · `isPublished: false` · `reviewNote`에 사유).

- **승인한 대본도 반려로 내릴 수 있다.** 판결 후보 반려(A-10)는 공개 중인 후보를 막지만(교체할 다른 후보가 있다), 대본은 사건마다 하나뿐이라 막으면 잘못 승인한 대본을 되돌릴 방법이 없어진다.
- 감사 로그 `CASE_SCRIPT_REJECT`.

| 거절 | 조건 |
| --- | --- |
| 400 `VALIDATION_ERROR` | 사유 빈 값 · 500자 초과 |
| 404 `CASE_NOT_FOUND` · `SCRIPT_NOT_FOUND` | A-13-10과 같음 |

### A-13-13. 사건 대본 대사 수정 — `PATCH /admin/cases/{caseId}/script/evidence/{factorId}`

> **신설 (BE-108).** 숫자 하나 · 말투 때문에 모델을 수십 번 다시 부르는 것보다 싸다. 신문 지면 수정(A-13-8)과 같은 자리의 기능이다.

```json
{ "text": "그 돈은 빌린 것이 아니라 받은 것입니다." }
```

| 필드 | 규칙 |
| --- | --- |
| `text` | 필수. 앞뒤 공백을 잘라 1 ~ 100자, 2줄 이하 (파이프라인 `script_generation.py`의 `MAX_CHARS` · `MAX_LINES`와 같은 값) |

- `{factorId}`는 **파이프라인 번호**(= `factor.display_order`)다. 대사는 판단 요소마다 한 줄이고 어느 측이 말하는지는 요소의 방향에서 정해지므로, 이 번호 하나로 줄이 정해진다.
- 금지어 · 원문에 없는 숫자 같은 **의미 검사는 하지 않는다.** 그 판단을 사람이 하라고 두는 기능이다.
- 고친 줄은 `validation`이 `{ ok: true, manual: true, warnings: [...] }`가 되고 `editedAt`이 붙는다. **경고는 남긴다** — 사람이 보고 판단할 값이다. 화면은 `manual`로 "사람이 고침"을 표시한다.
- **고치면 검수 기록이 지워져 다시 대기가 된다.** 내용이 바뀌었는데 지난 승인이 남아 있으면 검수하지 않은 대사가 공개된다.
- **`publishable`을 다시 계산한다.** 파이프라인과 같은 식(`not stopped and not failed`)을 다시 적용한다 — 빈 대사를 사람이 채워 모든 줄이 차면 승인할 수 있게 올라가고, 호출 한도로 멈춘 대본(`stopped: true`)은 **줄 자체가 모자라** 고쳐도 올라가지 않는다(그때는 다시 생성해야 한다).
- 감사 로그 `CASE_SCRIPT_UPDATE`. **고친 대사 본문은 남기지 않는다**(어느 요소를 고쳤는지만) — 원문 · 모델 출력과 같은 취급이다.
- `balanceWarning`은 **고치기 전 기준**으로 남는다(검사 · 변호인 분량은 파이프라인이 생성할 때 한 번 재는 값이다). 화면은 "수정 전 기준"으로 읽으면 된다.
- 길이는 파이프라인과 같은 방식으로 센다 — **줄바꿈은 글자 수에 넣지 않는다**. 50자 + 50자 두 줄은 통과한다.

응답은 A-13-10과 같은 형식.

| 거절 | 조건 |
| --- | --- |
| 400 `VALIDATION_ERROR` | 빈 값 · 100자 초과 · 3줄 이상 |
| 404 `CASE_NOT_FOUND` · `SCRIPT_NOT_FOUND` | A-13-10과 같음 |
| 404 `SCRIPT_LINE_NOT_FOUND` | 대본에 그 판단 요소의 대사가 없음 |

### A-13-14. 사건 공개 취소 — `POST /admin/cases/{caseId}/unpublish`

> **신설 (BE-118).** 공개한 뒤에 내려야 할 사건(권리자 · 본인 요청, 사전 학습 의심 근거가 더 모인 경우 등)을 사용자 화면에서 숨긴다. **지우지 않는다** — 삭제(파기)는 정책을 정한 뒤 2단계로 둔다.

```json
{ "reason": "권리자 요청으로 내림" }
```

| 필드 | 규칙 |
| --- | --- |
| `reason` | 필수. 앞뒤 공백을 잘라 1 ~ 200자. 감사 로그에만 남는다(사용자에게 보이지 않는다) |

- 사건을 `PUBLISHED → REVIEW`로 돌리고 `published_at`을 지운다. 200, A-6과 같은 형식.
- **사건 · 판결 · 사용자 체험 기록은 그대로 둔다.** 사용자 API는 `PUBLISHED`만 읽으므로(API 1 · 2 · 16 등, 체험 · 결과 API는 체험 조회에서 사건 공개 여부를 먼저 본다) 목록 · 상세 · 체험 · 결과에서 바로 사라진다 — 없는 사건과 같은 404 `CASE_NOT_FOUND`.
- 공개 판결(`judgment.is_published`)은 그대로 둔다. 다시 공개(A-13)할 때 공개 조건을 다시 판정한다.
- `REVIEW`로 돌아가므로 내용 수정(A-11) · 판결 교체(A-9)가 다시 열린다. 다시 공개하면 이미 체험한 사용자의 비교 결과가 바뀔 수 있다 — 8-3 #8.
- 감사 로그 `CASE_UNPUBLISH`, `detail`에 `reason`.

| 거절 | 조건 |
| --- | --- |
| 400 `VALIDATION_ERROR` | 사유 없음 · 공백만 · 200자 초과 (`details[0].field = "reason"`) |
| 404 `CASE_NOT_FOUND` | 사건이 없음 |
| 409 `INVALID_STATE` | 공개 중이 아님(`CASE_NOT_PUBLISHED`) |

### A-14. 사건 등록 시작 — `POST /admin/case-imports`

> **1차 구현됨 (BE-93).** 파이프라인(`tools/ai-judgment`)은 **자바로 옮기지 않았다** — 같은 서버의 내부 컨테이너로 띄우고 백엔드가 내부망으로 부른다(COMMON-50, 결정: `docs/decisions/COMMON-50.md`). 화면(FE-45)은 이 계약의 목으로 먼저 만들었고, `adminMockApi.js`와 이 절은 함께 고친다.
>
> **아직 남은 것**: 적재(파이프라인 결과 → 사건 DRAFT 생성)는 BE-93 2차에서 한다. 그때까지 A-15는 `SUCCEEDED`가 되어도 `caseId`가 `null`이다.

원본 판결문을 받아 파이프라인(비식별화 · 구조화 → 재판부 판결 정리 → 사전 학습 점검 → 참고 자료 검색 → AI 판결 생성 → 회차 선택 → 가치관 축 분류)을 서버에서 실행한다. 오래 걸리므로 **작업만 만들고 202로 바로 돌려준다.**

**적재는 파이프라인이 하지 않는다.** 파이프라인에도 적재(`load`) 단계가 있지만 운영 DB에 직접 쓰는 단계라 서비스에서 **어떤 요청으로도 켜지지 않게 막아 두었다**(COMMON-50). 그래서 A-15의 `load`는 1차 · 2차 모두 항상 `SKIPPED`다. 사건을 만드는 일은 검수 흐름을 쥔 서버가 BE-93 2차에서 맡는다.

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
| `crimeType` | 필수. `MURDER` · `FRAUD` · `INJURY` · `ROBBERY` · `THEFT` (`ROBBERY` · `THEFT`는 v0.55) |
| `sources` | 필수. 1 ~ 3건, 심급(`courtLevel`: `FIRST` · `APPEAL` · `SUPREME`)이 서로 달라야 한다 |
| `sources[].caseNumber` | 필수, 50자 이하. 이미 등록된 사건번호면 409 `DUPLICATE_SOURCE` |
| `sources[].originalText` | 필수, 200,000자 이하. 원본 판결문이라 요청 본문 로그에 남기지 않는다 |
| 그 밖 | `courtName` · `decidedAt`(YYYY-MM-DD) · `isFinal` · `sourceOrg` · `note`는 선택. 사건 원본 정보(`case_source`)에 저장한다 |

응답 202: `{ "importId": 1, "status": "QUEUED" }`

| 거절 | 조건 |
| --- | --- |
| 400 `VALIDATION_ERROR` | 위 규칙 위반. `details`에 `[{ "field": "sources[0].originalText", "reason": "INVALID" }]` |
| 409 `DUPLICATE_SOURCE` | 이미 등록한 사건번호 |
| 409 `IMPORT_IN_PROGRESS` | 실행 중인 등록이 있음 (한 번에 하나만. 외부 AI 호출 한도 보호). 서버와 파이프라인 서비스가 각각 막는다 — 서버 검사를 동시에 통과해도 서비스가 둘째를 거절한다(COMMON-50) |
| 502 `IMPORT_UNAVAILABLE` | 파이프라인 서비스에 닿지 못함, 또는 `PIPELINE_SERVICE_URL`을 설정하지 않은 서버 |

- 감사 로그 `CASE_IMPORT`를 남긴다(원문은 남기지 않고 사건번호 · 심급만).

### A-15. 사건 등록 진행 상황 — `GET /admin/case-imports/{importId}`

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
- `stages[].name`: `extract` · `court` · `contamination` · `rag` · `generate` · `select` · `axis` · `load`. 값은 `PENDING` · `RUNNING` · `DONE` · `SKIPPED` · `FAILED`.
  - **항상 `SKIPPED`인 단계가 셋 있다**: `load`(적재는 서버가 한다, A-14 참고) · `rag` · `contamination`. 서비스에서 끄고 시작하기 때문이며(COMMON-50) 오류가 아니다. 화면도 건너뜀으로 그린다.
- `SUCCEEDED`일 때 `caseId`는 **서버 구현 차수에 따라 다르다.** 클라이언트는 `caseId`가 있을 때만 검수 화면으로 보낸다.

  | 차수 | `SUCCEEDED` 응답 | 뜻 |
  | --- | --- | --- |
  | **BE-93 1차** (지금) | `caseId: null` | 파이프라인 생성까지 끝났고 **사건은 아직 만들어지지 않았다** |
  | **BE-93 2차** | `caseId: <사건 ID>` | **A-14-1로 적재한 뒤** 채워진다. 사건이 `DRAFT`로 만들어져 A-5 목록 · A-6 상세로 이어서 검수한다 |

  1차 범위에서 실제 모드의 사건 등록 메뉴는 열지 않는다(`adminMenu.js`의 `serverReady: false`, `docs/information-architecture.md`). 2차 전에 메뉴를 열려면 `caseId == null`을 다루는 처리가 화면에 먼저 있어야 한다.
- 단계별 상태는 파이프라인만 알고 있어 서버가 마지막으로 받은 값을 저장해 둔다. 끝난 작업은 파이프라인에 다시 묻지 않고 그 값을 그대로 내려준다.
- `FAILED`이면 `errorCode` · `message`를 내려 준다. `message`에 원문 · 모델 출력을 담지 않는다.

| 거절 | 조건 |
| --- | --- |
| 404 `IMPORT_NOT_FOUND` | 등록 작업이 없음 |

### A-14-1. 사건 등록 적재 — `POST /admin/case-imports/{importId}/load`

> 번호: A-16 ~ A-41은 8-4(운영 메뉴 API, 초안)가 예약해 두어, A-14(사건 등록)에 딸린 API라는 뜻으로 A-14-1을 쓴다 (A-13-N과 같은 방식).

생성 결과를 **사건으로 만든다.** A-15가 `SUCCEEDED`가 된 뒤 관리자가 누른다.

> **왜 A-15에서 자동으로 하지 않는가**: A-15는 화면이 2초마다 부르는 조회다. 거기서 적재하면 응답이 늦어지거나 끊겼을 때 같은 사건이 여러 번 만들어질 수 있다. 쓰기는 명시적인 호출 하나로 둔다.

요청 본문은 없다. 응답은 **A-15와 같은 형식**이고, 적재가 끝났으므로 `caseId`가 채워져 있다.

```json
{ "importId": 1, "status": "SUCCEEDED", "stages": [ ... ], "caseId": 42,
  "errorCode": null, "message": null, "createdAt": "...", "finishedAt": "..." }
```

**무엇이 만들어지는가**

| 표 | 내용 |
| --- | --- |
| `legal_case` | 사건 (`DRAFT`) |
| `case_section` · `penalty_rule` | 섹션 · 형벌 규칙 (`display_order` = 배열 순서) |
| `factor` | 판단 요소. **`display_order`에 파이프라인의 `factorId`를 그대로 넣는다** — 판결의 작용 요소가 이 번호로 요소를 찾는다 |
| `case_source` | 원본 판결문. **원문은 등록 요청에서 받아 둔 값을 옮긴다**(파이프라인은 원문을 돌려주지 않는다, FR-5-3) |
| `judgment` (AI · COURT) + `judgment_factor` | 판결 후보 둘. **공개하지 않은 상태**로 들어간다 |
| `ai_generation` | 어느 모델 · 몇 번째 회차를 왜 골랐는지 (`review_status: PENDING`) |

- **판결은 공개 상태로 만들지 않는다.** 관리자가 A-9에서 승인해야 공개 후보가 된다 — 적재가 바로 공개하면 검수를 건너뛴다.
- 재판부 판결 초안이 없으면 그 부분만 건너뛰고 사건은 만들어진다.
- 모두 한 트랜잭션이다. 중간에 실패하면 **아무것도 만들어지지 않는다** — 반쯤 만들어진 사건은 관리자가 고칠 수도 지울 수도 없다.

| 거절 | 조건 |
| --- | --- |
| 404 `IMPORT_NOT_FOUND` | 등록 작업이 없음 |
| 409 `IMPORT_NOT_READY` | 생성이 아직 끝나지 않았거나, 이미 적재한 등록 |
| 409 `DUPLICATE_TITLE` | 같은 제목의 사건이 이미 있음. 제목을 바꿔 다시 등록한다 |
| 422 `IMPORT_RESULT_INVALID` | 생성 결과를 사건으로 만들 수 없음. `details[].field`에 어느 값이 문제인지 담는다 (예: `case.factors[2].label`, `report.factorExtras[factorId=5].summaryTag`) |
| 502 `IMPORT_UNAVAILABLE` | 생성 결과를 가져오지 못함 |

- 감사 로그 `CASE_IMPORT_LOAD`를 남긴다(만들어진 사건 번호와 등록 번호만).
- 적재가 끝나면 파이프라인에 남은 작업을 지운다 — 판결문 원문이 서비스 볼륨에 남지 않게 한다(FR-5-3).

**에러 코드**: 1-5의 `JUDGMENT_NOT_FOUND` · `FACTOR_NOT_FOUND` · `DUPLICATE_TITLE` · `PUBLISH_NOT_READY`(v0.17 추가)와 공통 코드를 쓴다. 사건 등록(A-14 · A-14-1 · A-15)은 `DUPLICATE_SOURCE` · `IMPORT_IN_PROGRESS` · `IMPORT_NOT_FOUND` · `IMPORT_UNAVAILABLE` · `IMPORT_NOT_READY` · `IMPORT_RESULT_INVALID`를 더 쓴다(BE-93에서 1-5 목록에 추가했다).

- `CASE_NOT_FOUND`는 관리자 API에서 "사건이 없음"만 뜻한다(사용자 API는 "없거나 `PUBLISHED`가 아님").

### 8-3. 검수 API 확인 항목

| # | 항목 | 이 초안의 제안 | 확인할 사람 |
| --- | --- | --- | --- |
| 1 | 공개 뒤 판결 교체 · 내용 수정 | ✅ 공개 중에는 막는다(409 `CASE_PUBLISHED`). 고쳐야 하면 공개 취소(A-13-14) → 수정 → 다시 공개(A-13) — 사진 교체(#9, BE-103)와 같은 절차. 공개 취소 뒤 판결 교체는 #8(BE-130) | 팀 |
| 2 | 공개 취소 | ✅ 숨김으로 둔다(A-13-14, BE-118) — `PUBLISHED → REVIEW`, 기록은 지우지 않는다. 권리 요청에 따른 삭제(파기)는 정책을 정한 뒤 2단계 (10/28 팀 결정) | 팀 |
| 3 | `REVIEW` 상태 | ✅ 관리자가 처음 바꾸는 순간(A-9 ~ A-12) 자동으로 `DRAFT → REVIEW`. "누가 손대기 시작한 사건"을 목록에서 구분하는 용도. `REVIEW → DRAFT`로 되돌리는 경로는 두지 않는다 (#111 리뷰, A 동의) | A 김해송 (S-19 목록) |
| 4 | 재판부 후보 검수 상태 · 반려 사유 저장 | ✅ V16(BE-41)에서 `judgment`에 `review_status` · `reviewed_by_admin_id` · `reviewed_at` · `review_note`를 두어 AI · 재판부를 같은 방식으로 검수한다(ERD v1.22). 응답 필드는 그대로 | B |
| 5 | 검수자 기록 | ✅ 관리자 ID(`judgment.reviewed_by_admin_id`)로 남긴다. `ai_generation.reviewed_by`(이름)는 예전 기록용으로 둔다 | B |
| 6 | 모델 원본 출력(`raw_output`) | ✅ 내려 주지 않는다. 필요하면 A-8에 쿼리 옵션으로 추가 (#111 리뷰, A 동의) | A 김해송 (S-19 상세) |
| 7 | 관리자 OAuth(BE-40, D)와 체인 | ✅ **제외(BE-40)** — 관리자 로그인은 이메일 · 비밀번호(세션, BE-39 · BE-67 · BE-68)로 충분해 제외 (B 김현중 확인, 10/20). ~~관리자 체인에 `oauth2Login` 자리 연결은 B, 성공 처리 · 허용 목록은 D (#108, WBS v1.9 7-2-1)~~ | D 정민석 |
| 8 | 공개 취소 뒤 판결 교체 | ⚠️ 미정. 공개 취소하면 `REVIEW`라서 판결 교체(A-9) · 내용 수정(A-11)이 다시 열린다. 판결을 바꿔 다시 공개하면 이미 체험한 사용자의 비교 결과(S-08)가 바뀐다(#1의 이유와 충돌). 1단계는 막지 않고 운영 규칙("내린 사건의 판결은 바꾸지 않고, 바꿔야 하면 새 사건으로 적재")으로 둔다 — 코드로 막을지 팀 확인 (BE-118) | 팀 |
| 9 | 공개 뒤 사진 교체 | ✅ 별도 경로를 두지 않는다(BE-103) — 공개 중 사진 업로드 · 삭제는 그대로 409(`CASE_PUBLISHED`)로 막고, 고쳐야 하면 A-13-14(공개 취소) → 교체 → A-13(다시 공개)으로 한다. 공개가 곧 사진 승인이라(BE-78) 예외 경로를 열면 검수 없는 사진이 노출된다. 절차의 대가(내린 동안 완료 체험 결과도 404, 다시 공개하면 목록 맨 위로)는 A-13-3에 적었다 | 팀 |

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

> 판결 성향 테스트(S-15 · S-16) · 공유 결과 · 마이페이지(S-17)용 API다. 테이블은 [ERD 8장](erd.md)(V15, #124), 채점 · 체험 성향 · 종합은 [성향 산식](personality-scoring.md), 문구는 [성향 테스트 콘텐츠](personality-test-content.md)를 따른다. 9-2 확인 항목은 구현(BE-72)과 화면 실제 API 연동으로 확정했다.

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
{  "type": { "shareId": "Qm7xK2pa", "name": "그럴 수도 있지형", "…": "…" },  "axisScores": { "APOLOGY_SINCERITY": 0.75, "FAULT_STANDARD": 0.625, "PRINCIPLE_RELATION": 1.0, "ORDER_OPPORTUNITY": 0.5 },  "testedAt": "2026-10-14T14:00:00+09:00",  "completedExperienceCount": 2}
```

- `completedExperienceCount`(v0.50): 응시한 시점에 완료한 사건 수(완료한 회차가 하나라도 있는 사건, P-5 도전과제 `completed`와 같은 셈). "체험 n건 뒤에 다시 한 테스트"를 남기려고 저장한다. v0.50 전에 저장한 결과는 `null`

| # | 메서드 | 경로 | 설명 | 화면 |
| --- | --- | --- | --- | --- |
| P-1 | GET | `/personality/questions` | 문항 목록 | S-15 |
| P-2 | POST | `/personality/results` | 응답 제출 → 결과 저장 | S-15 → S-16 |
| P-3 | GET | `/personality/results/me` | 내 현재 결과 | S-16 |
| P-4 | GET | `/personality/types/{shareId}` | 공유용 유형 설명 (공개) | 공유 결과 |
| P-5 | GET | `/my-page` | 마이페이지 (테스트 결과 · 판결 성향 · 체험 목록 · 출석 일수) | S-17 |
| P-6 | POST | `/attendance` | 출석 기록 (하루 한 번, v0.41) | 사이트 진입 |
| P-7 | PUT | `/profile-deco` | 프로필 꾸미기 저장 (v0.42) | S-17 |
| P-8 | PATCH | `/profile-settings` | 프로필 선택 설정 저장 — 공개 성향카드 · 프로필카드 아래 성향 · 한 줄 소개 · 카드 아래 줄 · 레벨 표시, 보낸 칸만 (v0.52, v0.65) | S-17 |
| P-9 | POST | `/collection` | 수집 기록 — 완료한 사건에서 얻은 일러스트 · 의상 (v0.63) | S-09 · S-17 |
| P-10 | POST | `/collection/seen` | 컬렉션 "봤음" (v0.63) | S-17 |
| P-11 | POST | `/achievements/seen` | 도전과제 "봤음" (v0.63) | S-17 |

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
{  "answers": [    { "questionId": 1, "value": 0 },    { "questionId": 2, "value": 3 }  ],  "baseline": false}
```

- `value`: 0 매우 A · 1 조금 A · 2 보통 · 3 조금 B · 4 매우 B. 서버는 그 문항의 `display_order = value + 1`인 선택지로 바꿔 채점한다(성향 산식 2장).
- `baseline`(v0.50, 생략하면 `false`): **성향카드 다시 정하기**. 사용자당 한 번만 받는다. `true`인 결과는 처음 성향(P-5 `initialPersonality`)을 덮어쓰지 않고 다시 정한 결과(`baselineRetake.result`)로 따로 남는다. 테스트한 적이 없으면 이 제출이 첫 테스트(처음 성향)가 되고 다시 정하기 횟수는 쓰지 않는다(`false`와 같게 처리).

**응답 201**: 결과 공통 형식(9-1).

- 쿠키가 없거나 DB에 없는 익명 ID면 `anonymous_user`를 만들고 `Set-Cookie`로 내려준다(API 2와 같음). **요청 검증을 통과한 뒤에만** 발급한다. 400이면 사용자를 만들지 않고 쿠키도 내려 주지 않는다.
- **재응시**: 이전 현재 결과는 이력으로 남기고 새 결과가 현재 결과가 된다(성향 산식 5장). 같은 사용자의 제출이 동시에 와도 차례로 처리되고, 나중에 처리된 것이 현재 결과다.
- **일반 응시는 언제든, 횟수 제한 없이** 할 수 있다. 다시 정하기(`baseline: true`)도 새 결과가 현재 결과가 되는 것은 같다.
- 저장하는 것은 유형 · 축 점수 · 답(`questionId` · 선택지 ID) · 응시 시점 완료 사건 수 · 다시 정하기 여부다. 응답에는 답을 다시 넣지 않는다.

| 거절 | 조건 |
| --- | --- |
| 400 `VALIDATION_ERROR` | `answers` 없음, 활성 문항 중 빠진 문항 · 같은 문항 두 번, 없거나 비활성인 `questionId`, `value`가 0 ~ 4 정수가 아님(소수는 1-1 숫자 규칙대로 거절), `baseline`이 불리언이 아님 |
| 409 `BASELINE_RETAKE_USED` | `baseline: true`인데 이미 다시 정한 결과가 있음(v0.50). 결과를 저장하지 않는다 |

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
{  "personality": { "type": { "shareId": "Qm7xK2pa", "…": "…" }, "axisScores": { "…": "…" }, "testedAt": "2026-10-14T14:00:00+09:00", "completedExperienceCount": 4 },  "initialPersonality": { "source": "TEST", "type": { "shareId": "Qm7xK2pa", "…": "…" }, "axisScores": { "…": "…" }, "testedAt": "2026-10-10T20:00:00+09:00", "completedExperienceCount": 0 },  "baselineRetake": { "available": false, "result": { "type": { "…": "…" }, "axisScores": { "…": "…" }, "testedAt": "2026-10-14T14:00:00+09:00", "completedExperienceCount": 4 } },  "judgmentPersonality": {    "axisScores": { "APOLOGY_SINCERITY": 0.75, "FAULT_STANDARD": 0.11, "PRINCIPLE_RELATION": 0.83, "ORDER_OPPORTUNITY": null },    "type": null,    "experienceCount": 3  },  "history": [    { "caseId": 1, "title": "빌린 돈 문제로 찾아온 지인을 살해한 사건", "completedAt": "2026-10-14T15:00:00+09:00", "axisScores": { "APOLOGY_SINCERITY": 1.0, "FAULT_STANDARD": 0.0, "PRINCIPLE_RELATION": 1.0, "ORDER_OPPORTUNITY": null }, "type": null }  ],  "experiences": [    { "caseId": 1, "title": "빌린 돈 문제로 찾아온 지인을 살해한 사건", "crimeType": "MURDER", "status": "COMPLETED", "attemptNo": 1, "updatedAt": "2026-10-14T15:00:00+09:00" }  ],  "profile": { "attendanceDays": 3 },  "profileDeco": { "portrait": "/images/characters/standing/naero.webp", "frame": "wood", "background": "cream", "sashStyle": "long", "sashColorShort": "wine", "sashColorLong": "wine", "sashIconLeft": "none", "sashIconRight": "none", "bookmarkCharacter": "naero", "bookmarkFace": "happy", "bookmarkFaceStyle": "generated", "bookmarkColor": "bronze" },  "achievements": [    { "id": "first-case", "stat": "completed", "goal": 1, "value": 1, "cleared": true, "status": "CLEARED", "rewards": { "kinds": ["characters/scene"], "frames": ["wood"], "backgrounds": [], "sashStyles": ["long"], "sashColors": ["long:wine"], "sashIcons": [] }, "unlockedAt": "2026-10-14T15:00:10+09:00", "isNew": false },    { "id": "collect-5", "stat": "collected", "goal": 5, "value": 3, "cleared": false, "status": "IN_PROGRESS", "rewards": { "kinds": ["cases"], "frames": [], "backgrounds": ["news"], "sashStyles": [], "sashColors": [], "sashIcons": ["naero", "nambul"] }, "unlockedAt": null, "isNew": false },    { "…": "…" }  ],  "unlocked": { "kinds": ["characters/roles", "characters/standing", "characters/scene"], "frames": ["basic", "wood"], "backgrounds": ["cream"], "sashStyles": ["none", "short", "long"], "sashColors": ["short:wine", "long:wine"], "sashIcons": ["none", "star"] },  "collection": { "collected": 3, "items": [ { "kind": "illustration", "itemId": "case-1-origin", "caseId": 1, "acquiredAt": "2026-10-14T15:00:10+09:00", "isNew": true }, { "kind": "cast", "itemId": "naero:judge", "caseId": 1, "acquiredAt": "2026-10-14T15:00:10+09:00", "isNew": true }, { "…": "…" } ] },  "profileSettings": { "shownCard": "current", "profileCardType": "initial", "bio": "판결은 신중하게", "cardLine": "type", "cardLevel": false }}
```

| 필드 | 설명 |
| --- | --- |
| `personality` | 테스트 현재 결과(결과 공통 형식). 테스트 전이면 `null` → 화면은 테스트 안내. **대표 캐릭터는 이 유형**(성향 산식 4장) |
| `initialPersonality` | **처음 성향**(v0.50). 성향카드 화면 아래 "초심" 카드에 쓴다. 다시 응시해도 · 다시 정해도 바뀌지 않는다. 테스트한 적이 있으면 **첫 테스트 결과**(결과 공통 형식 + `source: "TEST"`). 테스트한 적이 없으면 **첫 판결**: 각 사건의 첫 회차 체험을 완료 순서로 **처음 3건** 평균한 유형(`source: "JUDGMENT"`, 모양은 `judgmentPersonality`와 같음 + `source`, `type`이 있을 때만). 나중에 테스트를 하면 그 첫 테스트가 대신한다. 둘 다 없으면 `null` |
| `baselineRetake` | **성향카드 다시 정하기**(v0.50). `result`: 다시 정한 결과(결과 공통 형식), 아직이면 `null`. `available`: 지금 다시 정할 수 있는지 — 첫 테스트가 있고(`initialPersonality.source`가 `TEST`) 아직 다시 정하지 않았을 때만 `true`. 빈 마이페이지면 `{ "available": false, "result": null }` |
| `judgmentPersonality` | 현재 판결 성향(성향 산식 4장). 각 사건의 **첫 회차**만, 완료 순서로 **최근 3건**의 축마다 체험별 축 점수 평균(v0.44 전에는 전체 평균). 값이 없는 축은 `null`("체험 자료 없음"). `type`은 **네 축 모두 값이 있을 때만** 있고 아니면 `null`. 값이 있으면 **유형 공통 형식(9-1) 그대로**다(`shareId`로 그 유형의 공유 링크로 이어 갈 수 있다). 화면은 "판결에서는 ○○○○"처럼 테스트 유형과 나란히 보여 준다. 완료한 체험이 없거나 네 축 모두 `null`이면 `judgmentPersonality` 자체가 `null`("종합 없음"). `experienceCount`는 **평균에 쓴 체험 수**(성향 기여(`experience_personality`)가 저장된 첫 회차 체험 중 최근 것, 최대 3)다. 종합 점수를 낸 체험과 같은 집합이고, 축 자료가 모두 비어 있는 체험도 센다. 7.6.7(체험 완료 시 성향 저장) 전에 완료해 기여가 없는 체험은 세지 않는다 |
| `history` | 성향 변화 흐름. 각 사건의 첫 회차 체험별 축 점수(값이 없는 축은 `null`), 완료 순. **`type`(v0.49)은 그 체험 한 건의 유형**이다 — `judgmentPersonality.type`과 같은 규칙으로 그 체험 하나만 넣어 정한다(**네 축 모두 값이 있을 때만**, 아니면 `null`, 값이 있으면 유형 공통 형식(9-1) 그대로). 내 결과 지면(`/me/cases/{caseId}`)의 "이 사건의 성향카드"에 쓴다. 최근 3건 평균인 `judgmentPersonality.type`과는 다를 수 있다 |
| `experiences` | 체험한 사건. 사건마다 최근 회차 하나, 최근 순. `status`로 결과 다시 보기(`COMPLETED`) · 이어 하기를 나눈다(1-6) |
| `profile.attendanceDays` | 누적 출석 일수(P-6, v0.41). 기록이 없거나 빈 마이페이지면 `0`. 로그인 전에는 기기(익명 ID)마다 따로 센다 |
| `profileDeco` | 프로필 꾸미기(v0.42, BE-96). 저장한 적이 없거나 빈 마이페이지면 기본값(내로 스탠딩 · `basic` · `cream` · 책갈피 없음 · 내로 · `happy` · `generated` · `bronze`). **잠긴 값이어도 거절하지 않고 그대로** 내려 준다(표가 바뀌어 잠긴 경우 화면이 기본값으로 보여 준다). 필드는 P-7 요청과 같다 |
| `achievements` | 도전과제 8개(서버 표가 원본, FE-68 순서). `stat`: `completed`(완료한 회차가 하나라도 있는 사건 수, 다시 하기를 시작해도 줄지 않는다) · `hasType`(성향 테스트 결과가 있으면 1) · `attendanceDays`(P-6) · `collected`(모은 일러스트 + 의상 수, v0.63 BE-83). `value`는 목표에서 멈춘다. `status`: `CLEARED` · `IN_PROGRESS` · `PENDING`(서버가 셀 수 없는 값 — v0.63부터 나오지 않지만 값은 남긴다. v0.63 전에는 `collected` 도전과제가 `PENDING`이었다). **`unlockedAt` · `isNew`(v0.63)**: 해금 기록이 있으면 해금 시각과 "새로 열림" 여부(아직 P-11로 보지 않았으면 `true`), 없으면 `null` · `false`. **한 번 해금된 도전과제는 표가 바뀌어 다시 미달이 되어도 `CLEARED` · `value = goal`로 남고 보상도 계속 열려 있다**(BE-96 원칙). 해금 시각은 서버가 처음 판정해 기록한 때다 — 마이페이지 조회 · P-9가 막 달성한 도전과제를 기록한다. `rewards`는 여섯 종류(`kinds` · `frames` · `backgrounds` · `sashStyles` · `sashColors`(`모양:색`) · `sashIcons`), 받지 않는 종류는 빈 배열. 제목 · 문구 · 그림은 화면이 `id`로 붙인다 |
| `unlocked` | 지금 열린 것(여섯 종류). 처음부터 열린 것 + `CLEARED` 도전과제의 보상 |
| `collection` | 모은 일러스트 · 의상(v0.63, BE-83). `collected`는 `items` 수(일러스트 + 의상, 도전과제 `collected`와 같은 값). `items`는 최근에 얻은 순: `kind`(`illustration` · `cast`) · `itemId` · `caseId`(처음 얻은 사건) · `acquiredAt` · `isNew`(아직 P-10으로 보지 않았으면 `true`, "새로 얻음"). 기록이 없거나 빈 마이페이지면 `{ "collected": 0, "items": [] }` |
| `level` | 레벨 · 직업(v0.54, BE-106). 모양은 L-1 응답과 같다. 기록이 없거나 빈 마이페이지면 Lv1 · 0 XP · 무직 |
| `profileSettings` | 프로필 선택 설정(v0.52, BE-104): 공개 성향카드(`shownCard`) · 프로필카드 아래 성향(`profileCardType`) · 한 줄 소개(`bio`). 저장은 P-8. 저장한 적이 없거나 빈 마이페이지면 `{ "shownCard": "current", "profileCardType": "current", "bio": "", "cardLine": "type", "cardLevel": false }`(`cardLine` · `cardLevel`은 v0.65, BE-114) |

- 쿠키가 없거나 기록이 없으면 `404`가 아니라 **빈 마이페이지**(`personality`·`initialPersonality`·`judgmentPersonality` `null`, 목록 빈 배열)를 `200`으로 준다. 마이페이지는 "아직 없음"을 안내하는 화면이기 때문이다.
- `judgmentPersonality` · `history`는 체험 성향 저장(7.6.7, BE-64)이 붙은 뒤부터 채워진다. 그 전에는 각각 `null` · 빈 배열이다.
- 판결 체험 중(S-03 ~ S-07)에 성향을 보여 주지 않는 규칙(FR-8-5)은 화면이 지킨다. 이 API는 마이페이지에서만 부른다.
- 화면의 **현재 성향카드**(마이페이지 첫 화면 · 내 정보 수정 · 성향카드 화면 위쪽)는 `judgmentPersonality.type`, 없으면 `personality.type`이다. 처음 성향(`initialPersonality`)은 성향카드 화면 아래 "초심 → 재심 → 최근" 줄에만 나온다(v0.50).

---

### P-6. 출석 기록 — `POST /attendance`

> 확장 단계(BE-97, v0.41). 마이페이지 출석 일수 · 출석 도전과제(FE-68, BE-96)에 쓴다. 결정 기록 [`BE-97.md`](decisions/BE-97.md)

**요청**: 본문 없음. 익명 ID 쿠키(1-2)를 읽는다. 프론트가 사이트에 들어올 때 한 번 부른다.

**응답 204** (본문 없음)

- 쿠키의 익명 ID가 DB에 있으면 **오늘 날짜로 한 줄** 남긴다. 같은 날 다시 불러도 결과가 같다(하루 한 번만 센다).
- 쿠키가 없거나 DB에 없는 값이면 **아무것도 하지 않고** `204`. 익명 ID를 새로 발급하지 않는다(발급은 API 2 · P-2 · P-7만).
- 익명 ID를 **새로 발급하는 요청(API 2 · P-2 · P-7)은 그날 출석도 함께 남긴다**(v0.43, BE-98). 앱 시작 때 부르는 이 API는 쿠키가 생기기 전이라, 첫 방문날이 빠지지 않게 하기 위해서다. 같은 날 다시 불러도 하루다.
- "오늘"은 `Asia/Seoul` 기준이며, 하루 시작 시각은 설정값이다(`attendance.day-start-hour`, 기본 0시). 예: 4시로 두면 03:59 접속은 전날 출석이다.
- 거절 응답은 없다(공통 Rate limit 429는 그대로).

---

### P-7. 프로필 꾸미기 저장 — `PUT /profile-deco`

> 확장 단계(BE-96, v0.42). 꾸미기 **전체**를 한 번에 보낸다. 조회는 P-5에 함께 있다. 결정 기록 [`BE-96.md`](decisions/BE-96.md)

**요청**

json

```json
{  "portrait": "/images/characters/scene/naero_idle-neutral.webp",  "frame": "wood",  "background": "cream",  "sashStyle": "short",  "sashColorShort": "wine",  "sashColorLong": "wine",  "sashIconLeft": "star",  "sashIconRight": "none",  "bookmarkCharacter": "nambul",  "bookmarkFace": "thinking",  "bookmarkFaceStyle": "cropped",  "bookmarkColor": "green"}
```

| 필드 | 값 |
| --- | --- |
| `portrait` | 대표 이미지 경로. **`/images/폴더/…/이름.webp` 모양만**(영문 · 숫자 · `_` · `-`). `..` · `//` · `\` · 쿼리 · 외부 주소는 거절. 종류는 `/images/`와 파일명 사이(예: `characters/scene`). 파일이 실제로 있는지는 보지 않는다 |
| `frame` | `basic` · `wood` · `gold` · `navy` · `stitch` |
| `background` | `cream` · `latte` · `tarot` · `news` · `linen` |
| `sashStyle` | 카드 책갈피 모양 `none` · `long` · `short` |
| `sashColorShort` · `sashColorLong` | 모양별 색 `wine` · `navy` · `gold` · `green` |
| `sashIconLeft` · `sashIconRight` | 짧은 책갈피 아이콘 `none` · `star` · `scale` · `gavel` · `cup` · `heart` · `naero` · `nambul` · `law` |
| `bookmarkCharacter` | `naero` · `nambul` · `law-scale` · `law-gavel` · `law-book` (잠금 없음) |
| `bookmarkFace` | `cheer` · `happy` · `neutral` · `sorry` · `surprised` · `thinking` (잠금 없음) |
| `bookmarkFaceStyle` | `generated` · `cropped` (잠금 없음) |
| `bookmarkColor` | 리본 색 `bronze` · `wine` · `navy` · `green` · `gold` (잠금 없음) |

**응답 200**: 저장된 꾸미기(요청과 같은 모양). 쿠키가 없었으면 익명 ID를 새로 발급해 `Set-Cookie`로 내려준다(P-2와 같다).

| 거절 | 조건 |
| --- | --- |
| 400 `VALIDATION_ERROR` | 값이 없음(`reason: REQUIRED`) · 목록에 없는 값(`INVALID_VALUE`) · 대표 이미지 형식 오류(`INVALID_FORMAT`). `details`에 필드별 사유 |
| 409 `DECO_LOCKED` | 아직 열리지 않은 항목(P-5 `unlocked`에 없음). `details`에 잠긴 필드(`reason: DECO_LOCKED`) |

- 검사 순서는 400 → 409이고, 하나라도 걸리면 **아무것도 저장하지 않는다.**
- **이미 저장해 둔 값은 다시 거절하지 않는다**(v0.43, BE-98): 저장값과 같고 저장할 때도 쓰이던 칸은 잠김 검사를 건너뛴다. 표가 바뀌어 저장값이 잠겨도 다른 칸은 저장할 수 있다. 저장할 때 쓰이지 않던 칸(예: 책갈피 없음일 때의 짧은 책갈피 색)은 값이 같아도 쓰이게 되면 다시 검사한다. 형식 · 값 목록(400)은 늘 전체를 본다.
- 잠김은 **지금 화면에 쓰이는 칸만** 본다: 대표 이미지(기본 두 장 `standing/naero` · `standing/nambul`은 늘 허용) · 테두리 · 배경 · 책갈피 모양, 고른 모양의 색(`모양:색`), 짧은 책갈피일 때 좌우 아이콘. 쓰이지 않는 칸(고르지 않은 모양의 색 등)은 기본값이 잠겨 있어도 거절하지 않는다.
- 수집 도전과제 보상(대표 이미지 `cases` · `characters/law` · `characters/adult`, 배경 `news`, 아이콘 `naero` · `nambul`, 책갈피 색 `green`)은 일러스트 · 의상을 5장 · 20장 모으면 열린다(v0.63, BE-83. 그 전에는 열 수 없었다).

---

### P-8. 프로필 선택 설정 저장 — `PATCH /profile-settings`

> 확장 단계(BE-104, v0.52). 공개 성향카드 · 프로필카드 아래 성향 · 한 줄 소개를 서버에 둔다(FE-77이 화면에서 이 브라우저에만 저장하던 값). **보낸 칸만 바꾸는 부분 저장**이다. 조회는 P-5 `profileSettings`. 결정 기록 [`BE-104.md`](decisions/BE-104.md)

**요청** (바꿀 칸만 보낸다)

json

```json
{  "shownCard": "retake"}
```

| 필드 | 값 |
| --- | --- |
| `shownCard` | **공개 성향카드**: 성향카드 화면의 "공개" 도장 칸. 커뮤니티에서 다른 사람에게 보여 줄 카드. `initial`(초심) · `retake`(재심) · `current`(최근) |
| `profileCardType` | **프로필카드 아래 성향**: 카드 꾸미기 창에서 고른 칸. 값은 `shownCard`와 같다. 공개 성향카드와는 **별개의 값**이다 |
| `cardLine` | **프로필카드 아래 줄**(v0.65, BE-114): 카드 아래에 보일 줄. `type`(성향) · `signature`(한 줄 소개) · `job`(직업) 중 하나. 기본 `type` |
| `cardLevel` | **레벨 표시**(v0.65, BE-114): 레벨을 `cardLine` 윗줄에 붙일지. boolean(`true` · `false`), 문자열 `"true"`는 400. 기본 `false` |
| `bio` | **한 줄 소개**. 문자열, 앞뒤 공백을 뺀 뒤 **0 ~ 40자**(유니코드 글자 기준, 이모지도 한 글자), 줄바꿈 · 제어 문자(탭 · NUL 등) 없음. 빈 문자열 `""`은 소개 지우기. HTML은 거르지 않고 그대로 저장한다 — 화면은 `textContent`로만 그린다(서버 HTML 걸러 내기는 금칙어와 함께 추후, `docs/decisions/BE-104.md`) |

**응답 200**: 저장 뒤의 다섯 값 전체(P-5 `profileSettings`와 같은 모양). 쿠키가 없었으면 익명 ID를 새로 발급해 `Set-Cookie`로 내려준다(P-7과 같다).

json

```json
{  "shownCard": "retake",  "profileCardType": "current",  "bio": "판결은 신중하게",  "cardLine": "job",  "cardLevel": true}
```

| 거절 | 조건 |
| --- | --- |
| 400 `VALIDATION_ERROR` | 본문이 객체가 아님 · 바꿀 칸이 하나도 없음 · 모르는 칸(`details`에 그 이름) · 값이 `null` · 문자열이 아님 · `shownCard` · `profileCardType`이 세 값 밖 · `bio`가 40자 초과 · 줄바꿈 · 제어 문자 포함 · `cardLine`이 세 값 밖 · `cardLevel`이 boolean이 아님 |

- **보낸 칸만 바꾼다.** 세 값은 화면마다 따로 바뀌므로(공개 도장 · 카드 꾸미기 창 · 내 정보 수정), 한 곳에서 저장할 때 다른 두 값을 보내지 않아도 된다. 오래된 화면이 다른 칸을 옛 값으로 덮어쓰지 않게 하려는 것이다(P-7은 꾸미기를 한 화면에서 함께 저장해 전체 저장).
- 보내지 않은 칸과 `null`은 다르다: 보내지 않으면 그대로 두고, `null`은 400이다(지우려면 `bio: ""`).
- **아직 재심이 없는데 `retake`를 골라도 저장한다.** 화면이 고른 칸에 카드가 없으면 대신 고르는 규칙(공개 카드: 최근 → 초심, 프로필카드 아래 성향: 최근 → 첫 칸)을 쓴다.
- 형식 검사가 먼저다: 400이면 아무것도 저장하지 않고, 쿠키가 없었어도 익명 ID를 발급하지 않는다.
- **공개 범위는 없다**: 프로필카드 · 성향카드는 커뮤니티에서 항상 다른 사람에게 보인다(FE-77). 한 줄 소개 금칙어 검사는 금칙어 기능(운영 A-39 ~ A-41, 초안)이 생기면 붙인다(추후).
- 다른 사람에게 보여 주는 쪽(커뮤니티 API)은 이 값을 읽도록 나중에 붙인다(이번 범위 밖).

---

### P-9. 수집 기록 — `POST /collection`

> 확장 단계(BE-83, v0.63). 사건 체험을 마치고 얻은 일러스트 · 의상(만난 모습)을 남긴다. **획득 조건은 화면(`collectionOf` · `castOf`)이 판정하고, 서버는 항목 · 획득 시각(서버 시각) · 확인 여부만 저장한다.** 조회는 P-5 `collection`. 결정 기록 [`BE-83.md`](decisions/BE-83.md)

**요청**

json

```json
{  "caseId": 1,  "backfill": false,  "items": [    { "kind": "illustration", "itemId": "case-1-origin" },    { "kind": "cast", "itemId": "naero:judge" }  ]}
```

| 필드 | 값 |
| --- | --- |
| `caseId` | 이 항목들을 얻은 사건. 이 사용자가 **완료한 회차가 하나라도 있어야** 한다(다시 하기 중이어도 된다) |
| `backfill` | 생략하면 `false`. **기존 사용자 백필**: 이미 완료한 사건인데 `collection`에 항목이 빠져 있으면 화면이 마이페이지 진입 때 `true`로 보낸다. `true`면 획득 시각을 **그 사건의 첫 완료 시각**으로, "본 것"으로 남기고("새로 얻음"이 한꺼번에 뜨지 않게), 이 요청으로 해금된 도전과제도 "본 것"으로 남긴다 |
| `items` | 1 ~ 20개. `kind`: `illustration`(투명 배경 일러스트) · `cast`(역할 의상). `itemId`: 소문자 · 숫자로 시작하고 소문자 · 숫자 · `:` · `_` · `-`로 64자까지. 일러스트는 일러스트 id(예: `case-1-origin`), 의상은 `{who}:{role}`(예: `naero:judge`). 서버는 의미 없는 문자열로만 다룬다 |

**응답 200**

json

```json
{  "added": [    { "kind": "illustration", "itemId": "case-1-origin", "caseId": 1, "acquiredAt": "2026-10-29T10:40:00+09:00", "isNew": true }  ],  "collected": 5,  "newlyUnlocked": ["collect-5"]}
```

| 필드 | 설명 |
| --- | --- |
| `added` | 이번 요청으로 **새로 저장된 항목만**(P-5 `collection.items`와 같은 모양). 이미 가진 항목은 빠진다 |
| `collected` | 저장 뒤 모은 수(일러스트 + 의상) |
| `newlyUnlocked` | 이번 요청으로 해금이 새로 기록된 도전과제 id(예: 5장째에 `collect-5`) |

| 거절 | 조건 |
| --- | --- |
| 400 `VALIDATION_ERROR` | `caseId` 없음 · 0 이하, `items`가 비었거나 20개 초과, 모르는 `kind`, `itemId` 형식 오류. `details`에 필드별 사유 |
| 404 `EXPERIENCE_NOT_FOUND` | 쿠키가 없음 · 모르는 익명 ID · 이 사건의 체험이 없음 (익명 ID를 새로 발급하지 않는다) |
| 404 `CASE_NOT_FOUND` | 사건이 없음 |
| 409 `INVALID_STATE` | 체험은 있지만 아직 완료하지 않음. `currentStatus`에 최근 회차 상태 |
| 409 `COLLECTION_LIMIT_EXCEEDED` | 일러스트: 이 사건에서 처음 얻은 수가 5를 넘음, 의상: 사용자당 12를 넘음. 이미 가진 항목은 세지 않는다 |

- **멱등**이다. 같은 요청을 다시 보내도 결과가 같다(`added`만 빈다). 같은 그림을 다른 사건에서 다시 보내도 **처음 얻은 사건 · 시각이 남는다**(그림별 key, 사건별이 아니다).
- 하나라도 걸리면 **아무것도 저장하지 않는다.**
- 서버는 대본을 몰라 가짜 `itemId`를 막지 못한다. 그래서 상한으로 총량만 묶는다. 다른 사용자와 비교하는 기능(랭킹 등)에 `collected`를 쓰기 전에 서버 목록 검증으로 바꿀지 다시 본다(`docs/decisions/BE-83.md`).
- **로그인 합쳐 읽기는 아직 없다.** 출석(P-6) · 꾸미기(P-7)처럼 익명 ID 하나 기준이고, 회원에 연결된 익명 ID들을 합쳐 읽는 것은 함께 별도로 붙인다.

---

### P-10. 컬렉션 "봤음" — `POST /collection/seen`

> 확장 단계(BE-83, v0.63). "새로 얻음" 표시를 지운다.

**요청** — 둘 중 하나만 보낸다

json

```json
{ "items": [ { "kind": "illustration", "itemId": "case-1-origin" } ] }
```

json

```json
{ "kind": "cast" }
```

| 필드 | 값 |
| --- | --- |
| `items` | 본 항목(P-9 `items`와 같은 모양, 100개까지) |
| `kind` | 그 종류 전부(`illustration` · `cast`). 컬렉션 탭을 열었을 때 |

**응답 204** (본문 없음)

- 쿠키가 없거나 모르는 익명 ID · 이미 본 항목 · 없는 항목은 조용히 건너뛴다(멱등).

| 거절 | 조건 |
| --- | --- |
| 400 `VALIDATION_ERROR` | `items` · `kind`를 둘 다 보내거나 둘 다 안 보냄, 형식 오류 |

---

### P-11. 도전과제 "봤음" — `POST /achievements/seen`

> 확장 단계(BE-83, v0.63). "새로 열림" 표시를 지운다.

**요청**

json

```json
{ "achievementIds": ["collect-5", "first-case"] }
```

**응답 204** (본문 없음)

- `achievementIds`는 1 ~ 20개(P-5 `achievements[].id`). 쿠키가 없거나 모르는 익명 ID · 이미 본 것 · 아직 해금되지 않은 id는 조용히 건너뛴다(멱등).

| 거절 | 조건 |
| --- | --- |
| 400 `VALIDATION_ERROR` | 목록이 비었거나 20개 초과, 빈 문자열 · 40자 초과 id |

---

### 9-2. 성향 API 확인 항목

| # | 항목 | 이 계약의 제안 | 확인할 사람 |
| --- | --- | --- | --- |
| 1 | 목 API와 달라지는 점 | ✅ 응답에서 `typeCode` 제거(`type.shareId`로 구분), 문항의 `a` · `b`(글자 포함) → `optionA` · `optionB`(문장만), 유형의 `daily` · `verdict` → `dailyDescription` · `verdictDescription`, `character`는 `NAERO` · `NAMBUL` 코드, 결과에 `testedAt` 추가, 내 결과 없음은 `null` 대신 404 | A 김해송 (목 → 실제 API 전환) |
| 2 | 마이페이지 경로 | ✅ `GET /my-page` 하나로 묶는다(화면 한 번 진입에 한 번 호출) | A |
| 3 | `experiences` 범위 | ✅ 사건마다 최근 회차 하나. 회차별 전체 이력이 필요하면 추가 | A |
| 4 | 이미지 경로 | ✅ `/images/characters/personality/{이미지 번호}.webp`, 이미지 번호(`01 ~ 32`)는 성향 이미지 번호표 기준으로 **유형마다 화면에 보이는 이미지 하나**(기본 캐릭터, 예: ISTJ는 남불 `18.webp`). **한 번 정한 번호는 바꾸지 않는다**(공유 미리보기 · 캐시가 깨지지 않게, #121 리뷰) | A |
| 5 | `shareId` 값 | ✅ 16개를 시드에 고정값으로 넣는다(바꾸면 공유한 링크가 깨진다). 목 API의 해시 값과는 다르다. v0.28: OG 빌드용 값(shareId · 유형 번호 · 이미지 번호 · 유형명 · `oneLiner`)은 `frontend/og/personality-types.json`에 두고, 시드와 같은지 백엔드 테스트(`PersonalityContentSeedTest`)가 확인한다(BE-73) | B (시드) |

---

## 10. (확장) 회원 인증 API (7.4.2 BE-62 · 7.4.3 BE-94 · BE-113)

카카오 로그인(7.4.2, BE-62)이 성공 처리에서 발급한 액세스 · 리프레시 토큰 중, **리프레시 토큰만 `NLNB_RT`
쿠키**(`HttpOnly` · `Secure` · `SameSite=Strict` · `Path=/api/v1/auth`)로 받는다. 액세스 토큰은 화면이 메모리에만
들고 있고, 아래 재발급 API로 다시 받는다(docs/tech-stack.md 3장 "리프레시 토큰 쿠키").

> 번호(v0.41): 회원 인증 API는 **M-N**을 쓴다. 처음 붙였던 A-16 · A-17은 8-4 운영 메뉴 API가 예약한 A-16 ~ A-41과 겹쳐 M-1 · M-2로 바꿨다(BE-94의 재발급 · 로그아웃). M-3 · M-4는 카카오 로그인(BE-62), M-5 · M-6은 아이디 회원가입 · 로그인(BE-113, v0.58)이다.

M-1 · M-2는 **`Authorization` 헤더를 보지 않고 리프레시 토큰 쿠키로만 인증**한다. 상태를 바꾸는 요청이라 Origin 검증(요청의
Origin이 이 서버 자신과 같은 출처인지)을 거친다 — 벗어나면 403 `FORBIDDEN`. M-5 · M-6도 응답이 리프레시 토큰 쿠키를 심으므로
같은 Origin 검증을 받는다(다른 사이트가 공격자 계정으로 로그인시키는 로그인 CSRF 방지, v0.58).

### M-1. 토큰 재발급 — `POST /api/v1/auth/token/reissue`

**응답 200**

```json
{ "accessToken": "eyJhbGciOi...", "accessTokenExpiresAt": "2026-10-21T00:15:00Z" }
```

- 리프레시 토큰은 **재발급마다 로테이션**된다 — 응답의 `Set-Cookie`로 새 `NLNB_RT`를 받고, 기존 값은 그 순간 무효가
  된다(탈취된 토큰이 먼저 쓰이면 원래 주인의 다음 재발급이 실패해 알아챌 수 있다).

| 거절 | 조건 |
| --- | --- |
| 401 `TOKEN_INVALID` | 리프레시 토큰 쿠키 없음, 모르는 값, 이미 로테이션 · 로그아웃으로 사라진 값 |
| 403 `FORBIDDEN` | Origin(없으면 Referer)이 이 서버와 다른 출처 |

### M-2. 로그아웃 — `POST /api/v1/auth/logout`

**응답 204**: 본문 없음. 쿠키가 없거나 이미 로그아웃된 상태여도 204(멱등).

- 리프레시 토큰이 있으면 Redis 저장소에서 지우고(그 뒤 재발급은 전부 실패한다), `NLNB_RT` 쿠키를 `Set-Cookie`로
  지운다. 이미 나간 액세스 토큰은 블랙리스트를 두지 않아 자연 만료(기본 15분)까지는 유효하다.
- 익명 ID 쿠키(`NLNB_AID`)가 **회원에 연결된** 익명 ID면 이 쿠키도 `Set-Cookie`(`Max-Age=0`, 발급 때와 같은 `Path=/`)로
  지운다(v0.70, BE-137). 로그인 때 회원에 연결한 익명 ID가 남으면 로그아웃 뒤 같은 브라우저(공용 PC)의 다음 사람
  체험 · 판결이 그 회원 계정으로 쌓이기 때문이다. 리프레시 쿠키가 이미 없어도(만료 뒤 로그아웃) 지운다.
  - 새 익명 ID는 이때 발급하지 않는다. 다음 행동(API 2 체험 시작 · P-2 성향 테스트 등) 때 회원과 연결 안 된 새 익명 ID가 발급된다.
    그전까지 마이페이지 등은 빈 상태로 보인다
  - 연결 안 된(손님) 익명 ID · DB에 없는 값은 지우지 않는다. 이전 기록은 회원 계정에 그대로 남는다(연결을 끊지 않는다)
  - 연결 여부를 확인하지 못하면(DB 오류) 지운다. 이때도 응답은 204다
  - 화면이 따로 할 일은 없다. 쿠키는 `HttpOnly`라 화면이 읽지 못하고, 브라우저가 응답의 `Set-Cookie`로 지운다

| 거절 | 조건 |
| --- | --- |
| 403 `FORBIDDEN` | Origin(없으면 Referer)이 이 서버와 다른 출처 |

### M-3. 카카오 로그인 시작 — `GET /api/v1/auth/oauth2/authorization/kakao` (BE-62)

브라우저 **이동**으로 연다(`fetch`가 아니라 `location.href` · 링크). JSON을 돌려주지 않는다.

- 302로 카카오 인가 화면(`https://kauth.kakao.com/oauth/authorize`)에 보낸다. 동의항목(scope)은 요청하지 않는다 — 회원번호만 쓰고 개인정보는 받지 않는다
- 이때 `NLNB_OAUTH_STATE` 쿠키(`HttpOnly` · `Secure` · `SameSite=Lax` · `Path=/api/v1/auth/oauth2` · 5분)를 심는다. 인가 요청 내용은 서버 Redis에만 두고 세션은 만들지 않는다
- `KAKAO_CLIENT_ID`가 없는 환경(로컬 · 테스트)에서는 이 경로가 없다(404)
- 운영 Nginx는 M-3 · M-4에 `/api` 공통 제한보다 엄격한 전용 제한을 건다(IP별 분당 30번 · 전체 초당 10번, PR #229 리뷰). 넘으면 JSON 429(1-4 형식)

### M-4. 카카오 로그인 콜백 — `GET /api/v1/auth/oauth2/callback/kakao` (BE-62)

카카오가 브라우저를 돌려보내는 주소다(카카오 콘솔 Redirect URI에 등록). 화면이 직접 부르지 않는다.

- **성공**: 회원을 찾거나 만들고, 익명 기록을 연결한 뒤 `NLNB_RT`(리프레시 토큰, M-1과 같은 쿠키)를 심고 **화면 경로 `/auth/callback`으로 302**. 토큰은 URL에 싣지 않는다 → 화면은 도착하면 **M-1 재발급을 한 번 불러 액세스 토큰을 받는다**
- **실패 · 취소**: `state` 불일치(쿠키 없음 · 다름 · 만료), 사용자가 카카오에서 취소, 카카오 오류 → **`/auth/callback?error=login_failed`로 302**(JSON 401이 아니다)
- 익명 ID 쿠키(`NLNB_AID`) 연결 규칙(docs/decisions/BE-62.md)
  - 아직 연결 안 된 익명 ID → 이 회원에 연결
  - 이미 같은 회원 → 그대로
  - 다른 회원에 연결됨(공용 PC) → 덮어쓰지 않고, 이 회원에 연결된 **새 익명 ID를 `Set-Cookie`로** 내려준다
  - 쿠키 없음 · 모르는 값 → 이 회원에 연결된 새 익명 ID를 내려준다
- 화면 경로(`/auth/callback`)는 환경변수로 바꿀 수 있고, 우리 사이트 안의 경로만 허용한다(열린 리다이렉트 방지)

### M-5. 아이디 회원가입 — `POST /api/v1/auth/signup` (BE-113)

**요청**

```json
{ "id": "naero_01", "password": "judge1234", "nickname": "내로판사", "agreed": true }
```

| 필드 | 규칙 |
| --- | --- |
| `id` | 필수. 영문 · 숫자 · 밑줄 4 ~ 20자. 서버가 **소문자로 바꿔 저장 · 비교**한다(`Naero`와 `naero`는 같은 아이디). `@`를 쓸 수 없다 — 화면은 `@`가 있으면 관리자 로그인(A-2)으로 보낸다 |
| `password` | 필수. 8 ~ 64자, 영문과 숫자를 하나 이상씩 포함. 64자는 비밀번호 해시(Argon2) 비용 상한이다. 서버는 일방향 해시로만 저장한다 |
| `nickname` | 필수. 한글 · 영문 · 숫자 2자 이상, 한글이 있으면 8자 · 영문 · 숫자만이면 12자까지. 공백 · 기호 불가. **중복을 허용하지 않는다**(대소문자 구분 없음, v0.69 BE-139. 처음 BE-113 가입은 허용했다 — 결정 기록 BE-113 결정 2를 BE-139가 바꿨다). `방청객`으로 시작하는 닉네임은 쓸 수 없다(자동 이름 `방청객 {회원 ID}` 흉내 방지, v0.72) |
| `agreed` | 필수(v0.74, BE-133). 이용약관 · 개인정보처리방침 동의. **`true`만 받는다** — 화면의 필수 동의 체크와 같다. 서버는 가입한 시각을 동의 시각으로, 그때의 약관 버전과 함께 회원에 남긴다(ERD `member.terms_agreed_at` · `terms_version`) |

**응답 200**: 가입하면 **바로 로그인된다.**

```json
{ "nickname": "내로판사" }
```

- `nickname`은 화면에 보일 표시 이름이다(v0.72): 닉네임이 있으면 그 값, 없으면(V39가 겹치던 닉네임을 비운 아이디 회원) `방청객 {회원 ID}` — 항상 문자열이다. 가입 응답은 늘 방금 정한 닉네임이다.

- `Set-Cookie`로 `NLNB_RT`(M-1과 같은 쿠키)를 심는다. 액세스 토큰은 본문에 없다 → 화면이 **M-1 재발급을 바로 한 번 불러** 받는다(M-4와 같은 흐름)
- 익명 ID 쿠키(`NLNB_AID`) 연결 규칙은 M-4와 같다. 새 익명 ID를 발급하면 `Set-Cookie`로 바꿔 심는다
- 쿠키는 회원 저장 · 토큰 발급이 모두 끝난 뒤에만 심는다. 카카오 계정과는 합쳐지지 않는 별개 회원이다

| 거절 | 조건 |
| --- | --- |
| 400 `VALIDATION_ERROR` | 위 규칙 위반. `details`에 칸 이름이 실린다: `[{ "field": "id" \| "password" \| "nickname" \| "agreed", "reason": "..." }]`. `agreed`가 없거나 `null`이면 `reason` `NotNull`, `false`면 `AssertTrue`(v0.74). 400이면 아무것도 저장하지 않는다 |
| 409 `DUPLICATE_ID` | 이미 쓰는 아이디(대소문자만 다른 아이디 포함, 동시 가입 포함) |
| 409 `DUPLICATE_NICKNAME` | (v0.69) 이미 쓰는 닉네임(대소문자만 달라도 같다, 동시 가입 포함). 아이디보다 나중에 검사한다 |
| 403 `FORBIDDEN` | Origin(없으면 Referer)이 이 서버와 다른 출처 |
| 429 `TOO_MANY_REQUESTS` | 운영 Nginx 전용 제한(1-5). `Retry-After: 6` |

### M-6. 아이디 로그인 — `POST /api/v1/auth/login` (BE-113)

**요청**

```json
{ "id": "naero_01", "password": "judge1234" }
```

- `id`는 대소문자를 가리지 않는다(서버가 소문자로 바꿔 찾는다). 관리자 계정(이메일)은 이 API로 로그인할 수 없다 — 관리자는 A-2

**응답 200**

```json
{ "nickname": "내로판사" }
```

- `nickname`은 표시 이름이다(M-5와 같다, v0.72).
- 쿠키 · 익명 ID 연결 · 화면의 M-1 재발급은 M-5와 같다. 발급되는 권한은 항상 일반 회원(`USER`)이다

| 거절 | 조건 |
| --- | --- |
| 401 `INVALID_CREDENTIALS` | 아이디 없음 · 비밀번호 불일치 · 아이디 형식 틀림(예: 이메일). **구분하지 않고**, 없는 아이디도 같은 시간이 걸리게 비교한다(계정 존재를 알 수 없게). 카카오 회원도 이 코드다 |
| 400 `VALIDATION_ERROR` | `id` · `password`가 비었거나 `id` 20자 · `password` 64자 초과 |
| 403 `FORBIDDEN` | Origin(없으면 Referer)이 이 서버와 다른 출처 |
| 429 `TOO_MANY_REQUESTS` | 운영 Nginx 전용 제한(1-5). `Retry-After: 3` |

- 비밀번호 변경 · 찾기 · 탈퇴 API는 아직 없다(BE-113 범위 밖). 계정 단위 로그인 실패 잠금도 없고 IP별 요청 제한만 있다

---

### M-8. 내 회원 정보 — `GET /api/v1/members/me` (BE-139)

> 로그인한 회원만(`Authorization: Bearer`). 화면 "내 정보 수정"의 계정 관리와 헤더 표시 이름에 쓴다. 개인정보는 저장하지 않으므로(BE-62) 이름 · 이메일은 없다.

**응답 200**

```json
{ "nickname": null, "displayName": "방청객 1024", "provider": "KAKAO", "loginId": null,
  "createdAt": "2026-10-27T14:00:00+09:00", "lastLoginAt": "2026-10-29T09:30:00+09:00" }
```

| 필드 | 값 |
| --- | --- |
| `nickname` | 닉네임. 아직 정하지 않았으면(카카오 회원) `null` |
| `displayName` | 화면 · 커뮤니티에 보이는 이름. 닉네임이 있으면 그 값, 없으면 **`방청객 {회원 ID}`**(커뮤니티 글쓴이 이름과 같다) |
| `provider` | 로그인 방식 `KAKAO` · `LOCAL`(아이디) |
| `loginId` | 아이디 회원의 로그인 아이디(소문자). 카카오 회원은 `null` |
| `createdAt` · `lastLoginAt` | 가입 시각 · 최근 로그인 시각(`+09:00`). 최근 로그인 기록이 없으면 `lastLoginAt`은 `null` |

| 거절 | 조건 |
| --- | --- |
| 401 `UNAUTHORIZED` | 로그인하지 않음 · 토큰은 유효한데 회원 행이 없음 |

### M-7. 닉네임 설정 — `PUT /api/v1/members/me/nickname` (BE-139)

> 로그인한 회원만(`Authorization: Bearer`). 카카오 회원 · 아이디 회원 공통이다. 카카오 회원은 닉네임이 없다(NULL) — 커뮤니티에는 `방청객 {회원 ID}`로 보이다가, 정하면 그 이름으로 바뀐다. 결정 기록 [`BE-139.md`](decisions/BE-139.md)

**요청**

```json
{ "nickname": "내로판사" }
```

- 형식은 M-5와 같다: 한글 · 영문 · 숫자 2자 이상, 한글이 있으면 8자 · 영문 · 숫자만이면 12자까지, 공백 · 기호 불가. 앞뒤 공백을 다듬지 않는다(공백이 있으면 400).
- **중복 금지**: 대소문자를 구분하지 않는다(`Naero`와 `naero`는 같은 닉네임). **내 닉네임의 대소문자만 바꾸는 것은 된다.** 닉네임이 없는(NULL) 회원은 겹쳐도 된다.
- 변경 횟수 제한은 아직 없다. 금칙어 검사는 **`방청객`으로 시작하는 닉네임만 금지**한다(v0.72, 자동 이름 흉내 방지, 400 `VALIDATION_ERROR`). 일반 금칙어 검사는 아직 없다(금칙어 기능 A-39 ~ A-41이 생기면 붙인다).

**응답 200**

```json
{ "nickname": "내로판사" }
```

| 거절 | 조건 |
| --- | --- |
| 401 `UNAUTHORIZED` | 로그인하지 않음 |
| 400 `VALIDATION_ERROR` | `nickname`이 없음 · 문자열이 아님 · 형식 위반(`details`: `field: nickname`) |
| 409 `DUPLICATE_NICKNAME` | 다른 회원이 쓰는 닉네임(동시 요청 포함 — 한 명만 된다) |

## 11. (확장) 레벨 · 직업 API (BE-106)

> 결정 기록 [`BE-106.md`](decisions/BE-106.md). 레벨은 **익명 ID 단위**다(출석 · 체험 · 업적과 같다). 로그인한 회원이 여러 기기를 쓰는 경우의 합산은 커뮤니티 · 로그인 연동 이슈에서 한다.

### 11-1. 공통

- **레벨 곡선**: 누적 XP(L) = 10000 × [1 − ln(1 + 0.08 × (100 − L)) / ln(1 + 0.08 × 99)]. Lv1 = 0 XP, Lv100 = 10000 XP. 초반이 빨리 오르고 뒤로 갈수록 느리다(레벨당 필요 XP: Lv1→2 41, Lv99→100 352).
- **직업 11등급**: 10레벨마다 하나씩 열린다 — 무직(1) · 로스쿨생(10) · 연수원생(20) · 수습 판사(30) · 판사(40) · 부장판사(50) · 고등법원 판사(60) · 지방법원장(70) · 고등법원장(80) · 대법관(90) · 대법원장(100). 해금 누적 XP: 0 · 385 · 854 · 1,377 · 1,967 · 2,645 · 3,442 · 4,408 · 5,634 · 7,314 · 10,000.
- **경험치 수급처** (레벨 구성은 커뮤니티 60 · 판결 체험 40을 목표로 한다. 판결 체험 쪽은 총량이 한정돼 있다)

| 구분 | 수급처 | XP | 지급 조건 · 상한 |
| --- | --- | --- | --- |
| 판결 체험 | 사건 첫 완료 | 100 | 사건당 1회 |
| | 성향 테스트 | 100 | 1회 |
| | 출석 | 5 | 하루 1회(P-6) |
| | 업적 달성 | 50 · 100 · 200 | 업적마다 1회. 쉬운 것(첫 사건 · 성향 · 출석 3일) 50, 중간(출석 7일 · 사건 3개 · 수집 5개) 100, 어려운 것(출석 30일 · 수집 20개) 200 |
| | 일러스트 · 의상 수집 | 10 | 모은 항목 1장마다. `collection_item` 행 수로 계산한다(v0.63, BE-83) |
| | SNS 공유 | 100 | 완료한 사건만, 사건당 1회 · 하루 1회 (L-3) |
| 커뮤니티 | 글 작성 | 30 | 하루 3건 |
| | 판결 공유 글 보너스 | +20 | 사건당 1회 |
| | 댓글 작성 | 10 | 하루 5건 |
| | 좋아요 누르기 | 2 | 글당 1회 · 하루 10개 |
| | 좋아요 받기 | 3 | 글당 10개 · 하루 30개 |

- 판결 체험의 사건 · 성향 · 출석 · 업적 XP는 기존 기록에서 **그때그때 계산**한다(저장하지 않는다). 글 · 댓글 · 좋아요 · SNS 공유는 지급 원장(`xp_event`)에 쌓는다. 커뮤니티 API(12장, v0.68 BE-115)가 글 · 댓글 · 좋아요 때 `LevelService.award(...)`로 지급한다.
- 어뷰징 방어: 같은 (사용자, 수급처, 대상)은 한 번만(유니크), 하루(KST) · 묶음 상한은 서버가 센다, 같은 사용자의 요청은 행 잠금으로 차례로 처리한다. 삭제 · 숨김 시 회수는 이번에 하지 않는다(글 삭제 · 신고 기능과 운영 정책이 아직 없다).

### L-1. 내 레벨 · 직업 — `GET /level`

**응답 200**

```json
{
  "level": 10,
  "xp": 412,
  "currentLevelXp": 385,
  "nextLevelXp": 430,
  "job": { "code": "LAW_STUDENT", "name": "로스쿨생" },
  "jobs": [
    { "code": "NONE", "name": "무직", "unlockLevel": 1, "unlockXp": 0, "unlocked": true, "selected": false },
    { "code": "LAW_STUDENT", "name": "로스쿨생", "unlockLevel": 10, "unlockXp": 385, "unlocked": true, "selected": true },
    { "code": "TRAINEE", "name": "연수원생", "unlockLevel": 20, "unlockXp": 854, "unlocked": false, "selected": false }
  ]
}
```

| 필드 | 설명 |
| --- | --- |
| `level` | 지금 레벨(1 ~ 100) |
| `xp` | 누적 경험치 |
| `currentLevelXp` · `nextLevelXp` | 지금 레벨에 닿은 누적 XP · 다음 레벨에 필요한 누적 XP. 최고 레벨이면 `nextLevelXp`는 `null`. 진행률 = (`xp` − `currentLevelXp`) / (`nextLevelXp` − `currentLevelXp`) |
| `job` | 카드에 보여 줄 직업. 사용자가 고른 직업(L-2), 고른 적이 없으면 열린 가장 높은 직업 |
| `jobs` | 직업 11개 전체(해금 순). `unlocked`: 지금 열렸는지, `selected`: 지금 카드에 보이는 직업인지(`job`과 같다) |

- 쿠키가 없거나 기록이 없으면 `404`가 아니라 Lv1 · 0 XP · 무직을 `200`으로 준다. 익명 ID를 새로 발급하지 않는다.

### L-2. 직업 선택 — `PUT /level/job`

카드 하단에 보여 줄 직업을 고른다(프로필 카드 · 커뮤니티 명함에 쓸 데이터).

**요청**

```json
{ "job": "LAW_STUDENT" }
```

**응답 200**: L-1과 같은 형식(저장 뒤의 값). 쿠키가 없었으면 `Set-Cookie`로 익명 ID를 발급한다(P-7 · P-8과 같다).

| 거절 | 조건 |
| --- | --- |
| 400 `VALIDATION_ERROR` | `job`이 없거나 모르는 코드 |
| 409 `JOB_LOCKED` | 아직 열리지 않은 직업(레벨이 모자람) |

- 직업이 열린 뒤에는 레벨이 내려가지 않으므로 고른 직업이 다시 잠기는 일은 없다. 고른 적이 없으면 열린 가장 높은 직업이 자동으로 보인다.

### L-3. SNS 공유 보상 — `POST /level/share-reward`

**요청**

```json
{ "caseId": 1 }
```

**응답 200**

```json
{ "awardedXp": 100, "level": { "level": 10, "xp": 485, "…": "…" } }
```

- 서버는 실제 공유 여부를 알 수 없어 **화면이 공유를 마친 뒤 부른 것을 믿는다.** 그래서 **완료한 사건만** 받을 수 있고, **사건당 1회 · 하루 1회**로 총량을 묶는다(평생 받는 양이 완료한 사건 수를 넘지 않는다).
- 이미 받았거나 오늘 상한을 채웠으면 `awardedXp`가 `0`인 `200`이다(오류가 아니다). `level`은 L-1과 같은 형식.
- SNS 공유 화면은 아직 없다(REQ-086, 이후). 이 API는 서버 준비분이다.

| 거절 | 조건 |
| --- | --- |
| 400 `VALIDATION_ERROR` | `caseId`가 없음 |
| 404 `EXPERIENCE_NOT_FOUND` | 쿠키가 없거나, 그 사건을 완료한 기록이 없음 |

## 12. (확장) 커뮤니티 API (BE-115)

> 결정 기록 [`BE-115.md`](decisions/BE-115.md). 화면 목 `frontend/src/mocks/communityMockApi.js`(FE-47 · FE-88 · FE-89 · FE-116)의 함수 · 응답 모양을 그대로 따른다. ERD v1.48 `community_post` · `community_comment` · `community_like`(V37).

### 12-1. 공통

- **읽기는 누구나, 쓰기(글 · 댓글 · 좋아요)는 로그인한 회원만** 한다. 쓰기는 `Authorization: Bearer {액세스 토큰}`이 있어야 하고, 없으면 `401 UNAUTHORIZED`다(목의 임시 코드 `LOGIN_REQUIRED` 대신 이 코드를 쓴다). 익명 ID 쿠키는 읽기만 하고 발급하지 않는다.
- **글쓴이와 명함 기준**: 글쓴이는 회원이다. 명함(레벨 · 직업 · 꾸미기 · 카드 아래 줄 · 성향 · 소개)과 경험치는 지금 구조대로 **익명 ID 단위**라, 쓴 요청에서 회원에 연결된 익명 ID 하나를 골라 함께 남긴다 — 쿠키의 익명 ID가 이 회원에 연결돼 있으면 그것, 아니면 이 회원에 연결된 익명 ID 중 가장 최근에 접속한 것. 마이페이지 · 레벨을 회원 기준으로 옮기면(후속) 이 규칙만 바뀐다.
- **글쓴이 명함 `author`** (글 · 댓글 · 메인의 사건 글이 같은 모양)

```json
{
  "nickname": "새벽의 배심원",
  "intro": "첫인상은 늘 한 번 의심해 봐요.",
  "level": 52,
  "job": { "code": "PRESIDING_JUDGE", "name": "부장판사" },
  "cardLine": { "line": "signature", "showLevel": true },
  "completedCount": 12,
  "profileCard": { "portrait": "/images/characters/standing/naero.webp", "frame": "gold", "…": "…" },
  "shownType": { "slot": "current", "type": { "shareId": "Qm7xK2pa", "name": "…", "…": "…" }, "deco": null }
}
```

| 필드 | 값 |
| --- | --- |
| `nickname` | 회원 닉네임. 닉네임이 없는 회원(카카오)은 **`방청객 {회원 ID}`** — 사람마다 다르고 바뀌지 않는다(닉네임은 M-7로 정할 수 있고, 정하면 그 이름으로 바뀐다) |
| `intro` | 한 줄 소개(P-8 `bio`). 없으면 `""` |
| `level` · `job` | L-1과 같은 값 · 형식 |
| `cardLine` | 프로필카드 아래 줄(P-8 `cardLine` · `cardLevel`, v0.65): `line`은 `type` · `signature` · `job`, `showLevel`은 레벨을 윗줄에 붙일지 |
| `completedCount` | 끝까지 체험한 사건 수 |
| `profileCard` | 프로필카드 꾸미기(P-7과 같은 칸). 저장 전이면 기본값 |
| `shownType` | 공개 성향카드. `slot`은 실제로 보여 주는 칸(`initial` 초심 · `retake` 재심 · `current` 최근), 고른 칸(P-8 `shownCard`)이 비면 최근 → 초심 → 재심 순으로 대신 고른다. `type`은 유형 공통 형식(9-1). `deco`(성향카드 꾸미기)는 아직 서버에 없어 늘 `null` — 화면이 기본 꾸미기로 그린다. 보여 줄 성향이 없으면 `shownType: null` |

- **글 `post`** (목록 · 상세 · 작성 응답이 같은 모양)

```json
{
  "postId": 108, "category": "VERDICT", "caseId": 1, "caseTitle": "빌린 돈 문제로 찾아온 지인을 살해한 사건",
  "author": { "…": "…" },
  "title": "처음엔 무겁게 봤는데, 다 읽고 나니 생각이 바뀌었어요", "body": "…", "images": [],
  "verdict": { "penaltyType": "PRISON", "reducedTo": null, "prisonMonths": 144, "fineAmount": null, "suspensionMonths": null, "upCount": 3, "downCount": 1 },
  "likeCount": 24, "liked": false, "commentCount": 3, "mine": false, "viewerJudged": true,
  "createdAt": "2026-10-19T21:14:00+09:00"
}
```

| 필드 | 값 |
| --- | --- |
| `category` | 말머리 `VERDICT`(판결 공유) · `QUESTION`(질문) · `FREE`(자유) |
| `caseId` · `caseTitle` | 이야기하는 사건. 사건이 없으면 둘 다 `null`, 사건이 공개 취소됐으면 `caseTitle`만 `null` |
| `images` | 사진 주소. **사진 올리기는 후속**이라 늘 빈 배열 |
| `verdict` | 판결 공유 글에 붙인 내 판결. 붙인 시점의 값을 남긴다(뒤에 다시 체험해도 바뀌지 않는다). 판결한 사람 자신의 판결만 담고 AI 판결 · 실제 판결은 넣지 않는다(앵커링 방지). 없으면 `null` |
| `liked` · `mine` | 보는 사람이 좋아요를 눌렀는가 · 쓴 글인가. 로그인 전이면 둘 다 `false` |
| `viewerJudged` | 보는 사람이 이 글의 사건 판결을 확정했는가(체험 상태 `VERDICT_CONFIRMED` 이후). 로그인 전이면 쿠키의 익명 ID로 본다. 판결 전이면 화면이 남의 판결 카드를 접는다(FE-93) |

- **글자 수**: 앞뒤 공백을 뺀 뒤 유니코드 글자 기준. 제목 1 ~ 60자(줄바꿈 · 제어 문자 없음), 본문 1 ~ 2,000자 · 댓글 1 ~ 500자(줄바꿈 · 탭은 되고 그 밖의 제어 문자는 안 됨). HTML은 거르지 않고 그대로 저장한다 — 화면은 `textContent`로만 그린다(P-8 `bio`와 같다).
- **경험치**(11-1 수급처 그대로, `LevelService.award`): 글 작성 30(하루 3건) · 판결 공유 글 보너스 20(사건당 1회) · 댓글 10(하루 5건) · 좋아요 누르기 2(글당 1회 · 하루 10개) · 좋아요 받기 3(글당 10개 · 하루 30개). 상한을 넘겨도 쓰기는 그대로 되고 경험치만 주지 않는다. **내 글에 누른 좋아요는 경험치를 주지 않는다.** 좋아요를 껐다 다시 켜도 다시 주지 않는다.
- 글 삭제 · 수정 · 신고, 사진 올리기는 이번 범위 밖이다(후속). 닉네임 설정은 M-7(BE-139)로 생겼다.

### C-1. 글 목록 — `GET /community/posts?category&caseId&sort&page`

| 쿼리 | 값 |
| --- | --- |
| `category` | 말머리로 거르기(생략하면 전체). 모르는 값이면 400 |
| `caseId` | 사건으로 거르기(생략하면 전체) |
| `sort` | `LATEST`(기본, 최신순) · `LIKES`(좋아요순) · `COMMENTS`(댓글순). 같으면 최신순. 모르는 값이면 400 |
| `page` | 1부터. **첫 글부터 `page`쪽 끝까지**(쪽마다 6개) 내려 준다 — 화면의 "더 보기"가 `page`를 늘려 다시 부르는 방식(목과 같다). 1 ~ 50으로 맞춘다 |

**응답 200**

```json
{
  "posts": [ { "…": "글" } ],
  "total": 21, "hasMore": true,
  "popular": [ { "postId": 108, "title": "…" } ],
  "cases": [ { "caseId": 1, "title": "…" } ]
}
```

- `popular`: 많이 본 글 셋. 조회 수는 세지 않아 **좋아요 순**(같으면 최신)으로 고른다. 거르기 조건과 상관없이 전체에서 고른다.
- `cases`: 공개 사건 전체(사건 고르기 · 거르기용).

### C-2. 글 상세 — `GET /community/posts/{postId}`

**응답 200**: 글 + `comments`(오래된 순)

```json
{ "postId": 108, "…": "…", "comments": [ { "commentId": 31, "author": { "…": "…" }, "body": "…", "createdAt": "2026-10-19T22:00:00+09:00", "mine": false } ] }
```

| 거절 | 조건 |
| --- | --- |
| 404 `POST_NOT_FOUND` | 글이 없음 |

### C-3. 글쓰기 — `POST /community/posts` (로그인)

**요청**

```json
{ "category": "VERDICT", "caseId": 1, "title": "…", "body": "…", "images": [], "shareVerdict": true }
```

| 필드 | 값 |
| --- | --- |
| `category` | 말머리(필수) |
| `caseId` | 이야기하는 공개 사건. 생략 · `null`이면 사건 없음 |
| `title` · `body` | 12-1 글자 수 |
| `images` | 생략하거나 빈 배열만 받는다(사진 올리기는 후속). 값이 있으면 400 |
| `shareVerdict` | `true`면 **서버가 내 판결을 붙인다**(화면은 판결 값을 보내지 않는다). `caseId`를 보냈으면 그 사건, 아니면 가장 최근에 확정한 판결이고, 글의 사건은 그 판결의 사건으로 맞춘다 |

**응답 201**: 쓴 글(글 모양, `mine: true`)

| 거절 | 조건 |
| --- | --- |
| 401 `UNAUTHORIZED` | 로그인하지 않음 |
| 400 `VALIDATION_ERROR` | 틀린 칸을 **모두** `details`에 담는다: `category`(`MUST_BE_ONE_OF_CATEGORIES`) · `caseId`(`CASE_NOT_FOUND`, 없거나 공개 전 사건) · `title`(`MUST_BE_1_TO_60_CHARS`) · `body`(`MUST_BE_1_TO_2000_CHARS`) · `images`(`NOT_SUPPORTED_YET`). 이 검사를 통과한 뒤 `shareVerdict`인데 붙일 판결이 없으면 `shareVerdict`(`VERDICT_NOT_CONFIRMED`) |

- 400이면 아무것도 저장하지 않는다.

### C-4. 좋아요 켜고 끄기 — `POST /community/posts/{postId}/like` (로그인)

**응답 200**

```json
{ "liked": true, "likeCount": 25 }
```

- 누를 때마다 켜고 끈다. 회원 한 명이 글 하나에 하나. 동시에 두 번 눌러도 수가 어긋나지 않는다.

| 거절 | 조건 |
| --- | --- |
| 401 `UNAUTHORIZED` | 로그인하지 않음 |
| 404 `POST_NOT_FOUND` | 글이 없음 |

### C-5. 댓글 — `POST /community/posts/{postId}/comments` (로그인)

**요청** `{ "body": "…" }` → **응답 201**: 쓴 댓글(C-2 `comments` 한 줄 모양, `mine: true`)

| 거절 | 조건 |
| --- | --- |
| 401 `UNAUTHORIZED` | 로그인하지 않음 |
| 400 `VALIDATION_ERROR` | `body`(`MUST_BE_1_TO_500_CHARS`) |
| 404 `POST_NOT_FOUND` | 글이 없음 |

### C-6. 커뮤니티 메인 — `GET /community/home`

> **응답은 한동안 그대로 온다**(서버 캐시, 기본 1시간, 설정 `community.home-cache-ttl`로 최대 하루까지 늘릴 수 있다). 새 글 · 댓글 · 레벨이 메인의 이야기 많은 사건 · 랭킹에 늦게 반영된다. 로그인 여부와 상관없이 모두가 같은 응답을 받는다. 글 목록 · 상세(C-1 · C-2)는 캐시하지 않는다.

**응답 200**

```json
{
  "hotCases": [ { "case": { "caseId": 1, "title": "…", "crimeCategoryLabel": "살인", "shortIntro": "…", "headline": "…", "deck": "…", "article": ["…", "…"], "thumbnailUrl": "…", "participantCount": 120, "estimatedMinutes": 15, "scriptReady": true }, "talkCount": 7, "postCount": 12, "posts": [ { "postId": 108, "title": "…", "likeCount": 24, "commentCount": 3, "author": { "…": "…" } } ] } ],
  "recommendedCases": [ { "case": { "…": "…" }, "postCount": 2, "posts": [ "…" ] } ],
  "ranking": [ { "rank": 1, "nickname": "…", "level": 71, "job": { "…": "…" }, "cardLine": { "…": "…" }, "completedCount": 21, "profileCard": { "…": "…" }, "shownType": { "…": "…" } } ],
  "popular": [ { "postId": 108, "title": "…" } ],
  "cases": [ { "caseId": 1, "title": "…" } ]
}
```

- **이야기 많은 사건 `hotCases`**: 최근 하루의 이야기 수(`talkCount` = 그 사건 글 + 그 사건 글에 달린 댓글)가 1 이상인 사건을 많은 순으로 셋(같으면 사건 ID 순). 사건 글은 좋아요 순으로 셋.
- **추천 사건 `recommendedCases`**: 이야기 많은 사건에 없는 공개 사건을 이야기 수 → 판결한 사람 수 순으로 셋. 사건 글은 둘. `talkCount`는 없다.
- `case`: 신문 지면(API 17)의 `headline` · `deck` · `article` 앞 두 단락과 사건 목록(API 1)의 값. 지면이 없으면 `headline` · `deck`은 `null`, `article`은 빈 배열. `postCount`는 그 사건 글 수(전체 기간).
- **레벨 랭킹 `ranking`**: 글이나 댓글을 쓴 회원을 레벨 → 체험한 사건 수 → 닉네임 순으로 열 명. 명함은 그 회원이 가장 최근에 쓴 글 · 댓글의 익명 ID 기준. 레벨은 사건 체험 · 커뮤니티 활동으로 오르며 성향과 무관하다. 명함에서 `intro`만 뺀 값이다.

### C-7. 붙일 수 있는 내 판결 — `GET /community/shareable-verdict`

**응답 200** — 가장 최근에 확정한 내 판결(공개 사건만)

```json
{ "caseId": 1, "caseTitle": "…", "verdict": { "penaltyType": "PRISON", "reducedTo": null, "prisonMonths": 144, "fineAmount": null, "suspensionMonths": null, "upCount": 3, "downCount": 1 } }
```

- 판결을 확정하기 전이거나 쿠키가 없으면 **204**(본문 없음). 목의 `null` 응답과 같은 뜻이다.
- 로그인했으면 12-1의 명함 기준 익명 ID, 아니면 쿠키의 익명 ID로 찾는다. 글쓰기(C-3)가 붙이는 판결과 같은 기준이다.

## 13. (확장) 일러스트 API (BE-142)

> 결정 기록 [`BE-142.md`](decisions/BE-142.md). 로그인 전 마이페이지(FE-138)의 "일러스트 N장" 안내에 쓴다.

### I-1. 일러스트 총 개수 — `GET /api/v1/illustrations/summary`

누구나 부를 수 있다(로그인 · 쿠키 불필요, 쿠키는 읽지도 발급하지도 않는다).

**응답 200**

```json
{ "total": 212, "byKind": { "cases": 76, "characters/adult": 9, "characters/icon/cropped": 18, "characters/icon/generated": 12, "characters/personality": 32 } }
```

| 필드 | 설명 |
| --- | --- |
| `total` | 일러스트 총 장수. **사건 컷(`images/cases`)과 캐릭터 이미지(`images/characters`, 아이콘 포함)의 모든 `.webp`**다. 루트의 대체 SVG · 배경(`desk-felt.webp`)은 세지 않는다 |
| `byKind` | 종류별 장수. 키는 `images/` 아래 폴더 경로(`cases`, `characters/scene`, `characters/icon/cropped` …), 값의 합이 `total`이다(예시는 일부만) |

- 모을 수 있는(컬렉션) 일러스트 수가 아니라 **서비스에 있는 일러스트 전부**의 수다.
- 이미지는 프론트(`frontend/public/images`)에만 있다. 백엔드는 목록 파일(`backend/src/main/resources/illustration-images.txt`)의 줄 수로 세며, 이미지를 더하거나 지운 뒤 `tools/illustrations/update-manifest.sh`로 목록을 갱신한다. 이미지 폴더와 목록이 어긋나면 백엔드 테스트(`IllustrationCatalogTest`)가 실패한다.
- 서버를 다시 띄울 때까지 값이 바뀌지 않는다(시작할 때 목록을 읽어 둔다).
- **저장소 기준이다(감수한 한계).** 사건 사진은 DB 저장값(관리자 업로드)이 우선이지만(BE-141) 이 값은 DB를 보지 않고 공개 여부도 따지지 않는다(비공개 사건 컷 · 공용 컷 포함, DB에만 있는 이미지는 미포함). 가능한 한 DB 기준으로 저장소를 최신화하는 것이 원칙이며, 관리자가 DB에 올릴 때 저장소에도 같은 파일을 올리면 어긋나지 않는다. DB 기준으로 바꾸는 일은 필수가 아닌 후속 이슈 BE-143이다.
