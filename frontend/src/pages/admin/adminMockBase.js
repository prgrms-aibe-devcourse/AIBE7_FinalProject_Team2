// 관리자 목 API 공통 (adminMockApi.js · adminOpsMockApi.js가 함께 쓴다). 로그인 여부만 sessionStorage에 두고 나머지 상태는 메모리에만 둔다.
export const delay = () => new Promise((resolve) => setTimeout(resolve, 200));
export const SESSION_KEY = 'nlnb-mock-admin-session';
export const ADMIN = { adminId: 1, email: 'admin@example.com', displayName: '관리자' };
export const now = () => new Date().toISOString();

export function fail(code, message, extra = {}) {
  throw { code, message, currentStatus: null, details: null, ...extra };
}

function loggedIn() {
  try { return sessionStorage.getItem(SESSION_KEY) === '1'; } catch { return false; }
}
export function requireLogin() {
  if (!loggedIn()) fail('UNAUTHORIZED', '로그인이 필요해요.', { status: 401 });
}

/** 목록 응답 모양 { items, page, size, totalElements } 로 자른다 */
export function paginate(rows, page = 0, size = 20) {
  if (!Number.isInteger(page) || page < 0 || !Number.isInteger(size) || size < 1 || size > 100) fail('VALIDATION_ERROR', '입력값을 확인해 주세요.', { status: 400 });
  return structuredClone({ items: rows.slice(page * size, page * size + size), page, size, totalElements: rows.length });
}
