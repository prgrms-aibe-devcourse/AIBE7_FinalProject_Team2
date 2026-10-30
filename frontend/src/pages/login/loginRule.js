// 로그인 화면 규칙 (FE-117 · FE-133). 화면(loginPage.js)과 떼어 두어 node --test로 확인한다

/**
 * 아이디 · 비밀번호가 틀렸다는 실패인가(입력칸을 "잘못된 입력"으로 표시할 경우).
 * 서버는 아이디가 없는지 · 비밀번호가 틀렸는지 나누지 않으므로(BE-113 결정 3) 두 칸을 함께 표시한다.
 * 카카오 로그인 · 연결 실패 · 요청 제한은 입력이 틀린 것이 아니라 표시하지 않는다
 */
export function isCredentialFailure(failure, method) {
  return method === 'password' && (failure?.code === 'INVALID_CREDENTIALS' || failure?.code === 'VALIDATION_ERROR');
}
