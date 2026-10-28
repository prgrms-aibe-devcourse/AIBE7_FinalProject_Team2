import test from 'node:test';
import assert from 'node:assert/strict';
import { validateSignup } from './signupRule.js';

const valid = { id: 'naero_01', password: 'judge1234', passwordConfirm: 'judge1234', nickname: '내로판사', agreed: true };

test('규칙에 맞으면 오류가 없다', () => {
  assert.deepEqual(validateSignup(valid), {});
});

test('모두 비우면 다섯 칸 모두 입력 안내가 화면 순서대로 나온다', () => {
  const errors = validateSignup({});
  assert.deepEqual(Object.keys(errors), ['id', 'password', 'passwordConfirm', 'nickname', 'agreed']);
  assert.equal(errors.id, '아이디를 입력해 주세요.');
});

test('아이디는 영문 소문자 · 숫자 · 밑줄 4~20자만 받는다(앞뒤 공백은 무시)', () => {
  assert.ok(validateSignup({ ...valid, id: 'abc' }).id);
  assert.ok(validateSignup({ ...valid, id: 'Naero' }).id);
  assert.ok(validateSignup({ ...valid, id: '내로판사' }).id);
  assert.ok(validateSignup({ ...valid, id: 'a'.repeat(21) }).id);
  assert.equal(validateSignup({ ...valid, id: '  naero  ' }).id, undefined);
});

test('비밀번호는 영문과 숫자를 섞어 8자 이상이어야 한다', () => {
  assert.ok(validateSignup({ ...valid, password: 'abc123', passwordConfirm: 'abc123' }).password);
  assert.ok(validateSignup({ ...valid, password: 'abcdefgh', passwordConfirm: 'abcdefgh' }).password);
  assert.ok(validateSignup({ ...valid, password: '12345678', passwordConfirm: '12345678' }).password);
});

test('비밀번호는 64자까지다 — 서버 규칙(BE-113)과 같게 (FE-117)', () => {
  const max = `a1${'x'.repeat(62)}`;
  assert.equal(validateSignup({ ...valid, password: max, passwordConfirm: max }).password, undefined);
  const over = `${max}y`;
  assert.equal(validateSignup({ ...valid, password: over, passwordConfirm: over }).password, '비밀번호는 64자까지 쓸 수 있어요.');
});

test('비밀번호 확인이 다르면 알린다', () => {
  assert.equal(validateSignup({ ...valid, passwordConfirm: 'judge12345' }).passwordConfirm, '비밀번호가 서로 달라요.');
});

test('닉네임은 앞뒤 공백을 빼고 2자 이상, 한글은 8자까지', () => {
  assert.ok(validateSignup({ ...valid, nickname: ' 내 ' }).nickname);
  assert.ok(validateSignup({ ...valid, nickname: '가'.repeat(9) }).nickname);
  assert.equal(validateSignup({ ...valid, nickname: '가'.repeat(8) }).nickname, undefined);
  assert.equal(validateSignup({ ...valid, nickname: 'a'.repeat(12) }).nickname, undefined);
});

test('약관에 동의하지 않으면 가입할 수 없다', () => {
  assert.ok(validateSignup({ ...valid, agreed: false }).agreed);
});
