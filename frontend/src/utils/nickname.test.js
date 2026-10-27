// npm test : 닉네임 검사 (내 정보 수정, FE-77)
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { lockNicknameLength, nicknameError } from './nickname.js';

test('한글이 있으면 8자, 영문 · 숫자만이면 12자까지는 통과, 앞뒤 공백은 무시', () => {
  ['판사', 'judge7', '익명의판사12', '  해송  ', '가'.repeat(8), 'a'.repeat(12)].forEach((value) => assert.equal(nicknameError(value), '', value));
});

test('한글이 있으면 9자부터, 영문 · 숫자만이면 13자부터 길다', () => {
  assert.match(nicknameError('가'.repeat(9)), /8자 이하/);
  assert.match(nicknameError('a'.repeat(13)), /12자 이하/);
});

test('빈 값 · 짧은 값 · 기호는 이유를 말한다', () => {
  assert.match(nicknameError('   '), /입력/);
  assert.match(nicknameError('가'), /2자 이상/);
  assert.match(nicknameError('판사!'), /한글, 영문, 숫자만/);
  assert.match(nicknameError('익명 판사'), /공백/); // 안쪽 공백도 쓸 수 없다
  assert.match(nicknameError('가  나'), /공백/);
});

test('입력 잠금은 앞뒤 공백을 빼고 센다(공백 붙은 붙여넣기가 한글 6자로 잘리지 않는다)', () => {
  const handlers = {};
  const input = { value: '', addEventListener: (type, fn) => { handlers[type] = fn; }, dispatchEvent: () => {} };
  lockNicknameLength(input);
  input.value = '  가가가가가가가가가가  ';
  handlers.input({ isComposing: false });
  assert.equal(input.value, '가가가가가가가가');
  input.value = '  가나다  ';
  handlers.input({ isComposing: false });
  assert.equal(input.value, '  가나다  '); // 한도 안이면 건드리지 않는다
});
