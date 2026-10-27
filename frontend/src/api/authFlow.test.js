import test from 'node:test';
import assert from 'node:assert/strict';
import { TOKEN_NOT_ISSUED, completeLogin } from './authFlow.js';

// 호출 순서를 기록하는 가짜 재발급 · 로그아웃
function deps({ reissueFails = null, logoutFails = false } = {}) {
  const calls = [];
  return {
    calls,
    reissue: async () => { calls.push('reissue'); if (reissueFails) throw reissueFails; },
    logout: async () => { calls.push('logout'); if (logoutFails) throw { code: 'NETWORK_ERROR' }; },
  };
}

test('가입 · 로그인이 성공하면 재발급까지 하고 닉네임을 돌려준다 (FE-117)', async () => {
  const d = deps();
  const result = await completeLogin(Promise.resolve({ nickname: '내로판사' }), d);
  assert.deepEqual(result, { nickname: '내로판사' });
  assert.deepEqual(d.calls, ['reissue']);
});

test('가입 · 로그인 요청이 실패하면 재발급하지 않고 서버 오류를 그대로 넘긴다', async () => {
  for (const code of ['INVALID_CREDENTIALS', 'DUPLICATE_ID', 'TOO_MANY_REQUESTS']) {
    const d = deps();
    await assert.rejects(completeLogin(Promise.reject({ code }), d), { code });
    assert.deepEqual(d.calls, []);
  }
});

test('재발급이 실패하면 남은 리프레시 쿠키를 로그아웃으로 지우고 TOKEN_NOT_ISSUED로 알린다', async () => {
  const d = deps({ reissueFails: { code: 'TOO_MANY_REQUESTS', status: 429 } });
  await assert.rejects(completeLogin(Promise.resolve({ nickname: '내로판사' }), d), { code: TOKEN_NOT_ISSUED, status: 429 });
  assert.deepEqual(d.calls, ['reissue', 'logout']);
});

test('로그아웃까지 실패해도 원래 실패(TOKEN_NOT_ISSUED)를 알린다', async () => {
  const d = deps({ reissueFails: { code: 'NETWORK_ERROR', status: 0 }, logoutFails: true });
  await assert.rejects(completeLogin(Promise.resolve({ nickname: 'x' }), d), { code: TOKEN_NOT_ISSUED });
  assert.deepEqual(d.calls, ['reissue', 'logout']);
});
