// npm test : 프로필카드 아래 성향 고르기(FE-77). 같은 유형은 한 칸으로 묶고, 고른 칸이 비면 최근 → 첫 칸 순으로 대신한다
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { selectedChoice, typeChoices } from './profileCardType.js';

const type = (shareId, name) => ({ shareId, name });
const data = (initial, retake, current) => ({
  initialPersonality: initial ? { source: 'TEST', type: initial } : null,
  baselineRetake: { result: retake ? { type: retake } : null },
  judgmentPersonality: current ? { type: current } : null,
});

test('typeChoices: 세 유형이 다르면 초심 → 재심 → 최근 세 칸', () => {
  const choices = typeChoices(data(type('a', 'A형'), type('b', 'B형'), type('c', 'C형')));
  assert.deepEqual(choices.map((choice) => choice.label), ['초심: A형', '재심: B형', '최근: C형']);
});

test('typeChoices: 같은 유형이 겹치면 한 칸으로 묶는다', () => {
  const two = typeChoices(data(type('a', 'A형'), type('b', 'B형'), type('a', 'A형')));
  assert.deepEqual(two.map((choice) => choice.label), ['초심 · 최근: A형', '재심: B형']);
  const one = typeChoices(data(type('a', 'A형'), type('a', 'A형'), type('a', 'A형')));
  assert.deepEqual(one.map((choice) => choice.label), ['초심 · 재심 · 최근: A형']);
});

test('typeChoices: 없는 칸은 빼고, 최근은 판결 성향이 없으면 테스트 현재 결과로 대신한다', () => {
  assert.deepEqual(typeChoices(data(type('a', 'A형'), null, null)).map((choice) => choice.label), ['초심: A형']);
  const fallback = { ...data(type('a', 'A형'), null, null), personality: { type: type('t', 'T형') } };
  assert.deepEqual(typeChoices(fallback).map((choice) => choice.label), ['초심: A형', '최근: T형']);
  assert.deepEqual(typeChoices({}), []);
});

test('selectedChoice: 고른 칸 → 없으면 최근 → 첫 칸 → 성향이 없으면 null', () => {
  const all = data(type('a', 'A형'), type('b', 'B형'), type('c', 'C형'));
  assert.equal(selectedChoice(all, 'retake').type.name, 'B형');
  assert.equal(selectedChoice(data(type('a', 'A형'), null, type('c', 'C형')), 'retake').type.name, 'C형'); // 재심 전이면 최근
  assert.equal(selectedChoice(data(type('a', 'A형'), null, null), 'current').type.name, 'A형');
  assert.equal(selectedChoice(data(type('a', 'A형'), null, type('a', 'A형')), 'initial').keys.join(), 'initial,current'); // 묶인 칸
  assert.equal(selectedChoice({}, 'current'), null);
});

test('typeChoices: 첫 판결이 대신한 초심(JUDGMENT)은 고를 수 없다', () => {
  const judged = { ...data(null, null, type('a', 'A형')), initialPersonality: { source: 'JUDGMENT', type: type('a', 'A형') } };
  assert.deepEqual(typeChoices(judged).map((choice) => choice.label), ['최근: A형']);
});

test('typeChoices: 묶인 칸을 고르면 최근을 저장한다', () => {
  assert.equal(typeChoices(data(type('a', 'A형'), null, type('a', 'A형')))[0].pick, 'current');
  assert.equal(typeChoices(data(type('a', 'A형'), type('b', 'B형'), null))[0].pick, 'initial');
});
