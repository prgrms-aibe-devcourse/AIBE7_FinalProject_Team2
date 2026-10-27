# caseDetail

**S-20 사건 디테일** 화면이다 (확장, IA v1.12 · FE-32, 흐름 목업 ③ "사건 정보를 읽는다").

| 파일 | 내용 |
| --- | --- |
| `caseDetailPage.js` | `renderCaseDetailPage(container, { caseId, api, navigate, from })` — 1막 무대(대본 있는 사건) 또는 오려 낸 기사를 그리고 체험으로 보낸다 |
| `caseDetail.css` | 사건 디테일 스타일 (책상 위 오려 낸 기사) |

## 구성

- **대본이 있는 사건(`content/scripts.js`)은 이 화면이 곧 판결 체험 1막이다.** S-03과 같은 1막 무대를 그린다. 화면 전체가 S-02와 같은 신문 지면이다(제호 · 날짜 띠 · 탭 줄 = 막 칩 · 배너 제목 · [사진 | 기사 단], `components/newspaperFront.js`). 1막 엽서의 사전 판단은 지면 위에 "독자 의견 엽서" 팝업으로 뜬다 — 남불이 앞으로 나서며 건네고 엽서가 남불 손에서 날아와 펼쳐진다. 내로 · 남불 · 대사창 · 조작 줄은 화면 아래에 고정되고, 대사가 짚는 기사로 화면이 굴러간다. 처음에는 캐릭터 · 대사 없이 지면만 보인다(무대 `parked`). 지면 왼쪽 귀는 "← 다른 사건 보기"(S-02), 오른쪽 귀는 하던 체험이 있으면 "이어서 하기"(완료면 "결과 다시 보기"), 처음 보는 사건이면 사건 번호 · 분류다. 우하단에 "시작하기" 버튼이 있다.
  - "시작하기" · "이어서 하기"를 누르면 API 2를 부르고, 응답이 `STARTED`면 화면을 다시 그리지 않고 주소만 `/cases/{id}/impression`(S-03)로 바꾼 뒤(`navigate(..., { keepView: true })`) 내로 · 남불이 들어오며 1막 첫 대사("이 사건, 궁금해요? …", 모든 사건 공통 — `overviewPage.js` INTRO_STEP)가 시작된다. 1막 엽서의 사전 판단 폼은 그 사이 API 4로 받아 채운다(못 받으면 S-03을 새로 연다).
  - 그 뒤 단계(`PRE_JUDGED` 이후)면 그 화면으로 이동한다.
  - 무대 · 지면 · 폼은 `overview/overviewPage.js`의 `caseStage` · `mountStagePage`를 같이 써서 S-03과 똑같이 보인다. 2막부터는 지금처럼 무대 상자 화면이다.
- **대본이 없는 사건**은 오려 낸 기사를 책상 위에 펼쳐 놓은 단순 상세 화면이다. 분류 · 사건 번호 · "신문으로 돌아가기" → 제목 → 부제 → [재연 사진 | 사건 개요 · 사건 정보(사건명 · 죄명 · 범죄 분류 · 예상 소요 · 참여) · 키워드] → 내로 · 남불 한마디 + 버튼. 지면 기사(본문)는 두지 않는다. 모든 사건 대본 제작은 FE-33(낮은 우선순위).
- **형량 · 판결 정보는 없다**(FR-2-8).
- S-02에서 기사를 골라 오면(`from: 'paper'`) 누른 기사의 제목이 이 화면의 큰 제목(`.story-title`) 자리로 커지며 날아온다(FE-118, `utils/articleZoom.js` `landArticle`). 그동안 본뜬 S-02 지면은 흐려지며 걷힌다. 1초가 지나도 자료가 없으면 본뜬 지면을 걷고 "불러오는 중"을 보인다. 새로고침 · 뒤로 가기로는 되풀이하지 않는다.
- Esc는 신문(S-02)으로(체험을 시작한 뒤에는 받지 않는다).

## 사용하는 API

| 호출 | API | 동작 |
| --- | --- | --- |
| `api.getCaseDetail(caseId)` | API 16 `GET /cases/{caseId}` | 사건 정보 · 개요 · 내 진행 상태(`myStatus`). 체험 없이 열린다 |
| `api.startExperience(caseId)` | API 2 `POST /cases/{caseId}/experience` | 버튼을 누르면 부르고, 응답 `status`로 이어서 볼 화면을 정해 이동([`utils/screenByStatus.js`](../../utils/README.md)). 무대에서 `STARTED`면 그 자리에서 1막을 잇는다 |
| `api.getOverview(caseId)` | API 4 `GET /cases/{caseId}/experience/overview` | 무대에서 체험을 시작한 뒤 1막 엽서의 사전 판단 폼(형량 구간)을 채운다 |

- 버튼 문구는 `myStatus`로 정한다: `null` "체험 시작", `IN_PROGRESS` "이어서 하기", `COMPLETED` "결과 다시 보기". 이동은 어느 경우든 API 2 응답 상태로 정한다(API 명세 5장).
- 없는 사건(`CASE_NOT_FOUND`)은 `withErrorRedirect`가 S-14로 보낸다.
