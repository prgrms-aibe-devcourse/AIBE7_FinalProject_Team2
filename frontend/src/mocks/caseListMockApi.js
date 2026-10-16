import { mockCases } from './caseListMockData.js';
import { mockOverview } from './overviewMockData.js';
import { read, restart, storageKey, validState } from './mockExperienceStore.js';

const delay = () => new Promise((resolve) => setTimeout(resolve, 250));
const crimeTypes = ['MURDER', 'FRAUD', 'INJURY'];
// caseId 1 외 사건의 체험은 새로고침하면 사라지는 메모리 상태로만 둔다.
const startedCases = new Map();

// HTTP 전송을 하지 않는 목이므로 HTTP 상태 대신 명세의 에러 본문을 던진다.
function fail(code, message) {
  throw { code, message };
}

// 목록 배지용 내 상태 (FE-19). 실제 API처럼 세부 상태를 진행 중 / 완료 두 가지로만 묶는다.
const toMyStatus = (status) => (status === 'COMPLETED' ? 'COMPLETED' : 'IN_PROGRESS');

// 아래 startExperience와 같은 저장소를 본다 → 배지와 "이어서 하기"가 여는 화면이 어긋나지 않는다.
// 실제 API처럼 체험 기록이 없으면 null(배지 없음 · "체험 시작"). caseId 1은 목 저장소에 값이 생겨야(체험 시작 시 read()가 저장)
// 진행 중이고, 나머지는 이번 세션에 시작한 사건만 진행 중. 목록을 보기만 해서 기록이 생기지 않도록 read()는 부르지 않는다.
function myStatusOf(caseId) {
  if (caseId === 1) {
    const raw = localStorage.getItem(storageKey);
    if (!raw) return null; // 체험 전 — read()와 같은 기준(값 없음 · 빈 값). FE-19 리뷰: 목에서도 "체험 시작" 상태가 보이게
    let stored = null;
    try { stored = JSON.parse(raw); } catch { /* 손상된 값은 read()가 초기 상태로 되살리므로 진행 중으로 본다 */ }
    return validState(stored) ? toMyStatus(stored.status) : 'IN_PROGRESS';
  }
  const started = startedCases.get(caseId);
  return started ? toMyStatus(started.status) : null;
}

// API 1 — GET /cases
export async function getCases(crimeType) {
  await delay();
  if (crimeType !== undefined && !crimeTypes.includes(crimeType)) {
    fail('VALIDATION_ERROR', '입력값을 확인해 주세요.');
  }
  const byCrimeType = Object.fromEntries(crimeTypes.map((type) => [
    type, mockCases.filter((item) => item.crimeType === type).length,
  ]));
  return structuredClone({
    summary: { total: mockCases.length, byCrimeType },
    cases: mockCases
      .filter((item) => !crimeType || item.crimeType === crimeType)
      .map((item) => ({ ...item, myStatus: myStatusOf(item.caseId) })),
  });
}

// API 16 — GET /cases/{caseId} (S-20 사건 디테일). API 1 카드 값 + 죄명 · 사건 개요 · 내 상태.
// 사건 1은 S-03 개요 목(API 4)과 같은 죄명 · 개요를 쓰고, 나머지 사건은 유형별 죄명 · 짧은 소개로 채운다(임의 데이터)
const CHARGE_NAMES = { MURDER: '살인', FRAUD: '사기', INJURY: '상해' };
export async function getCaseDetail(caseId) {
  await delay();
  const item = mockCases.find((entry) => entry.caseId === caseId);
  if (!item) fail('CASE_NOT_FOUND', '사건을 찾을 수 없습니다.');
  const known = caseId === mockOverview.case.caseId ? mockOverview.case : null;
  return structuredClone({
    ...item,
    chargeName: known?.chargeName ?? CHARGE_NAMES[item.crimeType],
    overview: known?.overview ?? item.shortIntro,
    myStatus: myStatusOf(caseId),
  });
}

// API 2 — POST /cases/{caseId}/experience (있으면 기존 체험을 돌려준다)
export async function startExperience(caseId) {
  await delay();
  if (!mockCases.some((item) => item.caseId === caseId)) {
    fail('CASE_NOT_FOUND', '사건을 찾을 수 없습니다.');
  }
  if (caseId === 1) {
    // 체험 기록이 없으면(myStatus null) 실제 API처럼 새 체험(STARTED, 1막)으로 시작한다. 목 저장소의 초기값(PRE_JUDGED)은
    // 주소로 S-04 이후 화면을 바로 열 때만 쓴다(FE-32 — S-20 "체험 시작"이 3막으로 건너뛰던 문제)
    const { status, lastReviewedStep } = myStatusOf(caseId) === null ? restart() : read();
    return { caseId, attemptNo: 1, status, lastReviewedStep, startedAt: new Date().toISOString() };
  }
  if (!startedCases.has(caseId)) {
    startedCases.set(caseId, {
      caseId, attemptNo: 1, status: 'STARTED', lastReviewedStep: 0, startedAt: new Date().toISOString(),
    });
  }
  return structuredClone(startedCases.get(caseId));
}
