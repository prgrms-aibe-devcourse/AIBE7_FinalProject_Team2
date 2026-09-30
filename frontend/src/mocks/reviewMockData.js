// TODO: API 6에 사건 제목 없음, 정합성 2차 점검 D-01 결정 후 실제 제목 공급원으로 교체.
export const mockCaseHeader = { caseId: 1, title: '빌린 돈 문제로 찾아온 지인을 살해한 사건', chargeName: '살인' };

export const mockSections = {
  1: { step: 1, stage: 'OVERVIEW', items: [
    { sectionType: 'OVERVIEW', title: '사건 개요',
      content: '피고인이 빌린 돈을 갚지 못해 오래 다투던 지인이 집으로 찾아오자, 말다툼 끝에 집에 있던 흉기로 피해자를 살해하고 구호 조치 없이 집을 나간 사건이다.' } ] },
  2: { step: 2, stage: 'DETAIL', items: [
    { sectionType: 'FACTS', title: '주요 사실관계',
      content: '피고인은 지인인 피해자에게 빌린 돈을 제때 갚지 못해, 사건 3개월 전부터 변제 문제로 여러 차례 피해자와 다퉜다. 사건 당일 피해자가 돈 문제를 이야기하려고 피고인의 집으로 찾아왔고, 두 사람은 말다툼을 벌였다. 다툼이 격해지자 피고인은 집에 있던 흉기를 집어 들어 피해자를 찔렀고, 피해자는 그 자리에서 숨졌다. 피고인은 구호 조치를 하지 않고 집을 나갔다가 같은 날 경찰에 체포됐다. 피해자에게는 부양하던 어린 자녀 2명이 있다.' },
    { sectionType: 'DAMAGE', title: '피해 결과', data: [
      { label: '피해자 수', value: '1명' }, { label: '피해 결과', value: '사망' },
      { label: '피해자와의 관계', value: '지인 (돈을 빌린 사이)' }, { label: '범행 도구', value: '집에 있던 흉기' } ] },
    { sectionType: 'DEFENDANT', title: '피고인 관련 사실', content: '30대이고 형사처벌을 받은 전력이 없다. 수사 초기부터 범행을 인정하고 반성하고 있다.' },
    { sectionType: 'SETTLEMENT', title: '합의 · 피해 회복', content: '피해 회복을 위해 5,000만 원을 공탁했으나 합의에 이르지 못했고, 유족은 엄벌을 원한다.' } ] },
  3: { step: 3, stage: 'ARGUMENT', items: [
    { sectionType: 'PROSECUTOR', title: '검사',
      content: '피고인은 돈 문제로 다투던 피해자를 흉기로 살해하고 구호 조치 없이 현장을 떠났다. 피해자에게는 부양하던 어린 자녀 2명이 있고, 유족은 엄벌을 원한다.' },
    { sectionType: 'DEFENSE', title: '피고인 · 변호인',
      content: '미리 계획한 범행이 아니라 말다툼 중 우발적으로 벌어진 일이다. 피고인은 오랜 채무로 정신적으로 지쳐 있었다. 수사 초기부터 범행을 인정하고 반성하고 있으며, 피해 회복을 위해 5,000만 원을 공탁했다. 형사처벌 전력이 없다.' } ] },
  4: { step: 4, stage: 'LAW', items: [] },
};

export const mockLaw = {
  appliedLaw: '형법 제250조 제1항 살인',
  statutoryPenaltyText: '사형, 무기 또는 5년 이상의 징역',
  allowedRanges: [
    { penaltyType: 'DEATH', allowedMin: 240, allowedMax: 600, text: '사형 (감경하면 무기징역 또는 징역 20년 ~ 50년)' },
    { penaltyType: 'LIFE', allowedMin: 120, allowedMax: 600, text: '무기징역 (감경하면 징역 10년 ~ 50년)' },
    { penaltyType: 'PRISON', allowedMin: 30, allowedMax: 360, text: '징역 2년 6개월 ~ 30년' } ],
  allowedRangeNote: '감경·가중 사유를 반영해 법률상 선고할 수 있는 가장 넓은 범위예요.',
  recommended: { minMonths: 84, maxMonths: 144,
    basis: "살인범죄 제2유형(보통 동기 살인). 특별감경인자 1개(실질적 피해 회복 — 5,000만 원 공탁)가 있고 특별가중인자는 없어 감경영역을 적용한다. 계획 없이 다투던 중 벌어진 범행이라 특별가중인자인 '계획적 살인 범행'에 해당하지 않는다." },
  terms: [
    { term: '보통 동기 살인', desc: '살인범죄 양형기준의 제2유형. 원한이나 다툼 등 흔히 볼 수 있는 동기로 저지른 살인' },
    { term: '감경영역', desc: '형을 가볍게 하는 특별한 사정이 있어 기본보다 낮은 권고 형량이 적용되는 구간' },
    { term: '작량감경', desc: '참작할 사정이 있을 때 판사가 형을 줄이는 것. 사형 · 무기징역도 징역으로 줄일 수 있다' },
    { term: '공탁', desc: '피해자가 받지 않는 배상금을 법원에 맡겨 두는 것' } ],
};

export const mockSummary = [
  '집으로 찾아온 지인 1명을 살해',
  '다투던 중 집에 있던 흉기를 사용, 구호 조치 없이 집을 나감',
  '5,000만 원 공탁, 합의는 안 됨 · 유족은 엄벌을 원함',
  '형사처벌 전력 없음, 수사 초기부터 범행 인정',
];
