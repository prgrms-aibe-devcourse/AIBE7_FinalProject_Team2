# AI 판결 파이프라인 서비스 (COMMON-50)

오프라인 도구인 `tools/ai-judgment` 파이프라인(BE-31)을 백엔드(Spring Boot)가 HTTP로 부를 수 있게 감싼 얇은 층이다.
**파이프라인 로직은 전혀 건드리지 않는다.** 요청을 설정 파일로 옮기고, 파이프라인을 서브프로세스로 돌리고,
파이프라인이 스스로 쓰는 `state.json`을 읽어 상태를 돌려줄 뿐이다.

```text
관리자 ──▶ backend (관리자 인증 · CSRF, A-14/A-15) ──▶ pipeline (FastAPI :8000) ──▶ pipeline.py run ──▶ 외부 LLM API
                                                              내부 네트워크 전용
```

| 파일 | 내용 |
| --- | --- |
| `app.py` | FastAPI 엔드포인트. 예외를 HTTP 상태로 옮기는 것 말고는 하는 일이 없다 |
| `jobs.py` | 작업 관리 전부 — 설정 파일 쓰기, 서브프로세스 실행, `state.json` 해석, 결과 추리기, 정리 |
| `requirements.txt` | `fastapi` · `uvicorn` 둘뿐. 파이프라인 자체는 여전히 표준 라이브러리만 쓴다 |
| `tests/test_jobs.py` | 작업 관리 단위 테스트. 서브프로세스는 가짜로 바꾼다 — 네트워크 · LLM 호출 없음 |
| `../Dockerfile` | 이 서비스를 담은 이미지. `deploy/docker-compose.pipeline.yml`이 띄운다 |

## 설계에서 꼭 알아야 할 것

- **내부 전용이라 인증이 없다.** 포트를 호스트로 열지 않고, **백엔드만 있는 별도 Compose 네트워크**에 둔다
  (`deploy/docker-compose.pipeline.yml`). 기본 네트워크에 두면 web · redis · postgres · 모니터링 에이전트도 닿는다.
  관리자 인증 · CSRF는 백엔드의 관리자 체인(API 명세 8-1)이 이미 한다. 밖에서 닿을 수 있게 바뀌면 그때는 인증이 필요하다.
- **한 번에 한 작업만 돈다.** 외부 AI 호출 한도와 t3.small 메모리 때문이다. 도는 작업이 있는데 또 시작하면 `409`.
- **상태의 단일 출처는 파이프라인의 `state.json`이다.** 서비스가 진행 상황을 따로 세지 않는다.
  그래서 컨테이너가 재시작돼도 어디까지 갔는지 그대로 남고, 작업 프로세스가 죽어도 서비스는 살아 있다.
- **끄고 시작하는 단계가 있다** (`jobs.py`의 `DISABLED_STAGES`).

  | 단계 | 끈 이유 |
  | --- | --- |
  | `load` | 운영 DB에 직접 쓰는 단계다. 적재는 관리자가 검수한 뒤 **백엔드**가 한다 |
  | `rag` · `contamination` | 시간 · 호출 비용이 크고 등록 화면에서 당장 필요하지 않다. 요청에서 켤 수는 있다 |

- **판결문 원문은 결과에 넣지 않는다** (FR-5-3). 원문은 볼륨에만 있고 A-7로만 본다.
  검수가 끝나면 백엔드가 `DELETE /jobs/{id}`로 지운다. 한 작업의 산출물은 **네 군데**에 흩어지므로
  삭제는 그 넷을 모두 지운다 (아래 "삭제가 지우는 것"). 시작하지 못한 작업(400 · 409)도 그 자리에서 지운다.

## 엔드포인트

백엔드의 A-14/A-15(`docs/api-specification.md`)가 이 응답을 거의 그대로 옮겨 내려준다.

| 메서드 · 경로 | 내용 |
| --- | --- |
| `GET /health` | 컨테이너 헬스체크. `{"status":"UP","runningJobId":...}` — 파이프라인을 돌려 보지는 않는다 |
| `POST /jobs` | 작업 생성 + 시작. **202**와 함께 첫 상태를 돌려준다 (파이프라인은 수십 분 걸려 기다리지 않는다) |
| `GET /jobs/{id}` | 작업 상태 + 단계별 진행 상황 |
| `GET /jobs/{id}/result` | 생성 결과 (사건 · 재판부 판결 · AI 판결 · 가치관 축 · 대본). **판결문 원문은 없다** |
| `GET /jobs/{id}/log?tail=200` | 파이프라인 출력 꼬리 (실패 원인 확인용, text/plain). `tail`은 1 ~ 2000 |
| `POST /jobs/{id}/cancel` | 도는 작업을 프로세스 그룹째 멈춘다. 어디까지 갔는지는 남는다. **이미 끝난 작업은 건드리지 않는다** |
| `DELETE /jobs/{id}` | 작업이 남긴 것을 모두 지운다 (원문 포함, 아래 "삭제가 지우는 것"). 도는 중이면 `409` |

### 삭제가 지우는 것

한 작업의 산출물은 네 군데에 흩어진다. **작업 폴더만 지우면 판결문 원문이 남는다** (PR #219 리뷰에서 발견).

| 위치 | 담긴 것 |
| --- | --- |
| `<작업 폴더>/<작업ID>/` | 입력받은 원문 `raw/` · 설정 · 로그 |
| `out/pipeline/job-<ID>/` | `state.json` · 참고 자료 · 사전 학습 점검 · 가치관 축 |
| `cases/job-<ID>.*` | extract · court 산출물 — **`source_internal.json`이 판결문 원문이다** |
| `out/runs/job-<ID>-*/` | generate 회차 결과 |

뒤의 둘은 `state.json`에 적힌 경로가 아니라 **작업 이름으로 훑어서** 지운다. 단계가 파일을 쓰다 중간에 실패하면
경로가 기록되지 않아, 경로만 보고 지우면 원문이 남기 때문이다.

### 오래된 작업 자동 정리 (COMMON-51)

정상 경로는 **검수를 마친 백엔드가 `DELETE /jobs/{id}`를 부르는 것**이다. 그런데 백엔드가 그 호출 전에
재시작 · 교체되거나, 검수가 미완으로 남거나, 호출이 실패하고 재시도되지 않으면 **판결문 원문이 볼륨에
무기한 남는다.** 그래서 서비스가 스스로도 한 번 더 치운다 (FR-5-3의 마지막 안전장치).

- **언제**: 서비스가 뜨자마자 한 번, 그 뒤로 6시간마다 (백그라운드 스레드 하나, `app.py`의 `lifespan`).
- **무엇을**: 마지막 기록이 보관 기간(`PIPELINE_JOB_RETENTION_DAYS`, 기본 7일)보다 오래된 작업.
  지우는 범위는 위 "삭제가 지우는 것"과 **똑같다** — `DELETE`와 같은 함수를 쓴다.
- **건드리지 않는 것**: 도는 작업. 며칠째 돌고 있어도 끝나야 지운다. `job.json`이 깨져도 폴더 이름으로
  프로세스를 찾아 확인하고, "확인 → 삭제"는 `start`와 같은 잠금 안에서 해 그 사이에 다시 시작되지 못한다.
- **나이 기준**: `finishedAt` · `startedAt` · `createdAt` · `log.txt` · `state.json` 중 **가장 늦은 기록**.
  다시 돌린 작업(BE-107 `--from script`)의 메타에 옛 시각만 남아 있어도 방금 만든 결과를 지우지 않는다.
  아무 기록도 없으면 **작업 폴더 수정 시각**을 쓴다 — `job.json`이 없거나 깨진 폴더도 `raw/`에 원문을
  담고 있을 수 있어, 읽을 값이 없다고 건너뛰면 그 폴더만 영영 남는다.
- **기록**: 지운 작업 ID와 마지막 기록 시각을 로그에 남긴다(원문 · 내용은 남기지 않는다). 지우지 못하면
  경고만 남기고 다음 주기에 다시 시도한다.

기본 7일은 운영에서 실제 검수 소요 시간을 보기 전의 보수적인 값이다. 관찰한 뒤 환경변수로 조정한다
(`docs/decisions/COMMON-51.md`).

`POST /jobs` 본문:

```json
{
  "sources": [{ "originalText": "판결문 전문", "note": "열람 출력본" }],
  "models": ["gemini:gemini-3.7-flash"],
  "stages": { "contamination": { "enabled": true, "runs": 3 } },
  "incidentDate": "2023-04-01",
  "indictmentDate": "2023-09-15"
}
```

- `sources` 필수, 1 ~ 3개. `originalText`가 비면 `400`.
- `models` 필수 (`generate` 단계가 쓸 모델). 없으면 `400`.
- `stages`는 선택. 끈 단계를 다시 켜거나 단계 설정을 덮어쓸 때만 쓴다. 모르는 단계 이름이면 `400`.
- **대본(`script`) 단계는 기본으로 돌지 않는다**(파이프라인 기본값이 꺼짐). 사건 등록에서 대본까지 만들려면
  `stages.script.enabled = true`와 `model` · `cast`(배역)를 함께 보내고 `until`을 `script`로 둔다(BE-107 · BE-108).
  돌리지 않으면 `result`의 `script`가 `null`이고, 사건 · 판결 적재는 그대로 된다.
- `result`의 `script`는 `evidence`(판단 요소별 대사) · `publishable` · `model` · `cast` · `count` · `failed` ·
  `balanceWarning`을 담는다. `publishable`이 `false`면 생성 실패 · 호출 한도 중단으로 **증거 카드와 대사가
  1:1로 어긋난** 상태다 — 적재는 되지만 관리자가 승인할 수 없다(BE-108). 컨테이너 안 경로(`evidenceFile`)는
  내보내지 않는다(가치관 축의 `votesFile`과 같은 이유).
- 대본 전체(1막 해설 · 2막 재판 · 3막 의견, BE-121)까지 만들려면 `stages.script.full = true`와 `classification`
  (분위기 · 쟁점 · 피해자 측 · 부가 인물 · 민감 플래그)을 함께 보낸다. 1막 신문 기사도 이 단계가 먼저 만든다. 그러면
  `result.script`에 `fullScript`(case-1 형식 대본 · `paper` 기사 포함) · `pending`(만들지 못한 단위) · `scriptPublishable`이 더해진다. 컨테이너 안 경로(`scriptFile`)는 내보내지 않는다.
  적재(`case_script` 확장)는 BE-126이라 지금 백엔드는 `fullScript`를 읽지 않는다.
  `load`는 여기서 켜도 되돌려 끈다.

상태 값:

| 작업 | `QUEUED` · `RUNNING` · `SUCCEEDED` · `FAILED` |
| --- | --- |
| 단계 | `PENDING` · `RUNNING` · `DONE` · `SKIPPED`(서비스에서 끔) · `FAILED` |

작업 프로세스가 중간에 사라졌는데 남은 단계가 시작도 못 했으면 `FAILED`로 본다 — 그래야 백엔드가 영원히 폴링하지 않는다.

## 응답 상태 코드

| 코드 | 뜻 |
| --- | --- |
| `400` | 요청이 잘못됐다 (`sources` 없음 · 빈 원문 · 모르는 단계 이름 등) |
| `404` | 그런 작업이 없다. 경로 조작(`../`)도 여기로 떨어진다 |
| `409` | 이미 도는 작업이 있다 / 도는 작업을 지우려 했다 |

## 로컬에서 돌려 보기

```bash
# tools/ai-judgment/service 에서
python3 -m venv .venv && . .venv/bin/activate
pip install -r requirements.txt
GEMINI_API_KEY=... PIPELINE_JOBS_DIR=../out/jobs uvicorn app:app --app-dir . --port 8000

# 다른 터미널에서
curl -s localhost:8000/health
```

컨테이너로 확인하려면 `deploy/README.md`의 "AI 판결 파이프라인 서비스"를 본다.

## 테스트

```bash
# tools/ai-judgment/service 에서 (의존성 없이 표준 라이브러리만으로 돈다)
python3 -m unittest discover tests
```

파이프라인을 실제로 돌리지 않는다 — 서브프로세스는 가짜로 바꾸고, 상태는 `state.json`을 직접 써서 만든다.
**네트워크 · LLM 호출 없음.** CI(`deploy-images.yml`)는 빌드한 이미지 안에서 이 테스트를 그대로 돌린다.

## 환경변수

| 이름 | 내용 |
| --- | --- |
| `PIPELINE_JOBS_DIR` | 작업 폴더 (판결문 원문 · 설정 · 로그). 컨테이너에서는 `/app/out/jobs` — 볼륨 경로와 같아야 한다 |
| `PIPELINE_JOB_RETENTION_DAYS` | 끝난 작업을 자동으로 지우기까지의 보관 기간(일). 기본 **7**, `0` 이하면 자동 정리를 끈다. 정수가 아니면 기본값으로 돌아가고 경고를 남긴다 (아래 "오래된 작업 자동 정리") |
| `ANTHROPIC_API_KEY` · `GEMINI_API_KEY` · `OPENAI_API_KEY` · `GROQ_API_KEY` · `NVIDIA_API_KEY` | 파이프라인이 그대로 쓴다. 없는 공급자의 단계만 실패하고 서비스는 뜬다 |

판결문 원문이 나가는 `extract` · `court` 단계에는 **유료 등급 키만** 쓴다 (무료 등급은 입력을 제품 개선에 쓸 수 있다).

## 왜 이렇게 만들었는지

대안(백엔드에 Java로 이식 · 서브프로세스 직접 실행 등)과 선택 이유는 `docs/decisions/COMMON-50.md`에 있다.
