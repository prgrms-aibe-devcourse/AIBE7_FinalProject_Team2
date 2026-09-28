# CLAUDE.md — AIBE7_FinalProject_Team2

이 문서는 Claude Code가 본 프로젝트에서 작업할 때 따라야 할 규칙을 정의한다. Claude Code는 Atlassian MCP(Jira)와 연결되어 있으며, 이슈 생성/수정부터 Git Branch 생성, Commit, Pull Request 생성까지 전 과정을 직접 수행할 수 있다.

---

## 0. 작업 시작 전 모델 / Effort 제안 규칙 (필수)

Claude Code는 어떤 작업이든 곧바로 실행하지 않는다. 아래 순서를 반드시 지킨다.

1. 사용자가 작업을 지시하면, Claude Code는 먼저 **작업에 적합한 모델**과 **Effort(작업 강도)**를 제안한다.
   - 모델: `Opus 5`(복잡한 설계/아키텍처 변경/난이도 높은 디버깅) 또는 `Sonnet 5`(일반적인 기능 구현, 단순 수정, 반복 작업) 중 하나를 추천하고 이유를 간단히 밝힌다.
   - Effort: 작업 범위에 맞는 수준(예: low / medium / high)을 함께 제안한다.
2. 사용자의 **동의**를 받은 뒤에만 실제 모델/Effort 설정을 변경한다. 동의 없이 임의로 모델을 바꾸지 않는다.
3. 모델/Effort가 확정되어도 **"시작하자"라는 명시적 입력이 있을 때만** 실제 작업(코드 수정, 이슈 생성, 브랜치 생성 등)을 시작한다. 제안과 동의만으로는 작업을 개시하지 않는다.
4. 사용자가 모델/Effort를 별도로 지정하지 않고 곧바로 "시작하자"라고 말한 경우에도, 1번 제안 단계를 생략하지 않는다. 반드시 먼저 제안 → 동의 확인 → 시작 순서를 지킨다.

예시 흐름:

```
사용자: "로그인 API 만들어줘"
Claude: "이 작업은 표준적인 CRUD/인증 로직이라 Sonnet 5 + medium effort를 제안합니다. 진행할까요?"
사용자: "좋아, 그걸로 해줘"
Claude: (모델/Effort 설정 변경) "설정을 변경했습니다. '시작하자'라고 말씀하시면 시작하겠습니다."
사용자: "시작하자"
Claude: (그제서야 Jira 이슈 확인 → 브랜치 생성 → 개발 시작)
```

### 0-1. Commit / PR은 별도 지시가 있을 때만 실행

"시작하자" 이후 진행하는 작업(파일 수정, 브랜치 생성, Jira 이슈 생성/전환 등)과 별개로, **`git commit`과 Pull Request 생성은 사용자가 명시적으로 요청하기 전까지 수행하지 않는다.** "시작하자"라는 입력만으로 커밋/PR까지 자동으로 진행하지 않으며, 코드/문서 변경을 마친 뒤에는 결과만 보고하고 커밋 여부를 확인받는다. 사용자가 "커밋해줘", "PR 올려줘"처럼 명시적으로 지시한 경우에만 아래 3~6장의 Commit/PR 규칙에 따라 실행한다.

---

## 1. Jira 연동 개요 (Atlassian MCP)

Jira는 "무엇을 해야 하는지"를 관리하고, GitHub는 "어떻게 개발했는지"를 관리한다. 두 시스템을 잇는 값은 **Jira Issue Key**이며, Claude Code는 Atlassian MCP를 통해 Jira 이슈를 직접 조회·생성·수정·전환·댓글 작성할 수 있다.

```
Jira 이슈 생성 → 할 일 → 진행 중 → GitHub Branch 생성 및 개발
→ Pull Request 생성 → 검토 중 → Code Review → PR Merge → 완료
```

Claude Code가 Atlassian MCP로 수행하는 작업 범위:

- Jira 이슈 생성 / 조회 / 수정 (제목, 설명, 담당자, 상위 Epic 등)
- Jira 이슈 상태 전환 (할 일 → 진행 중 → 검토 중 → 완료)
- Jira 이슈 댓글 작성 (진행 상황, 블로커 공유)
- 위 작업과 연동하여 Git Branch 생성, Commit 생성, Pull Request 생성까지 이어서 수행

### 1-1. Jira Space 구성

| 구분 | Jira Space | Issue Key |
| --- | --- | --- |
| 백엔드 | Backend | `BE` |
| 프론트엔드 | Frontend | `FE` |
| 공통 | Common | `COMMON` |

예: `BE-15`(로그인 API 구현), `FE-8`(로그인 화면 구현), `COMMON-1`(CodeRabbit 한국어 코드 리뷰 설정 추가)

`COMMON` Space는 특정 팀(백엔드/프론트엔드)에 국한되지 않는 저장소 공통 설정 작업에 사용한다. (예: CI/CD 설정, 코드 리뷰 봇 설정, 저장소 전역 문서/정책 정비 등)

---

## 2. Jira Workflow (상태)

`할 일 → 진행 중 → 검토 중 → 완료`

- **할 일**: 담당자는 정해졌지만 아직 개발을 시작하지 않은 상태.
- **진행 중**: Branch를 생성하고 개발을 시작하면 Atlassian MCP를 통해 전환한다.
- **검토 중**: 개발을 완료하고 PR을 생성하면 Atlassian MCP를 통해 전환한다. 이 상태에서는 추가 개발이 아니라 Code Review를 기다린다.
- **완료**: Code Review가 끝나고 PR이 실제로 Merge된 이후에만 전환한다. PR 생성만으로는 완료로 바꾸지 않는다.

상태 변경은 실제 상황과 반드시 일치시킨다. 예를 들어 실제로 개발 중인데 "할 일"로 두거나, PR 리뷰 중인데 "완료"로 바꾸는 것은 잘못된 사용이다. Claude Code는 브랜치 생성, PR 생성, PR 머지 각 시점에 맞춰 Jira 상태 전환을 자동으로 수행한다.

---

## 3. Jira Issue 작성 규칙

- 제목은 무엇을 개발하는지 명확히 작성한다. (좋은 예: "로그인 API 구현", "JWT Access Token 발급 기능 구현" / 나쁜 예: "로그인", "JWT", "수정")
- Description은 가능하면 아래 형식을 사용한다.

  ```
  ## 작업 내용
  - 로그인 API 구현
  - 이메일/비밀번호 검증
  - Access Token 발급

  ## 완료 조건
  - [ ] 로그인 API 구현
  - [ ] 잘못된 이메일/비밀번호 처리
  - [ ] Access Token 발급
  - [ ] 예외 처리
  - [ ] 테스트 작성
  ```

- 모든 Issue에는 담당자(Assignee)를 지정한다. 담당자가 없는 작업은 가급적 만들지 않는다. Claude Code가 이슈를 생성할 때도 담당자를 지정하거나, 지정할 담당자를 사용자에게 먼저 확인한다.
- 하나의 Issue에 너무 많은 작업을 담지 않는다. (나쁜 예: "회원 기능 전체 구현"에 회원가입/로그인/로그아웃/프로필/비밀번호 변경/탈퇴를 다 포함 / 좋은 예: `BE-20` 회원가입 API, `BE-21` 로그인 API, `BE-22` 로그아웃 API 등으로 분리하고, 필요하면 상위 기능을 Epic으로 묶는다.)
- 작업 중 문제로 진행이 막히면 Atlassian MCP를 통해 Jira Issue 댓글에 상황을 남긴다. (예: 문제 상황, 확인 내용, 필요한 조치를 팀원이 Jira만 보고도 이해할 수 있도록 작성)

---

## 4. GitHub Branch 규칙

Branch 이름에는 반드시 Jira Issue Key를 포함한다. Claude Code는 브랜치를 생성하기 전에 대상 Jira 이슈가 존재하는지 확인하고, 없다면 먼저 이슈 생성 여부를 사용자에게 확인한다.

- 형식: `feature/{ISSUE-KEY}-{작업내용}`, `fix/{ISSUE-KEY}-{작업내용}`, `refactor/{ISSUE-KEY}-{작업내용}`, `setup/{ISSUE-KEY}-{작업내용}`(저장소 공통 기초 설정 작업, 주로 `COMMON` Space와 함께 사용)
- 예: `feature/BE-15-login-api`, `feature/FE-8-login-page`, `fix/BE-21-login-error`, `refactor/BE-30-auth-service`, `setup/COMMON-1-coderabbit-config`
- 작업 내용은 영어 소문자와 하이픈(-)을 사용한다.
- main(또는 팀에서 정한 기준 Branch)에서 새 Branch를 생성한다.

  ```bash
  git checkout main
  git pull origin main
  git checkout -b feature/BE-15-login-api
  ```

- 브랜치 생성 직후 Claude Code는 Atlassian MCP를 통해 해당 Jira 이슈 상태를 "할 일 → 진행 중"으로 전환한다.

---

## 5. Commit 규칙

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

- 카테고리(Type)는 영어로, 설명 메시지는 한글로 작성한다.
- Jira Issue Key는 콜론(`:`) 뒤, 한글 설명 맨 앞에 둔다.

Claude Code는 Commit 생성 시 항상 대응하는 Jira Issue Key를 메시지에 포함한다.

---

## 6. Pull Request 규칙

- 제목에는 반드시 Jira Issue Key를 포함한다. 권장 형식: `[ISSUE-KEY] 작업 내용` (예: `[BE-15] 로그인 API 구현`). Conventional Commit 형식을 쓰는 경우: `feat: [ISSUE-KEY] 작업 내용`
- Description에는 최소한 아래 항목을 포함한다.

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
  - [ ] 존재하지 않는 사용자 테스트

  ## 확인 사항
  - API 정상 동작 확인
  - 예외 처리 확인
  ```

- PR을 생성한 뒤 Claude Code는 Atlassian MCP를 통해 해당 Jira 이슈 상태를 "진행 중 → 검토 중"으로 자동 전환한다. PR 생성만으로 "완료"로 바꾸지 않는다.
- PR 생성 시 Description 하단에 연동된 Jira Issue Key를 반드시 남겨, Jira ↔ GitHub 자동 연동(6장 참고)이 정확히 매칭되도록 한다.

---

## 7. Code Review

검토자는 아래 항목을 확인한다.

- 요구사항을 제대로 구현했는가?
- 기존 기능에 영향을 주지 않는가?
- 예외 처리가 적절한가?
- 코드 구조가 적절한가?
- 네이밍이 명확한가?
- 불필요한 코드가 없는가?
- 테스트가 필요한 부분을 테스트했는가?
- 보안상 문제가 없는가?

수정 요청이 발생하면 새 PR을 만들지 않고 같은 Branch에서 추가 Commit으로 반영한 뒤 다시 Review를 요청한다.

```bash
git add .
git commit -m "fix : BE-15 잘못된 비밀번호 처리 수정"
git push origin feature/BE-15-login-api
```

---

## 8. PR Merge

Merge 전 아래를 확인한다.

- Code Review 완료
- CI/CD 통과
- 충돌 없음
- 테스트 통과
- 요구사항 충족

PR이 실제로 Merge된 이후에만 Claude Code는 Atlassian MCP를 통해 해당 Jira 이슈 상태를 "검토 중 → 완료"로 전환한다. PR 생성만으로 완료 처리하지 않는다는 원칙을 다시 한번 지킨다.

---

## 9. 백엔드 / 프론트엔드 연계 작업

하나의 기능을 백엔드와 프론트엔드가 각각 작업하는 경우, 두 Issue를 관련 Issue로 연결해 관리한다.

- Backend: `BE-15` 로그인 API 구현 → Branch `feature/BE-15-login-api`
- Frontend: `FE-8` 로그인 화면 및 API 연동 → Branch `feature/FE-8-login-page`

Claude Code는 연계 작업 생성 시 Atlassian MCP로 두 이슈 간 "관련 이슈(relates to)" 링크를 설정한다.

---

## 10. Jira ↔ GitHub 연동 및 Atlassian MCP 도구 사용

Jira와 GitHub 연동이 되어 있으면 Issue Key를 기준으로 Branch, Commit, PR이 자동으로 연결되어 추적된다. 따라서 Branch/Commit/PR에 Jira Issue Key를 일관되게 작성하는 것이 중요하다.

Claude Code는 다음과 같이 두 축의 도구를 조합해 하나의 작업 흐름을 수행한다.

- **Atlassian MCP**: Jira 이슈 생성 / 조회 / 수정 / 상태 전환 / 댓글 작성 / 이슈 간 링크 설정
- **Git / GitHub (로컬 git 명령 및 `gh` CLI)**: 브랜치 생성, Commit 생성, Push, Pull Request 생성 및 관리

한 번의 작업 지시(예: "BE-15 로그인 API 개발 시작하자")에 대해 Claude Code는 다음을 순서대로 수행한다.

1. Atlassian MCP로 `BE-15` 이슈 조회 (없으면 사용자에게 생성 여부 확인 후 이슈 생성)
2. `main`에서 `feature/BE-15-login-api` 브랜치 생성
3. Atlassian MCP로 `BE-15` 상태를 "할 일 → 진행 중"으로 전환
4. 개발 진행 (Commit 생성 시 Jira Issue Key 포함)
5. 개발 완료 후 Pull Request 생성 (제목/본문에 Jira Issue Key 포함)
6. Atlassian MCP로 `BE-15` 상태를 "진행 중 → 검토 중"으로 전환
7. PR이 Merge되면 Atlassian MCP로 `BE-15` 상태를 "검토 중 → 완료"로 전환

### 10-1. 이슈 키 없이 작업을 지시하는 경우

사용자가 이슈 키 없이 "로그인 API 개발 시작하자"처럼만 지시할 수도 있다. 이 경우 Claude Code는 번호를 임의로 추측하지 않고 아래 순서를 따른다.

1. 어떤 Jira Space(Backend `BE` / Frontend `FE`)에 만들 작업인지 **항상 사용자에게 먼저 확인**한다. 지시 내용만으로 임의 판단하지 않는다.
2. Space가 정해지면, 동일하거나 매우 유사한 제목의 기존 이슈가 있는지 Atlassian MCP로 먼저 검색한다. 이미 있다면 새로 만들지 말고 그 이슈를 사용할지 사용자에게 확인한다.
3. 해당하는 기존 이슈가 없으면 신규 이슈를 생성한다. 이때도 3번(담당자 지정) 규칙에 따라 담당자를 확인한다. 이슈 키(예: `BE-16`)는 Jira가 생성 시점에 자동으로 다음 순번을 부여하므로, Claude Code는 Jira가 응답한 키를 그대로 사용한다.
4. 이후 절차(브랜치 생성 → "할 일 → 진행 중" 전환 → 개발 → PR 생성 → "검토 중" 전환 → Merge 후 "완료" 전환)는 10장 본문의 절차와 동일하게 진행한다.

이 흐름도 0장의 모델/Effort 제안 → 동의 → "시작하자" 입력 절차를 생략하지 않는다.

---

## 11. 전체 흐름 요약 (Cheat Sheet)

```
모델/Effort 제안 → 사용자 동의 → "시작하자" 입력
   ↓
BE-15 (Jira 이슈 확인/생성 — Atlassian MCP)
   ↓
feature/BE-15-login-api (Branch 생성)
   ↓
Jira 상태: 할 일 → 진행 중 (Atlassian MCP)
   ↓
feat : BE-15 로그인 API 구현 (Commit)
   ↓
[BE-15] 로그인 API 구현 (PR 생성)
   ↓
Jira 상태: 진행 중 → 검토 중 (Atlassian MCP)
   ↓
Code Review → PR Merge
   ↓
Jira 상태: 검토 중 → 완료 (Atlassian MCP)
```

---

## 12. 반드시 지킬 것

1. 어떤 작업이든 모델/Effort를 먼저 제안하고 동의를 받은 뒤, "시작하자"라는 명시적 입력이 있어야 실제 작업을 시작한다.
1-1. Commit과 Pull Request 생성은 "시작하자" 입력과 별개로, 사용자가 별도로 명시 지시할 때만 수행한다.
2. 모든 개발 작업은 Jira Issue를 생성한 뒤 시작한다. (Issue 없이 바로 개발하지 않는다.)
3. Branch 이름에 Issue Key를 넣는다. (백엔드 `BE`, 프론트엔드 `FE`, 공통 `COMMON`)
4. Commit 메시지에 Issue Key를 넣는다.
5. PR 제목에 Issue Key를 넣는다.
6. Branch 생성 시 Jira를 "진행 중"으로, PR 생성 후 "검토 중"으로 변경한다.
7. PR Merge 후에만 Jira를 "완료"로 변경한다.
8. 리뷰 요청을 받으면 가능한 한 빠르게 확인한다.
9. 진행이 막히면 임의로 판단하지 말고 Jira 이슈 댓글에 상황을 남기고 사용자에게 공유한다.
