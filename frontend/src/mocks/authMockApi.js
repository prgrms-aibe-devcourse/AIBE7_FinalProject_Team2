// 개발용 목업 로그인 (FE-52, S-12). 사용자 로그인 API가 아직 없어(카카오 로그인 검토 중) 화면 · 헤더 상태를 확인하려고 둔다.
// 어떤 아이디 · 비밀번호든 로그인된다. 계정을 저장하거나 비밀번호를 어디에도 보내지 · 남기지 않는다.
// 실제 API가 생기면 같은 이름(login · logout)으로 realApi.js에 붙이고 이 파일은 목 모드에서만 쓴다
const delay = () => new Promise((resolve) => setTimeout(resolve, 250));
const KAKAO_NICKNAME = '카카오 판사'; // 목업 카카오 로그인의 닉네임
const NICKNAME_MAX = 12;

function fail(code, details = null) {
  const messages = { VALIDATION_ERROR: '입력값을 확인해 주세요.' };
  throw { code, message: messages[code], currentStatus: null, details };
}

// 목업 닉네임: 아이디의 @ 앞부분(최대 12자)
export function mockNicknameOf(id) {
  return String(id).trim().split('@')[0].slice(0, NICKNAME_MAX);
}

// method: 'kakao' | 'password'. 응답 { nickname }
export async function login({ method, id, password } = {}) {
  await delay();
  if (method === 'kakao') return { nickname: KAKAO_NICKNAME };
  if (method !== 'password') fail('VALIDATION_ERROR', { field: 'method' });
  if (!mockNicknameOf(id ?? '')) fail('VALIDATION_ERROR', { field: 'id' });
  if (!password) fail('VALIDATION_ERROR', { field: 'password' });
  return { nickname: mockNicknameOf(id) };
}

export async function logout() {
  await delay();
  return null;
}
