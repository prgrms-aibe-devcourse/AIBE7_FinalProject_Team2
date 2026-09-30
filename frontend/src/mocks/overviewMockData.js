// API 4 응답 목. API 명세 v0.5의 가상 살인 예시 사건이다.
export const mockOverview = {
  case: {
    caseId: 1,
    title: '빌린 돈 문제로 찾아온 지인을 살해한 사건',
    crimeType: 'MURDER',
    crimeCategoryLabel: '생명범죄',
    chargeName: '살인',
    overview: '피고인이 빌린 돈을 갚지 못해 오래 다투던 지인이 집으로 찾아오자, 말다툼 끝에 집에 있던 흉기로 피해자를 살해하고 구호 조치 없이 집을 나간 사건이다.',
  },
  rangeOptions: [
    { rangeOptionId: 1, label: '징역형 집행유예' },
    { rangeOptionId: 2, label: '실형 3년 미만' },
    { rangeOptionId: 3, label: '실형 3년 이상 ~ 5년 미만' },
    { rangeOptionId: 4, label: '실형 5년 이상 ~ 10년 미만' },
    { rangeOptionId: 5, label: '실형 10년 이상 ~ 20년 미만' },
    { rangeOptionId: 6, label: '실형 20년 이상' },
    { rangeOptionId: 7, label: '무기징역' },
    { rangeOptionId: 8, label: '사형' },
  ],
  // 확장(REQ-093)이라 MVP 화면은 쓰지 않는다.
  preFactors: [
    { factorId: 1, label: '돈 문제로 오래 다툼이 있었다' },
    { factorId: 2, label: '다투던 중 흉기를 집어 들었다' },
    { factorId: 3, label: '범행 뒤 현장을 떠났다' },
  ],
};
