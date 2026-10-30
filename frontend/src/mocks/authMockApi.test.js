import { test } from 'node:test';
import assert from 'node:assert/strict';
import { login, mockNicknameOf, putNickname, signup } from './authMockApi.js';

test('닉네임은 아이디 그대로(앞뒤 공백 제외)이고 12자까지만 쓴다', () => {
  assert.equal(mockNicknameOf('  naero_01 '), 'naero_01');
  assert.equal(mockNicknameOf('가나다라마바사아자차카타파하'), '가나다라마바사아자차카타');
});

test('아이디 · 비밀번호로 로그인하면 닉네임을 돌려 준다', async () => {
  assert.deepEqual(await login({ method: 'password', id: 'nambul', password: 'pw' }), { nickname: 'nambul' });
});

test('카카오 로그인은 고정 닉네임을 돌려 준다', async () => {
  assert.deepEqual(await login({ method: 'kakao' }), { nickname: '카카오 회원' });
});

test('아이디나 비밀번호가 비었거나 로그인 방법이 틀리면 입력값 오류다', async () => {
  for (const request of [{ method: 'password', id: '', password: 'pw' }, { method: 'password', id: '   ', password: 'pw' }, { method: 'password', id: 'a', password: '' }, { method: 'other' }]) {
    await assert.rejects(login(request), { code: 'VALIDATION_ERROR' });
  }
});

// 오류의 details는 서버와 같은 [{ field, reason }] 배열이다 — 화면(signupPage.js)이 같은 코드로 칸 이름을 읽는다 (FE-117 · PR #306 리뷰)
test('오류 details는 서버처럼 칸 이름이 든 배열이고, 칸이 없으면 null이다', async () => {
  await assert.rejects(login({ method: 'password', id: 'naero', password: '' }), (failure) => {
    assert.deepEqual(failure.details, [{ field: 'password', reason: 'VALIDATION_ERROR' }]);
    return true;
  });
  await assert.rejects(signup({ id: 'admin_naero', password: 'judge1234', nickname: '내로판사', agreed: true }), (failure) => {
    assert.equal(failure.code, 'DUPLICATE_ID');
    assert.deepEqual(failure.details, [{ field: 'id', reason: 'DUPLICATE_ID' }]);
    return true;
  });
  await assert.rejects(signup({ id: '', password: '', nickname: '' }), (failure) => {
    assert.equal(failure.details, null);
    return true;
  });
});

test("닉네임이 '중복'으로 시작하면 가입 · 닉네임 설정은 이미 쓰는 닉네임이다 (BE-139 흉내)", async () => {
  await assert.rejects(signup({ id: 'naero_01', password: 'judge1234', nickname: '중복판사', agreed: true }), (failure) => {
    assert.equal(failure.code, 'DUPLICATE_NICKNAME');
    assert.deepEqual(failure.details, [{ field: 'nickname', reason: 'DUPLICATE_NICKNAME' }]);
    return true;
  });
  await assert.rejects(putNickname('중복판사'), { code: 'DUPLICATE_NICKNAME' });
});

test('닉네임 설정은 형식이 맞으면 { nickname }을 돌려주고 틀리면 입력값 오류다', async () => {
  assert.deepEqual(await putNickname('내로판사'), { nickname: '내로판사' });
  for (const value of ['', '가', '닉 네임', '가'.repeat(9), ' 판사 ']) {
    await assert.rejects(putNickname(value), (failure) => {
      assert.equal(failure.code, 'VALIDATION_ERROR');
      assert.deepEqual(failure.details, [{ field: 'nickname', reason: 'VALIDATION_ERROR' }]);
      return true;
    }, JSON.stringify(value));
  }
});

test('가입은 약관에 동의(agreed: true)해야 한다 — 없거나 false면 서버처럼 칸 이름 agreed로 거절, 중복 검사보다 먼저 (BE-133)', async () => {
  for (const agreed of [undefined, false, 'true']) {
    await assert.rejects(signup({ id: 'admin_naero', password: 'judge1234', nickname: '내로판사', agreed }), (failure) => {
      assert.equal(failure.code, 'VALIDATION_ERROR');
      assert.deepEqual(failure.details, [{ field: 'agreed', reason: 'VALIDATION_ERROR' }]);
      return true;
    }, String(agreed));
  }
  assert.deepEqual(await signup({ id: 'naero_01', password: 'judge1234', nickname: '내로판사', agreed: true }), { nickname: '내로판사' });
});
