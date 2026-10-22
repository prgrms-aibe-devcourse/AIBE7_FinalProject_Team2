---
name: 내Law남불
description: 판사의 책상 위에서 종이와 서류로 사건을 읽고 판단하는, 차분하고 묵직한 종이 질감의 판결 체험 서비스
colors:
  navy-ink: "#101d39"
  navy-accent: "#1f3563"
  navy-axis: "#2f5592"
  navy-mist: "#c9d1e3"
  navy-tint: "#eef1f7"
  wood-axis: "#a8652f"
  bronze-ornament: "#ba854b"
  bronze-label: "#86581f"
  bronze-pale: "#e6d2b5"
  paper-bright: "#fdfcfa"
  paper-soft: "#f7f3ee"
  paper-news: "#f4efe4"
  paper-strong: "#f1ebe4"
  paper-track: "#ebe5dc"
  paper-line: "#e2dad0"
  paper-line-strong: "#8b8174"
  white: "#ffffff"
  ink-text: "#151a24"
  ink-muted: "#5d5952"
  focus-blue: "#2f6fdb"
  danger: "#b3261e"
  danger-line: "#efb8b3"
  danger-tint: "#fcecea"
  caution: "#9a3d0a"
  caution-line: "#d4692a"
  caution-tint: "#fff1e7"
  judge-me: "#61538b"
  judge-ai: "#0b6762"
  judge-court: "#4f6526"
  factor-heavy: "#a03f35"
  factor-light: "#35704a"
  desk-wood: "#a8652f"
  desk-felt: "#1c2b4f"
  desk-felt-edge: "#14213f"
  desk-kraft: "#d6a468"
  desk-sheet: "#f7f3ee"
  desk-shadow: "#0f0a19"
  coffee-bg: "#ead8c0"
  coffee-ink: "#4a2c1c"
typography:
  display:
    fontFamily: "Noto Serif KR, AppleMyungjo, Batang, serif"
    fontSize: "34px"
  headline:
    fontFamily: "Noto Serif KR, AppleMyungjo, Batang, serif"
    fontSize: "28px"
    fontWeight: 900
    letterSpacing: "-0.02em"
  title:
    fontFamily: "Pretendard, -apple-system, Apple SD Gothic Neo, Malgun Gothic, sans-serif"
    fontSize: "22px"
  body:
    fontFamily: "Pretendard, -apple-system, Apple SD Gothic Neo, Malgun Gothic, sans-serif"
    fontSize: "15px"
    lineHeight: 1.75
  label:
    fontFamily: "Pretendard, -apple-system, Apple SD Gothic Neo, Malgun Gothic, sans-serif"
    fontSize: "13px"
  caption:
    fontFamily: "Pretendard, -apple-system, Apple SD Gothic Neo, Malgun Gothic, sans-serif"
    fontSize: "12px"
rounded:
  xs: "3px"
  sm: "4px"
  md: "6px"
  pad: "24px"
components:
  button-primary:
    backgroundColor: "{colors.navy-accent}"
    textColor: "{colors.white}"
    rounded: "{rounded.sm}"
    padding: "10px 18px"
    height: "44px"
  button-primary-hover:
    backgroundColor: "{colors.navy-ink}"
  button-secondary:
    backgroundColor: "{colors.white}"
    textColor: "{colors.ink-text}"
    rounded: "{rounded.sm}"
    padding: "10px 18px"
    height: "44px"
  button-secondary-hover:
    backgroundColor: "{colors.paper-soft}"
  nav-button:
    backgroundColor: "{colors.navy-ink}"
    textColor: "{colors.navy-mist}"
    padding: "10px 12px"
    height: "44px"
  nav-button-hover:
    textColor: "{colors.white}"
  mock-tag:
    rounded: "{rounded.xs}"
    padding: "0 5px"
---

# Design System: 내Law남불

> FE-76. 새 디자인을 만드는 문서가 아니라 **지금 코드(`frontend/src`)가 하는 일**을 적은 문서다. 코드와 다른 곳은 맨 아래 "현재 코드와 차이"에 따로 모았다. 제품의 목적 · 원칙 · 접근성 목표는 [`PRODUCT.md`](PRODUCT.md), 호버 규칙의 결정 배경은 [`docs/decisions/FE-76.md`](docs/decisions/FE-76.md)에 있다.
> 범위는 프론트 전체(책상 · 신문 지면 · 판결 체험 무대 · 성향 테스트 종이 · 마이페이지 · 커뮤니티 · 관리자 · 로그인). 로그인의 펠트 패드는 책상 계열의 한 사례일 뿐 기준이 아니다.

## Overview

**Creative North Star: "판사의 책상"**

화면은 모두 판사의 책상 위에 놓인 물건이다. 메인은 책상 그 자체이고, 신문 지면은 사건 목록, 서류철은 성향 테스트, 커피잔은 마이페이지, 노트북은 커뮤니티다. 판결 체험 무대와 로그인의 펠트 패드도 같은 책상에서 이어진다. 분위기는 **차분하고 묵직한 종이 질감**이다. 바탕은 크림 종이, 글자와 조작은 짙은 네이비, 장식은 브론즈 한 가지이고, 화면을 꾸미려고 색을 늘리지 않는다. 분위기는 색이 아니라 **물건의 은유**(종이 · 펠트 · 나무 · 크라프트)가 만든다. 새 시각 요소(표시 · 아이콘 · 장식)도 **판사의 책상 위에 실제로 있을 물건**(도장 · 클립 · 서류 · 명패)으로만 만든다. 책상 밖 은유는 쓰지 않는다(공개 카드 표시를 왕관으로 만들었다가 결재 도장으로 바꾼 사례).

무거운 주제를 다루므로 귀엽게 꾸미지 않는다. 캐릭터(내로 · 남불 · 로 합의부)는 판결 체험 무대와 결과처럼 정해진 자리에만 나온다. 피해 · 폭력 장면은 그리지 않고, 화난 표정도 없다. 판결의 방향을 색이나 연출로 미리 알리지 않는다(세 판결은 대등하다). 모바일과 데스크톱을 같은 비중으로 쓴다.

**Key Characteristics:**
- 브랜드 색은 종이 · 네이비 · 브론즈 세 가지다. 여기에 상태 색(오류 · 주의), 의미 색(세 판결 · 판단 요소 · 가중/감경 · 역할 이름표)과 책상 계열 · 마이페이지 커피 테마만 예외로 더한다. 빨강은 오류에만 쓴다.
- 토큰 3층 구조(팔레트 → 역할 → 화면). 화면 CSS에는 색 값을 직접 쓰지 않는다.
- 깊이는 책상 위 물건의 그림자로, 카드 중첩 없이 선과 면 색으로 구분한다.
- 연출은 건너뛸 수 있고, 연출 뒤에 정보를 숨기지 않는다.
- 색만으로 뜻을 전하지 않는다(기호 · 문장과 함께).

## Colors

종이(크림)가 바탕, 네이비가 글자와 조작, 브론즈가 장식이다. 토큰은 `style.css` `:root`에 3층으로 있다.

| 층 | 접두어 | 규칙 |
| --- | --- | --- |
| ① 팔레트 | `--palette-*` | 색 값(hex)은 여기에만 쓴다 |
| ② 역할 | `--color-*` | 화면 CSS는 이 이름만 쓴다. 특정 역할만 바꾸려면 그 줄만 고친다 |
| (별도) 책상 계열 | `--palette-wood-*` 등 · `--color-desk-*` · `--color-stage-*` | 3층과 별개인 네임스페이스. 책상 세계의 공용 색으로, `style.css`에서는 ④ 구역이다(S-01 책상 그림에서 시작해 지금은 여러 화면이 쓴다) |

### Primary
- **먹빛 네이비** (`#101d39`, `--color-heading` · `--color-banner-bg`): 로고 · 큰 제목 · 강조 값(형량) · 헤더 띠 · 주 버튼 호버.
- **서류 네이비** (`#1f3563`, `--color-accent`): 주 버튼 · 선택 표시 · 링크. 선택된 항목의 바탕은 **안개 네이비** (`#eef1f7`, `--color-accent-tint`).

### Secondary
- **놋쇠 장식** (`#ba854b`, `--color-decor`): 장식 선 · 로고 "Law". 작은 글자에는 쓰지 않는다.
- **놋쇠 라벨** (`#86581f`, `--color-label`): 섹션 머리 작은 라벨(신문 kicker).
- **옅은 놋쇠** (`#e6d2b5`, `--color-range-fill`): 권고 범위 막대. 어두운 면 위 포커스 링에도 쓴다.

### Neutral
- **밝은 종이** (`#fdfcfa`, `--color-bg`): 페이지 바탕. 카드 · 입력칸은 `--color-card` 흰색, 옅은 면은 **부드러운 종이** (`#f7f3ee`, `--color-surface`), 한 단계 진한 면은 `#f1ebe4` (`--color-surface-strong`), 신문지는 `#f4efe4` (`--color-paper`), 진행 막대 바탕은 `#ebe5dc` (`--color-track`).
- **종이 괘선** (`#e2dad0`, `--color-border`)과 **진한 괘선** (`#8b8174`, `--color-border-strong`): 구분선 / 입력칸 · 버튼 테두리.
- **잉크** (`#151a24`, `--color-text`)와 **옅은 잉크** (`#5d5952`, `--color-muted`): 본문 / 보조 글자.
- **포커스 블루** (`#2f6fdb`, `--color-focus`): 키보드 포커스 링 전용.

### 상태 색 (빨강은 오류에만)
- **오류**: 글자/주색 `#b3261e` (`--color-danger`), 테두리 `#efb8b3` (`--color-danger-border`), 바탕 `#fcecea` (`--color-danger-tint`), 어두운 면 위 글자 `--color-danger-on-dark`.
- **주의**(되돌릴 수 없음 · 권고 범위 밖): 글자 `#9a3d0a` (`--color-caution`), 테두리 `#d4692a`, 바탕 `#fff1e7`.

### 의미 색
- **세 판결**: 나 `#61538b` · AI `#0b6762` · 실제 재판부 `#4f6526` (`--color-judge-*`). 세 색은 밝기가 같다. 선 · 라벨에만 쓰고 바탕을 칠하지 않는다.
- **판단 요소**: 무겁게 `#a03f35` / 가볍게 `#35704a` (`--color-factor-*`), 바탕은 각각 `-tint`.
- **가중 ↑ · 감경 ↓** (`--color-up` · `--color-down`)은 항상 화살표 기호와 함께 쓴다.
- **4축 막대**: 엄격 쪽 남색 `#2f5592`(`--color-axis-strict`), 관대 쪽 갈색 `#a8652f`(`--color-axis-lenient`). 채도가 비슷한 두 색으로 양쪽을 구분만 하고 좋고 나쁨을 뜻하지 않는다. 갈색은 글자로는 대비가 모자라(3.3:1) 글자에는 한 단계 진한 `--color-axis-lenient-text`(`#8a4f22`)를 쓴다.
- 사건 유형(`--color-case-*`), 무대 이름표 역할(`--color-role-*`), 카카오 로그인(`--color-kakao`, 카카오 가이드 색이라 임의로 바꾸지 않는다)은 같은 방식의 역할 토큰이다.

### 책상 계열
- **책상 나무** (`#a8652f`), **펠트 패드** (`#1c2b4f`, 안쪽 테두리 `#14213f`), **크라프트 서류철** (`#d6a468`), **책상 종이** (`#f7f3ee`, 부드러운 종이와 같은 값), 그림자 **짙은 보라 검정** (`#0f0a19`) 등 물건 이름으로 짓는다(`--color-desk-*`).
- 어두운 면(펠트 · 나무) 위 글자는 밝은 종이색(`--color-desk-sheet`, `--color-desk-text`)으로 뒤집고, 주 버튼도 같이 뒤집는다. 포커스 링은 옅은 놋쇠.

### 스코프 테마 (마이페이지)
- 화면 하나가 분위기를 통째로 바꿔야 할 때는 **그 화면의 최상위 클래스 안에서만** 역할 토큰을 덮어쓴다. 의도된 설계이며 규칙이다.
- 지금 사례: `.my-page`가 `--color-bg` · `--color-card` · `--color-accent` 등을 **커피 팔레트**(바탕 `#ead8c0`, 진한 글자 `#4a2c1c`)로 덮어쓴다(커피잔이 마이페이지 입구, FE-44). 스코프 테마 안에서는 `--palette-*`를 직접 써도 되지만 같은 블록에 모아 쓴다.
- 헤더(`#route-header`)는 화면 밖이라 토큰을 못 받는다. 라우터가 `data-theme="coffee"`를 붙이고 `.site-header[data-theme="coffee"]`가 색을 따로 정한다.

### Named Rules
**The Role-Only Rule.** 화면 CSS(`pages/*`)는 `--color-*` 역할 토큰만 쓴다. 새 색은 ①에 팔레트를 먼저 넣고 ②에서 역할 이름을 붙인다. 확인: `grep -rnE "#[0-9a-fA-F]{3,8}\b|rgba?\(" frontend/src/pages --include="*.css"`. (예외: 스코프 테마)

**The Name-Fits-Use Rule.** 이름과 용도를 맞춘다. 어두운 면 위 글자에 테두리용 토큰을 빌려 쓰지 않고 전용 토큰을 만든다(예: `--color-danger-on-dark`).

**The Red-Means-Error Rule.** 빨강은 오류에만 쓴다. 판결이나 판단 요소의 색은 진하지 않은 레드 · 그린이고, 항상 문장이나 기호와 함께 쓴다.

**The Quiet Bronze Rule.** 브론즈는 장식 선과 로고 "Law" 정도로 드물게 쓴다. 작은 글자에는 쓰지 않는다.

## Typography

**Display Font:** Noto Serif KR (AppleMyungjo, Batang, serif) — 화면 제목 h1 · 로고
**Body Font:** Pretendard (-apple-system, Apple SD Gothic Neo, Malgun Gothic, sans-serif) — 본문 · 조작
**Article Font:** Nanum Myeongjo — 신문 기사 본문(S-02 지면). **Hand Font:** Nanum Pen Script — S-01 메모지 손글씨.

**Character:** 신문 제호와 판결문의 명조 제목, 읽기 편한 고딕 본문. 제목은 묵직하게, 본문은 또렷하게.

### Hierarchy
- **Display** (34px, `--text-display`): 큰 제목.
- **Headline** (900, 28px, `-0.02em`, `--text-title-1`): 화면 제목 h1(명조). 섹션 · 카드 제목은 본문 글꼴 그대로 둔다.
- **Title** (22px, `--text-title-2`; 18px, `--text-title-3`): 패널 · 섹션 제목.
- **Body** (15px, 행간 1.75, `--text-body`): 기본 본문. 행간은 1.6 ~ 1.75.
- **Label** (13px, `--text-small`) · **Caption** (12px, `--text-caption`): 보조 글자. 헤더 로고는 22px 900 명조, 목데이터 딱지는 11px.

### Named Rules
**The Korean Wrap Rule.** 한국어 줄바꿈은 `word-break: keep-all`, 긴 제목은 `text-wrap: balance`, 문장은 `text-wrap: pretty`, 긴 영문 · 주소는 `overflow-wrap: anywhere`.

**The 16px Input Rule.** 입력칸 글자는 16px 이상(그보다 작으면 iOS가 확대한다).

## Layout

본문 폭은 `--content-width` 1180px 가운데 정렬이고, 페이지 틀은 `1px solid var(--color-border)` 안에 `--color-bg`다. 읽기 중심 화면(마이페이지 등)은 안쪽 본문을 760px 안팎으로 좁힌다. 간격 눈금은 따로 정해져 있지 않다(아래 "현재 코드와 차이").

- **공통 헤더**: 라우터가 모든 화면 위(`#route-header`)에 붙이는 네이비 띠. `position: sticky; top: 0; z-index: 50`. 관리자는 자기 틀을 쓴다. 화면 CSS에 헤더 규칙을 다시 두지 않고, 특정 화면만 다르게 할 때는 `body:has(.xx-page) .site-header`로 감싸서 추가한다.
- 헤더 높이는 `main.js`가 재어 `--site-header-h`로 넣는다. `html { scroll-padding-top }`이 이를 써서 포커스 · 스크롤 이동이 헤더에 가리지 않는다.
- **반응형**: ≤ 720px에서 헤더가 두 줄(윗줄 로고 · 로그인, 아랫줄 메뉴 가로 스크롤). 분기점은 600 · 680 · 720 · 760 · 768이 섞여 있다.
- **터치**: 터치 대상은 44px 이상이 기본이다.
- z-index: 헤더 50, 화면 전환 연출 층 1000.

## Elevation & Depth

**물건이 놓인 그림자.** 깊이는 책상 위 물건이 만든다. 책상 계열(펠트 패드 · 서류철 · 편지 · 책상 물건)은 2단 그림자(가까운 짧은 것 + 먼 부드러운 것)로 놓이고, 일반 화면의 카드와 영역은 그림자 대신 **선(`--color-border`)과 면 색(`--color-surface`)**으로 나눈다. 그림자 색은 `--color-desk-shadow`를 `color-mix`로 투명하게 만든다.

### Shadow Vocabulary
- **패드 그림자** (`inset 0 0 0 3px var(--color-desk-pad-edge), 2px 4px 6px <shadow 35%>, 6px 18px 28px <shadow 30%>`): 로그인 펠트 패드. 안쪽 테두리가 펠트 가장자리를 만든다.
- **카드 들어올림** (`translateY(-3px)`): 마이페이지 카드. 그림자가 아니라 위치로 띄운다.
- 그 밖의 그림자는 화면마다 71종 가까이 흩어져 있다(눈금 미정).

### Named Rules
**The Placed-Object Rule.** 그림자는 "물건이 책상 위에 놓였다"는 뜻이다. 일반 카드를 떠 있는 것처럼 보이게 하려고 쓰지 않는다.

**The No-Nested-Cards Rule.** 카드 안에 카드를 두지 않는다. 구분은 선과 면 색으로 한다.

## Shapes

버튼 · 입력칸은 모서리를 작게(4px) 둔다. 헤더 안 계정 버튼은 6px, 목데이터 딱지는 3px(점선 테두리)이다. 책상 위 물건과 펠트 패드는 크게(로그인 패드 24px) 둥글려 물건 느낌을 낸다. 테두리는 1px 괘선이 기본이고, 장식 선은 브론즈다. 모서리 값은 34가지가 쓰이고 있어 눈금이 필요하다(아래 "현재 코드와 차이").

## Components

### Buttons
공통 버튼은 `style.css`의 `.primary-button` · `.secondary-button`이 기준이고, 화면 CSS 9곳에도 같은 값이 일부러 중복 정의돼 있다(라우터 · 404처럼 화면 CSS를 빌리지 않는 곳에서 쓰려고 `style.css`에도 있다).
- **Shape:** 작게 둥근 모서리(4px), 최소 높이 44px, 패딩 `10px 18px`, 글자 굵기 700.
- **Primary:** 서류 네이비 바탕 + 흰 글자. 호버는 먹빛 네이비.
- **Secondary:** 흰 바탕 + 잉크 글자 + 진한 괘선 테두리. 호버는 부드러운 종이. 단 **종이(책상 종이 = 부드러운 종이와 같은 색) 위**에서는 호버 색이 바탕과 같아 면이 사라져 보이므로, 호버에서 테두리 `--color-heading` + `kraft 16%` 틴트로 바꾼다(성향 테스트 `.personality-page`에 적용. 다른 종이 위 화면은 아직 전역 규칙 그대로).
- **어두운 면(펠트) 위:** 주 버튼은 종이색 바탕 + 펠트색 글자로 뒤집히고, 보조 버튼은 투명 바탕 + 종이색 글자 · 테두리가 된다.
- **눌림:** 전역 `scale .96` (`.12s`, `cubic-bezier(.2, .8, .2, 1)`).
- **비활성:** `opacity .6`, 호버 반응 없음.

### Navigation (헤더)
- 먹빛 네이비 띠 위에 로고(22px 900 명조, "Law"는 옅은 놋쇠)와 메뉴. 메뉴 버튼은 평소 안개 네이비 글자, 지금 화면(`aria-current="page"`)은 흰 글자 + 아래 2px 밑줄. 준비 중은 `aria-disabled="true"` + `opacity .6`.
- 로그인 버튼은 글자색 45% 테두리로 계정 동작임을 구분한다. 작은 화면에서는 두 줄이 된다.
- "연출" 스위치(`utils/motion.js`): 끄면 `<html data-motion="off">`가 되고 모든 CSS 애니메이션 · 전환 길이가 0이 된다.
- 마이페이지는 같은 헤더에 커피색만 덮어쓴 밝은 헤더를 쓴다.

### 글자 링크
호버에서 밑줄(`text-underline-offset: 3px`), 평소 밑줄 없음, 글자색은 그대로. **임시 확정**.

### 호버 · 눌림 (결정 7패턴)
기준은 "밝아짐"이 아니라 **요소 종류별 패턴**이다. 비교 페이지: [`docs/decisions/FE-76-hover-compare.html`](docs/decisions/FE-76-hover-compare.html).

| 패턴 | 규칙 | 대표 요소 |
| --- | --- | --- |
| **P1 버튼** | 배경색만 한 단계 변함. 크기 · 위치 그대로. 방향은 배경에서 멀어지는 쪽(흰 면 위는 어둡게, 펠트 · 어두운 면 위는 밝게). 눌림은 전역 `scale .96` | 공통 버튼 · 로그인 박스 · 카카오 버튼 |
| **P2 면 단위 항목** | 틴트 `kraft 16%`(`color-mix(in srgb, var(--color-desk-kraft) 16%, transparent)`). 테두리 · 글자 그대로 | `.cm-filter` · `.cm-action` · `.cm-attach` · `.my-row` · `.admin-menu-item` · `.is-pickable` · 지면 귀 |
| **P3 선택지** | 테두리 `--color-heading` + 틴트 `kraft 16%`, 둘이 함께 바뀜 | `.range-option` · `.answer-cell` · `.test-option` · `.tp-option` · `button.admin-card` |
| **P4 글자 링크** *(임시)* | 호버에서 밑줄(3px), 평소 밑줄 없음 | `.paper-link` · `.case-review-back` · `.cm-post-title a` 등 |
| **P5 헤더 메뉴** | 글자색만 변함(`--color-banner-text`). 마이페이지 커피 헤더는 `--color-text`(스코프 테마 예외) | `.nav-button` |
| **P6 비활성** | 호버 반응 없음, 커서 그대로. `opacity .6` 통일을 제안 | `.is-soon` · `[aria-disabled]` · `:disabled` |
| **P7 예외 연출** | 의도된 예외. 아래 표 | |

**공통 규칙:** 틴트는 뒤에 선이나 그림이 비치면 안 되는 요소(예: 성향 테스트의 `.test-dot`, 뒤에 가로선)에서는 투명(`transparent`) 대신 바탕색 위에 섞는다(`color-mix(…, var(--color-desk-sheet))`). 호버는 `@media (hover: hover) and (pointer: fine)` 안에서만 쓴다. 예외 연출은 `prefers-reduced-motion: reduce`에서 끈다. 전환 길이는 `.12s ~ .2s`, `ease-out`이 기본이고 예외 연출만 `cubic-bezier(.2, .8, .2, 1)`.

**P7 예외 연출** (물건의 은유를 지키려는 의도된 예외)

| 연출 | 동작 | 위치 |
| --- | --- | --- |
| 책상 물건 | `brightness 1.06 ~ 1.13` + 그림자 변화 | `landing.css` `.desk-object:hover > *` |
| 펜 → 엽서 연동 | 펜에 올리면 엽서도 반응. 키보드용 `:focus-visible` 짝 있음 | `landing.css` |
| 카드 들어올림 | `translateY(-3px)`, 눌리면 `1px`. 미리보기 · 상세에서는 끔 | `myPage.css` `.my-card` |
| 8막 편지 | `translate -0.4u` + 그림자 | `stage.css` |
| 폴더 탭 눌림 | `translateY(1px)` | `personality.css` · `verdict.css` |
| 별 확대 | `scale 1.12` | `caseReview.css` |
| 폴더 말림 | `folder-curl-wave` 애니메이션, 눌림 `scale .96` | `verdict.css` |
| 겹친 카드 앞뒤 바꾸기(성향 공유) | 같은 크기 카드 두 장(내로 앞 · 남불 뒤)을 겹쳐 놓고, 뒤 카드는 오른쪽 아래(`--peek-x` 50px · `--peek-y` 22px)로 비스듬히(5deg) 삐져나온다. 앞 · 뒤 어느 카드를 눌러도 앞뒤가 바뀐다(0.36s, `z-index`도 전환). 호버는 뒤 카드만 6px 더 나온다. 키보드 · 낭독기용 투명 버튼이 뒤 카드를 덮는다 | `personality.css` `.result-portrait-set` · `personalityParts.js` `portraitPair` |
| 철 돌리기 | 성향 테스트 검사지(가로) ↔ 결과지(세로). 제출 후 결과지는 눕힌 철을 돌려 세우고, 공유 화면의 "나도 해 보기"는 세운 철을 눕힌다. 떠나던 철의 자리 · 크기 · 스크롤 위치를 기억해 눈에 보이던 자리에서 시작한다(`folder-turn`, 1s). **모바일(≤ 720px)과 연출 끄기 · 동작 줄이기에서는 건너뛴다** | `personality.css` · `personalityParts.js` `canTurnFolder` |
| 종이 귀 접힘(성향 결과) | 호버 · 포커스에서 접힌 귀가 14px 커짐, 눌림 확대 없음. 판결 체험 폴더 말림과 모양이 비슷해 나중에 통일 예정 | `personality.css` `.result-turn-button` |
| 고리 강조 | 테두리 고리 `box-shadow` | `courtSketch.css` |
| 좋아요 눌림 | 아이콘 `scale .86` | `community.css` |
| 전역 버튼 눌림 | `scale .96` | `style.css` |
| 차트 점 툴팁 | JS `pointerenter`(마우스) · `pointerdown`(터치) | `myPage.js` |
| 브라우저 기본 툴팁 | `title` 속성 3곳 | `recommendedRangeBar.js` 2 · `stage.js` 1 |

### 포커스
키보드 포커스 링은 `outline: 3px solid var(--color-focus)`(37곳, 사실상 통일). 어두운 면 위에서는 옅은 놋쇠(`--color-range-fill`). 스크롤 영역(`.scroll-body`)도 키보드로 포커스되며 링이 보인다.

### 스크롤 영역
모든 스크롤 영역은 `components/scrollArea.js`를 쓴다. 기본 막대는 숨기고, 글 위에 겹치는 반투명 막대(5px)를 스크롤하는 동안만 보여 준다.

### 불러오는 중 · 오류 · 빈 상태
`.message-panel`(패딩 `44px 28px`, 본문 15px/1.75). 오류에는 다시 시도하는 버튼을 둔다. 404(S-14)는 가운데 480px 패널에 제목과 버튼 두 개. AI 판결 로딩 문구는 "AI 판결을 기다리는 중"처럼 생성 · 불러오기 양쪽으로 읽히게 쓴다(생성 중 · 계산 중처럼 단정하지 않는다).

### 성향 기준점 용어 (마이페이지)
**초심**(첫 성향 테스트) · **재심**(성향카드 다시 정하기, 1회) · **최근**(최근 체험 3건 평균). 그래프 가로축에 들어가므로 두 글자로 맞췄다. 버튼 이름은 "재심 청구하기".

### 결재 도장 (`.my-stamp`, `myPage.css`)
고른 카드에 "공개"를 찍는다. 잉크는 `--color-accent` 80%(네이비)이고 빨간 인주는 쓰지 않는다. 3px 이중 테두리, 모서리 4px, `rotate: -12deg`, 명조 900 · `--text-title-3`, 카드 오른쪽 아래. 장식이라 `aria-hidden`이고, 공개 여부는 버튼 이름(`aria-label` · `aria-pressed`)이 읽어 준다.

### 점선 빈 칸 버튼 (`.my-retake-slot`)
아직 없는 카드 자리를 카드와 같은 크기의 점선 칸으로 두고, 칸 전체를 "다음 행동" 버튼으로 쓴다(첫 테스트 하기 · 재심 청구 · 판결 체험하러 가기). `2px dashed --color-border-strong`, 모서리 14px(카드와 같음), 가운데에 ＋ 아이콘 · 제목 · 보조 문구. 호버는 P1(배경 `--color-surface`), 비활성은 `opacity .6`.

### 카드 고르기 (`.my-shown-pick`)
누르면 하나만 선택된다(`aria-pressed`). 호버는 P3(카드 테두리가 `--color-heading`으로).

### 성향 변경폭 막대 (마이페이지, `changeRow`)
영역 다툼 문법이다(경계선은 1px, 막대를 위아래로 쭉 가로지르고 경계 정중앙에 걸쳐 절반은 막대 · 절반은 빗금 몸통에 걸친다. 두 선이 겹치거나 붙으면 가운데 하나만 둔다). 화살표 · 빗금 띠에는 테두리가 없고(경계선만 남긴다) 막대가 비운 칸 안에 들어가 막대를 침범하지 않는다. 막대는 "지금" 함유량(왼쪽 엄격 남색 | 오른쪽 관대 갈색)이고, 처음 · 지금 경계에 같은 검정 세로선을 긋고, 그 사이(늘어난 몫)는 **늘어난 쪽 색 빗금**으로 칠한다(그 뒤 막대는 비운다). 비중 글자는 처음 → 지금이고 늘어난 쪽만 그 색 · 굵게다.
- **5%p 이상**: 머리가 있는 빗금 화살표(머리 끝이 지금 경계) + 오른쪽에 "+N%p 쪽 이름".
- **5%p 미만("거의 그대로")**: 글자는 "거의 그대로"로 두고, 처음과 지금이 **실제로 다르면** 머리 없는 **빗금 띠**를 그린다(`steadyBand`). 폭은 두 경계 사이에 비례하고, 표시 비중이 같은데 실제 값이 다르면 최소 **1%** 폭을 표시 경계 **가운데**에 두고, 띠의 양 끝에 경계선 둘을 긋는다(0.2%p만 달라도 표시 차이 1%p일 때와 같은 모양). 실제로 같으면(부동소수 오차 포함) 띠가 없고 글자는 **"변화 없음"**이다. 숫자 · 글자는 바꾸지 않고 **그리는 폭에만** 적용하므로 반반 특별법(`lenientPercent`, 정확히 반반이 아니면 50을 보여 주지 않는다)과 충돌하지 않는다. 방향으로 늘리지 않는 이유는 49 → 49에서 관대 쪽으로 1%p를 늘리면 끝이 50%에 닿기 때문이다.
- 1%는 데스크톱에서 약 5px, 모바일에서 약 2.4px이라 1% 띠는 윤곽선 안에 사선 하나가 보이는 작은 표식이다(2%p부터 빗금으로 읽힌다).

### 내 정보 수정 칸 묶음 (`.edit-field`, `myProfileEdit.css`)
- 한 칸은 **제목 → 조작 → 설명** 순이다. 제목은 `.edit-label`(700), 조작은 입력칸이나 버튼 줄(`.edit-actions`, 간격 8px), 설명은 `.edit-hint`(13px, 옅은 잉크)로 조작 **아래**에 둔다. 설명은 "~요."로 끝나는 문장으로 쓴다.
- 칸 안 간격은 6px, 칸 사이는 14 ~ 20px로 칸 안보다 넓다.
- 지금 할 수 없는 버튼(준비 중 · 이미 끝남)은 숨기지 않고 비활성(`opacity .6`, 호버 없음)으로 두고, 이유는 버튼 이름에 괄호로 붙이지 않고 설명에 쓴다. 끝난 동작은 이름만 바꾼다("첫 성향 테스트 완료" · "재심 완료").
- 다른 화면으로 가는 버튼은 "○○에서 보기 · 꾸미기"처럼 갈 곳을 이름에 넣는다.
- 폼 맨 아래 버튼 줄은 오른쪽 정렬, 주 버튼(저장)이 오른쪽 끝이고 취소는 그 왼쪽이다.

### 하나만 고르는 버튼 (`.edit-choice`)
보조 버튼 모양 그대로 나란히 두고, 고른 것은 `aria-pressed="true"` + 서류 네이비 테두리(안쪽 1px 더) + 안개 네이비 바탕이다(카드 꾸미기의 알약 선택과 같은 표시). 호버는 P3. 고를 카드가 없는 칸은 비활성이다. 내 정보 수정의 초심 · 재심 · 최근(공개 카드)이 쓴다.

### 겹친 성향카드 (`.edit-fan`, 내 정보 수정)
같은 크기 카드를 같은 높이에 두고 x만 `--fan-step`(56px, 380px 이하 36px)씩 어긋나게 겹친다. 고른 카드가 맨 왼쪽 · 맨 앞이고 결재 도장이 찍힌다. 뒤 카드의 보이는 부분을 눌러도 고를 수 있고, 호버는 P7 카드 들어올림(3px)이다.

### 성향 변화 흐름 차트 (마이페이지)
- **보이는 칸**: 최근 체험 10건까지. 테스트가 있으면 앞에 초심 칸을 하나 더 둔다. 테스트가 없으면 체험 10건만이다(첫 체험을 따로 붙이지 않는다).
- **기준점**(초심 · 재심 · 최근): 한 번의 판결이 아니라서 체험 선과 잇지 않는다. 마름모와 세로 점선으로 따로 찍고, 초심 · 재심은 속이 빈 마름모, 최근은 커피색이 찬 마름모다. 보이는 크기는 최근(칠 + 흰 윤곽이 안쪽을 먹음)과 초심 · 재심(속 빈 마름모의 진한 윤곽이 바깥으로 나옴)이 지름 약 9로 같게, 반대각선 절반을 최근 6 · 초심 · 재심 3.5(윤곽 1.5px)로 둔다(`trendGraph.js` `CURRENT_R` · `BASE_R`).
- **재심 위치**: 가로 칸을 만들지 않고 두 칸 사이 가운데에 얹는다(사람마다 시점이 달라도 체험 간격 · 기울기가 같게). "재심" 글자는 점선 위쪽, 그래프 위에 둔다(가로축 글자와 겹치지 않게).
- **간격 예외**: 재심이 초심 바로 뒤(체험 0건 뒤)이거나 최근 바로 앞(맨 끝)일 때만 그 사이를 0.7칸 더 띄운다(`CHART.anchorGap`). 나머지는 모두 같은 간격이다.

### 되돌릴 수 없는 동작 · 확인 상자
회원 탈퇴 · 기록 초기화 · 재심 1회 안내처럼 되돌릴 수 없는 동작은 주의 색(`--color-caution` · `--color-caution-border`)을 쓴다(빨강 금지). 버튼을 누르면 동의 체크가 있는 확인 상자가 열리고, 체크해야 실행 버튼이 켜진다(`dangerAction`, `myProfileEdit.js`). 입력칸은 글자 16px · 모서리 4px, 체크 줄 높이는 44px 이상이다.

### 빈 상태 안내 (옆 카드가 더 높을 때)
옆 카드 높이만큼 빈 자리가 생기면 안내 문구를 그 자리 가운데에 둔다(성향카드 화면의 판결 성향, `.my-detail.is-empty`).

### 목데이터 딱지 (`.mock-tag`)
목 값 옆에 붙는 점선 테두리 딱지(11px, 주변 글자색을 따라간다). 목 수치를 숨기지 않고 "목데이터"라고 표기한다.

### 색인 탭과 진행 책갈피 (성향 결과지)
- **색인 탭(결과 · 풀이)**: 파일철의 색인 탭처럼 윗변이 좁은 사다리꼴(`perspective(18px) rotateX(2.2deg)`, 위 모서리 10px)이고, 두 탭이 10px 겹쳐 꽂힌다. 글자는 사다리꼴과 따로 그려 일그러지지 않는다. 지금 탭은 철과 같은 색(`--color-desk-kraft`)으로 이어져 앞으로 나오고(위 하이라이트, 아래 1px이 철 테두리를 덮는다), 나머지는 한 겹 뒤(`--color-desk-kraft-back`, 아래쪽 그늘, 4px 낮게)다. 탭 줄은 철 위 가장자리에 붙어 틈이 없고, `z-index`를 두지 않아 뒤쪽 탭은 철 앞면 밑으로 들어간다. 호버는 P7 "폴더 탭 눌림"(뒤쪽 탭이 3px 올라옴).
- **진행 책갈피**(`.folder-bookmark`): 판결 체험 파일철의 책갈피와 같은 것으로, 펼쳐 본 가장 뒤 장의 탭 뒤에 끼워 둔 리본 + 얼굴이다. 마이페이지 "책갈피 설정"에서 고른 리본 색(`.bookmark-color-*`)과 캐릭터 얼굴(`selectedBookmarkFace`)을 쓴다. 잠금이 아니라 진행 표시라 앞 장으로 돌아와도 자리를 지키고, 옮겨질 때 0.24s 내려오며 나타난다(동작 줄이기에서는 바로 옮김). 장식이라 `aria-hidden`.
- **모양**: 폭 28px, 높이 36px. 서류철 상단(인덱스 탭 제외)보다 **6px만 솟고** 아래는 철 가장자리를 넘어 종이 위까지 30px 걸친다(`height` = 솟는 높이 + 걸치는 길이). 윗모서리는 위로 길쭉한 타원(`border-radius: 20% 20% 0 0 / 30% 30% 0 0`, 가로 5.6px · 세로 10.8px)이고, 아래는 V자 홈(`clip-path`)이다. 얼굴 아이콘은 지름 24px, 리본 위에서 3px 아래. 탭과 양쪽 8px 겹친다.
- 판결 체험의 책갈피는 `.verdict-page`에 묶인 별도 CSS라 모양이 조금 다르다(폭 22px · 아이콘 18px · 윗모서리 각짐). 통일 예정이다.

### 4축 막대 (`components/axisRow.js`)
성향 결과(S-16)와 마이페이지(S-17)가 함께 쓴다. 한 줄은 **[축 이름 | 양쪽 비중 글자 / 막대]**로, 축 이름은 막대 왼쪽(7em)이고 비중 글자는 막대 위다. 막대 줄은 20px로 묶어 막대(10px)가 축 이름과 같은 높이 가운데에 오고, 가운데 눈금(반반)은 위 · 아래로 4px씩 튀어나온다. 비중 글자는 평소 옅은 잉크 · 보통 굵기이고 더 기운 쪽만 그 쪽 색 · 굵게(700)다. 좁으면 양쪽 비중이 두 줄로 나뉘고 오른쪽 것은 오른쪽 끝에 붙는다. 체험 자료가 없는 축은 막대 대신 "체험 자료가 아직 없어요"를 두고 눈금도 뺀다. 결과 페이지의 4축과 관용 지수는 점선(`1px dashed`)으로 나뉜 별개 섹션이다(섹션 위아래 여백 32px). 남색 | 갈색 경계에는 **틈이나 선 없이 두 색이 바로 맞닿고**, 가운데 반반 눈금만 변경폭 경계선과 같은 검정 1px 선(막대 위아래로 4px 튀어나옴)이다(결과지 · 마이페이지 공통. 예전에는 결과지용 종이색 2px 틈이 있었으나 커피색 바탕에서 흰 줄로 튀어 뺐다).

### 성향 결과지 구성 (S-16 · 공유 화면)
내 결과는 **결과 · 풀이** 두 장(탭)이고, 두 장 모두 맨 위에 같은 모양의 캐릭터 카드를 둔다. 결과 장은 유형의 기본 캐릭터, 풀이 장은 같은 유형의 **다른 캐릭터**(이미지 번호 ±16)를 보여 준다. 결과 장 = 카드 · 4축 · 관용 지수, 풀이 장 = 카드 · 일상에서는 · 판결을 볼 때는 · 안내 · 버튼 줄(종이 아래에 고정, 데스크톱). 두 장을 오가는 **종이 귀**가 모서리에 있다(다음 장은 오른쪽 아래 "풀이 보기 →", 이전 장은 왼쪽 아래 "← 결과 보기"). 귀를 누르면 맨 위로 스크롤한다. 4축 · 관용 지수 · 설명은 각각 점선(`1px dashed`)으로 나뉜 섹션이다(위아래 여백 32px).
공유 화면(`/test/types/{shareId}`)은 개인 점수가 없어 내용이 짧으므로 탭 없이 한 장으로 이어서 보여 주고, 버튼("나도 해 보기")은 오른쪽 아래에 둔다. 카드는 위 P7의 "겹친 카드"다.

### 관용 지수 눈금
`components/toleranceScale.js`. 숫자 없이 "엄격함 ↔ 관대함" 양 끝 눈금 막대 위의 점. 높을수록 좋은 게 아니라는 안내를 함께 둔다.

### 연출 (모션)
기본 전환은 짧게(`.12s ~ .2s`, `ease-out`). 긴 연출(책상 → 신문, 커피잔 다이브, 페이지 넘김 등)은 해당 화면 CSS / JS에 있다. `data-motion="off"`는 길이를 0으로 만들되 끝 상태는 유지하고 `animationend` · `transitionend`도 바로 오게 한다. 기기의 "동작 줄이기"는 각 화면 CSS의 `prefers-reduced-motion`이 맡는다. 화면을 통째로 돌리거나 옮기는 큰 연출은 모바일(≤ 720px)에서 건너뛰는 것이 기본이다(철 돌리기가 사례, 위 P7 표).

## Do's and Don'ts

### Do:
- **Do** 화면 CSS는 `--color-*` 역할 토큰만 쓴다. 그림자 · 투명도는 `color-mix(in srgb, var(--토큰) N%, transparent)`로 만든다.
- **Do** 어두운 면 위 글자 · 주 버튼 · 포커스 링은 뒤집어 쓴다(종이색 글자, 옅은 놋쇠 포커스 링).
- **Do** 호버는 요소 종류별 패턴(P1 ~ P6)을 따르고, 예외는 P7 표에 이유와 함께 더한다.
- **Do** 색 뜻은 기호나 문장과 함께 전한다(가중 ↑, 감경 ↓, 오류 문구, 세 판결 라벨).
- **Do** 연출에는 건너뛰기 · 키보드 진행 · `prefers-reduced-motion`을 지키고, 연출을 건너뛰어도 판단에 필요한 정보는 모두 읽히게 한다.
- **Do** 화면 하나의 분위기를 바꿀 때는 그 화면의 최상위 클래스 안에서만 역할 토큰을 덮어쓴다.
- **Do** 새 표시 · 아이콘 · 장식은 책상 위에 있을 물건(도장 · 클립 · 서류 · 명패)으로 만든다.
- **Do** 되돌릴 수 없는 동작은 주의 색(`--color-caution`)으로 알리고, 확인 상자에서 동의를 체크해야 실행 버튼이 켜지게 한다.

### Don't:
- **Don't** 판결의 방향을 색이나 연출로 암시하지 않는다. 세 판결의 색은 선 · 라벨에만 쓰고 바탕을 칠하지 않는다.
- **Don't** 빨강을 오류 아닌 곳에 쓰지 않는다.
- **Don't** 브론즈(`--color-decor`)를 작은 글자에 쓰지 않는다.
- **Don't** 카드 안에 카드를 두지 않는다. 일반 카드에 책상 물건 같은 그림자를 입히지 않는다.
- **Don't** 이름과 용도가 다른 토큰을 빌려 쓰지 않는다.
- **Don't** 책상 밖 은유(왕관 등)로 표시를 만들지 않는다. 빨간 인주도 쓰지 않는다(빨강은 오류에만).
- **Don't** 피해 · 폭력 장면이나 화난 표정을 그리지 않는다. 사건 · 피해자를 희화화하지 않는다.
- **Don't** 호버를 `@media (hover: hover) and (pointer: fine)` 밖에 쓰지 않는다(터치에서 달라붙는다).

## 현재 코드와 차이

규칙(위)과 코드가 다른 곳이다. 2026-10-22, `develop` `7b97ceb` 기준 정적 점검이며 수치는 ±몇 개 오차가 있을 수 있다. 정리는 마감 직전 일괄로 한다(여러 팀원이 같은 시기에 화면 CSS를 고쳐 충돌 위험이 크다).

| 번호 | 항목 | 현황 | 상태 |
| --- | --- | --- | --- |
| 1 | 호버 가드 | 호버 규칙 59개 중 `@media (hover: hover)` 없는 것 22개 | 미정리 |
| 2 | P4 글자 링크 | 평소 밑줄 있는 5개(`login-link` · `footer-link` · `admin-link` · `cm-text-button` · `stage-skip`)가 아직 옛 방식 | 임시 확정, 하나씩 확인 |
| 3 | 호버 틴트 | `kraft 16%` 외에 4 ~ 5종이 흩어짐 | 미정리 |
| 4 | 글자 크기 | 토큰 122곳 대비 `px` 직접 207곳, 눈금(34/28/22/18/15/13/12)에 없는 14 · 11 · 10 · 16 · 17 · 24px 다수 | 미정리 |
| 5 | 간격 눈금 | 없음(안 쓰던 `--space-md`는 FE-76에서 삭제) | 눈금 결정 필요 |
| 6 | border-radius | 34형태 | 눈금 결정 필요 |
| 7 | box-shadow | 71종 | 눈금 결정 필요 |
| 8 | easing | 8종 | 미정리 |
| 9 | z-index | 22값 | 미정리 |
| 10 | 모바일 분기점 | 5종(600 · 680 · 720 · 760 · 768) | 모바일 점검 때 함께 |
| 11 | 버튼 최소 높이 | 44 · 40 · 38 · 36 · 34 · 32px 6종(44px이 다수) | 미정 |
| 12 | 비활성 `opacity` | .6 · .55 · .5 · .45 혼재, `:disabled`와 `aria-disabled` 혼용. 판결 `is-off`는 `.55` + 흑백 + `not-allowed` | `.6` 통일 제안, `is-off` 별도 확인 |
| 13 | `outline-offset` | 8종(2px 21곳, 4px 10곳 등) | 미정리 |
| 14 | 버튼 중복 정의 | `.primary-button` · `.secondary-button`을 9개 파일이 각자 정의 | 구조 결정 필요 |
| 15 | 직접 쓴 색값 | 6개, 대부분 마스크용 `#000`(`myPage.css` · `pageTurn.css`). `myPage.css`의 `--axis-strict` `#2f5592`, `--axis-lenient` `#a8652f`도 직접 값이다. 같은 값이 `--color-axis-*` 토큰으로 생겼고(성향 결과가 사용 중) 마이페이지는 아직 옛 값을 쓴다 | 마이페이지를 `--color-axis-*`로 바꾸고 직접 값 삭제 |
| 16 | 헤더 작은 버튼 | 로그인 36px 등 44px 미만 | 터치 대상 점검 때 확인 |
| 17 | 대화상자 배경 | `::backdrop`이 `oklch(0 0 0 / .55 ~ .6)`로 색 값을 직접 쓴다(마이페이지 다이얼로그 3곳: 꾸미기 · 컬렉션 확대 · 재심 확인) | 역할 토큰으로 바꾸거나 예외 이유를 적는다 |
| 18 | 공개 카드 저장 위치 | 공개 카드 · 카드 아래 성향 · 한 줄 소개를 서버(P-8 `PATCH /profile-settings`)에 저장한다(FE-81) | 정리됨 |
| 19 | 종이 계열 흰색 | 흰색 카드 `#ffffff` · 밝은 종이 `#fdfcfa`(페이지) · 부드러운 종이 `#f7f3ee`(옅은 면, 책상 종이)가 남아 있다. 책상 종이(`#f6f3ec`)는 부드러운 종이와 합쳤다. 카드 · 페이지 · 면은 계층을 만들어 합치지 않았다 | 계층을 합칠지 결정 필요 |
| 20 | 종이 위 보조 버튼 호버 | 성향 테스트만 kraft 16% + 진한 테두리로 바꿨고, 판결 체험 · 신문 지면 등 다른 종이 위 화면은 전역(흰색 → 부드러운 종이) 그대로라 호버가 안 보인다 | 다른 종이 위 화면에 확대할지 결정 |
| 21 | 진행 책갈피 중복 정의 | 판결 체험(`verdict.css`, `.verdict-page .folder-bookmark`)과 성향 결과지(`personality.css`, `.folder-bookmark`)가 같은 리본을 각자 정의하고 모양이 다르다(폭 22 · 28px, 윗모서리 각짐 · 타원) | 공용 컴포넌트로 합치고 모양 결정 |

FE-76에서 이미 정리한 것: 안 쓰는 토큰 3개 삭제(`--palette-navy-100` · `--color-on-case` · `--space-md`), `--color-desk-*` 주석을 실제 사용에 맞게 수정, `.login-error` 글자색을 `--color-danger-on-dark`로 변경.

## 이 문서를 고치는 법
- 코드를 바꿨는데 문서와 어긋나면 **코드가 맞고 문서가 틀린 것인지** 먼저 정한다. 새 규칙이면 문서와 코드를 같은 PR에서 고친다.
- "현재 코드와 차이" 항목을 정리하면 상태를 갱신하고, 규칙이 확정되면 위 해당 섹션으로 옮긴다.
- 프론트매터의 색 값은 `style.css` 팔레트와 같아야 한다. 값이 바뀌면 두 곳을 함께 고친다.
