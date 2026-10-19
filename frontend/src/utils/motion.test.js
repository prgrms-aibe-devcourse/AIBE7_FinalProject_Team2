import { test } from 'node:test';
import assert from 'node:assert/strict';
import { motionSwitchOn, reducedMotion, setMotionSwitch } from './motion.js';

function memoryStorage() {
  const map = new Map();
  return { getItem: (key) => map.get(key) ?? null, setItem: (key, value) => map.set(key, String(value)) };
}

test('저장된 값이 없으면 연출은 켜져 있다', () => {
  const storage = memoryStorage();
  assert.equal(motionSwitchOn(storage), true);
  assert.equal(reducedMotion(storage), false);
});

test('스위치를 끄면 연출을 건너뛰고, 다시 켜면 돌아온다', () => {
  const storage = memoryStorage();
  setMotionSwitch(false, storage);
  assert.equal(reducedMotion(storage), true);
  setMotionSwitch(true, storage);
  assert.equal(reducedMotion(storage), false);
});

test('저장소를 못 쓰면 이번 방문 동안만 기억한다', () => {
  const broken = { getItem: () => { throw new Error('blocked'); }, setItem: () => { throw new Error('blocked'); } };
  assert.equal(motionSwitchOn(broken), true);
  setMotionSwitch(false, broken);
  assert.equal(motionSwitchOn(broken), false);
  setMotionSwitch(true, memoryStorage()); // 다음 테스트에 남지 않게 되돌린다
});

test('저장소가 아예 없어도(null) 이번 방문 동안 기억한다', () => {
  setMotionSwitch(false, null);
  assert.equal(motionSwitchOn(null), false);
  setMotionSwitch(true, memoryStorage()); // 다음 테스트에 남지 않게 되돌린다
});
