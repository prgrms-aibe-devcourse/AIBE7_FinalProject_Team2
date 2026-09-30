import { mockCases } from './caseListMockData.js';
import { read } from './mockExperienceStore.js';

const delay = () => new Promise((resolve) => setTimeout(resolve, 250));
const crimeTypes = ['MURDER', 'FRAUD', 'INJURY'];
// caseId 1 외 사건의 체험은 새로고침하면 사라지는 메모리 상태로만 둔다.
const startedCases = new Map();

// HTTP 전송을 하지 않는 목이므로 HTTP 상태 대신 명세의 에러 본문을 던진다.
function fail(code, message) {
  throw { code, message };
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
    cases: mockCases.filter((item) => !crimeType || item.crimeType === crimeType),
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
