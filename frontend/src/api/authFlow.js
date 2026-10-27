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
    await reissueOnceMore(reissue);
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

// 재발급이 401이면 한 번만 다시 부른다 (PR #308 리뷰).
// client.js는 진행 중인 재발급 약속을 함께 쓴다(reissuing ??=). 로그인 직전에 다른 요청의 401 재시도가 재발급을 시작해 뒀다면,
// 그 요청은 새 리프레시 쿠키가 심기기 전에 나간 것이라 401로 끝난다 → 그 결과만 보고 실패로 처리하면 로그인은 됐는데 로그아웃된다.
// 그 약속이 끝난 뒤 다시 부르면 새 쿠키로 나간다. 401이 아닌 실패(연결 오류 · 429 · 5xx)는 다시 불러도 같아 바로 넘긴다
async function reissueOnceMore(reissue) {
  try {
    await reissue();
  } catch (failure) {
    if (failure?.status !== 401) throw failure;
    await reissue();
  }
}
