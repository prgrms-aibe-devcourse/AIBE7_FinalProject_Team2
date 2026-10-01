import { mockAiJudgment } from './aiMockData.js';
import { read } from './mockExperienceStore.js';

const delay = () => new Promise((resolve) => setTimeout(resolve, 250));

// HTTP 전송을 하지 않는 목이므로 HTTP 상태 대신 명세의 에러 본문만 던진다.
function fail(code, currentStatus) {
  const messages = { CASE_NOT_FOUND: '사건을 찾을 수 없습니다.', INVALID_STATE: '지금 단계에서는 할 수 없는 요청입니다.' };
  throw { code, message: messages[code], currentStatus };
}

function checkState(caseId) {
  const { status } = read();
  if (caseId !== 1) fail('CASE_NOT_FOUND', status);
  // VERDICT_CONFIRMED 전 거절. AI_REVEALED · COMPLETED에서도 다시 볼 수 있다(9장 "공개된 결과 화면끼리 이동").
  if (!['VERDICT_CONFIRMED', 'AI_REVEALED', 'COMPLETED'].includes(status)) fail('INVALID_STATE', status);
  return status;
}

// API 10 — GET /cases/{caseId}/experience/judgments/ai
export async function getAiJudgment(caseId) {
  await delay();
  checkState(caseId);
  return structuredClone(mockAiJudgment);
}
