// 로그인 화면의 관리자 · 회원 분기 규칙 (FE-53 · FE-117). 환경변수를 읽는 쪽(adminLogin.js)과 나눠 두어 node 테스트로 확인할 수 있게 한다.
//
// FE-117: 아이디에 @가 있으면 관리자(이메일 로그인, A-2), 없으면 회원(아이디 로그인, M-6)이다.
// 회원 아이디는 영문 · 숫자 · 밑줄만 받아 @를 쓸 수 없으므로(BE-113 결정 2) 둘이 겹치지 않는다.
// 예전에는 모든 아이디 · 비밀번호를 관리자 로그인에 먼저 보냈다 — 회원 비밀번호가 관리자 API로 가고, 관리자 감사 로그에
// 로그인 실패가 쌓이고, 관리자 로그인 요청 제한(IP별 분당 5번)을 회원이 써 버려서 이렇게 나눈다
export const isAdminLoginId = (id) => String(id ?? '').includes('@');

// 로그인 요청을 어디로 보낼지: 'admin'(관리자 로그인만) 또는 'member'(회원 로그인만 — 카카오 · 아이디). 둘 다 부르는 경우는 없다.
// 계정 기준으로는 겹치지 않지만, 입력 기준으로는 회원이 실수로 이메일을 넣으면 관리자 로그인으로 간다(관리자 실패와 같은 안내가 나온다)
export const loginTarget = (request) => (request?.method === 'password' && isAdminLoginId(request.id) ? 'admin' : 'member');

// 관리자면 { displayName, email, ... }, 관리자 아이디가 아니거나 계정 · 비밀번호가 틀리면 null. 연결 오류 등은 그대로 던진다.
// 목 모드도 같은 규칙이다(목 관리자 로그인은 아무 비밀번호나 받으므로 @가 있는 아이디면 관리자로 들어간다)
export async function tryAdminLoginWith({ loadApi }, id, password) {
  if (!isAdminLoginId(id)) return null;
  const api = await loadApi();
  try {
    await api.csrf(); // 로그인도 CSRF 토큰이 필요하다 (8-1)
    return await api.login(id, password);
  } catch (failure) {
    if (failure?.code === 'INVALID_CREDENTIALS' || failure?.code === 'VALIDATION_ERROR') return null;
    throw failure;
  }
}
