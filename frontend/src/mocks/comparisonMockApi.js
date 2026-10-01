import { mockOverview } from './overviewMockData.js';
import { mockComparisonBase, fallbackPreJudgment } from './comparisonMockData.js';
import { read, readPreJudgment } from './mockExperienceStore.js';
import { getMyJudgment } from './myJudgmentMock.js';
import { buildPreToFinal, buildUserSummary, buildMatrix, buildRuleSentences } from './comparisonMock.js';

const delay = () => new Promise((resolve) => setTimeout(resolve, 250));

// HTTP 전송을 하지 않는 목이므로 HTTP 상태 대신 명세의 에러 본문만 던진다.
function fail(code, currentStatus) {
  const messages = { CASE_NOT_FOUND: '사건을 찾을 수 없습니다.', INVALID_STATE: '지금 단계에서는 할 수 없는 요청입니다.' };
  throw { code, message: messages[code], currentStatus };
}

function checkState(caseId) {
  const { status } = read();
  if (caseId !== 1) fail('CASE_NOT_FOUND', status);
  // COMPLETED 전 거절. 화면은 S-08로 안내한다(API 명세 1-6).
  if (status !== 'COMPLETED') fail('INVALID_STATE', status);
  return status;
}

// API 14 — GET /cases/{caseId}/experience/comparison
export async function getComparison(caseId) {
  await delay();
  checkState(caseId);
  const myJudgment = getMyJudgment();
  const preJudgment = readPreJudgment() ?? fallbackPreJudgment;
  const { ai, court, factorLabels, factorTags, factorRevealStages } = mockComparisonBase;
  const matrix = buildMatrix(myJudgment.factors ?? [], ai.factors, court.factors, factorLabels, factorRevealStages);
  return {
    preToFinal: buildPreToFinal(myJudgment, preJudgment, mockOverview.rangeOptions),
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
