import { loadAdminApi } from '../admin/adminApi.js';
import { tryAdminLoginWith } from './adminLoginRule.js';

export { isAdminLoginId, loginTarget } from './adminLoginRule.js';

// 로그인 화면의 아이디(@ 포함)가 관리자 계정이면 관리자 세션(A-1 · A-2)으로 로그인한다 (FE-53 · FE-117). 규칙은 adminLoginRule.js.
// 목 · 실제 모드 규칙이 같아(FE-117) 모드를 넘기지 않는다 — 어느 API를 쓸지는 loadAdminApi가 모드를 보고 고른다
export const tryAdminLogin = (id, password) => tryAdminLoginWith({ loadApi: loadAdminApi }, id, password);

// 관리자 세션 로그아웃. 이미 끝난 세션(401)이거나 실패해도 이 브라우저의 표시는 호출하는 쪽이 지운다
export async function adminLogout() {
  const api = await loadAdminApi();
  await api.logout().catch(() => {});
}
