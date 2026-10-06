# utils

화면 여러 곳에서 쓰는 작은 유틸 모음이다.

| 파일 | 내용 |
| --- | --- |
| `screenByStatus.js` | 체험 상태(`status`)에 따라 **이어서 볼 화면**을 정하는 표. API 명세서 1-6 "체험 상태 → 화면"과 같은 값이다 |

## screenByStatus

| 체험 상태 | 이동 화면 이름 |
| --- | --- |
| `STARTED` | `overview` (S-03) |
| `PRE_JUDGED` · `REVIEWING` | `review` (S-04) |
| `REVIEWED` | `summary` (S-05) |
| `VERDICT_CONFIRMED` | `ai` |
| `AI_REVEALED` | `court` |
| `COMPLETED` | `comparison` |

- 사건 목록에서 체험을 시작할 때(API 2 응답의 `status`)와, 공통 래퍼(`app/errorRedirect.js`)가 `INVALID_STATE`를 받았을 때 이 표로 화면을 정한다. 각 화면은 상태 불일치로 직접 이동하지 않는다.
- `ai` · `court` · `comparison` 화면 파일은 아직 없다. 해당 화면을 만들 때 이 이름에 맞춘다.
- API 명세서 1-6이 바뀌면 이 표도 함께 바꾼다.
