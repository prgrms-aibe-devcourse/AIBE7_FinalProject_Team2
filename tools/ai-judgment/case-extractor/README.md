# 판결문 가공 스크립트 (`case-extractor`, BE-20)

판결문(PDF · txt)을 넣으면 **비식별화하고 재가공해서** 상위 폴더 도구가 쓰는 사건 입력 형식(`examples/case_input.json`과 같은 `case.json`)으로 내보낸다. BE-13에서 손으로 하던 사건 가공(비식별화 · 섹션 · 판단 요소 · 선고 가능 범위)의 **초안을 만드는 도구**이고, 결과는 반드시 팀이 원 판결문과 대조해 검수한다(REQ-075).

상위 폴더(`tools/ai-judgment`)는 표준 라이브러리만 쓰지만, 이 폴더는 Claude API(`anthropic`)와 PDF 읽기(`pypdf`) 때문에 **패키지 설치가 필요하다.** Python 3.10 이상.

비식별화 모델은 옵션으로 바꿀 수 있다 (BE-35): Claude(기본) 외에 OpenAI · Gemini도 쓴다 (아래 "모델 선택").

## 파일

| 파일 | 역할 |
| --- | --- |
| `extract_case.py` | 실행 스크립트. 텍스트 추출 → 패턴 마스킹 → Claude API → 검사 → 파일 저장 |
| `deidentify.py` | API로 보내기 전 패턴 마스킹, 결과에 남은 개인정보 검사 |
| `schema.py` | 모델 응답 JSON 스키마, 응답 → `case.json` 조립, 판결 누출 검사 (선고 형량 · 재판부 평가 표현) |
| `prompts/extract_system.md` | 비식별화 원칙 · 작성 형식을 담은 시스템 프롬프트 |
| `requirements.txt` | `anthropic`, `pypdf` |
| `tests/` | 단위 테스트 (API를 호출하지 않는다) |

## 준비

```bash
cd tools/ai-judgment/case-extractor
python3 -m venv .venv && source .venv/bin/activate
pip install -r requirements.txt

# API 인증: 둘 중 하나
export ANTHROPIC_API_KEY=...
ant auth login
```

판결문 원본은 git에 올라가지 않는 `tools/ai-judgment/cases/` 안에 둔다(예: `cases/raw/`). 상위 폴더 `.gitignore`가 `cases/`를 막는다.

## 사용법

```bash
# 1. 먼저 API로 보낼 내용을 확인한다 (호출하지 않음)
python3 extract_case.py ../cases/raw/판결문.pdf --name long-marriage-conflict --dry-run
#    → ../cases/long-marriage-conflict.request.md

# 2. 가공
python3 extract_case.py ../cases/raw/판결문.pdf --name long-marriage-conflict

# 같은 사건의 판결문이 여러 개(1심 · 항소심)면 함께 넣는다. 모델에는 판결문마다 머리표를 붙여 한 번에 보낸다
python3 extract_case.py ../cases/raw/1심.pdf ../cases/raw/항소심.pdf --name long-marriage-conflict
```

전체 자동 실행(비식별화 → 생성 → 적재)은 상위 폴더 `pipeline.py`(BE-31)가 이 스크립트를 부른다.

| 옵션 | 기본값 | 설명 |
| --- | --- | --- |
| `--name` | (필수) | 출력 파일 이름. 영어 소문자 · 숫자 · 하이픈만, **사건을 특정할 수 없는 이름**으로 짓는다(`docs/cases` 규칙과 같음) |
| `--out-dir` | `../cases` | 출력 폴더. git에 올라가는 위치로 바꾸지 않는다 |
| `--model` | `claude-opus-5-5` | 모델. Claude는 모델 ID(또는 `anthropic:모델ID`), 다른 공급자는 `openai:모델ID` · `gemini:모델ID` (아래 "모델 선택") |
| `--effort` | `high` | `low` · `medium` · `high` · `xhigh` · `max`. **Claude 전용**(다른 공급자는 무시) |
| `--max-tokens` | 32000 | Claude 외 공급자의 출력 상한 (Claude는 64000 고정) |
| `--dry-run` | | API를 호출하지 않고, 마스킹한 판결문과 프롬프트만 저장한다 |

### 모델 선택 (BE-35)

| `--model` 값 | 호출 방식 | 키 |
| --- | --- | --- |
| `claude-opus-5-5` · `anthropic:claude-…` | Claude SDK. **구조화 출력으로 스키마를 강제**하고 서버 측 대체 모델을 쓴다 | `ANTHROPIC_API_KEY` 또는 `ant auth login` |
| `openai:<모델ID>` | 상위 폴더 `llm.py`(Chat Completions, JSON 모드) | `OPENAI_API_KEY` |
| `gemini:<모델ID>` | 상위 폴더 `llm.py`(generateContent, JSON 응답) | `GEMINI_API_KEY` 또는 `GOOGLE_API_KEY` |

- Claude 외 공급자는 스키마를 강제할 수 없어서 **스키마를 프롬프트에 붙여 보내고**(`--dry-run` 파일에도 그대로 들어간다), 응답이 JSON이 아니거나 스키마와 다르면(타입 · 필수 항목 · 허용 값) 오류 내용을 알려 주며 **최대 2번 다시 요청**한다. 그래도 안 맞으면 멈춘다.
- 모델이 바뀌어도 이후 검사(개인정보 잔존 · 실제 판결 누출 · 서비스 대상 판정)는 같다. `report.json`에 요청한 모델(`requestedModel`)과 실제 응답한 모델(`model`)을 남긴다.
- 모델마다 비식별화 품질이 다르다. **어느 모델이든 결과는 원 판결문과 대조해 검수한다**(REQ-075). Claude 외 모델로 처음 가공할 때는 `warnings` · `reviewNotes`와 실명 · 지명 · 날짜 잔존을 특히 꼼꼼히 본다.
- **실제 판결문은 결제를 연결한 유료 키로만 보낸다.** 로컬 마스킹은 정규식으로 잡히는 값(주민번호 · 전화 · 사건번호 · 법원명 등)만 가리고, 당사자 · 법조인 이름 · 지명 · 날짜 등은 **모델이 비식별화하기 전 상태로 전송**된다. 무료 등급(예: Gemini API 무료 등급)은 약관상 제출 내용을 서비스 개선에 쓰고 사람이 검토할 수 있다. 유료 키도 공급자의 데이터 보관 · 학습 사용 정책(OpenAI 조직 설정의 데이터 보존 등)을 확인하고 팀이 정한 키만 쓴다. 무료 키는 가상 판결문 · 예시로 시험할 때만 쓴다.
- 응답이 정상 종료가 아니면(Gemini `SAFETY` · `RECITATION`, OpenAI `content_filter` 등 차단 · 거절) 다시 보내지 않고 사유를 알려 주며 멈춘다. 형식 오류 재시도 때는 판결문 전체가 다시 전송된다는 점도 참고한다.
- 판결문은 선택한 공급자로 전송된다(로컬 마스킹 후). 실행하는 파이프라인(`pipeline.py`)은 전송 대상 공급자를 먼저 출력한다.

### 모델 대체 체인 (BE-45)

`--model`을 **여러 번** 주면(파이프라인은 `stages.extract.model`을 목록으로) 앞 모델의 호출이 막혔을 때 다음 모델로 넘어간다.

```bash
python3 extract_case.py raw.pdf --name my-case --model gemini:<모델ID> --model openai:<모델ID>
```

- **넘어가는 경우는 호출 자체가 막힌 때뿐이다**: 과부하(503 · 529) · 분당 한도 · 일 한도 · 크레딧 소진(Claude SDK의 rate limit · 529 포함). 앞 모델의 재시도(BE-36, 기본 최대 5번)를 다 쓴 뒤에 넘어가고, 일 한도 · 크레딧 소진은 재시도 없이 바로 넘어간다.
- **품질 문제는 넘어가지 않는다**: 스키마 불일치 · 안전 필터 차단 · 검사 오류(개인정보 잔존 · 형량 누출 · 서비스 대상 아님)는 다른 모델로 덮지 않고 그대로 멈춘다. 원인이 가려지기 때문이다.
- **다음 모델은 같은 판결문으로 처음부터 다시 요청한다.** 앞 모델의 부분 결과는 쓰지 않는다(한 결과에 두 모델이 섞이지 않게). 앞 모델이 쓴 시간 · 비용은 버려진다.
- 스키마 안내는 모델마다 맞춘다(Claude는 구조화 출력, 그 밖은 프롬프트에 스키마 첨부).
- 모든 모델의 API 키를 호출 전에 확인한다. 하나라도 없으면 아무것도 보내지 않고 멈춘다.
- `report.json`에 응답한 모델(`requestedModel` · `model`), 체인(`modelChain`), 건너뛴 모델과 원인(`fallbacks`: `kind`는 `overloaded` · `rate_limit` · `daily_quota`)을 남긴다. 모델을 하나만 주면 예전과 같다.
- **대체 모델에도 판결문이 전송된다.** 목록의 모든 공급자가 외부 전송 대상이므로, 파이프라인은 전송 안내에 목록의 공급자를 모두 적는다. 쓰는 키는 모두 팀이 정한 유료 키여야 한다(위 안내).

### 출력

| 파일 | 내용 | 다음 단계 |
| --- | --- | --- |
| `<name>.case.json` | AI 판결 입력 (`examples/case_input.json` 형식). 실제 판결은 들어 있지 않다 | 상위 README 1단계의 사건 입력 파일 |
| `<name>.court_judgment_internal.json` | 실제 판결 (최종 확정 판결) | 상위 README 2단계 사전 학습 점검에만 쓴다 |
| `<name>.report.json` | 검사 결과 · 검수 메모 | 팀 검수 |
| `<name>.source_internal.json` | 원본 판결문 정보(사건번호 · 법원명 · 선고일 · 심급 · 원문)와 **사건 발생일**(`incidentDate`, BE-38). **마스킹 전에 로컬 정규식으로 꺼낸 값이고 API로 보내지 않는다.** 내부 전용 | 사건 적재 SQL의 `case_source` (`case_seed_sql.py`, BE-31). 못 찾은 값은 파이프라인 설정 `sources[]`에 직접 넣는다 |

**사건 발생일(BE-38)**: 모델이 범죄사실의 범행 날짜를 `incidentDate`(YYYY-MM-DD)로 내고, 스크립트가 ① 형식 ② **원문에 그 날짜가 실제로 나오는지** ③ 선고일보다 늦지 않은지 확인한다. 하나라도 어긋나면 넣지 않고 경고를 남긴다(경고에 날짜 값은 적지 않는다). 확인한 값은 `source_internal.json`에만 두고 `case.json` · 보고서 · AI 입력에는 넣지 않는다. 적재 SQL이 `legal_case.incident_date`에 넣는다(양형기준 버전 판단용, 서비스 화면에는 보이지 않음).

`case.json`에는 목록 카드 값(`listing`: `shortIntro` · `keywords` · `difficulty` · `estimatedMinutes`)도 들어간다. AI 판결 프롬프트에는 들어가지 않는다(`build_prompt.py`가 허용 항목만 고른다). `court_judgment_internal.json`에는 `compare.py --court`가 대조할 `caseTitle`이 들어간다.

`report.json`에는 다음이 들어간다.

- `status`: `NEEDS_REVIEW`(오류 없음, 검수 필요) 또는 `ERROR`
- `eligibility`: 서비스 대상 판결인지 모델이 판정한 값(`eligible`, 어긋난 조건 `reasons`). 기준은 `docs/cases/README.md` 1장 선정 조건(최종 확정 · 살인/사기/상해 단일 범행 · 형 선고 · 양형기준 적용과 근거 · 자백). false면 경고를 내고, 파이프라인은 기본으로 멈춘다
- `premasked`: 로컬에서 미리 가린 항목과 개수
- `deidentifiedItems`: 모델이 비식별화한 항목 종류 (`legal_case.deidentified_items` 후보)
- `factorExtras`: 판단 요소별 `preLabel` · `summaryTag` (`case.json` 형식에는 없지만 `factor` 시드에 필요)
- `penaltyRuleBasis`: 형벌별 선고 가능 범위 계산 근거 (`penalty_rule.allowed_basis` 후보)
- `errors` · `warnings` · `reviewNotes`

**검사에서 오류가 하나라도 나오면 `case.json`을 만들지 않고 `report.json`만 남긴다.** 같은 이름의 이전 결과 파일(`case.json`, `court_judgment_internal.json`, `--dry-run`의 `request.md`)도 지운다. 프롬프트나 입력을 고쳐 다시 실행한다.

## 재판부 판결 초안 (`court_draft.py`, BE-38)

비식별화 결과(`case.json` · `source_internal.json` · `court_judgment_internal.json`)로 재판부 판결(COURT) 초안을 만든다. 파이프라인 `court` 단계가 부르고, 단독으로도 실행한다.

```bash
python3 court_draft.py --name long-marriage-conflict --model openai:<모델ID>
#    → ../cases/<name>.court_draft.json (BE-14 형식, 내부 전용) · ../cases/<name>.court_report.json
python3 court_draft.py --name long-marriage-conflict --model gemini:<모델ID> --model openai:<모델ID>   # 앞 모델이 막히면 다음 모델 (BE-45)
```

| 단계 | 내용 |
| --- | --- |
| 입력 | 원문을 정규식으로 마스킹해 모델에 보낸다(비식별화 단계와 같은 마스킹). 판단 요소 목록과 최종 형벌 종류를 함께 준다 |
| 모델 응답 | 한 줄 요약 · 판결 이유 · 쉬운 설명 · 판결문 발췌 · 요소별 방향과 근거 · 부가 처분 · 확인 메모. **형량은 모델이 쓰지 않고** 추출기 값을 쓴다 |
| 인용 대조 | 발췌 · 근거는 원문을 글자 그대로 옮기고, 식별 정보를 바꾼 부분만 `⟦ ⟧`로 감싸게 한다. 스크립트가 `⟦ ⟧` 밖의 글자를 원문과 대조하고(공백만 무시), 다르면 오류. `⟦ ⟧` 하나는 원문 **1 ~ 40자**(공백 제외)를 대신하고 안의 말은 **20자 이내**다 — 원문 사이에 없는 말을 끼워 넣는 것(0자 대체)과 문장을 통째로 넣는 것을 막는다. 원문 그대로인 부분이 너무 적어도(대부분을 `⟦ ⟧`로 감싸 대조를 피하면) 오류. 대조는 정규식 역추적 없이 위치 집합으로 찾아 반복 단어가 많은 판결문에서도 빠르다. 화면용 글에서는 `⟦ ⟧` 표시만 지운다 |
| 형량 교차 확인 | **최종 판결문**(설정 `finalSourceIndex`, 없으면 심급이 가장 높은 판결)의 **주문**(`징역 …에 처한다` · `벌금 …원에 처한다` · `…년 …월간 (위 · 각) 형의 집행을 유예한다` 등)을 로컬 정규식으로 읽어 추출기 값과 맞는지 본다. 최종 판결 주문에 형이 없으면(상소 기각) 아래 심급 주문으로 내려간다. 하급심 형량을 뽑은 추출 오류도 이렇게 잡는다. 맞지 않으면 모델을 부르기 전에 멈춘다. 주문을 찾지 못하면 경고 |
| 그 밖의 검사 | 판단 요소 번호(목록 안 · 중복 없음), 요약 100자, 평가 표현, 부가 처분 종류(사회봉사 · 몰수), 개인정보 잔존(정확한 날짜 · 나이 · 법원명 · 사건번호 등) |
| 재시도 | 형식 · 대조 오류가 있으면 오류 내용을 알려 주며 최대 2번 다시 요청. 차단 · 거절 종료는 재시도하지 않는다. 끝내 통과하지 못하면 초안을 만들지 않고 보고서만 남긴다 |

- 초안은 **사실 기록**이다. 자동 검사는 인용 · 형량의 형식적 정확성만 본다. 요약 · 이유가 재판부 판단을 왜곡하지 않았는지, 요소 방향이 맞는지는 원 판결문과 대조해 사람이 검수한다(REQ-075 · 077, 관리자 후검수 BE-33).
- 초안 파일에는 실제 판결이 들어 있다. AI 판결 입력(`build_prompt.py`)에 넣지 않는다(FR-4-1).
- 출력 형식은 BE-14 JSON과 같아 비공개 저장소 `tools/court_judgment_to_sql.py`(공개 반영 R__20)와 공개 저장소 `court_seed_sql.py`(비공개 적재) 둘 다 받는다.

## 처리 순서

1. **텍스트 추출**: PDF는 `pypdf`, txt는 UTF-8 · CP949 순서로 읽는다. 법원 판결문 PDF는 AES로 암호화된 경우가 많아 `cryptography` 패키지가 필요하다(`requirements.txt`에 포함. 없으면 `cryptography>=3.1 is required for AES algorithm` 오류로 읽지 못한다). 텍스트가 거의 없으면(스캔본) 멈춘다. 스캔본은 OCR로 txt를 만든 뒤 넣는다.
2. **패턴 마스킹 (로컬)**: 정규식으로 확실히 잡히는 값을 `[사건번호]`처럼 바꾼 뒤에 API로 보낸다.
    - 주민등록번호, 이메일, 전화번호, 계좌번호, 사건번호, 압수번호, 법원명, 차량번호, 상세 주소(도로명 · 동호수 · 번지)
    - 날짜 · 나이는 가리지 않는다. 모델이 "사건 3개월 전", "70대"처럼 바꾸는 데 필요하다.
3. **비식별화 · 구조화 (Claude API)**: `prompts/extract_system.md`의 원칙대로 인명 · 지명 · 날짜 · 나이 · 직업 · 발언 · 범행 도구 등을 일반화하고, 양형 사실은 유지한다(FR-1-1). 구조화 출력으로 스키마에 맞는 JSON만 받는다. 모델이 거절하면 서버 측 대체 모델이 이어서 처리한다(`fallbacks: "default"`).
4. **검사**
    - **오류**: 마스킹 표시가 남음, 주민등록번호 · 전화번호 · 사건번호 · 법원명 · 상세 주소 · 정확한 날짜 · 정확한 나이가 개요 · 섹션 · 판단 요소 · `reviewNotes` · 형벌 규칙 근거에 남음(보고서에는 위치와 규칙만 적고 원본 값은 적지 않으며, `reviewNotes` · 형벌 규칙 근거에서 걸린 값은 `[삭제됨]`으로 바꿔 저장한다), 실제 선고 형량(예: "징역 10년")이 AI 입력에 드러남, `build_prompt.py`의 사건 입력 검사 실패, 판단 요소 길이 · OVERVIEW 요소 없음 · 형벌 규칙 형식 오류
    - **경고**: "피고인 ○○○"처럼 역할어 뒤에 실명일 수 있는 말, 재판부 판단이 드러날 수 있는 "선고"라는 말, 재판부의 평가 · 결론으로 보이는 표현("죄책", "엄중", "참작한다", "용서 · 정당화될 수 없다", "봄이 상당하다", "유리한 · 불리한 정상", "재판부" 등. 용어 설명은 제외. 검사 · 피고인 측 섹션에서는 "재판부" · "원심"이 함께 없으면 "양형 이유 표현일 수 있음" 안내로 낮춘다), OVERVIEW 요소에 `preLabel` 없음, 형벌 규칙은 모델이 계산한 값이라는 안내

## 지켜야 할 것

- 판결문 텍스트는 가공을 위해 Claude API로 전송된다. **법원이 공개한 판결문(가명 처리본)을 넣는다.** 보낼 내용은 `--dry-run`으로 미리 확인할 수 있다.
- 결과는 초안이다. 팀이 원 판결문과 대조해 **핵심 사실 누락, 비식별화로 인한 의미 변경, 실제 판결 누출**을 확인한다(REQ-075). 확인 기준은 `docs/cases/README.md` 템플릿과 샘플(`docs/cases/sample-virtual-murder.md`)의 "가공 검증" 표를 따른다.
- `penaltyRules`(선고 가능 범위)와 `recommended`(권고 범위)는 모델이 판결문에서 계산한 값이다. 법조문 · 양형기준으로 다시 확인한다(ERD `penalty_rule`). `penaltyRules`는 모델 응답 순서와 관계없이 `DEATH` → `LIFE` → `PRISON` → `FINE`(무거운 형벌부터)로 정렬해 `case.json`에 넣는다. 시드 SQL의 `penalty_rule.display_order`도 이 순서로 매긴다(ERD v1.9).
- `references.similarCases`는 비워 둔다. 대상 사건 판결문만으로는 만들 수 없고, 대상 사건을 뺀 유사 판례를 팀이 채운다(FR-4-2).
- `caseId`는 `null`(파일 구분용일 뿐 DB와 맞출 필요 없음), `factorId`는 1부터 붙여 시드 SQL의 `factor.display_order`와 맞춘다(상위 README 1단계). 적재 뒤 DB 값으로 바꿀 필요가 없다.
- 출력 파일과 판결문 원본은 git에 올리지 않는다.

## 프롬프트 · 스키마를 고칠 때

`schema.py`의 `EXTRACT_PROMPT_VERSION`을 올린다. `report.json`의 `promptVersion`에 기록된다.

## 테스트

```bash
python3 -m unittest discover tests
```

설명용 가상 판결문으로 마스킹 · 검사 · 조립 · 파일 저장을 확인한다. API는 가짜 함수로 바꿔 호출하지 않는다.
