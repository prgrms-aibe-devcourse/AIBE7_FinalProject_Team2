// API 12 — GET /cases/{caseId}/experience/judgments/court 응답 목.
export const mockCourtJudgment = {
  judgment: {
    subjectType: 'COURT',
    penaltyType: 'PRISON',
    reducedTo: null,
    prisonMonths: 120,
    fineAmount: null,
    suspensionMonths: null,
    extraDispositions: [{ type: 'CONFISCATION', value: '범행에 사용한 흉기' }],
    summary: '유족의 처벌 의사를 무겁게 보면서도 공탁과 반성을 감안한 판단',
    reasoning: '오랜 채무 문제로 다투다 격분해 범행에 이르렀고 피해자가 숨지는 중한 결과가 발생했다. 다만 피고인이 범행을 인정하며 반성하고 있고 상당액을 공탁했으며 형사처벌 전력이 없는 점을 함께 고려했다.',
    excerpt: '피고인은 채무 문제로 다투던 중 격분하여 범행에 이르렀고 피해자는 결국 사망하였다. 범행 후 구호 조치를 취하지 않은 점은 죄질이 무겁다. 다만 피고인이 범행을 인정하고 반성하고 있으며 상당액을 공탁한 점, 형사처벌 전력이 없는 점을 유리한 정상으로 참작한다.',
    plainExplanation: '다툼 끝에 사람이 숨졌고 다친 사람을 돕지 않은 점은 무겁게 봤지만, 잘못을 인정하고 돈을 물어주려 한 점, 처벌받은 적이 없는 점은 유리하게 봤어요.',
    factors: [
      { factorId: 5, label: '유족이 엄벌을 원한다', direction: 'UP', evidence: '피해자 유족은 엄벌을 탄원하고 있다.' },
      { factorId: 3, label: '범행 뒤 구호 조치 없이 현장을 떠났다', direction: 'UP', evidence: '피고인은 범행 후 구호 조치를 취하지 않았다.' },
      { factorId: 9, label: '피해 회복을 위해 5,000만 원을 공탁했다', direction: 'DOWN', evidence: '피고인은 5,000만 원을 공탁하였다.' },
      { factorId: 7, label: '수사 초기부터 범행을 인정하고 반성하고 있다', direction: 'DOWN', evidence: '피고인은 범행을 인정하고 반성하고 있다.' },
      { factorId: 8, label: '형사처벌 전력이 없다', direction: 'DOWN', evidence: '피고인은 형사처벌 받은 전력이 없다.' },
    ],
  },
  // myJudgment는 S-06에서 실제로 제출한 판결에서 계산한다(myJudgmentMock.js). AI 판결은 비교 대상 고정 목.
  aiJudgment: { subjectType: 'AI', penaltyType: 'PRISON', reducedTo: null, prisonMonths: 144, fineAmount: null, suspensionMonths: null },
  // (확장) 이 사건에 대하여 사이드바: 이번 FE-8 범위에는 포함하지 않는다.
  source: { sourceOrg: '법원 판결서 인터넷열람 서비스' },
  deidentifiedItems: ['인명', '지명', '사건번호', '날짜'],
};
