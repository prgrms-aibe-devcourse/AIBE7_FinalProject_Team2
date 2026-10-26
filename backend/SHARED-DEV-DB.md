# 공용 개발 DB (Supabase, RDS 전환 전 1주 한정)

> COMMON-59 · 기간: 2026-10-22 ~ 10/28 오전 (10/28 오후 운영 RDS 전환(BE-59) 뒤 삭제)

각자 로컬 Docker DB와 목데이터로만 보면 사람마다 결과가 달라 화면 · API 검증이 모호하다.
RDS는 요금 제한으로 10/28 오후부터 쓰므로, 10/22 ~ 10/28 오전 동안 **팀 4명이 같은 DB에 붙어 같은 실데이터로 확인**한다.
앱이 실제로 쓴 데이터(체험 · 판결 · 회원 연결 등)를 Supabase 화면에서 바로 볼 수 있다.

- 팀원 각자 **로컬에서 띄운 백엔드**가 이 DB에 붙는다.
- **EC2 운영 서버도 이 DB에 붙는다**(BE-59 외부 DB 모드, `deploy/README.md` "외부 DB 모드"). 운영에도 테스트 데이터만 있다는 판단(10/22)이라 같은 DB를 함께 쓴다 — 그래서 **팀원이 로컬에서 만든 데이터가 운영 화면에도 보이고, 운영 화면에서 만든 데이터도 여기에 쌓인다.** 운영 쪽 실제 사건 시드는 팀 합의 후 서버 `.env`에 `EXTERNAL_DB_ALLOWS_REAL_SEED=1`을 넣었을 때만 올라간다(없으면 `deploy.sh`가 막는다 — `deploy/README.md` "외부 DB(Supabase · RDS)에 실제 사건 시드 올리기").
- **테스트는 영향이 없다.** `./gradlew test`는 `build.gradle`이 로컬 테스트 DB(55432)만 허용한다([TESTING.md](TESTING.md)).
- Redis는 각자 로컬 그대로다(토큰 · 캐시는 공유하지 않는다).

## 한눈에 보기

| 항목 | 값 |
| --- | --- |
| 서비스 | Supabase 무료 플랜 (DB 500MB, 1주 미사용 시 일시정지) |
| 리전 | Northeast Asia (Seoul) |
| 연결 | **세션 풀러** `…pooler.supabase.com:5432` + `sslmode=require` (트랜잭션 풀러 6543 사용 금지) |
| 마이그레이션 · 시드 | **담당자 1명만** develop 기준으로 적용 (지금 담당: 정민석) |
| 팀원 | Flyway를 끄고 접속 (`SPRING_FLYWAY_ENABLED=false`). `DDL_AUTO`는 모든 모드에서 `validate` 고정 |
| 접속 정보 | `backend/.env.supabase` (커밋되지 않음 — 루트 `.gitignore`의 `.env.*`). 예시는 `backend/.env.supabase.example` |
| 실행 | `backend/run-shared-db.sh` (팀원) · `--migrate` (담당자, 가상 사건 시드) · `--migrate --with-private-seed` (담당자, 팀 합의 후 실제 사건 시드) · `--check` (검사만) |
| 대시보드 | Supabase 조직에 **Developer 역할**로 초대받으면 Table Editor · SQL Editor로 데이터를 본다 (초대만으로는 로컬 백엔드 접속이 안 된다 — 아래 규칙 5) |
| 데이터 확인 | Supabase 대시보드 Table Editor · SQL Editor |

## 팀 규칙

1. **세션 풀러(5432)만 쓴다.** 직접 연결 주소(`db.<ref>.supabase.co`)는 IPv6 전용이라 대부분의 집 · 사무실 네트워크(IPv4)에서 붙지 않고, 트랜잭션 풀러(6543)는 prepared statement를 지원하지 않아 JPA · Flyway가 깨진다.
2. **마이그레이션은 담당자만, develop 기준으로 적용한다.** (운영 backend도 배포할 때 **main 기준**으로 Flyway를 돌린다. main은 develop보다 뒤거나 같으므로, 담당자가 develop 기준으로 먼저 적용해 두면 운영 배포는 "할 일 없음"이 된다) 브랜치마다 마이그레이션 번호가 달라(V24 → V25처럼) 각자 Flyway를 켜고 붙으면 공용 DB 이력이 섞여 다른 사람 앱이 뜨지 않는다. 스크립트가 팀원 모드에서 Flyway를 끄고, 모든 모드에서 `DDL_AUTO=validate`로 고정해 Hibernate가 공용 스키마를 바꾸지 못하게 한다.
3. **새 마이그레이션(V26 등)은 develop에 머지된 뒤** 담당자가 적용한다. 브랜치 작업 중인 마이그레이션은 각자 로컬 DB에서 확인한다.
4. **공용 데이터다.** 관리자 화면에서 사건 공개 · 수정 · 판결 승인을 하면 모두에게 보인다. 지우거나 되돌리는 작업은 팀 채널에 먼저 알린다. SQL Editor에서 `DELETE` · `UPDATE`는 하지 않는다(필요하면 담당자에게).
   ⚠️ **이 규칙은 신뢰 기반이다.** 대시보드의 Read-only 역할은 Team · Enterprise 플랜에서만 쓸 수 있어서, 무료 플랜에서 초대받은 팀원(Developer)은 SQL Editor로 실제 쓰기가 가능하다. 기술적으로 막히지 않으니 각자 지킨다.
5. **로컬 백엔드를 붙이려면 DB 비밀번호가 필요하다 — 담당자가 DM으로만 준다.** Supabase 조직 초대는 대시보드(데이터 보기)만 열어 주고, 생성 뒤에는 대시보드에서도 비밀번호를 다시 보여 주지 않는다. 공개 채널 · 커밋 · PR · Jira에 쓰지 않는다.
6. **실데이터(비공개 시드)를 올리기 전에 팀이 합의한다.** 비공개 저장소(`backend/private-seed`)로 관리해 온 데이터다. 스크립트는 기본으로 가상 사건 시드만 넣고, 실제 사건 시드는 담당자가 `--with-private-seed`를 **직접 붙였을 때만** 넣는다(서브모듈이 받아져 있어도 자동으로 올라가지 않는다). 운영 서버도 같다 — `.env`에 경로(`REAL_SEED_DIR`)와 합의 표시(`EXTERNAL_DB_ALLOWS_REAL_SEED=1`)가 **둘 다** 있어야 올린다.
   **10/26 합의: RDS 전환 전 최종 테스트를 위해 공용 DB(Supabase)에 실제 사건 시드를 올린다.** 테스트 기간에는 공용 DB에서 실제 사건을 수정 · 삭제하지 않는다(규칙 4). RDS 전환이 확인되면 Supabase의 실제 데이터는 프로젝트 삭제와 함께 지운다.
   **사용 불가 데이터는 합의와 별개로 올리지 않는다.** LBox Open 판례는 라이선스상 사용 불가로 확인돼(10/12) BE-54에서 시드에서 지웠다(지금 시드에 0건). 다시 들어오면 스크립트가 `'LBOX'` 행을 보고 중단한다. 만약 이미 올라갔다면 RDS 전환 전에, 늦어도 종료 절차의 프로젝트 삭제와 함께 지운다.
7. **⚠️ 대시보드에서 DB 비밀번호를 리셋하지 않는다** (Project Settings → Database → Reset database password). 비밀번호를 모른다고 리셋하면 **팀 전원의 접속이 끊긴다.** 담당자에게 DM으로 받는다. 팀원을 **Developer 역할로 초대하면** 프로젝트 설정을 바꿀 수 없어 리셋도 막힌다(Owner · Administrator는 가능 — 담당자만 둔다).
8. **연결은 암호화만 하고 서버 인증서는 확인하지 않는다.** `sslmode=require`는 비밀번호 · 데이터를 TLS로 보내지만, 접속한 서버가 진짜 Supabase인지(인증서)는 검증하지 않는다. 1주 한시 운영이라 감수한다. 필요하면 대시보드 Database → SSL Configuration에서 CA 인증서를 받아 `sslmode=verify-full&sslrootcert=<경로>`로 바꿀 수 있다(이번에 직접 검증하지는 않았다. 스크립트는 `verify-ca` · `verify-full`도 허용한다). `sslmode`가 없거나 `disable` · `allow` · `prefer`면 스크립트가 중단한다.

## 담당자: 처음 한 번

### 1. 프로젝트 만들기

1. [supabase.com](https://supabase.com)에서 새 프로젝트를 만든다.
   - 이름: `lawnambul-dev`
   - Region: **Northeast Asia (Seoul)**
   - Database Password: 길고 무작위로 만든다(예: `openssl rand -base64 24`). 팀원에게 DM으로 전달한다.
2. **⚠️ Data API를 끈다.** 생성 화면의 Security에서 **Enable Data API · Automatically expose new tables · Enable automatic RLS를 모두 끄고** 만든다(만든 뒤라면 Project Settings → API → Data API Settings → **Enable Data API** 끄기).
   Supabase는 기본으로 `public` 스키마 테이블을 REST · GraphQL로 노출하는데, 우리 테이블은 RLS가 없어 API 키만 있으면 읽고 쓸 수 있다. 우리는 JDBC만 쓰므로 끈다.
3. 상단 **Connect** → **Session pooler**의 접속 문자열에서 호스트 · 사용자명을 확인한다.
   - 호스트 예: `aws-0-ap-northeast-2.pooler.supabase.com` (앞의 `aws-0` 부분은 프로젝트마다 다를 수 있다 — 대시보드 값을 그대로 쓴다)
   - 사용자명: `postgres.<프로젝트 ref>` (그냥 `postgres`가 아니다)
4. 팀원을 조직에 초대한다: Organization Settings → Team → Invite, 역할은 **Developer**. 데이터를 화면으로 보는 용도다(규칙 4 · 7). 로컬 백엔드를 붙일 팀원에게는 DB 비밀번호를 DM으로 따로 준다.

### 2. 접속 파일 만들기

```bash
cd backend
cp .env.supabase.example .env.supabase   # DB_URL의 호스트 · DB_USERNAME의 PROJECT_REF · DB_PASSWORD 세 값을 채운다
./run-shared-db.sh --check               # 띄우지 않고 설정만 검사 (예시 값 · 6543 포트 · 직접 연결 주소 등을 잡는다)
```

관리자 초기 계정을 만들 거면 `ADMIN_BOOTSTRAP_*`도 이번 한 번만 넣는다.

### 3. 스키마 · 시드 적용

```bash
# 저장소 루트에서
git checkout develop && git pull
cd backend

# 담당자 모드: develop 최신인지 확인한 뒤 Flyway를 켜고 띄운다 → V1 ~ 최신 마이그레이션 + 가상 사건 시드
./run-shared-db.sh --migrate

# (팀 합의 후에만) 실제 사건 시드까지 — 저장소 루트에서 서브모듈을 먼저 맞춘다
#   git submodule update --init backend/private-seed
./run-shared-db.sh --migrate --with-private-seed
```

- `--migrate`는 **develop이 origin/develop과 같을 때만** 실행된다(브랜치 작업 중인 마이그레이션이 공용 DB에 들어가지 않게).
- Flyway 위치는 스크립트가 정한다(셸 · `.env.local`의 `FLYWAY_LOCATIONS`는 무시) — 저장소의 마이그레이션 · 가상 시드만, `--with-private-seed`일 때만 `private-seed/seed`를 더한다.
- 시작 로그의 `담당자 모드 — … (시드: 가상 사건 시드만)` / `(시드: 가상 + 실제 사건 시드 …)`로 무엇을 올렸는지 본다.
- `vector` 확장 생성에서 실패하면 Supabase Database → Extensions에서 **vector**를 켜고 다시 실행한다.
- 적용이 끝나면 `Ctrl+C`로 내리고, 관리자 계정을 만들었으면 `.env.supabase`에서 `ADMIN_BOOTSTRAP_*` 값을 지운다.

### 4. 확인 (Supabase SQL Editor)

```sql
-- 마이그레이션: 최신 버전이 develop과 같고 success가 모두 true
select version, description, success from flyway_schema_history order by installed_rank desc limit 5;

-- 사건: 상태별 개수 (공개된 사건이 있어야 목록 화면에 보인다)
select status, count(*) from legal_case group by status;

-- 용량: 무료 플랜 500MB 안인지. 10/22 로컬 실측으로 실데이터 · RAG 시드까지 넣어 약 14MB
-- (임베딩 vector(3072) 때문에 양형기준 행당 약 15KB · 판례 행당 약 21KB, 나머지 테이블은 각 0.1MB 이하)
select pg_size_pretty(pg_database_size(current_database()));
```

### 이후: develop에 새 마이그레이션이 머지되면

```bash
# 저장소 루트에서
git checkout develop && git pull
git submodule update --init backend/private-seed   # 실제 사건 시드를 쓰는 경우만
cd backend
./run-shared-db.sh --migrate                       # 새 버전만 적용된다 (실제 사건 시드를 쓰면 --with-private-seed도 붙인다)
```

적용했으면 팀 채널에 "공용 DB V__ 적용"을 알린다. 그 전까지 팀원이 새 컬럼이 있는 develop으로 띄우면 아래 "문제가 생겼을 때"의 검증 실패가 난다.

## 팀원: 접속해서 쓰기

1. 담당자에게 DM으로 비밀번호 · 사용자명(`postgres.<ref>`) · 호스트를 받는다. (대시보드 초대는 데이터 보기용이다 — 비밀번호를 리셋하지 않는다)
2. 접속 파일을 만들고 검사한다.
   ```bash
   cd backend
   cp .env.supabase.example .env.supabase   # 세 값만 채운다. ADMIN_BOOTSTRAP_*는 비워 둔다
   ./run-shared-db.sh --check
   ```
3. 실행한다. 스크립트가 로컬 Redis만 띄우고(postgres는 띄우지 않는다), Flyway를 끈 채 `bootRun`을 실행한다.
   ```bash
   ./run-shared-db.sh
   ```

프론트는 평소대로 `npm run dev`(`VITE_API_MODE=real`)로 띄운다. 화면에서 체험 · 로그인 · 판결 입력을 한 뒤 Supabase **Table Editor**에서 바로 확인한다.

```sql
-- 방금 내 화면에서 만든 체험 기록 (최근 순)
select id, case_id, anonymous_user_id, member_id, status, updated_at from experience order by updated_at desc limit 10;

-- 카카오 로그인 후 익명 기록이 회원에 연결됐는지 (member_id가 채워진다)
select id, member_id, last_seen_at from anonymous_user order by last_seen_at desc limit 10;
```

**로컬 DB로 돌아갈 때**는 스크립트 대신 평소처럼 `docker compose up -d` → `./gradlew bootRun`을 실행한다(`.env.supabase`는 스크립트만 읽는다).

**IntelliJ에서 실행할 때**: IntelliJ 내장 터미널에서 `./run-shared-db.sh`를 실행하는 게 가장 간단하다. Gradle 창의 `bootRun`이나 `BackendApplication`의 ▶로 띄우면 `.env.supabase`를 읽지 않아 로컬 DB로 붙는다.

## 접속 파일 · 실행 스크립트

- **`backend/.env.supabase.example`** (커밋됨, 비밀값 없음) → `backend/.env.supabase`로 복사해 세 값(`DB_URL`의 호스트 · `DB_USERNAME`의 ref · `DB_PASSWORD`)만 채운다. 연결 수(3) · Flyway(false) 같은 나머지 값은 예시 그대로 둔다.
- JWT · 카카오 키 같은 나머지 실행 값은 기존 `backend/.env.local`을 그대로 쓴다.
- **`backend/run-shared-db.sh`** 가 두 파일을 읽어 `bootRun`을 실행한다. 띄우기 전에 아래를 검사하고, 걸리면 이유를 알려 주고 멈춘다(비밀번호는 출력하지 않는다).

| 검사 | 걸리면 |
| --- | --- |
| `.env.local` · `.env.supabase`가 있는지 | 만드는 방법을 안내하고 중단 |
| 세 값이 비었거나 예시 값(`PROJECT_REF` · `CHANGE_ME` · 꺾쇠)이 남았는지 | 중단 |
| `.env.supabase.example`(커밋되는 예시 파일)에 실제 값이 들어 있음 | 중단 — `.env.supabase`로 옮기라고 안내 |
| `DB_URL`에 사용자명 · 비밀번호(`…@`)가 들어 있음 — 대시보드의 `postgresql://…` 문자열을 통째로 붙인 경우 | 중단 (주소의 사용자 정보는 어떤 경우에도 출력하지 않는다) |
| 포트 6543(트랜잭션 풀러) · 직접 연결 주소(IPv6) · 세션 풀러가 아닌 주소 | 중단 |
| `sslmode`가 없거나 `require` · `verify-ca` · `verify-full`이 아님 | 중단 (암호화 없는 연결을 막는다, 규칙 8) |
| `DB_URL` 대신 다른 DB · 설정을 쓰게 하는 환경변수: `SPRING_DATASOURCE_*`(연결 풀 크기 제외) · `SPRING_APPLICATION_JSON` · `SPRING_CONFIG_*` · `SPRING_PROFILES_*` · `SPRING_JPA_HIBERNATE_DDL*` · `SPRING_FLYWAY_*`(`ENABLED` 제외), `JAVA_TOOL_OPTIONS` 등의 `-Dspring.*` | 중단 — 셸 export · `.env.local` · `.env.supabase` 어디서 왔든 같다 (PR #245 리뷰) |
| `DDL_AUTO`가 `validate`가 아님 | **validate로 고정**하고 계속 |
| 사용자명이 `postgres.<ref>` 형식인지 | 중단 |
| 팀원 실행인데 `SPRING_FLYWAY_ENABLED`가 켜져 있음 | **false로 고정**하고 계속 |
| 팀원 실행인데 `ADMIN_BOOTSTRAP_*` 값이 있음 | 중단 |
| `--migrate`인데 develop이 아니거나 origin/develop과 다름 · 마이그레이션 폴더에 커밋 안 한 변경 | 중단 |
| `--migrate`일 때 Flyway 위치 | 스크립트가 직접 정함 (`FLYWAY_LOCATIONS` 값은 무시) |
| `--with-private-seed`인데 서브모듈이 비었거나 저장소가 가리키는 커밋과 다름 | 중단 |
| `--with-private-seed`인데 시드에 LBox 행(`'LBOX'`)이 있음 | 중단 (사용 불가 데이터, 규칙 6) |
| `--with-private-seed`를 `--migrate` 없이 씀 | 중단 |

macOS 기본 bash(3.2)에서도 동작한다.

## 문제가 생겼을 때

| 증상 | 원인 · 조치 |
| --- | --- |
| 접속이 안 되고 호스트를 못 찾거나 네트워크에 닿지 않는다는 오류 | 직접 연결 주소(`db.<ref>.supabase.co`, IPv6 전용)를 쓴 경우다. 세션 풀러 주소로 바꾼다 |
| `prepared statement "S_1" already exists` 같은 오류 | 트랜잭션 풀러(포트 6543)를 쓴 경우다. 포트를 **5432**로 바꾼다 |
| 비밀번호는 맞는데 인증 실패 | 사용자명이 `postgres.<프로젝트 ref>`인지 본다(세션 풀러는 ref가 붙는다) |
| 시작할 때 `Schema-validation: missing column` · `missing table` | 내 브랜치(또는 최신 develop)에 공용 DB에 아직 없는 마이그레이션이 있다. 그 작업은 로컬 DB로 확인하거나, develop 머지 후 담당자 적용을 기다린다 |
| `Validate failed: Migrations have failed validation` | 누군가 스크립트 없이(`./gradlew bootRun` 직접) Flyway를 켠 채 다른 브랜치로 붙었다. 담당자에게 알린다 — 공용 DB는 `./run-shared-db.sh`로만 붙는다 |
| 연결 수가 가득 찼다는 오류 | 각자 `SPRING_DATASOURCE_HIKARI_MAXIMUMPOOLSIZE=3`인지(예전 예시의 `MAXIMUM_POOL_SIZE` 이름도 적용된다), 백엔드를 여러 개 띄워 두지 않았는지 본다 |
| 어제까지 되던 접속이 모두 인증 실패 | 누군가 대시보드에서 DB 비밀번호를 리셋했을 가능성이 크다. 담당자가 새 비밀번호를 DM으로 다시 돌린다(규칙 7) |
| 대시보드에 프로젝트가 Paused | 1주 동안 쓰지 않아 일시정지됐다. 대시보드에서 Restore한다(데이터는 남아 있다) |

## 종료 (RDS 전환 후)

0. **운영을 먼저 RDS로 옮긴다**(BE-59, `deploy/README.md` "외부 DB 모드 > RDS 전환 때"). 운영이 아직 이 DB에 붙어 있는 동안 프로젝트를 지우면 운영 서비스가 멈춘다.
1. 팀원: `backend/.env.supabase`를 지운다.
2. 담당자: Supabase Settings → General → **Delete project**.
3. RDS는 Flyway로 새로 적용한다(BE-59). Supabase의 테스트 데이터는 옮기지 않는다.
4. 이 문서 · `run-shared-db.sh` · `.env.supabase.example` · `.gitignore` 예외 줄과 README · TESTING.md의 링크를 지우는 PR을 올린다.
