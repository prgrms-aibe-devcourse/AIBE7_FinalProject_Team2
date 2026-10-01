import { mockVerdictForm } from './verdictMockData.js';
import { read, save, saveJudgment } from './mockExperienceStore.js';

const delay = () => new Promise((resolve) => setTimeout(resolve, 250));
const isObject = (value) => value !== null && typeof value === 'object' && !Array.isArray(value);
const nullableInteger = (value) => value === null || (Number.isInteger(value) && value >= 0);

function fail(code, currentStatus, details = null) {
  const messages = {
    CASE_NOT_FOUND: '사건을 찾을 수 없습니다.',
    INVALID_STATE: '지금 단계에서는 할 수 없는 요청입니다.',
    VALIDATION_ERROR: '입력값을 확인해 주세요.',
    INVALID_PENALTY_TYPE: '선택할 수 없는 형벌입니다.',
    OUT_OF_ALLOWED_RANGE: '선고할 수 있는 범위를 벗어났습니다.',
    INVALID_SUSPENSION: '집행유예 조건을 확인해 주세요.',
    INVALID_FACTOR: '판단 요소를 확인해 주세요.',
  };
  // HTTP 없는 목: 명세의 HTTP 상태에 대응하는 에러 본문만 던진다.
  throw { code, message: messages[code], currentStatus, details };
}

function checkState(caseId) {
  const { status } = read();
  if (caseId !== 1) fail('CASE_NOT_FOUND', status);
  if (status !== 'REVIEWED') fail('INVALID_STATE', status);
  return status;
}

export async function getVerdictForm(caseId) {
  await delay();
  checkState(caseId);
  return structuredClone(mockVerdictForm);
}

export async function postVerdict(caseId, body) {
  await delay();
  // 상태를 지연 뒤 읽는다. 검사부터 저장까지 await 없이 처리해 이중 확정을 막는다.
  const status = checkState(caseId);
  // 목 규칙: 상태 → 형식 → 형벌 → 형량 범위 → 집행유예 → 요소 순서.
  if (!isObject(body)) fail('VALIDATION_ERROR', status);
  const {
    penaltyType, reducedTo = null, prisonMonths = null, fineAmount = null, suspensionMonths = null,
    factors = [], freeOpinion = null,
  } = body;
  if (typeof penaltyType !== 'string'
    || (reducedTo !== null && typeof reducedTo !== 'string')
    || ![prisonMonths, fineAmount, suspensionMonths].every(nullableInteger)
    || !Array.isArray(factors)
    || !factors.every((factor) => isObject(factor) && Number.isInteger(factor.factorId))
    || freeOpinion !== null) {
    fail('VALIDATION_ERROR', status);
  }
  const option = mockVerdictForm.penaltyOptions.find((item) => item.penaltyType === penaltyType);
  const validCombination = option && (reducedTo === null || (option.reducibleTo ?? []).includes(reducedTo));
  const finalType = reducedTo ?? penaltyType;
  // 조합이 유효할 때만 최종 형벌의 필수값을 검사한다.
  if (validCombination && (
    (finalType === 'PRISON' && (prisonMonths === null || fineAmount !== null))
    || (finalType === 'FINE' && (fineAmount === null || prisonMonths !== null))
    || (['DEATH', 'LIFE'].includes(finalType) && [prisonMonths, fineAmount, suspensionMonths].some((value) => value !== null))
  )) fail('VALIDATION_ERROR', status);
  if (!validCombination) fail('INVALID_PENALTY_TYPE', status);
  const numeric = ['PRISON', 'FINE'].includes(finalType);
  const field = finalType === 'PRISON' ? 'prisonMonths' : 'fineAmount';
  const value = finalType === 'PRISON' ? prisonMonths : fineAmount;
  if (numeric && (value < option.allowedMin || value > option.allowedMax)) {
    fail('OUT_OF_ALLOWED_RANGE', status, [{ field, reason: 'OUT_OF_ALLOWED_RANGE' }]);
  }
  const rule = mockVerdictForm.suspensionRule;
  const ceiling = finalType === 'PRISON' ? rule.maxPrisonMonths : rule.maxFineAmount;
  if (suspensionMonths !== null && (!option.suspensionAllowed || value > ceiling
    || suspensionMonths < rule.minMonths || suspensionMonths > rule.maxMonths)) {
    fail('INVALID_SUSPENSION', status);
  }
  const ids = new Set();
  for (const { factorId, direction } of factors) {
    if (ids.has(factorId) || !mockVerdictForm.factors.some((item) => item.factorId === factorId)
      || !['UP', 'DOWN'].includes(direction)) fail('INVALID_FACTOR', status);
    ids.add(factorId);
  }
  // S-07 · S-08에서 "내 판결"로 보여줘야 해서 형벌 판단값만 별도로 저장한다(요소·자유 의견은 저장하지 않는다).
  saveJudgment({
    subjectType: 'USER',
    penaltyType,
    reducedTo: reducedTo ?? null,
    prisonMonths: finalType === 'PRISON' ? prisonMonths : null,
    fineAmount: finalType === 'FINE' ? fineAmount : null,
    suspensionMonths: suspensionMonths ?? null,
  });
  save({ status: 'VERDICT_CONFIRMED', lastReviewedStep: 4 });
  return { status: 'VERDICT_CONFIRMED' };
}
