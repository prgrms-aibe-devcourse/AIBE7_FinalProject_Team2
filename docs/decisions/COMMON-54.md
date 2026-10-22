# COMMON-54 의사결정 기록

> COMMON-52(AI 판결 파이프라인 운영 반영)의 하위 작업 · 담당 정민석

메인 EC2(t3.small 2GB)에 swap을 켜는 작업. 파이프라인 컨테이너를 올리기 전에(COMMON-52 · 53 · 55) 순간 급증에 대비한 안전망을 먼저 둔다.

## 문제 / 배경

- t3.small은 2GB인데 base 구성이 이미 1.75GB를 할당한다(backend 1g · postgres 512m · redis 256m). 실측 여유는 약 250MB이고, 모니터링 에이전트를 켠 서버는 그중 100MB를 이미 쓴다(COMMON-43 · COMMON-50).
- 서버에 swap이 없다. 메모리가 순간적으로 모자라면 커널 OOM이 **가장 큰 프로세스(backend)를 바로 죽인다**. 순간 급증이 생기는 때:
  - 파이프라인 extract(PDF · 비식별화) 단계 (COMMON-50: 쉴 때 약 32MB, extract 때 더 씀)
  - 배포 중 이미지 교체 · apt 업데이트 등 호스트 작업
- 파이프라인은 7.1.2 RDS 전환(10/28, postgres 512m이 빠짐) 뒤에 켜는 것이 전제지만(COMMON-50), 그 뒤에도 여유가 크지 않다.

## 검토한 대안

### 크기

- **1GB**: 디스크를 아끼지만, 2GB 서버에서 순간 급증(컨테이너 하나 분량 512MB ~ 1GB)을 다 받지 못할 수 있다.
- **2GB (메모리와 같은 크기)** ← 선택: 디스크 30GB 중 2GB. 이미지(배포마다 약 700MB, 두 벌 유지) · DB · 7일 백업과 나눠 써도 남는다. 스크립트가 "swap + 5GB" 여유를 확인한 뒤에만 만든다.
- **4GB 이상**: 메모리의 두 배를 swap으로 받는 상황이면 이미 서버가 감당할 수준을 넘은 것이다. 늘려 봐야 느린 상태가 길어질 뿐이라 의미가 없다.

### 방식

- **swap 파티션 / 별도 EBS 볼륨**: 볼륨 추가 · 연결은 IAM 권한이 필요하다(지금 없음, `deploy/README.md` 백업 · S3 절과 같은 제약).
- **zram(압축 메모리)**: 디스크를 쓰지 않아 빠르지만, 결국 같은 2GB 메모리를 나눠 쓰는 것이라 "메모리가 모자랄 때"의 안전망으로는 약하다.
- **swap 파일** ← 선택: 권한 없이 서버 안에서 끝나고, 크기 조정 · 제거가 쉽다.

### swappiness

- **Ubuntu 기본 60**: 여유가 있어도 안 쓰는 페이지를 미리 swap으로 내린다 → 디스크(EBS) I/O가 평소에도 생긴다.
- **10** ← 선택: 메모리가 정말 모자랄 때만 swap을 쓴다. 평소에는 swap이 0에 가까워야 정상이라는 기준을 세울 수 있다.
- **0 / 1**: 거의 쓰지 않다가 한계에서 한꺼번에 내리므로 급증 때 오히려 멈칫한다. 안전망 목적에는 10이 낫다.

### 컨테이너가 swap을 쓰게 할지

Docker는 `memswap_limit`을 주지 않으면, 서버에 swap이 있을 때 컨테이너에 `mem_limit`만큼 swap을 **더** 허용한다. 즉 swap을 켜는 순간 기존 컨테이너의 동작이 조용히 바뀐다.

- **그대로 둔다(전부 허용)**: backend가 1g를 넘어도 죽지 않고 swap으로 버틴다. 그런데 JVM 힙이 swap으로 밀리면 GC가 디스크를 기다려 응답이 수십 초씩 멈추고, 프로세스는 살아 있어 재시작도 되지 않는 **"느린 서버"** 가 된다. 사용자 입장에서는 재시작(20 ~ 40초)보다 나쁘다. Redis도 단일 스레드라 swap에 걸리면 모든 요청이 같이 느려진다.
- **사용자 요청을 받는 backend · postgres · redis만 막는다** ← 선택: `memswap_limit` = `mem_limit`으로 두면 swap을 켜기 전과 동작이 같다(넘으면 지금처럼 OOM 재시작). swap은 호스트 프로세스와 파이프라인 몫으로 남긴다.
- **파이프라인은 허용**: extract는 관리자가 기다리는 일괄 작업이라 느려져도 되고, 급증 때 죽는 것보다 낫다. 그래서 `docker-compose.pipeline.yml`에는 `memswap_limit`을 두지 않았다.

## 선택한 방법과 이유

- `deploy/setup-swap.sh`(다시 실행해도 안전):
  - 이미 켜진 swap이 있으면 만들지 않는다
  - 디스크 여유가 swap + 5GB 이상일 때만 `/swapfile` 2GB를 만든다(`fallocate`, 안 되면 `dd`) → 권한 600 → `mkswap` → `swapon`
  - `/etc/fstab`에 한 줄(없을 때만, 고치기 전에 사본) — 재부팅 뒤에도 켜진다
  - `vm.swappiness=10`을 `/etc/sysctl.d/99-lawnambul-swap.conf`에 — 재부팅 뒤에도 유지
  - `--check`로 바꾸지 않고 상태(메모리 · swap · swappiness · fstab · 디스크 · Docker swap 상한 지원)만 본다
- 서버 작업을 손으로 치지 않고 스크립트로 둔 이유: 다른 서버 작업(`backup-db.sh` · `cd-agent.sh`)과 같은 방식이고, 서버를 다시 만들거나 RDS 전환 뒤 다시 확인할 때 같은 절차를 그대로 쓸 수 있다.
- `docker-compose.prod.yml`의 backend · postgres · redis에 `memswap_limit`(= `mem_limit`).
- Grafana 인프라 대시보드의 메모리 패널에 "swap 사용" 선을 더했다 — 평소 0에 가까워야 하고, 계속 수백 MB면 상시 메모리가 된 것이다.

## 고려한 제약 사항

- **swap은 용량을 늘려 주지 않는다.** 파이프라인을 RDS 전환 뒤에 켠다는 COMMON-50 전제와 `deploy.sh` 메모리 가드(`PIPELINE_MEMORY_CHECKED`)는 그대로 둔다.
- `memswap_limit`은 커널이 컨테이너 swap 상한을 지원해야 적용된다(cgroup v2, Ubuntu 22.04 이상 기본). 지원하지 않으면 `docker info`에 `No swap limit support` 경고가 나오고 값이 무시된다 — 스크립트 `--check`와 README 확인 절차로 본다.
- `memswap_limit`은 컨테이너를 새로 만들 때 적용된다. 머지 후 자동 배포가 backend · postgres · redis를 다시 만들어 API가 20 ~ 40초 끊긴다(일반 배포와 같다).
- swap 파일에는 메모리에 있던 값(비밀값 포함 가능)이 내려갈 수 있다 → 권한 600. EBS 암호화 여부는 인스턴스 설정을 따른다.
- 디스크 30GB를 이미지 · DB · 백업과 나눈다. RDS 전환(10/28) 뒤에는 DB 데이터와 백업이 서버에서 빠져 여유가 늘어난다.

## 추후 재검토가 필요하면

- **COMMON-53(mem_limit · JVM 재조정)**: RDS 전환으로 postgres 512m이 빠진 뒤, swap 사용량(대시보드)과 `docker stats`를 보고 backend · 파이프라인의 `mem_limit`을 다시 나눈다. 이때 backend `memswap_limit`도 같은 값으로 같이 바꾼다.
- **swap이 상시로 쓰이면**: 크기를 늘리지 말고 메모리 배분을 고치거나 인스턴스 크기를 올린다.
- **경보**: 7.3.1(C)에 "swap 사용량이 N분 이상 일정 값 초과"를 넣을지 실제 사용량을 본 뒤 정한다.
