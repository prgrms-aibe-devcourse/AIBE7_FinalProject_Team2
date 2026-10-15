# caseDetail

**S-20 사건 디테일** 화면이다 (확장, IA v1.12 · FE-32, 흐름 목업 ③ "사건 정보를 읽는다").

| 파일 | 내용 |
| --- | --- |
| `caseDetailPage.js` | `renderCaseDetailPage(container, { caseId, api, navigate, from })` — 오려 낸 기사를 그리고 체험으로 보낸다 |
| `caseDetail.css` | 사건 디테일 스타일 (책상 위 오려 낸 기사) |

## 구성

- 신문(S-02)에서 고른 기사를 오려 책상 위에 펼쳐 놓은 모습이다. 분류 · 사건 번호 · "신문으로 돌아가기" → 제목 → 부제 → [재연 사진 | 사건 개요 · 사건 정보(사건명 · 죄명 · 범죄 분류 · 예상 소요 · 참여) · 키워드] → 지면 기사(두 단) → 내로 · 남불 한마디 + 버튼.
- **형량 · 판결 정보는 없다**(FR-2-8). 지면 기사는 API 1의 제안 필드 `headline` · `deck` · `article`(S-03 개요 범위)이 있을 때만 그리고, 없으면 사건 제목 · `shortIntro`를 쓴다.
- S-02에서 기사를 골라 오면(`from`: 고른 기사의 화면 위치) 오려 낸 기사가 그 자리에서 날아와 펼쳐진다(0.6초). 새로고침 · 뒤로 가기로는 되풀이하지 않고, 움직임 줄이기면 바로 놓인다.
- Esc는 신문(S-02)으로.

## 사용하는 API

| 호출 | API | 동작 |
| --- | --- | --- |
| `api.getCaseDetail(caseId)` | API 16 `GET /cases/{caseId}` | 사건 정보 · 개요 · 내 진행 상태(`myStatus`). 체험 없이 열린다 |
| `api.startExperience(caseId)` | API 2 `POST /cases/{caseId}/experience` | 버튼을 누르면 부르고, 응답 `status`로 이어서 볼 화면을 정해 이동([`utils/screenByStatus.js`](../../utils/README.md)) |

- 버튼 문구는 `myStatus`로 정한다: `null` "체험 시작", `IN_PROGRESS` "이어서 하기", `COMPLETED` "결과 다시 보기". 이동은 어느 경우든 API 2 응답 상태로 정한다(API 명세 5장).
- 없는 사건(`CASE_NOT_FOUND`)은 `withErrorRedirect`가 S-14로 보낸다.
