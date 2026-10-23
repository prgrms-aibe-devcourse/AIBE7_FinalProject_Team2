// 공개 카드(FE-77): 성향카드 화면의 초심 · 재심 · 최근 중 커뮤니티에서 다른 사람에게 보여 줄 카드 하나("공개" 도장이 찍힌다).
// 값은 서버(P-8 shownCard)가 원본이고 profileSettings.js의 사본을 읽고 쓴다(FE-81). 고른 칸에 카드가 없으면(재심 전 등) 화면이 최근 → 초심 → 재심 순으로 대신 고른다
import { SLOTS, getProfileSettings, updateProfileSettings } from './profileSettings.js';

export const SHOWN_SLOTS = SLOTS;

export const getShownCard = () => getProfileSettings().shownCard;

/** 저장되면 true, 거절되면 false(이전 값으로 돌아간다) */
export const setShownCard = (slot) => updateProfileSettings({ shownCard: slot });

/** 실제로 도장을 찍을 칸. slots = { initial, retake, current } 각 칸에 카드(유형)가 있는지 */
export function shownSlot(slots, chosen = getShownCard()) {
  if (slots[chosen]) return chosen;
  return ['current', 'initial', 'retake'].find((slot) => slots[slot]) ?? null;
}
