// npm test : 책갈피 캐릭터 저장
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { getBookmarkCharacter, setBookmarkCharacter } from './bookmarkCharacter.js';

const memory = () => {
  const data = new Map();
  return { getItem: (key) => data.get(key) ?? null, setItem: (key, value) => data.set(key, value) };
};

test('고른 캐릭터를 저장하고 다시 읽는다. 처음에는 내로다', () => {
  const storage = memory();
  assert.equal(getBookmarkCharacter(storage), 'naero');
  setBookmarkCharacter('nambul', storage);
  assert.equal(getBookmarkCharacter(storage), 'nambul');
});

test('모르는 값은 저장하지 않고, 저장소를 못 쓰면 내로로 본다', () => {
  const storage = memory();
  setBookmarkCharacter('law', storage);
  assert.equal(getBookmarkCharacter(storage), 'naero');
  const broken = { getItem: () => { throw new Error('blocked'); }, setItem: () => { throw new Error('blocked'); } };
  assert.doesNotThrow(() => setBookmarkCharacter('nambul', broken));
  assert.equal(getBookmarkCharacter(broken), 'naero');
});
