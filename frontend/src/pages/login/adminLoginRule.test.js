import test from 'node:test';
import assert from 'node:assert/strict';
import { tryAdminLoginWith } from './adminLoginRule.js';

const adminApi = (overrides = {}) => ({ csrf: async () => {}, login: async () => ({ displayName: '관리자', email: 'a@x.kr' }), ...overrides });
const run = (mode, api, id = 'admin1') => {
  let loaded = false;
  const result = tryAdminLoginWith({ mode, loadApi: async () => { loaded = true; return api; } }, id, 'pw');
  return { result, wasLoaded: () => loaded };
};

test('목 모드에서 admin으로 시작하지 않는 아이디는 관리자 API를 부르지 않는다', async () => {
  const { result, wasLoaded } = run('mock', adminApi(), 'user1');
  assert.equal(await result, null);
  assert.equal(wasLoaded(), false);
});

test('목 모드에서 admin 접두어는 대소문자를 가리지 않고 관리자로 시도한다', async () => {
  const { result } = run('mock', adminApi(), 'ADMIN7');
  assert.equal((await result).displayName, '관리자');
});

test('실제 모드는 아이디와 관계없이 서버에 묻는다', async () => {
  const { result, wasLoaded } = run('real', adminApi(), 'user1');
  assert.equal((await result).email, 'a@x.kr');
  assert.equal(wasLoaded(), true);
});

test('관리자 계정이 아니라는 오류 코드는 null로 바꾼다', async () => {
  for (const code of ['INVALID_CREDENTIALS', 'VALIDATION_ERROR']) {
    const { result } = run('real', adminApi({ login: async () => { throw { code }; } }));
    assert.equal(await result, null);
  }
});

test('연결 오류 등은 그대로 던진다', async () => {
  const { result } = run('real', adminApi({ csrf: async () => { throw { code: 'NETWORK', status: 503 }; } }));
  await assert.rejects(result, { code: 'NETWORK' });
});
