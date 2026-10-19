import { test } from 'node:test';
import assert from 'node:assert/strict';
import { clearUser, currentUser, saveUser } from './session.js';

function memoryStorage() {
  const map = new Map();
  return {
    getItem: (key) => map.get(key) ?? null,
    setItem: (key, value) => map.set(key, String(value)),
    removeItem: (key) => map.delete(key),
  };
}

test('저장된 사용자가 없으면 로그아웃 상태다', () => {
  assert.equal(currentUser(memoryStorage()), null);
});

test('로그인하면 닉네임을 읽고, 로그아웃하면 지운다', () => {
  const storage = memoryStorage();
  saveUser({ nickname: '  내로판사 ' }, storage);
  assert.deepEqual(currentUser(storage), { nickname: '내로판사' });
  clearUser(storage);
  assert.equal(currentUser(storage), null);
});

test('저장값이 깨졌거나 닉네임이 비면 로그아웃 상태로 본다', () => {
  const storage = memoryStorage();
  storage.setItem('nlnb-session', '{not json');
  assert.equal(currentUser(storage), null);
  storage.setItem('nlnb-session', JSON.stringify({ nickname: '   ' }));
  assert.equal(currentUser(storage), null);
});

test('저장소를 못 쓰면 이번 방문 동안만 기억한다', () => {
  const broken = { getItem: () => { throw new Error('blocked'); }, setItem: () => { throw new Error('blocked'); }, removeItem: () => { throw new Error('blocked'); } };
  saveUser({ nickname: '남불판사' }, broken);
  assert.deepEqual(currentUser(broken), { nickname: '남불판사' });
  clearUser(broken);
  assert.equal(currentUser(broken), null);
  clearUser(memoryStorage()); // 다음 테스트에 남지 않게 되돌린다
});
