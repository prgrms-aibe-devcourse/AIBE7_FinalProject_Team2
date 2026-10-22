// 프로필카드 아래에 보일 성향(FE-77): 초심 · 재심 · 최근 중 하나. 기본은 최근. 카드 꾸미기 창의 글자 라디오에서 고른다.
// 공개 성향카드(shownCard.js)와는 별개의 값이다. 서버 API 전이라 이 브라우저에만 저장한다(BE-104에서 서버로 옮긴다)
import { localStore } from './safeStorage.js';

const STORAGE_KEY = 'nlnb-profile-card-type';
const SLOT_NAMES = { initial: '초심', retake: '재심', current: '최근' };
const SLOT_ORDER = ['initial', 'retake', 'current'];
let unsaved; // 저장소를 못 쓸 때 이번 방문 동안의 값

/** 마이페이지 응답(P-5)에서 칸별 유형. 없는 칸은 null */
export function typeSlots(data) {
  const initial = data.initialPersonality === undefined ? data.personality : data.initialPersonality;
  return {
    initial: initial?.type ?? null,
    retake: data.baselineRetake?.result?.type ?? null,
    current: data.judgmentPersonality?.type ?? data.personality?.type ?? null,
  };
}

/**
 * 고를 수 있는 성향. 같은 유형(shareId)이 겹치면 한 칸으로 묶는다(칸이 3 → 2 → 1개).
 * 반환: [{ keys: ['current', 'initial'], type, label: '초심 · 최근: ○○형' }] (초심 → 재심 → 최근 순)
 */
export function typeChoices(data) {
  const slots = typeSlots(data);
  const groups = [];
  SLOT_ORDER.forEach((key) => {
    const type = slots[key];
    if (!type) return;
    const same = groups.find((group) => group.type.shareId === type.shareId);
    if (same) same.keys.push(key);
    else groups.push({ keys: [key], type });
  });
  return groups.map((group) => ({ ...group, label: `${group.keys.map((key) => SLOT_NAMES[key]).join(' · ')}: ${group.type.name}` }));
}

export function getProfileCardType(storage = localStore()) {
  if (unsaved) return unsaved;
  try {
    const value = storage?.getItem(STORAGE_KEY);
    return SLOT_ORDER.includes(value) ? value : 'current';
  } catch {
    return 'current';
  }
}

export function setProfileCardType(key, storage = localStore()) {
  try {
    storage.setItem(STORAGE_KEY, key);
    unsaved = undefined;
  } catch {
    unsaved = key;
  }
}

/** 지금 프로필카드 아래에 보일 선택(choices 중 하나). 고른 칸이 비면 최근 → 첫 칸 순으로 대신하고, 성향이 하나도 없으면 null */
export function selectedChoice(data, chosen = getProfileCardType()) {
  const choices = typeChoices(data);
  return choices.find((choice) => choice.keys.includes(chosen)) ?? choices.find((choice) => choice.keys.includes('current')) ?? choices[0] ?? null;
}
