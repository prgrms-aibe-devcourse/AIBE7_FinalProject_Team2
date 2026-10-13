# 내로 · 남불 · 로 이미지 생성 프롬프트 모음

**작성 이력**

| 버전 | 날짜 | 내용 |
| --- | --- | --- |
| v1 | 2026-10-14 | 지금까지 만든 캐릭터 시트 · 스탠딩 · 데포르메 프롬프트를 한 문서로 정리 |
| v1.1 | 2026-10-14 | 레포 문서로 옮김(COMMON-28). 확정 이미지와 다른 점(8장) 추가 |
| v2 | 2026-10-14 | **신규 프롬프트 v2(9장)** — 확정 설정 기준 블록(STYLE · DEFORM · IDENTITY 5종 · POSE · SCENE · AVOID), 법전 로 시트 · 스탠딩 · 키 비교 완성 프롬프트. 1 ~ 7장 원문은 그대로 둠 |
| v2.1 | 2026-10-14 | 로 키 · 체형 반영(저울 230 · 법봉 190 · 법전 210) |
| v2.2 | 2026-10-14 | 로(법전): **목 · 기둥 없이 공중에 떠 있는 법전**, 기본은 표지 정면 · 자유 회전(9-4 · 9-5 · 9-6 · 9-8 · 9-9 · 9-10) |

> 기준 결정: `character-main-handover.md` v2의 10/13 변경 절 (기본 = 마스코트형 약 3.5등신 · 란마풍 등은 변형 · 모든 캐릭터 무성 · 소품 제한 해제 · 화난 표정 금지)
> 이 문서는 **복사해서 바로 쓰는 프롬프트 모음**이다. 설계 배경은 `character-law-ai-judge-plan.md` v0.3을 본다(`Claude outputs/`, 커밋 안 함).
> **이후 생성은 9장(신규 프롬프트 v2)을 기본으로 한다.** 1 ~ 7장은 원문 기록이다.
> **작업본이다.** 이미지 생성 결과가 프롬프트와 다르게 나오는 경우가 있다. **확정 이미지가 기준**이고([캐릭터 에셋 아키텍처](character-asset-architecture.md) 2-1 기준 이미지 · 2-4 색 · 2-5 키), 프롬프트와 다른 점은 8장에 모았다. 프롬프트 문구는 이미지를 뽑으며 계속 고친다(아키텍처 5-1 "작업본" 원칙). 캐릭터 설정은 프롬프트 문구와 따로 [캐릭터 프로필 · 세계관](character-profile-worldview.md)이 기준이다.
> 만들 이미지 목록은 [캐릭터 이미지 제작 목록](character-image-checklist.md)이다.

---

## 0. 문서 구성

| 장 | 내용 | 비율 · 배경 | 그림체 |
| --- | --- | --- | --- |
| 1 | 공통 규칙 · 사용 요령 | - | - |
| 2 | 캐릭터 시트 — 내로 · 남불 | 16:9 · 신문지색 `#ECE2D4` | 마스코트 (메인) |
| 3 | 캐릭터 시트 — 로 · 로(법봉) | 16:9 · 신문지색 `#ECE2D4` | 마스코트 (메인) |
| 4 | 스탠딩 메인 이미지 — 내로 · 남불 · 로 · 로(법봉) 4종 | 4:5 · 크로마 그린 `#00B140` | 마스코트 (메인) |
| 5 | 로 스탠딩 — 시안 스타일(5등신 · 부드러운 음영) 2종 | 4:5 · 크로마 그린 | 대체 시안 |
| 6 | 데포르메별 프롬프트 11종 (내로 · 남불용 + 로용) | 시트 프롬프트의 DEFORMATION 블록 교체용 | 변형 |
| 7 | 문제와 대응 · 검수 | - | - |
| 8 | 확정 이미지와 다른 점 | - | - |
| 9 | **신규 프롬프트 v2** (확정 설정 기준, 이후 기본) | 용도별 | 내로 · 남불 `editorial` × `mascot`, 로 `authority` × `adult` |

---

## 1. 공통 규칙 · 사용 요령

### 1-1. 구조

이미지 프롬프트는 같은 블록 순서로 짠다. 블록 단위로 갈아 끼우면 캐릭터 · 포즈 · 데포르메 조합을 늘릴 수 있다.

| 블록 | 역할 | 바꾸는 경우 |
| --- | --- | --- |
| ART STYLE | 그림체(외곽선 없음 · 셀 셰이딩 1단 · 종이 질감 · 팔레트) | 거의 고정 |
| DEFORMATION | 등신 · 형태 단순화 · 연기 | 데포르메 변형 (6장) |
| CHARACTER | 정체성(고정 설명) | 캐릭터 추가 · 역할 변경 |
| POSE / SHEET LAYOUT | 포즈 · 시트 구성 | 장면별 |
| SCENE / BACKGROUND | 배경 · 구도 · 여백 | 용도별 |
| AVOID | 금지 요소 | 거의 고정 |

### 1-2. 고정할 정체성 규칙

- 내로: 네이비 털, 노란 눈(큰 눈동자), 분홍 코, 입 안은 반다나 색, 빨간 반다나는 항상 보임
- 남불: 불꽃은 머리에만, 몸은 녹슨 적갈색, 코 없음, 눈동자 항상 보임, 얼굴 가면 없음
- 로: 얼굴 없음, 머리 오브젝트(저울 또는 법봉), 남색 법복 · 흰 장갑, 감정은 머리 오브젝트와 자세로만, 봉인 판결 봉투가 대표 소품
- 모두 무성(내로 · 남불은 어떤 성별 역할도 하는 배우). 로의 남성형 체형은 5장의 시안에서만 쓰는 스킨이다.

### 1-3. 생성 요령

- 시트는 신문지색 배경(16:9), 스탠딩은 **크로마 그린 `#00B140` 단색 배경**으로 뽑아 배경을 지우고 투명 PNG(2048 × 2560)와 웹용 WebP(800 × 1000)로 쓴다. 캐릭터에 초록을 쓰지 않는다.
- 기존 시트나 메인 이미지를 **참조 이미지로 같이 첨부**하면 일관성이 크게 좋아진다. 첨부 시 맨 위에 한 줄을 붙인다.

```
Match the face, colors and design of the attached reference, but follow the proportions and rules described below.
```

- 프롬프트에는 작가 · 작품 이름을 넣지 않는다(특징만 풀어 쓴다).
- 시트 안의 라벨 · 글자는 생성 후 직접 얹는다.

---

## 2. 캐릭터 시트 — 내로 · 남불 (마스코트)

`2D 평면 에디토리얼` 그림체 · 약 3.5등신 마스코트 · 16:9 · 신문지색 배경.


```
Character sheet for the mascots "Naero" and "Nambul" of the Korean legal-experience service "Nae-Law-Nam-Bul".

ART STYLE — warm, calm 2D flat editorial illustration (keep this rendering for every deformation):
- No black outlines and no line art. Shapes are separated only by flat color areas; neighboring shapes meet directly color-to-color with no border or darker rim.
- Facial features are filled shapes, not strokes.
- Shading: one level of cel shading per surface (one flat shadow shape on the lower-right side) and very subtle gradients on large areas.
- Lighting: warm light from the upper left.
- Texture: fine, barely visible paper grain, matte finish.
- Shadows: none on the sheet. No ground shadows, no cast shadows, no drop shadows.
- Mood: calm, warm, slightly vintage, like illustrated stationery on a judge's desk.
- Palette (muted, from the service's main screen): paper white #FDFCFA, newsprint #ECE2D4, kraft beige #D6A468, warm wood #A8652F, deep navy #1C2B4F (dark #14213F, light #1F3563), ink #151A24, sticky-note yellow #F3E3B3, warm glow #FFD696, brick red #B3261E, orange #D4692A, rust #9C3A1D.

DEFORMATION — cute but well-proportioned mascot, about 3.5 heads tall:
- Head height (the face area, flame and ears excluded) is about 28% of the total body height. The head is round but not oversized.
- Slim torso with a gently narrower waist; legs are long and slender, about as long as the torso; arms are slim. Small hands and small feet. NOT stubby, NOT chubby, NOT potbellied.
- Feet are close together or in a relaxed narrow stance.
- Large round eyes with one big glossy highlight and one small second highlight; small simple mouths.
- Nambul is slightly taller than Naero because of the flame.
- Whatever the deformation, the identity rules below never change.

CHARACTER 1 — "Naero" (genderless cat actor, can play any role):
Deep navy fur (base #1C2B4F, shadow #14213F, highlight #1F3563), fur-tuft shapes on the cheeks and head. Big warm yellow eyes #FFD696 with solid dark pupils #151A24 and white highlights #FDFCFA. Small dusty pink nose #E2A08F. When the mouth is open, the inside of the mouth is brick red #B3261E, the same color as the bandana. Pointed ears with muted plum inner ears #5B3A40. Long curled tail. A brick-red bandana (#B3261E, shadow #9C3A1D) tied around the neck as a triangle with a small side knot; it is always visible. Naero stays clearly a black-navy cat mascot.

CHARACTER 2 — "Nambul" (genderless fire sprite actor, can play any role):
The flame exists ONLY on the head: the whole head is a flame of three stacked flat color layers — brick red outer flame #B3261E, orange middle #D4692A, warm yellow inner flame #FFD696 around the face. Below the head there is no fire at all: a plain body and limbs in rust red (#9C3A1D, shadow #7A2E17). No flames on the shoulders, arms, hands, back, hips, legs or feet, no flame-shaped tail, no sparks or embers. The face has only eyes and a mouth: warm-white eyeballs #FDFCFA, and inside EACH eyeball there is ALWAYS a clearly visible solid dark pupil #151A24 with a tiny white highlight, in every view and every open-eye expression. No nose. There is NO separate face mask or egg-shaped face area: the eyes and mouth sit directly on the glowing center of the flame.

SHEET LAYOUT:
- Top: both characters standing side by side, Naero on the left, Nambul on the right.
- Middle: a turnaround for each character — front, three-quarter, side and back views. In the back view, Nambul's flame stays on the head and ends at the neck.
- Bottom: six facial expressions for each character — neutral, happy, surprised, sulky, sad, sleepy.
- Side panel: height comparison and flat round color swatches of the main colors.
- Plain newsprint background #ECE2D4, clean grid layout, panels separated by space only (no frame lines), landscape 16:9.

AVOID: outlines, line art, contour lines, inner lines, darker edge rims, panel borders, drop shadows, ground shadows, 3D render, CGI, glossy reflections, photo realism, stubby legs, chubby body, oversized head, a nose on Nambul, blank eyes without pupils on Nambul, a separate face mask on Nambul, flames anywhere on Nambul except the head, flame tail, sparks, embers, sexualized or revealing depiction, text, letters, numbers, logos, angry or hostile faces, extra characters.
```

- 시트는 가로가 빡빡하면 `landscape 16:9`를 `landscape 21:9`로 바꾸거나, 표정 줄을 별도 시트로 나눈다.
- 시트 결과가 짧뚱하게 나오면 7장의 "문제와 대응"을 본다.

---

## 3. 캐릭터 시트 — 로 · 로(법봉) (마스코트)

내로 · 남불과 같은 마스코트 가족으로 보이도록 **둥글고 통통한 3 ~ 3.5등신**, 소재(청동 · 나무 · 천)는 모두 **평면 색 면**으로 그린다. 로는 얼굴이 없어 표정 대신 **감정 포즈**를 쓴다.


```
Character sheet for two versions of the mascot "Law", the AI judge of the Korean legal-experience service "Nae-Law-Nam-Bul". Both versions are the same character with different head objects, so they are drawn as a pair. They must look like they belong to the same mascot family as the cat "Naero" and the fire sprite "Nambul": chunky, round, simple, friendly.

ART STYLE — warm, calm 2D flat editorial illustration (keep this rendering for every deformation):
- No black outlines and no line art. Shapes are separated only by flat color areas; neighboring shapes meet directly color-to-color with no border or darker rim.
- Shading: one level of cel shading per surface (one flat shadow shape on the lower-right side) and at most a very subtle gradient on large areas.
- Lighting: warm light from the upper left.
- Texture: fine, barely visible paper grain, matte finish.
- Shadows: none on the sheet. No ground shadows, no cast shadows, no drop shadows.
- Mood: calm, warm, slightly vintage, like illustrated stationery on a judge's desk.
- Palette (muted, from the service's main screen): paper white `#FDFCFA`, newsprint `#ECE2D4`, kraft beige `#D6A468`, warm wood `#A8652F`, deep navy `#1C2B4F` (dark `#14213F`, light `#1F3563`), ink `#151A24`, warm glow `#FFD696`, matte bronze `#BA854B` (dark `#86581F`, light `#E6D2B5`), dark mahogany `#5A2418` (shadow `#3A150E`, mid-light `#7A3322`, highlight `#C47A52`, bright gloss `#E8B896`), warm white `#F7F3EE`.

DEFORMATION — chunky, round mascot, about 3 to 3.5 heads tall (the same family as Naero and Nambul):
- The head object is large and chunky: about 30 to 33% of the total body height, and clearly wider than the shoulders. The body is short, soft and rounded, with a slightly narrower waist. NOT tall, NOT slim, NOT realistic human proportions.
- Limbs are thick, rounded and short, like soft tubes. Hands are small rounded mitten shapes in white gloves with no individual fingers drawn. Feet are small rounded ink-black shoes, close together in a relaxed narrow stance.
- The robe is simplified into one soft, rounded bell shape: a plain navy shape with a deep V neckline, two equal-width front bands drawn as flat slightly darker navy shapes, short wide bell sleeves that stop above the wrists, and a closed hem that stops just above the shoes. NO fold lines, NO pleats, NO fabric wrinkles, NO realistic cloth shading. Only one flat shadow shape on the lower-right side.
- Everything is built from big, simple, rounded flat shapes with very few details, like the shapes of Naero and Nambul. Acting is cute and readable: comedic, energetic poses, big reactions, sweat-drop and surprise marks beside the head. Both versions share exactly the same body and height.
- Whatever the deformation, the identity rules below never change.

MATERIAL RULES — keep everything flat, never realistic:
- Bronze, wood and fabric are all drawn as flat color fills. Each material gets only: a base color, one flat shadow shape, and one or two small crisp flat highlight shapes. No realistic metal or wood texture, no reflections, no gradients, no glossy rendering, no ambient occlusion.

CHARACTER 1 — "Law (Scale)" (genderless AI judge):
No face, no eyes, no nose, no mouth, no neck, no human head. The head is a chunky matte-bronze balance scale sitting directly on the collar: a thick rounded central pillar, a thick gently curved beam, and two wide shallow round pans, each hanging from two short thick chains (simple, not delicate). Emotion only through the pans and posture: level = calm, slightly raised = happy, swinging = surprised, one pan lower = thinking, drooping chains + slumped posture = sorry, pans drooping and tilted slightly = sleepy.
Outfit: navy judge robe `#1C2B4F`, warm-white collar and white gloves `#F7F3EE`, ink-black shoes `#151A24`.

CHARACTER 2 — "Law (Gavel)" (genderless AI judge, same outfit):
No face, no eyes, no nose, no mouth, no human head. The head is a chunky, simplified judge's gavel in dark mahogany, with the mallet head on top and a short, thick handle working as a neck going into the collar:
- Mallet head: a big, rounded, horizontal barrel with a smooth curvy profile: a thick center barrel, flared collars at both ends, one or two simple rings, and rounded dome end caps. Wide and heavy like a toy, clearly wider than the shoulders. Nothing sticks out of the top or sides.
- Color: flat mahogany `#5A2418` with one flat shadow shape `#3A150E` on the lower right, one mid-light shape `#7A3322` on the upper left, and one or two small crisp flat highlight shapes (`#C47A52`, and a thin `#E8B896`). No bronze, no metal.
- Handle-neck: the same mahogany wood, short, thick and gently tapered, attached to the center of the underside of the mallet head and going straight into the collar.
- Emotion only through the mallet head's tilt, the handle-neck's lean and posture: level = calm, small lift = happy, quick tilt = surprised, tilted to one side = thinking, drooped forward + slumped posture = sorry, slowly nodding down = sleepy. The gavel is never swung or struck.
Outfit: exactly the same as Law (Scale).

Signature prop (both): a sealed verdict envelope in kraft `#D6A468`, drawn as a simple flat rounded shape with a small seal.

SHEET LAYOUT:
- Top: both characters standing side by side, both hands on hips like Naero and Nambul, Law (Scale) on the left, Law (Gavel) on the right.
- Middle: a turnaround for each character — front, three-quarter, side and back views, in the same row style as the Naero and Nambul sheet.
- Bottom: six emotion poses for each character — calm, happy, surprised, thinking, sorry, sleepy.
- Side panel: height comparison and flat round color swatches of the main colors.
- Plain newsprint background `#ECE2D4`, clean grid layout, panels separated by space only (no frame lines), landscape 16:9.

AVOID: faces, eyes, noses, mouths, necks on Law (Scale), human heads, tall or slim proportions, realistic human proportions, fingers, robe folds, pleats, wrinkles, realistic cloth, realistic metal or wood texture, glossy reflections, soft glow, gradients on the head objects, 3D render, CGI, clay, plastic, outlines, line art, contour lines, inner lines, darker edge rims, panel borders, drop shadows, ground shadows, a sound block or base under the gavel, delicate thin chains, thin lines, anything sticking out of the top of the mallet head, swinging or striking the gavel, sexualized or revealing depiction, text, letters, numbers, logos, angry or hostile poses, extra characters, Naero or Nambul.
```

- 로가 키 크고 가늘게 나오면 맨 끝에 붙인다.

```
Match the proportions of the attached reference sheet of Naero and Nambul: the same head-to-body ratio, the same thick rounded limbs, the same simple flat shapes.
```

- 비율 조절: `about 3 to 3.5 heads`를 `about 3 heads`(더 짧게) 또는 `about 3.5 heads`(더 길게)로 바꾼다.
- 법봉 광택이 3D처럼 나오면 맨 끝에 붙인다.

```
Draw the gavel's gloss only as flat, hard-edged highlight shapes on a flat 2D vector-style illustration. No photographic texture, no realistic reflection, no soft gradient glow.
```

---

## 4. 스탠딩 메인 이미지 — 4종 (마스코트)

공통: 4:5 세로 · 전신 · 중앙 · 여백 15% · 크로마 그린 `#00B140` 배경 · 손을 허리에 올린 기본 포즈. 4종을 같은 설정으로 뽑아 나란히 놓으면 한 세트로 보인다.

### 4-1. 내로


```
Single full-body mascot character illustration of "Naero".

ART STYLE — warm, calm 2D flat editorial illustration:
- No black outlines and no line art. Shapes are separated only by flat color areas; neighboring shapes meet directly color-to-color with no border or darker rim.
- Facial features are filled shapes, not strokes.
- Shading: one flat shadow shape per surface on the lower-right side, plus very subtle soft gradients on large areas only.
- Lighting: warm light from the upper left. Matte finish with a fine, barely visible paper grain on the character only.
- Shadows: none. No ground shadow, no cast shadow, no drop shadow, no glow.
- Mood: calm, warm, slightly vintage, like illustrated stationery on a judge's desk.
- Palette: deep navy #1C2B4F (dark #14213F, light #1F3563), ink #151A24, warm glow #FFD696, brick red #B3261E (shadow #9C3A1D), dusty pink #E2A08F, plum #5B3A40, paper white #FDFCFA.

DEFORMATION — cute but well-proportioned mascot, about 3.5 heads tall:
- Head height is about 28% of the total body height (ears excluded). The head is round but not oversized.
- Slim torso with a gently narrower waist; legs are long and slender, about as long as the torso; arms are slim. Small hands and small feet. NOT stubby, NOT chubby, NOT potbellied.
- Feet are close together or in a relaxed narrow stance.
- Large round eyes with one big glossy highlight and one small second highlight; small simple mouth.

CHARACTER — "Naero", a genderless cat actor (can play any role):
Deep navy fur (base #1C2B4F, shadow #14213F, highlight #1F3563) with small fur-tuft shapes on the cheeks and head. Big round warm yellow eyes #FFD696 with large solid dark pupils #151A24 and white highlights #FDFCFA. Small dusty pink nose #E2A08F. When the mouth is open, the inside of the mouth is brick red #B3261E, the same color as the bandana. Pointed ears with muted plum inner ears #5B3A40. Long thin curled tail. A brick-red bandana (#B3261E, shadow #9C3A1D) tied around the neck as a triangle with a small side knot; it is always visible. Keep the head compact and the body slim: the bandana is small and does not cover the chest. Stays clearly a navy cat mascot.

POSE & EXPRESSION: standing, front to slight three-quarter view, both hands on hips, chest slightly out, tail curled behind; a bright open-mouth smile, looking at the viewer.

SCENE: single character only, centered, full body from head to feet fully visible, shot from a slight distance, 4:5 vertical composition with 15% empty margin on every side. Background: perfectly flat uniform solid green #00B140, no gradient, no texture, no floor, no shadow, no props in the background. Do not use green anywhere on the character.

AVOID: outlines, line art, contour lines, inner lines, darker edge rims, drop shadows, ground shadows, 3D render, CGI, glossy plastic reflections, photo realism, stubby legs, chubby body, oversized head, text, letters, numbers, logos, angry or hostile faces, cropped limbs, extra characters.
```

### 4-2. 남불

```
Single full-body mascot character illustration of "Nambul".

ART STYLE — warm, calm 2D flat editorial illustration:
- No black outlines and no line art. Shapes are separated only by flat color areas; neighboring shapes meet directly color-to-color with no border or darker rim.
- Facial features are filled shapes, not strokes.
- Shading: one flat shadow shape per surface on the lower-right side, plus very subtle soft gradients on large areas only (such as the flame).
- Lighting: warm light from the upper left. Matte finish with a fine, barely visible paper grain on the character only.
- Shadows: none. No ground shadow, no cast shadow, no drop shadow, no glow.
- Mood: calm, warm, slightly vintage, like illustrated stationery on a judge's desk.
- Palette: brick red #B3261E, orange #D4692A, warm glow yellow #FFD696, rust #9C3A1D (shadow #7A2E17), ink #151A24, paper white #FDFCFA.

DEFORMATION — cute but well-proportioned mascot, about 3.5 heads tall:
- Head height (the face area, flame excluded) is about 28% of the total body height. The head is round but not oversized.
- Slim torso with a gently narrower waist; legs are long and slender, about as long as the torso; arms are slim. Small hands and small feet. NOT stubby, NOT chubby, NOT potbellied.
- Feet are close together or in a relaxed narrow stance.
- Large eyes with one big glossy highlight and one small second highlight; small simple mouth.

CHARACTER — "Nambul", a genderless fire sprite actor (can play any role), slightly taller than Naero because of the flame:
The head IS the flame. The flame is one plump, bouncy teardrop shape with 3 to 4 wavy rounded flame tongues on top and smaller wavy tips on the sides, like a retro anime fire. Colors flow smoothly (very soft gradient) from brick red #B3261E at the outer edge, to orange #D4692A, to warm glowing yellow #FFD696 at the center, where the face sits. There is NO separate face shape, NO mask, NO egg-shaped face area, NO visible boundary around the face: the eyes and mouth are placed directly on the glowing center of the flame.
Eyes: large, tall oval, set close together, warm-white eyeballs #FDFCFA with large solid dark pupils #151A24 and two white highlights; the pupils are ALWAYS clearly visible. Small smiling mouth with brick red inside #B3261E. NO nose.
Below the head: a slim plain body and slender limbs in rust red (#9C3A1D, shadow #7A2E17), a smooth simple silhouette with NO belly patch, NO chest marking, no clothing details. No fire below the head: no flames on the shoulders, arms, hands, back, hips, legs or feet; no flame tail; no sparks; no embers.

POSE & EXPRESSION: standing, front to slight three-quarter view, both hands on hips, a cheerful smile, looking at the viewer.

SCENE: single character only, centered, full body from head to feet fully visible, shot from a slight distance, 4:5 vertical composition with 15% empty margin on every side. Background: perfectly flat uniform solid green #00B140, no gradient, no texture, no floor, no shadow, no props in the background. Do not use green anywhere on the character.

AVOID: outlines, line art, contour lines, inner lines, darker edge rims, drop shadows, ground shadows, glow or light halo around the flame, 3D render, CGI, glossy plastic reflections, a nose, blank eyes without pupils, a separate face mask or egg-shaped face area, belly patch, flames anywhere except the head, flame tail, sparks, embers, stubby legs, chubby body, oversized head, text, letters, numbers, logos, angry or hostile faces, cropped limbs, extra characters.
```

### 4-3. 로 (저울)

```
Single full-body mascot character illustration of "Law".

ART STYLE — warm, calm 2D flat editorial illustration:
- No black outlines and no line art. Shapes are separated only by flat color areas; neighboring shapes meet directly color-to-color with no border or darker rim.
- Shading: one flat shadow shape per surface on the lower-right side, plus at most a very subtle soft gradient on large areas.
- Lighting: warm light from the upper left. Matte finish with a fine, barely visible paper grain on the character only.
- Shadows: none. No ground shadow, no cast shadow, no drop shadow, no glow.
- Mood: calm, warm, slightly vintage, like illustrated stationery on a judge's desk.
- Palette: navy #1C2B4F (dark #14213F, light #1F3563), matte bronze #BA854B (dark #86581F, light #E6D2B5), warm white #F7F3EE, ink #151A24, kraft #D6A468, paper white #FDFCFA.

DEFORMATION — chunky, round mascot, about 3 to 3.5 heads tall (the same family as Naero and Nambul):
- The head object is large and chunky: about 30 to 33% of the total body height, and clearly wider than the shoulders. The body is short, soft and rounded, with a slightly narrower waist. NOT tall, NOT slim, NOT realistic human proportions.
- Limbs are thick, rounded and short, like soft tubes. Hands are small rounded mitten shapes in white gloves with no individual fingers drawn. Feet are small rounded ink-black shoes, close together in a relaxed narrow stance.
- The robe is one soft, rounded bell shape: plain navy with a deep V neckline, two equal-width front bands drawn as flat slightly darker navy shapes, short wide bell sleeves that stop above the wrists, and a closed hem that stops just above the shoes. NO fold lines, NO pleats, NO wrinkles. Only one flat shadow shape on the lower-right side.
- Everything is built from big, simple, rounded flat shapes with very few details. Bronze, wood and fabric are flat color fills with one flat shadow shape and one or two small crisp flat highlight shapes: no realistic texture, no reflections, no glossy rendering.

CHARACTER — "Law", the genderless AI judge:
No face, no eyes, no nose, no mouth, no neck, no human head. The head is a chunky matte-bronze balance scale sitting directly on the collar: a thick rounded central pillar, a thick gently curved beam, and two wide shallow round pans, each hanging from two short thick chains. The scale is level (calm). Outfit: navy judge robe #1C2B4F, warm-white collar and white gloves #F7F3EE, ink-black shoes #151A24.

POSE & EXPRESSION: standing, front to slight three-quarter view, both hands on hips, chest slightly out, scale level and calm.

SCENE: single character only, centered, full body from head to feet fully visible, shot from a slight distance, 4:5 vertical composition with 15% empty margin on every side. Background: perfectly flat uniform solid green #00B140, no gradient, no texture, no floor, no shadow, no props in the background. Do not use green anywhere on the character.

AVOID: faces, eyes, noses, mouths, necks, human heads, outlines, line art, contour lines, inner lines, darker edge rims, drop shadows, ground shadows, glow, 3D render, CGI, clay, glossy plastic or mirror-like metal reflections, photo realism, tall or slim proportions, fingers, robe folds, delicate thin chains, text, letters, numbers, logos, angry or hostile poses, cropped limbs, extra characters.
```

### 4-4. 로 (법봉)

```
Single full-body mascot character illustration of "Law (Gavel version)".

ART STYLE — warm, calm 2D flat editorial illustration:
- No black outlines and no line art. Shapes are separated only by flat color areas; neighboring shapes meet directly color-to-color with no border or darker rim.
- Shading: one flat shadow shape per surface on the lower-right side, plus at most a very subtle soft gradient on large areas.
- Lighting: warm light from the upper left. Matte finish with a fine, barely visible paper grain on the character only.
- Shadows: none. No ground shadow, no cast shadow, no drop shadow, no glow.
- Mood: calm, warm, slightly vintage, like illustrated stationery on a judge's desk.
- Palette: navy #1C2B4F (dark #14213F, light #1F3563), dark mahogany #5A2418 (shadow #3A150E, mid-light #7A3322, highlight #C47A52, bright gloss #E8B896), warm white #F7F3EE, ink #151A24, paper white #FDFCFA.

DEFORMATION — chunky, round mascot, about 3 to 3.5 heads tall (the same family as Naero and Nambul):
- The head object is large and chunky: about 30 to 33% of the total body height, and clearly wider than the shoulders. The body is short, soft and rounded, with a slightly narrower waist. NOT tall, NOT slim, NOT realistic human proportions.
- Limbs are thick, rounded and short, like soft tubes. Hands are small rounded mitten shapes in white gloves with no individual fingers drawn. Feet are small rounded ink-black shoes, close together in a relaxed narrow stance.
- The robe is one soft, rounded bell shape: plain navy with a deep V neckline, two equal-width front bands drawn as flat slightly darker navy shapes, short wide bell sleeves that stop above the wrists, and a closed hem that stops just above the shoes. NO fold lines, NO pleats, NO wrinkles. Only one flat shadow shape on the lower-right side.
- Everything is built from big, simple, rounded flat shapes with very few details. Bronze, wood and fabric are flat color fills with one flat shadow shape and one or two small crisp flat highlight shapes: no realistic texture, no reflections, no glossy rendering.

CHARACTER — "Law", the genderless AI judge, gavel version:
No face, no eyes, no nose, no mouth, no human head. The head is a chunky judge's gavel in dark mahogany, with the mallet head on top and a short, thick handle working as the neck:
- Mallet head: a big, rounded, horizontal barrel with a smooth curvy profile: a thick center barrel, flared collars at both ends, one or two simple rings, and rounded dome end caps. Wide and heavy like a toy, clearly wider than the shoulders. Nothing sticks out of the top or sides.
- Color: flat mahogany #5A2418 with one flat shadow shape #3A150E on the lower right, one mid-light shape #7A3322 on the upper left, and one or two small crisp flat highlight shapes (#C47A52 and a thin #E8B896). No bronze, no metal.
- Handle-neck: the same mahogany wood, short, thick and gently tapered, attached to the center of the underside of the mallet head and going straight into the collar.
Outfit: navy judge robe #1C2B4F, warm-white collar and white gloves #F7F3EE, ink-black shoes #151A24.

POSE & EXPRESSION: standing, front to slight three-quarter view, both hands on hips, chest slightly out, the mallet head level and calm.

SCENE: single character only, centered, full body from head to feet fully visible, shot from a slight distance, 4:5 vertical composition with 15% empty margin on every side. Background: perfectly flat uniform solid green #00B140, no gradient, no texture, no floor, no shadow, no props in the background. Do not use green anywhere on the character.

AVOID: faces, eyes, noses, mouths, human heads, outlines, line art, contour lines, inner lines, darker edge rims, drop shadows, ground shadows, glow, 3D render, CGI, clay, plastic, photorealistic wood texture, realistic mirror-like reflections, a sound block or base, anything sticking out of the top of the mallet head, swinging or striking the gavel, tall or slim proportions, fingers, robe folds, text, letters, numbers, logos, angry or hostile poses, cropped limbs, extra characters.
```

- 4종의 머리 비율은 내로 · 남불 약 28%(3.5등신), 로 약 30 ~ 33%(3 ~ 3.5등신, 머리 오브젝트가 크다)다. 같은 높이로 서 보이게 하려면 로의 비율 문장을 `about 3.5 heads`로 맞춘다.
- 배경 제거 · 파일 형식(PNG 마스터 · WebP 웹용)은 `naero_nambul_main_image_prompts.md` 1장을 따른다(`Claude outputs/`).

---

## 5. 로 스탠딩 — 시안 스타일 (5등신 · 부드러운 음영)

마스코트(4장)와 다른 **대체 시안**이다. 약 5등신, 부드러운 면 음영, 은은한 광택 금속 · 나무, 완만한 옷 주름, 손가락이 있는 장갑. 마스코트 가족 느낌보다 "단정한 판사" 느낌이 필요한 화면(결과 화면 등)에 쓴다.

### 5-1. 로 (저울) — 평범한 체형 · 기둥을 위로 길게


```
Single full-body character illustration of "Law", the AI judge mascot of the Korean legal-experience service "Nae-Law-Nam-Bul", in the trending non-human 'object-head' character style. This version has an average male build.

ART STYLE — refined, warm 2D editorial illustration with soft painterly shading:
- No black outlines and no line art. Shapes are defined by color and soft shading only.
- Soft, smooth shading with gentle gradients on the robe and the head object; warm light from the upper left; a subtle lower-right shadow side on every form.
- The bronze has a warm satin sheen with a few soft highlights; the robe has gentle vertical fabric folds and soft tonal variation.
- A fine, barely visible paper grain over the whole character. Muted, slightly vintage, calm mood, like illustrated stationery on a judge's desk.
- No cast shadow, no ground shadow, no drop shadow, no glow.
- Palette: deep navy #1C2B4F (dark #14213F, light #1F3563), warm bronze #BA854B (dark #86581F, light #E6D2B5), warm white #F7F3EE, ink #151A24, kraft #D6A468.

PROPORTIONS — average male build, about 5 heads tall (the scale head counts as one head):
- The scale head is about 24% of the total body height, tall because of an extended central pillar. Its width (the beam and the two pans) is only about 1.3 times the shoulder width, so the scale reads as tall and upright, not wide.
- Average, natural proportions: ordinary shoulder width, a normal chest and waist, normal arms, gloved hands with clearly drawn fingers, normal legs, and ordinary shoes. Neither slim nor stocky, neither muscular nor frail.
- A relaxed upright stance with feet about shoulder-width apart.

CHARACTER — "Law (Scale)":
No face, no eyes, no nose, no mouth, no neck, no human head. The head is a warm bronze balance scale sitting directly on top of the collar: a long, elegant central pillar rising well above the beam with a small round finial on top, a gently curved horizontal beam placed in the upper part of the pillar, and two round shallow pans hanging from thin chains. Below the beam, the pillar's lower part is also slightly long, so the whole scale looks tall and slender. The scale is perfectly level (calm).
Outfit: a navy judge's robe with a deep V neckline, two equal-width front bands running down to the hem, wide bell sleeves, an ankle-length closed hem, soft vertical folds, no pleats. A warm-white collar, white gloves, and ink-black shoes.

POSE: standing upright, front to slight three-quarter view. The right hand holds a sealed verdict envelope (kraft #D6A468 with a small bronze seal) lightly against the hip; the left hand is open, palm up and slightly out to the side, as if calmly presenting something.

SCENE: single character only, centered, full body from head to feet fully visible, shot from a slight distance, 4:5 vertical composition with 15% empty margin on every side. Background: perfectly flat uniform solid green #00B140, no gradient, no texture, no floor, no shadow, no props. Do not use green anywhere on the character.

AVOID: faces, eyes, noses, mouths, necks, human heads, outlines, line art, harsh black edges, drop shadows, ground shadows, glow, photo realism, 3D render, CGI, plastic, a feminine build, bodybuilder or exaggerated muscles, a stocky or bulky body, a frail body, a wide or oversized scale, stubby or chubby proportions, text, letters, numbers, logos, angry or threatening poses, cropped limbs or a cropped finial, extra characters.
```

### 5-2. 로 (법봉) — 남성형 · 다부진 체형

```
Single full-body character illustration of "Law (Gavel version)", the AI judge mascot of the Korean legal-experience service "Nae-Law-Nam-Bul", in the trending non-human 'object-head' character style. This version has a male build: sturdy and dependable, but gentle.

ART STYLE — refined, warm 2D editorial illustration with soft painterly shading:
- No black outlines and no line art. Shapes are defined by color and soft shading only.
- Soft, smooth shading with gentle gradients on the robe and the head object; warm light from the upper left; a subtle lower-right shadow side on every form.
- The gavel is dark glossy lacquered mahogany with a few clean soft highlights following its curves; the robe has gentle vertical fabric folds and soft tonal variation.
- A fine, barely visible paper grain over the whole character. Muted, slightly vintage, calm mood, like illustrated stationery on a judge's desk.
- No cast shadow, no ground shadow, no drop shadow, no glow.
- Palette: deep navy #1C2B4F (dark #14213F, light #1F3563), dark mahogany #5A2418 (shadow #3A150E, mid-light #7A3322, highlight #C47A52, bright gloss #E8B896), warm white #F7F3EE, ink #151A24, kraft #D6A468.

PROPORTIONS — sturdy, solid, male build, about 5 heads tall (the gavel head with its handle-neck counts as one head):
- Head object about 20% of the total body height.
- Broad shoulders, a wide strong chest, a thick sturdy torso with a straight, blocky waist (no narrow waist), thick strong arms, large gloved hands with clearly drawn fingers, strong thick legs, and big solid shoes. The stance is planted and stable, feet shoulder-width apart.
- The overall silhouette is a strong, slightly stocky trapezoid, dependable and calm, never aggressive.

CHARACTER — "Law (Gavel)":
No face, no eyes, no nose, no mouth, no human head. The head is a classic lathe-turned wooden judge's gavel in deep, dark, glossy mahogany, with the mallet head on top and the handle working as a sturdy neck:
- Mallet head: a horizontal turned-wood barrel with a curvy, sculpted profile: a thick center barrel, flared collars at both ends, two or three stepped rings, and rounded dome end caps. Symmetrical, smooth, nothing sticking out of the top or sides. It sits level (calm) and is about as wide as the broad shoulders.
- Handle-neck: the same mahogany wood, a bit thicker and sturdier than a slim handle, gently tapered with one or two small turned rings, attached to the center of the underside of the mallet head and going straight down into the collar. It is clearly visible, so the head floats lightly above the shoulders.
Outfit: a navy judge's robe cut broad at the shoulders, with a deep V neckline, two equal-width front bands running down to the hem, wide bell sleeves, an ankle-length closed hem, soft vertical folds, no pleats. A warm-white collar, white gloves, and ink-black shoes.

POSE: standing upright, front to slight three-quarter view, chest slightly out. The right hand holds a sealed verdict envelope (kraft #D6A468 with a small mahogany-red seal) firmly against the hip; the left hand is open, palm up and slightly out to the side, as if calmly presenting something.

SCENE: single character only, centered, full body from head to feet fully visible, shot from a slight distance, 4:5 vertical composition with 15% empty margin on every side. Background: perfectly flat uniform solid green #00B140, no gradient, no texture, no floor, no shadow, no props. Do not use green anywhere on the character.

AVOID: faces, eyes, noses, mouths, human heads, outlines, line art, harsh black edges, drop shadows, ground shadows, glow, photo realism, 3D render, CGI, plastic, a feminine build, a narrow waist, a slim or frail body, bodybuilder or exaggerated muscles, a sound block or base under the gavel, anything sticking out of the top of the mallet head, swinging or striking the gavel, stubby or chubby proportions, oversized head, text, letters, numbers, logos, angry or threatening poses, cropped limbs, extra characters.
```

- 저울은 평범한 체형, 법봉은 다부진 체형으로 정리된 상태다. 두 로의 체형을 맞추려면 법봉의 PROPORTIONS를 5-1과 같은 "average male build" 문장으로 바꾼다.
- 머리가 어색하게 크거나 작으면 저울 `about 24%`를 22 ~ 26%로, 법봉 `about 20%`를 18 ~ 22%로 조절한다.

---

## 6. 데포르메별 프롬프트 (11종)

### 6-1. 쓰는 법

1. 2장(내로 · 남불) 또는 3장(로) 프롬프트에서 **`DEFORMATION` 블록만** 아래 블록으로 교체한다. 나머지(ART STYLE · CHARACTER · SHEET LAYOUT · AVOID)는 그대로 둔다.
2. 로는 얼굴이 없으므로, 로용 블록은 얼굴 · 눈 줄을 빼고 머리 오브젝트(저울 · 법봉)의 비율과 연기를 쓴다.
3. 3장의 `MATERIAL RULES` 블록은 로에 계속 붙여 둔다(소재를 평면 색 면으로 유지).
4. 등신이 높은 데포르메는 시트 칸이 좁아 턴어라운드가 잘릴 수 있다. 21:9로 바꾸거나 표정 줄을 따로 뽑는다.
5. 기본(마스코트)은 2장 · 3장의 블록이다. 아래 11종은 모두 변형이다.

| # | 데포르메 | 등신 | 핵심 특징 | 로 적용 |
| --- | --- | --- | --- | --- |
| 1 | 데즈카 오사무 스타일 — 일본식 디즈니풍 | 2.5~3 | 큰 둥근 눈 · 고무호스 팔다리 | 2.5~3등신 · 머리 오브젝트 약 40% |
| 2 | 김성모 스타일 — 한국식 극화풍 | 5~6 | 각진 얼굴 · 굵은 눈썹 · 진지한 개그 | 5~6등신 · 어깨 넓고 단단한 체형 |
| 3 | 강철의 연금술사 스타일 — 소년만화풍 | 5~6 | 날렵한 체형 · 역동적 자세 | 5~6등신 · 날렵한 체형 |
| 4 | 란마 스타일 — 80~90년대 일본 애니메이션풍 | 5 | 둥근 얼굴 · 레트로 셀 색감 | 5등신 · 부드러운 둥근 체형 |
| 5 | 던전 속 사정 스타일 — 남성향 모에 웹툰풍 | 4~5 | 작은 얼굴 · 그라데이션 홍채 · 모에 | 4~5등신 · 작고 가는 몸 · 큰 머리 |
| 6 | 우마무스메 스타일 — 최신 픽시브풍 | 5~6 | 정교한 눈 · 가챠 일러스트 마감 | 5~6등신 · 정교한 마감 |
| 7 | 트릭컬 스타일 — 볼따구풍 | 2 | 찹쌀떡 볼 · 거대한 머리 | 2등신 · 거대한 머리 오브젝트 |
| 8 | 하이큐 스타일 — 여성향 애니메이션풍 | 6~7 | 긴 팔다리 · 날카로운 이목구비 | 6~7등신 · 길고 깔끔한 실루엣 |
| 9 | 그래비티 폴즈 스타일 — 텀블러풍 | 3~4 | 점 눈동자 · 국수 팔다리 · 비대칭 | 3~4등신 · 비대칭 · 국수 팔다리 |
| 10 | 파워퍼프걸 스타일 — 카툰풍 | 2 | 거대한 타원 눈 · 극단적 단순화 | 2등신 · 극단적 단순화 |
| 11 | 팬티앤스타킹 위드 가터벨트 스타일 — 일본식 카툰풍 | 4~5 | 가늘고 긴 팔다리 · 펑크팝 | 4~5등신 · 가늘고 긴 팔다리 |

> 등신이 높은 데포르메(극화 · 소년만화 · 80~90 애니 · 픽시브 · 여성향)에서는 내로가 두 발로 선 의인화 고양이, 남불이 키 큰 불꽃 머리 인물로 바뀐다. 정체성(네이비 털 · 반다나 · 불꽃 머리)은 유지된다.
> 11종 이외의 스타일(픽셀 · 3D 등)도 같은 방식으로 DEFORMATION 블록만 추가하면 된다.


### 6-2. 데즈카 오사무 스타일 — 일본식 디즈니풍

**내로 · 남불용 DEFORMATION 블록**

```
DEFORMATION — Classic 1950s–60s Japanese 'Disney-influenced' manga deformation:
- About 2.5 to 3 heads tall; large round head, pear-shaped soft body, rubber-hose-like smooth limbs with round mitten hands (four rounded fingers at most) and big round shoes-like feet.
- Eyes: very large, round and shiny, with big pupils and two or three bold white highlights (one may be star-shaped); Naero has a few short eyelashes.
- Faces are soft and round with chubby cheeks; mouths are wide and expressive, opening into big rounded shapes.
- Acting: bouncy, theatrical, readable silhouettes; expressions are big and innocent.
- the flame on Nambul's head is drawn as a smooth, bulbous, rounded flame like a classic cartoon fire, three to four soft lobes.
- Whatever the deformation, the identity rules below never change.
```

**로용 DEFORMATION 블록**

```
DEFORMATION — Classic 1950s–60s Japanese 'Disney-influenced' manga deformation:
- About 2.5 to 3 heads tall; the head object is very large (about 40% of the body height); pear-shaped soft body, rubber-hose-like smooth limbs with round mitten hands (four rounded fingers at most) and big round shoe-like feet.
- The robe is a soft, rounded bell shape with big round sleeves; nothing is sharp.
- No faces: the head objects themselves are round, bouncy and friendly (scale pans round and shiny, gavel barrel plump and rounded).
- Acting: bouncy, theatrical, readable silhouettes; big innocent reactions shown by the pans bouncing or the gavel head tilting and hopping.
- Whatever the deformation, the identity rules never change.
```

### 6-3. 김성모 스타일 — 한국식 극화풍

**내로 · 남불용 DEFORMATION 블록**

```
DEFORMATION — Korean 'gekiga' dramatic comic deformation, played for humor:
- About 5 to 6 heads tall; sturdy, broad-shouldered, solid bodies with firm stances; Naero is an anthropomorphic standing cat, Nambul a tall solid figure.
- Faces: strong, angular planes; thick, bold eyebrow shapes; narrow, intense eyes with small pupils; firm closed mouths. The intensity reads as serious and determined, never angry or hostile.
- Shading: large, dramatic flat shadow shapes across half of the face and body (still flat color shapes, no hatching lines), giving a heavy, cinematic look.
- Acting: over-serious poses for comic effect — clenched fists, dramatic stares, a single sweat drop shape.
- the flame on Nambul's head tips may be slightly more pointed and taller to feel dramatic, but still smooth-edged.
- Whatever the deformation, the identity rules below never change.
```

**로용 DEFORMATION 블록**

```
DEFORMATION — Korean 'gekiga' dramatic comic deformation, played for humor:
- About 5 to 6 heads tall; sturdy, broad-shouldered, solid body with a firm stance; the head object is about 18% of the body height, drawn with strong, angular planes (a squared-off scale pillar, a heavier gavel barrel).
- The robe has strong, angular folds and large dramatic flat shadow shapes across half of the body (still flat, still no outlines).
- Acting: over-serious poses for comic effect — a solemn stance, a single dramatic sweat drop beside the head; emotion only by the head object's tilt. Never threatening: no raised fists, no striking with the gavel.
- Whatever the deformation, the identity rules never change.
```

### 6-4. 강철의 연금술사 스타일 — 소년만화풍

**내로 · 남불용 DEFORMATION 블록**

```
DEFORMATION — 2000s shonen manga deformation:
- About 5 to 6 heads tall; lean, athletic, well-balanced bodies; Naero is a standing anthropomorphic cat, Nambul a slim energetic figure.
- Faces: clean, slightly angular shapes; medium-large eyes with a clearly defined iris and one highlight; confident eyebrows; small mouths with lively expressions.
- Hands with simplified fingers, clear joints, practical clean silhouettes.
- Acting: dynamic, confident stances (one foot forward, slight twist), friendly grins; in gag expressions faces may briefly turn simplified and chibi-like.
- the flame on Nambul's head is spirited and upward, with three to five clean flame points.
- Whatever the deformation, the identity rules below never change.
```

**로용 DEFORMATION 블록**

```
DEFORMATION — 2000s shonen manga deformation:
- About 5 to 6 heads tall; lean, athletic, well-balanced body; the head object is about 18% of the body height, with a clean, slightly angular silhouette (a slender scale pillar, a sleek gavel barrel).
- Hands have simplified fingers and clear joints; the robe sleeves and hem flutter slightly with movement.
- Acting: dynamic, confident stances (one foot forward, slight twist); emotion shown by the head object's motion.
- Whatever the deformation, the identity rules never change.
```

### 6-5. 란마 스타일 — 80~90년대 일본 애니메이션풍

**내로 · 남불용 DEFORMATION 블록**

```
DEFORMATION — 1980s–90s Japanese TV anime deformation:
- About 5 heads tall; soft, rounded bodies with slightly long legs; Naero is a standing anthropomorphic cat with a cute, girlish build, Nambul a lively boyish figure.
- Faces: round faces with a small, slightly pointed chin; large eyes with tall oval irises, one big glossy highlight and a smaller second one; small, simple mouths.
- Colors feel like retro anime cels: flat fills with one crisp shadow tone, slightly softer saturation.
- Acting: comedic, energetic poses, big reactions (surprise jumps, sweat-drop gag faces).
- the flame on Nambul's head has bouncy, wavy flame points, like a retro anime fire effect.
- Whatever the deformation, the identity rules below never change.
```

**로용 DEFORMATION 블록**

```
DEFORMATION — 1980s–90s Japanese TV anime deformation:
- About 5 heads tall; soft, rounded body with slightly long legs; the head object is about 20% of the body height, with gently rounded edges.
- Colors feel like retro anime cels: flat fills with one crisp shadow tone, slightly softer saturation.
- Acting: comedic, energetic poses, big reactions (surprise jumps, a sweat-drop gag mark beside the head), emotion only by the head object's movement and the body posture.
- Whatever the deformation, the identity rules never change.
```

### 6-6. 던전 속 사정 스타일 — 남성향 모에 웹툰풍

**내로 · 남불용 DEFORMATION 블록**

```
DEFORMATION — Korean 'moe' webtoon deformation (cute-focused, wholesome):
- About 4 to 5 heads tall; small, slender body, larger head, delicate hands and feet; Naero is a cute standing anthropomorphic cat, Nambul a small slim figure.
- Faces: small, soft faces; very large, glossy eyes with gradient irises (darker top, lighter bottom), two or three highlights and a soft reflected light; tiny mouths; soft blush shapes on the cheeks.
- Expressions are cute and gentle: shy smiles, puffed cheeks, sparkly eyes. Wholesome and non-sexual; modest, natural poses.
- Clean webtoon coloring: flat fills, one soft shadow tone, a light airy feel.
- the flame on Nambul's head is soft and fluffy, with gently curling tips.
- Whatever the deformation, the identity rules below never change.
```

**로용 DEFORMATION 블록**

```
DEFORMATION — Korean 'moe' webtoon deformation (cute-focused, wholesome):
- About 4 to 5 heads tall; a small, slender body, a slightly larger head object (about 24% of the body height), delicate hands and feet.
- Clean webtoon coloring: flat fills, one soft shadow tone, a light airy feel; the head object gets a soft, small sparkle highlight.
- Acting: cute and gentle, shy tilts of the head object, a small sparkle mark beside it; wholesome and non-sexualized.
- Whatever the deformation, the identity rules never change.
```

### 6-7. 우마무스메 스타일 — 최신 픽시브풍

**내로 · 남불용 DEFORMATION 블록**

```
DEFORMATION — Modern pixiv / mobile-game illustration deformation:
- About 5 to 6 heads tall; slim, polished proportions; Naero is a standing anthropomorphic cat girl-style mascot (still a cat with fur, ears and tail), Nambul a slim boyish figure.
- Faces: refined small faces; large, highly detailed eyes with layered iris gradients, sparkles and multiple highlights; small mouths; subtle blush.
- Fur and flame are drawn with layered, flowing tuft shapes like modern anime hair, with many overlapping locks.
- Finish feels like a polished gacha-game character sheet, but still within the muted palette and flat rendering.
- Acting: lively, idol-like friendly poses (peace sign, small wave).
- the flame on Nambul's head flows upward in layered, hair-like locks of flame.
- Whatever the deformation, the identity rules below never change.
```

**로용 DEFORMATION 블록**

```
DEFORMATION — Modern pixiv / mobile-game illustration deformation:
- About 5 to 6 heads tall; slim, polished proportions; the head object is about 18% of the body height, drawn with refined, layered details (finely detailed scale chains and pan rims, a gavel with elegant turned rings).
- Finish feels like a polished gacha-game character sheet, but still within the muted palette and flat rendering rules: layered flat shapes, small sparkle highlights.
- Acting: lively, idol-like friendly poses (a small wave, a presenting gesture).
- Whatever the deformation, the identity rules never change.
```

### 6-8. 트릭컬 스타일 — 볼따구풍

**내로 · 남불용 DEFORMATION 블록**

```
DEFORMATION — 'Cheek-squish' mochi chibi deformation:
- About 2 heads tall; head is huge and wide, cheeks are big, round and squishy like mochi, bulging out past the face line; tiny round body and nub limbs.
- Eyes: round and set wide apart, low on the face; mouth tiny and often pouty (a small 'ㅅ' or 'w' shape as a filled shape); big soft blush ovals on the cheeks.
- Overall shape is a soft dumpling silhouette; very bouncy, cute and silly.
- Acting: cheek-puffing, squishing, wobbling poses; expressions exaggerated through cheek shapes.
- the flame on Nambul's head sits on top of a round, cheeky face as a small, puffy three-lobed flame.
- Whatever the deformation, the identity rules below never change.
```

**로용 DEFORMATION 블록**

```
DEFORMATION — 'Cheek-squish' mochi chibi deformation:
- About 2 heads tall; the head object is huge and wide (about 50% of the body height) and soft and plump: a plump round scale with big round pans, a fat round gavel barrel; a tiny round dumpling body with nub limbs.
- Overall shape is a soft dumpling silhouette; very bouncy, cute and silly.
- Acting: wobbling, squishing poses; pouty feelings are shown by one pan drooping or the gavel head sagging to one side.
- Whatever the deformation, the identity rules never change.
```

### 6-9. 하이큐 스타일 — 여성향 애니메이션풍

**내로 · 남불용 DEFORMATION 블록**

```
DEFORMATION — Modern sports anime deformation (character-focused, sharp and clean):
- About 6 to 7 heads tall; long-limbed, slim, athletic bodies; Naero is a tall, sleek standing anthropomorphic cat, Nambul a lean, sporty figure.
- Faces: sharp, clean features; eyes with defined upper lash shapes and expressive irises; slim jaw; natural small mouths.
- Fur tufts and flame are drawn as sharp, clean, spiky-but-tidy hair-like shapes.
- Acting: dynamic sports-anime energy — mid-stride, determined looks, bright competitive grins, but never angry.
- Clean, high-contrast flat coloring with one crisp shadow tone.
- the flame on Nambul's head rises like spiky sporty hair, with four or five clean flame points.
- Whatever the deformation, the identity rules below never change.
```

**로용 DEFORMATION 블록**

```
DEFORMATION — Modern sports anime deformation (character-focused, sharp and clean):
- About 6 to 7 heads tall; long-limbed, slim, athletic body; the head object is about 15% of the body height with sharp, clean edges.
- Acting: dynamic sports-anime energy — mid-stride, determined stance, a bright competitive mood shown by the head object held high.
- Clean, high-contrast flat coloring with one crisp shadow tone.
- Whatever the deformation, the identity rules never change.
```

### 6-10. 그래비티 폴즈 스타일 — 텀블러풍

**내로 · 남불용 DEFORMATION 블록**

```
DEFORMATION — 2010s Western indie cartoon / Tumblr deformation:
- About 3 to 4 heads tall; quirky, slightly asymmetric shapes; noodly limbs, rounded mitten hands with few fingers, small feet.
- Eyes: large round white eyes with small solid dot pupils (Nambul's pupils always present), eyes often touching or nearly touching each other.
- Faces: simple, expressive shapes; wide mouths that change shape a lot; small rounded nose only for Naero.
- Acting: awkward, funny, mysterious-adventure vibe — shrugging, side-eye glances, nervous grins.
- the flame on Nambul's head is a simple, slightly lopsided cartoon flame with three soft points.
- Whatever the deformation, the identity rules below never change.
```

**로용 DEFORMATION 블록**

```
DEFORMATION — 2010s Western indie cartoon / Tumblr deformation:
- About 3 to 4 heads tall; quirky, slightly asymmetric shapes; the head object is about 26% of the body height and a little lopsided (the scale slightly crooked, the gavel head slightly tilted); noodly limbs, rounded mitten hands with few fingers, small feet.
- Acting: awkward, funny, mysterious-adventure vibe — shrugging, nervous wobble of the head object.
- Whatever the deformation, the identity rules never change.
```

### 6-11. 파워퍼프걸 스타일 — 카툰풍

**내로 · 남불용 DEFORMATION 블록**

```
DEFORMATION — Late-1990s minimalist Western TV cartoon deformation:
- About 2 heads tall; huge head, tiny body, very short stubby limbs, no fingers (round nub hands), small oval feet.
- Eyes: giant oval eyes taking up about half of the face, large pupils with one or two big highlights; tiny or no visible mouth when neutral.
- Shapes are extremely simple and graphic, bold color blocks, minimal details, strong silhouette.
- Acting: punchy, heroic, cute poses — flying-like jumps, fists up in determination (never angry), big happy reactions.
- the flame on Nambul's head is a bold, simple flame shape with three clean rounded points.
- Whatever the deformation, the identity rules below never change.
```

**로용 DEFORMATION 블록**

```
DEFORMATION — Late-1990s minimalist Western TV cartoon deformation:
- About 2 heads tall; a huge head object (about 50% of the body height), a tiny body, very short stubby limbs, no fingers (round nub hands), small oval feet.
- Shapes are extremely simple and graphic: bold color blocks, minimal details, a strong silhouette; the scale and gavel are drawn as simple bold shapes.
- Acting: punchy, heroic, cute poses; never aggressive.
- Whatever the deformation, the identity rules never change.
```

### 6-12. 팬티앤스타킹 위드 가터벨트 스타일 — 일본식 카툰풍

**내로 · 남불용 DEFORMATION 블록**

```
DEFORMATION — Japanese-made Western-cartoon-inspired deformation (graphic and stylish):
- About 4 to 5 heads tall; elongated, very thin limbs, small hands and feet, stylish exaggerated silhouettes (long legs, small torso).
- Faces: very simplified graphic faces; large wide eyes with small pupils; mouths drawn as bold simple shapes that change dramatically.
- Shapes are sharp and bold with strong graphic stylization, flat color blocks, very little shading.
- Acting: snappy, stylish, energetic poses with a punk-pop attitude, but wholesome and non-sexual.
- the flame on Nambul's head is a bold, stylized graphic flame with tall, sweeping points.
- Whatever the deformation, the identity rules below never change.
```

**로용 DEFORMATION 블록**

```
DEFORMATION — Japanese-made Western-cartoon-inspired deformation (graphic and stylish):
- About 4 to 5 heads tall; elongated, very thin limbs, small hands and feet, a stylish exaggerated silhouette (long legs, a small torso); the head object is about 22% of the body height, drawn as a bold, graphic shape.
- Shapes are sharp and bold, flat color blocks, very little detail; the robe is a sleek graphic shape.
- Acting: snappy, stylish, energetic poses with a punk-pop attitude, but wholesome and non-threatening.
- Whatever the deformation, the identity rules never change.
```

---

## 7. 문제와 대응 · 검수

### 7-1. 문제와 대응

| 문제 | 원인 | 대응 |
| --- | --- | --- |
| 짧고 뚱뚱해 보임 | `2.5 ~ 3 heads` · `short stubby limbs` 같은 표현 | 3.5등신, 머리 높이 28%, 긴 다리 · 가는 몸통, `NOT stubby` |
| 머리가 화면을 너무 차지함 | 4:5 구도에 여백이 적음 | 여백 15%, `shot from a slight distance` |
| 남불 얼굴이 가면처럼 분리됨 | `inner flame around the face`를 얼굴 면으로 해석 | 얼굴은 불꽃 중심에 바로 올림, `NO separate face shape` |
| 남불 배 무늬 · 곰 인형 같은 몸 | 몸 설명 부족 | `NO belly patch`, 가는 몸 |
| 3D처럼 나옴 · 경계선이 남음 | `soft gradients · glow` 같은 표현 | 3D · 광택 금지어, 색 면끼리 맞닿음 |
| 로가 키 크고 가늘며 질감이 3D처럼 나옴 | 5등신 · 주름 · 금속 광택 표현 | 3장의 DEFORMATION + MATERIAL RULES, 로브 주름 금지, 소재는 평면 색 면 |
| 법봉 머리 위로 더듬이가 남 | 손잡이를 머리 위로 세운 설명 | 손잡이는 머리 아래 목처럼, `nothing sticking out of the top` |
| 법봉이 위협적으로 보임 | 휘두르는 자세 | `never swung or struck`, 가만히 선 자세 |

### 7-2. 검수

- [ ] 선 · 그림자 · 글자 없음, 초록 배경이 균일함(스탠딩) / 신문지 배경 격자 배치(시트)
- [ ] 내로: 반다나가 보이고 가슴을 덮지 않음, 입속은 반다나 색
- [ ] 남불: 불꽃은 머리에만, 코 없음, 눈동자 보임, 얼굴이 불꽃 중심에 자연스럽게 있음
- [ ] 로: 얼굴 · 목(저울) 없음, 법복 형태 유지, 소재가 평면 색 면, 감정은 머리 오브젝트와 자세로 표현
- [ ] 로(법봉): 머리 위로 튀어나온 것 없음, 손잡이가 목 역할, 휘두르는 자세 없음
- [ ] 캐릭터 어디에도 초록 없음, 손발이 잘리지 않음
- [ ] 같은 세트로 놓았을 때 4종의 높이 · 그림체가 어긋나지 않음

---

## 8. 확정 이미지와 다른 점 (10/14 기준)

확정 이미지(`~/Documents/NaeLawNamBul/`의 `character-sheet_*` · `standing_*`, 아키텍처 2-1)와 이 문서의 프롬프트를 비교했다. 이미지 생성 결과가 프롬프트와 다르게 나온 부분이고, **기준은 확정 이미지**다. 다시 뽑을 때는 프롬프트를 확정 이미지 쪽으로 고치거나, 확정 이미지를 참조 이미지로 첨부한다(1-3).

### 8-1. 비율 · 키

| 대상 | 프롬프트 | 확정 이미지 | 기준 문서 |
| --- | --- | --- | --- |
| 내로 · 남불 등신 | 약 3.5등신, 머리 28%, 가는 몸통 · 긴 다리(2장 · 4-1 · 4-2) | **약 2등신**, 머리 약 50%(남불은 불꽃 포함 약 60%), 짧은 몸통 · 짧은 다리 | 아키텍처 2-5 · 7-3 #14 ⚠️ |
| 내로 : 남불 키 | 남불이 불꽃만큼 조금 더 큼(2장 · 4-2) | 시트 키 칸은 남불 약 1.5배. **확정값은 둘 다 120** | 아키텍처 2-5 |
| 로 그림체 · 비율 | 기본은 마스코트(3장 · 4-3 · 4-4: 3 ~ 3.5등신, 머리 30 ~ 33%, 평면 색 면, 주름 없음, 손가락 없음) · 5장은 "대체 시안" | **확정 로는 5장 쪽**(사실 질감 · 주름 · 손가락 있는 장갑). 시트 · 스탠딩 모두 약 4 ~ 5등신, 머리 약 21 ~ 25% | 아키텍처 2-3 (`authority` × `adult`) |
| 로 키 | 4장 메모 "같은 높이로 서 보이게" | **저울 230 · 법봉 190 · 법전 210**(내로 · 남불 120 대비 약 1.9 · 1.6 · 1.75배) | 아키텍처 2-5 |

### 8-2. 로 시트 · 자세

| 대상 | 프롬프트 (3장) | 확정 시트 (`character-sheet_law-scale_law-gavel.png`) |
| --- | --- | --- |
| 위 칸 | 둘이 나란히, 두 손 허리 | 둘이 나란히, **봉투를 들고 섬** |
| 아래 칸 | 감정 포즈 6종(calm · happy · surprised · thinking · sorry · sleepy) | **자세 6종**(서기 · 주먹 · 걷기 · 턱 괴기 · 봉투 들기 · 봉투 내밀기) |
| 옆 칸 | 키 비교 + 색 견본 | 색 견본만(키 비교 없음) |
| 빈 칸 | — | 저울 정면 아래 칸이 비어 있음 |
| 스탠딩 자세 | 4-3 · 4-4는 두 손 허리 | 확정 스탠딩은 5장과 같이 **봉투를 들고 한 손을 펴 안내** |

### 8-3. 색

프롬프트 팔레트와 확정 이미지에서 뽑은 색(아키텍처 2-4)이다. 확정 이미지가 전반적으로 **더 밝고 채도가 높다**.

| 부위 | 프롬프트 | 확정 이미지 | 차이 |
| --- | --- | --- | --- |
| 내로 털 | `#1C2B4F` | `#1D2C51` | 거의 같음 |
| 내로 눈 | `#FFD696` | `#FCD788` | 거의 같음 |
| 내로 반다나 | `#B3261E` (벽돌 빨강) | `#C82C21` | 더 밝은 빨강 |
| 내로 코 | `#E2A08F` (dusty pink) | `#EC7460` 근처 | 더 진한 코랄 |
| 내로 귀 안쪽 | `#5B3A40` (plum) | `#823942` | 더 붉음 |
| 남불 불꽃 바깥 | `#B3261E` | `#CB1E0E` | 더 선명한 빨강 |
| 남불 불꽃 중간 | `#D4692A` | `#F46517` | 더 선명한 주황 |
| 남불 얼굴 면 | `#FFD696` | `#FDBA51` | 더 진한 노랑 |
| 남불 몸 | `#9C3A1D` (rust) | `#A92407` | 더 붉음 |
| 로 판사복 | `#1C2B4F` | `#253048` | 조금 밝음 |
| 로 저울 황동 | `#BA854B` | `#D29C5E` (밝은 면) | 더 밝음 |
| 로 법봉 마호가니 | `#5A2418` | `#5A220B` | 거의 같음 |
| 봉투 크래프트 | `#D6A468` | `#CF995A` | 거의 같음 |
| 봉투 도장 | 청동(5-1) · 마호가니 빨강(5-2) | 붉은 갈색 밀랍 | 저울 쪽은 프롬프트와 다름 |
| 시트 배경 | `#ECE2D4` | 약 `#F2E0C5` | 더 노란 베이지 |

### 8-4. 설정 문구

설정과 프롬프트 문구는 따로 간다(아키텍처 5-1). 출력 품질 때문에 그대로 둔 것이지만, 바뀐 설정과 다른 점을 적어 둔다.

| 프롬프트 문구 | 지금 설정 | 비고 |
| --- | --- | --- |
| 내로 · 남불 `actor (can play any role)`, 1-2 "배우" | 배우가 아니라 **법의 정령**, 역할은 명령 수행의 수단(프로필 · 세계관 2 · 5장) | 이미지 결과에는 영향 없음. 문구는 그대로 둬도 된다 |
| 로 `male build`(5장), "남성형 체형은 시안 스킨" | 로는 **무성**. 확정 로가 5장 체형을 따르므로 이 문구가 사실상 기본이 됐다 | 출력 품질용 문구로 둔다. 화면 · 대사에서 성별을 드러내지 않는다 |
| 로 = 저울 · 법봉 2종 | 로는 **3인 합의부**(저울 = 중도 · 법봉 = 엄벌 · **법전 = 자비**) | **법전 로 프롬프트가 없다** → 5장 형식으로 새로 쓴다(제작 목록 A1 · A2) |
| 머리말 "기본 = 마스코트형 약 3.5등신" | 로는 `authority` 그림체가 기본(아키텍처 2-3) | — |

### 8-5. 다시 뽑을 때 권장

1. **참조 이미지 첨부를 기본으로 한다.** 내로 · 남불은 `standing_naero.png` · `standing_nambul.png`, 로는 `standing_law-scale.png` · `standing_law-gavel.png`를 붙이고 1-3의 한 줄을 맨 위에 둔다. 단, "follow the proportions described below"는 프롬프트 비율(3.5등신)을 따르게 하므로, 확정 비율을 지키려면 다음 문장으로 바꾼다.

```
Match the face, colors, design and proportions of the attached reference exactly.
```

2. 로는 4장 · 3장(마스코트) 대신 **5장 프롬프트**를 기본으로 쓴다. 법봉 체형을 저울과 맞출지는 5장 아래 메모를 따른다.
3. 색 hex를 확정값(8-3)으로 바꿀지는 결과를 보며 정한다. 프롬프트 hex를 그대로 두고 참조 이미지로 색을 맞추는 쪽이 지금까지는 결과가 나았다면 그대로 둔다.

---

## 9. 신규 프롬프트 v2 (확정 설정 기준)

1 ~ 7장 원문은 그대로 두고, **10/13 ~ 10/14 확정 설정**(확정 이미지 · 무성 · 법의 정령 · 로 3인 합의부 · 키 내로 · 남불 120 / 저울 230 · 법봉 190 · 법전 210 · 색 2-4)에 맞춘 프롬프트를 새로 쓴다. 이후 생성은 이 장을 기본으로 한다.

| 원문(1 ~ 7장)과 다른 점 | v2 |
| --- | --- |
| 내로 · 남불 비율 | 3.5등신 → **확정 이미지 비율(약 2등신, 큰 머리 · 짧은 몸)** ⚠️ 아키텍처 7-3 #14가 바뀌면 DEFORM 블록만 고친다 |
| 로 그림체 | 마스코트 → **권위형 사실 질감(`authority` × `adult`)**, 저울 · 법봉 · **법전** 3종 |
| 색 | 원문 팔레트 → **확정 이미지 색(아키텍처 2-4)** |
| 설정 문구 | `actor` · `male build` 빼고 **genderless** · "법의 정령"은 이미지에 필요 없으므로 넣지 않음 |
| 키 | 내로 = 남불(120), 저울 230 · 법봉 190 · 법전 210(체형 반영) |
| 참조 이미지 | 확정 이미지를 첨부하고 **비율까지 그대로 따르게** 한다(8-5) |

### 9-1. 쓰는 법

1. **참조 이미지가 있으면 9-2의 [REF] 한 줄 + 바꿀 내용만** 쓴다(짧은 프롬프트). Max 생성기는 참조 이미지를 잘 따르므로 이 방식이 기본이다. 예시는 [제작 목록](character-image-checklist.md) 5장.
2. 참조 이미지가 없거나 새 캐릭터(법전 로 등)를 처음 만들 때는 **블록을 순서대로 이어 붙인다**: `[REF]`(있으면) → `STYLE` → `DEFORM` → `IDENTITY`(캐릭터 수만큼) → `POSE` → `SCENE` → `AVOID`(공통 + 캐릭터별).
3. 괜찮은 예시 사진이 있으면 [REF-STYLE] 줄로 "구도 · 분위기만 따라 하고 캐릭터는 바꾸지 말 것"을 붙인다.

### 9-2. 참조 문장

**[REF]** — 확정 이미지(스탠딩 · 시트)를 첨부할 때

```
Use the attached reference image(s) as the exact model of the character(s): copy the face, colors, proportions, head-to-body ratio and design exactly. Do not redesign them. Change only what is described below.
```

**[REF-STYLE]** — 구도 · 분위기 예시 사진을 따로 첨부할 때

```
The extra attached example image is only a guide for composition, pose and mood. Do not copy its characters, art style or colors; keep our characters exactly as in the character reference.
```

### 9-3. STYLE 블록

**`editorial`** — 내로 · 남불

```
ART STYLE — clean, warm 2D flat mascot illustration:
- No black outlines and no line art; shapes are separated only by flat color areas.
- Facial features are filled shapes, not strokes.
- One soft shadow tone per surface on the lower-right side; at most a very subtle gradient on large areas (such as the flame).
- Warm light from the upper left. Matte finish. No cast shadow, no ground shadow, no drop shadow, no glow.
- Mood: friendly, cheerful, slightly vintage.
```

**`authority`** — 로 (저울 · 법봉 · 법전)

```
ART STYLE — refined, warm 2D editorial illustration with soft painterly shading and believable materials:
- No black outlines and no line art; forms are defined by color and soft shading.
- The judge's robe is matte wool with gentle vertical folds; brass has a warm satin sheen; wood is polished mahogany; leather is smooth with soft highlights.
- Warm light from the upper left, a subtle lower-right shadow side on every form. No cast shadow, no ground shadow, no drop shadow, no glow.
- Mood: calm, dignified and trustworthy, like a formal portrait; never cold or threatening.
```

### 9-4. DEFORM 블록

**`mascot`** — 내로 · 남불 (확정 이미지 비율)

```
DEFORMATION — chunky, cute mascot, about 2 heads tall (match the reference):
- A big round head about half of the total height (Naero: ears excluded; Nambul: the flame head is about 60% of the height including the flame).
- A short, soft, rounded torso; short sturdy legs; small rounded hands and feet. Simple, round, friendly shapes.
- Large round eyes with one big glossy highlight and one small second highlight; small simple mouths.
- Naero and Nambul are exactly the same height (Naero measured to the ear tips, Nambul to the flame tip).
```

**`adult`** — 로

```
DEFORMATION — upright adult figure, about 4.5 heads tall counting the head object as one head:
- The head object is about 21 to 25% of the total height and sits directly on the collar or on a short post (Law (Book) is the exception: its book floats above the collar with a clear gap and no neck or post).
- A calm, upright adult build hidden by a broad ankle-length robe; the build differs by version (see CHARACTER); no gendered body features.
- White gloves with clearly drawn fingers; plain dark leather shoes.
- Height relative to Naero and Nambul on the same ground line: Law (Scale) about 1.9 times (tall, straight, average build), Law (Book) about 1.75 times (medium height, average build), Law (Gavel) about 1.6 times (the shortest, with broad shoulders and a wide, sturdy chest).
```

### 9-5. IDENTITY 블록

**`naero`**

```
CHARACTER — "Naero", a genderless cat mascot standing on two legs:
Deep navy fur (#1D2C51, shadow #1A274B), small fur tufts on the head and cheeks. Big round golden-yellow eyes (#FCD788) with large dark pupils (#191C29) and white highlights. A small coral-pink nose (#EC7460). Pointed ears with rosy-plum inner ears (#823942). A long tail with a curled tip. A red triangular bandana (#C82C21, shadow #A42119) tied at the neck, always visible. When the mouth is open, the inside is the same red as the bandana.
```

**`nambul`**

```
CHARACTER — "Nambul", a genderless fire sprite mascot:
The head IS a flame: deep red outer flame (#CB1E0E), orange middle flame (#F46517), warm yellow center (#FDBA51). The eyes and mouth sit directly on the yellow center; there is no separate face shape or mask. Large round eyes with cream whites (#FCF3E3) and dark pupils (#18181B) that are always visible. A small open mouth with a red inside (#E74F32). No nose. Below the head: a plain brick-red body and limbs (#A92407, shadow #771708) with no fire anywhere below the head.
```

**`law-scale`** — 중도 · 기본 대표

```
CHARACTER — "Law", a genderless AI judge with a brass balance scale as its head:
No face, no eyes, no mouth, no neck, no human head. The head is a brass scale of justice (light #D29C5E, mid #B58A59, dark #956331): a tall central post with a round finial, a gently curved beam and two shallow pans on thin chains; the post rises straight out of the collar. Outfit: a deep navy judge's robe (#253048, folds #171F30, light #38445C) with a deep V neckline, two vertical front bands, wide bell sleeves and an ankle-length hem; a warm-white shirt collar and white gloves (#F2E4D0); dark shoes. Build: tall and straight, average width. The scale is level.
```

**`law-gavel`** — 엄벌

```
CHARACTER — "Law", a genderless AI judge with a judge's gavel as its head:
No face, no eyes, no mouth, no human head. The head is a polished mahogany gavel (dark #5A220B, mid #95562C, highlight #CA9654): a horizontal barrel with ringed collars and rounded end caps, on a short turned handle that works as the neck and goes straight into the collar. Nothing sticks out of the top. Same robe, collar, gloves and shoes as the scale version. Build: the shortest of the three Laws, with broad shoulders and a wide, sturdy chest (solid and dependable, never aggressive). The gavel is level and is never swung or struck.
```

**`law-book`** — 자비 (새로 만듦, 색은 제안 ⚠️) · **목 없이 공중에 떠 있는 법전, 기본은 표지 정면**

```
CHARACTER — "Law", a genderless AI judge with a floating law book as its head:
No face, no eyes, no mouth, no neck, no human head. The head is a thick, closed law book that FLOATS freely in the air above the collar: there is no neck, no post and no stand, and a clear empty gap separates the book from the collar (the collar is empty and open at the top). By default the FRONT COVER faces the viewer squarely, and the book is upright and level. The book can rotate freely in the air (turn, tilt, spin) to show emotion, but its default is front cover forward. Deep oxblood leather cover (#5E1F2B, shadow #3F141D) with brass corner guards and a thin brass border frame (#D29C5E); the thickness of the cream page edges (#F2E4D0) is visible at the sides so it clearly reads as a thick book. No letters, numbers or symbols on the cover or spine. Same robe, collar, gloves and shoes as the other Law versions. Build: medium height, average build with slightly rounded shoulders. Law does not hold another book in its hands.
```

- 법전 색(옥스블러드 가죽 + 황동 장식)은 법봉의 마호가니 · 판사복 네이비와 구분되도록 고른 **제안**이다. 결과를 보고 정한다.

### 9-6. POSE 블록 (자주 쓰는 것)

| 코드 | 문장 |
| --- | --- |
| `idle` (내로 · 남불) | `POSE — standing, front to slight three-quarter view, both hands on hips, a bright smile, looking at the viewer.` |
| `idle` (로) | `POSE — standing upright, front to slight three-quarter view, one hand holding a sealed kraft verdict envelope (#CF995A, red-brown wax seal) at the hip, the other hand open, palm up, presenting calmly.` |
| `deliver` (로) | `POSE — stepping half a step forward and holding the sealed verdict envelope out with both hands toward the viewer, calm and polite.` |
| `guide` | `POSE — pointing to one side with one hand as if introducing something, the other hand relaxed, friendly expression.` |
| `react` | `POSE — whole-body reaction: {surprised: jumping slightly with wide eyes and an open round mouth / happy: arms up with a big smile / sorry: shoulders down, eyebrows drooping, small mouth}.` |
| `think` | `POSE — one hand on the chin, head tilted slightly, thoughtful.` |
| `cheer` | `POSE — both fists up in front of the chest, cheering brightly.` |

로의 감정은 머리 물체로만 보인다: 저울 = 접시 기울기, 법봉 = 머리 각도, 법전 = 공중에서의 회전 · 펼쳐지는 정도(기본은 표지 정면, 아키텍처 3-2). **판결 전 화면용 로는 언제나 저울 · 수평.**

### 9-7. SCENE 블록

**`scene-cut`** — 장면 컷 · 스탠딩 (4:5)

```
SCENE — a single character, centered, full body from head to feet fully visible, 4:5 vertical composition with 15% empty margin on every side. Background: perfectly flat uniform solid green #00B140, no gradient, no floor, no shadow, no props in the background. Do not use green anywhere on the character or props.
```

**`group`** — 2 ~ 3인 컷 (4:5 또는 16:9)

```
SCENE — {characters} standing side by side on the same ground line, {left-to-right order}, the same scale relation as the references (Naero and Nambul the same height; Law (Scale) about 1.9 times, Law (Book) about 1.75 times, Law (Gavel) about 1.6 times their height), nobody in front of the others. Background: perfectly flat solid green #00B140. Full bodies visible with 10% margin. Do not use green on any character.
```

**`icon`** — 아이콘 (1:1)

```
SCENE — a face-only icon of {character}, the head filling about 70% of a 1:1 square, centered, simple and readable at 64 px. Background: flat solid green #00B140. Do not use green on the character.
```

**`sheet`** — 시트 (16:9)

```
SCENE — a clean character sheet on a plain newsprint background #ECE2D4, panels separated by space only (no frame lines, no labels), landscape 16:9.
```

**`reenact`** — 사건 재연 컷 (4:3 · 16:9)

```
SCENE — a calm newspaper-photo-like scene that shows only the situation and relationship of a real case: {situation}. Naero plays {role A} and Nambul plays {role B}, shown only through simple role outfits and props; their faces and colors stay the same. Plain newsprint background #ECE2D4 with a few simple props, {4:3 | 16:9} landscape. No victims' injuries, no weapons, no crime in progress, no blood, no bodies; nothing that hints at the verdict.
```

### 9-8. AVOID 블록

**공통** (모든 프롬프트 끝에 붙인다)

```
AVOID: text, letters, numbers, logos, watermarks, speech bubbles, angry or hostile faces or poses, sexualized or revealing depiction, gendered body features, cropped limbs, extra characters, black outlines, line art, drop shadows, ground shadows, 3D render, CGI, photo realism.
```

**캐릭터별** (공통 뒤에 이어 붙인다)

| 캐릭터 | 덧붙일 AVOID |
| --- | --- |
| `naero` | `a different fur color, a missing bandana, a human face, whisker lines` |
| `nambul` | `a nose, blank eyes without pupils, a separate face mask, flames anywhere below the head, sparks, embers` |
| 로 공통 | `faces, eyes, mouths, a human head, a neck, swinging or striking anything, a tilted scale before the verdict` |
| `law-gavel` | `a sound block or base, anything sticking out of the top of the gavel` |
| `law-book` | `a neck, a post or stand under the book, the book touching or resting on the collar, the spine facing the viewer by default, an open book with readable pages, letters or symbols on the cover, a box or folder shape, holding another book` |

> `authority` 프롬프트에서는 공통 AVOID의 `3D render, CGI, photo realism`을 그대로 둔다(사실 질감이지만 사진 · 3D는 아니다).

### 9-9. 완성 프롬프트 — 로(법전) 기준 시트 (제작 목록 A1)

`law-scale` · `law-gavel` 확정 시트를 함께 첨부한다.

```
Use the attached reference sheet of Law (Scale) and Law (Gavel) as the exact model for the body, robe, gloves, shoes, proportions, art style and sheet layout. Create a matching sheet for a third version, Law (Book). Do not redesign the body; change only the head.

ART STYLE — refined, warm 2D editorial illustration with soft painterly shading and believable materials:
- No black outlines and no line art; forms are defined by color and soft shading.
- The judge's robe is matte wool with gentle vertical folds; brass has a warm satin sheen; leather is smooth with soft highlights.
- Warm light from the upper left, a subtle lower-right shadow side on every form. No cast shadow, no ground shadow, no drop shadow, no glow.
- Mood: calm, dignified and trustworthy; never cold or threatening.

DEFORMATION — upright adult figure, about 4.5 heads tall counting the head object as one head; the head object is about 21 to 25% of the total height; a calm, upright, average adult build hidden by a broad ankle-length robe; no gendered body features; white gloves with fingers; plain dark shoes.

CHARACTER — "Law", a genderless AI judge with a floating law book as its head:
No face, no eyes, no mouth, no neck, no human head. The head is a thick, closed law book that FLOATS freely in the air above the collar: there is no neck, no post and no stand, and a clear empty gap separates the book from the collar (the collar is empty and open at the top). By default the FRONT COVER faces the viewer squarely, and the book is upright and level. The book can rotate freely in the air (turn, tilt, spin) to show emotion, but its default is front cover forward. Deep oxblood leather cover (#5E1F2B, shadow #3F141D) with brass corner guards and a thin brass border frame (#D29C5E); the thickness of the cream page edges (#F2E4D0) is visible at the sides so it clearly reads as a thick book. No letters, numbers or symbols on the cover or spine. Deep navy judge's robe (#253048) with a deep V neckline, two vertical front bands, wide bell sleeves and an ankle-length hem; warm-white collar and gloves (#F2E4D0); dark shoes. Signature prop: a sealed kraft verdict envelope (#CF995A) with a red-brown wax seal.

SHEET LAYOUT (same as the reference sheet):
- Top: front full body holding the envelope.
- Middle: turnaround — front, three-quarter, side and back views. The book keeps floating above the empty collar in every view; in the side view the gap under the book is clearly visible, and in the back view the back cover faces the viewer.
- Bottom: six poses — standing, one fist raised gently, walking, hand on chin thinking, holding the envelope, holding the envelope out. Emotion is shown only by the floating book: front cover forward, closed and level = calm; floating slightly higher and opening a little = happy; spinning half a turn with pages fluttering = surprised; turned to a three-quarter angle, half open and tilted = thinking; closed, sinking slightly lower and tilting down = sorry.
- Side panel: flat round color swatches of the main colors.

SCENE — plain newsprint background #ECE2D4, panels separated by space only (no frame lines, no labels), landscape 16:9.

AVOID: text, letters, numbers, logos, watermarks, angry or threatening poses, sexualized depiction, gendered body features, cropped limbs, extra characters, black outlines, line art, drop shadows, ground shadows, 3D render, CGI, photo realism, faces, eyes, mouths, a human head, a neck, a post or stand under the book, the book touching the collar, the spine facing the viewer by default, an open book with readable pages, letters or symbols on the cover, a box or folder shape, holding another book.
```

### 9-10. 완성 프롬프트 — 로(법전) 스탠딩 (제작 목록 A2)

`standing_law-scale.png` · `standing_law-gavel.png`(그리고 A1 결과가 있으면 그 시트)를 첨부한다.

```
Use the attached reference images of Law (Scale) and Law (Gavel) as the exact model for the body, robe, gloves, shoes, proportions, pose and art style. Draw Law (Book) in exactly the same standing pose. Change only the head.

CHARACTER — "Law", a genderless AI judge with a floating law book as its head. No face, no neck, no human head. The head is a thick, closed law book that FLOATS freely in the air above the collar: there is no neck, no post and no stand, and a clear empty gap separates the book from the collar (the collar is empty and open at the top). By default the FRONT COVER faces the viewer squarely, and the book is upright and level. The book can rotate freely in the air (turn, tilt, spin) to show emotion, but its default is front cover forward. Deep oxblood leather cover (#5E1F2B, shadow #3F141D) with brass corner guards and a thin brass border frame (#D29C5E); the thickness of the cream page edges (#F2E4D0) is visible at the sides so it clearly reads as a thick book. No letters, numbers or symbols on the cover or spine. Deep navy judge's robe #253048, warm-white collar and gloves #F2E4D0, dark shoes.

POSE — standing upright, front to slight three-quarter view; the right hand holds a sealed kraft verdict envelope (#CF995A, red-brown wax seal) at the hip; the left hand is open, palm up, presenting calmly. The book floats above the collar, front cover facing the viewer, closed and level (calm).

SCENE — a single character, centered, full body from head to feet fully visible, 4:5 vertical composition with 15% empty margin on every side. Background: perfectly flat uniform solid green #00B140, no gradient, no floor, no shadow. Do not use green anywhere on the character.

AVOID: text, letters, numbers, logos, angry or threatening poses, gendered body features, cropped limbs, extra characters, black outlines, line art, drop shadows, ground shadows, 3D render, CGI, photo realism, faces, eyes, mouths, a human head, a neck, a post or stand under the book, the book touching the collar, the spine facing the viewer by default, an open book with readable pages, letters or symbols on the cover, a box or folder shape, holding another book.
```

### 9-11. 완성 프롬프트 — 키 비교 시트 (제작 목록 A3)

네 스탠딩(법전이 있으면 다섯)을 첨부한다.

```
Use the attached reference images as the exact models of Naero, Nambul and the three versions of Law. Copy their designs, colors and art styles exactly (Naero and Nambul keep their flat mascot style; Law keeps its refined painterly style).

Draw a height comparison sheet: all characters standing in a row on one shared ground line, front view, neutral calm poses, left to right — Naero, Nambul, Law (Scale), Law (Gavel), Law (Book). Heights: Naero and Nambul are exactly the same height (Naero to the ear tips, Nambul to the flame tip). Measured to the top of the head object: Law (Scale) about 1.9 times Naero's height, Law (Book) about 1.75 times, Law (Gavel) about 1.6 times. Law (Gavel) is the shortest but has the broadest shoulders and sturdiest build; Law (Scale) is tall and straight; Law (Book) is in between. Faint, thin horizontal guide lines at the top of Naero and at the top of Law are allowed; no numbers or labels.

SCENE — plain newsprint background #ECE2D4, generous spacing, landscape 16:9.

AVOID: text, letters, numbers, logos, labels, angry faces or poses, gendered body features, cropped limbs, extra characters, black outlines, drop shadows, ground shadows, 3D render, photo realism, Nambul taller than Naero, all Laws the same height.
```
