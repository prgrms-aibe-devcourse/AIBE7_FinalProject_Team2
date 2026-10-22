// npm test : 공개 카드 고르기(FE-77). 고른 칸에 카드가 없으면 지금 → 처음 → 다시 정함 순으로 대신 고른다
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { shownSlot } from './shownCard.js';

test('고른 칸에 카드가 있으면 그 칸', () => {
  assert.equal(shownSlot({ initial: {}, retake: {}, current: {} }, 'retake'), 'retake');
});

test('고른 칸이 비면 지금 → 처음 순으로 대신 고르고, 모두 비면 null', () => {
  assert.equal(shownSlot({ initial: {}, retake: null, current: {} }, 'retake'), 'current');
  assert.equal(shownSlot({ initial: {}, retake: null, current: null }, 'current'), 'initial');
  assert.equal(shownSlot({ initial: null, retake: null, current: null }, 'current'), null);
});
