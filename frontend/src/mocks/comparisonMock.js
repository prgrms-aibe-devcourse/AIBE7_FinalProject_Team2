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
  const found = rangeOptions.find((item) => item.rangeOptionId === preJudgment.rangeOptionId);
  if (!found) {
    // 코드리뷰 지적: 저장된 구간을 못 찾으면 사용자가 고르지 않은 구간이 "처음 판단"으로 조용히 표시되는 문제가 있었다.
    console.warn(`사전 판단 구간(rangeOptionId: ${preJudgment.rangeOptionId})을 찾지 못해 대체 구간으로 표시합니다.`);
  }
  const option = found ?? rangeOptions[3];
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

// REQ-109 MVP 규칙: 사용자가 고른 요소의 요약 태그를 방향별로 모아 문장을 만든다(ERD `factor.summary_tag`).
// factorLabels(화면 표시용 완전한 문장)를 그대로 쓰면 "~다을" 같은 비문이 되므로 짧은 명사구(factorTags)를 쓴다.
export function buildUserSummary(factors, factorTags) {
  const tag = (factorId) => factorTags[factorId] ?? `요소 ${factorId}`;
  const up = factors.filter((factor) => factor.direction === 'UP').map((factor) => tag(factor.factorId));
  const down = factors.filter((factor) => factor.direction === 'DOWN').map((factor) => tag(factor.factorId));
  if (!up.length && !down.length) return '판단 요소를 고르지 않은 판단';
  if (!down.length) return `${up.join(' · ')}을 무겁게 본 판단`;
  if (!up.length) return `${down.join(' · ')}을 감안한 판단`;
  return `${up.join(' · ')}을 무겁게 보고 ${down.join(' · ')}을 감안한 판단`;
}

// REQ-060 · 061: 셋 중 누구도 고려하지 않은 요소는 뺀다(API 14 명세).
// revealStage: factorId 1 ~ 3(사전 판단 요소)은 OVERVIEW, 나머지는 DETAIL(API 14 응답 모양을 맞춘다).
export function buildMatrix(userFactors, aiFactors, courtFactors, factorLabels, factorRevealStages) {
  const ids = new Set([...userFactors, ...aiFactors, ...courtFactors].map((factor) => factor.factorId));
  const directionOf = (list, factorId) => list.find((factor) => factor.factorId === factorId)?.direction ?? null;
  return [...ids].sort((a, b) => a - b).map((factorId) => {
    const user = directionOf(userFactors, factorId);
    const ai = directionOf(aiFactors, factorId);
    const court = directionOf(courtFactors, factorId);
    const category = user === ai && ai === court ? 'ALL_SAME'
      : user === null && ai !== null && ai === court ? 'ONLY_ME_MISSED'
      : 'DIVERGED';
    return {
      factorId, label: factorLabels[factorId] ?? `요소 ${factorId}`,
      revealStage: factorRevealStages[factorId] ?? 'DETAIL',
      user, ai, court, category,
    };
  });
}

// 매트릭스 분류를 그대로 규칙 문장으로 바꾼다(AI를 부르지 않는다, FR-6-3).
// 문장에는 라벨(완전한 문장)이 아니라 요약 태그(짧은 명사구)를 써서 "~다 점을" 같은 비문을 피한다.
export function buildRuleSentences(matrix, factorTags) {
  const tag = (row) => factorTags[row.factorId] ?? row.label;
  const allSame = matrix.filter((row) => row.category === 'ALL_SAME');
  const upTags = allSame.filter((row) => row.user === 'UP').map(tag);
  const downTags = allSame.filter((row) => row.user === 'DOWN').map(tag);
  const common = [];
  if (upTags.length || downTags.length) {
    const parts = [];
    if (upTags.length) parts.push(`${upTags.join(' · ')}을 형량을 높이는 요소로`);
    if (downTags.length) parts.push(`${downTags.join(' · ')}을 형량을 낮추는 요소로`);
    common.push(`세 판결 모두 ${parts.join(', ')} 보았어요.`);
  } else {
    common.push('세 판결이 똑같이 본 판단 요소는 없어요.');
  }

  const differences = [];
  const onlyMeMissed = matrix.filter((row) => row.category === 'ONLY_ME_MISSED').map(tag);
  if (onlyMeMissed.length) differences.push(`AI와 재판부는 ${onlyMeMissed.join(' · ')}을 고려했지만, 내 판결에서는 고려하지 않았어요.`);
  const diverged = matrix.filter((row) => row.category === 'DIVERGED').map(tag);
  if (diverged.length) differences.push(`${diverged.join(' · ')}에 대한 판단은 세 판결이 서로 달랐어요.`);
  if (!differences.length) differences.push('세 판결 사이에 판단이 엇갈린 점은 없어요.');

  return { common, differences };
}
