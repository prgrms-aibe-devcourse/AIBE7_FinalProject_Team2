import test from 'node:test';
import assert from 'node:assert/strict';
import { isAdminLoginId, loginTarget, tryAdminLoginWith } from './adminLoginRule.js';

const adminApi = (overrides = {}) => ({ csrf: async () => {}, login: async () => ({ displayName: '관리자', email: 'a@x.kr' }), ...overrides });
// 규칙이 모드와 상관없어(FE-117) 모드를 넘기지 않는다. 목 · 실제 중 어느 API인지는 loadApi가 정한다
const run = (api, id = 'admin@lawnambul.kr') => {
  let loaded = false;
  const result = tryAdminLoginWith({ loadApi: async () => { loaded = true; return api; } }, id, 'pw');
  return { result, wasLoaded: () => loaded };
};

test('@가 있으면 관리자 아이디, 없으면 회원 아이디다 (FE-117)', () => {
  assert.equal(isAdminLoginId('admin@lawnambul.kr'), true);
  assert.equal(isAdminLoginId('@'), true);
  assert.equal(isAdminLoginId('naero_01'), false);
  assert.equal(isAdminLoginId('admin7'), false); // 예전 목 규칙(admin 접두어)은 더 쓰지 않는다
  assert.equal(isAdminLoginId(''), false);
  assert.equal(isAdminLoginId(undefined), false);
});

test('로그인 요청은 관리자 · 회원 중 한쪽으로만 간다 — loginPage.signIn이 이 값으로 나눈다', () => {
  assert.equal(loginTarget({ method: 'password', id: 'admin@lawnambul.kr', password: 'pw' }), 'admin');
  assert.equal(loginTarget({ method: 'password', id: 'naero_01', password: 'pw' }), 'member');
  assert.equal(loginTarget({ method: 'password', id: 'NAERO_01', password: 'pw' }), 'member');
  assert.equal(loginTarget({ method: 'kakao' }), 'member'); // 카카오는 아이디가 없어도 회원
  assert.equal(loginTarget({ method: 'kakao', id: 'a@b.c' }), 'member'); // 카카오 요청에 아이디가 섞여 와도 관리자로 보내지 않는다
  assert.equal(loginTarget(undefined), 'member');
});

test('회원 아이디는 관리자 API를 불러오지도 부르지도 않는다 — 회원 비밀번호가 관리자 로그인으로 가지 않게', async () => {
  let called = false;
  const { result, wasLoaded } = run(adminApi({ csrf: async () => { called = true; }, login: async () => { called = true; } }), 'naero_01');
  assert.equal(await result, null);
  assert.equal(wasLoaded(), false);
  assert.equal(called, false);
});

test('관리자 아이디(@ 포함)는 CSRF 토큰을 받은 뒤 관리자 로그인을 시도한다', async () => {
  const calls = [];
  const { result, wasLoaded } = run(adminApi({
    csrf: async () => { calls.push('csrf'); },
    login: async (id) => { calls.push(`login:${id}`); return { displayName: '관리자', email: id }; },
  }));
  assert.equal((await result).email, 'admin@lawnambul.kr');
  assert.equal(wasLoaded(), true);
  assert.deepEqual(calls, ['csrf', 'login:admin@lawnambul.kr']);
});

test('관리자 계정이 아니라는 오류 코드는 null로 바꾼다', async () => {
  for (const code of ['INVALID_CREDENTIALS', 'VALIDATION_ERROR']) {
    const { result } = run(adminApi({ login: async () => { throw { code }; } }));
    assert.equal(await result, null);
  }
});

test('연결 오류 · 요청 제한 등은 그대로 던진다', async () => {
  for (const code of ['NETWORK', 'TOO_MANY_REQUESTS']) {
    const { result } = run(adminApi({ csrf: async () => { throw { code, status: 503 }; } }));
    await assert.rejects(result, { code });
  }
});
