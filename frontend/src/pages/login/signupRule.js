// 회원가입 입력 규칙 (FE-104). 화면(signupPage.js)과 떼어 두어 node --test로 확인한다.
// 서버 규칙(BE-113 결정 2, auth/dto/SignupRequest.java)과 같다: 아이디 영문 소문자 · 숫자 · 밑줄 4~20자,
// 비밀번호 영문 + 숫자 8~64자(64자는 서버 Argon2 해시 비용 상한), 닉네임은 utils/nickname.js (FE-117)
import { nicknameError } from '../../utils/nickname.js';

export const ID_PATTERN = /^[a-z0-9_]{4,20}$/;
export const PASSWORD_MIN = 8;
export const PASSWORD_MAX = 64;

// values: { id, password, passwordConfirm, nickname, agreed }. 틀린 칸만 { 칸 이름: 문구 }로, 화면 위에서 아래 순서로 돌려준다(첫 칸에 포커스를 둔다)
export function validateSignup({ id = '', password = '', passwordConfirm = '', nickname = '', agreed = false } = {}) {
  const errors = {};
  const trimmedId = id.trim();
  if (!trimmedId) errors.id = '아이디를 입력해 주세요.';
  else if (!ID_PATTERN.test(trimmedId)) errors.id = '아이디는 영문 소문자 · 숫자 · 밑줄(_)로 4~20자예요.';

  if (!password) errors.password = '비밀번호를 입력해 주세요.';
  else if (password.length > PASSWORD_MAX) errors.password = `비밀번호는 ${PASSWORD_MAX}자까지 쓸 수 있어요.`;
  else if (password.length < PASSWORD_MIN || !/[A-Za-z]/.test(password) || !/[0-9]/.test(password)) {
    errors.password = `비밀번호는 영문과 숫자를 섞어 ${PASSWORD_MIN}자 이상이에요.`;
  }

  if (!passwordConfirm) errors.passwordConfirm = '비밀번호를 한 번 더 입력해 주세요.';
  else if (password && passwordConfirm !== password) errors.passwordConfirm = '비밀번호가 서로 달라요.';

  const nicknameMessage = nicknameError(nickname); // 마이페이지 닉네임 변경과 같은 규칙(한글이 있으면 8자까지)
  if (nicknameMessage) errors.nickname = nicknameMessage;

  if (!agreed) errors.agreed = '이용약관과 개인정보처리방침에 동의해 주세요.';
  return errors;
}
