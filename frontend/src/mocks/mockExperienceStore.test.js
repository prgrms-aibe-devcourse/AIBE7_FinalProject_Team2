// npm test : 목 체험 저장소 — 브라우저 저장소 접근이 막혀도 던지지 않고 이번 방문 동안은 상태를 이어 간다
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { initialState, peek, read, restart, save } from './mockExperienceStore.js';

test('localStorage 접근 자체가 막혀도 저장한 상태를 이번 방문 동안 이어 읽는다', () => {
  const original = Object.getOwnPropertyDescriptor(globalThis, 'localStorage');
  Object.defineProperty(globalThis, 'localStorage', { configurable: true, get() { throw new Error('blocked'); } });
  try {
    assert.equal(peek(), null);
    assert.deepEqual(read(), initialState()); // 읽으면 초기 상태를 저장한다
    const state = { status: 'STARTED', lastReviewedStep: 0 };
    assert.equal(save(state), state);
    assert.deepEqual(peek(), state); // 저장 직후 같은 상태를 읽는다
    assert.deepEqual(read(), state);
    assert.doesNotThrow(() => restart());
  } finally {
    if (original) Object.defineProperty(globalThis, 'localStorage', original);
    else delete globalThis.localStorage;
  }
});
