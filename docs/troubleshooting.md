# 트러블슈팅 (로컬 개발 환경)

로컬에서 백엔드 · 프론트를 띄우다 막힌 경우와 해결 방법을 모은다. 새 문제를 해결하면 같은 형식(증상 → 원인 → 확인 → 해결)으로 항목을 추가한다.

> 실제 사건 내용(형량 · 판단 요소 · 사실관계)은 이 문서에 쓰지 않는다. 공개 저장소다.

**작성 이력**

| 버전 | 날짜 | 내용 |
| --- | --- | --- |
| v1 | 2026-10-06 | 초안 (BE-28) — 실제 사건이 화면에 안 보임, 8080 포트 충돌, 프론트 목 · 실제 모드 구분, 실행 전 점검 순서 |

---

## 0. 먼저 볼 것 (실행 전 점검 순서)

| 순서 | 확인 | 명령 |
| --- | --- | --- |
| 1 | DB 컨테이너가 떠 있는가 | `cd backend && docker compose ps` → `lawnambul-postgres`가 `healthy` |
| 2 | 8080을 다른 백엔드가 쓰고 있지 않은가 | `lsof -nP -iTCP:8080 -sTCP:LISTEN` → `java`가 없어야 한다 (3장) |
| 3 | 실제 사건을 볼 거라면 서브모듈이 있는가 | `ls backend/private-seed/seed` → `R__10` · `R__20` · `R__30` |
| 4 | 백엔드 실행 | `cd backend && ./gradlew bootRun` |
| 5 | 기동 로그의 `[bootRun]` 두 줄 | `Flyway 위치(...)` · `실제 사건 시드: 포함 / 없음` |
| 6 | 프론트를 실제 API로 실행 | `cd frontend && VITE_API_MODE=real npm run dev` |

---

## 1. 실제 사건이 화면에 안 보이고 가상 사건만 나온다

**증상**

- 프론트를 실제 모드(`VITE_API_MODE=real`)로 띄웠는데 사건 목록에 가상 사건("빌린 돈 문제로 찾아온 지인을 살해한 사건", caseId 1)만 보인다.
- 가상 시드 사건 제목이 프론트 목 데이터와 같아서 **"목업이 나온다"로 착각하기 쉽다.**

**원인 (2026-10-06 재현)**

- 백엔드가 실제 사건 위치(`filesystem:./private-seed/seed`) 없이 떴다. Flyway가 `R__10` · `R__20` · `R__30`을 한 번도 실행하지 않았다.
- 예전에는 실행 직전 셸에 `FLYWAY_LOCATIONS=...`를 붙여야 했다. 아래처럼 값이 빠지기 쉬운데, **빠져도 오류 없이** 시드 없이 뜬다.
  - `export`한 터미널 탭과 `./gradlew bootRun`을 실행한 탭이 다름
  - IDE(IntelliJ) 실행 버튼으로 띄움 (셸 환경변수를 받지 않음)
  - 긴 한 줄을 붙여 넣다가 줄이 나뉨 → 앞부분 `FLYWAY_LOCATIONS=...`가 실행되는 프로그램에 전달되지 않고 셸 변수로만 남음

**확인**

```bash
# 백엔드가 돌려주는 사건 (실제 사건 ID는 1이 아닐 수 있다)
curl -s http://localhost:8080/api/v1/cases | python3 -m json.tool | grep -E '"caseId"|"title"'

# 실제 사건 시드가 실행된 기록 (10 real case content · 20 court judgment · 30 ai judgment)
docker exec lawnambul-postgres psql -U lawnambul -d lawnambul -c \
  "select description, success, installed_on from flyway_schema_history where version is null order by installed_rank;"

# 떠 있는 백엔드 프로세스가 FLYWAY_LOCATIONS를 받았는지 (macOS)
ps eww -p "$(lsof -nP -iTCP:8080 -sTCP:LISTEN -t -a -c java)" | tr ' ' '\n' | grep '^FLYWAY_LOCATIONS='
```

**해결 (BE-28 이후)**

- 환경변수 없이 `./gradlew bootRun`만 실행한다. `backend/build.gradle`이 위치를 자동으로 정한다.
  - 가상 사건 시드(`db/seed`)는 항상
  - `backend/private-seed/seed`에 SQL이 있으면 실제 사건도 (절대 경로로 넘겨 실행 위치와 상관없음)
- 기동 로그에서 확인한다.

  ```
  [bootRun] Flyway 위치(자동): classpath:db/migration,classpath:db/seed,filesystem:/…/backend/private-seed/seed
  [bootRun] 실제 사건 시드: 포함
  ... Migrating schema "public" with repeatable migration "10 real case content"
  ```
- `실제 사건 시드: 없음 (서브모듈이 비어 있음)`이 나오면 저장소 루트에서 서브모듈을 받는다 (비공개 저장소 권한 필요).

  ```bash
  git submodule update --init backend/private-seed
  ```
- `FLYWAY_LOCATIONS`를 직접 주면 자동 설정보다 우선한다. 로그에 `(직접 지정)`으로 표시된다. 일부러 스키마만 띄울 때만 쓴다.

---

## 2. 백엔드가 "꺼졌다가 다시 안 켜진다"

**증상**

- `./gradlew bootRun`이 `Port 8080 was already in use`, 또는 `Web server failed to start`로 끝난다.

**원인**

- 이전에 띄운 백엔드가 아직 8080을 쓰고 있다. 다른 터미널 탭, IDE, Claude Code 같은 도구의 **백그라운드 작업**으로 띄운 서버는 터미널에 보이지 않아 놓치기 쉽다. (2026-10-06 재현: 검증용으로 띄운 백그라운드 서버가 남아 있었음)

**확인 · 해결**

```bash
lsof -nP -iTCP:8080 -sTCP:LISTEN      # java 프로세스가 있으면 이전 서버
kill <PID>                            # 그 java의 PID
cd backend && ./gradlew --stop        # (선택) Gradle 데몬 정리
./gradlew bootRun
```

- 같은 목록에 `ssh`가 8080을 잡고 있는 경우가 있다(Rancher Desktop 포트 포워딩). **`ssh`는 끄지 않는다.** 백엔드 실행을 막지 않는다. `localhost`는 IPv6(`::1`)로 먼저 연결되어 로컬 `java`로 간다.

---

## 3. 프론트가 목 데이터인지 실제 API인지 헷갈린다

| 실행 | 데이터 |
| --- | --- |
| `npm run dev` (기본) | **목 모드**. 프론트 안의 가상 사건 3건(`src/mocks/`) |
| `VITE_API_MODE=real npm run dev` | **실제 API**. 백엔드(8080)가 돌려주는 사건 (가상 시드 + 실제 사건) |
| `preview.html` | 항상 목 데이터 (화면 단독 확인용) |

- 개발 서버가 이미 5173에 떠 있으면 새로 띄운 서버는 5174 등 다른 포트로 뜬다. **브라우저 주소의 포트가 지금 띄운 서버인지** 확인한다. (`lsof -nP -iTCP:5173 -sTCP:LISTEN`)
- 실제 모드인데 가상 사건만 보이면 프론트가 아니라 **백엔드 시드 문제**다 → 1장.

---

## 4. 그 밖에 자주 나오는 오류

| 메시지 | 원인 | 해결 |
| --- | --- | --- |
| `Connection refused` (5432) | DB 컨테이너가 꺼져 있음 (Docker · Rancher Desktop 미실행 포함) | Docker 앱 실행 후 `cd backend && docker compose up -d` |
| `legal_case에서 title=…를 찾을 수 없습니다` | `R__20` · `R__30`보다 `R__10`(실제 사건 콘텐츠)이 먼저 들어가지 않음 | 1장 확인. 서브모듈 위치가 Flyway 위치에 포함됐는지 |
| `판단 요소 번호 · 라벨이 DB와 다릅니다` | 비공개 시드에서 요소 라벨을 바꿨는데 기존 DB에는 옛 라벨이 남음 (`R__10`은 이미 있는 사건을 건너뜀) | 비공개 저장소 README "반복 마이그레이션 공통 주의" 참고. 로컬이면 DB를 비우고 다시 적재 |
| `Validate failed` / checksum mismatch (`V*`) | 이미 적용된 버전 마이그레이션 파일을 고침 | `V*` 파일은 고치지 않는다. 새 버전 파일을 추가 |

로컬 DB를 처음부터 다시 만들려면 (**로컬 체험 기록까지 모두 지워진다**):

```bash
cd backend
docker compose down -v && docker compose up -d
./gradlew bootRun
```
