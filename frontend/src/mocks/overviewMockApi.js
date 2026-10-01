import { mockOverview } from './overviewMockData.js';
import { read, save, savePreJudgment } from './mockExperienceStore.js';

const delay = () => new Promise((resolve) => setTimeout(resolve, 250));

// HTTP 전송을 하지 않는 목이므로 HTTP 상태 대신 명세의 에러 본문을 던진다.
function fail(code, currentStatus, details = null) {
  const messages = {
    CASE_NOT_FOUND: '사건을 찾을 수 없습니다.',
    INVALID_STATE: '지금 단계에서는 할 수 없는 요청입니다.',
    VALIDATION_ERROR: '입력값을 확인해 주세요.',
    INVALID_RANGE_OPTION: '선택할 수 없는 형량 구간입니다.',
    INVALID_FACTOR: '선택할 수 없는 작용 요소입니다.',
    TOO_MANY_FACTORS: '작용 요소는 최대 2개까지 고를 수 있습니다.',
  };
  throw { code, message: messages[code], currentStatus, details };
}

function checkState(caseId) {
  const { status } = read();
  if (caseId !== mockOverview.case.caseId) fail('CASE_NOT_FOUND', status);
  if (status !== 'STARTED') fail('INVALID_STATE', status);
  return status;
}

// API 4 — GET /cases/{caseId}/experience/overview
export async function getOverview(caseId) {
  await delay();
  checkState(caseId);
  return structuredClone(mockOverview);
}

// API 5 — POST /cases/{caseId}/experience/pre-judgment
export async function postPreJudgment(caseId, body) {
  await delay();
  // 상태를 지연 뒤 읽는다. 검사부터 저장까지 await 없이 처리해 이중 제출을 막는다.
  const status = checkState(caseId);
  const isObject = body !== null && typeof body === 'object' && !Array.isArray(body);
  if (!isObject || !Number.isInteger(body.rangeOptionId)
    || (body.factorIds !== undefined && !Array.isArray(body.factorIds))) {
    fail('VALIDATION_ERROR', status);
  }
  if (!mockOverview.rangeOptions.some((item) => item.rangeOptionId === body.rangeOptionId)) {
    fail('INVALID_RANGE_OPTION', status);
  }
  const factorIds = body.factorIds ?? [];
  if (factorIds.length > 2) fail('TOO_MANY_FACTORS', status);
  if (new Set(factorIds).size !== factorIds.length
    || !factorIds.every((id) => mockOverview.preFactors.some((item) => item.factorId === id))) {
    fail('INVALID_FACTOR', status);
  }
  // S-09 최상단 "처음 판단 → 직접 판결"에서 써야 해서 사전 판단 선택값을 저장한다.
  savePreJudgment({ rangeOptionId: body.rangeOptionId, factorIds });
  return save({ status: 'PRE_JUDGED', lastReviewedStep: 1 });
}
