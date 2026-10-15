import { mockCases } from './caseListMockData.js';
import { read, storageKey, validState } from './mockExperienceStore.js';

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

// API 2 — POST /cases/{caseId}/experience (있으면 기존 체험을 돌려준다)
export async function startExperience(caseId) {
  await delay();
  if (!mockCases.some((item) => item.caseId === caseId)) {
    fail('CASE_NOT_FOUND', '사건을 찾을 수 없습니다.');
  }
  if (caseId === 1) {
    const { status, lastReviewedStep } = read();
    return { caseId, attemptNo: 1, status, lastReviewedStep, startedAt: new Date().toISOString() };
  }
  if (!startedCases.has(caseId)) {
    startedCases.set(caseId, {
      caseId, attemptNo: 1, status: 'STARTED', lastReviewedStep: 0, startedAt: new Date().toISOString(),
    });
  }
  return structuredClone(startedCases.get(caseId));
}
