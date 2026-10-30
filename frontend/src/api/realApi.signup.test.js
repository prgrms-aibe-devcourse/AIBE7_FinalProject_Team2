// npm test : 실제 API의 아이디 회원가입(M-5) 요청 본문 (BE-133). fetch를 바꿔 끼워 서버로 가는 본문에 약관 동의가 실리는지 확인한다
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { signup } from './realApi.js';

let calls;
// 가입은 200 { nickname }, 이어지는 첫 토큰 재발급(M-1)도 200 — 화면과 같은 흐름
function stubFetch() {
  calls = [];
  globalThis.fetch = async (url, init) => {
    calls.push({ url, method: init.method, body: init.body === undefined ? undefined : JSON.parse(init.body) });
    const body = url.endsWith('/auth/signup') ? { nickname: '내로판사' } : { accessToken: 'token' };
    return { status: 200, ok: true, json: async () => body };
  };
}

test('가입 요청은 약관 동의(agreed)를 함께 보낸다 — 서버는 true만 받는다 (BE-133)', async () => {
  stubFetch();
  await signup({ id: 'naero_01', password: 'judge1234', nickname: '내로판사', agreed: true });
  assert.deepEqual(calls[0], {
    url: '/api/v1/auth/signup', method: 'POST',
    body: { id: 'naero_01', password: 'judge1234', nickname: '내로판사', agreed: true },
  });
});

test('동의 값이 없거나 true가 아니면 false로 보낸다 — 서버가 400(details agreed)으로 거절한다', async () => {
  for (const agreed of [undefined, false, 'true', 1]) {
    stubFetch();
    await signup({ id: 'naero_01', password: 'judge1234', nickname: '내로판사', agreed });
    assert.equal(calls[0].body.agreed, false, String(agreed));
  }
});
