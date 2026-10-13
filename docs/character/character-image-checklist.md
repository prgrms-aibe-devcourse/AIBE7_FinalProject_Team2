# **캐릭터 이미지 제작 목록**

**작성 이력**

| 버전 | 날짜 | 내용 |
| --- | --- | --- |
| v0.1 | 2026-10-14 | 초안 (COMMON-28, WBS 7.6.8 관련) — 만들어야 할 캐릭터 이미지를 종류 · 우선순위 · 수량 · 형식 · 상태로 정리 |
| v0.2 | 2026-10-14 | 항목별 **예시 프롬프트**(5장, 프롬프트 모음 9장 v2 기준 · 참조 이미지 첨부형) 추가 |
| v0.3 | 2026-10-14 | A1 · A2 · A3 1차 생성 결과 · 후속 보완점 기록 |
| v0.4 | 2026-10-14 | 로 스탠딩 빈손(A4 추가, 봉투는 `deliver`만), B1 `idle`은 A4 변환만 |

> 형식 · 이름 규칙 · 검수 기준은 [캐릭터 에셋 아키텍처](character-asset-architecture.md)가 기준이다(시트 2-2 · 장면 · 감정 코드 3-2 · 상황 4-1 · 크기 6-5 · 검수 7-2). 이 문서는 **무엇을 몇 장 만들고 어디까지 했는지**만 적는다.
> 캐릭터 설정은 [캐릭터 프로필 · 세계관](character-profile-worldview.md), 성향 이미지 번호는 [성향 이미지 번호표](personality-image-numbers.md)가 기준이다.
> 프롬프트는 [이미지 생성 프롬프트 모음](character-image-prompts.md) 9장(v2)을 쓴다. 항목별 예시는 이 문서 5장.
> 원본 PNG는 레포 밖 `~/Documents/NaeLawNamBul/character/`(시트는 `sheets/`, 스탠딩은 `standing/`)에 둔다. 웹용 webp만 `frontend/public/images/characters/`에 넣는다.

**상태 표기**: ⬜ 대기 · 🟨 생성 중 · 🟦 검수 중 · ✅ 완료

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

---

## 2. 목록

### A. 기준 (먼저)

| # | 이미지 | 수량 | 형식 | 비고 | 상태 |
| --- | --- | --- | --- | --- | --- |
| A0 | 내로 · 남불 기준 시트, 로(저울 · 법봉) 기준 시트, 스탠딩 4장 | 6 | 시트 · 투명 PNG | `character-sheet_*` · `standing_*`(아키텍처 2-1) | ✅ |
| A1 | 로(법전) 기준 시트 | 1 | 시트 | 저울 · 법봉 시트와 같은 칸(정면 · 3/4 · 측면 · 뒷면 · 자세 6종 · 색 견본). 머리는 **목 · 기둥 없이 옷깃 위 공중에 떠 있는** 두꺼운 법전, 기본은 표지 정면. 손에는 법전을 들지 않는다. **10/14 생성** `character-sheet_law-book.png` — 후속: 아쉬움 포즈 칸에서 책이 어깨에 얹힘(옷깃에 닿음), 옷깃 안 마네킹 목, 턴어라운드 · 포즈 줄 중복 | 🟦 검수 중 |
| A2 | 로(법전) 스탠딩 | 1 | 투명 PNG | (1차는 봉투를 든 자세로 생성 → A4에서 빈손으로 다시). 파일 `standing_law-book.png`. **10/14 생성**(971 × 1619) — 떠 있는 법전 · 표지 정면 OK. 후속: 옷깃 안 회갈색 마네킹 목(`#8B7457`) 정리, 구두가 갈색 | 🟦 검수 중 |
| A3 | 키 비교 시트 | 1 | 시트 | 내로 120 · 남불 120 · 저울 230 · 법봉 190 · 법전 210, 같은 바닥선. 기존 내로 · 남불 시트의 키 칸(남불 1.5배)은 쓰지 않는다. **10/14 v1** `character-sheet_height_v1.png` — 로 셋 상대 비율 · 법봉 떡대 OK. 후속: 로가 내로 · 남불 대비 약 8 ~ 10% 큼(저울 약 2.07배 · 목표 1.92), 법전이 옷깃에 얹힘(떠 있지 않음), 숫자 라벨이 이미지에 들어감 | 🟨 재생성 |
| A4 | 로 스탠딩 **빈손 버전** (저울 · 법봉 · 법전) | 3 | 투명 PNG | 10/14 결정: 스탠딩에서 봉투 제외. 지금 `standing_law-*.png`를 첨부하고 봉투만 뺀다(프롬프트 모음 9-12). 완성되면 기존 파일은 `_envelope`를 붙여 보관하고 새 파일이 `standing_law-*.png` | ⬜ |

- A1 · A2가 끝나면 아키텍처 2-4(색) · 2-5(프로필) · 5-1(IDENTITY)에 법전을 추가한다. 10/14 A2 실측: 표지 `#722C30` ~ `#621E25`(제안 `#5E1F2B`보다 밝은 와인색), 황동 모서리 `#CFAA75` 근처.
- A3 다시 뽑을 때 추가 문장: `Law (Book) floats above the collar with a clear gap; it never rests on the shoulders.` · `Make Naero and Nambul about 8% larger so the ratios match exactly.` · 숫자 라벨 없이 뽑고 나중에 얹는다.

### B. 우선 컷 (화면에 바로 쓰는 것)

| # | 캐릭터 | 컷 | 수량 | 쓰는 곳 | 상태 |
| --- | --- | --- | --- | --- | --- |
| B1 | 로(저울) | `idle-neutral`(**A4 빈손 스탠딩을 webp로 변환만**) · `deliver-neutral`(새로 생성, 봉투) | 2 | S-01 메인 · S-02 신문 지면 · 로딩 끝 전달 | ⬜ |
| B2 | 내로 (`mascot`) | `guide-neutral` · `guide-happy` · `react-surprised` · `react-happy` · `react-sorry` | 5 | 안내 · 리액션 | ⬜ |
| B3 | 남불 (`mascot`) | B2와 같은 5종 | 5 | 안내 · 리액션 | ⬜ |
| B4 | 내로 · 남불 (`cheek`) | `idle-sorry` · `idle-thinking` | 2 ~ 4 | 로딩 · 빈 상태 · 오류 | ⬜ |
| B5 | 내로 · 남불 (`ppg` 아이콘) | `face-{neutral · happy · surprised · thinking · sorry · cheer}` | 12 | 토스트 · 완료 피드백 | ⬜ |

### C. 판결 결과 화면 (S-07 · S-09)

| # | 이미지 | 수량 | 비고 | 상태 |
| --- | --- | --- | --- | --- |
| C1 | 로 3인 합의 컷 | 1 ~ 2 | 셋이 나란히(`idle`) · 고개를 맞댐(`think`). 같은 크기 · 높이, 서열 없음. 이견(2 : 1)처럼 보이는 연출 금지(프로필 · 세계관 5-3) | ⬜ |
| C2 | 로(법봉) · 로(법전) `deliver-neutral` | 2 | 발표자 컷. 저울은 B1 | ⬜ |
| C3 | S-09 비교 `trio-thinking` | 1 | 내로 왼쪽 · 로(저울) 가운데 · 남불 오른쪽. 셋 중 하나를 더 크게 · 앞에 두지 않는다 | ⬜ |

### D. 성향 테스트 (S-15 · S-16)

| # | 이미지 | 수량 | 비고 | 상태 |
| --- | --- | --- | --- | --- |
| D1 | 성향 결과 `01.webp` ~ `16.webp` | 16 | 장면 컷 4:5, `mascot`. 내로 8 · 남불 8. 밈 장면은 [번호표](personality-image-numbers.md). 화난 표정 금지, 4글자 코드 · 글자 넣지 않음 | ⬜ |

### E. 사건 재연 (S-02 · 사건 디테일)

| # | 이미지 | 수량 | 비고 | 상태 |
| --- | --- | --- | --- | --- |
| E1 | 사건별 재연 컷 | 사건 수 × 3 (`headline` · `sub-1` · `detail-1`) | 내로 · 남불이 **피해자 · 가해자를 번갈아** 맡는다(프로필 · 세계관 5-2). 흉기 · 상처 · 범행 순간 · 시신은 그리지 않는다(아키텍처 4-3). 누가 어떤 역할을 맡았는지 기록한다 | ⬜ |

### F. 시트 (P1, 컷을 만들기 전 참고용)

| # | 시트 | 수량 | 비고 | 상태 |
| --- | --- | --- | --- | --- |
| F1 | 연기 시트 | 4 (내로 · 남불 · 저울 · 법전) | 장면 코드별 전신 자세(3-2). 법봉은 기존 시트의 자세 6종으로 대신한다 | ⬜ |
| F2 | 역할 의상 시트 | 2 (내로 · 남불) | 검사 · 변호인 · 판사 · 사건 역할. **구분 방법(띠 색 vs 소품)이 정해진 뒤**(아키텍처 7-3 #2) | ⬜ 보류 |
| F3 | 데포르메 대표 시트 | 6 | `ranma` · `cheek` · `ppg` × 내로 · 남불 | ⬜ |

### G. 후순위

| # | 이미지 | 비고 |
| --- | --- | --- |
| G1 | 나머지 데포르메 8종 시트 | 아키텍처 3-1 |
| G2 | 소품 시트 | 3-3 카탈로그를 물건만 따로 |
| G3 | 스타일 변형 시트 | `riso` · `flat25` 등(3-6) |
| G4 | 꾸미기 레이어 | `layers/`, 저장 방식이 정해진 뒤 |
| G5 | 이벤트 · 굿즈용 | IP 확장(프로필 · 세계관 6장) |

---

## 3. 순서 · 합계

**추천 순서**: A1 → A2 → A3 → B1 → B2 · B3 → D1 → C → B4 · B5 → F → E(사건이 정해지는 대로)

| 묶음 | 수량 |
| --- | --- |
| A 기준(남은 것) | 3 |
| B 우선 컷 | 26 ~ 28 |
| C 판결 결과 | 4 ~ 5 |
| D 성향 | 16 |
| F 시트 | 12 |
| **합계 (A ~ D, F)** | **약 61 ~ 64** |
| E 재연 | 사건 수 × 3 (별도) |

---

## 4. 완료 처리

- 한 장을 만들면 아키텍처 7-2 체크리스트로 검수하고, 이 표의 상태를 ✅로 바꾼다.
- webp를 `frontend/public/images/characters/` 경로(아키텍처 6-4)에 넣는 것은 화면 연결 작업과 함께 한다.
- 프롬프트를 바꿔 다시 뽑은 경우 원본 폴더에 날짜를 붙여 남긴다(예: `standing_law-book_2026-10-14.png`).

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
- **A4 로 스탠딩 빈손 버전**: 프롬프트 모음 9-12(완성본). 첨부 해당 `standing_law-*.png`

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

**D1 성향 결과 `{번호}.webp`** — 첨부 해당 캐릭터 스탠딩. 밈 장면은 [번호표](personality-image-numbers.md)

```
Use the attached reference image as the exact model of {Naero | Nambul}: copy the face, colors, proportions and flat art style exactly.
SCENE — {밈 장면을 영어로: e.g. 01 "Naero standing straight with arms crossed, a thick rule binder beside them" / 16 "Naero wrapped in a blanket, holding a mug, smiling gently"}. Friendly and warm; the character is never angry or mocking.
A single character (props allowed), centered, full body visible, 4:5 vertical, 15% margin, flat solid green #00B140 background. No green on the character or props.
AVOID (add): text on props, the letters of personality codes.
```

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
Use the attached reference images as the exact model of {Naero | Nambul | Law (Scale) | Law (Book)}. Create an acting sheet in the same art style.
SHEET — eight full-body poses in two rows: idle, guide (pointing to one side), read (holding a document), think (hand on chin), react (surprised), cheer (both fists up), deliver (holding out an envelope, Law only) {or duo-ready pose for Naero/Nambul}. Calm, friendly acting; never angry.
SCENE — plain newsprint background #ECE2D4, panels separated by space only, no labels, landscape 16:9.
```

**F2 역할 의상 시트** — 보류(구분 방법이 정해진 뒤). 형식은 F1과 같고 SHEET 줄만 바꾼다.

```
SHEET — {Naero | Nambul} in four role outfits, front and three-quarter views: judge (the same navy robe as Law), prosecutor, defense lawyer, and a case-role outfit (plain shirt and vest). Prosecutor and defense outfits have the same shape and the same brightness; they differ only by {front band color | a held prop}. The face, fur/flame and colors never change.
```

**F3 데포르메 대표 시트** — 첨부 내로 · 남불 시트. 프롬프트 모음 6장의 DEFORMATION 블록(란마 6-5 · 볼따구 6-8 · 카툰 6-11)을 9장 블록과 함께 쓴다.

```
Use the attached reference sheet as the model of Naero and Nambul's colors and identity. Redraw them in the {deformation} deformation below, keeping the bandana, yellow eyes, navy fur, three-color flame head, visible pupils and no nose.
{프롬프트 모음 6장의 해당 DEFORMATION 블록}
SHEET — the same layout as the reference sheet: front full bodies, a turnaround, six expressions each, color swatches. Plain newsprint background #ECE2D4, landscape 16:9.
```
