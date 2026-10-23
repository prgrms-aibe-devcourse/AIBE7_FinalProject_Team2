# COMMON-62 의사결정 기록

## 문제 / 배경
- WBS 7.3.3(Grafana Cloud 연동)을 시작하면서, Grafana Cloud 계정 정보(remote_write 엔드포인트 ·
  Instance ID · API 키)를 받아 메트릭을 보내는 작업을 처음에 **잘못된 가정 위에서** 구현했다.
- `monitoring/docker-compose.prod.yml`(COMMON-43 때 "전용 모니터링 EC2" 차선 시나리오를 위해 미리
  만들어 둔 설정)을 보고, 실제로 그런 서버가 있는 것처럼 착각해 거기에 `remote_write`를 추가했다.
  실제로는 **운영 인스턴스가 1대뿐**이고, 별도 모니터링 서버는 애초에 만든 적이 없다 — WBS v1.34에서
  이미 "Grafana Cloud가 1순위, 전용 EC2는 Cloud가 막힐 때만 만드는 차선"으로 정해져 있었는데, 이걸
  놓쳤다(정민석 리뷰로 발견, 2026-10-22).

## 검토한 대안

### 1. 메트릭을 어디서 Grafana Cloud로 보낼까
- 대안 A(처음 시도, 틀림) — `monitoring/docker-compose.prod.yml`의 Prometheus에 `remote_write`를
  추가해, "전용 모니터링 EC2"에서 pull(메인 서버 8081 · 9100을 긁어옴) + push(Cloud로 remote_write)를
  동시에 한다. 문제: 그런 서버가 없다. 만들려면 COMMON-43이 피하려던 것(EC2 비용, 메인 서버 포트
  노출)을 다시 만들게 된다.
- 대안 B(선택) — 로그가 이미 가는 길(메인 EC2 안의 Alloy → push)과 같은 패턴으로, **메인 EC2 안의
  Alloy가 같은 Docker 네트워크에서 직접 backend:8081 · node-exporter:9100을 긁어(scrape) Cloud로
  push**한다(`deploy/alloy-agent.alloy`에 `prometheus.scrape` · `prometheus.remote_write` 추가). 새
  서버도, 메인 서버의 새 포트 노출도 필요 없다 — Alloy가 이미 메인 서버 안에 떠 있다.

### 2. 경보 평가 · Alertmanager는 어디서 돌까
- 대안 A — 메인 EC2 안에 Prometheus(규칙 평가) + Alertmanager를 같이 띄운다. 구현은 간단하지만,
  **COMMON-43이 "감시자가 감시 대상과 같이 죽으면 장애 순간에 못 본다"고 한 이유가 메인 서버 자체가
  죽을 때 그대로 적용된다** — 메인 서버가 다운되면 그 안의 Alertmanager도 같이 죽어, 정작 가장 중요한
  "백엔드가 죽었다"를 못 알린다(PR #262 리뷰 3번이 지적한 구멍과 같은 종류의 문제가 인프라 차원에서도
  생긴다).
- 대안 B(선택, 정민석 제안) — 경보 평가 · Alertmanager는 **Grafana Cloud 쪽(Cloud Alerting)으로
  옮긴다.** Cloud는 메인 서버와 별개의 인프라라, 메인 서버가 완전히 죽어도(Alloy까지 멈춰도) Cloud는
  "지표가 안 들어온다(no data)"를 그대로 감지해 경보로 쓸 수 있다 — 정확히 빠져 있던 "백엔드 다운"
  경보(PR #262 리뷰 3번)를 인프라 차원에서 보완하는 방법이다. `monitoring/alert-rules.yml`은 Prometheus
  표준 문법이라 Cloud 쪽(Mimir 기반 ruler)으로 옮기기 쉽다 — 다만 실제로 Cloud에 규칙 · 연락처를
  올리는 작업은 적절한 권한의 API 토큰(또는 수동 UI 설정)이 필요해 **별도 후속 작업**으로 남긴다.
  로컬 Alertmanager(COMMON-61, `monitoring/docker-compose.yml`)는 **로컬 확인용으로만** 남긴다.

### 3. Grafana Cloud API 키를 Alloy에 어떻게 전달할까
- 대안 A — Prometheus의 `password_file`처럼 비밀값 파일을 만들어 마운트한다(Alertmanager 웹훅과
  같은 패턴). 다만 이 패턴은 PR #262 리뷰에서 바로 그 "컨테이너가 nobody로 돌아 파일을 못 읽는"
  문제를 겪었던 방식이라, 같은 종류의 권한 문제를 또 만들 위험이 있다.
- 대안 B(선택) — **환경변수**로 전달한다. `deploy/alloy-agent.alloy`가 이미 `LOKI_URL`을
  `sys.env("LOKI_URL")`로 읽는 것과 같은 패턴(`GRAFANA_CLOUD_PROM_URL` ·
  `GRAFANA_CLOUD_PROM_USERNAME` · `GRAFANA_CLOUD_PROM_API_KEY`)을 그대로 따른다. 파일 권한 문제
  자체가 없고(docker-compose의 `environment:`로 전달되므로 uid 불일치가 끼어들 자리가 없다), 기존
  관례와도 일치한다.

## 선택한 방법과 이유
- `deploy/alloy-agent.alloy`에 `prometheus.scrape`(backend · node-exporter) → `prometheus.relabel`
  (대시보드 · 경보가 쓰는 지표군만 필터) → `prometheus.remote_write`(Grafana Cloud) 파이프라인을
  추가했다. 자격 증명은 환경변수 3개(`GRAFANA_CLOUD_PROM_URL` · `_USERNAME` · `_API_KEY`)로 받는다 —
  서버 `.env`에 값을 넣고 `docker-compose.monitoring-agent.yml`의 `environment:`로 Alloy 컨테이너에
  전달한다(오버레이 · `deploy.sh` 쪽 반영은 정민석 담당, COMMON-62 진행 중).
- `monitoring/docker-compose.prod.yml`에 잘못 추가했던 `remote_write`는 되돌렸다 — 그 파일은 다시
  "Cloud가 막혔을 때의 차선" 역할로만 남긴다.
- 로컬에서 실제 자격 증명으로 Alloy를 띄워 scrape → relabel → remote_write 전체 경로를 검증했다
  (`prometheus.scrape.backend` · `prometheus.remote_write.grafana_cloud` 모두 `healthy`, 샘플 전송
  성공 · 실패 0건 확인).
- 운영 반영(`.env` 값 설정 · `./deploy.sh --reapply`)은 **이미지가 안 바뀌어 자동 배포로는 적용되지
  않으므로** 정민석이 수동으로 진행한다.

## 고려한 제약 사항
- Grafana Cloud 쪽 경보 · 연락처 설정(대안 2-B)은 이번 PR 범위에 넣지 않았다 — 적절한 권한의 API
  토큰이 필요하거나 수동 UI 설정이 필요해 **별도 이슈(COMMON-63, 백승호 담당)**로 다룬다. 그때까지
  운영 경보는 **공백 상태다** — 로컬 Alertmanager는 로컬 확인용일 뿐 운영 경보를 대신하지 않는다.
  M3(운영 전환) 전에 Cloud 쪽 경보
  설정을 마쳐야 한다.
- 접근 권한 3번째 좌석(A · B 중 선정)은 아직 팀 결정 전이다.
- Alloy의 `prometheus.remote_write` WAL(`truncate_frequency = "2h"`)은 로그 쪽(`loki.write`)의 1시간
  재시도 한도와 다른 값이다 — 운영에서 실제 장애 패턴을 보고 맞출지 다시 본다.

## 추후 재검토가 필요하면
- Grafana Cloud 쪽 경보 · 연락처(Slack) 설정을 코드(Terraform 등)로 관리할지, 수동 UI로 할지는
  권한·도구가 정해지면 다시 본다.
- 운영에 remote_write가 안정적으로 도는 걸 확인한 뒤, 로컬 Alertmanager를 계속 유지할지(로컬 확인용)
  없앨지도 그때 다시 본다.
