// npm test : 로그인 화면 규칙 (FE-133)
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { credentialInputsToMark, isCredentialFailure } from './loginRule.js';

test('아이디 · 비밀번호 로그인의 인증 실패 · 형식 오류만 입력칸을 잘못된 입력으로 표시한다', () => {
  assert.equal(isCredentialFailure({ code: 'INVALID_CREDENTIALS' }, 'password'), true);
  assert.equal(isCredentialFailure({ code: 'VALIDATION_ERROR' }, 'password'), true);
  // 입력이 틀린 것이 아닌 실패는 표시하지 않는다
  ['NETWORK_ERROR', 'TOO_MANY_REQUESTS', 'TOKEN_NOT_ISSUED', 'INTERNAL_ERROR'].forEach((code) =>
    assert.equal(isCredentialFailure({ code }, 'password'), false, code));
  // 카카오 로그인은 입력칸이 없다
  assert.equal(isCredentialFailure({ code: 'INVALID_CREDENTIALS' }, 'kakao'), false);
  assert.equal(isCredentialFailure(undefined, 'password'), false);
});

test('표시할 칸은 보낸 값과 지금 값이 같은 칸만 — 기다리는 사이 고친 칸에는 지난 실패를 붙이지 않는다 (PR #354 리뷰)', () => {
  const failure = { code: 'INVALID_CREDENTIALS' };
  const request = { method: 'password', id: 'naero_01', password: 'judge1234' };
  assert.deepEqual(credentialInputsToMark(failure, request, { id: ' naero_01 ', password: 'judge1234' }), ['id', 'password']);
  assert.deepEqual(credentialInputsToMark(failure, request, { id: 'naero_01', password: 'judge12345' }), ['id']);
  assert.deepEqual(credentialInputsToMark(failure, request, { id: 'naero_02', password: 'other' }), []);
  // 입력 오류가 아닌 실패 · 카카오는 표시하지 않는다
  assert.deepEqual(credentialInputsToMark({ code: 'NETWORK_ERROR' }, request, { id: 'naero_01', password: 'judge1234' }), []);
  assert.deepEqual(credentialInputsToMark(failure, { method: 'kakao' }, { id: '', password: '' }), []);
});
