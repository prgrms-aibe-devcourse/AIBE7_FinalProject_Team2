// API 10 — GET /cases/{caseId}/experience/judgments/ai 응답 목.
export const mockAiJudgment = {
  judgment: {
    subjectType: 'AI',
    penaltyType: 'PRISON',
    reducedTo: null,
    prisonMonths: 144,
    fineAmount: null,
    suspensionMonths: null,
    extraDispositions: [],
    summary: '다투다 벌어진 범행과 공탁 · 반성을 함께 저울질한 판단',
    reasoning: '흉기를 집어 들어 피해자를 공격하고 구호 조치 없이 자리를 떠난 점은 무겁지만, 공탁으로 피해 회복을 시도했고 범행을 인정하며 전력이 없는 점을 고려했다.',
    factors: [
      { factorId: 2, label: '다투던 중 집에 있던 흉기를 집어 들었다', direction: 'UP', evidence: null },
      { factorId: 3, label: '범행 뒤 구호 조치 없이 현장을 떠났다', direction: 'UP', evidence: null },
      { factorId: 9, label: '피해 회복을 위해 5,000만 원을 공탁했다', direction: 'DOWN', evidence: null },
      { factorId: 7, label: '수사 초기부터 범행을 인정하고 반성하고 있다', direction: 'DOWN', evidence: null },
      { factorId: 8, label: '형사처벌 전력이 없다', direction: 'DOWN', evidence: null },
    ],
  },
  // myJudgment · diffFromMine은 S-06에서 실제로 제출한 판결에서 계산한다(myJudgmentMock.js).
  references: ['형법 제250조', '살인범죄 양형기준', '유사 판례 5건'],
};
