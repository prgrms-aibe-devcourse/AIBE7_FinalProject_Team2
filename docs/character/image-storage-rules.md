# 이미지 적재 규칙 (폴더 · 이름 · 변환)

캐릭터 · 사건 이미지를 어디에 어떤 이름으로 두는지 한 곳에 모은 문서다. 규칙의 기준은 이 문서이고, 아래 문서는 자기 주제에 맞는 부분만 다룬다.

| 문서 | 다루는 것 |
| --- | --- |
| [character-asset-architecture.md](character-asset-architecture.md) 6장 | 프롬프트 블록 · 매니페스트 · 조합기 (이미지 경로는 이 문서를 본다) |
| [character-image-checklist.md](character-image-checklist.md) | 이미지 목록 · 진행 상태 · 검수 기록 |
| [personality-image-numbers.md](personality-image-numbers.md) | 성향 이미지 번호표 |
| 이미지 저장소 `NaeLawNamBul_Images`의 `README.md` | 저장소 소개 · 상태 (규칙은 이 문서로 연결) |

## 1. 이미지가 있는 곳 (3곳)

| 곳 | 무엇 | 형식 |
| --- | --- | --- |
| `~/Documents/NaeLawNamBul/character/` | 작업 폴더. 생성 직후 파일을 받아 이름을 붙이는 곳 | 투명 PNG |
| 공개 저장소 `NaeLawNamBul_Images` | 위 폴더의 정리본(원본 백업 포함). 폐기한 시안은 올리지 않는다 | 투명 PNG |
| 이 저장소 `frontend/public/images/` | 화면에 쓰는 변환본 | webp |

- 원본 PNG는 용량 때문에 이 저장소에 넣지 않는다.
- 새로 뽑은 파일은 먼저 작업 폴더의 `staging/`에 두고, 이름을 바꿔 아래 폴더로 옮긴다. `staging/`은 비워 둔다.
- 일부 PNG에는 AI 생성 출처 표시(C2PA)가 들어 있다.

## 2. 원본 PNG 폴더 구조 (작업 폴더 · 이미지 저장소 공통)

```
character/
├── standing/        스탠딩 일러스트
├── sheets/          시트(기준 · 연기 · 의상 · 데포르메 · 소품)
├── cuts/            화면에 쓰는 컷
│   ├── {naero|nambul}/base/{scene|cheek|cheek-slim|role}/   장면 · 볼따구 · 옛 볼따구 · 역할 컷
│   ├── {naero|nambul}/icon/                                  얼굴 아이콘
│   ├── {law-scale|law-gavel|law-book}/…                      로 컷(마스코트 버전은 mascot/)
│   ├── personality/                                          성향 결과 컷
│   ├── reenact/common/                                       공용 재연 컷
│   ├── reenact/case-{사건 키}/                               사건별 재연 컷
│   ├── reenact/background/                                   공용 배경(`bg-B01` ~ `bg-B16`, 불투명) · 폴백 합성용
│   ├── reenact/composite/                                    임시 합성 사건 사진(투명 컷 + 배경, 10/29) · 폴백
│   ├── trio/                                                 로 3인 합의 컷
│   └── compare/                                              내로 · 로울 · 남불 비교 컷
├── _original/       생성 직후 원본 백업(`original_` 접두사)
└── staging/         (작업 폴더에만) 방금 뽑은 파일을 이름 붙이기 전에 두는 곳
```

쓸 때는 `standing/` · `sheets/` · `cuts/`의 파일을 쓰고, `_original/`은 백업이다. 폐기본은 작업 폴더의 `discard/`에 두고 저장소에는 올리지 않는다.

## 3. 파일 이름 규칙

```
{대상}_{종류}_{세부}.png
```

폴더를 보지 않아도 이름만으로 무슨 이미지인지 알 수 있고, 저장소 안에서 이름이 겹치지 않아야 한다. 이름은 영어 소문자와 하이픈이다(번호는 숫자).

| 구분 | 값 |
| --- | --- |
| 대상 | `naero` · `nambul` · `law-scale` · `law-gavel` · `law-book` · `trio` · `compare` · `all`(여럿이 나오는 시트) · `reenact-common-{property\|body\|life}` · `reenact-case-{사건 키}` · `personality-{번호}_{캐릭터}` |
| 종류 | `standing` · `sheet` · `cut` · `cut-cheek` · `cut-cheek-slim` · `cut-role-{judge\|prosecutor\|defense\|actor\|case-{사건 키}}` · `cut-mascot` · `icon` |
| 세부 | 컷: `{장면}-{감정}` / 시트: `reference` · `acting` · `role-outfits` · `wardrobe` · `deform-*` / 재연 컷: `{headline\|origin\|conclusion}` / 성향: 밈 이름 |

- 장면: `idle` · `guide` · `react` · `read` · `cheer` · `deliver` / 감정: `neutral` · `happy` · `surprised` · `thinking` · `sorry` · `cheer`. 안내 · 리액션 · 성향 · 아이콘에는 화난 표정을 쓰지 않는다. 재연 · 연기 · 시트는 극적인 표정을 허용한다(폭력 · 조롱은 금지).
- 이미지 안에는 글자 · 숫자 · 로고를 넣지 않는다.
- 끝의 `_sample`은 화면 확인용 샘플이라는 뜻이다. 정식본이 나오면 뗀다.
- 예: `naero_standing.png` · `nambul_cut_guide-neutral.png` · `naero_icon_face-cheer.png` · `personality-06_naero_pinky-promise.png` · `reenact-common-property_cut_headline.png` · `naero_cut-role-judge_idle-neutral.png`

### 3-1. 재연 컷 (공용 · 사건별)

공용과 사건별 모두 **자리 3개**를 쓴다.

| 자리 | 이름 | 뜻 | 보이는 곳 |
| --- | --- | --- | --- |
| ① | `headline` | 대표 컷 | 1막 기사 사진 |
| ② | `origin` | 발단(관계 · 배경) | 변호인 측 증거 화면 |
| ③ | `conclusion` | 결론(사건 뒤) | 검사 측 증거 화면 |

- 공용: `cuts/reenact/common/reenact-common-{property|body|life}_cut_{headline|origin|conclusion}.png`. 범죄 분류 3종(재산 · 신체 · 생명) × 자리 3장 = 9장.
- 사건별: `cuts/reenact/case-{사건 키}/reenact-case-{사건 키}_cut_{headline|origin|conclusion}.png`. 폴더 이름과 파일 이름의 사건 키가 같다.
- **사건 재연 컷은 10/29부터 배경까지 포함해 뽑는 것이 기본이다**(프롬프트 가이드 9-7 `BG-scene`). 배경 포함본은 위 `reenact-case-{사건 키}_cut_{자리}.png` 이름 그대로 `case-{사건 키}/`에 둔다.
- 10/29 이전에 만든 63장은 **투명 컷**이다. 이것은 같은 이름으로 `case-{사건 키}/`에 있고, 배경과 합성한 임시본(`composite/`)이 사건 사진으로 쓰인다(8장). 배경 포함본이 나오면 투명 컷은 `_original/`에 `_v1`로 보존하고 같은 이름의 배경 포함본으로 교체한다.
- 사건 키는 **DB 사건 번호**다(예: `case-5/reenact-case-5_cut_headline.png`). 10/29에 21건(번호 2 ~ 22)을 임시 키에서 DB 번호로 바꿨다. 번호가 아직 없는 새 사건은 임시 키(`R01` 형식)로 두고, 번호가 정해지면 폴더 · 파일 이름 · 원본 백업 이름을 함께 바꾼다. 웹 주소가 이 번호로 정해지므로 **사건 번호는 환경(로컬 · 운영)마다 같아야 한다**(BE-141). 사건 번호와 실제 사건의 대응표는 공개 문서에 적지 않는다.
- 생성 도구가 컷에 `-1` `-2` `-3` 같은 번호를 붙여 내보내면 `-1` = headline, `-2` = origin, `-3` = conclusion으로 옮긴다.
- 옛 이름은 쓰지 않는다: `sub-1` → `origin`, `detail-1` → `conclusion`.

### 3-2. 공용 배경 (투명 컷 아래에 깔리는 배경, 폴백용)

`cuts/reenact/background/bg-B{01~16}.png`. 캐릭터가 없는 배경이고, 가운데 아래쪽을 비워 두며 바닥선은 이미지 높이 80% 근처에 둔다. 배경은 불투명 PNG다(알파 없음, 1670~1672 × 940~942). 번호와 장소 대응은 작업 시점의 프롬프트 문서를 따른다. 합성본(`composite/`)의 이름은 `reenact-case-{사건 키}_composite_{headline|origin|conclusion}.png`다. 배경 포함본으로 뽑는 컷은 배경을 따로 만들지 않고, 같은 장소를 맞추려면 이 배경을 참조 이미지로 첨부한다.

## 4. 원본 백업 (`_original/`)

- 이름은 `original_` + 정식 파일 이름이다. 예: `original_reenact-case-5_cut_headline.png`
- 같은 이미지를 다시 뽑으면 `_v1` `_v2`를 붙인다. 예: `original_personality-13_nambul_whiteboard_v3.png`
- 정식 파일을 만들 때(이름 붙여 옮길 때) 같은 파일을 `_original/`에도 복사한다. 탈락본은 `_original/`에만 두기도 한다.
- 배경을 지우는 등 후처리한 파일로 교체하면, 처리 전 파일을 `_original/`에 `original_{이름}_v1.png`로 남긴다.

## 5. 형식

| 종류 | 비율 · 원본 크기 | 배경 |
| --- | --- | --- |
| 장면 컷 · 성향 컷 | 4:5, 1122 × 1402 | 투명 |
| 아이콘 | 1:1, 1254 × 1254 | 투명 |
| 시트 | 16:9, 1672 × 941 (±1 px 허용) | 투명(10/15부터). 그 전에 만든 시트는 신문지색 `#ECE2D4` |
| 사건 재연 컷 | 16:9, 1672 × 941 (±1 px 허용) | **배경 포함이 기본**(10/29부터). 10/29 이전 63장은 투명 컷 |
| 합의 · 비교 컷 | 16:9, 1672 × 941 | 투명 |
| 스탠딩 | 로 971 × 1619, 내로 · 남불 1672 × 941 캔버스 | 투명 |

- 새로 받은 이미지는 옮기기 전에 알파 채널과 비율을 확인한다.
- 그림자 · 테두리 · 등장 움직임은 이미지에 넣지 않고 CSS로 준다. 색은 `style.css` 토큰만 쓴다.

## 6. 웹 변환본 (`frontend/public/images/`)

변환은 투명 webp 품질 85다. 웹 이름은 원본과 다르고, 아래 대응으로 바꾼다.

| 원본 PNG | 웹 webp | 크기 |
| --- | --- | --- |
| `reenact-common-{분류}_cut_{자리}.png` | `cases/common-{분류}-{reenact\|origin\|conclusion}.webp` | 1200 × 675 |
| `reenact-case-{사건 키}_cut_{자리}.png`(배경 포함본) · `reenact-case-{사건 키}_composite_{자리}.png`(임시 합성본) | `cases/case-{사건 id}-{reenact\|origin\|conclusion}.webp` | 1200 × 675 |
| `{캐릭터}_cut_{장면}-{감정}.png` | `characters/scene/{캐릭터}_{장면}-{감정}.webp` | 800 × 1000 |
| `{캐릭터}_cut-cheek_*.png` | `characters/cheek/{캐릭터}_idle-{sorry\|thinking}.webp` | 800 × 1000 |
| `{캐릭터}_cut-role-{역할}_idle-neutral.png` | `characters/roles/{캐릭터}_{역할}.webp` | 높이 560 |
| `{로}_cut-mascot_*.png` | `characters/mascot/{로}_{idle\|think\|deliver}.webp` | 높이 560 |
| `{trio\|compare}_cut-mascot_*.png` | `characters/mascot/` | 1600 × 900 |
| `*_standing.png` | `characters/standing/*.webp` | 높이 800 |
| `personality-{번호}_{캐릭터}_{밈}.png` | `characters/personality/{번호 01~32}.webp` | 4:5 |
| `{캐릭터}_icon_face-{감정}.png` | `characters/icon/generated/{캐릭터}_face-{감정}.webp` | 128 × 128 |

- 웹 폴더에 남아 있는 `adult/` · `law/` · `cheek-slim/` · `deform/`은 지금 화면이 읽지 않지만 사용 예정이거나 보존용이다. 지우지 않는다.
- 성향 이미지는 4글자 유형 코드를 이름에 쓰지 않고 번호만 쓴다(화면 비노출 원칙). 한 번 정한 번호는 바꾸지 않는다.
- 사건 id는 사건 DB 번호다. 사건 키를 id로 바꾸는 시점은 3-1을 본다.

## 7. 새 이미지를 들일 때 순서

1. 생성한 파일을 작업 폴더 `staging/`에 둔다.
2. 크기 · 알파 채널 · 비율을 확인한다(5장).
3. 3장 규칙으로 이름을 바꿔 2장의 폴더로 옮긴다. `staging/`은 비운다.
4. `_original/`에 `original_` 이름으로 복사한다(4장).
5. 이미지 저장소에 올린다. 폐기본은 올리지 않는다.
6. 화면에 쓸 것은 6장 대응으로 webp로 변환해 `frontend/public/images/`에 넣고, `character-image-checklist.md`의 진행 상태를 갱신한다.

## 8. 임시 사건 사진 (10/29)

사건 재연 컷 21건 × 3장 중 배경 포함본이 아직 없는 것은 **투명 컷에 공용 배경을 합성한 임시본**을 사건 사진으로 쓴다.

- 만드는 법: 컷 짝마다 정한 배경(번호표는 프롬프트 문서)을 1672 × 941로 맞춰 깔고 컷을 얹는다. 발이 화면 안에서 떠 있는 컷(캐릭터 하단이 화면 맨 아래에서 떨어진 컷)만 발밑에 옅은 그림자를 넣는다. 합성 뒤 어색한 컷은 그림자를 뺀 버전을 쓴다(10/29: 63장 중 39장은 그림자 있음, 24장은 그림자 없음).
- 위치: `cuts/reenact/composite/reenact-case-{사건 키}_composite_{자리}.png`(PNG), 웹 변환본은 작업 폴더의 `web/cases/case-{사건 키}-{reenact|origin|conclusion}.webp`(1200 × 675, 품질 85).
- 사건 사진으로 올리는 방법: 사진은 관리자 API로 올린다(대표 사진 A-13-3 · 발단 · 결론 A-13-4, 역할 `ORIGIN` · `CONCLUSION`). 대표 컷(`headline`)이 대표 사진, `origin` · `conclusion`이 발단 · 결론 사진이다.
- 교체: 배경 포함본이 나오면 3-1 순서대로 교체하고, 그 컷의 `composite/` 파일과 웹 변환본을 지우거나 새 컷의 변환본으로 바꾼다.
- 이 임시본은 최종 품질이 아니다. 합성 특유의 게임 같은 느낌과 문틀 · 그림자 어긋남이 있어 배경 포함본으로 바꾸는 것이 목표다.
