# 모니터링 (Prometheus BE-70 WBS 7.2.2 · Loki/Alloy BE-71 WBS 7.2.3 · Grafana COMMON-43 WBS 7.2.4 · 경보 7.3.1 · 7.3.2)

Prometheus로 백엔드 Actuator 메트릭(7.2.1)을 수집하고, Loki + Grafana Alloy로 백엔드 JSON 로그(7.2.3)를 적재하고,
Grafana로 대시보드 4종(7.2.4)을 띄운다. Prometheus가 경보 규칙(7.3.1)을 평가해 Alertmanager로 보내고,
Alertmanager가 그룹핑 · 억제한 뒤 Slack으로 알린다(7.3.2).

**Grafana는 데이터를 저장하지 않는다.** 데이터는 Prometheus(메트릭) · Loki(로그)에 있고 Grafana는 그걸 읽어 그리는
조회 계층일 뿐이다. 그래서 Grafana가 죽어도 데이터는 남지만, Prometheus · Loki가 죽은 시간대의 데이터는 영영 없다 —
아래 "별도 EC2" 배치가 이 차이를 전제로 한다.

| 파일 | 내용 |
| --- | --- |
| `docker-compose.yml` | **로컬** 전체 스택(Alertmanager · Prometheus · Loki · Alloy · Grafana · node_exporter). `backend/docker-compose.yml`(DB)과 독립적으로 켜고 끈다 |
| `prometheus.yml` | **로컬** scrape 대상 · 간격 · 경보 규칙 파일 · Alertmanager 주소. 보존 기간은 `docker-compose.yml`의 `--storage.tsdb.retention.time` |
| `alert-rules.yml` | **경보 규칙(7.3.1)**. 로컬 · 운영 공용 — 5xx 비율 · p95 지연 · DB 커넥션 고갈 · AI 호출 실패율 · 사건 등록 배치 지연 |
| `alertmanager.yml` | **Alertmanager 라우팅 · 그룹핑 · 억제 · Slack 채널(7.3.2)**. 로컬 · 운영 공용. 웹훅 URL은 여기 없다(아래) |
| `secrets/` | Slack 웹훅 URL(`slack-webhook-url`, 커밋 안 함, `.example` 참고). `monitoring/targets/`와 같은 원칙 |
| `loki-config.yml` | Loki 단일 바이너리 구성(파일시스템 저장), 보존 기간. 로컬 · 운영 공용 |
| `alloy-config.alloy` | **로컬** — `backend/logs`의 JSON 로그를 tail해서 Loki로 보낸다 (Promtail 대체, 아래 참고). (BE-63) 웹 429 전용 로그 규칙도 같이 들어 있지만, 로컬 Compose에는 웹이 없어 비어 있다 |
| `grafana/provisioning/` | 데이터소스(Prometheus · Loki) · 대시보드 공급자 설정. 로컬 · 운영 공용 |
| `grafana/dashboards/*.json` | 대시보드 4종. 로컬 · 운영 공용이고 7.3.3 Grafana Cloud에도 같은 파일을 가져간다 |
| `docker-compose.prod.yml` | **모니터링 전용 EC2**용(Alertmanager · Prometheus · Loki · Grafana) |
| `prometheus.prod.yml` | **운영** scrape 설정 · 경보 규칙 파일 · Alertmanager 주소. 대상 주소는 `targets/`(커밋 안 함)에서 읽는다 |
| `.env.example` | 운영 환경변수 예시(Grafana 계정 · 보존 기간 등) |

메인 EC2 쪽 에이전트(Alloy · node_exporter)는 `deploy/docker-compose.monitoring-agent.yml` · `deploy/alloy-agent.alloy`에 있다.
메인 서버에는 저장소에서 `deploy/`만 두기 때문이다(`deploy/README.md`).

## 대시보드 4종

Grafana 폴더 "남불법정"에 들어간다. 각 패널의 ⓘ에 "이 수치를 어떻게 읽는지"를 적어 뒀다.

| 대시보드 | 보는 것 | 비고 |
| --- | --- | --- |
| 서비스 | 처리량 · 5xx 비율 · p95 지연 · 라우트별 p95 · ERROR 로그 | 7.3.1 경보(5xx · p95)와 같은 식을 쓴다 |
| AI 파이프라인 | AI 호출 성공률 · 체험 시작 · 판결 확정 · 전환율 | 지금 계측된 AI 호출은 **RAG 임베딩뿐**이다(아래 참고) |
| DB | HikariCP 커넥션 사용률 · 대기 · 획득 실패 · 획득/사용 시간 | PostgreSQL 서버 내부 지표는 RDS 전환 후(아래 참고) |
| 인프라 | CPU · 메모리 · 디스크 · 네트워크 · 수집 상태 · JVM 힙 · GC | 메모리를 맨 앞에 둔 이유는 t3.small 여유가 250MB뿐이라서다 |

대시보드는 **파일이 원본이다.** 화면에서 고친 내용은 저장되지 않는다(`allowUiUpdates: false`) — 고칠 때는 JSON을
고쳐 커밋한다. 화면에서 손으로 만들면 그 서버의 Grafana DB에만 남아 7.3.3에서 처음부터 다시 만들어야 한다.

### 지금 비어 있는 것 (의도적으로 미룬 것)

- **AI 파이프라인**: `lawnambul_ai_call_total`이 재는 것은 RAG 유사 판례 검색의 임베딩 호출(`GeminiQueryEmbeddingClient`)뿐이다.
  판결문 생성은 오프라인 파이프라인(`tools/ai-judgment`)에서 돌아 서버 메트릭에 잡히지 않는다. 생성 단계 계측은 COMMON-40과 함께 본다.
- **DB**: `postgres_exporter`를 붙이지 않았다. 7.1.2에서 RDS로 전환하면(**10/28 예정**, COMMON-43 협의에서 D 확인) 연결 대상이 바뀌어 다시 해야 하기 때문이다.
  대신 7.3.1 경보가 실제로 쓸 지표인 **HikariCP 커넥션 풀**로 먼저 만들었다.

## 경보 (7.3.1 · 7.3.2)

Prometheus가 `alert-rules.yml`의 규칙을 15초마다(평가 주기) 다시 계산하고, 조건이 일정 시간(`for:`) 계속
참이면 Alertmanager로 알린다. Alertmanager는 그걸 묶고(`group_by`) 걸러서(`inhibit_rules`) Slack으로 보낸다.

**운영에서 이 규칙 · Alertmanager는 어디서 도나 (PR #262 리뷰 9번)**: 지금은 **안 돈다.** 이 저장소의
`docker-compose.yml`(로컬) · `docker-compose.prod.yml`(전용 EC2 차선, 지금 안 씀)에서만 로컬 확인용으로
띄운다. 운영 EC2는 1대뿐이고 별도 모니터링 서버를 두지 않기로 했다(COMMON-43 1순위 = Grafana Cloud).
7.3.3에서 메트릭은 메인 EC2의 Alloy가 Cloud로 push하고(`deploy/alloy-agent.alloy`), **경보 평가 ·
Alertmanager도 운영에서는 Grafana Cloud 쪽(Cloud Alerting)으로 옮긴다** — 메인 서버가 죽으면 그 서버
안의 Alertmanager도 같이 죽어 정작 가장 필요한 순간에 조용해지기 때문이다(COMMON-62). 이 `alert-rules.yml`은
Prometheus 표준 문법이라 Cloud 쪽으로 그대로 옮길 수 있다 — Cloud 쪽 반영은 별도 이슈.

| 규칙 | 조건 | 지속 | 심각도 |
| --- | --- | --- | --- |
| `HighErrorRate` | 5분간 5xx 비율 > 5% (요청이 5분에 30건은 있을 때만) | 5m | critical |
| `HighLatencyP95` | 5분간 p95 응답 시간 > 2초 (관리자 경로 제외) | 5m | warning |
| `DbConnectionPoolNearExhaustion` | HikariCP 사용률 > 90% | 3m | critical |
| `DbConnectionPending` | 커넥션을 기다리는 스레드 있음 | 2m | warning (위 경보가 울리는 동안은 억제됨) |
| `HighAiCallFailureRate` | 10분간 AI 호출 실패율 > 30% (호출이 10분에 30번은 있을 때만) | 5m | warning |
| `CaseImportStuck` | 진행 중인 사건 등록 작업이 10분 넘게 안 끝남 | 2m | warning |
| `BackendDown` | backend 타깃이 스크레이프에 응답 안 함 | 1m | critical |
| `BackendScrapeTargetMissing` | backend job 자체가 설정에서 사라짐 | 1m | critical |
| `Watchdog` | 항상 참(`vector(1)`) — 경보 경로 자체가 살아 있는지 확인용, Slack에는 안 보냄 | — | none |
| `AlertDeliveryFailing` | Alertmanager의 알림 전송이 계속 실패함 | 5m | warning |

`CaseImportStuck`이 보는 지표(`lawnambul_case_import_oldest_running_seconds`)는 기존에 없던 걸 7.3.1에서
새로 추가했다(`CaseImportMetrics`). 스크레이프 때마다 DB를 직접 조회하면 DB 커넥션 풀이 가득 찬 순간(=
`DbConnectionPoolNearExhaustion`이 울려야 할 순간)에 그 조회가 커넥션을 기다리다 스크레이프 전체가
타임아웃될 수 있어(PR #262 리뷰), **1분마다 백그라운드(`@Scheduled`)로 미리 계산해 캐시해 두고
스크레이프 때는 캐시만 읽는다.**

`BackendDown` · `BackendScrapeTargetMissing`이 없으면, 위 다른 규칙은 전부 backend 지표가 들어와야
계산되므로 **backend가 죽으면 지표 자체가 끊겨 아무 경보도 안 울린다** — 가장 큰 장애에서 오히려
조용해지는 구멍이었다(PR #262 리뷰, 머지 전 필수 수정). `Watchdog` · `AlertDeliveryFailing`은 지금은
**로컬 규칙 확인용이다.** "경보가 안 온다"를 역으로 감지하는 구조(Dead man's switch)인데, `Watchdog`은
`null` 수신자로 보내 외부에 생존 신호를 전달하지 않고, Slack 전송 경로 자체가 죽으면 `AlertDeliveryFailing`도
같은 경로로 전달되지 못한다 — 즉 이 구성만으로는 "신호가 조용해졌다"는 걸 알아챌 외부 관찰자가 없다.
실제로 경보 경로 장애를 감지하려면 Alertmanager 바깥에서 도는 독립된 외부 감시가 별도로 필요하다.

### Slack 웹훅 설정

1. Slack에서 수신 웹훅(Incoming Webhook)을 만들고 보낼 채널을 고른다 — **이 채널이 그 웹훅의 고정 채널이다.**
   `alertmanager.yml`에 `channel:`을 따로 지정하지 않는다(다른 채널 이름을 주면 그 채널이 실제로 있어야 하고,
   없으면 `404 channel_not_found`로 실패한다 — 직접 겪은 문제).
2. `monitoring/secrets/slack-webhook-url.example`을 복사해 `slack-webhook-url`로 만들고 웹훅 URL을 넣는다.
   ```bash
   cp secrets/slack-webhook-url.example secrets/slack-webhook-url
   vi secrets/slack-webhook-url   # 발급받은 https://hooks.slack.com/services/... 값으로 교체
   chmod 600 secrets/slack-webhook-url
   ```
   **Mac · Windows(Docker Desktop)는 이대로 된다** — 직접 테스트해 Slack 수신까지 확인함. Docker
   Desktop의 파일 공유 계층이 번역해 주기 때문이다. **운영(Linux EC2)은 다르다** — "운영 적용" 절 참고,
   `chmod 600` 그대로 두면 컨테이너가 파일을 못 읽어 경보가 조용히 안 간다. 이 파일은 `.gitignore`에
   있다 — 커밋되지 않는다.
3. `docker compose up -d`(또는 `alertmanager`만 `docker compose restart alertmanager`)로 반영한다.

### 확인

- http://localhost:9090/alerts 에서 규칙 상태(`inactive` · `pending` · `firing`)를 본다.
- http://localhost:9093 에서 Alertmanager가 받은 경보를 본다.
- 실제로 Slack까지 가는지 테스트 경보를 직접 보낼 수 있다(Prometheus 조건을 기다리지 않고):
  ```bash
  curl -X POST http://localhost:9093/api/v2/alerts -H 'Content-Type: application/json' -d '[{
    "labels": {"alertname": "TestAlert", "severity": "warning"},
    "annotations": {"summary": "테스트", "description": "Alertmanager → Slack 연결 확인"},
    "startsAt": "'"$(date -u +%Y-%m-%dT%H:%M:%S.000Z)"'"
  }]'
  ```
  `group_wait`(30s) 뒤에 Slack 채널에 메시지가 오면 정상이다. 안 오면 `docker compose logs alertmanager`에서
  `Notify for alerts failed` 줄을 찾는다(웹훅 URL 오타 · 채널 문제가 대부분이다).

## Promtail 대신 Grafana Alloy를 쓴 이유

WBS 7.2.3은 "Promtail(또는 Grafana Alloy)"로 둘 다 열어 뒀지만, **Promtail은 2026-03-02에 공식 EOL(지원 종료)**됐다
(커뮤니티 LTS가 2025-02부터였고, 상용 지원도 2026-02-28에 끝났다). 새로 시작하는 작업에 이미 수명이 끝난 도구를
고를 이유가 없어 Grafana Alloy로 진행했다. 설정 형식(River/Alloy 구문)은 다르지만 역할은 같다 — 로그 파일을 tail해서
Loki로 보낸다.

## 로컬에서 띄우기

1. 백엔드를 먼저 띄운다: `cd backend && ./gradlew bootRun` (관리 포트 8081이 열린다, 7.2.1)
2. Prometheus를 띄운다:
   ```bash
   cd monitoring
   docker compose up -d
   ```
3. **http://localhost:3000 에서 대시보드를 본다** (초기 계정 `admin` / `admin`, 로컬 전용).
   왼쪽 대시보드 목록 → 폴더 "남불법정"에 4종이 들어 있다. 데이터소스도 함께 자동 등록된다.
4. http://localhost:9090/targets 에서 `backend` job이 **UP**인지 확인한다. `host.docker.internal:8081`에 연결하므로,
   백엔드가 아직 안 떠 있으면 DOWN으로 보이는 게 정상이다 — 백엔드를 띄운 뒤 몇 초 안에 UP으로 바뀐다.
   (`node` job은 백엔드와 무관하게 바로 UP이다.)
5. http://localhost:9090/graph 에서 `lawnambul_experience_started_total` 같은 커스텀 메트릭을 바로 조회할 수 있다.
6. 로그 확인: http://localhost:3100/ready 로 Loki 기동을 확인한 뒤, LogQL로 직접 조회할 수 있다.
   ```bash
   curl -sG http://localhost:3100/loki/api/v1/query_range --data-urlencode 'query={job="backend"}'
   # 특정 요청만 보고 싶으면 traceId로 필터 (응답 헤더 X-Trace-Id 값을 그대로 쓰면 된다)
   curl -sG http://localhost:3100/loki/api/v1/query_range --data-urlencode 'query={job="backend"} | json | traceId="..."'
   ```
   같은 LogQL을 Grafana의 Explore나 "서비스" 대시보드의 로그 패널에서 그대로 쓸 수 있다.

로컬 `node_exporter`가 보여주는 건 Mac 호스트가 아니라 **Docker Desktop VM**의 자원이다. 인프라 대시보드가
제대로 그려지는지 확인하는 용도로만 보고, 실제 수치는 운영(메인 EC2)에서 본다.

## 실측: 시계열 수

로컬에서 요청 몇 건만 흘려보낸 값이라 참고용이다 (`count({job="backend"})`, `prometheus_tsdb_head_series`).

| 상태 | backend job 시계열 | 전체(backend + prometheus 자신) |
| --- | --- | --- |
| `http.server.requests` 히스토그램 끄기 전 | 152 | 886 |
| 켠 뒤, 라우트 2개(`/api/v1/cases`, `/actuator/health`) 호출 (범위 전체 · actuator 포함) | 265 | 1004 |
| **버킷 범위 10ms~5s로 좁히고 `/actuator/**` 제외한 뒤** (#120 리뷰), 라우트 1개(`/api/v1/cases`) 호출 | 255 (라우트 자체는 47) | 896 |

라우트 하나당 히스토그램 버킷이 69개(전체 범위) → 44개(10ms~5s로 좁힘)로 줄었다. `/actuator/**` 스크레이프 자체가
만드는 시계열도 빠졌다. 그래도 API 수가 늘어나면 비용은 여전히 선형으로 커지므로, Grafana Cloud 무료 등급으로
`remote_write`할 때(7.3.3, R9) 이 메트릭을 전부 보낼지는 D와 따로 정해야 한다(PR 본문 "D와 협의할 것" 참고).

## 스크레이프 대상 범위 (지금 정한 것 · 미룬 것)

- **지금 포함**: `backend`(Actuator), `prometheus`(자기 자신), `node`(호스트 자원 — 7.2.4 인프라 대시보드가 쓴다)
- **보류**: `postgres_exporter` — 7.1.2에서 RDS로 전환(**10/28 예정**)하면 연결 대상이 바뀌므로, 전환 일정이 확정된 뒤 추가한다
- **아직 결정 안 함**: Prometheus Agent 모드(수집만 하고 평가는 Grafana Cloud에서) 여부 — 7.3 경보 설계(어디서 규칙을 평가할지)와 묶인 결정이라
  지금은 TODO로만 남겨 둔다

## 로그 라벨 · 민감정보

- **traceId는 Loki 라벨이 아니다.** Loki는 Prometheus와 똑같이 라벨 조합마다 별도 스트림(=별도 인덱스)을 만든다.
  요청마다 다른 traceId를 라벨로 쓰면 스트림이 무한히 늘어나 Loki가 못 버틴다(7.2.1의 메트릭 카디널리티 규칙과 같은 이유).
  traceId는 **로그 내용(JSON 필드)**으로만 남기고, 조회는 `| json | traceId="..."`로 한다.
  라벨로 두는 건 `job`·`level`처럼 값의 종류가 적은 것뿐이다(`alloy-config.alloy`).
- **(BE-63) 웹 429 전용 로그**는 `job="web"`, 라벨은 `route`(값 4개: `auth_token` · `admin_login` · `oauth` · `api`)뿐이다.
  막힌 IP · 경로는 JSON 필드라 `{job="web"} | json`으로 본다. 운영에서는 모니터링 에이전트의 `web-ratelimit-logs` 볼륨으로 읽는다
  (`deploy/README.md` "429 로그 수집"). IP는 막힌 쪽을 찾기 위해 남긴다 — 백엔드 마스킹(토큰 · 이메일) 대상이 아니다.
- **민감정보 마스킹**은 `backend/src/main/resources/logback-spring.xml`의 `MaskingJsonGeneratorDecorator`가 한다.
  필드 이름(`password`·`token` 등)과 메시지 속 패턴(Bearer 토큰·이메일) 둘 다 가린다. 지금 코드는 이미 비밀번호 · API 키를
  로그에 남기지 않지만, 이 마스킹은 그 규칙이 깨졌을 때의 마지막 안전망이다.

## Linux에서 띄우는 경우

`host.docker.internal`은 Mac · Windows Docker Desktop만 기본 지원한다. Linux(Docker Engine)에서는 `docker-compose.yml`의
`prometheus` 서비스에 아래를 추가해야 호스트의 8081에 붙는다.

```yaml
    extra_hosts:
      - "host.docker.internal:host-gateway"
```

**`secrets/slack-webhook-url`의 `chmod 600`도 Linux에서는 다르게 동작한다** — Docker Desktop의 파일 공유
계층(권한 번역)이 없어서, 로컬이라도 Linux면 "경보" 절 대신 "운영 적용"의 `chown 65534:65534` 절차를
따라야 한다.

## 운영 적용

**모니터링 스택을 메인 서버에 함께 올리는 선택지는 없다.** 근거는 `docs/decisions/COMMON-43.md`에 정리했고, 요약하면
(1) 메인은 t3.small 2GB에 1.75GB가 이미 할당돼 올릴 자리가 없고, (2) 감시자가 감시 대상과 같이 죽으면 정작 장애
순간에 원인을 볼 수 없고, (3) Grafana는 사람이 붙는 UI라 메인 보안 그룹(22 · 80 · 443)에 노출을 더 만들고 싶지 않다.

### 어느 쪽으로 갈지 — Grafana Cloud가 1순위다 (2026-10-16, D 협의)

| | 상태 |
| --- | --- |
| **1순위 — Grafana Cloud 무료 등급** | 결정됨. 실제 연동은 **7.3.3 범위** |
| **차선 — 전용 EC2 추가** | Cloud가 막히면 이쪽. 아래 구성이 그 대비다 |

Cloud가 1순위인 결정적 이유는 비용이 아니라 **노출면**이다. Cloud 방식에서는 메인 서버의 Alloy가 Docker 내부
네트워크로 `backend:8081`을 직접 읽어 밖으로 내보내므로(push), **`8081` · `9100` · `3100`을 하나도 열 필요가 없다.**
아래 보안 그룹 표와 8081 노출이 통째로 사라지고, 팀원 Grafana 접근 문제(SSH 22번이 D의 IP만 열려 있어 사실상 못 쓴다)도
없어진다. 남는 비용은 메인 서버에 Alloy · node_exporter가 올라가는 메모리 150MB 정도뿐이다.

**아래 전용 EC2 구성은 지우지 않고 차선으로 남겨 둔다.** 어느 쪽으로 가든 대시보드 JSON은 그대로 쓴다.

### 전용 EC2(차선)로 갈 경우 — 어디서 무엇이 도는가

```
   [메인 EC2]                                  [모니터링 EC2]
   backend:8081 (Actuator)  ◀──── pull ─────── Prometheus
   node_exporter:9100       ◀──── pull ─────── (같은 Prometheus)
   Alloy (에이전트)          ───── push ──────▶ Loki
                                               Grafana ──▶ 사람 (SSH 터널)

   deploy/docker-compose.                      monitoring/docker-compose.
   prod.yml + monitoring-agent.yml             prod.yml
```

**메트릭은 pull, 로그는 push다.** 메트릭은 "현재 값"이라 Prometheus가 주기적으로 긁어오면 되지만, 로그는 지나간
줄을 나중에 가져올 수 없어서 만든 쪽이 보내야 한다. 그래서 Alloy만 메인 서버에 남는다 — Alloy는 읽어서 전달만 하는
에이전트라 가볍고(100MB 미만), 무거운 쪽인 저장·인덱싱(Loki)이 빠지는 게 핵심이다.

### 보안 그룹 — 퍼블릭에는 아무것도 열지 않는다

| 통신 | 열 곳 | 허용 범위 |
| --- | --- | --- |
| Prometheus → Actuator | 메인 SG `8081` | 모니터링 EC2 **사설 IP만** |
| Prometheus → node_exporter | 메인 SG `9100` | 모니터링 EC2 **사설 IP만** |
| Alloy → Loki | 모니터링 SG `3100` | 메인 EC2 **사설 IP만** |
| 사람 → Grafana | **열지 않음** | SSH 터널 (`ssh -N -L 3000:localhost:3000 <모니터링 EC2>`) |

둘 다 같은 VPC 사설 통신이라 인터넷을 타지 않는다. Loki에는 인증이 없고 Actuator도 인증이 없으므로, **보안 그룹이
유일한 보호선이다** — 규칙을 `0.0.0.0/0`으로 열면 메트릭·로그가 그대로 공개된다.

그래서 **컨테이너 포트도 `0.0.0.0`이 아니라 사설 IP에만 바인딩한다**(`MAIN_PRIVATE_IP` · `MONITORING_PRIVATE_IP`).
Docker가 연 포트는 서버 방화벽(ufw)을 우회하므로, 보안 그룹 규칙 하나가 잘못 열리면 곧바로 공개되기 때문이다
(D 지적, COMMON-43). 기본값을 두지 않아서 값이 없으면 컨테이너가 아예 뜨지 않는다.

Grafana 접근은 **SSH 터널이 1차안이지만 실제로는 쓰기 어렵다** — 메인 서버 `22`번이 D의 IP만 열려 있어 팀원마다
보안 그룹에 IP를 추가해야 하고, IP가 바뀔 때마다 고쳐야 한다(IAM 권한이 없어 SSM도 못 쓴다). 퍼블릭 공개는
도메인·HTTPS(BE-75) 이후다 — HTTPS 없이 열면 로그인 비밀번호가 평문으로 오간다. **이 문제도 Cloud로 가면 사라진다.**

### 올리는 순서

```bash
# --- 모니터링 EC2 ---
cd monitoring
cp .env.example .env && chmod 600 .env     # GRAFANA_ADMIN_PASSWORD 채우기

# 스크레이프 대상(메인 EC2 사설 IP). 사설 IP는 커밋하지 않으므로 서버에서 만든다
mkdir -p targets
cat > targets/backend.json <<'EOF'
[{"targets": ["<메인 EC2 사설 IP>:8081"], "labels": {"instance": "main"}}]
EOF
cat > targets/node.json <<'EOF'
[{"targets": ["<메인 EC2 사설 IP>:9100"], "labels": {"instance": "main"}}]
EOF

# Slack 웹훅 (7.3.2). 커밋하지 않으므로 서버에서 만든다 — "경보" 절의 웹훅 설정 참고.
# ⚠️ Linux(EC2)는 chmod 600으로 두면 안 된다 — Alertmanager · Prometheus 컨테이너가 nobody(uid 65534)로
# 실행되는데, 로컬(Mac · Windows Docker Desktop)과 달리 파일 공유 계층의 권한 번역이 없어서
# 소유자(배포 사용자)만 읽게 두면 컨테이너가 못 읽는다. 그대로 두면 경보가 울려도 Slack으로 조용히 안
# 간다(Alertmanager 로그에 "Notify for alerts failed … permission denied"만 남고 설정 자체는 정상으로
# 읽힌 것처럼 보여 알아채기 어렵다 — 직접 재현해서 확인함, PR #262 리뷰).
mkdir -p secrets
echo -n "https://hooks.slack.com/services/..." > secrets/slack-webhook-url
sudo chown 65534:65534 secrets/slack-webhook-url
sudo chmod 400 secrets/slack-webhook-url

docker compose -f docker-compose.prod.yml up -d --no-build

# --- 메인 EC2 ---
cd deploy
# .env에 아래 세 값 추가 (LOKI_URL만 넣으면 다음 자동 배포 때 오버레이가 빠진다 — 아래 ⚠️ 절 참고)
#   MONITORING_AGENT=1
#   MAIN_PRIVATE_IP=<메인 EC2 사설 IP>
#   LOKI_URL=http://<모니터링 EC2 사설 IP>:3100/loki/api/v1/push

# 수동 up은 deploy.sh의 잠금을 잡지 않아 cd-agent.sh의 자동 배포와 겹칠 수 있다. 적용 전후로 타이머를 멈췄다 켠다
sudo systemctl stop lawnambul-cd.timer
docker compose -f docker-compose.prod.yml -f docker-compose.monitoring-agent.yml up -d --no-build
sudo systemctl start lawnambul-cd.timer
```

운영 compose 파일에는 `build:`가 있어서, 이미지가 없으면 서버가 직접 빌드를 시도한다. `deploy.sh`와 같은 기준으로
`--no-build`를 붙여 서버에서는 항상 GHCR에서 받은 이미지만 쓰게 한다 (PR #168 리뷰 반영).

`targets/*.json`은 Prometheus가 알아서 다시 읽는다 — 인스턴스를 중지했다 켜서 사설 IP가 바뀌어도 이 파일만 고치면
되고, 재시작도 설정 리로드도 필요 없다. 설정 파일(`prometheus.prod.yml`)을 고쳤을 때만 리로드가 필요하다
(`--web.enable-lifecycle`을 켜 뒀으므로 `curl -X POST http://localhost:9090/-/reload`).

### ⚠️ 배포 때 오버레이가 빠지면 모니터링이 조용히 끊긴다

base 파일만으로 backend를 다시 올리면 **8081 포트와 로그 볼륨이 빠진 채 컨테이너가 재생성되고, 에이전트 컨테이너만
연결 없이 남는다.** 오류가 나지 않아 눈치채기 어렵다(D 지적, COMMON-43).

**BE-60 CD가 들어온 지금은 상시 위험이다** — `deploy/cd-agent.sh`가 2분마다 GHCR `main` 이미지를 확인해
`deploy.sh`로 자동 배포하므로, 수동으로 오버레이를 붙여 올려 두어도 다음 배포 한 번이면 원래대로 돌아간다.
**(PR #168 리뷰 반영) 이 분기를 넣었다** — `deploy.sh`의 `COMPOSE` 배열이 `.env`의 `MONITORING_AGENT=1`을 보고
이 오버레이를 포함한다(real-seed 오버레이 바로 뒤). 에이전트를 쓰는 서버의 `.env`에 아래 세 값을 넣는다.

```bash
MONITORING_AGENT=1
MAIN_PRIVATE_IP=<메인 EC2 사설 IP>
LOKI_URL=http://<모니터링 EC2 사설 IP>:3100/loki/api/v1/push
```

`MONITORING_AGENT`가 비어 있는 서버는 분기를 타지 않으므로 **기존 배포 동작이 그대로다.** `1`인데 나머지 둘이
없으면 배포를 시작하기 전에 멈춘다(exit 4) — 자동 배포가 "이미지를 받지 못했다"처럼 엉뚱한 원인으로 보이지 않게
하기 위해서다. 원래 **`deploy.sh`는 D 소유 영역**이라(7-2-1) 머지 전에 D 확인이 필요하다.

### 아직 남은 것

- **Cloud 세 번째 좌석** — 무료 등급은 시각화 사용자 3명이다. C · D는 확정이고 남은 한 자리를 A · B 중 누구에게 줄지
  팀에서 정한다. 계정 공유는 하지 않는다.
- ~~**`deploy.sh`에 `MONITORING_AGENT` 플래그 추가**~~ → PR #168 리뷰에서 반영했다(위 ⚠️ 절). **D 소유 파일을
  대신 고친 것이라 머지 전 확인만 받으면 된다.**
- **Grafana Cloud 실제 연동은 7.3.3 범위다** — 계정 생성 · `remote_write` · Alloy를 내부 네트워크 스크레이프 방식으로
  전환. 이번 작업은 전용 EC2 전제의 구성까지이고, 대시보드 JSON은 양쪽에서 그대로 쓴다.
- **전용 EC2로 갈 경우에만**: 실제 EC2 생성 · 보안 그룹 변경(D), Prometheus 보존 30일/8GB 디스크 여유 확인(D),
  에이전트 적용 타이밍(backend 재생성에 20~40초 중단 — 월요일 첫 배포 이후). 사설 IP는 저장소·채팅이 아니라 DM으로 받는다.
