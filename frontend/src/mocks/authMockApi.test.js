import { test } from 'node:test';
import assert from 'node:assert/strict';
import { login, mockNicknameOf } from './authMockApi.js';

test('닉네임은 아이디의 @ 앞부분이고 12자까지만 쓴다', () => {
  assert.equal(mockNicknameOf('  naero@example.test '), 'naero');
  assert.equal(mockNicknameOf('가나다라마바사아자차카타파하'), '가나다라마바사아자차카타');
});

test('아이디 · 비밀번호로 로그인하면 닉네임을 돌려 준다', async () => {
  assert.deepEqual(await login({ method: 'password', id: 'nambul@x', password: 'pw' }), { nickname: 'nambul' });
});

test('카카오 로그인은 고정 닉네임을 돌려 준다', async () => {
  assert.deepEqual(await login({ method: 'kakao' }), { nickname: '카카오 판사' });
});

test('아이디나 비밀번호가 비었거나 @ 앞이 비면 입력값 오류다', async () => {
  for (const request of [{ method: 'password', id: '', password: 'pw' }, { method: 'password', id: 'a', password: '' }, { method: 'password', id: '@abc', password: 'pw' }, { method: 'other' }]) {
    await assert.rejects(login(request), { code: 'VALIDATION_ERROR' });
  }
});
