import { API_MODE } from '../../api/config.js';
import { loadAdminApi } from '../admin/adminApi.js';
import { tryAdminLoginWith } from './adminLoginRule.js';

// 로그인 화면의 아이디 · 비밀번호가 관리자 계정이면 관리자 세션(A-1 · A-2)으로 로그인한다 (FE-53). 규칙은 adminLoginRule.js
export const tryAdminLogin = (id, password) => tryAdminLoginWith({ mode: API_MODE, loadApi: loadAdminApi }, id, password);

// 관리자 세션 로그아웃. 이미 끝난 세션(401)이거나 실패해도 이 브라우저의 표시는 호출하는 쪽이 지운다
export async function adminLogout() {
  const api = await loadAdminApi();
  await api.logout().catch(() => {});
}
