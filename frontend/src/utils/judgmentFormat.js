// S-07 · S-08 공통 — 판결(사용자 · AI · 실제) 표시 형식을 맞추는 순수 함수 모음.
import { formatMonths } from '../components/recommendedRangeBar.js';

export const penaltyLabels = { DEATH: '사형', LIFE: '무기징역', PRISON: '징역', FINE: '벌금' };
const dispositionLabels = { COMMUNITY_SERVICE: '사회봉사', CONFISCATION: '몰수' };

export function formatMoney(amount) {
  const units = [[100000000, '억'], [10000, '만']];
  const parts = [];
  let rest = amount;
  for (const [unit, label] of units) {
    const count = Math.floor(rest / unit);
    if (count) parts.push(`${count.toLocaleString('ko-KR')}${label}`);
    rest %= unit;
  }
  if (rest || !parts.length) parts.push(rest.toLocaleString('ko-KR'));
  return `${parts.join(' ')} 원`;
}

// 최종 선고 형벌은 reducedTo가 있으면 그 값이다(v0.4, API 명세).
function finalPenaltyType(judgment) {
  return judgment.reducedTo ?? judgment.penaltyType;
}

export function formatPenalty(judgment) {
  const type = finalPenaltyType(judgment);
  if (type === 'PRISON') return `징역 ${formatMonths(judgment.prisonMonths)}`;
  if (type === 'FINE') return `벌금 ${formatMoney(judgment.fineAmount)}`;
  return penaltyLabels[type] ?? type;
}

// 집행유예 · 부가 처분을 한 줄로 합친다. 둘 다 없으면 빈 문자열.
export function formatDispositions(judgment) {
  const parts = [];
  if (judgment.suspensionMonths) parts.push(`집행유예 ${formatMonths(judgment.suspensionMonths)}`);
  for (const item of judgment.extraDispositions ?? []) {
    const label = dispositionLabels[item.type] ?? item.type;
    parts.push(item.value ? `${label} ${item.value}` : label);
  }
  return parts.join(' · ');
}

// API 10 diffFromMine → "내 판결보다 짧음/김" 문구 (화면 문구 규칙은 미정, 요구사항 15장).
export function diffPhrase(diff) {
  if (!diff.samePenaltyType) return '형벌 종류가 달라 단순 비교하기 어려워요.';
  const value = diff.prisonMonthsDiff ?? diff.fineAmountDiff;
  if (!value) return '내 판결과 같아요.';
  const amountText = diff.prisonMonthsDiff != null ? formatMonths(Math.abs(value)) : formatMoney(Math.abs(value));
  return `내 판결보다 ${amountText} ${value < 0 ? '짧아요' : '길어요'}.`;
}
