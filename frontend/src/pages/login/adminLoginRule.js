// 로그인 화면이 관리자 로그인을 시도하는 규칙 (FE-53). 환경변수를 읽는 쪽(adminLogin.js)과 나눠 두어 node 테스트로 확인할 수 있게 한다.
// 관리자면 { displayName, email, ... }, 관리자 계정이 아니면 null. 연결 오류 등은 그대로 던진다.
// 목 관리자 로그인은 아무 값이나 받아서 모든 목 사용자가 관리자가 되므로, 목 모드에서는 아이디가 admin으로 시작할 때만 시도한다
export async function tryAdminLoginWith({ mode, loadApi }, id, password) {
  if (mode === 'mock' && !/^admin/i.test(id)) return null;
  const api = await loadApi();
  try {
    await api.csrf(); // 로그인도 CSRF 토큰이 필요하다 (8-1)
    return await api.login(id, password);
  } catch (failure) {
    if (failure?.code === 'INVALID_CREDENTIALS' || failure?.code === 'VALIDATION_ERROR') return null;
    throw failure;
  }
}
