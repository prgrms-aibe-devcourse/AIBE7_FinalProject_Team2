import { test } from 'node:test';
import assert from 'node:assert/strict';
import { progressOf } from './levelProgress.js';

test('레벨 안에서 얼마나 왔는지 센다', () => {
  assert.deepEqual(progressOf({ xp: 412, currentLevelXp: 385, nextLevelXp: 430 }), { top: false, span: 45, done: 27, percent: 60 });
});

test('범위를 벗어난 xp는 0 ~ 가득으로 맞춘다', () => {
  assert.equal(progressOf({ xp: 100, currentLevelXp: 385, nextLevelXp: 430 }).done, 0);
  assert.equal(progressOf({ xp: 999, currentLevelXp: 385, nextLevelXp: 430 }).done, 45);
});

test('최고 레벨은 가득 찬다', () => {
  assert.deepEqual(progressOf({ xp: 10000, currentLevelXp: 10000, nextLevelXp: null }), { top: true, span: 1, done: 1, percent: 100 });
});
