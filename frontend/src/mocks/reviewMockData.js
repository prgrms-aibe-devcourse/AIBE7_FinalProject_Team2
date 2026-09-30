export const mockCaseHeader = { caseId: 1, title: '지인 투자금 편취 사건', chargeName: '사기' };

export const mockSections = {
  1: { step: 1, stage: 'OVERVIEW', items: [
    { sectionType: 'OVERVIEW', title: '사건 개요',
      content: '피고인이 10년 가까이 알고 지낸 지인에게 사업 투자 수익을 약속하고 4,500만 원을 한 번에 송금받은 뒤, 약속한 수익과 원금을 돌려주지 않은 사건이다.' } ] },
  2: { step: 2, stage: 'DETAIL', items: [
    { sectionType: 'FACTS', title: '주요 사실관계',
      content: '2023년 5월, 피고인은 운영하던 사업이 어려운 상태에서 10년 가까이 알고 지낸 피해자에게 "사업에 투자하면 매달 수익금을 주고 1년 뒤 원금을 돌려주겠다"고 말해 4,500만 원을 한 번에 송금받았다. 피고인은 처음부터 투자할 생각이 없었고, 받은 돈을 생활비와 빚을 갚는 데 썼다.' },
    { sectionType: 'DAMAGE', title: '피해 결과', data: [
      { label: '피해자 수', value: '1명' }, { label: '피해 금액', value: '4,500만 원' },
      { label: '범행 기간', value: '1회 (2023년 5월)' }, { label: '회복 금액', value: '1,500만 원' } ] },
    { sectionType: 'DEFENDANT', title: '피고인 관련 사실', content: '형사처벌을 받은 전력이 없다. 수사 단계부터 범행을 인정했다.' },
    { sectionType: 'SETTLEMENT', title: '합의 · 피해 회복', content: '재판 중 1,500만 원을 갚았으나 합의에는 이르지 못했고, 피해자는 처벌을 원한다.' } ] },
  3: { step: 3, stage: 'ARGUMENT', items: [
    { sectionType: 'PROSECUTOR', title: '검사',
      content: '피고인은 오랜 지인의 신뢰를 이용해 처음부터 갚을 생각 없이 4,500만 원을 받아 개인 용도로 썼다. 피해 금액이 크고, 피해자가 처벌을 원한다.' },
    { sectionType: 'DEFENSE', title: '피고인 · 변호인',
      content: '피고인은 수사 단계부터 범행을 인정하고 반성하고 있다. 재판 중 1,500만 원을 갚았고 나머지도 갚겠다고 약속했다. 형사처벌 전력이 없다.' } ] },
  4: { step: 4, stage: 'LAW', items: [] },   // ④의 내용은 아래 law로 그린다
};

export const mockLaw = {
  appliedLaw: '형법 제347조 사기',
  statutoryPenaltyText: '10년 이하 징역 또는 2천만 원 이하 벌금',
  allowedRanges: [
    { penaltyType: 'PRISON', allowedMin: 1, allowedMax: 120, text: '징역 1개월 ~ 10년' },
    { penaltyType: 'FINE', allowedMin: 25000, allowedMax: 20000000, text: '벌금 2만 5천 원 ~ 2천만 원' } ],
  allowedRangeNote: '감경·가중 사유를 반영해 법률상 선고할 수 있는 가장 넓은 범위예요.',
  recommended: { minMonths: 6, maxMonths: 18, basis: '일반사기 제1유형(1억 원 미만), 기본영역. 특별양형인자 없음' },
  terms: [ { term: '기본영역', desc: '형을 무겁게 하거나 가볍게 하는 특별한 사정이 없어 기본 권고 형량이 적용되는 구간' } ],
};

export const mockSummary = [
  '지인 1명에게 1회 4,500만 원 송금받음',
  '받은 돈을 생활비와 빚 갚는 데 사용',
  '재판 중 1,500만 원 변제, 합의는 안 됨',
  '형사처벌 전력 없음',
];
