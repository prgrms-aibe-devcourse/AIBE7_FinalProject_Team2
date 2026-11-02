// npm test : 약관 동의 API의 요청 모양 (BE-145, 명세 M-12 ~ M-14). fetch를 바꿔 끼워 메서드 · 주소 · 본문이 맞는지 확인한다
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { agreeTerms, cancelKakaoSignup, signupKakao } from './realApi.js';

let calls;
// 가입은 200 { nickname }, 이어지는 첫 토큰 재발급(M-1)도 200, 취소 · 동의는 204 — 화면과 같은 흐름
function stubFetch() {
  calls = [];
  globalThis.fetch = async (url, init) => {
    calls.push({ url, method: init.method, body: init.body === undefined ? undefined : JSON.parse(init.body) });
    if (url.endsWith('/auth/kakao/signup') && init.method === 'POST') return { status: 200, ok: true, json: async () => ({ nickname: '방청객 1024' }) };
    if (url.endsWith('/token/reissue')) return { status: 200, ok: true, json: async () => ({ accessToken: 'token' }) };
    return { status: 204, ok: true, json: async () => { throw new Error('본문 없음'); } };
  };
}

test('M-12 카카오 가입: POST /auth/kakao/signup에 agreed:true를 보내고, 이어서 첫 액세스 토큰을 받는다', async () => {
  stubFetch();
  const body = await signupKakao({ agreed: true });
  assert.deepEqual(body, { nickname: '방청객 1024' });
  assert.deepEqual(calls[0], { url: '/api/v1/auth/kakao/signup', method: 'POST', body: { agreed: true } });
  assert.equal(calls[1].method, 'POST');
  assert.match(calls[1].url, /\/auth\/token\/reissue$/);
});

test('M-12: 동의 값이 없거나 true가 아니면 false로 보낸다 — 서버가 400(details agreed)으로 거절한다', async () => {
  for (const agreed of [undefined, false, 'true', 1]) {
    stubFetch();
    await signupKakao({ agreed });
    assert.equal(calls[0].body.agreed, false, String(agreed));
  }
});

test('M-12: 가입 대기가 사라졌으면(409) 서버 오류 코드를 그대로 던진다', async () => {
  globalThis.fetch = async () => ({ status: 409, ok: false, json: async () => ({ code: 'KAKAO_SIGNUP_EXPIRED', message: '시간이 지났습니다.' }) });
  await assert.rejects(signupKakao({ agreed: true }), { code: 'KAKAO_SIGNUP_EXPIRED', status: 409 });
});

test('M-13 가입 취소: DELETE /auth/kakao/signup, 본문 없이 보내고 204면 null이다', async () => {
  stubFetch();
  assert.equal(await cancelKakaoSignup(), null);
  assert.deepEqual(calls, [{ url: '/api/v1/auth/kakao/signup', method: 'DELETE', body: undefined }]);
});

test('M-14 기존 회원 동의: POST /members/me/terms-agreement에 agreed:true를 보내고 204면 null이다', async () => {
  stubFetch();
  assert.equal(await agreeTerms({ agreed: true }), null);
  assert.deepEqual(calls, [{ url: '/api/v1/members/me/terms-agreement', method: 'POST', body: { agreed: true } }]);
});
