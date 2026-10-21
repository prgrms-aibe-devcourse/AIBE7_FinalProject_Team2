import { test, beforeEach } from 'node:test';
import assert from 'node:assert/strict';
import { logout, reissueAccessToken, request } from './client.js';
import { currentUser, saveUser } from '../utils/session.js';

// fetch를 바꿔 끼워 요청을 기록한다. responder(url, init)가 { status, body }를 돌려준다
let calls;
function stubFetch(responder) {
  calls = [];
  globalThis.fetch = async (url, init) => {
    calls.push({ url, auth: init.headers.Authorization });
    const { status, body } = await responder(url, init);
    return { status, ok: status >= 200 && status < 300, json: async () => body };
  };
}
const reissued = (token) => ({ status: 200, body: { accessToken: token, accessTokenExpiresAt: '2026-10-21T00:15:00Z' } });
const invalid = { status: 401, body: { code: 'TOKEN_INVALID', message: '' } };
const store = () => { const map = new Map(); return { getItem: (k) => map.get(k) ?? null, setItem: (k, v) => map.set(k, v), removeItem: (k) => map.delete(k) }; };

beforeEach(async () => {
  stubFetch(() => ({ status: 204 }));
  await logout(); // 테스트마다 토큰을 비운다
  globalThis.localStorage = store();
});

test('재발급하면 이후 요청에 액세스 토큰을 싣는다', async () => {
  stubFetch((url) => (url.endsWith('/auth/token/reissue') ? reissued('A') : { status: 200, body: {} }));
  await reissueAccessToken();
  await request('GET', '/my-page');
  assert.equal(calls[1].auth, 'Bearer A');
});

test('401이면 한 번 재발급하고 같은 요청을 새 토큰으로 다시 보낸다', async () => {
  let first = true;
  stubFetch((url) => {
    if (url.endsWith('/auth/token/reissue')) return reissued('B');
    if (first) { first = false; return invalid; }
    return { status: 200, body: { ok: true } };
  });
  assert.deepEqual(await request('GET', '/my-page'), { ok: true });
  assert.deepEqual(calls.map((c) => [c.url.replace('/api/v1', ''), c.auth]), [['/my-page', undefined], ['/auth/token/reissue', undefined], ['/my-page', 'Bearer B']]);
});

test('동시에 401을 받아도 재발급은 한 번만 부른다', async () => {
  stubFetch((url, init) => {
    if (url.endsWith('/auth/token/reissue')) return new Promise((resolve) => setTimeout(() => resolve(reissued('C')), 5));
    if (init.headers.Authorization) return { status: 200, body: {} };
    return invalid;
  });
  await Promise.all([request('GET', '/a'), request('GET', '/b')]);
  assert.equal(calls.filter((c) => c.url.endsWith('/auth/token/reissue')).length, 1);
});

test('재발급이 401이면 원래 오류를 던지고 로그인 표시를 지운다', async () => {
  saveUser({ nickname: '카카오 회원' });
  stubFetch(() => invalid);
  await assert.rejects(request('GET', '/my-page'), { status: 401 });
  assert.equal(calls.length, 2); // 원래 요청 + 재발급, 다시 보내지 않는다
  assert.equal(currentUser(), null);
});

test('재발급이 연결 오류면 로그인 표시는 남긴다', async () => {
  saveUser({ nickname: '카카오 회원' });
  stubFetch(() => { throw new TypeError('offline'); });
  await assert.rejects(reissueAccessToken(), { code: 'NETWORK_ERROR' });
  assert.deepEqual(currentUser(), { nickname: '카카오 회원' });
});

test('401이 아닌 오류는 재발급하지 않는다', async () => {
  stubFetch(() => ({ status: 404, body: { code: 'CASE_NOT_FOUND', message: '' } }));
  await assert.rejects(request('GET', '/cases/9'), { code: 'CASE_NOT_FOUND' });
  assert.equal(calls.length, 1);
});

test('로그아웃하면 토큰을 버린다', async () => {
  stubFetch((url) => (url.endsWith('/auth/token/reissue') ? reissued('D') : { status: 204 }));
  await reissueAccessToken();
  await logout();
  await request('GET', '/cases');
  assert.equal(calls.at(-1).auth, undefined);
});
