// npm test : 닉네임 검사 (내 정보 수정, FE-77)
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { NICKNAME_CLAMPED, isUnnamed, lockNicknameLength, nicknameError, nicknameReason } from './nickname.js';

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

test('닉네임을 정하지 않은 회원의 표시 이름(방청객 {회원 ID} · 카카오 회원)은 닉네임이 아니다 (FE-137)', () => {
  ['방청객 1024', '방청객 7', '카카오 회원', ' 방청객 12 '].forEach((value) => assert.equal(isUnnamed(value), true, value));
  ['판사', '방청객', '방청객1024', '방청객 가', 'judge7', '', null, undefined].forEach((value) => assert.equal(isUnnamed(value), false, String(value)));
  // 자동 이름은 공백이 있어 닉네임 형식 검사에 걸린다 — 닉네임 칸에 그대로 두고 저장할 수 없다
  assert.notEqual(nicknameError('방청객 1024'), '');
});

test("'방청객'으로 시작하는 닉네임은 서버처럼 거절한다 — 자동 이름 흉내 방지 (BE-139 리뷰)", () => {
  ['방청객', '방청객99', '방청객가나'].forEach((value) => assert.match(nicknameError(value), /방청객/, value));
  ['판사', '나는방청객'].forEach((value) => assert.equal(nicknameError(value), '', value)); // 앞에 오지 않으면 괜찮다
});

test('입력 잠금이 뒤를 자르면 onClamp로 알린다 — 다른 input 처리가 끝난 뒤라 칸 안내가 바로 지워지지 않는다 (BE-134)', () => {
  const handlers = {};
  const order = [];
  const input = {
    value: '',
    addEventListener: (type, fn) => { handlers[type] = fn; },
    dispatchEvent: () => order.push('input 처리(칸 안내 지움)'),
  };
  let clamped = 0;
  lockNicknameLength(input, { onClamp: () => { clamped += 1; order.push('onClamp'); } });

  input.value = '가나다abcdefghij'; // 영문을 치다가 한글 한도(8자)를 넘김
  handlers.input({ isComposing: false });
  assert.equal(input.value, '가나다abcde');
  assert.equal(clamped, 1);

  input.value = '가나다라마바사아자'; // 한글 조합이 끝난 뒤 자른다
  handlers.compositionend();
  assert.equal(input.value, '가나다라마바사아');
  assert.deepEqual(order.slice(-2), ['input 처리(칸 안내 지움)', 'onClamp']);

  input.value = 'abcdefghijkl'; // 영문 · 숫자만이면 자르지 않는다
  handlers.input({ isComposing: false });
  assert.equal(clamped, 2);
  assert.match(NICKNAME_CLAMPED, /한글이 들어가면 8자까지/);
});

test('거절 사유 이름은 서버(Nickname.validate)와 같은 순서 · 이름이다 (BE-134)', () => {
  const cases = [[null, 'NotBlank'], ['  ', 'NotBlank'], ['닉네임!', 'Pattern'], ['방청객 7', 'Pattern'], ['가', 'Size'],
    ['a'.repeat(13), 'Size'], ['가'.repeat(9), 'SizeWithHangul'], ['방청객', 'Reserved'], ['가나다abcde', ''], ['a'.repeat(12), '']];
  cases.forEach(([value, reason]) => assert.equal(nicknameReason(value), reason, String(value)));
});
