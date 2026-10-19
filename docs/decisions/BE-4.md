# BE-4 의사결정 기록

> MVP 단계(WBS 1.3) · PR #37 · 담당 정민석

## 문제 / 배경
- 공통 에러 응답(`ErrorCode`·`BusinessException`·`ApiExceptionAdvice`), 익명 ID 쿠키(`NLNB_AID`) 발급·조회, 체험 상태 전이 공통 로직을 만들어야 했다.
- API 명세 1-4·1-6은 "상태 충돌(`INVALID_STATE`) 응답에는 반드시 `currentStatus`가 있어야 화면이 이동할 곳을 안다"를 요구했는데, 동시 요청 상황에서 이 전제가 깨지는 경로가 여러 차례 발견됐다.
- 접속 시각 갱신을 어떤 트랜잭션 경계로 처리할지(매 요청의 커넥션 사용량과 동시성 보장 사이의 트레이드오프)도 정해야 했다.

## 검토한 대안
- 동시 요청 제어 방식: 조건부 갱신(`WHERE status = ? AND last_reviewed_step = ?`) vs 비관적 잠금(`PESSIMISTIC_WRITE`). 같은 시기의 다른 PR(BE-8 #40)은 비관적 잠금을 썼다.
- 동시 제출 패자의 처리 순서(리뷰에서 지적됨, #37 코멘트 peanutboi710):
  - (a) 상태 전이(`apply`)를 먼저 호출하고 판단 저장을 나중에 한다.
  - (b) 서비스에서 유니크 위반 예외를 잡아 현재 상태를 다시 읽고 `InvalidExperienceStateException`으로 바꿔 던진다.
- 접속 시각 갱신 트랜잭션 경계: 조회 트랜잭션에 포함할지, `REQUIRES_NEW`로 분리할지. 분리하면 매 조회 요청마다 커넥션을 하나 더 쓰지만, 같은 트랜잭션의 `anonymous_user` 행 잠금으로 요청이 직렬화되는 부작용(BE-6 방식)이 없어진다.

## 선택한 방법과 이유
- 동시 요청은 조건부 갱신으로 처리하고 비관적 잠금은 쓰지 않았다. 유니크 제약 위반은 `409 INVALID_STATE`로, 그 밖의 제약 위반·예상 못 한 예외는 `500 INTERNAL_ERROR`로 분류했다(원인은 로그에만 남김).
- (리뷰 반영) 대안 (a)를 선택해 `ExperienceTransitionService` 사용 순서를 "상태 전이 먼저, 판단 저장은 그 다음"으로 뒤집었다. 반대 순서가 실제로 패자에게 `currentStatus` 없는 409를 발생시키는 것과, 권장 순서로 패자가 유니크 위반 없이 `currentStatus` 포함 409를 받는 것을 각각 테스트로 확인했다.
- (리뷰 반영) 이어서 `apply(experience, relatedWrites, change)` 오버로드를 추가해, 판단 저장(`relatedWrites`)을 상태 전이 호출 쪽에 넘기면 호출자가 저장 순서를 직접 맞추지 않아도 되게 했다. 패자는 유니크 위반 없이 깨끗하게 `currentStatus` 포함 `INVALID_STATE`를 받는다.
  - 이 과정에서 실제 버그 2건을 발견해 함께 고쳤다: 유니크 위반으로 flush 실패 후 같은 트랜잭션에서 바로 재조회하면 Hibernate가 AssertionFailure로 죽는 문제, 그리고 PostgreSQL이 제약 위반 후 그 트랜잭션 전체를 실패 상태로 만들어 같은 트랜잭션의 SELECT까지 거부하는 문제. 재조회는 반드시 새 트랜잭션(`REQUIRES_NEW`)에서 하도록 `ExperienceRepository.findStateInNewTransaction`을 추가했다.
- (리뷰 반영) 섹션 확인(API 7) 동시 클릭 문제: `applyIdempotent`의 재확인 로직을 상태만이 아니라 `(상태, lastReviewedStep)` 비교로 일반화해, "이미 그 섹션 이상 진행됨"을 멱등 성공으로 처리했다. 반환 타입도 `boolean`에서 `TransitionResult(changed, status, lastReviewedStep)`로 바꿔, 멱등 성공 시 호출자가 넘긴 목표값이 아니라 실제 현재 값을 돌려주도록 했다.
- 공개 요청(AI/실제 판결, 비교)은 이미 공개된 상태여도 성공으로 처리한다(멱등).
- 접속 시각 갱신은 `REQUIRES_NEW` + 10분 간격 조건으로 분리했다. 실제로 행이 바뀌는 UPDATE는 10분에 한 번만 발생하지만, 트랜잭션 시작·쿼리 실행 자체는 매 조회 요청마다 일어난다(PR 본문을 리뷰 중 이렇게 정정함).
- (리뷰 반영) `ApiExceptionAdvice`가 `ResponseEntityExceptionHandler`를 상속하도록 바꿔 `handleExceptionInternal` 한 곳으로 통일했다. 이 변경으로 기존에 누락되어 있던 `MissingRequestHeaderException`이 500이 아니라 400으로 바뀌었다.
- 쿠키 `Secure` 속성은 항상 켠다(API 명세 1-2 고정).

## 고려한 제약 사항
- 접속 시각 갱신을 `REQUIRES_NEW`로 분리하면 동시 요청이 커넥션 풀 크기(HikariCP 기본 10)에 가까워질 때 풀 고갈로 멈출 가능성이 있다. MVP 트래픽 기준으로는 당장 문제가 아니라고 판단했고, 코드는 바꾸지 않았다. 운영 전환 시 재확인이 필요하다.
- `InvalidJudgmentException`을 둘러싼 `develop` 충돌(BE-9 #39와 동시 진행): BE-9는 `RuntimeException` 상속 + 문자열 `apiErrorCode` + `Reason` 4개·`field`·3인자 생성자를, BE-4는 `BusinessException` 상속 + `ErrorCode`를 썼다. BE-4 구조로 합치면서 BE-9의 `Reason` 확장을 옮겨 담고, `field`가 `BusinessException.details`에도 실리도록 연결했다(API 명세 API 9의 `details: [{field, reason}]` 요구사항 때문).
- 상태 전이 방식이 PR마다 달라 생기는 혼선(BE-4는 공통 서비스, BE-7은 조건부 UPDATE 직접 구현, BE-8은 `PESSIMISTIC_WRITE`, BE-9는 유니크 위반을 잡아 변환)을 리뷰에서 표로 정리해 공유했다(#37 코멘트, peanutboi710). BE-7·BE-8은 이번 PR에서 직접 고치지 않고, 다음에 정리할 때 공통 메서드로 바꾸도록 남겼다.
- `relatedWrites`로 넘긴 쓰기가 지연 쓰기(INSERT가 아닌 기존 엔티티 수정)일 경우 유니크 위반 변환 범위 밖일 수 있다는 지적을 반영해, `relatedWrites.run()` 직후 `entityManager.flush()`를 추가했다.
- BE-6·BE-7·BE-9가 같은 경로의 파일(`common/exception`, `AnonymousUserService`, `AnonymousUserRepository`, `ExperienceRepository` 등)을 동시에 건드려 머지 충돌이 예상되어, 팀 채널에 별도로 안내했다.

## 관련 문서
- `docs/api-specification.md` (1-2·1-4·1-5·1-6, API 7·9 관련 섹션)
- `docs/conventions/CODE_CONVENTIONS.md` (3-4 응답 구조, 이 PR에서 옛 공통 래퍼 표기를 현재 API 명세 기준으로 수정)
