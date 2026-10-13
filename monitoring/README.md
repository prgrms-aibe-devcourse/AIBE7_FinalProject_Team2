# 모니터링 (BE-70, WBS 7.2.2)

로컬 Prometheus로 백엔드 Actuator 메트릭(7.2.1)을 수집한다. 로그(Promtail · Loki, 7.2.3)와 대시보드(Grafana, 7.2.4)는 뒤이어 이 폴더에 추가한다.

| 파일 | 내용 |
| --- | --- |
| `docker-compose.yml` | Prometheus 컨테이너 하나. `backend/docker-compose.yml`(DB)과 독립적으로 켜고 끈다 |
| `prometheus.yml` | scrape 대상 · 간격. 보존 기간은 `docker-compose.yml`의 `--storage.tsdb.retention.time` |

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

## Linux에서 띄우는 경우

`host.docker.internal`은 Mac · Windows Docker Desktop만 기본 지원한다. Linux(Docker Engine)에서는 `docker-compose.yml`의
`prometheus` 서비스에 아래를 추가해야 호스트의 8081에 붙는다.

```yaml
    extra_hosts:
      - "host.docker.internal:host-gateway"
```

## 운영 적용은 아직이다

지금은 "로컬 Compose"까지만이다(WBS 7.2.2). `ports: ["9090:9090"]`은 로컬 전용이다 — 운영 보안 그룹은 80 · 443 · 22만
열려 있고, 9090을 그대로 여는 일은 없다. 운영 적용은 백엔드가 Docker 네트워크 안에서 뜨는 `deploy/docker-compose.prod.yml`
기준으로 scrape 대상을 `backend:8081`로 바꾸고, Grafana Cloud `remote_write`(7.3.3)까지 묶어서 D(정민석)와 별도로
협의한 뒤 진행한다 (이 PR 본문 "D와 협의할 것" 참고).
