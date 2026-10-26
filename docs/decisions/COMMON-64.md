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
