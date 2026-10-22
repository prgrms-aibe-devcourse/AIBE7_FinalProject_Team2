// 닉네임 검사. shortcut: 서버 규칙(길이 · 허용 글자)이 아직 없어 화면이 임시로 정한 값이다 — 닉네임 API 명세가 정해지면 그 규칙으로 바꾼다
export const NICKNAME_MIN = 2;
export const NICKNAME_MAX = 12;
export const NICKNAME_HINT = `한글 · 영문 · 숫자 ${NICKNAME_MIN} ~ ${NICKNAME_MAX}자`;

/** 문제가 있으면 고치는 방법을 담은 안내 문구, 괜찮으면 빈 문자열 */
export function nicknameError(value) {
  const nickname = String(value).trim();
  if (!nickname) return '닉네임을 입력해 주세요.';
  if (nickname.length < NICKNAME_MIN || nickname.length > NICKNAME_MAX) return `닉네임은 ${NICKNAME_MIN}자 이상 ${NICKNAME_MAX}자 이하로 적어 주세요.`;
  if (!/^[가-힣A-Za-z0-9]+$/.test(nickname)) return '닉네임은 한글, 영문, 숫자만 쓸 수 있어요. 공백과 기호는 빼 주세요.';
  return '';
}
