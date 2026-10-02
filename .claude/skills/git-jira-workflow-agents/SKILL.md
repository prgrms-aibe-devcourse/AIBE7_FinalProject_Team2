---
name: git-jira-workflow-agents
description: AIBE7_FinalProject_Team2 프로젝트에서 Claude Code 외의 AI 코딩 에이전트(GPT 계열 도구, Cursor, 기타 LLM 기반 코딩 어시스턴트)가 Jira(Atlassian MCP) 이슈 관리, Git 브랜치 생성, 커밋, Pull Request 작업을 수행할 때 따라야 할 워크플로우 규칙. 브랜치를 만들거나, 커밋 메시지를 작성하거나, PR을 생성하거나, Jira 이슈를 조회·생성·상태 전환하는 작업을 지시받았을 때 로드한다.
---

이 스킬은 규칙을 따로 담지 않는다. **저장소 루트의 `CLAUDE.md`를 읽고 그 규칙을 그대로 따른다.** 작업 규칙의 원본은 `CLAUDE.md` 하나다. `CLAUDE.md`의 "Claude Code"는 지금 작업 중인 에이전트로 바꿔 읽는다.

모델 전환 기능이나 Atlassian MCP(Jira) 연동이 없는 에이전트에만 적용하는 예외는 저장소 루트의 `AGENTS.md`에 있다.

특히 아래 장을 확인한다.

| 작업 | `CLAUDE.md` |
| --- | --- |
| 작업 시작 전 모델 · Effort 제안, "시작하자" 확인, Commit · PR은 명시 지시 때만 | 0장 · 0-1장 |
| Jira 이슈 작성 · 상태 전환 | 1 ~ 3장 |
| Branch · Commit · PR 규칙 | 4 ~ 6장 |
| 이슈 키 없이 작업을 지시받은 경우 | 10-1장 |

규칙을 바꿀 때는 `CLAUDE.md`만 고친다. 이 스킬에 규칙을 복제하지 않는다.
