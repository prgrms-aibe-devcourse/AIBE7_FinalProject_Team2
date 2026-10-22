# COMMON-61 의사결정 기록

## 문제 / 배경
- WBS 7.3.1(Prometheus 경보 규칙) · 7.3.2(Alertmanager)가 COMMON-58 종합 점검에서 "Jira 이슈조차
  없고 구현도 안 됨"으로 지적됐다. M2(10/23) 확인 기준에 "Alertmanager 알림 수신"이 포함돼 있어 급했다.
- 기존 `monitoring/`에는 Prometheus · Loki · Grafana만 있고, "조건을 평가해서 사람에게 알리는" 기능이
  전혀 없었다.

## 검토한 대안

### 1. Slack 웹훅 URL을 어디에 둘까
- 대안 A — `alertmanager.yml`에 `slack_configs.api_url`로 직접 적는다. Alertmanager 공식 예시 대부분이
  이 방식이라 간단하다.
- 대안 B(선택) — `slack_configs.api_url_file`로 별도 파일(`monitoring/secrets/slack-webhook-url`)을
  가리킨다. 웹훅 URL이 사실상 비밀번호와 같은 자격 증명이라, 공개 저장소에 커밋되는 YAML에 직접 적을
  수 없다(CLAUDE.md 3-1 보안). `monitoring/targets/`(사설 IP)와 같은 원칙으로 `.gitignore` 처리하고,
  `.example` 플레이스홀더만 커밋했다.

### 2. Slack 메시지를 보낼 채널을 어떻게 지정할까
- 대안 A(처음 시도, 실패) — `slack_configs.channel: "#lawnambul-alerts"`로 명시한다. 문서상으로는
  "어느 채널로 보낼지 지정하는 필드"처럼 보인다.
- 대안 B(선택) — `channel`을 아예 지정하지 않는다.
- **실제로 로컬에서 Alertmanager를 띄우고 테스트 경보를 보내 확인한 결과**, 대안 A는
  `404 channel_not_found`로 실패했다. Slack 수신 웹훅(Incoming Webhook)은 **발급할 때 이미 보낼
  채널이 고정**되고, `channel` 필드로 다른 채널을 지정하려면 그 채널이 실제로 존재해야 한다 — 임의로
  적은 채널 이름(`#lawnambul-alerts`)이 워크스페이스에 없어서 거절당했다. `channel`을 빼고
  웹훅 발급 시점에 고정된 채널로 그대로 보내도록 고쳐 해결했다. 이 과정은 문서로만 판단했다면 몰랐을
  것이라 실제 발송 테스트를 거친 게 중요했다.

### 3. "배치 지연"(사건 등록, BE-93) 경보를 어떤 지표로 잴까
- 대안 A — 기존 메트릭(`lawnambul.ai.call` 등)으로 대신 유추한다. 새 코드를 안 만들어도 된다.
- 대안 B(선택) — `CaseImportMetrics`를 새로 만들어 "진행 중(QUEUED · RUNNING)인 사건 등록 작업 중
  가장 오래된 것의 경과 시간(초)"을 Gauge로 낸다. 기존 메트릭 중 "작업이 오래 멈춰 있다"를 직접 재는
  게 없어서 대안 A로는 정확한 경보를 만들 수 없었다. `CaseImportRepository.findByStatusIn`(이미
  A-14 `IMPORT_IN_PROGRESS` 판정에 쓰이던 메서드)을 재사용해 스크레이프할 때(15초마다)마다 DB를 한 번
  조회한다 — 관리자 전용 · 저빈도 기능이라 이 정도 조회 비용은 문제가 안 된다고 판단했다(스케줄러를
  따로 두지 않고 Micrometer `Gauge`의 공급자 함수로 충분).
- 사건 ID 같은 값은 태그로 넣지 않았다(WBS R9, 카디널리티 규칙과 동일한 원칙) — 경과 시간 숫자 하나만
  낸다.

### 4. 경보 규칙의 임계값을 어떻게 정했을까
- 정식 SLO가 아직 없어(SLO 정의는 7.3.4, 이번 범위 밖) 대시보드에서 참고하던 값과 상식적인 기준으로
  정했다: 5xx 5%, p95 2초, HikariCP 사용률 90%, AI 호출 실패율 30%, 사건 등록 10분.
  **이 값들은 운영 데이터를 실제로 보면서 다시 조정해야 한다** — 7.3.4(런북)에서 같이 본다.

## 선택한 방법과 이유
- 경보 규칙(`monitoring/alert-rules.yml`): 6개 룰, 5개 카테고리(WBS 7.3.1 그대로).
- Alertmanager(`monitoring/alertmanager.yml`): `group_by: [alertname, severity]` · critical은
  `group_wait` · `repeat_interval`을 짧게 · DB 커넥션 고갈(critical)이 울리는 동안 DB 커넥션 대기
  (warning) 억제 · Slack 수신자 1개(채널은 웹훅 고정값).
- 로컬 · 운영(`docker-compose.yml` · `docker-compose.prod.yml`) 양쪽에 `alertmanager` 서비스 추가,
  `prometheus.yml` · `prometheus.prod.yml`에 `rule_files` · `alerting.alertmanagers` 블록 추가.
- 실제로 로컬에서 띄워 Slack까지 테스트 알림이 가는 것을 확인한 뒤에 커밋했다(위 2번 문제를 그 과정에서
  찾아 고쳤다).

## 고려한 제약 사항
- 운영(모니터링 전용 EC2)에는 아직 실제로 반영하지 않았다 — 설정 파일만 준비됐고, 서버에서
  `secrets/slack-webhook-url`을 만들고 `docker compose up -d`를 다시 해야 적용된다(`monitoring/README.md`
  "올리는 순서" 참고).
- 경보 임계값은 1차 추정치다. 운영에서 오탐 · 미탐이 나오면 7.3.4(런북 작성)와 함께 조정한다.
- Alertmanager 버전은 `v0.27.0`(api_url_file 지원이 안정적인 라인)을 썼다 — Prometheus(v3.13 LTS) ·
  Loki · Grafana처럼 별도 LTS 기준이 없어 "충분히 쓰인 안정 버전"을 고르는 같은 원칙을 따랐다.

## 추후 재검토가 필요하면
- 7.3.3(Grafana Cloud 연동)으로 모니터링 호스트가 옮겨가면, Alertmanager도 Cloud의 관리형 Alertmanager를
  쓸지 지금처럼 자체 호스팅을 유지할지 다시 정해야 한다(지금은 자체 호스팅 그대로 둔 상태).
- 팀이 커지면 `route.routes`에 심각도 · 종류별 채널을 더 나눌 수 있다(지금 구조를 유지한 채 확장 가능).
