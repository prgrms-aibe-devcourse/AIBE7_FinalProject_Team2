# **AI 판결 RAG 판례 저장소 인수인계 (BE-50 · BE-51)**

**작성 이력**

| 버전 | 날짜 | 내용 |
| --- | --- | --- |
| v1 | 2026-10-12 | 초안 — WBS v1.5(COMMON-23) 업무 재조정으로 RAG 본류를 넘기며 작성. 작성 김해송 → 인수 B 김현중(BE-52 · BE-54), 관련 D 정민석(BE-53) · C 백승호(BE-55) |
| v1.2 | 2026-10-12 | 정리 결과 공유 반영(비공개 #16 `work/rag-store/`) — 5-1은 공유본으로 바로 R__40을 다시 만드는 방법을 권장으로, 남은 개인정보 경고 12건 오탐 확인 반영 |
| v1.1 | 2026-10-12 | #102 리뷰 반영 — #99 머지 반영, 5-1 대안 · 공통 후속 작업 분리 · `out/rag/` 없이 진행하는 방법 추가, 건수 차이(77 · 68, 28 · 27) 설명, 5장 담당 제안 |
| v1.3 | 2026-10-12 | 5-5 BE-52 처리 결과 반영 — 양형기준 6개 버전 저장소, 버전 기준일(공소제기일 → 발생일) 결정 (BE-52) |

> 반대 방향 인계(7.6.8 캐릭터 · 7.7.1 메인, B 김현중 → A 김해송)는 B가 따로 넘긴다(WBS v1.6).
> 이 문서는 **사건 내용을 담지 않는다**(사건번호 · 사실관계 · 형량 없음). 판례 목록 · 원문은 비공개 저장소(`backend/private-seed`)에 있다.
> 설계 결정은 [AI 판결 오프라인 파이프라인](ai-judgment-pipeline.md) 8-1(COMMON-21), 수집처 · 건수는 [판례 수집처 조사](rag-precedent-sources.md), 테이블은 [ERD](erd.md) 3-4, 명령은 [`tools/ai-judgment/README.md`](../tools/ai-judgment/README.md) "RAG 판례 저장소"를 기준으로 한다. 이 문서는 그 사이를 잇는 **현재 상태 · 남은 일 · 주의점**만 적는다.

---

## 1. 한눈에

| 항목 | 상태 |
| --- | --- |
| 완료 | BE-50 판례 수집(공개 #94 · 비공개 #14), BE-51 판례 저장소(공개 #97 · 비공개 #15), 비공개 시드 서브모듈 갱신(공개 #99, `8bd40f8`) — develop 기준으로 서브모듈을 받으면 R__40이 들어간다. V11이 없는 오래된 브랜치에서 갱신하면 Flyway가 실패하므로 develop을 먼저 합친다 |
| 검색 대상 판례 | **law.go.kr 47건 · 사건 그룹 38개** (살인 27 · 살인미수 16 · 강도살인 3 · 촉탁 · 승낙 1 / 1심 27 · 항소심 20 / 선고 2013~ 27 · 2009~12 12 · ~2008 8) |
| 목표 대비 | 8-1의 "살인 100건 안팎"에 크게 못 미친다(검색 대상 기준 살인 기수 27건. 후보 기준으로는 28건이며 1건이 무죄 보류로 빠짐 — `rag-precedent-sources.md`의 "28건 안팎"은 후보 기준). **확충 방안을 새로 찾아야 한다**(5-3) |
| 운영 DB | **아직 없다.** 로컬 · 임시 DB와 SQL 파일만 있다. RDS는 비용 문제로 나중(7.1.2) |

## 2. 결정 사항 (이 작업에서 정한 것)

| 날짜 | 결정 | 근거 · 영향 |
| --- | --- | --- |
| 10/10 | 사람 확인 없이 자동 판정으로 적재 | 행마다 `flags.verified = false`, 의심 사유는 `flags.checks` |
| 10/12 | **LBox Open 사용 불가** (라이선스 문의 결과) | 이미 넣은 LBox 15건은 `on_hold`(검색 제외)로 남아 있다. **배포 전에 삭제해야 한다**(5-1). 남은 LBox 후보 41건은 정리하지 않는다 |
| 10/12 | 살인 관련 공소사실 무죄 판결(다른 죄만 유죄) 6건 보류 | 형량 · 양형 이유가 살인 기준이 아닌데, 사실관계는 살인 사건과 비슷해 유사도로 걸러지지 않는다(시험에서 상위 5건에 9번 듦). 목록은 비공개 `work/rag-precedent-holds.json` |
| 10/12 | 양형기준 버전은 **공소제기일** 기준, 확인 못 하면 미상 | 판결문에 공소제기일이 거의 없어 지금 `guideline_id`는 **전부 NULL**. 발생일 기준 값은 `flags.guidelineByIncidentDate`에 참고로만 둠 |
| 10/12 | 병합 사건번호는 **번호마다** 해시 (`case_number_hashes text[]`) | 칸 전체를 해시하면 대상 사건(번호 하나)과 달라 자기 사건 제외가 빗나간다. law.go.kr 53건 중 12건이 병합 표기 |
| 10/12 | 검색어 임베딩 · 검색은 백엔드(Java), 판례 적재는 오프라인(Python) | 관리자 페이지 연동 · Java 전환은 BE-56(범위 밖) |

## 3. 무엇이 어디에 있나

### 3-1. 공개 저장소

| 위치 | 역할 |
| --- | --- |
| `backend/src/main/resources/db/migration/V11__create_rag_precedent.sql` | `rag_precedent` 테이블(`vector(3072)` · 사용 보류 · 해시 배열) · 검색 함수 `rag_precedent_search` · `CREATE EXTENSION vector` |
| `backend/.../rag/` | `GeminiQueryEmbeddingClient`(`RETRIEVAL_QUERY`) · `RagSearchService.searchSimilar(caseId)` · `CaseNumberHash.ofAll` · 설정 `rag.*` |
| `tools/ai-judgment/rag_store.py` | `build`(모델로 비식별화 · 요약) · `prepare` / `import`(대화형 세션이 정리할 때) · `embed`(`RETRIEVAL_DOCUMENT`) · `sql`(R__40 생성) |
| `tools/ai-judgment/prompts/rag_precedent_system.md` | 판례 정리(비식별화 · 요약 · 양형 이유 · 형량 · 양형인자) 프롬프트 |
| `tools/ai-judgment/llm.py` `embed()` | Gemini 배치 임베딩(100건 묶음). BE-53 · BE-55도 그대로 쓸 수 있다 |

### 3-2. 비공개 저장소 (`backend/private-seed`)

| 위치 | 역할 |
| --- | --- |
| `seed/R__40_rag_precedent.sql` | 판례 68건(law.go.kr 53 · LBox 15, 보류 21). 손으로 고치지 않는다 |
| `work/BE-50-precedent-candidates.json` · `.md` | 후보 목록(전체 240건, 조건 통과 · 중복 제외 109건). `tools/rag_precedent_candidates.py`로 만든다 |
| `work/rag-precedent-holds.json` | 판례별 사용 보류 `{판례 키: 사유}` |
| `work/rag-store/` (비공개 #16) | **판례 정리 결과 공유본** — law.go.kr 53건의 `precedents/`(요약 · 양형 이유 · 형량 · 메타데이터, 사건번호는 해시만) · `embeddings/`. LBox 없음, 판결문 원문 없음. `rag_store.py`의 `--out`으로 쓴다 |
| `raw/BE-50/` (git 제외) | 판결문 원문. 각자 스크립트로 다시 받는다 |

### 3-3. 로컬에만 있는 중간 결과 (git 제외)

`tools/ai-judgment/out/rag/` — `precedents/`(정리 결과 77건) · `embeddings/` · `manual/inputs`(마스킹 입력). 김해송 PC에만 있다. **law.go.kr 53건은 비공개 `work/rag-store/`에 공유했으므로**(3-2) 인수자는 그 폴더를 쓰면 된다. 77건(law.go.kr 53 · LBox 24)과 R__40 68건의 차이 9건은 비공개 #14 리뷰에서 문장 조각 비교 버그를 고친 뒤 law.go.kr 사본으로 판정돼 후보에서 빠진 LBox 판례다(`sql --candidates`가 후보에 없는 판례를 적재하지 않음). R__40을 다시 만들려면 이 폴더가 필요하므로, 다시 만들 일이 생기면 김해송에게 파일을 받거나 처음부터 build · embed를 다시 돌린다.

## 4. 동작 요약 (넘겨받는 쪽이 알아야 할 규칙)

- **자기 사건 제외**: 대상 사건 `case_source`의 사건번호를 번호마다 해시해 넘기면, 그 해시가 하나라도 있는 판례의 **사건 그룹 전체**(다른 심급 · 사본)를 뺀다. 해시 규칙은 Python `rag_store.case_number_hashes` = Java `CaseNumberHash.ofAll`(교차 테스트 있음). 바꾸면 두 곳을 함께 바꾸고 판례를 다시 적재한다
- **사건 그룹**: 본문 50% 이상 겹침(사본) · 원심 사건번호 인용 · 요약 임베딩 유사도 0.95 이상으로 묶는다
- **점수**: 하한 `rag.search.min-score` 0.6, 상한 `rag.search.max-score` 0.95(이상이면 자기 사건 의심으로 빼고 경고). **둘 다 임시값**(5-4)
- **RAG 꺼짐**: `GEMINI_API_KEY`가 없거나 판례 저장소가 비면 `RagSearchResult.disabled()` → 호출하는 쪽이 퓨샷으로 대체(8-1)
- **임베딩 입력**: 비식별화한 사실관계 요약만. 원문 · 사건번호 · 양형 이유는 보내지 않는다(테스트로 확인)
- **무료 등급**: Gemini 무료 키는 생성 모델이 **하루 20회** 한도라 판례 정리(build)에는 사실상 쓸 수 없었다. 이번 정리는 대부분 대화형 세션(`prepare` / `import`)으로 했다. 임베딩은 무료 키로 문제없었다

## 5. 남은 일 (인계)

### 5-1. 배포 전 LBox 행 삭제 — **필수** (담당 제안: B, 아래 준비가 된 뒤)

R__40에서 LBox 15건을 빼고 다시 만든다. 지금 `rag_store.py`에는 출처를 빼는 옵션이 없다.

**방법 (하나를 고른다)**

| 방법 | 필요한 것 | 내용 |
| --- | --- | --- |
| **C. 공유본으로 다시 만들기 (권장)** | 비공개 `work/rag-store/` (#16 머지) | 공유본에는 LBox가 없으므로 아래 한 번이면 **LBox 행이 빠진 R__40**이 나온다. law.go.kr 53건은 지금 R__40의 행과 같다(2026-10-12 확인). 코드 수정이 필요 없다 |
| A. 출처 제외 옵션 | `out/rag/` | `rag_store.py sql`에 출처 제외 옵션(예: `--exclude-source LBOX`)을 추가하고 R__40을 다시 만든다 |
| B. 후보 목록에서 LBox 끄기 | `out/rag/` | 후보 스크립트(`tools/rag_precedent_candidates.py`)에서 LBox 수집을 끄고 후보 목록을 다시 만든 뒤 `sql --candidates`로 R__40을 다시 만든다(후보에 없는 판례는 적재하지 않는다) |

```bash
# 공개 저장소 tools/ai-judgment에서. P = 비공개 저장소 체크아웃 (../../backend/private-seed)
python3 rag_store.py sql --out $P/work/rag-store --candidates $P/work/BE-50-precedent-candidates.json \
    --holds $P/work/rag-precedent-holds.json --output $P/seed/R__40_rag_precedent.sql
```

A · B는 `out/rag/`(김해송 PC)가 필요하고, 무료 키 하루 20회 한도 때문에 정리부터 다시 하기도 어렵다. C를 쓴다.

**어느 방법이든 이어서 할 일 (공통 후속)**

- 비공개 저장소 `raw/BE-50/lbox/`(각자 로컬) · 후보 목록의 LBox 항목 처리(후보 스크립트에서 LBox 수집 끄기 등)를 정리한다
- 문서(`rag-precedent-sources.md` 1 · 2 · 6장, ERD 3-4, 도구 README)의 "LBox 사용 보류"를 **"사용 불가 · 삭제"**로 고친다. 이때 `rag-precedent-sources.md`의 살인 기수 건수도 이 문서(1장)와 맞춘다

### 5-2. law.go.kr 인증키 발급 · 본문 다시 받기 (담당 제안: 키를 신청한 사람)

- open.law.go.kr 가입 → 활용신청(승인 1 ~ 2일). 지금 본문은 API 가이드 **시험 키(`test`)**로 받았다
- 받은 뒤 `tools/rag_precedent_candidates.py --oc <키>`로 다시 받고, 본문이 같은지 비교한다(같으면 R__40은 그대로). 판례 API 호출 한도도 이때 확인한다
- https 호출이라 macOS python.org Python은 `SSL_CERT_FILE` 설정이 필요하다

### 5-3. 판례 확충 — **다른 방안 필요** (담당 제안: 팀 결정)

LBox가 빠지면서 검색 대상이 law.go.kr 47건(살인 기수 27건)뿐이다. 방안을 고르기 전에 **"살인 기수가 몇 건 미만이면 RAG 대신 퓨샷으로 대체한다"는 기준선**을 먼저 정한다. 검토해 볼 만한 것:

| 방안 | 메모 |
| --- | --- |
| law.go.kr **본문 검색** 결과(약 435건)에서 살인 사건을 더 고르기 | 사건명이 아니라 본문에 "살인"이 들어간 판결이라 대부분 다른 사건이다. 자동 판정 + 사람 선별이 필요 |
| law.go.kr 검색어 넓히기 (존속살해 · 영아살해 · 강도살인 · 촉탁 · 승낙 등 사건명 변형) | 지금은 사건명 "살인 · 살해"만 썼다 |
| 법원도서관 각급법원판결공보 | 25건 중 23건이 law.go.kr와 겹친다. 남는 2건은 살인미수 |
| 사법정보공개포털 판결서 인터넷열람 | 2013년 이후 확정 형사 판결 전체를 볼 수 있지만 **건당 1,000원**이라 팀 결정으로 제외했다. 재검토 여부는 팀 판단 |
| AI Hub 판결서 데이터 | 가공 데이터 · 출처 불명확 · "학습용으로만" 조항 때문에 제외했다 |
| 목표 조정 | 판례가 적으면 8-1대로 RAG 대신 퓨샷으로 대체하는 범위를 정한다 |

### 5-4. 점수 하한 · 상한 확정 (담당 제안: B, BE-54에서 샘플 10건으로)

- 지금 검색 점수는 0.7 ~ 0.85에 몰려 있어 하한 0.6은 거르는 효과가 없다. 상한 0.95는 판례끼리(문서 ↔ 문서) 분포에서 나온 값이라, 실제 검색(사건 개요 ↔ 판례)에서는 거의 걸리지 않는다
- 실제 서비스 사건 10건(8-1 샘플)으로 상위 5건을 확인해 하한(0.85 근처부터) · 상한을 정하고, 같은 사건 쌍의 질의 점수로 상한을 맞춘다

### 5-5. BE-52 · BE-54 (B 김현중)

- **BE-52 양형기준**: (2026-10-12 처리, 담당 A 김해송으로 재배정) 살인범죄 6개 버전(2009 최초 포함)을 데이터화해 `rag_guideline` · 비공개 시드 `R__41`로 넣는다. 버전 기준일은 **공소제기일 → 없으면 발생일**로 정했다(대신했으면 `flags.guidelineBasis`로 표시, 판례 검색은 같은 버전 우선). 판례 `guideline_id` 채우기는 BE-54. [양형기준 저장소](sentencing-guideline-rag.md) 6 · 7장
- **BE-54 generate + RAG**: 생성은 오프라인 Python 파이프라인이다. 검색을 부르는 방법을 정한다
  - (권장) Python에서 `llm.embed(..., task_type="RETRIEVAL_QUERY")`로 검색어를 만들고 DB 함수 `rag_precedent_search`를 직접 부른다. `exclude_hashes`는 `rag_store.case_number_hashes`로 만든다
  - 또는 백엔드 `RagSearchService`를 쓴다(서비스 안 생성 단계, BE-56)
  - RAG가 꺼졌거나 판례가 부족하면 퓨샷 대체. 사건번호가 없는 대상 사건은 해시 제외가 안 되므로 `exclude_groups`나 상한으로 막는다

### 5-6. BE-53 (D 정민석) · BE-55 (C 백승호)

- 판례 저장소는 develop에 있다. 임베딩은 `llm.embed`(배치 100, 차원 검사)를 그대로 쓰면 된다. task type은 비교 용도면 `SEMANTIC_SIMILARITY`(8-1)

## 6. 배포 체크리스트 (운영 DB를 만들 때, 7.1.2)

- [ ] LBox 행 삭제(5-1) 후 R__40 반영
- [ ] pgvector 확장: Docker면 `pgvector/pgvector:pg17` 이미지, RDS면 확장 허용 · 앱 계정 `rds_superuser`(없으면 마스터 계정으로 `CREATE EXTENSION IF NOT EXISTS vector;` 먼저)
- [ ] 비공개 시드 적용 여부와 방식(COMMON-17)
- [ ] RAG를 켤 거면 `GEMINI_API_KEY` 주입(없으면 RAG만 꺼진 채 정상 기동)

## 7. 알려진 한계

| 항목 | 내용 |
| --- | --- |
| 자동 판정 | 유형 · 조건 · 사건 그룹 · 발생일은 패턴 · 겹침 · 유사도로 판정했다. 사람 확인은 하지 않았다 |
| 발생일 | 범행 문장 앞 날짜로 추정. law.go.kr 후보 53건 중 18건은 못 찾았고, 6건은 선고일과 3년 넘게 차이 나 `flags.checks`에 표시 |
| 주된 피고인 | 피고인이 여럿이면 살인으로 처벌된 주된 피고인 한 명 기준으로 정리했다(`notes`) |
| 소년 형량 | 부정기형 2건은 장기를 `prison_months`로 넣었고, 소년법으로 무기징역을 완화한 1건이 있다(`notes`). 다른 판례와 형량을 바로 비교하기 어렵다 |
| 판례 정리 품질 | 모델 · 세션마다 요약 문체가 조금 다르다(Gemini 5건 · 대화형 세션 나머지). 형식 검사는 모두 통과. 남은 개인정보 검사 경고가 있던 12건은 사람이 확인해 **오탐**으로 판정했다(역할어 뒤 일반 명사, 실명 없음 — 공유본 `privacyWarningsReview`) |
