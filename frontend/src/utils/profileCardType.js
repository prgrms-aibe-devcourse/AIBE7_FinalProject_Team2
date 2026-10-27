// 프로필카드 아래에 보일 성향(FE-77): 초심 · 재심 · 최근 중 하나. 기본은 최근. 카드 꾸미기 창의 글자 라디오에서 고른다.
// 공개 성향카드(shownCard.js)와는 별개의 값이다. 값은 서버(P-8 profileCardType)가 원본이고 profileSettings.js의 사본을 읽고 쓴다(FE-81)
import { getCardLineSettings, getProfileSettings, updateCardLine, updateProfileSettings } from './profileSettings.js';

const SLOT_NAMES = { initial: '초심', retake: '재심', current: '최근' };
const SLOT_ORDER = ['initial', 'retake', 'current'];

/** 마이페이지 응답(P-5)에서 칸별 유형. 없는 칸은 null */
export function typeSlots(data) {
  const initial = data.initialPersonality === undefined ? data.personality : data.initialPersonality;
  const tested = initial?.source !== 'JUDGMENT'; // 첫 테스트 전(첫 판결이 대신한 초심)에는 성향카드 화면에 초심 카드가 없으니 고를 수 없다
  return {
    initial: tested ? (initial?.type ?? null) : null,
    retake: data.baselineRetake?.result?.type ?? null,
    current: data.judgmentPersonality?.type ?? data.personality?.type ?? null,
  };
}

/**
 * 고를 수 있는 성향. 같은 유형(shareId)이 겹치면 한 칸으로 묶는다(칸이 3 → 2 → 1개).
 * 반환: [{ keys: ['initial', 'current'], pick: 'current', type, label: '초심 · 최근: ○○형' }] (초심 → 재심 → 최근 순)
 * pick: 이 칸을 골랐을 때 저장할 값. 묶인 칸이면 최근을 우선한다(최근을 기본으로 쓰던 사용자가 초심에 묶이지 않게)
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
  return groups.map((group) => ({ ...group, pick: group.keys.includes('current') ? 'current' : group.keys[0], label: `${group.keys.map((key) => SLOT_NAMES[key]).join(' · ')}: ${group.type.name}` }));
}

export const getProfileCardType = () => getProfileSettings().profileCardType;

/** 저장되면 true, 거절되면 false(이전 값으로 돌아간다) */
export const setProfileCardType = (key) => updateProfileSettings({ profileCardType: key });

/** 지금 프로필카드 아래에 보일 선택(choices 중 하나). 고른 칸이 비면 최근 → 첫 칸 순으로 대신하고, 성향이 하나도 없으면 null */
export function selectedChoice(data, chosen = getProfileCardType()) {
  const choices = typeChoices(data);
  return choices.find((choice) => choice.keys.includes(chosen)) ?? choices.find((choice) => choice.keys.includes('current')) ?? choices[0] ?? null;
}

export const getCardLine = () => getCardLineSettings().cardLine;
export const getCardLevel = () => getCardLineSettings().cardLevel;
/** 카드 아래 줄: 성향 · 서명 · 직업 중 하나, 레벨은 앞에 붙이거나 끈다. 서버에 보내지 않는다(profileSettings.js) */
export const setCardLine = (cardLine) => updateCardLine({ cardLine });
export const setCardLevel = (cardLevel) => updateCardLine({ cardLevel });
