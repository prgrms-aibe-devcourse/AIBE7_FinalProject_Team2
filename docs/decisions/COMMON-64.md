# COMMON-64 의사결정 기록

## 문제 / 배경

- COMMON-62(지표 remote_write)와 COMMON-63(Grafana Cloud 경보 · Slack)은 코드와 Cloud 설정까지 끝났지만, **운영 EC2에서는
  Alloy가 한 번도 켜진 적이 없었다.** 운영 `deploy/.env`에 `MONITORING_AGENT`가 없어 `deploy.sh`가 모니터링 오버레이를 붙이지
  않았기 때문이다(10/23 백승호 확인). WBS v1.49 "남은 것 ① 오버레이 · deploy.sh 환경변수 반영(D)"이 남아 있던 상태다.
- 켜려고 해도 막혀 있었다. 오버레이가 `MAIN_PRIVATE_IP`와 `LOKI_URL`(모니터링 전용 EC2의 Loki, 사설 IP:3100)을 요구했는데,
  그 EC2는 두지 않기로 했다(COMMON-43 → Grafana Cloud 1순위). `alloy-agent.alloy`의 `loki.write`도 같은 값을 읽어서,
  값이 비면 Alloy가 설정 오류로 시작하지 못해 **지표 전송까지 함께 멈춘다.**
- 그 사이 COMMON-63에서 Cloud 규칙의 noDataState를 Alerting으로 바꾸자 9개 규칙이 모두 no data로 울렸다(지표가 실제로 없으니 정확한 반응).

## 검토한 대안

### 1. 로그를 어디로 보낼까

- **대안 A — Grafana Cloud Loki로 보낸다.** `loki.write`에 지표 remote_write와 같은 basic_auth를 붙이면 된다. 429 로그
  대시보드(BE-63)까지 한 번에 살아난다. 무료 등급에 로그가 포함된다.
- 대안 B — 당장은 지표만 켜고 로그는 뺀다. 로그 파이프라인을 켜고 끌 수 있게 설정 구조를 바꿔야 해서 오히려 손이 많이 가고,
  로그 작업이 다시 남는다.

**선택: A.**

### 2. 메인 서버 포트(8081 · 9100)를 계속 열까

- 전용 EC2의 Prometheus가 pull하던 시절의 요구다. Alloy가 같은 Docker 네트워크에서 `backend:8081` · `node-exporter:9100`을
  직접 긁어 push하므로 필요 없다. **지웠다**(`MAIN_PRIVATE_IP`도 함께). 밖으로 여는 포트가 80 · 443만 남는다.

### 3. `env` 라벨을 어떻게 붙일까

- COMMON-63에서 `external_labels = {env = "prod"}`를 고정으로 넣었는데, 같은 `alloy-agent.alloy`로 로컬 Cloud 전송 테스트도
  하므로 로컬 데이터도 prod로 들어간다(PR #266 리뷰). → `env = coalesce(sys.env("MONITORING_ENV"), "local")`로 받고,
  운영 전용 오버레이가 기본값 `prod`를 넘긴다. 로그(`loki.write`)에도 같은 라벨을 붙였다.

### 4. 로그 토큰

- 토큰 하나에 `metrics:write` · `logs:write`를 함께 주는 경우가 많아 `GRAFANA_CLOUD_LOKI_API_KEY`는 선택으로 두고,
  비우면 지표 토큰을 쓴다(`coalesce`). 주소 · ID는 지표와 로그가 다르므로 둘 다 필수다.

## 선택한 방법과 이유

- `deploy/alloy-agent.alloy`: `loki.write` → `GRAFANA_CLOUD_LOKI_URL` · `_USERNAME` · `_API_KEY`(basic_auth), `env` 라벨을 지표 · 로그 모두 환경변수로
- `deploy/docker-compose.monitoring-agent.yml`: Grafana Cloud 변수 6개 + `MONITORING_ENV`(기본 prod) 전달, 8081 · 9100 포트와 `MAIN_PRIVATE_IP` 제거,
  손으로 `docker compose up` 하지 말고 `./deploy.sh --reapply`로 적용하라는 안내(외부 DB 오버레이가 빠지면 backend가 내려간다)
- `deploy/deploy.sh`: `MONITORING_AGENT=1`일 때 필수 값 5개 검사 + 주소 https 검사(토큰이 평문으로 나가지 않게)
- `deploy/.env.example` · `deploy/README.md`("모니터링 에이전트 (Grafana Cloud)" 절 신설) · `monitoring/README.md` 옛 안내 정리

## 확인한 것 (로컬)

- `docker compose config`(더미 값): Alloy에 변수 7개가 전달되고, 밖으로 열리는 포트는 web의 80 · 443뿐. 필수 값이 없으면 그 변수 이름으로 실패
- `grafana/alloy:v1.14.1` 실제 이미지로 설정 로드: 컴포넌트 11개 모두 healthy, `MONITORING_ENV`를 비우면 `env="local"`, 로그 토큰을 비우면 지표 토큰으로 대체
- `deploy.sh` 검사: 값 누락 · http 주소는 exit 4로 멈추고, 따옴표 친 https 주소는 통과

## 고려한 제약 사항

- Cloud 실제 전송(인증 · 수신)은 운영 값으로만 확인할 수 있다 — 서버 반영 후 `deploy/README.md` "확인" 절대로 본다.
- 서버 `.env`에 남은 옛 `MAIN_PRIVATE_IP` · `LOKI_URL`은 더 읽지 않는다. 지워도 되고 남아도 무해하다.
- 전용 EC2(차선)로 돌아갈 일이 생기면 이번에 지운 포트 · 변수 · 검사를 함께 되돌려야 한다(`monitoring/README.md`에 기록).

## 추후 재검토가 필요하면

- Alloy · node-exporter 실측 메모리(상한 192m · 64m)를 보고 COMMON-52(파이프라인) 메모리 재조정 때 함께 본다.
- 대시보드 JSON은 Cloud에 Import한 것이라 파일과 자동 동기화되지 않는다 — 수정이 잦아지면 Grafana API로 올리는 스크립트를 검토한다.

## 운영 반영 중 발견 (10/23, 후속 수정)

- `MONITORING_AGENT=1` + `./deploy.sh --reapply`가 성공(스모크 통과)했는데 **alloy · node-exporter 컨테이너가 없었다.**
  `deploy.sh`의 `start_and_check`가 `backend` · `web`(+ 파이프라인)만 서비스 이름으로 올리기 때문이다. 오버레이가 붙어
  로그 볼륨은 생겼지만 에이전트를 띄우는 단계가 없었다 — COMMON-43(#168) 때는 에이전트를 손으로 한 번 띄우는 절차였고,
  그 뒤 자동 배포에 이 단계가 들어간 적이 없다.
- 수정: `start_monitoring_agent` 추가 — 사용자 경로 확인 뒤 `up -d --no-build --no-deps alloy node-exporter`.
  파이프라인(`start_pipeline`)과 같은 기준으로 **실패해도 배포를 되돌리지 않고 경고만 남긴다**(사용자 화면 · API와 무관하고,
  Alloy가 멈추면 Grafana Cloud 경보가 따로 알린다). `--no-deps`로 backend · web을 다시 건드리지 않는다.
- 서버는 같은 파일 조합(기본 + 외부 DB + 모니터링)으로 두 서비스만 `--no-deps`로 한 번 올려 즉시 복구했다.
- 확인(로컬, docker · curl · 스모크 스텁): `MONITORING_AGENT=1`이면 backend → web → `alloy node-exporter` 순으로 up, 없으면 기존과 같다.

## 경보 재구성 · 장애 시험 (10/26)

### 문제 / 배경

- Grafana Cloud 스택을 직접 확인해 보니 **경보 규칙이 0개**였다. 자동 생성된 recording rule 2개뿐이고, 연락처는 `empty`,
  기본 알림 경로도 `empty`였다. COMMON-63에서 변환해 올렸다고 기록된 규칙 · 연락처 · 정책이 이 스택에 없었다
  (다른 스택에 올렸는지, 나중에 지워졌는지는 확인하지 못했다). 즉 **백엔드가 죽어도 아무 알림이 가지 않는 상태**였다.
- 같은 날 로그(Loki)도 "30일간 0건"으로 보였다 → 서버에서 시험 한 줄을 넣어 15초 안에 수신을 확인했다.
  0건이었던 이유는 요청이 없어 backend가 새로 쓴 로그가 없었기 때문이고, 수집 경로는 정상이었다.

### 검토한 대안

| 안 | 판단 |
| --- | --- |
| COMMON-63처럼 Convert API로 `alert-rules.yml`을 다시 변환 | 변환 결과의 No data 처리가 Prometheus 방식(빈 결과 = 정상)과 달라, 비율 식이 요청 없을 때 울릴 수 있다. 조건 구조도 손으로 다시 봐야 한다 |
| **Grafana-managed 규칙을 직접 만들기(채택)** | 규칙마다 No data = OK, `env="prod"` 필터, 조건(`> -1`)을 명시적으로 정할 수 있다. 화면에서 바로 고칠 수 있는 형태 |
| `Watchdog` 유지(항상 음소거) | 음소거하면 Slack 전송 경로를 증명하지 못하고, 풀면 계속 쌓인다 → 넣지 않고 **실제 장애 시험**으로 경로를 확인했다 |
| 설정을 Terraform · 파일 프로비저닝으로 관리 | Cloud 스택에 파일 프로비저닝을 붙일 수 없고, 남은 기간 대비 과하다 → Export 사본을 `monitoring/cloud/`에 기록 |

### 선택한 방법과 이유

- 규칙 8개(로컬과 같은 기준) + 모든 식 `env="prod"` + 데이터 없음 = 정상. 서버 전체 정지는 `absent(up)` 규칙이 잡는다.
- 음소거 `운영시간외`(Asia/Seoul 평일 18:00 ~ 09:10 · 주말): 서버가 평일 09 ~ 18시에만 켜져 꺼진 시간의 알림을 막는다.
  09:10까지로 둔 이유는 부팅 직후 backend 준비 시간(1분 남짓) 동안 매일 "발생 → 해결"이 오는 것을 피하기 위해서다.
- Slack 메시지 양식(`lawnambul-slack` 템플릿): 기본 양식은 `Value: A=0, C=1`만 보여 규칙 요약이 나오지 않았다.
- 대시보드는 Cloud용 JSON 사본을 커밋하지 않는다. 원본 하나에서 데이터 소스 uid만 바꿔 가져온다(`monitoring/README.md`).

### 확인한 것 (운영)

- Contact point Test → Slack 수신
- `docker stop lawnambul-backend-1` → 정지 후 약 2분 40초에 `BackendDown` 발생 알림 → `docker start` 뒤 5분 안에 해결 알림.
  새 양식으로 한 번 더 같은 시험을 해 요약 · 시각(KST) · 링크가 보이는 것을 확인
- 저장소의 `slack-template.tmpl`을 Grafana 템플릿 시험 API로 렌더링 → Cloud에 있는 것과 결과가 같음
- 대시보드 4종(패널 33개 · 쿼리 50개)을 Cloud 데이터 소스로 실행 → 모두 오류 없음

### 고려한 제약 사항

- 설정 원본이 Cloud라 저장소 파일과 자동으로 맞춰지지 않는다. 화면에서 고치면 Export해 파일도 고친다.
- 알림 정책 Export는 Editor 권한으로 막혀(403) 같은 형식으로 옮겨 적었다.
- 해결 알림은 `group_interval`(5분) 때문에 최대 5분 늦다. 1분으로 줄이면 짧은 흔들림에도 알림이 잦아져 그대로 뒀다.
- 평일 공휴일 서버 정지는 음소거가 모른다 → 그날 Silence.

### 추후 재검토가 필요하면

- RDS 전환(10/28) 뒤 DB 지표(`postgres_exporter` 또는 CloudWatch)를 붙이면 경보를 추가한다.
- EC2 자체 지표(t3 CPU 크레딧 · 인스턴스 상태 검사)는 서버 안에서 볼 수 없다 → CloudWatch 경보 권한이 되면 검토.
- `cd-agent.sh`가 `.env` 읽기 권한을 확인하지 않아 권한 문제 때 조용히 진행할 수 있다(PR #282 리뷰) — 별도 이슈.
