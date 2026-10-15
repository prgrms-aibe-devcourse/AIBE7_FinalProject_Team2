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
| v0.7 | 2026-10-14 | A ~ C 1차 생성분 검수 · 상태 갱신(4-1 검수 기록), 원본 폴더 구조 정리(`cuts/` · `discard/sheets/` · `discard/standing/`), 컷 4:5 맞춤 · 이름 정리 |
| v0.8 | 2026-10-14 | D1 성향 16장 검수 · 상태 갱신, `cuts/personality/personality-01_naero_rule-binder.png` ~ `16.png`로 이름 · 폴더 정리 · 4:5 맞춤, 재생성 7장 표시 |
| v0.9 | 2026-10-14 | E1 공용 사건 재연 컷 · F1 연기 시트 · F2 역할 의상 시트 검수 · 상태 갱신, `sheets/acting/` · `sheets/role-outfits/` · `cuts/reenact/common/` 정리 |
| v0.10 | 2026-10-14 | F3 데포르메 대표 시트 6장 검수 · 상태 갱신(Claude 검수 + 사용자 자체 검수 메모), `sheets/deform/` 정리 |
| v0.11 | 2026-10-14 | **전체 목록 점검**(2-1) — 빠진 이미지 추가: A5 캐릭터별 기준 시트(1인 1장), B6 `idle-neutral`(나머지 4캐릭터), B7 `cheer`, B8 `read-thinking`, D2 성향 반대편 캐릭터 16장(번호표 변형 규칙 `-2`, v0.25에서 이미지 번호 `01 ~ 32`로 바뀜), F1 로봉 연기 시트, G6 로 데포르메 시트. 추가분 생성 프롬프트(5장) |
| v0.12 | 2026-10-14 | **사용자 결정 반영** — F2 역할 의상 확정(검사 버건디 앞단 띠 · 변호인 정장 · 판사 흰 가발 · 사건 역할 셔츠 + 청바지 · 반다나 제거, 남불도 같이), F3 란마 강하게 지시해 재생성, F3 · B4 볼따구 통과. F2 · 란마 프롬프트 갱신(5장). 이미지 한 장씩 정리한 작업 목록 문서 추가(`Claude outputs/`) |
| v0.13 | 2026-10-14 | A5 캐릭터별 기준 시트 4장 검수 · 정리 — 내로 · 남불 · 로울 통과(`sheets/{코드}_sheet_reference.png`, 옛 2인 시트는 `discard/sheets/`), 로봉은 떡대 수정 필(`staging/`에 1차본). 새 이미지 · 수정 이미지는 `staging/` 폴더를 임시로 씀 |
| v0.14 | 2026-10-14 | 로봉 1인 시트 수정본 확인(떡대 체형 반영, 폭 약 29% 넓음) — 통과. 시트 기준으로 **로봉 스탠딩 재작업(R4)** 추가. 파일 정리 · 문서 정리는 이후 |
| v0.15 | 2026-10-14 | R4 로봉 스탠딩 재작업 통과(10% 와이드 프롬프트 → 20% 와이드 요청, 실측 어깨 +19% · 로브 +10 ~ 15%)로 `law-gavel_standing.png` 교체, 로봉이 나오는 컷의 연쇄 재작업 정리(C1 idle · think, C2 로봉 deliver, A3 재합성) |
| v0.16 | 2026-10-14 | A5 로봉 1인 시트 수정본 통과(떡대 체형, 정면 폭/높이 0.62 · 로울 대비 +29%) → 기준 시트 5장(`{코드}_sheet_reference.png`) 모두 정리 완료. 키 비교 합성 v3를 새 로봉 스탠딩으로 재합성 |
| v0.17 | 2026-10-14 | 신규 생성분 검수 · 정리 — F1 로봉 연기 시트 · B7 응원 2 · B8 기록 읽기 2 · D2 성향 반대편 14장 통과(정식 위치로 정리, 원본 `_original/`), D2 02-2 · 06-2는 다시 뽑기. 장면 문구 변경: 02 편지 쓰기(수첩 제거), 06 새끼손가락 크게, 11 허그 느낌 |
| v0.18 | 2026-10-14 | 새 시트 · 아이콘 검수(R2 · F1 로북 · F2 내로 · B5 `cheer` · `sorry` 통과, F2 남불 · F3 란마 보류), **남불 판사 가발은 불꽃을 가린 채 가발만 보이게**로 결정(3-4 · 5-1), 옛 시안은 `discard/` 폴더로 이동 |
| v0.19 | 2026-10-14 | 통과 시트 · 아이콘 정식 반영 — R2 로북 시트 · F1 로북 연기 시트 · F2 내로 역할 의상 · F3 란마 2장(사용자 판단 통과) · B5 `cheer` · `sorry` 4장을 정식 위치로 교체하고 이전 버전은 `discard/`로 이동. 남은 시트 수정은 F2 남불(가발 처리) 1장 |
| v0.20 | 2026-10-14 | C1 합의 컷 2 · C2 로봉 발표 컷 · D1 5장(02 · 03 · 09 · 10 · 11) · D2-02 재생성본 통과 · 정식 반영(옛 버전 `discard/`), `trio-idle` 초록 잔여 후처리 제거. 06(샤카 모양 · 손이 몸과 겹침) · 13(사용자 메모) · D2-06(손가락 4개 · 주황 손) 재생성 프롬프트 보강 |
| v0.21 | 2026-10-14 | 남은 재생성분 통과 · 정식 반영 — F2 남불(판사 가발이 불꽃을 가림) · D1-06 · D1-13 · D2-06(4손가락 중 새끼손가락만 세운 손이 팔에 연결, 남불 몸 길이 기준 −5%) · 공용 재연 컷 3장. 옛 버전과 실패 시도는 `discard/`(`attempts/`)로 이동. 사건별 재연(E1)만 남음 |
| v0.22 | 2026-10-14 | B6 `idle-neutral` 4장 변환 완료(내로 · 남불 · 로봉 · 로북, 스탠딩 → 4:5), `discard/` 루트의 시도 이미지 2장을 `discard/attempts/`로 정리 |
| v0.23 | 2026-10-14 | C3 `trio-thinking` 후처리 완료(마스코트를 줄여 로울 1.92배 · 같은 바닥선). 남은 후처리 · 변환 없음 |
| v0.24 | 2026-10-14 | **폴더 · 파일 이름 변경**: `discard/` → `discard/`, `staging/` → `staging/`, 모든 원본 파일(정식 · 폐기 · `_original`)을 폴더 없이 역할을 알 수 있는 이름으로 변경(263개, 해시 확인), 문서 경로 갱신 |
| v0.25 | 2026-10-14 | **성향 이미지 번호를 `01 ~ 32`로 통일**(번호표 v0.4) — 변형 접미사 `-2` 폐지, 내로 `01 ~ 16` · 남불 `17 ~ 32`. D1 · D2 이름 · 설명 갱신. 화면에는 유형마다 기본 캐릭터 하나만 보여 주고(하드코딩), OG는 유형별 그 이미지로 고정 |
| v0.26 | 2026-10-15 | **10/15 이관 결정**(COMMON-35) — 생성은 GPT Pro 팀원(김현중, gpt image 2.0)이 맡고 지금 이미지는 유지(재생성은 최종 디테일 때), 1장 생성 방식 · 배경(모두 투명, 신문 느낌은 후보정) 갱신. A6 마스코트 로 6장 추가, G2 · G6을 이관 묶음으로 당김, G7 · G8(데포르메별 자세 · 표정, 캐릭터 카드) 추가. 사건 1 첫 샘플은 E1-공용 세트 2로 옮기고 E1 사건 1은 새 의상 · 극적 연출로 새로 생성 |
| v0.27 | 2026-10-15 | **추가 생성 목록 H** 정리(COMMON-35) — 1순위: H0 데포르메 묶음 시트(11종을 한 장에, 느낌 보고 결정) · H1 의상 시트(로 포함 5장) · H2 마스코트 로 컷 · H3 역할 의상 정식 컷(지금은 시트에서 잘라 씀), 2순위: H4 사건별 재연 + 사건 배역 컷, 3순위: H5 ~ H9(G1 · G7 · G8 등을 옮김). 파일 이름 규칙에 `cut-mascot` · `cut-role-{역할}` · `sheet_deform-lineup` · `sheet_wardrobe` 추가 |

> 형식 · 이름 규칙 · 검수 기준은 [캐릭터 에셋 아키텍처](character-asset-architecture.md)가 기준이다(시트 2-2 · 장면 · 감정 코드 3-2 · 상황 4-1 · 크기 6-5 · 검수 7-2). 이 문서는 **무엇을 몇 장 만들고 어디까지 했는지**만 적는다.
> 캐릭터 설정은 [캐릭터 프로필 · 세계관](character-profile-worldview.md), 성향 이미지 번호는 [성향 이미지 번호표](personality-image-numbers.md)가 기준이다.
> 프롬프트는 [이미지 생성 프롬프트 모음](character-image-prompts.md) 9장(v2)을 쓴다. 항목별 예시는 이 문서 5장.
> 원본 PNG는 레포 밖 `~/Documents/NaeLawNamBul/character/`에 둔다. 웹용 webp만 `frontend/public/images/characters/`에 넣는다. 원본은 팀원 확인용 공개 레포 `NaeLawNamBul_Images`에 올렸다(에셋 아키텍처 7-3 #5, 갤러리 페이지가 이 레포를 읽는다). 폴더 구조는 아래와 같다(10/14).
>
> - `sheets/` 시트(현재 사용). 기준 시트는 **캐릭터마다 1장** `{naero · nambul · law-scale · law-gavel · law-book}_sheet_reference.png`(A5, 아키텍처 2-1) — 5장 모두 10/14 정리 완료(로북은 R2 수정본 통과). 두 캐릭터가 함께 든 옛 2인 시트는 `discard/sheets/`로 옮겼다
> - `staging/` 새로 뽑았거나 고칠 이미지를 두는 **임시 폴더**(검수 뒤에 정식 위치로 옮기고 이름을 규칙대로 바꾼다). 지금은 비어 있다
> - `standing/` 스탠딩 5장(기준)
> - `discard/` 폐기한 이전 시안(`discard/sheets/` 옛 시트 · 합성본, `discard/standing/` 봉투를 든 옛 스탠딩 · 탈락 후보, `discard/cuts/` · `discard/icons/` · `discard/personality/` 옛 컷, `discard/attempts/` 실패한 재생성 시도). 파일 이름은 `discard_` 접두사 + 실패 이유. 쓰지 않고 `NaeLawNamBul_Images` 레포에도 올리지 않는다
> - `cuts/{캐릭터}/base/{캐릭터}_cut_{장면}-{감정}.png` · `cuts/{캐릭터}/icon/{캐릭터}_icon_face-{감정}.png` — 아키텍처 6-4와 같은 구조(웹 변환 전 PNG). 로 합의 컷은 `cuts/trio/`, 내로 · 남불 · 로 비교 컷은 `cuts/compare/` 성향 결과 컷은 `cuts/personality/personality-{01 ~ 16}_{캐릭터}_{밈}.png`(기본 · 반대편 캐릭터가 각각 한 장씩, 번호 · 밈은 번호표 기준) 사건 재연 컷은 `cuts/reenact/case-{caseId}/`(아키텍처 6-4), **사건 이미지가 없을 때 대신 쓰는 공용 컷**은 `cuts/reenact/common/{headline · sub-1 · detail-1}.png`. 연기 시트는 `sheets/acting/{naero · nambul · law-scale · law-gavel · law-book}_sheet_acting.png`, 역할 의상 시트는 `sheets/role-outfits/{naero · nambul}.png` 데포르메 대표 시트는 `sheets/deform/{naero · nambul}-{ranma · cheek · ppg}.png`
> - `_original/`(`character/` 바로 아래) 생성 직후 원본 114장 보관(`original_` 접두사, 재생성 이력은 `_v1` `_v2`). 다른 폴더에 둔 정식 파일의 사본이다. 원본 백업은 공개 레포 `NaeLawNamBul_Images`에도 올렸다(10/14, 새 이름 구조로 반영 완료). 컷 PNG는 4:5(1122 × 1402)로 캔버스를 맞췄다(그림은 늘리거나 줄이지 않고 투명 여백만 추가)

> **파일 이름 규칙 (10/14, 폴더 없이 파일 이름만으로 역할을 알 수 있게)**: `{대상}_{종류}_{세부}.png` — 대상 `naero` · `nambul` · `law-scale` · `law-gavel` · `law-book` · `trio` · `compare` · `reenact-common` · `personality-{번호}`, 종류 `standing` · `sheet`(`reference` · `acting` · `role-outfits` · `deform-{ranma|cheek|ppg}`) · `cut` · `cut-cheek` · `icon`. 예: `naero_standing.png`, `law-gavel_sheet_reference.png`, `nambul_cut_guide-neutral.png`, `naero_icon_face-cheer.png`, `personality-06_naero_pinky-promise.png`, `trio_cut_idle.png`. 폐기는 `discard_` 접두사 + `_v1` · `_tryN-이유`, 원본은 `original_` 접두사. 옛 이름 → 새 이름은 `rename-map`(`Claude outputs/character/rename-map_2026-10-14.csv`). 웹용 webp 이름 규칙(`{장면}-{감정}.webp` · `01.webp`)은 바뀌지 않았다. **10/15 추가**: 마스코트 로 컷 `{law-*}_cut-mascot_{장면}-{감정}.png`(로 셋 함께는 `trio_cut-mascot_{장면}.png`), 역할 의상 컷 `{naero · nambul}_cut-role-{judge · prosecutor · defense · actor}_{장면}-{감정}.png`, 사건 배역 컷 `{naero · nambul}_cut-role-case-{caseId}_{장면}-{감정}.png`, 데포르메 묶음 시트 `{캐릭터}_sheet_deform-lineup.png`, 의상 시트 `{캐릭터}_sheet_wardrobe.png`.

**상태 표기**: ⬜ 대기 · 🟨 생성 중 · 🟦 검수 중 · ✅ 완료

> **지금 이미지는 그대로 쓴다(10/15).** 지금까지 만든 이미지도 gpt image 2.0으로 뽑아 품질이 충분하므로 다시 뽑지 않고, **없는 이미지만** 새로 만든다. 다시 뽑는 것은 최종 디테일을 잡을 때 한다(그때 시트 · 스탠딩 몇 장을 품질별로 뽑아 비교해 품질을 정한다). ✅는 "지금 이미지 기준 완료"라는 뜻이다.
> **생성은 GPT Pro 팀원(김현중)이 맡는다(10/15).** 묶음별 작업판 겸 검수 보고서(HTML)로 주고받는다 — 이번 묶음은 `Claude outputs/character/gpt-handover_2026-10-15/`(커밋 안 함, 항목 · 프롬프트는 `items.js`). 생성 설정은 1장 "생성" 행.

---

## 1. 공통 규칙

| 항목 | 규칙 |
| --- | --- |
| 장면 컷 | 4:5, 투명 webp 800 × 1000(품질 85), 원본 투명 PNG 2048 × 2560. 파일 이름 `{장면}-{감정}.webp` |
| 아이콘 | 1:1, 투명 webp 128 × 128(64 px로 줄여서 확인), 원본 512 × 512. 파일 이름 `face-{감정}.webp` |
| 재연 컷 | 16:9(필요하면 4:3), webp 1600 × 900 · 1200 × 900. **투명 배경**에 캐릭터와 필요한 소품만, 신문 느낌은 후보정, 흑백 필터로 확인. 10/14 이전 컷은 신문지색 배경 |
| 시트 | 16:9, **투명 배경**(10/14 이전 시트는 신문지색 `#ECE2D4`). 웹에는 넣지 않는다 |
| 생성 배경 | **모든 이미지를 투명 배경으로 바로 뽑는다**(10/15, 프롬프트 모음 9-7 `BG`). 투명 출력이 안 되면 크로마 그린 `#00B140`으로 뽑아 배경을 지운다(그때는 캐릭터 · 소품에 초록을 쓰지 않는다) |
| 생성 (10/15) | gpt image 2.0, 품질은 기본 medium(토큰 사용량에 맞춰 조절), 다른 이미지의 기준이 되는 이미지는 high. 프롬프트 1개당 1장, 다시 뽑는 것은 검수 지시마다 1회(파일 이름 뒤 `_r1` · `_r2`). 첨부는 공개 레포 `NaeLawNamBul_Images`의 정식 파일만(폐기 자료 금지). 비율이 다르게 나오면 캔버스를 맞춘다 |
| 글자 | 이미지 안에 글자 · 숫자 · 로고를 넣지 않는다(말풍선 · 이름표는 웹 UI) |
| 판결 전 화면 | 로는 저울 1인 · 수평(`neutral`)만 |
| 봉투 | 로는 **빈손이 기본**. 판결문 봉투는 `deliver`(발표 · 로딩 끝)에서만 든다(10/14) |
| 표정 | 화난 표정(`angry`)은 만들지 않는다 |
| 키 | 내로 120 · 남불 120 · 로 저울 230 · 법봉 190 · 법전 210(같은 바닥선, 아키텍처 2-5). **마스코트 로**(A6)는 로울 180 · 로북 170 · 로봉 160 |
| 로 이름 | 로울 = 저울(`law-scale`) · 로봉 = 법봉(`law-gavel`) · 로북 = 법전(`law-book`). 파일 · 폴더에는 코드를 쓴다. 판결 화면에서는 이름 대신 "AI 판사"(이미지에는 원래 글자를 넣지 않는다) |

---

## 2. 목록

### A. 기준 (먼저)

| # | 이미지 | 수량 | 형식 | 비고 | 상태 |
| --- | --- | --- | --- | --- | --- |
| A0 | 내로 · 남불 기준 시트, 로(저울 · 법봉) 기준 시트, 스탠딩 4장 | 6 | 시트 · 투명 PNG | `*_sheet_reference` · `*_standing`(아키텍처 2-1). 시트 2장은 **두 캐릭터가 한 장**에 있어 A5에서 1인 시트로 다시 만든다(내로 · 남불 시트의 키 칸은 남불 1.5배로 확정값과 다르고 남불 턴어라운드는 3칸, 로 시트는 모든 칸에 봉투) | ✅ (시트는 A5로 교체 완료) |
| A1 | 로(법전) 기준 시트 | 1 | 시트 | 저울 · 법봉 시트와 같은 칸(정면 · 3/4 · 측면 · 뒷면 · 자세 6종 · 색 견본). 머리는 **목 · 기둥 없이 옷깃 위 공중에 떠 있는** 두꺼운 법전, 기본은 표지 정면. 손에는 법전을 들지 않는다. **10/14 채택: GPT 버전** `law-book_sheet_reference.png`(1672 × 941) — 줄 반복 없음 · 모든 칸에서 책이 떠 있음 · 마네킹 목 없음 · 구두 검정 OK. 후속(=R2): 칸 배치가 피라미드형(1 / 4 / 6)이라 저울 · 법봉 시트와 다름, 자세 6종에 "아쉬움" 칸 없음, 색 견본 13개 중 밝은 파랑 · 주황 · 테라코타 3개가 캐릭터에 쓰이지 않음. 이전 생성본은 `discard/sheets/` | ✅ (R2 수정본으로 교체) |
| A2 | 로(법전) 스탠딩 | 1 | 투명 PNG | 1차(봉투 든 자세)는 `discard/standing/discard_law-book_standing_holding-envelope.png`. **R1 결과 `law-book_standing.png`(971 × 1619)가 정식** — 빈손 · 옷깃 안 비어 있음(마네킹 목 없음) · 검정 구두 · 두껍고 거의 정사각형인 와인색 법전(시트와 같은 모양). 10/14 새로 만든 `a2.png`는 책이 세로로 긴 얇은 판형 · 밝은 빨강이고 마네킹 목이 남아 탈락(`discard/standing/`) | ✅ |
| A3 | 키 비교 시트 | 1 | 시트 | 내로 120 · 남불 120 · 저울 230 · 법봉 190 · 법전 210, 같은 바닥선. 기존 내로 · 남불 시트의 키 칸(남불 1.5배)은 쓰지 않는다. 생성본(`v1` · `v2` · 새로 뽑은 `A3.png`)은 비율이 계속 어긋남 — 새 생성본 실측(내로 = 1): 남불 1.17 · 로울 2.52 · 로봉 2.13 · 로북 2.12(목표 1 · 1.92 · 1.58 · 1.75), 로 셋 구두가 바닥선 아래. **스탠딩 5장을 목표 비율로 줄여 합성한 `all_sheet_height-compare.png`로 대체**(R3). 생성본은 `discard/sheets/`. **10/14 재합성 완료**: 로봉 스탠딩이 떡대 체형으로 바뀌어 새 스탠딩 5장으로 다시 합성(높이 비율은 그대로, 옛 합성본은 `discard/sheets/discard_all_sheet_height-compare_v3-slim-gavel.png`) | ✅ (합성, 10/14 새 로봉으로 재합성) |
| A4 | 로 스탠딩 **빈손 버전** (저울 · 법봉 · 법전) | 3 | 투명 PNG | 10/14 결정: 스탠딩에서 봉투 제외. **로울 · 로봉 2장 검수 통과** → 새 파일이 `law-scale_standing.png` · `law-gavel_standing.png`, 기존 봉투본은 `discard/standing/`로 보관. 새 로울은 971 × 1619(기존 봉투본은 1122 × 1402)라 화면 배치 확인 필요, 구두가 짙은 갈색(로북은 검정). **로북은 R1 결과(`law-book_standing.png`)를 쓴다** — 새로 뽑은 `a4-3`은 R1과 거의 같아(픽셀 9% 차이) `discard/standing/`로 보관. **(10/14 로봉 스탠딩은 R4로 떡대 체형 재작업, 위 R4 행)** | ✅ |
| R1 | 로북 스탠딩 수정 | 1 | 투명 PNG | A2 1차 결과를 고침: 봉투 빼기 · 옷깃 안 마네킹 목 없애기 · 구두 검정. **`law-book_standing.png`가 R1 결과**(프롬프트 모음 9-13) | ✅ |
| R2 | 로북 기준 시트 수정 | 1 | 시트 | A1 GPT 버전을 고침: 칸 배치를 저울 · 법봉 시트와 같게, 자세 6칸에 "아쉬움" 칸, 캐릭터에 안 쓰인 색 견본 제거. **10/14 수정본 통과** → `sheets/law-book_sheet_reference.png`(옛 GPT 버전은 `discard/sheets/`). 같은 배치(1 / 4 / 6) · 아쉬움 칸 포함 · 옷깃 안 마네킹 목 없음 · 색 견본 6개, 봉투는 마지막 칸만. 사소: 아쉬움 칸 책과 옷깃 사이 틈이 좁음 | ✅ |
| A5 | **캐릭터별 기준 시트** (내로 · 남불 · 로울 · 로봉) | 4 | 시트 | 10/14 결정: 기준 시트는 **한 장에 한 캐릭터**(아키텍처 2-1 "캐릭터마다 1장"). 로북은 이미 1인 시트(A1 · R2). 칸은 정면 전신 · 턴어라운드 4칸(앞 · 3/4 · 옆 · 뒤) · 표정 6종(마스코트) 또는 자세 6종(로) · 그 캐릭터 색만 있는 색 견본, **키 비교 칸 없음**, 로는 빈손 기본 + 봉투는 한 칸만. **10/14 검수**: **내로 · 남불 · 로울 3장 통과** → `sheets/{naero · nambul · law-scale}_sheet_reference.png`로 정리, 옛 2인 시트(`discard_naero-and-nambul_sheet_reference_two-person.png` · `discard_law-scale-and-law-gavel_sheet_reference_two-person.png`)는 `discard/sheets/`. 사소한 점: 내로 · 남불 `thinking` 표정의 눈썹이 짜증 나 보임(화난 표정 금지 규칙의 경계선), 내로 `sorry`가 눈 감은 표정, 로울 3/4 뷰가 정면과 비슷. **로봉**: 1차본은 떡대 체형이 안 나와(폭/높이 0.44 · 로울 0.48과 같음) 수정했고, **수정본 통과**(0.62 · 로울 대비 +29%, 어깨 · 가슴 · 소매 두꺼움, 4칸 · 6칸 · 봉투 한 칸 OK) → `sheets/law-gavel_sheet_reference.png`, 1차본은 `discard/sheets/discard_law-gavel_sheet_reference_v1-slim-body.png`. 사소: 색 견본 5개(봉투 · 밀랍색 없음), 걷는 칸 다리 비율 | ✅ 4장 |
| R3 | 키 비교 v2 | 1 | 시트 | 비율 보정(로울 1.92 · 로북 1.75 · 로봉 1.58배), 로북 떠 있게, 숫자 없이, 전원 빈손. **합성 `all_sheet_height-compare.png`로 대체**(내로 · 남불 · 로 셋이 목표 비율 그대로, 같은 바닥선, 글자 없음 · 가이드선만). 한계: 머리 끝 높이에 맞춰 줄여서 로봉의 떡대(어깨 폭) 체형은 덜 드러남. 체형까지 살리려면 모델로 다시 생성 | ✅ (합성) |
| R4 | 로봉 스탠딩 재작업 | 1 | 투명 PNG | 10/14 로봉 1인 시트 수정본에서 떡대 체형이 확정되어 지금 스탠딩을 넓혀 다시 뽑음. 프롬프트는 문장형 지시를 잘 못 따르는 생성 AI에 맞춰 **수치를 직접 넣는 방식**(10%로 쓰고 사용자가 20%로 조정, 작업 목록 문서 R4 이력). **검수 통과**: 어깨 +19% · 로브 아래 +15% · 중간 +10%(지금 대비 실측), 전체 약 10 ~ 19% — 머리 크기 · 포즈 · 구두(검정) · 로브 형태 유지, 초록 잔여 0. 사용자 판단: "실사체에서는 이 정도가 적당". 새 파일이 `standing/law-gavel_standing.png`, 옛 슬림본은 `discard/standing/discard_law-gavel_standing_slim-body.png`. **연쇄**: 아래 C1 · C2 · A3 · B6 · F1 로봉이 새 체형 기준으로 바뀜 | ✅ |
| A6 | **마스코트 로** 스탠딩 3 · 기준 시트 3 (로울 · 로봉 · 로북) | 6 | 투명 PNG · 시트 | 10/15 추가: 로를 내로 · 남불과 같은 **2등신 마스코트 그림체**로(기본 `adult` 로는 그대로). 키 로울 180 · 로북 170 · 로봉 160(내로 · 남불 120), 체형 차이 유지. 품질 high. 스탠딩 → 그 스탠딩을 첨부해 시트. 파일 `{law-*}_standing_deform-mascot.png` · `{law-*}_sheet_deform-mascot.png`. 프롬프트 모음 9-16. G6의 첨부 기준 | 🟨 이관 |

- A1 · A2가 끝나면 아키텍처 2-4(색) · 2-5(프로필) · 5-1(IDENTITY)에 법전을 추가한다. 10/14 A2 실측: 표지 `#722C30` ~ `#621E25`(제안 `#5E1F2B`보다 밝은 와인색), 황동 모서리 `#CFAA75` 근처.
- A3 다시 뽑을 때 추가 문장: `Law (Book) floats above the collar with a clear gap; it never rests on the shoulders.` · `Make Naero and Nambul about 8% larger so the ratios match exactly.` · 숫자 라벨 없이 뽑고 나중에 얹는다.

### B. 우선 컷 (화면에 바로 쓰는 것)

| # | 캐릭터 | 컷 | 수량 | 쓰는 곳 | 상태 |
| --- | --- | --- | --- | --- | --- |
| B1 | 로(저울) | `idle-neutral` · `deliver-neutral`(봉투) | 2 | S-01 메인 · S-02 신문 지면 · 로딩 끝 전달 | ✅ 검수 통과 · PNG 정리(`cuts/law-scale/base/`), webp 변환 전. `idle-neutral`은 A4 변환 대신 새로 생성한 컷(`law-scale_standing.png`와 별개). 저울 수평 · 두 손 봉투 · 목 없음 OK, `deliver`는 "반 걸음 앞으로"가 약함 |
| B2 | 내로 (`mascot`) | `guide-neutral` · `guide-happy` · `react-surprised` · `react-happy` · `react-sorry` | 5 | 안내 · 리액션 | ✅ 검수 통과 · PNG 정리(`cuts/naero/base/`) · 4:5 맞춤. 후속: 컷마다 캐릭터 크기 차이(웹 변환 때 기준 정함), `react-happy` 눈 감은 웃음 확인 |
| B3 | 남불 (`mascot`) | B2와 같은 5종 | 5 | 안내 · 리액션 | ✅ 검수 통과 · PNG 정리(`cuts/nambul/base/`) · 4:5 맞춤. 후속: `react-surprised` 캐릭터가 작음, `react-happy` 눈 감음(남불은 동공이 보이는 눈이 정체성이라 확인) |
| B4 | 내로 · 남불 (`cheek`) | `idle-sorry` · `idle-thinking` | 4 | 로딩 · 빈 상태 · 오류 | ✅ **10/14 사용자 판단으로 통과** · PNG 정리(`cuts/{naero,nambul}/base/{naero,nambul}_cut-cheek_`). 한계로 기록: 몸통이 "작고 둥근 만두 + 돌기 팔다리" 지시와 다름(`idle-thinking` 둘은 가는 다리로 서 있음), 남불 `idle-thinking`은 입을 활짝 웃음 |
| B5 | 내로 · 남불 (`ppg` 아이콘) | `face-{neutral · happy · surprised · thinking · sorry · cheer}` | 12 | 토스트 · 완료 피드백 | ✅ 10/14 `cheer` · `sorry` 4장 재생성본 통과(`cheer`는 별 하이라이트 + 반짝이로 `happy`와 64 px에서 구분, `sorry`는 땀방울이 읽힘 — 남불은 흐림), 아이콘 12장 모두 통과. 머리 비율은 칸의 76 ~ 85%(규격 약 70%, 허용). 이전 `cheer` · `sorry`는 `discard/icons/` |
| B6 | 내로 · 남불 · 로봉 · 로북 | `idle-neutral`(**스탠딩을 4:5로 변환만**, 생성 없음) | 4 | S-17 프로필 기본 자세(4-1 `profile`) · 컬렉션 · 빈 상태 기본 · 결과 화면 합의 전후 단독 컷. 로울은 B1에 있음 → 5캐릭터 모두 `idle-neutral`을 갖춘다. 로봉은 **10/14 새 체형 스탠딩**을 변환한다 | ✅ 10/14 변환 완료(4:5 · 1122 × 1402, 내로 · 남불은 스탠딩 bbox를 캔버스 높이 약 72%로 가운데 배치, 로봉 · 로북은 971 × 1619 캔버스에 투명 여백을 더해 맞춤, 로봉은 새 떡대 스탠딩). 로울은 B1의 `idle-neutral`을 그대로 씀. 한계: 내로 · 남불 스탠딩은 입을 벌린 밝은 웃음이라 `neutral`보다 `happy`에 가까움 |
| B7 | 내로 · 남불 (`mascot`) | `cheer-cheer` | 2 | S-06 판결 입력 응원(4-1 `cheer`). **특정 형량 쪽 응원처럼 보이면 안 됨**(빈손, 가리키지 않음) | ✅ 10/14 검수 통과 · `cuts/{naero,nambul}/base/{naero,nambul}_cut_cheer-cheer.png`(4:5). 작게 뛰며 두 주먹 · 빈손 · 가리키지 않음 |
| B8 | 남불 · 내로 (`mascot`) | `read-thinking`(서류철 · 돋보기) | 2 | S-04 기록 함께 읽기(4-1 `companion`). 기본은 남불, 내로는 바꿔 쓰기용 | ✅ 10/14 검수 통과 · `…/read-thinking.png`(4:5). 서류철 + 돋보기, 사소: 눈썹이 날카로워 보임 |

### C. 판결 결과 화면 (S-07 · S-09)

| # | 이미지 | 수량 | 비고 | 상태 |
| --- | --- | --- | --- | --- |
| C1 | 로 3인 합의 컷 | 2 | 셋이 나란히(`idle`) · 고개를 맞댐(`think`). 같은 바닥선, **키는 각자 체형대로**(로울 230 · 로봉 190 · 로북 210, 아키텍처 2-5), 서열 없음. 이견(2 : 1)처럼 보이는 연출 금지(프로필 · 세계관 5-3). 16:9 투명 PNG(1672 × 941), `cuts/trio/trio_cut_idle.png` · `trio-think.png`. **`idle`**: 배치 · 키(로봉 0.79 · 로북 0.89, 목표 0.83 · 0.91) OK, 로봉 · 로북 손가락 사이에 **초록 잔여 픽셀 92개**(배경 제거 때 남음) → 제거 필요. **`think`**: 포즈 OK, 셋이 서로를 향해 기울이지 않고(로북은 바깥쪽으로 기울어짐), 로북 키가 로봉과 거의 같음(0.86, 목표 0.91). **10/14 재생성본 통과**: 새 떡대 로봉 기준 `trio-idle`(키 로봉 0.82 · 로북 0.89, 초록 잔여는 후처리로 제거) · `trio-think`(서로를 향해 기울임, 사소: 로봉 · 로북 높이 0.86으로 거의 같음). 옛 버전은 `discard/cuts/`. 이전 연쇄 메모:(`idle`은 초록 잔여 문제도 재생성으로 해결, 작업 목록 문서 C1-idle · C1-think) | ✅ 10/14 재생성본 통과 |
| C2 | 로(법봉) · 로(법전) `deliver-neutral` | 2 | 발표자 컷. 저울은 B1. 4:5(1122 × 1402), `cuts/law-gavel/base/` · `cuts/law-book/base/`. 반 걸음 앞으로 두 손으로 봉투 · 법봉 머리 수평 · 법전 닫힌 채 떠 있음 · 목 없음 · 검정 구두 OK. 두 컷의 키 비율은 0.97(목표 0.905)이라 변환 때 크기 기준으로 맞춘다. **연쇄(10/14)**: 로봉 `deliver-neutral`은 10/14 새 체형으로 재생성해 통과(옛 슬림본은 `discard/cuts/`)(작업 목록 문서 C2-gavel) | ✅ |
| C3 | S-09 비교 `trio-thinking` | 1 | 내로 왼쪽 · 로(저울) 가운데 · 남불 오른쪽. 셋 중 하나를 더 크게 · 앞에 두지 않는다. 16:9(1672 × 941), `cuts/compare/compare_cut_trio-thinking.png`. 내로 · 남불 같은 키 · 정체성 OK. **10/14 후처리 완료**: 마스코트 둘을 약 0.87배로 줄여 로울이 내로 · 남불의 1.92배(817 / 426), 같은 바닥선(발 y 876) — 옛 버전은 `discard/cuts/`. 이전 후속: 로울이 내로의 **1.69배**(목표 1.92), 로울 구두가 마스코트보다 36 px 아래(같은 바닥선 아님) → 로울을 약 1.14배 키우고 바닥선을 맞춘다 | ✅ 10/14 후처리 완료 |

### D. 성향 테스트 (S-15 · S-16)

| # | 이미지 | 수량 | 비고 | 상태 |
| --- | --- | --- | --- | --- |
| D1 | 성향 결과 **기본 캐릭터** 16장(웹 이름은 번호표의 이미지 번호: 내로 `01 ~ 16` · 남불 `17 ~ 32`) | 16 | 장면 컷 4:5, `mascot`. 내로 8 · 남불 8(번호표 "캐릭터" 열). 밈 장면은 [번호표](personality-image-numbers.md). 화난 표정 금지, 4글자 코드 · 글자 넣지 않음. 10/14 16장 생성 · 검수: 캐릭터 배정 · 소품 · 글자 없음 · 화난 표정 없음 OK, `cuts/personality/personality-01_naero_rule-binder.png` ~ `16.png`로 정리(4:5, 1122 × 1402). **통과 9장**(01 · 04 · 05 · 07 · 08 · 12 · 14 · 15 · 16), **재생성 7장 중 02 · 03 · 09 · 10 · 11 5장은 10/14 재생성본 통과**, **06 · 13도 10/14 재생성본 통과**(06은 4손가락 중 새끼손가락만 세운 손이 팔에 연결되고 몸과 분리, 13은 보드를 몸 옆으로 빼서 두 손이 모두 보임). 이전 지적: 06(엄지 · 새끼손가락 두 개가 펴진 샤카 모양이고 손이 몸과 겹침 — 사용자 의견: 손을 더 뻗거나 손 그림자로 분리) · 13(사용자 메모 "내로 버전과 비슷하게", 배경 초록 그대로)**. 이전 재생성 대상 7장은 — 자세: 02(편지 · 연필 · 다이어리 손 부족) · 06(새끼손가락 안 읽힘) · 10(어깨동무 팔 뭉툭) · 11(악수를 이끄는 느낌 아님, 팔을 대칭으로 벌림) · 13(보드에 손이 가려지고 표정이 생각 중이 아님), 표정: 03(스톱워치를 내밀지 않음 · 입이 활짝 웃음 · 몸통 쪽 초록 잔여 8 × 6 px) · 09(갸웃한 호기심이 아니라 그냥 웃음). 전체적으로 입 벌린 밝은 웃음이 많아 장면 감정이 약함 | ✅ 16장 |
| D2 | 성향 결과 **반대편 캐릭터** 16장(웹 이름은 번호표의 이미지 번호, 화면에서는 아직 안 씀) | 16 | 10/14 결정: 유형마다 내로 · 남불 **둘 다** 갖는다(32장). 번호표 **이미지 번호 규칙**(내로 `01 ~ 16` · 남불 `17 ~ 32`, 유형 번호 + 16, 변형 접미사 없음)을 따른다. 기본이 남불인 유형은 반대편이 내로(`02.webp` 등), 기본이 내로인 유형은 반대편이 남불(`17.webp` 등)이다. 내로 8(02 · 05 · 07 · 08 · 10 · 11 · 13 · 15) · 남불 8(01 · 03 · 04 · 06 · 09 · 12 · 14 · 16). 밈 장면은 D1과 같다(5장 D 장면 표). **10/14 16장 생성 · 검수**: **14장 통과**(01 · 03 · 04 · 05 · 07 · 08 · 09 · 10 · 11 · 12 · 13 · 14 · 15 · 16) → `cuts/personality/personality-{번호}_{반대편 캐릭터}_{밈}.png`(4:5)로 정리, 생성 직후 원본은 `_original/`. 11-2(지금 이름 내로 `11.webp`)는 어깨를 으쓱하는 모양이지만 사용자 판단으로 통과(허그 느낌). **다시 뽑기 2장 중 02-2(지금 이름 내로 `02.webp`)는 10/14 재생성본 통과**(`cuts/personality/personality-02_naero_apology-letter.png`, 편지 쓰기), **06-2(지금 이름 남불 `22.webp`)도 10/14 3차 재생성본 통과**(손이 팔에 연결, 새끼손가락만 세움, 몸 길이는 기준 스탠딩 대비 −5%로 허용). 이전 지적 06-2(손가락 4개로 · 손이 몸과 같은 빨강이 아니라 주황 · 배경 초록 그대로). 이전 메모: 02-2(누끼를 안 땀 · 포즈 어색 → 수첩을 없애고 편지를 쓰는 장면으로), 06-2(굵은 손가락 하나를 세운 모양이라 새끼손가락 약속이 안 읽힘 → 새끼손가락만 편 주먹을 크게). 1차본은 `staging/`에 있음. 화면에는 유형마다 기본 캐릭터 하나만 보여 준다(번호표, 지금은 하드코딩) | ✅ 16장 |

### E. 사건 재연 (S-02 · 사건 디테일)

| # | 이미지 | 수량 | 비고 | 상태 |
| --- | --- | --- | --- | --- |
| E1 | 사건별 재연 컷 | 사건 수 × 3 (`headline` · `sub-1` · `detail-1`) | 내로 · 남불이 **피해자 · 가해자를 번갈아** 맡는다(프로필 · 세계관 5-2). 흉기 · 상처 · 범행 순간 · 시신은 그리지 않는다(아키텍처 4-3). 누가 어떤 역할을 맡았는지 기록한다. **10/15**: 연기는 드라마처럼 극적이어도 된다(화난 · 적대 표정 · 폭력은 금지, 양쪽 감정을 같은 무게로, 아키텍처 4-3 · 프롬프트 모음 9-17), 배경은 투명, 의상은 사건마다 정한다. **사건 1**(가상 샘플): 내로 = 돈을 빌려준 지인(트렌치코트), 남불 = 빌린 집주인 · 피고인(늘어난 후드티). `cuts/reenact/case-1/reenact-case-1_cut_{headline · sub-1 · detail-1}.png`. 10/14 첫 샘플(셔츠 · 청바지, 방 배경)은 E1-공용 세트 2로 옮겼다 | 🟨 사건 1 이관 |
| E1-공용 | 공용 사건 재연 컷 | 3 (`headline` · `sub-1` · `detail-1`) | **생성되지 않은 사건의 이미지를 대신하는 공용 컷**(사건 id가 없는 대체용, 어떤 사건에도 갈 수 있다). `cuts/reenact/common/`, 16:9 1672 × 941. 흉기 · 상처 · 피해자 · 시신 없음, 정체성 · 글자 없음 OK. **10/14 재생성본 통과**(소방관 의상 · 도장 · 서명 제거, 차분한 표정, 불꽃 · 귀 잘림 없음, 옛 버전은 `discard/cuts/`). 사소: 소품이 많은 편, `detail`은 흑백에서 내로 카디건이 털과 붙음. 이전 후속: **표정**(3장 모두 입을 크게 벌린 활짝 웃음 — 어떤 사건에도 쓰이므로 차분한 표정이 안전), `headline`의 **소방관 의상**(화재 사건 연상, 공용에는 부적절) · 배경이 복잡함, `detail-1`은 남불 불꽃 끝이 위쪽에서 잘리고 도장 · 서명 장면이 판결을 연상시킬 수 있음. 역할은 직업 예시(기자 · 소방관 / 탐정 · 기록 / 서명)라 피해자 · 가해자 번갈아 기록 대상이 아님 | ✅ 10/14 재생성본 통과 |
| E1-공용 2 | 공용 사건 재연 컷 **세트 2** | 3 (`headline` · `sub-1` · `detail-1`) | 10/14 사건 1 샘플로 만든 컷(실내에서 두 사람이 돈 문제로 마주한 장면, 셔츠 · 청바지, 방 배경 · 16:9)을 10/15 공용으로 옮겼다. `cuts/reenact/common/reenact-common-2_cut_{headline · sub-1 · detail-1}.png` | ✅ (옮김) |

### F. 시트 (P1, 컷을 만들기 전 참고용)

| # | 시트 | 수량 | 비고 | 상태 |
| --- | --- | --- | --- | --- |
| F1 | 연기 시트 | 5 (내로 · 남불 · 저울 · 법봉 · 법전) | 장면 코드별 전신 자세(3-2). **10/14 로봉 추가**(전에는 2인 기준 시트의 자세 6종으로 대신했지만, 시트가 1인 1장으로 바뀌고 로 셋을 똑같이 갖추기로 함 — 로봉은 ⬜). `sheets/acting/{naero · nambul · law-scale · law-gavel · law-book}_sheet_acting.png`(16:9). **내로 · 남불 · 로울 3장 검수 통과**(8포즈, 정체성 유지, 봉투는 로 `deliver`에만, 목 없음). **로북**은 10/14 수정본 통과(`sheets/acting/law-book_sheet_acting.png`, 8포즈 · 마네킹 목 없음 · 봉투 deliver만, 사소: `think` 칸 손이 책 모서리를 받침 · `sorry` 칸 책 모서리가 옷깃에 매우 가까움, 옛 버전은 `discard/sheets/`). 내로 · 남불은 6번째 포즈가 서로 다름(내로 팔 뻗고 다리 참 · 남불 두 주먹), 로울 응원 칸은 두 주먹이 아니라 손을 모은 자세. 로봉은 **10/14 통과**(`sheets/acting/law-gavel_sheet_acting.png`, 8포즈 · 법봉 수평 · 봉투 deliver만 · 떡대 체형, 사소: 읽기 칸 종이에 글자처럼 보이는 줄) | ✅ 5장 |
| F2 | 역할 의상 시트 | 2 (내로 · 남불) | 판사 · 검사 · 변호인 · 사건 역할. **10/14 확정**(사용자 결정, 남불도 같이): 판사 = 로와 같은 판사복 + **흰 곱슬 가발(중세 유럽풍, 남불은 불꽃을 가린 채 가발만 보이게)**, 검사 = 무릎 기장 법복 + **버건디 앞단 띠**(`#6F1D2E` 제안값), 변호인 = **정장만(띠 없음)**, 사건 역할 = **셔츠 + 청바지**, **반다나 제거**(아키텍처 3-4 · 7-3 #2 ✅). 이전 시험본(황토 띠 · 판사복 주름 · 빨간 타이 · 정면과 같은 3/4 뷰)은 버리고 `sheets/role-outfits/{naero · nambul}.png`를 **다시 뽑아 덮어쓴다**(5장 F2 프롬프트). **10/14 결과**: 내로 통과(`sheets/role-outfits/naero_sheet_role-outfits.png`, 반다나 제거 · 판사 흰 가발 · 검사 버건디 두 줄 · 변호인 정장 · 사건 역할 셔츠 + 청바지, 사소: 3/4 행이 살짝만 돌아 있음, 시험본은 `discard/sheets/`), 남불은 10/14 재생성본 통과(`sheets/role-outfits/nambul_sheet_role-outfits.png`, 판사 흰 가발이 불꽃을 완전히 가림 · 3/4 행은 정면과 거의 같아 약함 · 가발이 매우 큼). 이전 지적: 판사 가발 위로 불꽃이 보여서 **불꽃을 가린 채 가발만 보이게** 다시 뽑는다(3/4 행을 실제로 돌리고 변호인 칸도 붉은 발 유지, 1차본 `staging/discard_nambul_sheet_role-outfits_try1-flame-above-wig.png`). 시험본 실측: 검사 띠 `#C17F39` / 변호인 `#E2AE75`(변호인이 약 28 ~ 31% 밝음, 둘 다 황토 계열이라 구분 약함) → 띠 색안 폐기 | ✅ 2장 |
| F3 | 데포르메 대표 시트 | 6 | `ranma` · `cheek` · `ppg` × 내로 · 남불. `sheets/deform/`(16:9, 정면 전신 · 턴어라운드 · 표정 6종 · 색 견본). **`ppg` 2장 ✅**(거대한 타원 눈 · 손가락 없는 손 · 굵은 색면). **`cheek` 2장 ✅ 10/14 사용자 판단으로 통과**(한계로 기록: 내로 "조금 아쉬움", 남불 색 견본에 내로 색 혼입 · 뒷모습 중복 · 불꽃이 "작고 통통한 3갈래"보다 큼). **`ranma` 2장은 란마 그림체가 안 나옴**(머리가 몸의 약 60%, 약 1.7등신 — 목표 약 5등신 · 머리 약 20%, 눈이 둥글고 하이라이트 하나, 갸름한 턱 · 출렁이는 불꽃 없음, 기본 마스코트처럼 나옴). **사용자 결정(10/14): 강하게 지시해 재생성 → 10/14 재생성본을 사용자 판단으로 통과**(`sheets/deform/{naero · nambul}-ranma.png`: 마스코트 2등신보다 확연히 키 크고 호리호리, 내로 약 3.7등신 · 남불 약 3.0등신으로 목표 약 5등신에는 못 미침, 얼굴은 여전히 둥글고 눈 하이라이트 하나 — 샘플 기준 통과, 옛 마스코트형은 `discard/sheets/`). 이전 판정: — 5장 F3 란마 프롬프트(비율을 수치로 못 박고 AVOID에 2등신 치비를 넣음, 지금 시트는 첨부하지 않음). 사용자 메모 "역이용 가능"은 지금 시트를 마스코트의 키 큰 변형 참고로 보관 | ✅ 6장 |

### G. 후순위

| # | 이미지 | 비고 |
| --- | --- | --- |
| G1 | 나머지 데포르메 8종 시트 | 아키텍처 3-1. **→ H5로 옮김**(H0을 보고 고른 데포르메만) |
| G2 | 소품 시트 | 3-3 카탈로그를 물건만 따로. **10/15 이관 묶음에 포함**(🟨): 10종(신문 · 돋보기 · 메모장 · 펜 · 서류철 · 클립보드 · 봉투 · 법전 · 법봉 · 작은 저울) + 크기 기준용 작은 내로, `props_sheet_catalog.png` |
| G3 | 스타일 변형 시트 | `riso` · `flat25` 등(3-6) |
| G4 | 꾸미기 레이어 | `layers/`, 저장 방식이 정해진 뒤 |
| G5 | 이벤트 · 굿즈용 | IP 확장(프로필 · 세계관 6장) |
| G6 | 로 데포르메 시트 (로울 · 로봉 · 로북 × `cheek` · `ranma` · `ppg`) | 아키텍처 2-2 "데포르메 시트 — 대표"의 로 몫. 로 쪽 DEFORMATION 블록은 프롬프트 모음 6-5 · 6-8 · 6-11에 이미 있음. 지금 로딩은 로 셋 `adult` 합의 연출이라 급하지 않음. **만들 때는 셋을 같이**(로울만 만들지 않는다), STYLE은 `editorial`로 바꾼다(2-3). **10/15 이관 묶음에 포함**(🟨, A6 검수 통과 뒤): A6 마스코트 시트 + 내로의 같은 데포르메 시트를 첨부, 법전 머리 한 줄 추가, `ranma`는 마스코트 비율을 따라 하지 않게 못 박음. 파일 `{law-*}_sheet_deform-{cheek · ranma · ppg}.png` |
| G7 | 데포르메별 자세 · 표정 | 10/15 추가. 데포르메마다 연기 시트 · 컷. **범위 정의 필요**(어느 데포르메 · 몇 장). **→ H6 · H7로 옮김** |
| G8 | 캐릭터 카드 | 10/15 추가. 컬렉션 등에 쓸 카드. **정의 필요**(구성 · 비율 · 글자를 웹에서 얹을지). **→ H8로 옮김** |

### H. 추가 생성 목록 (10/15 정리)

> 우선순위는 **1순위 → 2순위 → 3순위**. 1순위의 H0을 보고 어느 데포르메를 본격적으로 만들지 정한다(H5 ~ H7). 프롬프트는 [프롬프트 모음](character-image-prompts.md) 9-18 ~ 9-21. 배경은 모두 투명(1장).

**1순위**

| # | 이미지 | 수량 | 형식 | 비고 | 상태 |
| --- | --- | --- | --- | --- | --- |
| H0 | **데포르메 묶음 시트** — 한 캐릭터를 기본 + 데포르메 11종, 12칸에 같은 자세로 | 5 (내로 · 남불 · 로울 · 로봉 · 로북) | 시트 16:9 | **느낌을 보는 용도**. 11종(아키텍처 3-1) 전부를 한 장에 넣어 비교하고, 본격적으로 만들 데포르메를 고른다. 로는 기본 칸에 마스코트 로(A6)를 쓰므로 A6 뒤. 12칸이 너무 빽빽하게 나오면 6칸씩 두 장으로 나눈다. 렌더링(외곽선 없음 · 평면 색)은 같게 두고 비율 · 형태 · 연기만 바꾼다 | ⬜ |
| H1 | **의상 시트** — 일상 의상 8벌 | 5 (내로 · 남불 · 로울 · 로봉 · 로북) | 시트 16:9 | 기존 역할 의상 시트(F2: 판사 · 검사 · 변호인 · 사건 역할)와 별개. 정장 · 트렌치코트 · 후드티 · 니트 · 작업복 · 앞치마 · 패딩 · 잠옷(제복은 법정 역할과 헷갈려 뺌). 내로 · 남불은 사건 재연(H4)의 의상을 여기서 고르고, 로는 법복을 벗은 모습(컬렉션 · 카드용, 기본 로 `authority` × `adult`, 머리 물체 · 흰 장갑 유지). 반다나 없음, 초록 · 캐릭터 고정색 피함 | ⬜ |
| H2 | **마스코트 로 컷** | 12 | 장면 컷 4:5 · 16:9 | 로 셋 × `idle-neutral` · `deliver-neutral` · `think-thinking`(9) + `trio_cut-mascot_idle` · `trio_cut-mascot_think`(2, 16:9) + 내로 · 로울 · 남불 비교 `compare_cut-mascot_trio-thinking`(1, 16:9). 키 로울 180 · 로북 170 · 로봉 160(내로 · 남불 120). A6 통과 뒤 | ⬜ |
| H3 | **역할 의상 정식 컷** | 8 | 장면 컷 4:5 | 지금 판결 체험 무대(`frontend/public/images/characters/roles/*.webp`, FE-27 · 28)는 **역할 의상 시트에서 잘라 낸 컷**(높이 560 px)을 쓰고, **남불 변호인 컷은 없다**. 내로 · 남불 × 판사 · 검사 · 변호인 · 사건 역할 = 8장을 `idle-neutral`로 정식 생성한다. **검사 · 변호인은 같은 자세 · 같은 표정**(한쪽이 옳아 보이지 않게). 파일 `{naero · nambul}_cut-role-{역할}_idle-neutral.png` | ⬜ |

**2순위**

| # | 이미지 | 수량 | 형식 | 비고 | 상태 |
| --- | --- | --- | --- | --- | --- |
| H4 | **사건별 재연** — 재연 컷 3 + 사건 배역 컷 2 | 사건마다 5 | 16:9 · 4:5 | 재연 컷(E1, `headline` · `sub-1` · `detail-1`)에 더해, 무대(3막 등)에서 그 사건 의상을 입은 내로 · 남불 컷(`{naero · nambul}_cut-role-case-{caseId}_idle-neutral.png`)을 함께 만든다. 의상은 H1에서 고른다. 배역은 사건마다 번갈아(세계관 5-2). 사건 1은 재연 컷 3장이 이관 묶음에 있으므로 배역 컷 2장만 남음. 2 ~ 9번 사건은 확정되는 대로 | ⬜ |

**3순위** (H0을 보고 범위를 정한다)

| # | 이미지 | 비고 |
| --- | --- | --- |
| H5 | 고른 데포르메의 **기준 시트** | 옛 G1. 데포르메 × 캐릭터마다 1장(정면 · 턴어라운드 · 표정 · 색 견본). 로는 G6(3종) 다음 |
| H6 | 데포르메별 **연기 · 표정 시트** | 옛 G7. 장면 코드 8 + 감정 6 |
| H7 | 데포르메 **쓰임 컷** | 옛 G7. 아키텍처 3-1 "주 쓰임" 화면에 넣을 컷(예: `tezuka` 튜토리얼 안내, `gekiga` S-07 공개 직전, `sports` S-09 대결 구도, `gravity` S-04 기록 확인) |
| H8 | **캐릭터(수집) 카드** | 옛 G8. 8막 S-09에서 보여 주는 수집 카드(유저 흐름 8막, 세계관 6장). 카드 단위 · 비율 · 배경 일러스트 여부 정의 필요. 일러스트만 이미지로, 프레임 · 글자는 웹 |
| H9 | **최종 재생성** | 시트 · 스탠딩 몇 장을 품질(low · medium · high)별로 뽑아 비교한 뒤 전체 디테일 정리 |


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

- 앞으로 캐릭터가 늘면 이 표 기준으로 "다른 캐릭터도 있어야 하는 이미지인지"를 먼저 정한다. 성향 이미지는 번호표의 "새 이미지 번호 배정" 규칙(`32` 다음부터 이어서)으로 붙인다.

---

## 3. 순서 · 합계

**추천 순서**: A1 → A2 → A3 → B1 → B2 · B3 → D1 → C → B4 · B5 → F → E(사건이 정해지는 대로). **10/14 추가분**: A5(1인 시트 — 이후 생성의 첨부 기준이 되므로 먼저) → B6(변환만) → D2 · D1 재생성 7장 → B7 · B8 → F1 로봉 → G6. **10/15 이관 묶음**: A6(마스코트 로) → G6(A6 통과 뒤), E1 사건 1 · G2는 아무 때나. **다음 생성 목록(H, 10/15)**: 1순위 H0 · H1 · H2 · H3 → 2순위 H4(사건별 재연) → 3순위 H5 ~ H9(H0을 보고 범위 결정)

| 묶음 | 수량 |
| --- | --- |
| A 기준(남은 것) | 0 |
| B 우선 컷 | 36 (B1 ~ B5 28 + B6 4(변환) · B7 2 · B8 2. B5 일부 재생성 예정, B4는 통과) |
| C 판결 결과 | 5 |
| D 성향 | 32 (D1 16 + D2 16) |
| F 시트 | 13 (F1 5 · F2 2 · F3 6) |
| **합계 (A ~ D, F)** | **약 91** |
| 10/15 이관 묶음 | 19 (A6 6 · G6 9 · E1 사건 1 3 · G2 1) |
| H 1순위 | 30 (H0 5 · H1 5 · H2 12 · H3 8) |
| H 2순위 | 사건마다 5 (사건 1은 2) |
| E 재연 | 사건 수 × 3 (별도), 공용 6(세트 1 · 2) |

---

## 4. 완료 처리

- 한 장을 만들면 아키텍처 7-2 체크리스트로 검수하고, 이 표의 상태를 ✅로 바꾼다.
- webp를 `frontend/public/images/characters/` 경로(아키텍처 6-4)에 넣는 것은 화면 연결 작업과 함께 한다.
- 프롬프트를 바꿔 다시 뽑은 경우 원본 폴더에 날짜를 붙여 남긴다(예: `law-book_standing_2026-10-14.png`).

---

### 4-1. 검수 기록 (2026-10-14)

> 기준은 이 문서 1장(공통 규칙) · 2장(목록)과 프롬프트 모음 9장이다. 이미지는 직접 열어 보거나 묶음 시트로 모아서 확인했고, 비율은 `magick`으로 실측했다. 모두 **샘플 기준** 판정이다.

| 구분 | 대상 | 결과 | 확인한 것 · 남은 일 |
| --- | --- | --- | --- |
| A | A1 / R2 `law-book_sheet_reference.png` (GPT 버전) | 🟨 보완 | 줄 반복 없음 · 책 떠 있음 · 목 없음 OK. 칸 배치 · 아쉬움 칸 · 안 쓰인 색 견본 3개 보완 |
| A | A2 / R1 `law-book_standing.png` | ✅ | 정식. 새로 뽑은 `a2` · `a4-3`은 `discard/standing/` |
| A | A3 / R3 `all_sheet_height-compare.png` | ✅ (합성) | 생성본은 로 셋이 목표보다 21 ~ 35% 큼 → 합성으로 대체 |
| A | A4 로울 · 로봉 빈손 | ✅ | 원본과 같고 봉투만 제거. 옛 봉투본은 `discard/standing/` |
| A | A5 내로 · 남불 · 로울 1인 시트 | ✅ | 1인 · 4칸 중복 없음 · 그 캐릭터 색 견본만(남불 시트에 내로 색 혼입 없음). 사소: `thinking` 눈썹 · 내로 `sorry` 눈 감음 · 로울 3/4 뷰 약함 |
| A | A5 로봉 1인 시트(수정본) | ✅ | 정면 폭/높이 0.62(1차본 0.44 · 로울 0.48 · +29%), 1인 · 빈손 · 4칸 · 봉투 한 칸 OK. 사소: 색 견본 5개 |
| A | R4 로봉 스탠딩 재작업 | ✅ | 지금 대비 어깨 +19% · 로브 아래 +15% · 중간 +10%, 머리 · 포즈 · 구두 · 로브 유지, 투명 · 초록 잔여 0. 사용자 판단으로 "실사체에서 적당" |
| C | 연쇄 — C1 `trio-idle` · `trio-think`, C2 로봉 `deliver-neutral` | 🟨 재생성 | 로봉 체형 변경으로 옛 슬림 로봉이 들어간 컷. 작업 목록 문서 C1-idle · C1-think · C2-gavel |
| A | 연쇄 — A3 키 비교 합성 v3 | ✅ | 10/14 새 로봉 스탠딩으로 재합성(높이 1 · 1 · 1.92 · 1.58 · 1.75, 로봉 떡대 반영) |
| B | B1 ~ B3 | ✅ | 투명 배경 · 초록 잔여 0 · 규격 개수 일치. 4:5 맞춤 |
| B | B4 | ✅ (사용자 판단) | 몸통 형태 · 남불 thinking 표정은 한계로 기록, 통과로 결정 |
| B | B5 `cheer` · `sorry` 4장 | ✅ | `cheer` 별 하이라이트 + 반짝이로 `happy`와 구분, `sorry` 땀방울이 읽힘(남불은 흐림). 64 px 확인 |
| A | R2 로북 기준 시트 · F1 로북 연기 시트 | ✅ | R2 로울 · 로봉 시트와 같은 배치 · 아쉬움 칸 · 마네킹 목 없음 · 색 견본 6개. F1 8포즈 · 봉투 deliver만. 사소: 로북 `think` · `sorry` 칸의 책 모서리 여유 |
| C | C1 `trio-idle` | 🟨 | 초록 잔여 92 px 제거 |
| C | C1 `trio-think` | 🟨 | 서로 향해 기울임 없음 · 로북 키 |
| C | C2 `deliver` 2장 | ✅ | |
| C | C3 `trio-thinking` | 🟨 | 로울 1.69배(목표 1.92) · 바닥선 36 px 차이 |
| D | D1 통과 9장 (01 · 04 · 05 · 07 · 08 · 12 · 14 · 15 · 16) | ✅ | 번호표 밈 장면과 자세 · 소품 일치. 04 연필이 종이에서 떨어짐, 07 표정이 "차분히"보다 밝음, 15 다리 자세가 약간 어색한 것은 사소해서 통과 |
| D | D1 재생성 5장(02 · 03 · 09 · 10 · 11) | ✅ | 02 편지 쓰기 · 03 닫힌 입 · 눈썹(스톱워치는 얼굴 옆, 사소) · 09 갸웃 · 10 팔 뻗음 · 11 허그 느낌 |
| D | D1 06 · 13 재생성본 | ✅ | 06 4손가락 중 새끼손가락만 세운 손이 팔에 연결(몸과 분리) · 13 보드를 몸 옆으로 빼서 두 손 모두 보임, 누끼 정상 |
| C | C1 합의 컷 · C2 로봉 발표 컷 재생성본 | ✅ | C1 키 로봉 0.82 · 로북 0.89, `trio-think` 서로를 향해 기울임, `trio-idle` 초록 44 px 후처리로 제거. C2 떡대 체형 · 법봉 수평 · 두 손 봉투 |
| C | C3 비교 컷 후처리 | ✅ | 로울 1.92배(817 / 426) · 같은 바닥선, 마스코트를 줄여 맞춤 |
| D | D2 반대편 14장(01 · 03 ~ 05 · 07 ~ 16) | ✅ | 번호별 밈 장면 · 반대편 캐릭터 · 소품 · 표정 일치, 글자 없음, 투명 · 초록 잔여 없음(13-2 보드의 초록 원은 소품). 11-2는 사용자 판단으로 통과 |
| D | D2 02-2 재생성본 | ✅ | 내로가 편지를 쓰는 장면, 누끼 정상, 반다나 보임 |
| D | D2 06-2 재생성본(3차) | ✅ | 손이 팔에 연결 · 새끼손가락만 세움 · 손은 몸과 같은 빨강. 몸 길이는 기준 스탠딩 대비 1차 +13% · 2차 −7% · 3차 −5%로 허용. 투명 배경 |
| B | B7 응원 · B8 기록 읽기 4장 | ✅ | B7 두 주먹 · 빈손, B8 서류철 + 돋보기. 사소: B8 눈썹이 날카로워 보임 |
| B | B6 `idle-neutral` 4장(변환) | ✅ | 내로 · 남불 · 로봉 · 로북 스탠딩을 4:5로 변환. 로울은 B1 컷 사용 |
| E | E1-공용 `cuts/reenact/common/` 3장(재생성본) | ✅ | 소방관 · 도장 제거, 차분한 표정, 불꽃 · 귀 잘림 없음, 글자 없음. 사소: 소품이 많은 편 · `detail` 흑백에서 카디건과 털이 붙음 |
| F | F1 연기 시트 내로 · 남불 · 로울 | ✅ | 8포즈 구분 명확. 6번째 포즈 차이 · 로울 응원 칸은 허용 범위 |
| F | F1 연기 시트 로봉 | ✅ | 8포즈 · 법봉 수평 · 봉투는 deliver만 · 새 떡대 체형 · 구두 검정. 사소: 읽기 칸 종이에 글자처럼 보이는 줄, 응원 칸이 손 모은 자세 |
| F | F1 연기 시트 로북 | 🟨 | 아쉬움 칸 책이 옷깃에 닿을 듯 + 옷깃 안 마네킹 목 |
| F | F2 역할 의상 내로 | ✅ | 반다나 제거 · 판사 흰 가발 · 검사 버건디 두 줄 · 변호인 정장(띠 없음) · 사건 역할 셔츠 + 청바지. 사소: 3/4 행이 살짝만 돌아 있음 |
| F | F2 역할 의상 남불 | ✅ | 판사 흰 가발이 불꽃을 완전히 가림 · 검사 버건디 두 줄 · 변호인 정장 · 붉은 발 유지 · 사건 역할 셔츠 + 청바지. 사소: 3/4 행이 정면과 거의 같음 |
| F | F3 `ppg` 내로 · 남불 | ✅ | 형태 · 표정 · 정체성 OK. 남불 `ppg` 표정 5번 아래 작은 검은 점은 사소 |
| F | F3 `ranma` 내로 · 남불 | ✅ (사용자 판단) | 10/14 재생성본: 호리호리한 키 큰 체형 · 4칸 · 표정 6종 · 그 캐릭터 색 견본만. 내로 약 3.7등신 · 남불 약 3.0등신(목표 약 5등신 미달), 얼굴은 마스코트와 같은 둥근 눈 |
| F | F3 `cheek` 내로 · 남불 | ✅ (사용자 판단) | 내로 규격 통과, 남불 색 견본 혼입 · 뒷모습 중복은 한계로 기록. 사용자 검수 "조금 아쉬움"이지만 통과로 결정 |

- 공통으로 남은 일: 웹 변환 전 캐릭터 크기 기준 통일(컷마다 캔버스 대비 높이가 다름), 정식 해상도(2048 × 2560 · 512 × 512)는 모델 교체 후 재생성 때 맞춘다.
- D1 재생성 보강 문구(재생성 때 해당 SCENE 줄에 더한다): 02 — 수첩 없이 한 손에 편지지를 들고 연필로 편지를 쓴다(10/14 결정), 표정은 미안한 듯 조용한 미소 / 03 — 스톱워치를 팔을 쭉 뻗어 카메라 쪽으로, 한쪽 눈썹만 올리고 입은 한쪽만 올린 미소 / 06 — 새끼손가락만 편 주먹을 얼굴 크기로 크게 앞으로, 나머지 손가락은 말아 쥠 / 09 — 고개를 기울이고 입은 작게 오므린 의문 표정 / 10 — 팔을 길게 옆으로 벌리고 손바닥 위로, 어깨동무 자리를 비워 둠 / 11 — 악수 대신 허그하는 느낌: 양팔을 앞으로 감싸듯 벌림(10/14 결정) / 13 — 한 손은 턱, 한 손은 보드 가장자리에 마커, 입은 작게 다문 생각 표정
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

- **A1 로(법전) 기준 시트**: 프롬프트 모음 9-9(완성본). 첨부 `discard_law-scale-and-law-gavel_sheet_reference_two-person.png`
- **A2 로(법전) 스탠딩**: 프롬프트 모음 9-10(완성본). 첨부 `law-scale_standing.png` · `law-gavel_standing.png`
- **A3 키 비교 시트**: 프롬프트 모음 9-11(완성본). 첨부 `*_standing` 전부
- **A4 로 스탠딩 빈손 버전**: 프롬프트 모음 9-12(완성본). 첨부 해당 `law-*_standing.png`. 로북은 A4-3 대신 R1을 쓴다
- **R1 로북 스탠딩 수정**: 9-13. 첨부 `law-book_standing.png` · `law-scale_standing.png`
- **R2 로북 기준 시트 수정**: 9-14. 첨부 `discard_law-book_sheet_reference_v1.png` (+ R1 결과) — 10/14 R2 통과, 지금 `law-book_sheet_reference.png`는 통과본
- **R3 키 비교 v2**: 9-15. 첨부 `discard_all_sheet_height-compare_v1.png` + 스탠딩 전부

**A5 캐릭터별 기준 시트 — 내로 · 남불** — 첨부 `discard_naero-and-nambul_sheet_reference_two-person.png`(그림체 · 색 참고) · 해당 `*_standing.png`

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

**A5 캐릭터별 기준 시트 — 로울 · 로봉** — 첨부 `law-book_sheet_reference.png`(1인 시트 배치 참고) · `discard_law-scale-and-law-gavel_sheet_reference_two-person.png`(그림체 참고) · 해당 `law-*_standing.png`(빈손 기준)

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

- 로북 R2(9-14)는 10/14 통과했다. 다섯 장을 나란히 놓고 칸 배치 · 크기가 같은지 확인한다.

### B. 우선 컷

**B1 로(저울) `idle-neutral` · `deliver-neutral`** — 첨부 `law-scale_standing.png`

```
Use the attached reference image as the exact model of Law (Scale): copy the head, robe, colors, proportions and art style exactly. Change only the pose.
POSE — {idle: (A4 빈손 스탠딩을 쓰면 생성할 필요 없음) standing upright, front to slight three-quarter view, empty-handed: one hand open, palm up and slightly out to the side, presenting calmly; the other hand resting at the side. No envelope or other prop. | deliver: stepping half a step forward and holding the sealed verdict envelope out with both hands toward the viewer, calm and polite}. The scale is perfectly level.
SCENE — a single character, centered, full body visible, 4:5 vertical, 15% margin, perfectly flat solid green #00B140 background. No green on the character.
AVOID (add): faces, a neck, a tilted scale.
```

**B2 · B3 내로 · 남불 `guide` · `react`** — 첨부 `naero_standing.png` 또는 `nambul_standing.png`

```
Use the attached reference image as the exact model of {Naero | Nambul}: copy the face, colors, proportions (about 2 heads tall, big head, short body) and flat art style exactly. Change only the pose and expression.
POSE — {guide-neutral: pointing to one side with one hand as if introducing something, calm friendly face | guide-happy: the same pointing pose with a bright open smile | react-surprised: jumping slightly, wide eyes, round open mouth, small surprise marks beside the head | react-happy: both arms up, big smile | react-sorry: shoulders down, drooping eyebrows, small mouth, one small sweat drop}.
SCENE — a single character, centered, full body visible, 4:5 vertical, 15% margin, perfectly flat solid green #00B140 background. No green on the character.
AVOID (add): {Naero: a missing bandana, a different fur color | Nambul: a nose, blank eyes, flames below the head}.
```

**B4 내로 · 남불 `cheek` (로딩 · 빈 상태)** — 첨부 `naero_standing.png` · `nambul_standing.png`

```
Use the attached reference image as the model of {Naero | Nambul}'s colors and design, but redraw the character as a 'cheek-squish' mochi chibi: about 2 heads tall with a huge wide head, big round squishy cheeks bulging past the face line, a tiny round dumpling body and nub limbs, soft blush ovals. Keep {the navy fur, yellow eyes and red bandana | the three-color flame head, visible pupils and no nose} exactly.
POSE — {idle-sorry: sitting, cheeks puffed, drooping eyebrows, a small sweat drop | idle-thinking: head tilted, a small question mark shape beside the head (no text)}.
SCENE — a single character, centered, 4:5 vertical, 15% margin, flat solid green #00B140 background. No green on the character.
```

**B5 내로 · 남불 `ppg` 아이콘 `face-{감정}`** — 첨부 `naero_standing.png` · `nambul_standing.png`

```
Use the attached reference image as the model of {Naero | Nambul}'s colors and identity, simplified into a bold, minimal cartoon face icon: giant oval eyes, very simple shapes, strong color blocks. Keep {the navy fur, yellow eyes, pink nose and a hint of the red bandana | the three-color flame, visible pupils, no nose}.
EXPRESSION — {neutral | happy | surprised | thinking | sorry | cheer}.
SCENE — face only, the head filling about 70% of a 1:1 square, centered, readable at 64 px, flat solid green #00B140 background. No green on the character.
```

**B6 `idle-neutral` (내로 · 남불 · 로봉 · 로북)** — 생성하지 않는다. 해당 `*_standing.png`를 4:5 캔버스(투명 여백만 추가)로 맞춰 `cuts/{캐릭터}/base/{캐릭터}_cut_idle-neutral.png`로 둔다(내로 · 남불 `editorial-mascot`, 로 `authority-adult`). 스탠딩을 고치면 다시 변환한다.

**B7 내로 · 남불 `cheer-cheer`** — 첨부 `naero_standing.png` 또는 `nambul_standing.png`

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

**C1 로 3인 합의 컷** — 첨부 `law-scale_standing.png` · `law-gavel_standing.png` · `law-book_standing.png`

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

**C3 S-09 비교 `trio-thinking`** — 첨부 `naero_standing.png` · `law-scale_standing.png` · `nambul_standing.png`

```
Use the attached reference images as the exact models of Naero, Law (Scale) and Nambul. Keep each character's own art style (Naero and Nambul flat mascots, Law refined painterly).
SCENE — Naero on the left, Law (Scale) in the center, Nambul on the right, standing on the same ground line, all thinking (one hand on the chin, heads tilted slightly). Naero and Nambul are the same height; Law (Scale) is about 1.9 times taller. Nobody stands in front; nobody is emphasized. The scale is level.
Background: perfectly flat solid green #00B140, full bodies visible, 10% margin, 16:9. No green on the characters.
```

### D. 성향 테스트

**D1 · D2 성향 결과 `01.webp` ~ `32.webp`(이미지 번호)** — 첨부 해당 캐릭터 스탠딩. 밈 장면은 [번호표](personality-image-numbers.md), 영어 장면 문구는 아래 표

```
Use the attached reference image as the exact model of {Naero | Nambul}: copy the face, colors, proportions and flat art style exactly.
SCENE — {아래 표의 장면 문구, {C}를 Naero 또는 Nambul로}. Friendly and warm; the character is never angry or mocking. The facial expression must match the scene described (not a default open-mouth grin).
A single character (props allowed), centered, full body visible, 4:5 vertical, 15% margin, flat solid green #00B140 background. No green on the character or props.
AVOID (add): text on props, the letters of personality codes.
```

**장면 문구 (D1 · D2 공통, `{C}` = 캐릭터)** — D1 재생성 보강 문구(4-1)를 반영했다. D1 재생성 7장(02 · 03 · 06 · 09 · 10 · 11 · 13)도 이 문구로 다시 뽑는다.

| 유형 번호 | 기본 캐릭터 이미지 | 반대편 캐릭터 이미지 | 장면 문구 |
| --- | --- | --- | --- |
| 01 | 내로 | 남불 | {C} standing straight with arms crossed, a thick plain rule binder standing beside them, a calm, firm but friendly look |
| 02 | 남불 | 내로 | {C} writing an apology letter: holding a sheet of letter paper in one hand and writing on it with a pencil in the other hand; there is no notebook; a quiet, slightly apologetic smile |
| 03 | 내로 | 남불 | {C} holding a stopwatch out toward the viewer with the arm fully stretched, one eyebrow raised, a confident half smile with one corner of the mouth up |
| 04 | 내로 | 남불 | {C} holding a clipboard and ticking a checkbox with the pencil tip touching the paper, focused eyes, a small closed-mouth smile |
| 05 | 남불 | 내로 | {C} holding a smartphone with both hands and typing a group announcement, a cheerful smile; the screen is blank |
| 06 | 내로 | 남불 | {C} holding up one hand as a fist with only the little finger extended, drawn large and clearly visible near the face, toward the viewer for a pinky promise; the other fingers are curled; a warm gentle smile |
| 07 | 남불 | 내로 | {C} pushing up round glasses with one finger, looking calmly at the viewer, mouth closed in a small calm line |
| 08 | 남불 | 내로 | {C} with a blank, neutral face (straight mouth) giving a thumbs-up with one hand, the other hand on the hip |
| 09 | 내로 | 남불 | {C} with one hand on the chin and the head tilted to one side, curious eyes, a small pursed questioning mouth |
| 10 | 남불 | 내로 | {C} with one arm stretched out wide to the side at shoulder height, palm up, inviting someone in for a shoulder hug, leaving empty space beside them, a friendly smile |
| 11 | 남불 | 내로 | {C} opening both arms wide and slightly forward, curved inward, as if about to give a warm hug to someone standing in front of them, a kind encouraging smile |
| 12 | 내로 | 남불 | {C} with one hand on the chest and the other palm held gently forward, a soft understanding smile |
| 13 | 남불 | 내로 | {C} holding a small whiteboard with simple arrows and shapes (no text) at one side, the other hand on the chin, eyes looking up, mouth closed, deep in thought |
| 14 | 내로 | 남불 | {C} holding a plain handkerchief in one hand and offering the other hand forward, a small, slightly sad but kind smile |
| 15 | 남불 | 내로 | {C} holding a glowing cheer stick high, mid-step, an excited big smile |
| 16 | 내로 | 남불 | {C} wrapped in a soft blanket and holding a warm mug with both hands, a gentle peaceful smile |

- 남불이 담요(16) · 종이(02 · 13) 같은 소품을 들 때는 `the props stay clear of the flame`을 덧붙인다. 내로는 담요(16)를 둘러도 반다나가 보이게(`the bandana stays visible`).
- 파일: D1(기본 캐릭터)은 `cuts/personality/personality-{번호}_{기본 캐릭터}_{밈}.png`, D2(반대편)는 `cuts/personality/personality-{번호}_{반대편 캐릭터}_{밈}.png`(4:5 맞춤 후, 이름은 파일 이름 규칙 참고).

### E. 사건 재연

**E1 사건별 재연 컷 `headline` · `sub-1` · `detail-1`** — 첨부 `naero_standing.png` · `nambul_standing.png`

```
Use the attached reference images as the exact models of Naero and Nambul (faces, colors, proportions, flat style). They are acting out a real case as roles; only their outfits and props change.
SCENE — a newspaper-photo-like scene: {situation in English, e.g. "two neighbors facing each other in an apartment hallway after a noise complaint"}. Naero plays {role A, e.g. "the downstairs neighbor in a cardigan"} and Nambul plays {role B, e.g. "the upstairs neighbor in a hoodie"}. Show only the situation and relationship.
Draw only the characters and the few props listed: no room, no walls, no floor, no scenery. Fully transparent background, 16:9 landscape.
AVOID (add): injuries, weapons, blood, bodies, a crime in progress, anything hinting at the verdict, mocking the victim.
```

- 10/15: 배경은 투명(위 예시 반영), 극적인 연출은 프롬프트 모음 9-17 CASE RULES를 붙인다. 의상은 사건마다 정한다.
- 역할은 사건마다 **번갈아** 정한다(이번 사건에서 내로가 가해자 역할이면 다음 사건은 남불). 정한 배정을 이 표 비고나 recipe에 남긴다.

### F. 시트

**F1 연기 시트** — 첨부 해당 캐릭터 시트 · 스탠딩

```
Use the attached reference images as the exact model of {Naero | Nambul | Law (Scale) | Law (Gavel) | Law (Book)}. Create an acting sheet in the same art style.
SHEET — eight full-body poses in two rows: idle, guide (pointing to one side), read (holding a document), think (hand on chin), react (surprised), cheer (both fists up), deliver (holding out an envelope, Law only) {or duo-ready pose for Naero/Nambul}. Calm, friendly acting; never angry.
SCENE — plain newsprint background #ECE2D4, panels separated by space only, no labels, landscape 16:9.
```

- 로봉(10/14 추가)은 `law-gavel_standing.png`와 A5 로봉 시트(나오면)를 첨부한다. 법봉 머리는 수평이 기본이고 휘두르지 않는다(`AVOID (add): swinging the gavel`). 로 셋의 감정은 머리 물체로만(아키텍처 3-2 감정 코드).

**F2 역할 의상 시트** — 10/14 확정(사용자 결정). 첨부 해당 캐릭터 스탠딩(+ A5 1인 시트). 지금 시트(황토 띠 시험본)는 첨부하지 않는다. 한 장에 한 캐릭터

```
Use the attached reference images as the exact model of {Naero | Nambul}: copy the face, colors, proportions and flat art style exactly. Create a role outfit sheet for this ONE character only. Only the outfits change; keep {Naero: the face, navy fur, yellow eyes and pointed ears (the ears stay visible in every outfit; the judge's wig sits between and behind the ears) | Nambul: the three-color flame head, visible pupils and no nose (the flame stays recognizable in every outfit EXCEPT the judge: for the judge, the white curled wig fully covers the top and back of the flame head, so no flame is visible above or behind the wig and only the flame-colored face shows below the wig; no hat in the other outfits)}.
SHEET LAYOUT — four columns: judge, prosecutor, defense lawyer, case-role; the top row shows front views and the bottom row shows truly turned three-quarter views (a clear body turn, not a copy of the front view). The same character, the same face and the same calm expression in all eight panels.
OUTFITS —
- judge: the same navy robe as Law (deep V neckline, two vertical front bands, wide bell sleeves, ankle-length hem, no pleats), a white shirt collar, and a white curled European judge's wig in a medieval style; no bandana.
- prosecutor: a knee-length navy robe (shorter than the judge's robe) with two vertical burgundy front bands (#6F1D2E); no wig; no bandana.
- defense lawyer: a plain dark charcoal business suit (jacket, trousers, white shirt, a plain dark navy necktie); no robe, no colored band, no wig; no bandana.
- case-role: a plain white shirt and blue jeans; no vest, no bandana.
The prosecutor and the defense lawyer must not read as a "good" side and a "bad" side: both look equally neutral and dignified.
SCENE — plain newsprint background #ECE2D4, panels separated by space only, no frame lines, no labels, landscape 16:9.
AVOID (add): a bandana in any outfit, a wig on anyone except the judge, hats, a second character, ochre or tan bands, a band or stole on the defense lawyer, a vest on the case-role outfit.
```

**F3 데포르메 대표 시트** — 첨부 내로 · 남불 시트. 프롬프트 모음 6장의 DEFORMATION 블록(란마 6-5 · 볼따구 6-8 · 카툰 6-11)을 9장 블록과 함께 쓴다.

```
Use the attached reference sheet as the model of {Naero | Nambul}'s colors and identity. Redraw this ONE character in the {deformation} deformation below, keeping {the bandana, yellow eyes and navy fur | the three-color flame head, visible pupils and no nose}. Do not draw the other character, and use only this character's colors in the swatches.
{프롬프트 모음 6장의 해당 DEFORMATION 블록}
SHEET — one front full body, a turnaround of exactly four views (front, three-quarter, side, back), six expressions, color swatches. Plain newsprint background #ECE2D4, landscape 16:9.
```

**F3 `ranma` 재생성 (강하게 지시, 10/14)** — 첨부 해당 캐릭터 스탠딩 + A5 1인 시트(나오면). **지금 `sheets/deform/{naero · nambul}-ranma.png`는 첨부하지 않는다**(2등신 비율을 따라 하므로)

```
Use the attached reference images as the model of {Naero | Nambul}'s colors and identity (face details, colors, markings). IMPORTANT: do NOT copy the attached image's body proportions. The attached mascot is only about 2 heads tall; this sheet must show a much taller, slimmer anime-style version of the same character. Redraw this ONE character in the 1980s–90s Japanese TV anime deformation below, keeping {the bandana, yellow eyes and navy fur | the three-color flame head, visible pupils and no nose}. Do not draw the other character, and use only this character's colors in the swatches.
DEFORMATION — 1980s–90s Japanese TV anime deformation:
- About 5 heads tall; soft, rounded body with slightly long legs; {Naero is a standing anthropomorphic cat | Nambul is a lively figure with a flame for a head} with a slim, friendly build.
- Faces: round faces with a small, slightly pointed chin; large eyes with tall oval irises, one big glossy highlight and a smaller second one; small, simple mouths.
- Colors feel like retro anime cels: flat fills with one crisp shadow tone, slightly softer saturation.
- Acting: comedic, energetic poses, big reactions (surprise jumps, sweat-drop gag faces).
- {Nambul only: the flame on Nambul's head has bouncy, wavy flame points, like a retro anime fire effect.}
- Whatever the deformation, the identity rules below never change.
FIX — the proportions must be unmistakably different from the attached mascot: the full body is about 5 heads tall, so the head is only about 20% of the total body height (NOT a large-head chibi); the neck-to-feet length is clearly longer than the head; slightly long legs; a round face with a small, slightly pointed chin; large eyes with tall oval irises, one big glossy highlight and a smaller second one; a small simple mouth. The face is clearly an older-style retro anime face, not the rounded mascot face.
SHEET — one front full body, a turnaround of exactly four views (front, three-quarter, side, back), six expressions (neutral, happy, surprised, thinking, sorry, sleepy), color swatches. Plain newsprint background #ECE2D4, landscape 16:9.
AVOID (add): a 2-head chibi proportion, a huge head, the same proportions as the attached mascot, a second character, another character's colors in the swatches, duplicate views.
```

- 10/14부터 F3도 **한 장에 한 캐릭터**로 뽑는다(지금 6장은 이미 캐릭터별). 첨부는 A5 1인 시트가 나오면 그것으로 바꾼다.

**G6 로 데포르메 시트 (후순위)** — F3과 같은 형식. 첨부 해당 로의 A5 1인 시트 · 스탠딩. 프롬프트 모음 6장의 **로용** DEFORMATION 블록(6-5 · 6-8 · 6-11)을 쓰고, STYLE은 `editorial`(9-3)로 바꾼다. 로 셋을 같은 데포르메로 함께 만든다. 법전 머리는 로용 블록에 없으므로 `the head object is a plump, thick closed book floating above the collar with a clear gap` 한 줄을 더한다.
