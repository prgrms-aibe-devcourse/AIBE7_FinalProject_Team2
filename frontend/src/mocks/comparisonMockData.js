// API 14 응답을 만드는 데 쓰는 고정 목 데이터. AI · 실제 판결은 S-07 · S-08과 같은 사건을 가정한다.
export const mockComparisonBase = {
  ai: {
    subjectType: 'AI', penaltyType: 'PRISON', reducedTo: null, prisonMonths: 144, fineAmount: null, suspensionMonths: null,
    summary: '다투다 벌어진 범행과 공탁 · 반성을 함께 저울질한 판단',
    factors: [
      { factorId: 2, direction: 'UP' },
      { factorId: 3, direction: 'UP' },
      { factorId: 9, direction: 'DOWN' },
      { factorId: 7, direction: 'DOWN' },
      { factorId: 8, direction: 'DOWN' },
    ],
  },
  court: {
    subjectType: 'COURT', penaltyType: 'PRISON', reducedTo: null, prisonMonths: 120, fineAmount: null, suspensionMonths: null,
    extraDispositions: [{ type: 'CONFISCATION', value: '범행에 사용한 흉기' }],
    summary: '유족의 처벌 의사를 무겁게 보면서도 공탁과 반성을 감안한 판단',
    factors: [
      { factorId: 5, direction: 'UP' },
      { factorId: 3, direction: 'UP' },
      { factorId: 9, direction: 'DOWN' },
      { factorId: 7, direction: 'DOWN' },
      { factorId: 8, direction: 'DOWN' },
    ],
  },
  // 매트릭스 라벨 출처. verdictMockData.js의 판단 요소와 같은 사건이다.
  factorLabels: {
    1: '빌린 돈을 갚지 못해 오래 다툼이 있었다',
    2: '다투던 중 집에 있던 흉기를 집어 들었다',
    3: '범행 뒤 구호 조치 없이 현장을 떠났다',
    4: '사건 3개월 전부터 변제 문제로 여러 차례 다퉜다',
    5: '유족이 엄벌을 원한다',
    6: '피해자에게는 부양하던 어린 자녀 2명이 있다',
    7: '수사 초기부터 범행을 인정하고 반성하고 있다',
    8: '형사처벌 전력이 없다',
    9: '피해 회복을 위해 5,000만 원을 공탁했다',
    10: '피고인은 우발적 범행이라고 주장한다',
    11: '피고인은 오랜 채무로 정신적으로 지쳐 있었다고 주장한다',
  },
};

// S-06을 거치지 않고 "상태 강제 선택"으로 들어온 경우를 대비한 대체 사용자 판단(myJudgmentMock.js).
export const fallbackUserFactors = [
  { factorId: 2, direction: 'UP' },
  { factorId: 3, direction: 'UP' },
  { factorId: 5, direction: 'UP' },
  { factorId: 7, direction: 'DOWN' },
];

// S-03을 거치지 않은 경우의 대체 사전 판단.
export const fallbackPreJudgment = { rangeOptionId: 4, factorIds: [] };

// rangeOptionId(overviewMockData.js, 가벼움 → 무거움 순) → 구간 가중치.
// 형벌 종류 무게는 API 14 명세와 같다: FINE < SUSPENDED < PRISON < LIFE < DEATH.
export const rangeBuckets = {
  1: { tier: 1 }, // 징역형 집행유예
  2: { tier: 2, min: 0, max: 36 }, // 실형 3년 미만
  3: { tier: 2, min: 36, max: 60 }, // 실형 3 ~ 5년
  4: { tier: 2, min: 60, max: 120 }, // 실형 5 ~ 10년
  5: { tier: 2, min: 120, max: 240 }, // 실형 10 ~ 20년
  6: { tier: 2, min: 240, max: Infinity }, // 실형 20년 이상
  7: { tier: 3 }, // 무기징역
  8: { tier: 4 }, // 사형
};
