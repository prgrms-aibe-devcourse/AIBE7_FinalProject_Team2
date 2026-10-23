# COMMON-63 의사결정 기록

## 문제 / 배경

COMMON-62까지로 운영 메트릭은 메인 EC2의 Alloy가 Grafana Cloud로 push하고 있지만, 그 메트릭을 보고
경보를 평가·발송하는 쪽은 아직 없었다. `docs/decisions/COMMON-62.md`에서 이미 "운영 경보 평가 ·
Alertmanager는 Grafana Cloud 쪽으로 옮긴다"까지는 결정했고(메인 서버가 죽으면 로컬 Alertmanager도
같이 죽는 문제), 이 이슈는 그 결정을 실제로 Grafana Cloud 계정에 반영하는 작업이다.
`monitoring/alert-rules.yml`(COMMON-61, 로컬 10개 규칙)을 어떤 방법으로 Cloud에 옮길지가 핵심이었다.

## 검토한 대안

### 1. Cloud 쪽 경보를 어떤 API로 반영할까

- **대안 A: Mimir ruler API (Basic Auth)** — Grafana Cloud의 Prometheus(Mimir)는 Cortex/Mimir 표준
  ruler API(`/api/prom/rules/{namespace}` 류)를 지원한다. `alert-rules.yml`이 이미 Prometheus 표준
  문법이라 그대로 옮길 수 있다는 게 Jira 이슈 설명에 적힌 원래 전제였다. 다만 이 API는 보통 Access
  Policy 토큰에 `alerts:write`/`rules:write` 같은 별도 범위가 필요해, COMMON-62에서 쓴
  `metrics:write` 토큰과는 다른 토큰을 새로 발급받아야 할 가능성이 있었다.
- **대안 B: Grafana Service Account 토큰으로 Mimir ruler를 데이터소스 프록시로 호출** — Grafana가
  `/api/ruler/<datasource-uid>/api/v1/rules/{namespace}`로 Mimir ruler API를 그대로 중계하는
  경로가 있어, 이미 COMMON-63 착수 시점에 검증해 둔 Grafana Service Account 토큰(`glsa_...`,
  Editor 권한)만으로 될 수도 있었다. **실제로 시도해 보니 이 경로는 요청을 받긴 하지만(200대 응답)
  실제 규칙이 들어가지 않았다** — 데이터소스의 "Manage alerts via Alerting UI" 설정이 꺼져 있어야
  한다는 추정이 가능했지만, 그 Prometheus 데이터소스가 `readOnly: true`(스택 기본 제공, API로 설정
  변경 불가)라 이 경로 자체를 켤 수 없었다.
- **대안 C: Grafana "Convert Prometheus rules" API** — Grafana 11대에 추가된
  `POST /api/convert/prometheus/config/v1/rules/{namespace}` 엔드포인트. Prometheus 표준 규칙
  YAML을 그대로 보내면 Grafana가 내부적으로 Grafana-managed 규칙으로 변환해 등록한다(실제로 이
  Grafana Cloud 계정에 이미 있던 `asserts` 앱 규칙 598개도 `provenance: converted_prometheus`로
  같은 방식으로 들어와 있었다 — 선례가 있는 경로임을 실측으로 확인). 같은 Service Account 토큰으로
  바로 됐다.

**선택: 대안 C.** 새 토큰 발급 없이, 이미 검증된 토큰 하나로 끝난다. YAML도 원본 그대로 보낼 수 있어
Jira 이슈가 원한 "Prometheus 표준 문법이라 그대로 옮길 수 있다"는 목표에도 대안 A·B보다 더 부합한다
(대안 B는 결과적으로 안 됐고, 대안 A는 토큰을 새로 받아야 하니 한 단계 더 필요했다).

### 2. 로컬에는 있고 Cloud에는 못 옮기는 규칙 — `AlertDeliveryFailing`

로컬 `lawnambul-meta` 그룹의 `AlertDeliveryFailing`(`rate(alertmanager_notifications_failed_total[5m]) > 0`)은
로컬 Alertmanager 자신을 Prometheus가 스크레이프해야 나오는 지표를 본다(`monitoring/prometheus.yml`의
`alertmanager` job). 운영에는 애초에 Alertmanager 컨테이너가 없고, Alloy도 이 지표를 Cloud로 보내지
않는다(`deploy/alloy-agent.alloy`의 `cloud_filter`가 백엔드 · 노드 지표만 허용). 즉 이 규칙을 Cloud에
그대로 옮겨도 값이 영원히 비어 평가될 수 없다.

- **대안 A: 그대로 안 옮기고 뺀다.** "경보 경로가 살아 있는가"는 Cloud에서도 `Watchdog`
  (`vector(1)`, Dead man's switch)만으로 충분히 확인된다 — Watchdog이 계속 firing 상태를 유지하는 한
  Prometheus 평가 → Grafana 알림 정책 → (뮤트된) 상태까지 파이프라인 전체가 살아 있다는 뜻이고, 이게
  멈추면 역으로 알아챌 수 있다.
- **대안 B: Grafana Cloud 내부 지표(`grafanacloud-usage` 등)로 대체 지표를 새로 찾아 만든다.** 계정의
  내부 알림 전송 실패 지표가 어떤 이름 · 어느 데이터소스에 있는지 확인하는 조사가 추가로 필요하고, 이번
  이슈의 범위(기존 규칙 이식 + Slack 연락처)를 벗어난다.

**선택: 대안 A.** Watchdog으로 같은 목적을 충분히 달성하고, 범위를 불필요하게 늘리지 않는다.

### 3. Watchdog을 Slack으로 안 보내는 방법 — "null 수신자" 대응

로컬 Alertmanager에는 `receivers: [{name: "null"}]`로 보내 아무 데도 전달하지 않는 패턴이 있다
(`monitoring/alertmanager.yml`). Grafana Cloud의 알림 정책(Notification policy) API에는 "아무 데도
보내지 않는 수신자"라는 1급 개념이 없다 — 라우트마다 receiver 필드가 필수다.

- **대안 A: 더미 webhook 수신자를 만들어 존재하지 않는 주소로 보낸다.** 전송이 계속 실패로 기록되어
  로그 · 상태가 지저분해지고, 실패 자체가 다른 걸 가리는 신호처럼 보일 수 있다.
- **대안 B: 항상 적용되는 mute timing을 만들어 `severity="none"` 라우트에 건다.** Grafana가 1급으로
  지원하는 기능이고, "경보는 평가되고 있지만 알림만 보내지 않는다"는 의도를 정확히 표현한다.

**선택: 대안 B.** `always-muted`(월~일 00:00~23:59) mute timing을 만들어 Watchdog 라우트에 적용했다.

### 4. 억제(inhibit) 규칙

로컬 Alertmanager의 `DbConnectionPoolNearExhaustion` → `DbConnectionPending` 억제 규칙(PR #262 리뷰
반영분)에 대응하는 기능을, 이 Grafana Cloud 계정의 알림 정책 Provisioning API(`/api/v1/provisioning/policies`)
로 확인해 본 결과 지원되지 않았다(요청 자체를 받는 필드가 없음). 이번 범위에서는 기능 조사만 하고
구현하지 않기로 했다 — 로컬(COMMON-61)에서 억제가 이미 동작하고, 운영에서 한 묶음으로 같이 울리는
정도는(둘 다 critical/warning 경보로 Slack에 보이는 것 자체는 막지 않음) 실사용에 치명적이지 않다고
판단했다.

## 선택한 방법과 이유

1. Grafana "Convert Prometheus rules" API로 `monitoring/alert-rules.yml`의 규칙을 네임스페이스
   `lawnambul`에 `lawnambul-service`(7개) · `lawnambul-meta`(`Watchdog`)로 이식.
2. `AlertDeliveryFailing`은 이식하지 않음(Cloud에서는 평가 불가능한 지표, Watchdog으로 대체).
3. Slack 연락처(`lawnambul-slack`)는 로컬과 같은 웹훅 URL을 재사용.
4. 알림 정책: 기본 그룹핑(`alertname`, `severity`) · `severity="critical"` 빠른 재알림(10s/1h) ·
   `severity="none"`(Watchdog)은 `always-muted` mute timing으로 항상 음소거.
5. 억제 규칙은 이번 범위에서 보류(로컬에만 유지).

## 고려한 제약 사항

- 데이터소스가 `readOnly: true`라 Mimir ruler 데이터소스 프록시 경로를 켤 수 없었다(계정 설정 변경
  권한이 API로 주어지지 않음).
- Grafana Cloud 알림 정책 API에 억제 규칙 · null 수신자 개념이 1급으로 없다.
- 토큰(Grafana Service Account `glsa_...`, Access Policy `metrics:write`)은 어디에도 커밋하지 않았다
  — 검증 · 설정에 쓴 임시 파일은 작업 후 전부 삭제했다.

## 추후 재검토가 필요하면

- Grafana Cloud 계정에서 알림 정책 API가 억제 규칙을 지원하게 되면(또는 수동 UI 설정으로 가능해지면)
  `DbConnectionPoolNearExhaustion` → `DbConnectionPending` 억제를 Cloud에도 반영을 검토한다.
- 데이터소스의 "Manage alerts via Alerting UI"를 수동으로 켤 수 있는 권한이 생기면, 대안 B(Mimir ruler
  데이터소스 프록시)로 바꿀 이유는 딱히 없다 — 현재 방식(Convert Prometheus rules)이 이미 YAML을
  그대로 쓸 수 있어 실익이 없다.
