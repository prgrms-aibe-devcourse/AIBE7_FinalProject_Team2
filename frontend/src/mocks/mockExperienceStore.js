// 목 전용 저장소. 실제 체험 진행 상태는 서버에서 관리한다.
export const storageKey = 'nlnb-mock-review';
export const defaultSteps = {
  STARTED: 0, PRE_JUDGED: 1, REVIEWING: 2, REVIEWED: 4,
  VERDICT_CONFIRMED: 4, AI_REVEALED: 4, COMPLETED: 4,
};
export const initialState = () => ({ status: 'PRE_JUDGED', lastReviewedStep: 1 });

export function validState(state) {
  if (!state || !Object.hasOwn(defaultSteps, state.status)) return false;
  return state.status === 'REVIEWING'
    ? [2, 3].includes(state.lastReviewedStep)
    : state.lastReviewedStep === defaultSteps[state.status];
}

export function save(state) {
  localStorage.setItem(storageKey, JSON.stringify(state));
  return state;
}

export function read() {
  const raw = localStorage.getItem(storageKey);
  if (raw) {
    try {
      const state = JSON.parse(raw);
      if (validState(state)) return state;
    } catch { /* 손상된 목 저장값은 초기 상태로 복원한다. */ }
  }
  return save(initialState());
}
