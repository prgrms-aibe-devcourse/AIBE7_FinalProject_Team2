import { mockOverview } from './overviewMockData.js';
import { mockComparisonBase, fallbackPreJudgment } from './comparisonMockData.js';
import { read, readPreJudgment } from './mockExperienceStore.js';
import { getMyJudgment } from './myJudgmentMock.js';
import { buildPreToFinal, buildPreToCourt, buildUserSummary, buildMatrix, buildRuleSentences } from './comparisonMock.js';

const delay = () => new Promise((resolve) => setTimeout(resolve, 250));

// HTTP 전송을 하지 않는 목이므로 HTTP 상태 대신 명세의 에러 본문만 던진다.
function fail(code, currentStatus) {
  const messages = { CASE_NOT_FOUND: '사건을 찾을 수 없습니다.', INVALID_STATE: '지금 단계에서는 할 수 없는 요청입니다.' };
  throw { code, message: messages[code], currentStatus };
}

function checkState(caseId) {
  const { status } = read();
  if (caseId !== 1) fail('CASE_NOT_FOUND', status);
  // COMPLETED 전 거절. 공통 래퍼가 currentStatus에 맞는 화면으로 안내한다(API 명세 1-6).
  if (status !== 'COMPLETED') fail('INVALID_STATE', status);
  return status;
}

// API 14 — GET /cases/{caseId}/experience/comparison
// record: 마이페이지 "내 결과 보기"(FE-75)의 목 전용 옵션. 마이페이지 목은 여러 사건을 마친 기록이라(personalityMockApi.js)
// 지금 체험 상태 · 사건 번호를 따지지 않고 1번 사건 비교 목을 돌려준다(화면에 "목데이터" 표기). 실제 API에는 없는 인수다
export async function getComparison(caseId, { record = false } = {}) {
  await delay();
  if (!record) checkState(caseId);
  const myJudgment = getMyJudgment();
  const preJudgment = readPreJudgment() ?? fallbackPreJudgment;
  const { ai, court, factorLabels, factorTags, factorRevealStages } = mockComparisonBase;
  const matrix = buildMatrix(myJudgment.factors ?? [], ai.factors, court.factors, factorLabels, factorRevealStages);
  return {
    preToFinal: buildPreToFinal(myJudgment, preJudgment, mockOverview.rangeOptions),
    preToCourt: buildPreToCourt(court, preJudgment, mockOverview.rangeOptions),
    judgments: {
      USER: { ...myJudgment, summary: buildUserSummary(myJudgment.factors ?? [], factorTags) },
      AI: { ...ai },
      COURT: { ...court },
    },
    matrix,
    ruleSentences: buildRuleSentences(matrix, factorTags),
    // (확장) AI 비교 분석: MVP에서는 항상 false(API 14 명세).
    analysisAvailable: false,
  };
}
