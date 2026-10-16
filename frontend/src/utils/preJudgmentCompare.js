// 사전 판단(구간 이름)과 실제 판결을 형벌 무게로 비교한다 — 8막 독자 의견란의 "편집부 의견" 한 줄에 쓴다.
// API 14는 사전 판단을 "내 최종 판결"과만 비교해 주므로(preToFinal.direction), 실제 판결과의 비교는 화면에서 구간 이름을 읽어 한다.
// 구간 이름 형식: "징역형 집행유예" · "실형 3년 미만" · "실형 3년 이상 ~ 5년 미만" · "실형 20년 이상" · "무기징역" · "사형"(sentence_range_option.label)
// 읽지 못하는 이름이면 null을 돌려주고 의견 줄은 숨긴다.

// 형벌 무게: 집행유예 1 < 실형 2(개월 범위) < 무기 3 < 사형 4 (벌금 0)
function preWeight(label) {
  if (label.includes('사형')) return { tier: 4 };
  if (label.includes('무기')) return { tier: 3 };
  if (label.includes('집행유예')) return { tier: 1 };
  if (label.includes('벌금')) return { tier: 0 }; // 법원 쪽 FINE(tier 0)과 같은 층
  if (!label.includes('실형')) return null;
  const min = label.match(/(\d+)년 이상/);
  const max = label.match(/(\d+)년 미만/);
  if (!min && !max) return null;
  return { tier: 2, min: min ? Number(min[1]) * 12 : 0, max: max ? Number(max[1]) * 12 : Infinity };
}

function courtWeight(judgment) {
  const type = judgment.reducedTo ?? judgment.penaltyType;
  if (type === 'DEATH') return { tier: 4 };
  if (type === 'LIFE') return { tier: 3 };
  if (type === 'FINE') return { tier: 0 };
  return { tier: judgment.suspensionMonths ? 1 : 2, months: judgment.prisonMonths ?? 0 };
}

/** @returns {'SAME' | 'PRE_HEAVIER' | 'PRE_LIGHTER' | null} 사전 판단이 실제 판결보다 어느 쪽인지 */
export function comparePreToCourt(preLabel, courtJudgment) {
  const pre = preWeight(preLabel ?? '');
  if (!pre || !courtJudgment) return null;
  const court = courtWeight(courtJudgment);
  if (pre.tier !== court.tier) return pre.tier > court.tier ? 'PRE_HEAVIER' : 'PRE_LIGHTER';
  if (pre.tier !== 2) return 'SAME';
  if (court.months < pre.min) return 'PRE_HEAVIER';
  if (court.months >= pre.max) return 'PRE_LIGHTER';
  return 'SAME';
}

// 편집부 의견(짧게). 어느 쪽이 맞는지 말하지 않고 사실만 적는다(PRODUCT.md 원칙 1)
const EDITOR_NOTES = {
  SAME: '실제 사건과 비슷하게 판단했어요.',
  PRE_HEAVIER: '실제 사건보다 무겁게 판단했어요.',
  PRE_LIGHTER: '실제 사건보다 가볍게 판단했어요.',
};
export const editorNote = (preLabel, courtJudgment) => EDITOR_NOTES[comparePreToCourt(preLabel, courtJudgment)] ?? null;
