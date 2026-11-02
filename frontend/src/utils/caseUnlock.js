// 사건 순차 해금 (BE-146, API 1 · 16). 서버가 잠김을 정하고 화면은 그대로 따른다 — 단계 값은 서버(CaseUnlock)가 원본이다.
// 잠긴 사건은 아직 체험할 수 없는 사건(API 2가 409 CASE_LOCKED)이다. 이미 체험한 사건 · 준비 중 · 점검 중 사건은 잠기지 않는다(서버가 false로 준다)

/** 아직 열리지 않은 사건인가. 필드가 없으면(구버전 응답 · 목) 열린 것으로 본다 */
export const isLocked = (item) => item?.locked === true;

/**
 * 다음 단계가 열리기까지 더 마쳐야 하는 사건 수. API 1 최상위 unlock({ completedCases, nextStepAt })으로 센다.
 * 모든 사건이 열린 단계(nextStepAt null) · 값이 없으면 0
 */
export function casesUntilNextStep(unlock) {
  const { completedCases, nextStepAt } = unlock ?? {};
  if (!Number.isFinite(completedCases) || !Number.isFinite(nextStepAt)) return 0;
  return Math.max(0, nextStepAt - completedCases);
}

/** 서버가 409 CASE_LOCKED로 시작을 막았는가 (API 2) */
export const isCaseLocked = (error) => error?.code === 'CASE_LOCKED';
