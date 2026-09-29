# Git 규칙 (Branch / Commit / Pull Request)

이 문서는 CLAUDE.md·AGENTS.md·.claude/skills에 흩어져 있던 **Git / GitHub 관련 규칙**을 한 군데로 모은다. 개발 워크플로우 전체 흐름과 Jira 상태 전환 시점은 아래를 따른다.

---

## 0. Jira ↔ Git 흐름 (요약)

```
모델/Effort 제안 → 사용자 동의 → "시작하자" 입력
   ↓
Jira 이슈 확인/생성 (Atlassian MCP)
   ↓
feature/{ISSUE-KEY}-{작업내용} 브랜치 생성
   ↓
Jira 상태: 할 일 → 진행 중
   ↓
Commit (메시지에 ISSUE-KEY 포함)
   ↓
Pull Request 생성 (제목/본문에 ISSUE-KEY 포함)
   ↓
Jira 상태: 진행 중 → 검토 중
   ↓
Code Review → PR Merge
   ↓
Jira 상태: 검토 중 → 완료 (Merge 이후에만)
```

- 상태 전환은 실제 상황과 반드시 일치시킨다. PR 생성만으로 "완료"로 바꾸지 않는다.

---

## 1. Branch 규칙

Branch 이름에는 반드시 Jira Issue Key를 포함한다.

- 형식: `feature/{ISSUE-KEY}-{작업내용}`, `fix/{ISSUE-KEY}-{작업내용}`, `refactor/{ISSUE-KEY}-{작업내용}`
- 작업내용은 영어 소문자 + 하이픈(-)
- 예: `feature/BE-15-login-api`, `feature/FE-8-login-page`, `fix/BE-21-login-error`, `refactor/BE-30-auth-service`

생성 절차:

```bash
git checkout main
git pull origin main
git checkout -b feature/BE-15-login-api
```

브랜치 생성 직후 Jira 상태를 "할 일 → 진행 중"으로 전환한다. (Atlassian MCP)

---

## 2. Commit 규칙

- 형식: `영어카테고리 : ISSUE-KEY 한글 설명 메시지`
- 예: `feat : BE-15 로그인 API 구현`, `fix : BE-21 로그인 예외 처리 수정`, `refactor : BE-30 인증 서비스 리팩터링`, `test : BE-15 로그인 테스트 추가`

| Type | 용도 |
| --- | --- |
| `feat` | 새로운 기능 |
| `fix` | 버그 수정 |
| `refactor` | 기능 변경 없는 코드 개선 |
| `test` | 테스트 추가/수정 |
| `docs` | 문서 수정 |
| `chore` | 설정 및 기타 작업 |
| `style` | 코드 포맷팅/스타일 변경 |

- 카테고리는 영어, 설명 메시지는 **한글로** 작성한다.
- Jira Issue Key는 콜론(`:`) 뒤, 한글 설명 맨 앞에 둔다.
- 커밋 메시지에 항상 대응하는 Jira Issue Key를 포함한다.

---

## 3. Pull Request 규칙

- 제목에는 반드시 Jira Issue Key를 포함한다. 권장 형식: `[ISSUE-KEY] 작업 내용` (예: `[BE-15] 로그인 API 구현`)
- Description에는 최소한 아래를 포함한다.

  ```
  ## 작업 내용
  - 로그인 API 구현
  - 이메일/비밀번호 검증
  - Access Token 발급

  ## Jira
  BE-15

  ## 테스트
  - [ ] 로그인 성공 테스트
  - [ ] 잘못된 비밀번호 테스트

  ## 확인 사항
  - API 정상 동작 확인
  - 예외 처리 확인
  ```

- PR 본문 하단에 연동된 Jira Issue Key를 남긴다. (Jira ↔ GitHub 자동 연동을 위함)
- PR 생성 후 Jira 상태를 "진행 중 → 검토 중"으로 전환한다. (완료로 바꾸지 않음)

---

## 4. Code Review

검토자는 아래를 확인한다.

- 요구사항을 제대로 구현했는가?
- 기존 기능에 영향을 주지 않는가?
- 예외 처리가 적절한가?
- 코드 구조가 적절한가?
- 네이밍이 명확한가?
- 불필요한 코드가 없는가?
- 테스트가 필요한 부분을 테스트했는가?
- 보안상 문제가 없는가?

수정 요청이 발생하면 **새 PR을 만들지 않고** 같은 Branch에서 추가 Commit으로 반영한 뒤 다시 리뷰를 요청한다.

```bash
git add .
git commit -m "fix : BE-21 로그인 예외 처리 수정"
git push origin feature/BE-21-login-error
```

---

## 5. PR Merge

Merge 전에 아래를 확인한다.

- Code Review 완료
- CI/CD 통과
- 충돌 없음
- 테스트 통과
- 요구사항 충족

PR이 **실제로 Merge된 이후에만** Jira 상태를 "검토 중 → 완료"로 전환한다. 또한 빠른 리뷰 요청은 가능한 한 빠르게 확인한다.

---

## 6. 이슈 키 없이 작업을 지시받은 경우

1. 백엔드(`BE`)/프론트엔드(`FE`) 중 어디에 만들 작업인지 **먼저 사용자에게 확인**한다.
2. 동일/유사 제목의 기존 이슈가 있는지 Atlassian MCP로 검색한다. 있으면 새로 만들지 않고 사용 여부를 확인한다.
3. 없으면 신규 이슈를 생성하고, Jira가 부여한 키를 그대로 사용한다.
4. 이후 절차는 위와 동일하다.
