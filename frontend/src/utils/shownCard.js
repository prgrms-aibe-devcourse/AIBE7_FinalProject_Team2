// 공개 카드(FE-77): 성향카드 화면의 처음 · 다시 정함 · 지금 중 커뮤니티에서 다른 사람에게 보여 줄 카드 하나("공개" 도장이 찍힌다).
// 서버 API 전이라 이 브라우저에만 저장한다. 고른 칸에 카드가 없으면(다시 정하기 전 등) 화면이 지금 → 처음 순으로 대신 고른다
import { localStore } from './safeStorage.js';

const STORAGE_KEY = 'nlnb-shown-card';
export const SHOWN_SLOTS = ['initial', 'retake', 'current'];
let unsaved; // 저장소를 못 쓸 때 이번 방문 동안의 값

export function getShownCard(storage = localStore()) {
  if (unsaved) return unsaved;
  try {
    const value = storage?.getItem(STORAGE_KEY);
    return SHOWN_SLOTS.includes(value) ? value : 'current';
  } catch {
    return 'current';
  }
}

export function setShownCard(slot, storage = localStore()) {
  try {
    storage.setItem(STORAGE_KEY, slot);
    unsaved = undefined;
  } catch {
    unsaved = slot;
  }
}

/** 실제로 도장을 찍을 칸. slots = { initial, retake, current } 각 칸에 카드(유형)가 있는지 */
export function shownSlot(slots, chosen = getShownCard()) {
  if (slots[chosen]) return chosen;
  return ['current', 'initial', 'retake'].find((slot) => slots[slot]) ?? null;
}
