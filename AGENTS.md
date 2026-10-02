# AGENTS.md — AIBE7_FinalProject_Team2

Claude Code 외의 AI 코딩 에이전트(GPT 계열 도구, Cursor, 기타 LLM 기반 코딩 어시스턴트)도 **[CLAUDE.md](CLAUDE.md)의 규칙을 그대로 따른다.** 작업 규칙의 원본은 `CLAUDE.md` 하나이며, 이 문서에는 규칙을 복제하지 않는다.

- 작업을 시작하기 전에 `CLAUDE.md`를 처음부터 끝까지 읽는다.
- `CLAUDE.md`의 "Claude Code"는 지금 작업 중인 에이전트로 바꿔 읽는다.

## Claude Code 외 에이전트에만 적용하는 예외

| 상황 | 적용 |
| --- | --- |
| 모델 전환 · Effort 조정 기능이 없는 에이전트 | `CLAUDE.md` 0장의 모델 · Effort 제안 단계만 생략한다. 동의 확인과 "시작하자" 입력 뒤에만 작업을 시작하는 규칙은 그대로 지킨다. |
| Atlassian MCP(Jira) 연동이 없는 에이전트 | 이슈 생성 · 상태 전환 · 댓글 같은 Jira 자동화 단계는 생략하고, 필요한 Jira 작업을 사용자에게 안내한다. Branch · Commit · PR 규칙은 그대로 지킨다. |

규칙을 바꿀 때는 `CLAUDE.md`만 고친다. 이 문서에는 위 예외 외의 내용을 추가하지 않는다. (규칙 복제로 문서가 어긋났던 사례: COMMON-2)
