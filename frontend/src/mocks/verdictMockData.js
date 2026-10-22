export const mockVerdictForm = {
  penaltyOptions: [
    { penaltyType: 'DEATH', allowedMin: 240, allowedMax: 600, text: '사형 (감경하면 무기징역 또는 징역 20년 ~ 50년)', suspensionAllowed: false, reducibleTo: ['LIFE', 'PRISON'] },
    { penaltyType: 'LIFE', allowedMin: 120, allowedMax: 600, text: '무기징역 (감경하면 징역 10년 ~ 50년)', suspensionAllowed: false, reducibleTo: ['PRISON'] },
    { penaltyType: 'PRISON', allowedMin: 30, allowedMax: 360, text: '징역 2년 6개월 ~ 30년', suspensionAllowed: true, reducibleTo: [] },
  ],
  statutoryPenaltyText: '사형, 무기 또는 5년 이상의 징역',
  allowedRangeNote: '감경·가중 사유를 반영해 법률상 선고할 수 있는 가장 넓은 범위예요.',
  recommended: { minMonths: 84, maxMonths: 144, basis: "살인범죄 제2유형(보통 동기 살인). 특별감경인자 1개(실질적 피해 회복 — 5,000만 원 공탁)가 있고 특별가중인자는 없어 감경영역을 적용한다. 계획 없이 다투던 중 벌어진 범행이라 특별가중인자인 '계획적 살인 범행'에 해당하지 않는다." },
  suspensionRule: { maxPrisonMonths: 36, maxFineAmount: 5000000, minMonths: 12, maxMonths: 60 },
  factors: [
    { factorId: 1, label: '빌린 돈을 갚지 못해 오래 다툼이 있었다' },
    { factorId: 2, label: '다투던 중 집에 있던 흉기를 집어 들었다' },
    { factorId: 3, label: '범행 뒤 구호 조치 없이 현장을 떠났다' },
    { factorId: 4, label: '사건 3개월 전부터 변제 문제로 여러 차례 다퉜다' },
    { factorId: 5, label: '유족이 엄벌을 원한다' },
    { factorId: 6, label: '피해자에게는 부양하던 어린 자녀 2명이 있다' },
    { factorId: 7, label: '수사 초기부터 범행을 인정하고 반성하고 있다' },
    { factorId: 8, label: '형사처벌 전력이 없다' },
    { factorId: 9, label: '피해 회복을 위해 5,000만 원을 공탁했다' },
    { factorId: 10, label: '피고인은 우발적 범행이라고 주장한다' },
    { factorId: 11, label: '피고인은 오랜 채무로 정신적으로 지쳐 있었다고 주장한다' },
  ],
};
