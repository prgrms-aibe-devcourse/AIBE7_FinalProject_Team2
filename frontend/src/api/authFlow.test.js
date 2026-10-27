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

// 재발급 결과를 순서대로 돌려주는 가짜(null이면 성공)
function sequence(results) {
  const calls = [];
  let n = 0;
  return {
    calls,
    reissue: async () => { calls.push('reissue'); const failure = results[n++] ?? null; if (failure) throw failure; },
    logout: async () => { calls.push('logout'); },
  };
}

test('재발급이 처음 401이면 한 번 더 불러 성공하면 로그인으로 끝낸다 — 진행 중이던 재발급(쿠키 전)을 받은 경우 (PR #308 리뷰)', async () => {
  const d = sequence([{ code: 'TOKEN_INVALID', status: 401 }, null]);
  assert.deepEqual(await completeLogin(Promise.resolve({ nickname: '내로판사' }), d), { nickname: '내로판사' });
  assert.deepEqual(d.calls, ['reissue', 'reissue']); // 로그아웃하지 않는다
});

test('재발급이 두 번 다 401이면 로그아웃하고 TOKEN_NOT_ISSUED로 알린다', async () => {
  const d = sequence([{ code: 'TOKEN_INVALID', status: 401 }, { code: 'TOKEN_INVALID', status: 401 }]);
  await assert.rejects(completeLogin(Promise.resolve({ nickname: 'x' }), d), { code: TOKEN_NOT_ISSUED, status: 401 });
  assert.deepEqual(d.calls, ['reissue', 'reissue', 'logout']);
});

test('401이 아닌 재발급 실패(429 · 연결 오류)는 다시 부르지 않는다', async () => {
  for (const failure of [{ code: 'TOO_MANY_REQUESTS', status: 429 }, { code: 'NETWORK_ERROR', status: 0 }]) {
    const d = sequence([failure, null]);
    await assert.rejects(completeLogin(Promise.resolve({ nickname: 'x' }), d), { code: TOKEN_NOT_ISSUED });
    assert.deepEqual(d.calls, ['reissue', 'logout']);
  }
});

test('로그아웃까지 실패해도 원래 실패(TOKEN_NOT_ISSUED)를 알린다', async () => {
  const d = deps({ reissueFails: { code: 'NETWORK_ERROR', status: 0 }, logoutFails: true });
  await assert.rejects(completeLogin(Promise.resolve({ nickname: 'x' }), d), { code: TOKEN_NOT_ISSUED });
  assert.deepEqual(d.calls, ['reissue', 'logout']);
});
