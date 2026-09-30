// 새로고침하면 비워지는 사건별 초안. 브라우저 저장소/목 API에는 입력값을 보내지 않는다.
const drafts = new Map();

export function getDraft(caseId) {
  const draft = drafts.get(caseId);
  return draft ? structuredClone(draft) : null;
}

export function saveDraft(caseId, draft) {
  drafts.set(caseId, structuredClone(draft));
}

export function clearDraft(caseId) {
  drafts.delete(caseId);
}
