import { API_MODE } from '../../api/config.js';
import { loadAdminApi } from '../admin/adminApi.js';

// 로그인 화면의 아이디 · 비밀번호가 관리자 계정이면 관리자 세션(A-1 · A-2)으로 로그인한다 (FE-53).
// 관리자면 { displayName, email, ... }, 관리자 계정이 아니면 null. 연결 오류 등은 그대로 던진다.
// 목 관리자 로그인은 아무 값이나 받아서 모든 목 사용자가 관리자가 되므로, 목 모드에서는 아이디가 admin으로 시작할 때만 시도한다
export async function tryAdminLogin(id, password) {
  if (API_MODE === 'mock' && !/^admin/i.test(id)) return null;
  const api = await loadAdminApi();
  try {
    await api.csrf(); // 로그인도 CSRF 토큰이 필요하다 (8-1)
    return await api.login(id, password);
  } catch (failure) {
    if (failure?.code === 'INVALID_CREDENTIALS' || failure?.code === 'VALIDATION_ERROR') return null;
    throw failure;
  }
}

// 관리자 세션 로그아웃. 이미 끝난 세션(401)이거나 실패해도 이 브라우저의 표시는 호출하는 쪽이 지운다
export async function adminLogout() {
  const api = await loadAdminApi();
  await api.logout().catch(() => {});
}
