// 닉네임 검사. 서버 규칙(API 명세 M-5 · M-7, BE-113 · BE-139)과 같은 형식을 화면에서 먼저 확인한다. 중복(409 DUPLICATE_NICKNAME)은 서버만 안다
// 한글이 하나라도 있으면 8자, 영문 · 숫자만이면 12자까지(한글이 글자 폭이 넓어 카드에서 같은 폭을 맞춘다). 공백은 쓸 수 없다(공백 수만 다른 비슷한 닉네임을 구분하기 어렵다)
// 서버가 닉네임을 정하지 않은 회원에게 붙이는 자동 이름 '방청객 {회원 ID}'과 헷갈리지 않게 이 말로 시작하는 닉네임은 서버도 거절한다(BE-139 리뷰)
const RESERVED_PREFIX = '방청객';
export const NICKNAME_MIN = 2;
export const NICKNAME_MAX = 12; // 입력칸 한도(영문 · 숫자). 한글이 섞이면 NICKNAME_MAX_HANGUL로 검사한다
export const NICKNAME_MAX_HANGUL = 8;
export const NICKNAME_HINT = `한글 · 영문 · 숫자로 ${NICKNAME_MIN}자 이상 써요. 한글이 있으면 ${NICKNAME_MAX_HANGUL}자, 영문 · 숫자만이면 ${NICKNAME_MAX}자까지예요. 공백은 쓸 수 없고, '방청객'으로 시작할 수 없어요.`;

/**
 * 닉네임을 아직 정하지 않은 회원의 표시 이름인가: 서버 자동 이름 '방청객 {회원 ID}'(M-8 displayName)와 카카오 로그인 직후의 임시 표시 '카카오 회원'.
 * 둘 다 공백이 있어 닉네임 형식이 아니다 — 닉네임 칸에 채워 넣지 않는다
 */
export const isUnnamed = (nickname) => /^(방청객 \d+|카카오 회원)$/.test(String(nickname ?? '').trim());

/** 문제가 있으면 고치는 방법을 담은 안내 문구, 괜찮으면 빈 문자열 */
export function nicknameError(value) {
  const nickname = String(value).trim();
  if (!nickname) return '닉네임을 입력해 주세요.';
  if (!/^[가-힣A-Za-z0-9]+$/.test(nickname)) return '닉네임은 한글, 영문, 숫자만 쓸 수 있어요. 공백과 기호는 빼 주세요.';
  if (nickname.startsWith(RESERVED_PREFIX)) return `'${RESERVED_PREFIX}'으로 시작하는 닉네임은 쓸 수 없어요. 처음 정하지 않은 회원의 이름과 헷갈려요.`;
  const max = /[가-힣]/.test(nickname) ? NICKNAME_MAX_HANGUL : NICKNAME_MAX;
  if (nickname.length < NICKNAME_MIN) return `닉네임은 ${NICKNAME_MIN}자 이상으로 적어 주세요.`;
  if (nickname.length > max) return `닉네임은 ${max}자 이하로 적어 주세요.${max === NICKNAME_MAX_HANGUL ? ` 한글이 있으면 ${NICKNAME_MAX_HANGUL}자까지예요.` : ''}`;
  return '';
}

/** 입력칸 잠금: 한글이 섞이면 한글 한도를 넘게 쳐지지 않는다. 한글 조합 중에는 글자가 깨지지 않게 조합이 끝난 뒤 자른다 */
export function lockNicknameLength(input) {
  const clamp = () => {
    const trimmed = input.value.trim(); // 검증(nicknameError)과 같이 앞뒤 공백을 빼고 센다
    if (/[가-힣]/.test(trimmed) && trimmed.length > NICKNAME_MAX_HANGUL) input.value = trimmed.slice(0, NICKNAME_MAX_HANGUL);
  };
  input.addEventListener('input', (event) => { if (!event.isComposing) clamp(); });
  input.addEventListener('compositionend', () => { clamp(); input.dispatchEvent(new Event('input')); });
}
