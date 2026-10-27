// 개발용 목업 로그인 (FE-52, S-12). 목 모드에서 화면 · 헤더 상태를 확인하려고 둔다. 실제 모드는 realApi.js의
// 같은 이름(login · signup · logout, FE-74 · FE-117)을 쓴다.
// 어떤 아이디 · 비밀번호든 로그인된다. 계정을 저장하거나 비밀번호를 어디에도 보내지 · 남기지 않는다.
// @가 있는 아이디는 로그인 화면이 목 관리자 로그인으로 보내므로(adminLoginRule.js) 여기로 오지 않는다
const delay = () => new Promise((resolve) => setTimeout(resolve, 250));
const KAKAO_NICKNAME = '카카오 회원'; // 목업 카카오 로그인의 닉네임
const NICKNAME_MAX = 12;

// 오류 모양은 서버와 같다: details는 [{ field, reason }] 배열(API 명세 1-4, PR #306 리뷰). 칸이 없으면 null
function fail(code, field = null) {
  const messages = { VALIDATION_ERROR: '입력값을 확인해 주세요.', DUPLICATE_ID: '이미 쓰고 있는 아이디예요.' };
  throw { code, message: messages[code], currentStatus: null, details: field ? [{ field, reason: code }] : null };
}

// 목업 닉네임: 아이디의 @ 앞부분(최대 12자)
export function mockNicknameOf(id) {
  return String(id).trim().split('@')[0].slice(0, NICKNAME_MAX);
}

// method: 'kakao' | 'password'. 응답 { nickname }
export async function login({ method, id, password } = {}) {
  await delay();
  if (method === 'kakao') return { nickname: KAKAO_NICKNAME };
  if (method !== 'password') fail('VALIDATION_ERROR', 'method');
  if (!mockNicknameOf(id ?? '')) fail('VALIDATION_ERROR', 'id');
  if (!password) fail('VALIDATION_ERROR', 'password');
  return { nickname: mockNicknameOf(id) };
}

// 목업 회원가입 (FE-104). 계정을 만들지 않고 닉네임만 돌려준다(가입하면 바로 로그인된 상태).
// admin으로 시작하는 아이디는 이미 있는 아이디로 본다(중복 안내 화면 확인용). 응답 { nickname }
export async function signup({ id, password, nickname } = {}) {
  await delay();
  if (!String(id ?? '').trim() || !password || !String(nickname ?? '').trim()) fail('VALIDATION_ERROR');
  if (String(id).trim().startsWith('admin')) fail('DUPLICATE_ID', 'id');
  return { nickname: String(nickname).trim() };
}

export async function logout() {
  await delay();
  return null;
}
