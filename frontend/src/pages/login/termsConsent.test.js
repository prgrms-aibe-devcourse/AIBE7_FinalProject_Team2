import assert from 'node:assert/strict';
import { test } from 'node:test';
import { SIGNUP_EXPIRED, askKakaoSignupConsent, offerTermsConsent } from './termsConsent.js';
import { TOKEN_NOT_ISSUED } from '../../api/authFlow.js';

// 창을 띄우는 함수를 바꿔 끼운다: 고른 동작(agree · decline)을 실제로 실행하고, 창이 하듯 결과를 돌려준다
const choose = (pick) => async (_options, { agree, decline }) => {
  if (pick === 'agree') return (await agree()) ?? 'agreed';
  await decline();
  return 'declined';
};

test('카카오 첫 가입: 동의하면 M-12를 agreed:true로 부르고 서버가 준 닉네임을 돌려준다', async () => {
  const calls = [];
  const api = { signupKakao: async (body) => { calls.push(['signup', body]); return { nickname: '방청객 1024' }; }, cancelKakaoSignup: async () => calls.push(['cancel']) };

  const result = await askKakaoSignupConsent(api, choose('agree'));

  assert.deepEqual(result, { status: 'agreed', nickname: '방청객 1024' });
  assert.deepEqual(calls, [['signup', { agreed: true }]]);
});

test('카카오 첫 가입: 창에 첫 가입용(signup)이라는 것을 알려 준다', async () => {
  let received;
  await askKakaoSignupConsent({ signupKakao: async () => ({}) }, async (options, actions) => { received = options; return actions.agree().then(() => 'agreed'); });
  assert.deepEqual(received, { kind: 'signup' });
});

test('카카오 첫 가입: 닫으면 M-13으로 가입 대기를 지우고 declined다 — 가입 요청은 보내지 않는다', async () => {
  const calls = [];
  const api = { signupKakao: async () => calls.push('signup'), cancelKakaoSignup: async () => calls.push('cancel') };

  const result = await askKakaoSignupConsent(api, choose('decline'));

  assert.deepEqual(result, { status: 'declined', nickname: undefined });
  assert.deepEqual(calls, ['cancel']);
});

test('카카오 첫 가입: 취소 요청이 실패하거나 없어도 닫을 수 있다 — 로그인된 것이 없고 가입 대기는 10분 뒤 사라진다', async () => {
  const failing = { signupKakao: async () => ({}), cancelKakaoSignup: async () => { throw { status: 500 }; } };
  assert.equal((await askKakaoSignupConsent(failing, choose('decline'))).status, 'declined');
  assert.equal((await askKakaoSignupConsent({ signupKakao: async () => ({}) }, choose('decline'))).status, 'declined');
});

test('카카오 첫 가입: 가입 대기가 사라졌으면(409 KAKAO_SIGNUP_EXPIRED) expired로 끝낸다', async () => {
  const api = { signupKakao: async () => { throw { code: SIGNUP_EXPIRED, status: 409 }; } };
  assert.equal((await askKakaoSignupConsent(api, choose('agree'))).status, 'expired');
});

test('카카오 첫 가입: 가입은 됐는데 재발급만 실패했으면(TOKEN_NOT_ISSUED) failed로 끝낸다 — 가입 대기가 이미 소비돼 다시 동의해도 소용없다', async () => {
  const api = { signupKakao: async () => { throw { code: TOKEN_NOT_ISSUED, status: 0 }; } };
  assert.equal((await askKakaoSignupConsent(api, choose('agree'))).status, 'failed');
});

test('카카오 첫 가입: 그 밖의 실패는 던져서 창이 안내하고 다시 고르게 한다', async () => {
  const api = { signupKakao: async () => { throw { code: 'NETWORK_ERROR', status: 0 }; } };
  await assert.rejects(askKakaoSignupConsent(api, choose('agree')), { code: 'NETWORK_ERROR' });
});

test('기존 회원: termsRequired가 아니면 창을 띄우지 않는다 — 없음 · false · 회원 정보를 못 받음 · 동의 API가 없음', async () => {
  const shown = [];
  const show = async (options) => shown.push(options);
  const api = { agreeTerms: async () => {} };
  assert.equal(await offerTermsConsent(api, { termsRequired: false }, show), 'none');
  assert.equal(await offerTermsConsent(api, {}, show), 'none');
  assert.equal(await offerTermsConsent(api, null, show), 'none');
  assert.equal(await offerTermsConsent({}, { termsRequired: true }, show), 'none');
  assert.deepEqual(shown, []);
});

test('기존 회원: termsRequired면 창을 띄우고, 동의하면 M-14를 agreed:true로 부른다', async () => {
  const calls = [];
  let received;
  const api = { agreeTerms: async (body) => calls.push(body), logout: async () => calls.push('logout') };

  const result = await offerTermsConsent(api, { termsRequired: true }, async (options, actions) => { received = options; await actions.agree(); return 'agreed'; });

  assert.equal(result, 'agreed');
  assert.deepEqual(received, { kind: 'member' });
  assert.deepEqual(calls, [{ agreed: true }]);
});

test('기존 회원: 닫으면 로그아웃하고 declined다 — 동의 요청은 보내지 않는다', async () => {
  const calls = [];
  const api = { agreeTerms: async () => calls.push('agree'), logout: async () => calls.push('logout') };

  const result = await offerTermsConsent(api, { termsRequired: true }, choose('decline'));

  assert.equal(result, 'declined');
  assert.deepEqual(calls, ['logout']);
});

test('기존 회원: 로그아웃이 실패하면 던진다 — 로그인이 살아 있으므로 창이 안내하고 다시 고르게 한다', async () => {
  const api = { agreeTerms: async () => {}, logout: async () => { throw { status: 500 }; } };
  await assert.rejects(offerTermsConsent(api, { termsRequired: true }, choose('decline')), { status: 500 });
});
