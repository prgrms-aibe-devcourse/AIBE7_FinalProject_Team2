import { mockSections, mockLaw, mockSummary } from './reviewMockData.js';
import { defaultSteps, initialState, validState, save, read, clearJudgment } from './mockExperienceStore.js';

const allowedStatuses = ['PRE_JUDGED', 'REVIEWING', 'REVIEWED'];
const delay = () => new Promise((resolve) => setTimeout(resolve, 250));

// HTTP 전송을 하지 않는 목이므로 HTTP 상태 대신 명세의 에러 본문을 던진다.
function fail(code, currentStatus, details = null) {
  const messages = {
    INVALID_STATE: '지금 단계에서는 할 수 없는 요청입니다.',
    VALIDATION_ERROR: '입력값을 확인해 주세요.',
    STEP_OUT_OF_ORDER: '이전 섹션을 먼저 확인해 주세요.',
    CASE_NOT_FOUND: '사건을 찾을 수 없습니다.',
  };
  throw { code, message: messages[code], currentStatus, details };
}

function checkState(state, caseId) {
  if (caseId !== 1) fail('CASE_NOT_FOUND', state.status);
  if (!allowedStatuses.includes(state.status)) fail('INVALID_STATE', state.status);
}

function progress(state) {
  return { ...state, openStep: state.lastReviewedStep === 4 ? null : state.lastReviewedStep + 1 };
}

export async function getReview(caseId) {
  await delay();
  const state = read();
  checkState(state, caseId);
  const result = progress(state);
  const visibleThrough = result.openStep ?? 4;
  return structuredClone({
    ...result,
    sections: Object.values(mockSections)
      .filter(({ step }) => step <= visibleThrough)
      .map((section) => ({ ...section, confirmed: section.step <= state.lastReviewedStep })),
    lockedSteps: [1, 2, 3, 4].filter((step) => step > visibleThrough),
    law: visibleThrough === 4 ? mockLaw : null,
    summary: state.status === 'REVIEWED' ? mockSummary : null,
  });
}

export async function postReviewStep(caseId, step) {
  await delay();
  // 지연 뒤 최신 상태를 읽고 동기적으로 갱신해 중복 요청도 한 단계만 진행한다.
  const state = read();
  checkState(state, caseId);
  if (!(Number.isInteger(step) && [2, 3, 4].includes(step))) {
    fail('VALIDATION_ERROR', state.status, [{ field: 'step', reason: 'MUST_BE_INTEGER_2_3_OR_4' }]);
  }
  if (step <= state.lastReviewedStep) return progress(state);
  if (step !== state.lastReviewedStep + 1) fail('STEP_OUT_OF_ORDER', state.status);
  return progress(save({ status: step === 4 ? 'REVIEWED' : 'REVIEWING', lastReviewedStep: step }));
}

export async function resetMock() {
  await delay();
  clearJudgment();
  return save(initialState());
}

export async function setMockStatus(status, lastStep = defaultSteps[status]) {
  await delay();
  const state = { status, lastReviewedStep: lastStep };
  if (!validState(state)) {
    fail('VALIDATION_ERROR', read().status, [{ field: 'lastStep', reason: 'INCONSISTENT_STATE' }]);
  }
  return save(state);
}
