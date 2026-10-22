// 목 전용 저장소. 실제 체험 진행 상태는 서버에서 관리한다.
import { localStore } from '../utils/safeStorage.js';

// 저장소 접근이 막힌 브라우저(쿠키 차단 · 사생활 보호 모드)에서는 이번 방문 동안 메모리에 둔다(session.js · communityMockApi.js와 같은 방식).
// 저장이 실패한 값은 메모리가 우선이라, 저장 직후 peek() · read()가 같은 상태를 읽는다
const memory = new Map();

export const getItem = (key) => {
  if (memory.has(key)) return memory.get(key);
  try { return localStore()?.getItem(key) ?? null; } catch { return null; }
};
const setItem = (key, value) => {
  try {
    const store = localStore();
    if (!store) throw new Error('no storage'); // 저장소가 없으면 메모리로 보낸다
    store.setItem(key, value);
    memory.delete(key);
  } catch {
    memory.set(key, value);
  }
};
const removeItem = (key) => {
  memory.delete(key);
  try { localStore()?.removeItem(key); } catch { /* 저장소를 못 쓰면 메모리만 지운다 */ }
};

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
  setItem(storageKey, JSON.stringify(state));
  return state;
}

// 저장하지 않고 읽기만 한다. 기록이 없으면 null (read()는 없으면 초기 상태를 저장해 다른 화면 상태를 바꾼다)
export function peek() {
  const raw = getItem(storageKey);
  if (!raw) return null;
  try {
    const state = JSON.parse(raw);
    return validState(state) ? state : null;
  } catch {
    return null;
  }
}

export function read() {
  const raw = getItem(storageKey);
  if (raw) {
    try {
      const state = JSON.parse(raw);
      if (validState(state)) return state;
    } catch { /* 손상된 목 저장값은 초기 상태로 복원한다. */ }
  }
  return save(initialState());
}

function jsonStore(key) {
  return {
    save: (value) => {
      setItem(key, JSON.stringify(value));
      return value;
    },
    read: () => {
      const raw = getItem(key);
      if (!raw) return null;
      try {
        return JSON.parse(raw);
      } catch {
        return null;
      }
    },
    clear: () => removeItem(key),
  };
}

// S-05에서 확정 제출한 판결 · S-03에서 고른 사전 판단(목 전용, S-08에서 사용).
// 상태(storageKey)와 별도 키로 둬서 다른 save() 호출이 이 값들을 덮어쓰지 않게 한다.
const judgmentStore = jsonStore('nlnb-mock-judgment');
export const saveJudgment = judgmentStore.save;
export const readJudgment = judgmentStore.read;
export const clearJudgment = judgmentStore.clear;

const preJudgmentStore = jsonStore('nlnb-mock-pre-judgment');
export const savePreJudgment = preJudgmentStore.save;
export const readPreJudgment = preJudgmentStore.read;
export const clearPreJudgment = preJudgmentStore.clear;

// 무대의 "처음부터 다시"(목 전용): 사건 시작 전 상태로 되돌리고 저장한 판단을 지운다
export function restart() {
  clearJudgment();
  clearPreJudgment();
  return save({ status: 'STARTED', lastReviewedStep: 0 });
}

// 막 버튼으로 바로 가기(목 전용, 테스트 기간): 그 막의 화면을 볼 수 있는 상태로 맞춘다
const ACT_STATUS = { '1a': 'STARTED', '1b': 'STARTED', 2: 'PRE_JUDGED', 3: 'REVIEWED', 4: 'VERDICT_CONFIRMED', 5: 'AI_REVEALED', 6: 'COMPLETED' }; // 키는 대본 키(ACT_GROUPS)
export function jumpToAct(act) {
  const status = ACT_STATUS[act];
  if (status === 'STARTED') return restart();
  return save({ status, lastReviewedStep: defaultSteps[status] });
}
