// 관리자 API 실제 호출 (API 명세 8장 A-1 ~ A-15). adminMockApi.js와 함수 이름 · 인자 · 응답 형식이 같다.
// A-1 ~ A-13은 BE-39 · BE-41 ~ BE-43으로 구현돼 있다. A-14 · A-15(사건 등록)는 초안 계약이라 서버가 구현되기 전에는 404가 온다.
import { adminGet, adminPatch, adminPost, adminPut, fetchCsrf } from './adminClient.js';

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

// A-14 · A-15 사건 등록(원본 판결문 → 파이프라인 실행)
export const createImport = (request) => adminPost('/case-imports', request);
export const getImport = (importId) => adminGet(`/case-imports/${id(importId)}`);
