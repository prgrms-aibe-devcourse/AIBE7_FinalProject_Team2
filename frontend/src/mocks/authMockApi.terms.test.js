// npm test : 목 약관 동의 (BE-145). 서버 M-8 termsRequired · M-12 ~ M-14와 같은 모양으로 답하는지 확인한다
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { agreeTerms, cancelKakaoSignup, getMe, signupKakao } from './authMockApi.js';

test('목 회원은 동의 기록이 없는 것으로 보이고(termsRequired), 서버 M-8과 같은 칸을 준다', async () => {
  const me = await getMe();
  assert.equal(me.termsRequired, true);
  assert.deepEqual(Object.keys(me).sort(), ['createdAt', 'displayName', 'lastLoginAt', 'loginId', 'nickname', 'provider', 'termsRequired']);
});

test('agreed가 true가 아니면 서버처럼 400(details agreed)이고, 동의 상태는 바뀌지 않는다', async () => {
  for (const agreed of [undefined, false, 'true', 1]) {
    await assert.rejects(signupKakao({ agreed }), { code: 'VALIDATION_ERROR', details: [{ field: 'agreed', reason: 'VALIDATION_ERROR' }] });
    await assert.rejects(agreeTerms({ agreed }), { code: 'VALIDATION_ERROR' });
  }
  assert.equal((await getMe()).termsRequired, true);
});

test('카카오 가입 취소는 아무것도 바꾸지 않는다', async () => {
  assert.equal(await cancelKakaoSignup(), null);
  assert.equal((await getMe()).termsRequired, true);
});

test('동의하면(M-14) termsRequired가 false가 되고, 카카오 가입(M-12)은 닉네임을 돌려준다', async () => {
  assert.equal(await agreeTerms({ agreed: true }), null);
  assert.equal((await getMe()).termsRequired, false);
  assert.deepEqual(await signupKakao({ agreed: true }), { nickname: '방청객 1024' });
});
