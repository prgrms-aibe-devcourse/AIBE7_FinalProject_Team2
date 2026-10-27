import { test } from 'node:test';
import assert from 'node:assert/strict';
import { login, mockNicknameOf, signup } from './authMockApi.js';

test('닉네임은 아이디의 @ 앞부분이고 12자까지만 쓴다', () => {
  assert.equal(mockNicknameOf('  naero@example.test '), 'naero');
  assert.equal(mockNicknameOf('가나다라마바사아자차카타파하'), '가나다라마바사아자차카타');
});

test('아이디 · 비밀번호로 로그인하면 닉네임을 돌려 준다', async () => {
  assert.deepEqual(await login({ method: 'password', id: 'nambul@x', password: 'pw' }), { nickname: 'nambul' });
});

test('카카오 로그인은 고정 닉네임을 돌려 준다', async () => {
  assert.deepEqual(await login({ method: 'kakao' }), { nickname: '카카오 회원' });
});

test('아이디나 비밀번호가 비었거나 @ 앞이 비면 입력값 오류다', async () => {
  for (const request of [{ method: 'password', id: '', password: 'pw' }, { method: 'password', id: 'a', password: '' }, { method: 'password', id: '@abc', password: 'pw' }, { method: 'other' }]) {
    await assert.rejects(login(request), { code: 'VALIDATION_ERROR' });
  }
});

// 오류의 details는 서버와 같은 [{ field, reason }] 배열이다 — 화면(signupPage.js)이 같은 코드로 칸 이름을 읽는다 (FE-117 · PR #306 리뷰)
test('오류 details는 서버처럼 칸 이름이 든 배열이고, 칸이 없으면 null이다', async () => {
  await assert.rejects(login({ method: 'password', id: 'naero', password: '' }), (failure) => {
    assert.deepEqual(failure.details, [{ field: 'password', reason: 'VALIDATION_ERROR' }]);
    return true;
  });
  await assert.rejects(signup({ id: 'admin_naero', password: 'judge1234', nickname: '내로판사' }), (failure) => {
    assert.equal(failure.code, 'DUPLICATE_ID');
    assert.deepEqual(failure.details, [{ field: 'id', reason: 'DUPLICATE_ID' }]);
    return true;
  });
  await assert.rejects(signup({ id: '', password: '', nickname: '' }), (failure) => {
    assert.equal(failure.details, null);
    return true;
  });
});
