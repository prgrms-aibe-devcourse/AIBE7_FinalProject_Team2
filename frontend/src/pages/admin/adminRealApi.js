// 관리자 API 실제 호출 (API 명세 8장 A-1 ~ A-15, A-13-1 ~ A-13-13 · A-13-18 ~ 21). adminMockApi.js와 함수 이름 · 인자 · 응답 형식이 같다.
// A-1 ~ A-13은 BE-39 · BE-41 ~ BE-43으로 구현돼 있다. A-14 · A-15(사건 등록)는 초안 계약이라 서버가 구현되기 전에는 404가 온다.
import { adminDelete, adminGet, adminPatch, adminPost, adminPut, fetchCsrf } from './adminClient.js';

export * from './adminOpsRealApi.js'; // 운영 메뉴(대시보드 · 회원 · 게시글 · 신고 · 관리자 계정 · 공지)

const id = (value) => encodeURIComponent(value);

// A-1 ~ A-4 인증
export const csrf = () => fetchCsrf();
export const login = (email, password) => adminPost('/auth/login', { email, password });
export const logout = () => adminPost('/auth/logout');
export const me = () => adminGet('/auth/me');

// A-5 검수 사건 목록. status: 'DRAFT,REVIEW' 등, pending: true | undefined
export function getCases({ status, pending, page = 0, size = 20 } = {}) {
  const query = new URLSearchParams();
  if (status) query.set('status', status);
  if (pending !== undefined) query.set('pending', String(pending));
  query.set('page', String(page));
  query.set('size', String(size));
  return adminGet(`/cases?${query}`);
}

// A-6 · A-7 · A-8 상세 조회
export const getCase = (caseId) => adminGet(`/cases/${id(caseId)}`);
export const getSourceText = (caseId, sourceId) => adminGet(`/cases/${id(caseId)}/sources/${id(sourceId)}`);
export const getJudgment = (judgmentId) => adminGet(`/judgments/${id(judgmentId)}`);

// A-9 ~ A-13 상태 변경
export const approveJudgment = (judgmentId) => adminPost(`/judgments/${id(judgmentId)}/approve`);
export const rejectJudgment = (judgmentId, reason) => adminPost(`/judgments/${id(judgmentId)}/reject`, { reason });
export const updateCase = (caseId, changes) => adminPatch(`/cases/${id(caseId)}`, changes);
export const setValueAxis = (factorId, valueAxis) => adminPut(`/factors/${id(factorId)}/value-axis`, { valueAxis });
export const publishCase = (caseId) => adminPost(`/cases/${id(caseId)}/publish`);
// A-13-14 공개 취소: 사용자 화면에서 숨긴다(PUBLISHED → REVIEW). 사유는 감사 로그에만 남는다 (BE-118)
export const unpublishCase = (caseId, reason) => adminPost(`/cases/${id(caseId)}/unpublish`, { reason });

// A-13-22 특별 모드 경고 확인: 낮춘 검사 오류를 원 판결문과 대조한 뒤 누른다. 확인해야 공개 조건 SPECIAL_MODE_UNREVIEWED가 풀린다 (BE-165)
export const confirmSpecialMode = (caseId) => adminPost(`/cases/${id(caseId)}/special-mode/confirm`);

// A-14 · A-15 · A-14-1 사건 등록(원본 판결문 → 파이프라인 실행 → 적재)
export const createImport = (request) => adminPost('/case-imports', request);
export const getImport = (importId) => adminGet(`/case-imports/${id(importId)}`);
// A-14-1 적재: 생성이 끝난(SUCCEEDED) 등록을 사건(DRAFT)으로 만든다. A-15와 같은 형식에 caseId가 채워져 돌아온다(FE-72)
export const loadImport = (importId) => adminPost(`/case-imports/${id(importId)}/load`);

// ── A-13-1 ~ A-13-5 · A-13-9 사건 사진 (BE-91 · BE-101)
// 사진은 사람이 만들어 올리고, AI는 참고할 한글 프롬프트만 추천한다 (BE-91 결정)
export const suggestRepresentativePrompt = (caseId) => adminPost(`/cases/${id(caseId)}/photos/representative/prompt`);
export const suggestIncidentPrompt = (caseId, hint) =>
  adminPost(`/cases/${id(caseId)}/photos/incident/prompt`, hint ? { hint } : {});

/** A-13-3 대표사진 교체. 응답은 A-6과 같은 사건 상세다 */
export function uploadRepresentativePhoto(caseId, file) {
  const form = new FormData();
  form.append('file', file);
  return adminPost(`/cases/${id(caseId)}/photos/representative`, form);
}

/** A-13-4 발단 · 결론 사진 업로드. 같은 역할로 다시 올리면 서버가 교체한다(역할별 1장) */
export function uploadIncidentPhoto(caseId, { file, role, caption, prompt }) {
  const form = new FormData();
  form.append('file', file);
  form.append('role', role);
  if (caption) form.append('caption', caption);
  if (prompt) form.append('prompt', prompt);
  return adminPost(`/cases/${id(caseId)}/photos/incident`, form);
}

export const getIncidentPhotos = (caseId) => adminGet(`/cases/${id(caseId)}/photos/incident`);
export const deleteIncidentPhoto = (caseId, role) => adminDelete(`/cases/${id(caseId)}/photos/incident/${id(role)}`);

// ── A-13-6 ~ A-13-8 신문 지면 (BE-92)
export const generateCasePaper = (caseId) => adminPost(`/cases/${id(caseId)}/paper/generate`);
export const getCasePaper = (caseId) => adminGet(`/cases/${id(caseId)}/paper`);
export const updateCasePaper = (caseId, changes) => adminPatch(`/cases/${id(caseId)}/paper`, changes);

// ── A-13-10 ~ A-13-13 사건 대본 검수 (BE-108)
export const getCaseScript = (caseId) => adminGet(`/cases/${id(caseId)}/script`);
export const approveCaseScript = (caseId) => adminPost(`/cases/${id(caseId)}/script/approve`);
export const rejectCaseScript = (caseId, reason) => adminPost(`/cases/${id(caseId)}/script/reject`, { reason });
/** A-13-13 대사 한 줄 수정. factorId는 파이프라인 번호(= factor.display_order)다 */
export const updateCaseScriptLine = (caseId, factorId, text) =>
  adminPatch(`/cases/${id(caseId)}/script/evidence/${id(factorId)}`, { text });

// ── A-13-18 ~ A-13-21 대본 블록 단위 검수 (BE-126 2차, FE-148). unit은 U0 ~ U4, 응답은 모두 A-13-10과 같은 형식이다
const blockUrl = (caseId, unit) => `/cases/${id(caseId)}/script/blocks/${id(unit)}`;
export const approveScriptBlock = (caseId, unit) => adminPost(`${blockUrl(caseId, unit)}/approve`);
export const rejectScriptBlock = (caseId, unit, reason) => adminPost(`${blockUrl(caseId, unit)}/reject`, { reason });
/** A-13-20 "확인했음" — 낡았지만 그대로 두기로 한 블록의 근거를 지금 값으로 맞춘다 */
export const acknowledgeScriptBlockStale = (caseId, unit) => adminPost(`${blockUrl(caseId, unit)}/acknowledge-stale`);
/** A-13-21 블록 문장 한 줄 수정 (U0 · U1 · U2 · U4). path는 검수 결과와 같은 JSON Pointer다 */
export const updateScriptBlockText = (caseId, unit, path, text) => adminPatch(`${blockUrl(caseId, unit)}/text`, { path, text });
