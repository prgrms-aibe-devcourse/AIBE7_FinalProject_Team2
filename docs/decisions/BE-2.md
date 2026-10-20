# BE-2 의사결정 기록

> MVP 단계(WBS 1.1) · PR #26 · 담당 정민석

## 문제 / 배경
- ERD 3장 기준 13개 테이블(MVP 11개 + 확장 2개, 기존 WBS의 11개에서 변경)에 대해 Flyway 마이그레이션을 구성해야 했다.
- 사형(DEATH)·무기징역(LIFE) 형벌과, 감경 후 선고되는 형벌(`judgment.reduced_to`)을 스키마 제약으로 어떻게 표현할지가 핵심이었다 (BE-13 #27의 ERD 설계를 DDL로 옮기는 작업).
- `ddl-auto`를 모든 환경에서 `validate`로 바꾸면서, 엔티티와 DB 스키마가 항상 일치해야 한다는 전제가 생겼다.

## 검토한 대안
- 감경 후 형벌 반영 범위: PR #27(BE-13) 리뷰에서 ERD의 CHECK 제약(`chk_judgment_penalty_values`, `chk_judgment_death_life_no_suspension`, `chk_judgment_reduced_to`)이 보완되었고, 이 PR에서 같은 내용을 마이그레이션 DDL에도 반영할지, 당장은 느슨하게 두고 나중에 보완할지 검토했다 (#26 코멘트, peanutboi710 요청).
- 형량 값의 범위 검증을 어디서 할지: DB CHECK로 양수만 보장하고 "집행유예 1~5년" 같은 법 규정 수준의 세부 범위는 서비스 코드 상수에서 검사할지, DB 제약으로 더 세밀하게 넣을지 검토했다.

## 선택한 방법과 이유
- (리뷰 반영) BE-13 ERD 보완 내용을 그대로 DDL에 반영: 최종 선고 형벌이 `PRISON`이면 `prison_months` 필수·`fine_amount` NULL, `penalty_type`이 `DEATH`·`LIFE`이면 `reduced_to`와 관계없이 `suspension_months` NULL, `reduced_to`는 `DEATH`→`LIFE`·`PRISON`, `LIFE`→`PRISON`만 허용.
- (리뷰 반영) 형량 값 양수 CHECK(`chk_judgment_positive_values`)를 추가해 DB는 형식(0보다 큼)만 보장하고, 집행유예 가능 연수 같은 법 규정 수준의 세부 범위는 서비스 코드 상수에서 검사하도록 역할을 나눴다. DB 제약을 법 규정 수준까지 넓히면 법이 바뀔 때마다 마이그레이션이 필요해진다.
- (리뷰 반영) 사용자 판단 공개 CHECK(`chk_judgment_user_published`)를 추가해 `subject_type = USER`이면 `is_published = true`를 DB 단에서도 보장했다.
- penalty_rule의 DEATH·LIFE 행은 법정형을 NULL로 두고, 선고 가능 범위에 감경 범위(무기 120~600개월, 사형 240~600개월)를 넣어 "감경했을 때의 선고 가능 범위"를 같은 테이블 구조로 표현했다.

## 고려한 제약 사항
- ERD v1.5 기준 `penalty_rule`은 (case_id, penalty_type) 유니크, `experience`·`comparison_analysis`는 CHECK 제약을 갖는다 (ERD 7장 공통 규칙).
- 같은 시기에 병합된 PR #28(COMMON-11)이 `docs/erd.md`·`docs/wbs.md`를 함께 수정해 작성 이력 버전 번호가 겹쳤다. `develop`을 먼저 반영해 충돌을 해결하면서 이력 번호(v1.5→v1.6)와 기준 문서 버전 표기를 다시 맞췄다.
- 사전 판단 형량 구간은 22행(사기·상해 7개, 살인은 벌금형을 빼고 무기징역·사형을 더해 8개)으로 고정값이다.

## 관련 문서
- `docs/erd.md` (3장 ERD, 7장 공통 규칙, 작성 이력)
- `docs/wbs.md`
