// API 14(세 판결 비교) 응답을 조립하는 순수 함수 모음. comparisonMockApi.js에서만 쓴다.
import { formatPenalty, formatDispositions } from '../utils/judgmentFormat.js';
import { rangeBuckets } from './comparisonMockData.js';

const directionTexts = {
  HEAVIER: '사건을 모두 확인한 뒤, 처음 생각보다 무거운 판결을 내렸어요.',
  SAME: '사건을 모두 확인한 뒤, 처음 생각과 비슷한 판결을 내렸어요.',
  LIGHTER: '사건을 모두 확인한 뒤, 처음 생각보다 가벼운 판결을 내렸어요.',
};

function finalWeight(judgment) {
  const type = judgment.reducedTo ?? judgment.penaltyType;
  if (type === 'DEATH') return { tier: 4, months: 0 };
  if (type === 'LIFE') return { tier: 3, months: 0 };
  if (type === 'FINE') return { tier: 0, months: 0 };
  // PRISON: 집행유예가 있으면 SUSPENDED(1), 없으면 실형(2).
  return { tier: judgment.suspensionMonths ? 1 : 2, months: judgment.prisonMonths ?? 0 };
}

// preToFinal.direction: 구간(rangeOptionId)과 최종 판결을 형벌 무게 순으로 비교한다(API 14 명세).
function directionFromBucket(weight, bucket) {
  if (weight.tier !== bucket.tier) return weight.tier > bucket.tier ? 'HEAVIER' : 'LIGHTER';
  if (bucket.min === undefined) return 'SAME';
  if (weight.months < bucket.min) return 'LIGHTER';
  if (weight.months >= bucket.max) return 'HEAVIER';
  return 'SAME';
}

export function buildPreToFinal(myJudgment, preJudgment, rangeOptions) {
  const option = rangeOptions.find((item) => item.rangeOptionId === preJudgment.rangeOptionId) ?? rangeOptions[3];
  const bucket = rangeBuckets[option.rangeOptionId] ?? rangeBuckets[4];
  const direction = directionFromBucket(finalWeight(myJudgment), bucket);
  const finalJudgmentText = [formatPenalty(myJudgment), formatDispositions(myJudgment)].filter(Boolean).join(' · ');
  return {
    preJudgment: { rangeOptionId: option.rangeOptionId, label: option.label, factorIds: preJudgment.factorIds ?? [] },
    finalJudgmentText,
    direction,
    summaryText: directionTexts[direction],
  };
}

// REQ-109 MVP 규칙: 사용자가 고른 요소의 라벨을 방향별로 모아 문장을 만든다(요약 태그 대신 라벨을 그대로 쓴다).
export function buildUserSummary(factors, factorLabels) {
  const label = (factorId) => factorLabels[factorId] ?? `요소 ${factorId}`;
  const up = factors.filter((factor) => factor.direction === 'UP').map((factor) => label(factor.factorId));
  const down = factors.filter((factor) => factor.direction === 'DOWN').map((factor) => label(factor.factorId));
  if (!up.length && !down.length) return '판단 요소를 고르지 않은 판단';
  if (!down.length) return `${up.join(' · ')}을 무겁게 본 판단`;
  if (!up.length) return `${down.join(' · ')}을 감안한 판단`;
  return `${up.join(' · ')}을 무겁게 보고 ${down.join(' · ')}을 감안한 판단`;
}

// REQ-060 · 061: 셋 중 누구도 고려하지 않은 요소는 뺀다(API 14 명세).
export function buildMatrix(userFactors, aiFactors, courtFactors, factorLabels) {
  const ids = new Set([...userFactors, ...aiFactors, ...courtFactors].map((factor) => factor.factorId));
  const directionOf = (list, factorId) => list.find((factor) => factor.factorId === factorId)?.direction ?? null;
  return [...ids].sort((a, b) => a - b).map((factorId) => {
    const user = directionOf(userFactors, factorId);
    const ai = directionOf(aiFactors, factorId);
    const court = directionOf(courtFactors, factorId);
    const category = user === ai && ai === court ? 'ALL_SAME'
      : user === null && ai !== null && ai === court ? 'ONLY_ME_MISSED'
      : 'DIVERGED';
    return { factorId, label: factorLabels[factorId] ?? `요소 ${factorId}`, user, ai, court, category };
  });
}

// 매트릭스 분류를 그대로 규칙 문장으로 바꾼다(AI를 부르지 않는다, FR-6-3).
export function buildRuleSentences(matrix) {
  const allSame = matrix.filter((row) => row.category === 'ALL_SAME');
  const upLabels = allSame.filter((row) => row.user === 'UP').map((row) => row.label);
  const downLabels = allSame.filter((row) => row.user === 'DOWN').map((row) => row.label);
  const common = [];
  if (upLabels.length || downLabels.length) {
    const parts = [];
    if (upLabels.length) parts.push(`${upLabels.join(' · ')} 점을 형량을 높이는 요소로`);
    if (downLabels.length) parts.push(`${downLabels.join(' · ')} 점을 형량을 낮추는 요소로`);
    common.push(`세 판결 모두 ${parts.join(', ')} 보았어요.`);
  } else {
    common.push('세 판결이 똑같이 본 판단 요소는 없어요.');
  }

  const differences = [];
  const onlyMeMissed = matrix.filter((row) => row.category === 'ONLY_ME_MISSED').map((row) => row.label);
  if (onlyMeMissed.length) differences.push(`AI와 재판부는 ${onlyMeMissed.join(' · ')} 점을 고려했지만, 내 판결에서는 고려하지 않았어요.`);
  const diverged = matrix.filter((row) => row.category === 'DIVERGED').map((row) => row.label);
  if (diverged.length) differences.push(`${diverged.join(' · ')} 점은 세 판결의 판단이 서로 달랐어요.`);
  if (!differences.length) differences.push('세 판결 사이에 판단이 엇갈린 점은 없어요.');

  return { common, differences };
}
