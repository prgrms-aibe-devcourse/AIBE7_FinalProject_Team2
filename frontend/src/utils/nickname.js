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

/**
 * 서버가 닉네임을 거절할 때의 사유 이름(BE-134, member/domain/Nickname.java와 같은 순서 · 이름). 괜찮으면 빈 문자열.
 * 화면은 사유로 분기하지 않는다 — 목(authMockApi)이 서버와 같은 오류 모양을 내는 데 쓴다. 공백은 앞뒤도 그대로 본다(서버는 자르지 않는다)
 */
export function nicknameReason(value) {
  const nickname = value == null ? '' : String(value);
  if (!nickname.trim()) return 'NotBlank';
  if (!/^[가-힣A-Za-z0-9]+$/.test(nickname)) return 'Pattern';
  if (nickname.length < NICKNAME_MIN || nickname.length > NICKNAME_MAX) return 'Size';
  if (/[가-힣]/.test(nickname) && nickname.length > NICKNAME_MAX_HANGUL) return 'SizeWithHangul';
  if (nickname.startsWith(RESERVED_PREFIX)) return 'Reserved';
  return '';
}

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

/** 입력칸 잠금으로 뒤를 잘랐을 때 알리는 문구 (BE-134: 영문을 치다가 말없이 잘려 혼란스러웠다) */
export const NICKNAME_CLAMPED = `한글이 들어가면 ${NICKNAME_MAX_HANGUL}자까지라 뒤는 잘랐어요.`;

/**
 * 입력칸 잠금: 한글이 섞이면 한글 한도를 넘게 쳐지지 않는다. 한글 조합 중에는 글자가 깨지지 않게 조합이 끝난 뒤 자른다.
 * onClamp: 잘랐을 때 부른다(화면이 NICKNAME_CLAMPED를 보여 준다). 다른 input 처리(칸 안내 지우기)가 끝난 뒤에 부르므로 안내가 바로 지워지지 않는다.
 * 조합 끝의 이벤트 순서는 브라우저마다 다르다 — Firefox는 compositionend 뒤에 input을 한 번 더 보낸다(PR #354 리뷰). 그 input이 칸 안내를 지워도
 * 같은 작업(task) 안이면 onClamp를 한 번 더 불러 안내를 되살린다. 다음 작업에서는 잊는다(사용자가 다음 글자를 칠 때는 다시 부르지 않는다)
 */
export function lockNicknameLength(input, { onClamp } = {}) {
  const clamp = () => {
    const trimmed = input.value.trim(); // 검증(nicknameError)과 같이 앞뒤 공백을 빼고 센다
    if (!/[가-힣]/.test(trimmed) || trimmed.length <= NICKNAME_MAX_HANGUL) return false;
    input.value = trimmed.slice(0, NICKNAME_MAX_HANGUL);
    return true;
  };
  let justClamped = false; // 조합 끝에서 잘랐고, 같은 작업 안에서 뒤따를 input을 기다리는 중
  input.addEventListener('input', (event) => {
    if (event.isComposing) return;
    if (clamp()) onClamp?.();
    else if (justClamped) { justClamped = false; onClamp?.(); } // Firefox: compositionend 뒤 input이 지운 안내를 되살린다
  });
  input.addEventListener('compositionend', () => {
    const cut = clamp();
    input.dispatchEvent(new Event('input')); // 다른 처리(칸 안내 지우기)가 새 값을 보게 한다
    if (!cut) return;
    onClamp?.();
    justClamped = true;
    setTimeout(() => { justClamped = false; }, 0);
  });
}
