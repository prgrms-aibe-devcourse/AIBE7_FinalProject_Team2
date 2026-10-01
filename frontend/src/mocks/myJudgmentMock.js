// S-07 · S-08 공통 — S-06에서 실제로 제출한 판결을 myJudgment · diffFromMine으로 바꾼다.
import { readJudgment } from './mockExperienceStore.js';

// "상태 강제 선택"처럼 S-06을 거치지 않고 상태만 바꾼 경우를 대비한 대체값.
const fallbackMyJudgment = { subjectType: 'USER', penaltyType: 'PRISON', reducedTo: null, prisonMonths: 180, fineAmount: null, suspensionMonths: null };

function finalPenaltyType(judgment) {
  return judgment.reducedTo ?? judgment.penaltyType;
}

export function getMyJudgment() {
  return readJudgment() ?? structuredClone(fallbackMyJudgment);
}

// API 10 diffFromMine과 같은 규칙: 형벌 종류가 다르면 모두 null(ERD · API 명세).
export function diffFromJudgment(subject, myJudgment) {
  const subjectType = finalPenaltyType(subject);
  const samePenaltyType = subjectType === finalPenaltyType(myJudgment);
  if (!samePenaltyType) return { samePenaltyType, prisonMonthsDiff: null, fineAmountDiff: null };
  return {
    samePenaltyType,
    prisonMonthsDiff: subjectType === 'PRISON' ? subject.prisonMonths - myJudgment.prisonMonths : null,
    fineAmountDiff: subjectType === 'FINE' ? subject.fineAmount - myJudgment.fineAmount : null,
  };
}
