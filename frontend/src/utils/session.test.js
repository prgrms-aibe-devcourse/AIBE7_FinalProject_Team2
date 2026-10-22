import { test } from 'node:test';
import assert from 'node:assert/strict';
import { clearUser, currentUser, isAdmin, saveUser } from './session.js';

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

test('관리자로 로그인하면 role을 함께 읽고, 일반 사용자는 role이 없다', () => {
  const storage = memoryStorage();
  saveUser({ nickname: '운영자', role: 'admin' }, storage);
  assert.deepEqual(currentUser(storage), { nickname: '운영자', role: 'admin' });
  assert.equal(isAdmin(currentUser(storage)), true);
  saveUser({ nickname: '내로판사', role: 'judge' }, storage); // admin 말고는 저장하지 않는다
  assert.deepEqual(currentUser(storage), { nickname: '내로판사' });
  assert.equal(isAdmin(currentUser(storage)), false);
  assert.equal(isAdmin(null), false);
});

test('localStorage 접근 자체가 막혀도 인수 없이 부른 헤더가 깨지지 않는다', () => {
  const original = Object.getOwnPropertyDescriptor(globalThis, 'localStorage');
  Object.defineProperty(globalThis, 'localStorage', { configurable: true, get() { throw new Error('blocked'); } });
  try {
    assert.equal(currentUser(), null);
    assert.doesNotThrow(() => saveUser({ nickname: '남불판사' }));
    assert.deepEqual(currentUser(), { nickname: '남불판사' }); // 이번 방문 동안만 기억한다
    assert.doesNotThrow(() => clearUser());
    assert.equal(currentUser(), null);
  } finally {
    if (original) Object.defineProperty(globalThis, 'localStorage', original);
    else delete globalThis.localStorage;
  }
});
