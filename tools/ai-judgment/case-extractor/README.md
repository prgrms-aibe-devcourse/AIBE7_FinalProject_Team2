# 판결문 가공 스크립트 (`case-extractor`, BE-20)

판결문(PDF · txt)을 넣으면 **비식별화하고 재가공해서** 상위 폴더 도구가 쓰는 사건 입력 형식(`examples/case_input.json`과 같은 `case.json`)으로 내보낸다. BE-13에서 손으로 하던 사건 가공(비식별화 · 섹션 · 판단 요소 · 선고 가능 범위)의 **초안을 만드는 도구**이고, 결과는 반드시 팀이 원 판결문과 대조해 검수한다(REQ-075).

상위 폴더(`tools/ai-judgment`)는 표준 라이브러리만 쓰지만, 이 폴더는 Claude API(`anthropic`)와 PDF 읽기(`pypdf`) 때문에 **패키지 설치가 필요하다.** Python 3.10 이상.

## 파일

| 파일 | 역할 |
| --- | --- |
| `extract_case.py` | 실행 스크립트. 텍스트 추출 → 패턴 마스킹 → Claude API → 검사 → 파일 저장 |
| `deidentify.py` | API로 보내기 전 패턴 마스킹, 결과에 남은 개인정보 검사 |
| `schema.py` | 모델 응답 JSON 스키마, 응답 → `case.json` 조립, 판결 누출 검사 |
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
```

| 옵션 | 기본값 | 설명 |
| --- | --- | --- |
| `--name` | (필수) | 출력 파일 이름. 영어 소문자 · 숫자 · 하이픈만, **사건을 특정할 수 없는 이름**으로 짓는다(`docs/cases` 규칙과 같음) |
| `--out-dir` | `../cases` | 출력 폴더. git에 올라가는 위치로 바꾸지 않는다 |
| `--model` | `claude-opus-5-5` | Claude 모델 |
| `--effort` | `high` | `low` · `medium` · `high` · `xhigh` · `max` |
| `--dry-run` | | API를 호출하지 않고, 마스킹한 판결문과 프롬프트만 저장한다 |

### 출력

| 파일 | 내용 | 다음 단계 |
| --- | --- | --- |
| `<name>.case.json` | AI 판결 입력 (`examples/case_input.json` 형식). 실제 판결은 들어 있지 않다 | 상위 README 1단계의 사건 입력 파일 |
| `<name>.court_judgment_internal.json` | 실제 판결 (최종 확정 판결) | 상위 README 2단계 사전 학습 점검에만 쓴다 |
| `<name>.report.json` | 검사 결과 · 검수 메모 | 팀 검수 |

`report.json`에는 다음이 들어간다.

- `status`: `NEEDS_REVIEW`(오류 없음, 검수 필요) 또는 `ERROR`
- `premasked`: 로컬에서 미리 가린 항목과 개수
- `deidentifiedItems`: 모델이 비식별화한 항목 종류 (`legal_case.deidentified_items` 후보)
- `factorExtras`: 판단 요소별 `preLabel` · `summaryTag` (`case.json` 형식에는 없지만 `factor` 시드에 필요)
- `penaltyRuleBasis`: 형벌별 선고 가능 범위 계산 근거 (`penalty_rule.allowed_basis` 후보)
- `errors` · `warnings` · `reviewNotes`

**검사에서 오류가 하나라도 나오면 `case.json`을 만들지 않고 `report.json`만 남긴다.** 같은 이름의 이전 결과 파일도 지운다. 프롬프트나 입력을 고쳐 다시 실행한다.

## 처리 순서

1. **텍스트 추출**: PDF는 `pypdf`, txt는 UTF-8 · CP949 순서로 읽는다. 텍스트가 거의 없으면(스캔본) 멈춘다. 스캔본은 OCR로 txt를 만든 뒤 넣는다.
2. **패턴 마스킹 (로컬)**: 정규식으로 확실히 잡히는 값을 `[사건번호]`처럼 바꾼 뒤에 API로 보낸다.
    - 주민등록번호, 이메일, 전화번호, 계좌번호, 사건번호, 압수번호, 법원명, 차량번호, 상세 주소(도로명 · 동호수 · 번지)
    - 날짜 · 나이는 가리지 않는다. 모델이 "사건 3개월 전", "70대"처럼 바꾸는 데 필요하다.
3. **비식별화 · 구조화 (Claude API)**: `prompts/extract_system.md`의 원칙대로 인명 · 지명 · 날짜 · 나이 · 직업 · 발언 · 범행 도구 등을 일반화하고, 양형 사실은 유지한다(FR-1-1). 구조화 출력으로 스키마에 맞는 JSON만 받는다. 모델이 거절하면 서버 측 대체 모델이 이어서 처리한다(`fallbacks: "default"`).
4. **검사**
    - **오류**: 마스킹 표시가 남음, 주민등록번호 · 전화번호 · 사건번호 · 법원명 · 상세 주소 · 정확한 날짜 · 정확한 나이가 개요 · 섹션 · 판단 요소 · `reviewNotes` · 형벌 규칙 근거에 남음(보고서에는 위치와 규칙만 적고 원본 값은 적지 않으며, `reviewNotes` · 형벌 규칙 근거에서 걸린 값은 `[삭제됨]`으로 바꿔 저장한다), 실제 선고 형량(예: "징역 10년")이 AI 입력에 드러남, `build_prompt.py`의 사건 입력 검사 실패, 판단 요소 길이 · OVERVIEW 요소 없음 · 형벌 규칙 형식 오류
    - **경고**: "피고인 ○○○"처럼 역할어 뒤에 실명일 수 있는 말, 재판부 판단이 드러날 수 있는 "선고"라는 말, OVERVIEW 요소에 `preLabel` 없음, 형벌 규칙은 모델이 계산한 값이라는 안내

## 지켜야 할 것

- 판결문 텍스트는 가공을 위해 Claude API로 전송된다. **법원이 공개한 판결문(가명 처리본)을 넣는다.** 보낼 내용은 `--dry-run`으로 미리 확인할 수 있다.
- 결과는 초안이다. 팀이 원 판결문과 대조해 **핵심 사실 누락, 비식별화로 인한 의미 변경, 실제 판결 누출**을 확인한다(REQ-075). 확인 기준은 `docs/cases/README.md` 템플릿과 BE-13 사건 파일의 "가공 검증" 표를 따른다.
- `penaltyRules`(선고 가능 범위)와 `recommended`(권고 범위)는 모델이 판결문에서 계산한 값이다. 법조문 · 양형기준으로 다시 확인한다(ERD `penalty_rule`).
- `references.similarCases`는 비워 둔다. 대상 사건 판결문만으로는 만들 수 없고, 대상 사건을 뺀 유사 판례를 팀이 채운다(FR-4-2).
- `caseId`는 `null`, `factorId`는 1부터 붙인 임시값이다. 시드 적재 뒤 DB 값으로 바꾼다(상위 README 1단계).
- 출력 파일과 판결문 원본은 git에 올리지 않는다.

## 프롬프트 · 스키마를 고칠 때

`schema.py`의 `EXTRACT_PROMPT_VERSION`을 올린다. `report.json`의 `promptVersion`에 기록된다.

## 테스트

```bash
python3 -m unittest discover tests
```

설명용 가상 판결문으로 마스킹 · 검사 · 조립 · 파일 저장을 확인한다. API는 가짜 함수로 바꿔 호출하지 않는다.
