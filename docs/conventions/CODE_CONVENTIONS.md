# 코드 컨벤션 (Code Conventions)

이 문서는 AIBE7_FinalProject_Team2에서 공통으로 지키는 **가볍고(not too strict) 실용적인** 코드 규칙을 정의한다. 모든 팀원과 AI 코딩 에이전트가 따르는 기준이다. 과하게 엄격한 규칙은 지양하고, "왜"가 분명한 규칙만 유지한다.

> 참고: 백엔드는 **Spring Boot (Java)** 기준으로 작성했다. 프론트엔드 기술 스택이 확정되면 해당 스택 규칙을 하위 문서에 추가한다.

---

## 1. 공통 원칙

- **일관성이 우선이다.** 팀에서 정한 컨벤션이 개인 취향보다 우선한다.
- **자동화로 강제할 수 있는 것은 규칙으로 남기지 않는다.** 포맷팅은 IDE/Formatter(Spring  기준 = Spotless 또는 Checkstyle)에 맡기고, 사람이 신경 쓸 규칙만 남긴다.
- 특별한 이유가 없으면 이 문서의 기본값을 따른다. 예외가 필요하면 PR Description에 이유를 밝힌다.
- **"사용자(Next) 관점"** 으로 네이밍한다.

---

## 2. 공통 네이밍

| 항목 | 규칙 | 예시 |
| --- | --- | --- |
| 클래스/타입 | UpperCamelCase | `LoginService`, `AuthController` |
| 메서드/변수 | lowerCamelCase | `login()`, `accessToken` |
| 상수/Enum 값 | UPPER_SNAKE_CASE | `TOKEN_EXPIRY_SECONDS` |
| API 경로 | 소문자 축약 없음, kebab-case | `/api/users` |
| 패키지 | 소문자, 도메인 중심 | `com.team2.project.auth` |

- 축약어(API, URL)는 그대로 두고 나머지 단어만 대문자 규칙을 따른다. (예: `OAuthToken` ≠ `OAuthT??` — 일관되게 `OAuth` 유지)
- 불리언은 `is`/`has`/`can` 접두사로 시작한다. (`isActive`, `hasPermission`)

---

## 3. Java / Spring Boot 규칙

### 3-1. 클래스 구조

- 하나의 클래스는 **하나의 책임**을 가진다.
- 표준 계층 구성:
  ```
  controller → service → repository
              → domain (entity / enum / exception)
  dto, common(exception, response), config
  ```
- DTO, 공통 응답 등이 많아지면 `dto/`, `common/` 하위로 묶는다.

### 3-2. Lombok 사용

- `@Getter`, `@RequiredArgsConstructor` 등 생성/접근자 생성은 Lombok을 사용한다.
- 가변 필드가 있는 DTO가 아니라면 `@Setter`는 최소화한다. (불변성을 권장)
- 필요 시 `@Builder`를 사용하되, Builder가 불필요한 곳에는 억지로 쓰지 않는다.

### 3-3. 예외 처리

- 비즈니스 예외는 **도메인 전용 커스텀 예외**로 정의한다. (예: `LoginException`)
- Spring 표준 예외(`IllegalArgumentException`)를 비즈니스 흐름에 남용하지 않는다.
- 글로벌 예외 핸들러(`@RestControllerAdvice`)로 예외 → 일관된 응답 포맷으로 변환한다.
- 에러 응답에는 사용자에게 필요한 메시지만 담고, 내부 스택 트레이스를 노출하지 않는다.

### 3-4. 응답 구조

- 성공 응답은 결과 객체를 그대로 돌려준다 (감싸는 객체 없음).
- 실패 응답은 공통 형식(`code`, `message`, `currentStatus`, `details`)으로 통일한다.
- 자세한 규칙과 에러 코드 목록은 `docs/api-specification.md` 1-4 · 1-5 참고.

### 3-5. 정적 분석 도구

- 포맷팅·Lint는 대부분 자동화로 강제한다. (예: Spotless)
- **없으면 새로 도입하지 말고, IDE 기본 포맷터를 팀 공통으로 맞춘다.**

---

## 4. 하나의 메서드/기능이 지나치게 커지지 않게

- 메서드는 하나의 일만 하도록 짧게 유지한다. (가이드: ~20~30줄 이내 권장, 절대법칙은 아님)
- 조건이 겹치면 조기에 `return` 한다.
- 반복되는 코드는 작은 helper로 추출하되, 과도한 추상화는 금한다.
  - 추출된 helper가 "이름을 지어야 설명되는" 수준이면 추출하지 않는다.

---

## 5. 테스트 규칙

| 구분 | 프레임워크 | 응용 |
| --- | --- | --- |
| 단위 테스트 | JUnit 5 | service 로직 검증, `Mockito` 사용 가능 |
| 통합 테스트 | Spring Boot Test | DB 연동 검증 |

- 테스트 메서드는 `{메서드명}_{상태/기대결과}` 형태로 작성한다. (예: `login_success`, `login_invalidPassword_fails`)
- **핵심 로직(인증, 결제, 계산 등)** 은 반드시 테스트한다.
- 모든 테스트 대상이 커버리지 100%일 필요는 없다. 팀이 중요하다고 합의한 경로를 우선 테스트한다.

---

## 6. (예비) 프론트엔드 규칙

프론트엔드 스택이 정해지면 이 문서 하위에 문서(예: `docs/CONVENTIONS_FRONTEND.md`)를 별도로 추가한다. 그때까지 아래 공통 원칙만 적용한다.

- 공통 네이밍: 컴포넌트/타입 UpperCamelCase, 상태/유틸 lowerCamelCase
- 상태 관리 라이브러리, 폴더 구조, 포맷터(Prettier 등)는 스택 확정 후에 결정한다.

---

## 7. 어떤 규칙이 먼저 깨졌는지 보고하는 기준

1. 네이밍이 명확하지 않다
2. 하나의 클래스/메서드/파일이 여러 책임을 가진다
3. 예외 처리 방식이 일관되지 않다(하드코딩된 오류 응답 등)
4. 포맷터/컨벤션을 무시했다

이 외의 사소한 지적은 리뷰 엄격도 Inline Review보다는 코멘트로 남긴다.
