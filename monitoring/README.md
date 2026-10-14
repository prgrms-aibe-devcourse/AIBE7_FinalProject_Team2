# 모니터링 (Prometheus BE-70 WBS 7.2.2 · Loki/Alloy BE-71 WBS 7.2.3)

로컬 Prometheus로 백엔드 Actuator 메트릭(7.2.1)을 수집하고, 로컬 Loki + Grafana Alloy로 백엔드 JSON 로그(7.2.3)를 적재한다.
대시보드(Grafana, 7.2.4)는 뒤이어 이 폴더에 추가한다.

| 파일 | 내용 |
| --- | --- |
| `docker-compose.yml` | Prometheus · Loki · Alloy 컨테이너. `backend/docker-compose.yml`(DB)과 독립적으로 켜고 끈다 |
| `prometheus.yml` | scrape 대상 · 간격. 보존 기간은 `docker-compose.yml`의 `--storage.tsdb.retention.time` |
| `loki-config.yml` | Loki 단일 바이너리 구성(파일시스템 저장), 보존 기간 |
| `alloy-config.alloy` | `backend/logs`의 JSON 로그를 tail해서 Loki로 보내는 파이프라인 (Promtail 대체, 아래 참고) |

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
3. http://localhost:9090/targets 에서 `backend` job이 **UP**인지 확인한다. `host.docker.internal:8081`에 연결하므로,
   백엔드가 아직 안 떠 있으면 DOWN으로 보이는 게 정상이다 — 백엔드를 띄운 뒤 몇 초 안에 UP으로 바뀐다.
4. http://localhost:9090/graph 에서 `lawnambul_experience_started_total` 같은 커스텀 메트릭을 바로 조회할 수 있다.
5. 로그 확인: http://localhost:3100/ready 로 Loki 기동을 확인한 뒤, LogQL로 직접 조회할 수 있다.
   ```bash
   curl -sG http://localhost:3100/loki/api/v1/query_range --data-urlencode 'query={job="backend"}'
   # 특정 요청만 보고 싶으면 traceId로 필터 (응답 헤더 X-Trace-Id 값을 그대로 쓰면 된다)
   curl -sG http://localhost:3100/loki/api/v1/query_range --data-urlencode 'query={job="backend"} | json | traceId="..."'
   ```
   Grafana(7.2.4)가 붙으면 이 LogQL을 화면에서 바로 쓸 수 있다.

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

- **지금 포함**: `backend`(Actuator), `prometheus`(자기 자신)
- **보류**: `node_exporter`(호스트 자원) — 7.2.4 인프라 대시보드 · 7-5 조정안 때 다시 본다
- **보류**: `postgres_exporter` — 7.1.2에서 RDS로 전환(10/26 예정)하면 연결 대상이 바뀌므로, 전환 일정이 확정된 뒤 추가한다
- **아직 결정 안 함**: Prometheus Agent 모드(수집만 하고 평가는 Grafana Cloud에서) 여부 — 7.3 경보 설계(어디서 규칙을 평가할지)와 묶인 결정이라
  지금은 TODO로만 남겨 둔다

## 로그 라벨 · 민감정보

- **traceId는 Loki 라벨이 아니다.** Loki는 Prometheus와 똑같이 라벨 조합마다 별도 스트림(=별도 인덱스)을 만든다.
  요청마다 다른 traceId를 라벨로 쓰면 스트림이 무한히 늘어나 Loki가 못 버틴다(7.2.1의 메트릭 카디널리티 규칙과 같은 이유).
  traceId는 **로그 내용(JSON 필드)**으로만 남기고, 조회는 `| json | traceId="..."`로 한다.
  라벨로 두는 건 `job`·`level`처럼 값의 종류가 적은 것뿐이다(`alloy-config.alloy`).
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

## 운영 적용은 아직이다

지금은 "로컬 Compose"까지만이다(WBS 7.2.2 · 7.2.3). `ports: ["9090:9090"]`·`["3100:3100"]`은 로컬 전용이다 — 운영 보안
그룹은 80 · 443 · 22만 열려 있고, 그대로 여는 일은 없다. 운영에서는 백엔드가 Docker 네트워크 안에서 뜨므로
Prometheus는 scrape 대상을 `backend:8081`로, Alloy는 `backend/logs` 대신 백엔드 컨테이너와 같은 Compose에서
볼륨을 공유하는 방식으로 바꿔야 한다. Grafana Cloud `remote_write`(7.3.3)까지 묶어서 D(정민석)와 별도로 협의한 뒤
진행한다 (BE-70 PR #120 본문 "D와 협의할 것" 참고 — BE-71도 같은 협의 범위에 들어간다).
