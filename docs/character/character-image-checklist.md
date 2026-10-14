# **캐릭터 이미지 제작 목록**

**작성 이력**

| 버전 | 날짜 | 내용 |
| --- | --- | --- |
| v0.1 | 2026-10-14 | 초안 (COMMON-28, WBS 7.6.8 관련) — 만들어야 할 캐릭터 이미지를 종류 · 우선순위 · 수량 · 형식 · 상태로 정리 |
| v0.2 | 2026-10-14 | 항목별 **예시 프롬프트**(5장, 프롬프트 모음 9장 v2 기준 · 참조 이미지 첨부형) 추가 |
| v0.3 | 2026-10-14 | A1 · A2 · A3 1차 생성 결과 · 후속 보완점 기록 |
| v0.4 | 2026-10-14 | 로 스탠딩 빈손(A4 추가, 봉투는 `deliver`만), B1 `idle`은 A4 변환만 |
| v0.5 | 2026-10-14 | 수정 항목 R1 ~ R3 추가, 지금 이미지는 샘플(모델 교체 후 재생성) |
| v0.6 | 2026-10-14 | 원본은 팀원 확인용 프라이빗 레포에 올릴 예정(#121 리뷰) |
| v0.7 | 2026-10-14 | A ~ C 1차 생성분 검수 · 상태 갱신(4-1 검수 기록), 원본 폴더 구조 정리(`cuts/` · `sheets/old/` · `standing/envelope/`), 컷 4:5 맞춤 · 이름 정리 |
| v0.8 | 2026-10-14 | D1 성향 16장 검수 · 상태 갱신, `cuts/personality/01.png` ~ `16.png`로 이름 · 폴더 정리 · 4:5 맞춤, 재생성 7장 표시 |
| v0.9 | 2026-10-14 | E1 공용 사건 재연 컷 · F1 연기 시트 · F2 역할 의상 시트 검수 · 상태 갱신, `sheets/acting/` · `sheets/role-outfits/` · `cuts/reenact/common/` 정리 |
| v0.10 | 2026-10-14 | F3 데포르메 대표 시트 6장 검수 · 상태 갱신(Claude 검수 + 사용자 자체 검수 메모), `sheets/deform/` 정리 |
| v0.11 | 2026-10-14 | **전체 목록 점검**(2-1) — 빠진 이미지 추가: A5 캐릭터별 기준 시트(1인 1장), B6 `idle-neutral`(나머지 4캐릭터), B7 `cheer`, B8 `read-thinking`, D2 성향 반대편 캐릭터 16장(번호표 변형 규칙 `-2`), F1 로봉 연기 시트, G6 로 데포르메 시트. 추가분 생성 프롬프트(5장) |

> 형식 · 이름 규칙 · 검수 기준은 [캐릭터 에셋 아키텍처](character-asset-architecture.md)가 기준이다(시트 2-2 · 장면 · 감정 코드 3-2 · 상황 4-1 · 크기 6-5 · 검수 7-2). 이 문서는 **무엇을 몇 장 만들고 어디까지 했는지**만 적는다.
> 캐릭터 설정은 [캐릭터 프로필 · 세계관](character-profile-worldview.md), 성향 이미지 번호는 [성향 이미지 번호표](personality-image-numbers.md)가 기준이다.
> 프롬프트는 [이미지 생성 프롬프트 모음](character-image-prompts.md) 9장(v2)을 쓴다. 항목별 예시는 이 문서 5장.
> 원본 PNG는 레포 밖 `~/Documents/NaeLawNamBul/character/`에 둔다. 웹용 webp만 `frontend/public/images/characters/`에 넣는다. 원본은 나중에 팀원 확인용 프라이빗 레포에 올린다(에셋 아키텍처 7-3 #5). 폴더 구조는 아래와 같다(10/14).
>
> - `sheets/` 시트(현재 사용), `sheets/old/` 이전 생성본. 기준 시트는 **캐릭터마다 1장** `character-sheet_{naero · nambul · law-scale · law-gavel · law-book}.png`(A5, 아키텍처 2-1). 두 캐릭터가 함께 든 옛 시트는 1인 시트가 나오면 `sheets/old/`로 옮긴다
> - `standing/` 스탠딩 5장(기준), `standing/envelope/` 봉투를 든 옛 스탠딩, `standing/_alt/` 탈락 후보
> - `cuts/{캐릭터}/base/{스타일-데포르메}/{장면}-{감정}.png` · `cuts/{캐릭터}/icon/{스타일-데포르메}/face-{감정}.png` — 아키텍처 6-4와 같은 구조(웹 변환 전 PNG). 로 합의 컷은 `cuts/trio/`, 내로 · 남불 · 로 비교 컷은 `cuts/compare/` 성향 결과 컷은 `cuts/personality/{01 ~ 16}.png` · 반대편 캐릭터 버전은 `{01 ~ 16}-2.png`(번호표 번호 · 변형 규칙 그대로, 아키텍처 6-4의 `personality/`와 같은 구조) 사건 재연 컷은 `cuts/reenact/case-{caseId}/`(아키텍처 6-4), **사건 이미지가 없을 때 대신 쓰는 공용 컷**은 `cuts/reenact/common/{headline · sub-1 · detail-1}.png`. 연기 시트는 `sheets/acting/{naero · nambul · law-scale · law-book}.png`, 역할 의상 시트는 `sheets/role-outfits/{naero · nambul}.png` 데포르메 대표 시트는 `sheets/deform/{naero · nambul}-{ranma · cheek · ppg}.png`
> - `_original/`(`character/` 바로 아래) 생성 직후 임시 이름(`a4-1` · `b1-1` · `f 3-1` 등) 원본 70장 보관. 이름을 바꿔 다른 폴더에 둔 파일의 사본이다. 원본 백업은 프라이빗 레포 `NaeLawNamBul_Images`에도 올렸다(10/14). 컷 PNG는 4:5(1122 × 1402)로 캔버스를 맞췄다(그림은 늘리거나 줄이지 않고 투명 여백만 추가)

**상태 표기**: ⬜ 대기 · 🟨 생성 중 · 🟦 검수 중 · ✅ 완료

> **지금 만드는 이미지는 샘플이다.** 현재 생성 모델이 프롬프트를 그대로 살리지 못하는 경우가 많아 화면 · 흐름 확인용 샘플로 쓰고, 더 좋은 모델로 바꾸면 같은 프롬프트로 다시 만든다. ✅도 "샘플 기준 완료"라는 뜻이다.

---

## 1. 공통 규칙

| 항목 | 규칙 |
| --- | --- |
| 장면 컷 | 4:5, 투명 webp 800 × 1000(품질 85), 원본 투명 PNG 2048 × 2560. 파일 이름 `{장면}-{감정}.webp` |
| 아이콘 | 1:1, 투명 webp 128 × 128(64 px로 줄여서 확인), 원본 512 × 512. 파일 이름 `face-{감정}.webp` |
| 재연 컷 | 4:3 · 16:9, webp 1200 × 900 · 1600 × 900. 신문지색 배경 그대로, 흑백 필터로 확인 |
| 시트 | 16:9, 신문지색 `#ECE2D4` 배경. 웹에는 넣지 않는다 |
| 생성 배경 | 장면 컷 · 아이콘은 크로마 그린 `#00B140`으로 만들고 배경을 지운다. 캐릭터 · 소품에 초록을 쓰지 않는다 |
| 글자 | 이미지 안에 글자 · 숫자 · 로고를 넣지 않는다(말풍선 · 이름표는 웹 UI) |
| 판결 전 화면 | 로는 저울 1인 · 수평(`neutral`)만 |
| 봉투 | 로는 **빈손이 기본**. 판결문 봉투는 `deliver`(발표 · 로딩 끝)에서만 든다(10/14) |
| 표정 | 화난 표정(`angry`)은 만들지 않는다 |
| 키 | 내로 120 · 남불 120 · 로 저울 230 · 법봉 190 · 법전 210(같은 바닥선, 아키텍처 2-5) |
| 로 이름 | 로울 = 저울(`law-scale`) · 로봉 = 법봉(`law-gavel`) · 로북 = 법전(`law-book`). 파일 · 폴더에는 코드를 쓴다. 판결 화면에서는 이름 대신 "AI 판사"(이미지에는 원래 글자를 넣지 않는다) |

---

## 2. 목록

### A. 기준 (먼저)

| # | 이미지 | 수량 | 형식 | 비고 | 상태 |
| --- | --- | --- | --- | --- | --- |
| A0 | 내로 · 남불 기준 시트, 로(저울 · 법봉) 기준 시트, 스탠딩 4장 | 6 | 시트 · 투명 PNG | `character-sheet_*` · `standing_*`(아키텍처 2-1). 시트 2장은 **두 캐릭터가 한 장**에 있어 A5에서 1인 시트로 다시 만든다(내로 · 남불 시트의 키 칸은 남불 1.5배로 확정값과 다르고 남불 턴어라운드는 3칸, 로 시트는 모든 칸에 봉투) | ✅ (시트는 A5로 교체 예정) |
| A1 | 로(법전) 기준 시트 | 1 | 시트 | 저울 · 법봉 시트와 같은 칸(정면 · 3/4 · 측면 · 뒷면 · 자세 6종 · 색 견본). 머리는 **목 · 기둥 없이 옷깃 위 공중에 떠 있는** 두꺼운 법전, 기본은 표지 정면. 손에는 법전을 들지 않는다. **10/14 채택: GPT 버전** `character-sheet_law-book.png`(1672 × 941) — 줄 반복 없음 · 모든 칸에서 책이 떠 있음 · 마네킹 목 없음 · 구두 검정 OK. 후속(=R2): 칸 배치가 피라미드형(1 / 4 / 6)이라 저울 · 법봉 시트와 다름, 자세 6종에 "아쉬움" 칸 없음, 색 견본 13개 중 밝은 파랑 · 주황 · 테라코타 3개가 캐릭터에 쓰이지 않음. 이전 생성본은 `sheets/old/` | 🟨 R2로 수정 |
| A2 | 로(법전) 스탠딩 | 1 | 투명 PNG | 1차(봉투 든 자세)는 `standing/envelope/standing_law-book_envelope.png`. **R1 결과 `standing_law-book.png`(971 × 1619)가 정식** — 빈손 · 옷깃 안 비어 있음(마네킹 목 없음) · 검정 구두 · 두껍고 거의 정사각형인 와인색 법전(시트와 같은 모양). 10/14 새로 만든 `a2.png`는 책이 세로로 긴 얇은 판형 · 밝은 빨강이고 마네킹 목이 남아 탈락(`standing/_alt/`) | ✅ |
| A3 | 키 비교 시트 | 1 | 시트 | 내로 120 · 남불 120 · 저울 230 · 법봉 190 · 법전 210, 같은 바닥선. 기존 내로 · 남불 시트의 키 칸(남불 1.5배)은 쓰지 않는다. 생성본(`v1` · `v2` · 새로 뽑은 `A3.png`)은 비율이 계속 어긋남 — 새 생성본 실측(내로 = 1): 남불 1.17 · 로울 2.52 · 로봉 2.13 · 로북 2.12(목표 1 · 1.92 · 1.58 · 1.75), 로 셋 구두가 바닥선 아래. **스탠딩 5장을 목표 비율로 줄여 합성한 `character-sheet_height_v3.png`로 대체**(R3). 생성본은 `sheets/old/` | ✅ (합성) |
| A4 | 로 스탠딩 **빈손 버전** (저울 · 법봉 · 법전) | 3 | 투명 PNG | 10/14 결정: 스탠딩에서 봉투 제외. **로울 · 로봉 2장 검수 통과** → 새 파일이 `standing_law-scale.png` · `standing_law-gavel.png`, 기존 봉투본은 `standing/envelope/`로 보관. 새 로울은 971 × 1619(기존 봉투본은 1122 × 1402)라 화면 배치 확인 필요, 구두가 짙은 갈색(로북은 검정). **로북은 R1 결과(`standing_law-book.png`)를 쓴다** — 새로 뽑은 `a4-3`은 R1과 거의 같아(픽셀 9% 차이) `standing/_alt/`로 보관 | ✅ |
| R1 | 로북 스탠딩 수정 | 1 | 투명 PNG | A2 1차 결과를 고침: 봉투 빼기 · 옷깃 안 마네킹 목 없애기 · 구두 검정. **`standing_law-book.png`가 R1 결과**(프롬프트 모음 9-13) | ✅ |
| R2 | 로북 기준 시트 수정 | 1 | 시트 | A1 GPT 버전을 고침: 칸 배치를 저울 · 법봉 시트와 같게, 자세 6종에 "아쉬움" 칸, 캐릭터에 안 쓰인 색 견본 3개 제거. 줄 반복 없음 · 책 떠 있음 · 마네킹 목 없음은 이미 통과(9-14). **이 시트를 첨부해 고친다** | 🟨 수정 대기 |
| A5 | **캐릭터별 기준 시트** (내로 · 남불 · 로울 · 로봉) | 4 | 시트 | 10/14 결정: 기준 시트는 **한 장에 한 캐릭터**(아키텍처 2-1 "캐릭터마다 1장"). 로북은 이미 1인 시트(A1 · R2). 칸은 마스코트(내로 · 남불)와 로(로울 · 로봉)가 각자 같은 배치: 정면 전신 · 턴어라운드 4칸(앞 · 3/4 · 옆 · 뒤) · 표정 6종(마스코트) 또는 자세 6종(로) · 그 캐릭터 색만 있는 색 견본. **키 비교 칸은 넣지 않는다**(키는 A3 합성 시트). 로는 빈손이 기본, 봉투는 `deliver` 칸 하나만. 파일 `character-sheet_{naero · nambul · law-scale · law-gavel}.png`, 나오면 옛 2인 시트는 `sheets/old/`(5장 A5 프롬프트) | ⬜ |
| R3 | 키 비교 v2 | 1 | 시트 | 비율 보정(로울 1.92 · 로북 1.75 · 로봉 1.58배), 로북 떠 있게, 숫자 없이, 전원 빈손. **합성 `character-sheet_height_v3.png`로 대체**(내로 · 남불 · 로 셋이 목표 비율 그대로, 같은 바닥선, 글자 없음 · 가이드선만). 한계: 머리 끝 높이에 맞춰 줄여서 로봉의 떡대(어깨 폭) 체형은 덜 드러남. 체형까지 살리려면 모델로 다시 생성 | ✅ (합성) |

- A1 · A2가 끝나면 아키텍처 2-4(색) · 2-5(프로필) · 5-1(IDENTITY)에 법전을 추가한다. 10/14 A2 실측: 표지 `#722C30` ~ `#621E25`(제안 `#5E1F2B`보다 밝은 와인색), 황동 모서리 `#CFAA75` 근처.
- A3 다시 뽑을 때 추가 문장: `Law (Book) floats above the collar with a clear gap; it never rests on the shoulders.` · `Make Naero and Nambul about 8% larger so the ratios match exactly.` · 숫자 라벨 없이 뽑고 나중에 얹는다.

### B. 우선 컷 (화면에 바로 쓰는 것)

| # | 캐릭터 | 컷 | 수량 | 쓰는 곳 | 상태 |
| --- | --- | --- | --- | --- | --- |
| B1 | 로(저울) | `idle-neutral` · `deliver-neutral`(봉투) | 2 | S-01 메인 · S-02 신문 지면 · 로딩 끝 전달 | ✅ 검수 통과 · PNG 정리(`cuts/law-scale/base/authority-adult/`), webp 변환 전. `idle-neutral`은 A4 변환 대신 새로 생성한 컷(`standing_law-scale.png`와 별개). 저울 수평 · 두 손 봉투 · 목 없음 OK, `deliver`는 "반 걸음 앞으로"가 약함 |
| B2 | 내로 (`mascot`) | `guide-neutral` · `guide-happy` · `react-surprised` · `react-happy` · `react-sorry` | 5 | 안내 · 리액션 | ✅ 검수 통과 · PNG 정리(`cuts/naero/base/editorial-mascot/`) · 4:5 맞춤. 후속: 컷마다 캐릭터 크기 차이(웹 변환 때 기준 정함), `react-happy` 눈 감은 웃음 확인 |
| B3 | 남불 (`mascot`) | B2와 같은 5종 | 5 | 안내 · 리액션 | ✅ 검수 통과 · PNG 정리(`cuts/nambul/base/editorial-mascot/`) · 4:5 맞춤. 후속: `react-surprised` 캐릭터가 작음, `react-happy` 눈 감음(남불은 동공이 보이는 눈이 정체성이라 확인) |
| B4 | 내로 · 남불 (`cheek`) | `idle-sorry` · `idle-thinking` | 4 | 로딩 · 빈 상태 · 오류 | 🟨 **수정 예정** · PNG 정리(`cuts/{naero,nambul}/base/editorial-cheek/`). 얼굴 · 볼 OK, 몸통이 "작고 둥근 만두 + 돌기 팔다리" 지시와 다름(`idle-thinking` 둘은 가는 다리로 서 있음), 남불 `idle-thinking`은 입을 활짝 웃어 표정이 어긋남 |
| B5 | 내로 · 남불 (`ppg` 아이콘) | `face-{neutral · happy · surprised · thinking · sorry · cheer}` | 12 | 토스트 · 완료 피드백 | 🟨 **일부 재생성 예정** · PNG 정리(`cuts/{naero,nambul}/icon/editorial-ppg/`), 64 px에서 감정 읽힘. 후속: 머리가 칸의 83 ~ 88%(규격 약 70%, 여백 6%까지 줄어듦), `cheer`가 `happy`와 반짝이 말고 차이 작음, `sorry` 땀방울이 64 px에서 안 보임 |
| B6 | 내로 · 남불 · 로봉 · 로북 | `idle-neutral`(**스탠딩을 4:5로 변환만**, 생성 없음) | 4 | S-17 프로필 기본 자세(4-1 `profile`) · 컬렉션 · 빈 상태 기본 · 결과 화면 합의 전후 단독 컷. 로울은 B1에 있음 → 5캐릭터 모두 `idle-neutral`을 갖춘다 | ⬜ |
| B7 | 내로 · 남불 (`mascot`) | `cheer-cheer` | 2 | S-06 판결 입력 응원(4-1 `cheer`). **특정 형량 쪽 응원처럼 보이면 안 됨**(빈손, 가리키지 않음) | ⬜ |
| B8 | 남불 · 내로 (`mascot`) | `read-thinking`(서류철 · 돋보기) | 2 | S-04 기록 함께 읽기(4-1 `companion`). 기본은 남불, 내로는 바꿔 쓰기용 | ⬜ |

### C. 판결 결과 화면 (S-07 · S-09)

| # | 이미지 | 수량 | 비고 | 상태 |
| --- | --- | --- | --- | --- |
| C1 | 로 3인 합의 컷 | 2 | 셋이 나란히(`idle`) · 고개를 맞댐(`think`). 같은 바닥선, **키는 각자 체형대로**(로울 230 · 로봉 190 · 로북 210, 아키텍처 2-5), 서열 없음. 이견(2 : 1)처럼 보이는 연출 금지(프로필 · 세계관 5-3). 16:9 투명 PNG(1672 × 941), `cuts/trio/trio-idle.png` · `trio-think.png`. **`idle`**: 배치 · 키(로봉 0.79 · 로북 0.89, 목표 0.83 · 0.91) OK, 로봉 · 로북 손가락 사이에 **초록 잔여 픽셀 92개**(배경 제거 때 남음) → 제거 필요. **`think`**: 포즈 OK, 셋이 서로를 향해 기울이지 않고(로북은 바깥쪽으로 기울어짐), 로북 키가 로봉과 거의 같음(0.86, 목표 0.91) | 🟨 보정 필요 |
| C2 | 로(법봉) · 로(법전) `deliver-neutral` | 2 | 발표자 컷. 저울은 B1. 4:5(1122 × 1402), `cuts/law-gavel/base/authority-adult/` · `cuts/law-book/base/authority-adult/`. 반 걸음 앞으로 두 손으로 봉투 · 법봉 머리 수평 · 법전 닫힌 채 떠 있음 · 목 없음 · 검정 구두 OK. 두 컷의 키 비율은 0.97(목표 0.905)이라 변환 때 크기 기준으로 맞춘다 | ✅ |
| C3 | S-09 비교 `trio-thinking` | 1 | 내로 왼쪽 · 로(저울) 가운데 · 남불 오른쪽. 셋 중 하나를 더 크게 · 앞에 두지 않는다. 16:9(1672 × 941), `cuts/compare/trio-thinking.png`. 내로 · 남불 같은 키 · 정체성 OK. 후속: 로울이 내로의 **1.69배**(목표 1.92), 로울 구두가 마스코트보다 36 px 아래(같은 바닥선 아님) → 로울을 약 1.14배 키우고 바닥선을 맞춘다 | 🟨 비율 보정 |

### D. 성향 테스트 (S-15 · S-16)

| # | 이미지 | 수량 | 비고 | 상태 |
| --- | --- | --- | --- | --- |
| D1 | 성향 결과 **기본** `01.webp` ~ `16.webp` | 16 | 장면 컷 4:5, `mascot`. 내로 8 · 남불 8(번호표 "캐릭터" 열). 밈 장면은 [번호표](personality-image-numbers.md). 화난 표정 금지, 4글자 코드 · 글자 넣지 않음. 10/14 16장 생성 · 검수: 캐릭터 배정 · 소품 · 글자 없음 · 화난 표정 없음 OK, `cuts/personality/01.png` ~ `16.png`로 정리(4:5, 1122 × 1402). **통과 9장**(01 · 04 · 05 · 07 · 08 · 12 · 14 · 15 · 16), **재생성 7장** — 자세: 02(편지 · 연필 · 다이어리 손 부족) · 06(새끼손가락 안 읽힘) · 10(어깨동무 팔 뭉툭) · 11(악수를 이끄는 느낌 아님, 팔을 대칭으로 벌림) · 13(보드에 손이 가려지고 표정이 생각 중이 아님), 표정: 03(스톱워치를 내밀지 않음 · 입이 활짝 웃음 · 몸통 쪽 초록 잔여 8 × 6 px) · 09(갸웃한 호기심이 아니라 그냥 웃음). 전체적으로 입 벌린 밝은 웃음이 많아 장면 감정이 약함 | 🟨 7장 재생성 예정 |
| D2 | 성향 결과 **반대편 캐릭터** `01-2.webp` ~ `16-2.webp` | 16 | 10/14 결정: 유형마다 내로 · 남불 **둘 다** 갖는다(32장). 번호표 **변형 규칙**(`{번호}-{변형}`, `-2` = 기본의 반대편 캐릭터, 아래 다른 캐릭터 · 스킨도 `-3`부터 같은 규칙)을 따른다. 내로 8(02 · 05 · 07 · 08 · 10 · 11 · 13 · 15) · 남불 8(01 · 03 · 04 · 06 · 09 · 12 · 14 · 16). 밈 장면은 D1과 같고, D1 재생성 보강 문구를 처음부터 넣는다(5장 D 장면 표). 원본 `cuts/personality/{번호}-2.png`. ⚠️ 화면에서 어느 버전을 보여 줄지 · 변형 주소를 DB에 둘지는 미정(번호표) — OG · `image_url`은 기본만 | ⬜ |

### E. 사건 재연 (S-02 · 사건 디테일)

| # | 이미지 | 수량 | 비고 | 상태 |
| --- | --- | --- | --- | --- |
| E1 | 사건별 재연 컷 | 사건 수 × 3 (`headline` · `sub-1` · `detail-1`) | 내로 · 남불이 **피해자 · 가해자를 번갈아** 맡는다(프로필 · 세계관 5-2). 흉기 · 상처 · 범행 순간 · 시신은 그리지 않는다(아키텍처 4-3). 누가 어떤 역할을 맡았는지 기록한다 | ⬜ |
| E1-공용 | 공용 사건 재연 컷 | 3 (`headline` · `sub-1` · `detail-1`) | **생성되지 않은 사건의 이미지를 대신하는 공용 컷**(사건 id가 없는 대체용, 어떤 사건에도 갈 수 있다). `cuts/reenact/common/`, 16:9 1672 × 941. 흉기 · 상처 · 피해자 · 시신 없음, 정체성 · 글자 없음 OK. 후속: **표정**(3장 모두 입을 크게 벌린 활짝 웃음 — 어떤 사건에도 쓰이므로 차분한 표정이 안전), `headline`의 **소방관 의상**(화재 사건 연상, 공용에는 부적절) · 배경이 복잡함, `detail-1`은 남불 불꽃 끝이 위쪽에서 잘리고 도장 · 서명 장면이 판결을 연상시킬 수 있음. 역할은 직업 예시(기자 · 소방관 / 탐정 · 기록 / 서명)라 피해자 · 가해자 번갈아 기록 대상이 아님 | 🟨 샘플로 사용 가능 · 보정 권장 |

### F. 시트 (P1, 컷을 만들기 전 참고용)

| # | 시트 | 수량 | 비고 | 상태 |
| --- | --- | --- | --- | --- |
| F1 | 연기 시트 | 5 (내로 · 남불 · 저울 · 법봉 · 법전) | 장면 코드별 전신 자세(3-2). **10/14 로봉 추가**(전에는 2인 기준 시트의 자세 6종으로 대신했지만, 시트가 1인 1장으로 바뀌고 로 셋을 똑같이 갖추기로 함 — 로봉은 ⬜). `sheets/acting/{naero · nambul · law-scale · law-book}.png`(16:9). **내로 · 남불 · 로울 3장 검수 통과**(8포즈, 정체성 유지, 봉투는 로 `deliver`에만, 목 없음). **로북**은 아쉬움 칸에서 책 모서리가 옷깃에 거의 닿고 옷깃 안이 마네킹 목처럼 보임(R2와 같은 문제). 내로 · 남불은 6번째 포즈가 서로 다름(내로 팔 뻗고 다리 참 · 남불 두 주먹), 로울 응원 칸은 두 주먹이 아니라 손을 모은 자세 | ✅ 3장 · 🟨 로북 · ⬜ 로봉 |
| F2 | 역할 의상 시트 | 2 (내로 · 남불) | 검사 · 변호인 · 판사 · 사건 역할. **구분 방법(띠 색 vs 소품)이 정해진 뒤**(아키텍처 7-3 #2). 10/14 띠 색안 시험본 생성(`sheets/role-outfits/{naero · nambul}.png`) — **참고용, 판정 보류**. 실측: 검사 띠 `#C17F39` / 변호인 `#E2AE75`(내로, 변호인이 약 31% 밝음) · `#C98127` / `#E0B067`(남불, 약 28%) — 명도를 맞추라는 3-4 규칙과 다르고, 둘 다 황토 계열이라 "다른 편"으로 읽히지 않음(띠 색안이 구분에 약하다는 근거). 그 외: 판사복이 로와 달리 주름 있고 짧음, 내로 판사만 빨간 타이(남불은 세 역할 모두 남색 타이), 내로 3/4 뷰가 정면과 거의 같음, 두 시트의 레이아웃이 다름, 남불 사건 역할 옷이 몸 빨강과 비슷 | 🟨 보류(재생성 필요) |
| F3 | 데포르메 대표 시트 | 6 | `ranma` · `cheek` · `ppg` × 내로 · 남불. `sheets/deform/`(16:9, 정면 전신 · 턴어라운드 · 표정 6종 · 색 견본). **`ppg` 2장 ✅**(거대한 타원 눈 · 손가락 없는 손 · 굵은 색면). **내로 `cheek`**: 규격(거대한 머리 · 볼 블러시 · 만두 몸)은 맞지만 사용자 검수 "조금 아쉬움". **`ranma` 2장은 란마 그림체가 안 나옴**: 머리가 몸의 약 60%(약 1.7등신, 목표 약 5등신), 눈이 둥글고 하이라이트 하나, 갸름한 턱 · 출렁이는 불꽃 없음 — 기본 마스코트처럼 나옴(사용자 검수 동일, "역이용 가능"). **남불 `cheek`**: 색 견본에 내로 색(남색 3 · 코 분홍 · 귀 자주)이 섞임, 뒷모습이 두 장 중복, 불꽃이 "작고 통통한 3갈래"보다 큼. 사용자 검수: `cheek` 두 장은 "조금 아쉬움" | ✅ `ppg` 2장 · 🟨 `ranma` 2장 · 🟨 `cheek` 2장(아쉬움) |

### G. 후순위

| # | 이미지 | 비고 |
| --- | --- | --- |
| G1 | 나머지 데포르메 8종 시트 | 아키텍처 3-1 |
| G2 | 소품 시트 | 3-3 카탈로그를 물건만 따로 |
| G3 | 스타일 변형 시트 | `riso` · `flat25` 등(3-6) |
| G4 | 꾸미기 레이어 | `layers/`, 저장 방식이 정해진 뒤 |
| G5 | 이벤트 · 굿즈용 | IP 확장(프로필 · 세계관 6장) |
| G6 | 로 데포르메 시트 (로울 · 로봉 · 로북 × `cheek` · `ranma` · `ppg`) | 아키텍처 2-2 "데포르메 시트 — 대표"의 로 몫. 로 쪽 DEFORMATION 블록은 프롬프트 모음 6-5 · 6-8 · 6-11에 이미 있음. 지금 로딩은 로 셋 `adult` 합의 연출이라 급하지 않음. **만들 때는 셋을 같이**(로울만 만들지 않는다), STYLE은 `editorial`로 바꾼다(2-3) |

### 2-1. 전체 목록 점검 (10/14)

> 2장 목록을 아키텍처 3-7(로 쓰임) · 4-1(상황별 기본값) · 4-2(화면별 규칙)와 캐릭터 5명(내로 · 남불 · 로울 · 로봉 · 로북) 기준으로 대조했다. **한 캐릭터만 있고 나머지가 빠진 이미지**를 찾아 위 목록에 추가했다.

**빠져 있어서 추가한 것**

| 빠진 것 | 근거 | 추가 항목 |
| --- | --- | --- |
| 기준 시트가 2인 1장(내로 · 남불 / 로울 · 로봉) | 아키텍처 2-1 "기준 시트 (캐릭터마다 1장)". 2인 시트는 키 칸 · 턴어라운드 · 봉투가 확정값과도 다름 | A5 (4장) |
| `idle-neutral`이 로울만 있음(B1) | 4-1 `profile`(S-17)은 고른 캐릭터의 `idle`, 컬렉션은 로 이름을 보여 줌(3-7) → 5캐릭터 모두 필요 | B6 (4장, 스탠딩 변환) |
| `cheer`(응원) 컷 없음 | 4-1 `cheer` — S-06 판결 입력, 내로 · 남불 | B7 (2장) |
| `read-thinking`(동행) 컷 없음 | 4-1 `companion` — S-04 기록 함께 읽기, 남불(+ 내로 바꿔 쓰기) | B8 (2장) |
| 성향 이미지가 유형마다 한 캐릭터만 | 10/14 결정: 유형마다 내로 · 남불 둘 다 | D2 (16장) |
| 연기 시트가 로울 · 로북만, 로봉 없음 | 로 셋은 서열 없는 별개 캐릭터(3-7). 대신 쓰던 2인 시트는 A5로 바뀜 | F1 로봉 (1장) |
| 데포르메 대표 시트가 내로 · 남불만 | 2-2 "데포르메 시트 — 대표 \| 내로 · 남불 · 로" | G6 (후순위, 로 셋 함께) |

**일부 캐릭터만 있는 게 맞는 것**(의도된 비대칭 — 추가하지 않음)

| 이미지 | 캐릭터 | 이유 |
| --- | --- | --- |
| 판결 전 화면 · S-01 · S-02 신문 지면의 로 | 로울만 | 판결 확정 전에는 저울 1인 · 수평만(3-7, 1장 공통 규칙). 법봉 · 법전은 결론 방향을 드러냄 |
| C3 S-09 비교 `trio-thinking` | 내로 · 로울 · 남불 | `trio`의 로는 기본 대표 저울(3-2). S-09는 세 판결을 같은 무게로 — 로 셋을 넣으면 AI 판결이 커 보임 |
| B2 · B3 `guide` · `react`, B4 `cheek`, B5 아이콘, E 재연, F2 역할 의상, D 성향 | 내로 · 남불 | 안내 · 리액션 · 사건 역할 · 성향은 내로 · 남불의 일(4-1). 로는 얼굴이 없어 아이콘 · 재연에 쓰지 않음 |
| C1 합의 컷 · C2 + B1 `deliver` | 로 셋 | 이미 셋 모두 있음(합의 2장 · 발표자 3장) |
| A3 키 비교 시트 | 5명 함께 | 키를 비교하려면 함께 나와야 함. 1인 1장 규칙의 예외 |
| S-08 실제 판결 | 로 없음 | 실제 재판부와 혼동 방지(4-2) |

- 앞으로 캐릭터가 늘면 이 표 기준으로 "다른 캐릭터도 있어야 하는 이미지인지"를 먼저 정한다. 성향 이미지는 번호표 변형 규칙(`-3`부터)으로 붙인다.

---

## 3. 순서 · 합계

**추천 순서**: A1 → A2 → A3 → B1 → B2 · B3 → D1 → C → B4 · B5 → F → E(사건이 정해지는 대로). **10/14 추가분**: A5(1인 시트 — 이후 생성의 첨부 기준이 되므로 먼저) → B6(변환만) → D2 · D1 재생성 7장 → B7 · B8 → F1 로봉 → G6

| 묶음 | 수량 |
| --- | --- |
| A 기준(남은 것) | 5 (R2 · A5 4장) |
| B 우선 컷 | 36 (B1 ~ B5 28 + B6 4(변환) · B7 2 · B8 2. B4 · B5 일부 재생성 예정) |
| C 판결 결과 | 5 |
| D 성향 | 32 (D1 16 + D2 16) |
| F 시트 | 13 (F1 5 · F2 2 · F3 6) |
| **합계 (A ~ D, F)** | **약 91** |
| E 재연 | 사건 수 × 3 (별도) |

---

## 4. 완료 처리

- 한 장을 만들면 아키텍처 7-2 체크리스트로 검수하고, 이 표의 상태를 ✅로 바꾼다.
- webp를 `frontend/public/images/characters/` 경로(아키텍처 6-4)에 넣는 것은 화면 연결 작업과 함께 한다.
- 프롬프트를 바꿔 다시 뽑은 경우 원본 폴더에 날짜를 붙여 남긴다(예: `standing_law-book_2026-10-14.png`).

---

### 4-1. 검수 기록 (2026-10-14)

> 기준은 이 문서 1장(공통 규칙) · 2장(목록)과 프롬프트 모음 9장이다. 이미지는 직접 열어 보거나 묶음 시트로 모아서 확인했고, 비율은 `magick`으로 실측했다. 모두 **샘플 기준** 판정이다.

| 구분 | 대상 | 결과 | 확인한 것 · 남은 일 |
| --- | --- | --- | --- |
| A | A1 / R2 `character-sheet_law-book.png` (GPT 버전) | 🟨 보완 | 줄 반복 없음 · 책 떠 있음 · 목 없음 OK. 칸 배치 · 아쉬움 칸 · 안 쓰인 색 견본 3개 보완 |
| A | A2 / R1 `standing_law-book.png` | ✅ | 정식. 새로 뽑은 `a2` · `a4-3`은 `standing/_alt/` |
| A | A3 / R3 `character-sheet_height_v3.png` | ✅ (합성) | 생성본은 로 셋이 목표보다 21 ~ 35% 큼 → 합성으로 대체 |
| A | A4 로울 · 로봉 빈손 | ✅ | 원본과 같고 봉투만 제거. 옛 봉투본은 `standing/envelope/` |
| B | B1 ~ B3 | ✅ | 투명 배경 · 초록 잔여 0 · 규격 개수 일치. 4:5 맞춤 |
| B | B4 | 🟨 수정 예정 | 몸통 형태 · 남불 thinking 표정 |
| B | B5 | 🟨 일부 재생성 예정 | 머리 비율 · `cheer` 구분 · `sorry` 땀방울 |
| C | C1 `trio-idle` | 🟨 | 초록 잔여 92 px 제거 |
| C | C1 `trio-think` | 🟨 | 서로 향해 기울임 없음 · 로북 키 |
| C | C2 `deliver` 2장 | ✅ | |
| C | C3 `trio-thinking` | 🟨 | 로울 1.69배(목표 1.92) · 바닥선 36 px 차이 |
| D | D1 통과 9장 (01 · 04 · 05 · 07 · 08 · 12 · 14 · 15 · 16) | ✅ | 번호표 밈 장면과 자세 · 소품 일치. 04 연필이 종이에서 떨어짐, 07 표정이 "차분히"보다 밝음, 15 다리 자세가 약간 어색한 것은 사소해서 통과 |
| D | D1 재생성 7장 (02 · 03 · 06 · 09 · 10 · 11 · 13) | 🟨 | 02 · 10 · 13은 자체 검수와 동일. 03 · 06 · 09 · 11은 추가 확인. 프롬프트 보강 문구는 아래 참고 |
| E | E1-공용 `cuts/reenact/common/` 3장 | 🟨 | 규칙 위반 없음. 표정 · 소방관 의상 · `detail-1` 잘림 · 도장 보정 권장. 흑백 확인: `headline` · `sub-1` 구분 OK, `detail-1`은 내로 털과 도장 · 법전이 붙음 |
| F | F1 연기 시트 내로 · 남불 · 로울 | ✅ | 8포즈 구분 명확. 6번째 포즈 차이 · 로울 응원 칸은 허용 범위 |
| F | F1 연기 시트 로북 | 🟨 | 아쉬움 칸 책이 옷깃에 닿을 듯 + 옷깃 안 마네킹 목 |
| F | F2 역할 의상 시트 2장 | 🟨 보류 | 띠 색 · 소품 구분 방법이 정해지면 재생성. 그때 판사복 모양 · 타이 · 3/4 뷰 · 레이아웃을 맞춤 |
| F | F3 `ppg` 내로 · 남불 | ✅ | 형태 · 표정 · 정체성 OK. 남불 `ppg` 표정 5번 아래 작은 검은 점은 사소 |
| F | F3 `ranma` 내로 · 남불 | 🟨 | 란마 그림체 미적용(마스코트처럼 나옴). 사용자 메모: 역이용 가능 — 기본 마스코트의 변형(몸만 키운 형태)으로 쓸 수 있는지 판단 |
| F | F3 `cheek` 내로 · 남불 | 🟨 | 내로는 규격 통과, 남불은 색 견본에 내로 색 혼입 · 뒷모습 중복. 사용자 검수 두 장 모두 "조금 아쉬움" — 어디가 아쉬운지는 재생성 전에 구체화 |

- 공통으로 남은 일: 웹 변환 전 캐릭터 크기 기준 통일(컷마다 캔버스 대비 높이가 다름), 정식 해상도(2048 × 2560 · 512 × 512)는 모델 교체 후 재생성 때 맞춘다.
- D1 재생성 보강 문구(재생성 때 해당 SCENE 줄에 더한다): 02 — 한 손에 사과 편지 · 한 손에 연필, 다이어리는 겨드랑이에 끼거나 생략, 표정은 미안한 듯 조용한 미소 / 03 — 스톱워치를 팔을 쭉 뻗어 카메라 쪽으로, 한쪽 눈썹만 올리고 입은 한쪽만 올린 미소 / 06 — 새끼손가락만 크게 앞으로, 나머지 손가락은 말아 쥠 / 09 — 고개를 기울이고 입은 작게 오므린 의문 표정 / 10 — 팔을 길게 옆으로 벌리고 손바닥 위로, 어깨동무 자리를 비워 둠 / 11 — 양손을 앞쪽 양옆으로 내밀어 마주 보게(악수 연결) / 13 — 한 손은 턱, 한 손은 보드 가장자리에 마커, 입은 작게 다문 생각 표정
- 해시로 확인: 이름 정리 때 B5 아이콘 12장이 원본과 같은 파일인지 대조했다(밀린 이름은 바로 고침).

---

## 5. 예시 프롬프트

[이미지 생성 프롬프트 모음](character-image-prompts.md) 9장(v2) 기준이다. **확정 이미지를 참조로 첨부하고 바꿀 내용만 쓰는 짧은 프롬프트**를 기본으로 했다. 블록 전문이 필요하면 9장의 블록을 이어 붙인다.

- **첨부**: 각 예시의 "첨부" 줄에 적은 확정 이미지를 같이 올린다(`~/Documents/NaeLawNamBul/character/`(시트는 `sheets/`, 스탠딩은 `standing/`)).
- **{ }** 안은 바꿔 쓴다.
- 모든 예시 끝에 **공통 AVOID**를 붙인다.

```
AVOID: text, letters, numbers, logos, watermarks, speech bubbles, angry or hostile faces or poses, sexualized or revealing depiction, gendered body features, cropped limbs, extra characters, black outlines, line art, drop shadows, ground shadows, 3D render, CGI, photo realism.
```

### A. 기준

- **A1 로(법전) 기준 시트**: 프롬프트 모음 9-9(완성본). 첨부 `character-sheet_law-scale_law-gavel.png`
- **A2 로(법전) 스탠딩**: 프롬프트 모음 9-10(완성본). 첨부 `standing_law-scale.png` · `standing_law-gavel.png`
- **A3 키 비교 시트**: 프롬프트 모음 9-11(완성본). 첨부 `standing_*` 전부
- **A4 로 스탠딩 빈손 버전**: 프롬프트 모음 9-12(완성본). 첨부 해당 `standing_law-*.png`. 로북은 A4-3 대신 R1을 쓴다
- **R1 로북 스탠딩 수정**: 9-13. 첨부 `standing_law-book.png` · `standing_law-scale.png`
- **R2 로북 기준 시트 수정**: 9-14. 첨부 `character-sheet_law-book.png` (+ R1 결과)
- **R3 키 비교 v2**: 9-15. 첨부 `character-sheet_height_v1.png` + 스탠딩 전부

**A5 캐릭터별 기준 시트 — 내로 · 남불** — 첨부 `character-sheet_naero-nambul.png`(그림체 · 색 참고) · 해당 `standing_*.png`

```
Use the attached reference images as the exact model of {Naero | Nambul}: copy the face, colors, proportions (about 2 heads tall, big head, short body) and flat art style exactly. Create a character sheet for this ONE character only. The attached sheet shows two characters; draw only {Naero | Nambul}.
SHEET LAYOUT —
- Top: one large front full body, hands on hips, friendly smile.
- Middle: turnaround — exactly four views: front, three-quarter, side, back. No duplicate views.
- Bottom: six head-and-shoulder expressions: neutral, happy, surprised, thinking, sorry, sleepy.
- Side panel: flat round color swatches of THIS character's colors only.
- No height comparison panel and no second character.
SCENE — plain newsprint background #ECE2D4, panels separated by space only, no frame lines, no labels, landscape 16:9.
AVOID (add): a second character, another character's colors in the swatches, a height chart, duplicate views, {Naero: a missing bandana, a different fur color | Nambul: a nose, blank eyes, flames below the head}.
```

**A5 캐릭터별 기준 시트 — 로울 · 로봉** — 첨부 `character-sheet_law-book.png`(1인 시트 배치 참고) · `character-sheet_law-scale_law-gavel.png`(그림체 참고) · 해당 `standing_law-*.png`(빈손 기준)

```
Use the attached standing image as the exact model of Law ({Scale | Gavel}): copy the head, robe, gloves, shoes, proportions and refined painterly art style exactly. Use the attached Law (Book) sheet only as the layout reference. Create a character sheet for this ONE Law only. The older attached sheet shows two Laws holding envelopes; draw only Law ({Scale | Gavel}) and keep the hands empty except in one pose.
SHEET LAYOUT —
- Top: one front full body, empty-handed (one hand open, presenting calmly).
- Middle: turnaround — exactly four views: front, three-quarter, side, back, all empty-handed. The head sits on its {brass post | wooden handle}; no face, no neck.
- Bottom: six poses — standing empty-handed, one fist raised gently, walking, hand on chin thinking, presenting with an open palm, holding a sealed kraft envelope out with both hands (the ONLY pose with the envelope).
- Emotion only by the head: {Scale: level = calm, pans lifted slightly = happy, pans swinging = surprised, one pan lower = thinking, level with slack chains and dropped shoulders = sorry | Gavel: level = calm, lifted slightly = happy, a small wobble = surprised, tilted to one side = thinking, bowed slightly with dropped shoulders = sorry}.
- Side panel: flat round color swatches of THIS character's colors only.
- No height comparison panel and no other Law.
SCENE — plain newsprint background #ECE2D4, panels separated by space only, no frame lines, no labels, landscape 16:9.
AVOID (add): a second Law, envelopes in more than one pose, faces, a neck, {a tilted scale in the front view | swinging the gavel}, another character's colors in the swatches.
```

- 로북은 R2(9-14)로 1인 시트를 고친다. 나오면 다섯 장을 나란히 놓고 칸 배치 · 크기가 같은지 확인한다.

### B. 우선 컷

**B1 로(저울) `idle-neutral` · `deliver-neutral`** — 첨부 `standing_law-scale.png`

```
Use the attached reference image as the exact model of Law (Scale): copy the head, robe, colors, proportions and art style exactly. Change only the pose.
POSE — {idle: (A4 빈손 스탠딩을 쓰면 생성할 필요 없음) standing upright, front to slight three-quarter view, empty-handed: one hand open, palm up and slightly out to the side, presenting calmly; the other hand resting at the side. No envelope or other prop. | deliver: stepping half a step forward and holding the sealed verdict envelope out with both hands toward the viewer, calm and polite}. The scale is perfectly level.
SCENE — a single character, centered, full body visible, 4:5 vertical, 15% margin, perfectly flat solid green #00B140 background. No green on the character.
AVOID (add): faces, a neck, a tilted scale.
```

**B2 · B3 내로 · 남불 `guide` · `react`** — 첨부 `standing_naero.png` 또는 `standing_nambul.png`

```
Use the attached reference image as the exact model of {Naero | Nambul}: copy the face, colors, proportions (about 2 heads tall, big head, short body) and flat art style exactly. Change only the pose and expression.
POSE — {guide-neutral: pointing to one side with one hand as if introducing something, calm friendly face | guide-happy: the same pointing pose with a bright open smile | react-surprised: jumping slightly, wide eyes, round open mouth, small surprise marks beside the head | react-happy: both arms up, big smile | react-sorry: shoulders down, drooping eyebrows, small mouth, one small sweat drop}.
SCENE — a single character, centered, full body visible, 4:5 vertical, 15% margin, perfectly flat solid green #00B140 background. No green on the character.
AVOID (add): {Naero: a missing bandana, a different fur color | Nambul: a nose, blank eyes, flames below the head}.
```

**B4 내로 · 남불 `cheek` (로딩 · 빈 상태)** — 첨부 `standing_naero.png` · `standing_nambul.png`

```
Use the attached reference image as the model of {Naero | Nambul}'s colors and design, but redraw the character as a 'cheek-squish' mochi chibi: about 2 heads tall with a huge wide head, big round squishy cheeks bulging past the face line, a tiny round dumpling body and nub limbs, soft blush ovals. Keep {the navy fur, yellow eyes and red bandana | the three-color flame head, visible pupils and no nose} exactly.
POSE — {idle-sorry: sitting, cheeks puffed, drooping eyebrows, a small sweat drop | idle-thinking: head tilted, a small question mark shape beside the head (no text)}.
SCENE — a single character, centered, 4:5 vertical, 15% margin, flat solid green #00B140 background. No green on the character.
```

**B5 내로 · 남불 `ppg` 아이콘 `face-{감정}`** — 첨부 `standing_naero.png` · `standing_nambul.png`

```
Use the attached reference image as the model of {Naero | Nambul}'s colors and identity, simplified into a bold, minimal cartoon face icon: giant oval eyes, very simple shapes, strong color blocks. Keep {the navy fur, yellow eyes, pink nose and a hint of the red bandana | the three-color flame, visible pupils, no nose}.
EXPRESSION — {neutral | happy | surprised | thinking | sorry | cheer}.
SCENE — face only, the head filling about 70% of a 1:1 square, centered, readable at 64 px, flat solid green #00B140 background. No green on the character.
```

**B6 `idle-neutral` (내로 · 남불 · 로봉 · 로북)** — 생성하지 않는다. 해당 `standing_*.png`를 4:5 캔버스(투명 여백만 추가)로 맞춰 `cuts/{캐릭터}/base/{스타일-데포르메}/idle-neutral.png`로 둔다(내로 · 남불 `editorial-mascot`, 로 `authority-adult`). 스탠딩을 고치면 다시 변환한다.

**B7 내로 · 남불 `cheer-cheer`** — 첨부 `standing_naero.png` 또는 `standing_nambul.png`

```
Use the attached reference image as the exact model of {Naero | Nambul}: copy the face, colors, proportions (about 2 heads tall, big head, short body) and flat art style exactly. Change only the pose and expression.
POSE — cheering someone on: both small fists held up in front of the chest, a bright determined smile, a tiny hop. Empty hands, not pointing in any direction, no props.
SCENE — a single character, centered, full body visible, 4:5 vertical, 15% margin, perfectly flat solid green #00B140 background. No green on the character.
AVOID (add): pointing, holding signs or numbers, {Naero: a missing bandana | Nambul: a nose, blank eyes, flames below the head}.
```

**B8 남불 · 내로 `read-thinking`** — 첨부 해당 캐릭터 스탠딩

```
Use the attached reference image as the exact model of {Nambul | Naero}: copy the face, colors, proportions (about 2 heads tall, big head, short body) and flat art style exactly. Change only the pose and expression.
POSE — reading a case file together with the viewer: holding an open plain file folder in one hand and a small magnifying glass in the other, eyebrows slightly drawn together, mouth closed, calm and focused.
SCENE — a single character, centered, full body visible, 4:5 vertical, 15% margin, perfectly flat solid green #00B140 background. No green on the character or props.
AVOID (add): readable text on the papers, a big grin, {Nambul: a nose, blank eyes, flames below the head | Naero: a missing bandana}.
```

### C. 판결 결과 화면

**C1 로 3인 합의 컷** — 첨부 `standing_law-scale.png` · `standing_law-gavel.png` · `standing_law-book.png`

```
Use the attached reference images as the exact models of the three versions of Law (Scale, Gavel, Book): copy their heads, robes, colors, proportions and art style exactly.
SCENE — {idle: the three stand side by side on the same ground line, Gavel on the left, Scale in the center, Book on the right, each at its own height (Scale tallest, Book medium, Gavel shortest and broadest), calm | think: the three lean their heads slightly toward each other in a quiet discussion, one hand on the chin}. Nobody stands in front of the others. No disagreement, no pointing at each other.
The scale is level, the gavel is level, the book floats above the collar with no neck, front cover facing the viewer, closed.
Background: perfectly flat solid green #00B140, full bodies visible, 10% margin, {4:5 | 16:9}. No green on the characters.
AVOID (add): faces, necks, one Law stepping in front of the others, arguing poses.
```

**C2 로(법봉) · 로(법전) `deliver-neutral`** — 첨부 해당 스탠딩

```
Use the attached reference image as the exact model of Law ({Gavel | Book}). Change only the pose.
POSE — stepping half a step forward and holding the sealed kraft verdict envelope out with both hands toward the viewer, calm and polite. The {gavel is level | book floats above the collar with no neck, front cover facing the viewer, closed and level}.
SCENE — a single character, centered, full body visible, 4:5 vertical, 15% margin, flat solid green #00B140 background.
AVOID (add): faces, swinging the gavel, a neck or post under the book, an open book.
```

**C3 S-09 비교 `trio-thinking`** — 첨부 `standing_naero.png` · `standing_law-scale.png` · `standing_nambul.png`

```
Use the attached reference images as the exact models of Naero, Law (Scale) and Nambul. Keep each character's own art style (Naero and Nambul flat mascots, Law refined painterly).
SCENE — Naero on the left, Law (Scale) in the center, Nambul on the right, standing on the same ground line, all thinking (one hand on the chin, heads tilted slightly). Naero and Nambul are the same height; Law (Scale) is about 1.9 times taller. Nobody stands in front; nobody is emphasized. The scale is level.
Background: perfectly flat solid green #00B140, full bodies visible, 10% margin, 16:9. No green on the characters.
```

### D. 성향 테스트

**D1 · D2 성향 결과 `{번호}.webp` · `{번호}-2.webp`** — 첨부 해당 캐릭터 스탠딩. 밈 장면은 [번호표](personality-image-numbers.md), 영어 장면 문구는 아래 표

```
Use the attached reference image as the exact model of {Naero | Nambul}: copy the face, colors, proportions and flat art style exactly.
SCENE — {아래 표의 장면 문구, {C}를 Naero 또는 Nambul로}. Friendly and warm; the character is never angry or mocking. The facial expression must match the scene described (not a default open-mouth grin).
A single character (props allowed), centered, full body visible, 4:5 vertical, 15% margin, flat solid green #00B140 background. No green on the character or props.
AVOID (add): text on props, the letters of personality codes.
```

**장면 문구 (D1 · D2 공통, `{C}` = 캐릭터)** — D1 재생성 보강 문구(4-1)를 반영했다. D1 재생성 7장(02 · 03 · 06 · 09 · 10 · 11 · 13)도 이 문구로 다시 뽑는다.

| 번호 | 기본 `{번호}` | `-2` | 장면 문구 |
| --- | --- | --- | --- |
| 01 | 내로 | 남불 | {C} standing straight with arms crossed, a thick plain rule binder standing beside them, a calm, firm but friendly look |
| 02 | 남불 | 내로 | {C} holding a small sealed apology letter in one hand and writing in a small notebook with a pencil in the other hand, a quiet, slightly apologetic smile |
| 03 | 내로 | 남불 | {C} holding a stopwatch out toward the viewer with the arm fully stretched, one eyebrow raised, a confident half smile with one corner of the mouth up |
| 04 | 내로 | 남불 | {C} holding a clipboard and ticking a checkbox with the pencil tip touching the paper, focused eyes, a small closed-mouth smile |
| 05 | 남불 | 내로 | {C} holding a smartphone with both hands and typing a group announcement, a cheerful smile; the screen is blank |
| 06 | 내로 | 남불 | {C} holding one big pinky finger out toward the viewer for a pinky promise, the other fingers curled, a warm gentle smile |
| 07 | 남불 | 내로 | {C} pushing up round glasses with one finger, looking calmly at the viewer, mouth closed in a small calm line |
| 08 | 남불 | 내로 | {C} with a blank, neutral face (straight mouth) giving a thumbs-up with one hand, the other hand on the hip |
| 09 | 내로 | 남불 | {C} with one hand on the chin and the head tilted to one side, curious eyes, a small pursed questioning mouth |
| 10 | 남불 | 내로 | {C} with one arm stretched out wide to the side at shoulder height, palm up, inviting someone in for a shoulder hug, leaving empty space beside them, a friendly smile |
| 11 | 남불 | 내로 | {C} reaching both hands forward to the left and right, palms facing each other, as if bringing two people's hands together for a handshake, a kind encouraging smile |
| 12 | 내로 | 남불 | {C} with one hand on the chest and the other palm held gently forward, a soft understanding smile |
| 13 | 남불 | 내로 | {C} holding a small whiteboard with simple arrows and shapes (no text) at one side, the other hand on the chin, eyes looking up, mouth closed, deep in thought |
| 14 | 내로 | 남불 | {C} holding a plain handkerchief in one hand and offering the other hand forward, a small, slightly sad but kind smile |
| 15 | 남불 | 내로 | {C} holding a glowing cheer stick high, mid-step, an excited big smile |
| 16 | 내로 | 남불 | {C} wrapped in a soft blanket and holding a warm mug with both hands, a gentle peaceful smile |

- 남불이 담요(16) · 종이(02 · 13) 같은 소품을 들 때는 `the props stay clear of the flame`을 덧붙인다. 내로는 담요(16)를 둘러도 반다나가 보이게(`the bandana stays visible`).
- 파일: D1은 `cuts/personality/{번호}.png`, D2는 `cuts/personality/{번호}-2.png`(4:5 맞춤 후).

### E. 사건 재연

**E1 사건별 재연 컷 `headline` · `sub-1` · `detail-1`** — 첨부 `standing_naero.png` · `standing_nambul.png`

```
Use the attached reference images as the exact models of Naero and Nambul (faces, colors, proportions, flat style). They are acting out a real case as roles; only their outfits and props change.
SCENE — a calm newspaper-photo-like scene: {situation in English, e.g. "two neighbors facing each other in an apartment hallway after a noise complaint"}. Naero plays {role A, e.g. "the downstairs neighbor in a cardigan"} and Nambul plays {role B, e.g. "the upstairs neighbor in a hoodie"}. Show only the situation and relationship.
Plain newsprint background #ECE2D4 with a few simple props, {4:3 | 16:9} landscape.
AVOID (add): injuries, weapons, blood, bodies, a crime in progress, anything hinting at the verdict, mocking the victim.
```

- 역할은 사건마다 **번갈아** 정한다(이번 사건에서 내로가 가해자 역할이면 다음 사건은 남불). 정한 배정을 이 표 비고나 recipe에 남긴다.

### F. 시트

**F1 연기 시트** — 첨부 해당 캐릭터 시트 · 스탠딩

```
Use the attached reference images as the exact model of {Naero | Nambul | Law (Scale) | Law (Gavel) | Law (Book)}. Create an acting sheet in the same art style.
SHEET — eight full-body poses in two rows: idle, guide (pointing to one side), read (holding a document), think (hand on chin), react (surprised), cheer (both fists up), deliver (holding out an envelope, Law only) {or duo-ready pose for Naero/Nambul}. Calm, friendly acting; never angry.
SCENE — plain newsprint background #ECE2D4, panels separated by space only, no labels, landscape 16:9.
```

- 로봉(10/14 추가)은 `standing_law-gavel.png`와 A5 로봉 시트(나오면)를 첨부한다. 법봉 머리는 수평이 기본이고 휘두르지 않는다(`AVOID (add): swinging the gavel`). 로 셋의 감정은 머리 물체로만(아키텍처 3-2 감정 코드).

**F2 역할 의상 시트** — 보류(구분 방법이 정해진 뒤). 형식은 F1과 같고 SHEET 줄만 바꾼다.

```
SHEET — {Naero | Nambul} in four role outfits, front and three-quarter views: judge (the same navy robe as Law), prosecutor, defense lawyer, and a case-role outfit (plain shirt and vest). Prosecutor and defense outfits have the same shape and the same brightness; they differ only by {front band color | a held prop}. The face, fur/flame and colors never change.
```

**F3 데포르메 대표 시트** — 첨부 내로 · 남불 시트. 프롬프트 모음 6장의 DEFORMATION 블록(란마 6-5 · 볼따구 6-8 · 카툰 6-11)을 9장 블록과 함께 쓴다.

```
Use the attached reference sheet as the model of {Naero | Nambul}'s colors and identity. Redraw this ONE character in the {deformation} deformation below, keeping {the bandana, yellow eyes and navy fur | the three-color flame head, visible pupils and no nose}. Do not draw the other character, and use only this character's colors in the swatches.
{프롬프트 모음 6장의 해당 DEFORMATION 블록}
SHEET — one front full body, a turnaround of exactly four views (front, three-quarter, side, back), six expressions, color swatches. Plain newsprint background #ECE2D4, landscape 16:9.
```

- 10/14부터 F3도 **한 장에 한 캐릭터**로 뽑는다(지금 6장은 이미 캐릭터별). 첨부는 A5 1인 시트가 나오면 그것으로 바꾼다.

**G6 로 데포르메 시트 (후순위)** — F3과 같은 형식. 첨부 해당 로의 A5 1인 시트 · 스탠딩. 프롬프트 모음 6장의 **로용** DEFORMATION 블록(6-5 · 6-8 · 6-11)을 쓰고, STYLE은 `editorial`(9-3)로 바꾼다. 로 셋을 같은 데포르메로 함께 만든다. 법전 머리는 로용 블록에 없으므로 `the head object is a plump, thick closed book floating above the collar with a clear gap` 한 줄을 더한다.
