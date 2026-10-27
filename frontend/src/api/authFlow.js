// 아이디 가입(M-5) · 로그인(M-6) 응답 뒤 처리 (FE-117). fetch와 떼어 두어 node --test로 확인한다(authFlow.test.js).
//
// 서버는 가입 · 로그인에 성공하면 리프레시 토큰 쿠키(NLNB_RT)만 심고 { nickname }을 준다(BE-113 결정 3).
// 화면은 바로 M-1 재발급으로 첫 액세스 토큰을 받는다(카카오 콜백과 같은 흐름, authCallbackPage.js).

// 가입 · 로그인은 됐는데 재발급이 실패한 경우의 오류 코드. 서버 오류 코드와 겹치지 않는 화면 전용 값이다
export const TOKEN_NOT_ISSUED = 'TOKEN_NOT_ISSUED';

/**
 * @param {Promise<{ nickname?: string }>} pending 가입 · 로그인 요청. 실패(401 · 409 · 429 등)는 그대로 던진다
 * @param {{ reissue: () => Promise<unknown>, logout: () => Promise<unknown> }} deps
 * @returns {Promise<{ nickname: string | undefined }>}
 */
export async function completeLogin(pending, { reissue, logout }) {
  const body = await pending;
  try {
    await reissue();
  } catch (cause) {
    // 재발급이 실패하면(연결 오류 · 429 · 5xx 등) 화면은 로그인하지 못한 것으로 보여 준다. 그런데 서버가 이미 심은 리프레시 쿠키가
    // 남아 있으면, 공용 PC에서 "로그인 안 됐네" 하고 떠난 뒤에도 그 브라우저에 로그인이 살아 있게 된다 → 로그아웃(M-2)으로 쿠키를 지운다.
    // 로그아웃까지 실패해도 원래 실패를 알리는 것이 먼저라 그 오류는 삼킨다
    await Promise.resolve().then(logout).catch(() => {});
    throw {
      code: TOKEN_NOT_ISSUED,
      message: '로그인을 마치지 못했어요. 다시 로그인해 주세요.',
      currentStatus: null,
      details: null,
      status: cause?.status ?? 0,
    };
  }
  return { nickname: body?.nickname };
}
