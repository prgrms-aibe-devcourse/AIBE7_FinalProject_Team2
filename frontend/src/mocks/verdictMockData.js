export const mockVerdictForm = {
  penaltyOptions: [
    { penaltyType: 'PRISON', allowedMin: 1, allowedMax: 120, text: '징역 1개월 ~ 10년', suspensionAllowed: true },
    { penaltyType: 'FINE', allowedMin: 25000, allowedMax: 20000000, text: '벌금 2만 5천 원 ~ 2천만 원', suspensionAllowed: true },
  ],
  statutoryPenaltyText: '10년 이하 징역 또는 2천만 원 이하 벌금',
  allowedRangeNote: '감경·가중 사유를 반영해 법률상 선고할 수 있는 가장 넓은 범위예요.',
  recommended: { minMonths: 6, maxMonths: 18, basis: '일반사기 제1유형(1억 원 미만), 기본영역. 특별양형인자 없음' },
  suspensionRule: { maxPrisonMonths: 36, maxFineAmount: 5000000, minMonths: 12, maxMonths: 60 },
  factors: [
    { factorId: 1, label: '피해 금액이 4,500만 원이다' },
    { factorId: 2, label: '10년 가까이 알고 지낸 지인 관계를 이용했다' },
    { factorId: 3, label: '처음부터 투자할 생각 없이 받은 돈을 생활비와 빚 갚는 데 썼다' },
    { factorId: 4, label: '재판 중 피해 금액 중 1,500만 원을 갚았다' },
    { factorId: 5, label: '피해자가 처벌을 원한다' },
    { factorId: 6, label: '수사 단계부터 범행을 인정하고 반성했다' },
    { factorId: 7, label: '형사처벌 전력이 없다' },
  ],
};
