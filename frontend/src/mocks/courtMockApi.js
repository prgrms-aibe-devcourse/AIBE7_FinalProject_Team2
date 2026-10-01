import { mockCourtJudgment } from './courtMockData.js';
import { read, save } from './mockExperienceStore.js';

const delay = () => new Promise((resolve) => setTimeout(resolve, 250));

// HTTP 전송을 하지 않는 목이므로 HTTP 상태 대신 명세의 에러 본문만 던진다.
function fail(code, currentStatus) {
  const messages = { CASE_NOT_FOUND: '사건을 찾을 수 없습니다.', INVALID_STATE: '지금 단계에서는 할 수 없는 요청입니다.' };
  throw { code, message: messages[code], currentStatus };
}

function checkState(caseId, allowed) {
  const { status } = read();
  if (caseId !== 1) fail('CASE_NOT_FOUND', status);
  if (!allowed.includes(status)) fail('INVALID_STATE', status);
  return status;
}

// API 11 — POST /cases/{caseId}/experience/court-reveal
export async function postCourtReveal(caseId) {
  await delay();
  const status = checkState(caseId, ['VERDICT_CONFIRMED', 'AI_REVEALED', 'COMPLETED']);
  // 이미 공개됐으면 상태를 그대로 두고 현재 상태를 돌려준다(API 11 명세).
  if (status !== 'VERDICT_CONFIRMED') return { status };
  return save({ status: 'AI_REVEALED', lastReviewedStep: 4 });
}

// API 12 — GET /cases/{caseId}/experience/judgments/court
export async function getCourtJudgment(caseId) {
  await delay();
  // AI_REVEALED 전(= S-07 미확인) 거절. 화면은 S-07로 안내한다(REQ-050 순서 유지).
  checkState(caseId, ['AI_REVEALED', 'COMPLETED']);
  return structuredClone(mockCourtJudgment);
}

// API 13 — POST /cases/{caseId}/experience/comparison-reveal
export async function postComparisonReveal(caseId) {
  await delay();
  const status = checkState(caseId, ['AI_REVEALED', 'COMPLETED']);
  if (status !== 'AI_REVEALED') return { status };
  return save({ status: 'COMPLETED', lastReviewedStep: 4 });
}
