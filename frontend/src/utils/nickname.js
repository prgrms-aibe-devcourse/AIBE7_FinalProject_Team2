// 닉네임 검사. shortcut: 서버 규칙(길이 · 허용 글자)이 아직 없어 화면이 임시로 정한 값이다 — 닉네임 API 명세가 정해지면 그 규칙으로 바꾼다
// 길이는 공백까지 센다. 한글이 하나라도 있으면 9자, 영문 · 숫자만이면 12자까지(한글이 글자 폭이 넓어 카드에서 같은 폭을 맞춘다)
export const NICKNAME_MIN = 2;
export const NICKNAME_MAX = 12; // 입력칸 한도(영문 · 숫자). 한글이 섞이면 NICKNAME_MAX_HANGUL로 검사한다
export const NICKNAME_MAX_HANGUL = 9;
export const NICKNAME_HINT = `한글 · 영문 · 숫자로 ${NICKNAME_MIN}자 이상 써요. 한글이 있으면 ${NICKNAME_MAX_HANGUL}자, 영문 · 숫자만이면 ${NICKNAME_MAX}자까지예요(공백 포함).`;

/** 문제가 있으면 고치는 방법을 담은 안내 문구, 괜찮으면 빈 문자열 */
export function nicknameError(value) {
  const nickname = String(value).trim();
  if (!nickname) return '닉네임을 입력해 주세요.';
  if (!/^[가-힣A-Za-z0-9 ]+$/.test(nickname)) return '닉네임은 한글, 영문, 숫자, 공백만 쓸 수 있어요. 기호는 빼 주세요.';
  const max = /[가-힣]/.test(nickname) ? NICKNAME_MAX_HANGUL : NICKNAME_MAX;
  if (nickname.length < NICKNAME_MIN) return `닉네임은 ${NICKNAME_MIN}자 이상으로 적어 주세요.`;
  if (nickname.length > max) return `닉네임은 ${max}자 이하로 적어 주세요.${max === NICKNAME_MAX_HANGUL ? ' 한글이 있으면 공백 포함 9자까지예요.' : ''}`;
  return '';
}
