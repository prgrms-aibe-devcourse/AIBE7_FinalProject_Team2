// npm test : 책갈피 캐릭터 저장
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { getBookmarkCharacter, selectedBookmarkFace, setBookmarkCharacter, setBookmarkFace } from './bookmarkCharacter.js';

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

test('얼굴 아이콘을 고르면 캐릭터와 표정이 함께 저장되고, 처음에는 내로 happy다', () => {
  const storage = memory();
  assert.equal(selectedBookmarkFace(storage), '/images/characters/icon/generated/naero_face-happy.webp');
  setBookmarkFace('nambul', 'thinking', 'generated', storage);
  assert.equal(getBookmarkCharacter(storage), 'nambul');
  assert.equal(selectedBookmarkFace(storage), '/images/characters/icon/generated/nambul_face-thinking.webp');
});

test('모르는 캐릭터나 표정은 저장하지 않는다', () => {
  const storage = memory();
  setBookmarkFace('law', 'happy', storage);
  setBookmarkFace('naero', 'angry', storage);
  assert.equal(selectedBookmarkFace(storage), '/images/characters/icon/generated/naero_face-happy.webp');
});

test('잘라 만든 얼굴도 고를 수 있고, 모르는 종류는 저장하지 않는다', () => {
  const storage = memory();
  setBookmarkFace('naero', 'cheer', 'cropped', storage);
  assert.equal(selectedBookmarkFace(storage), '/images/characters/icon/cropped/naero_face-cheer.webp');
  setBookmarkFace('nambul', 'happy', 'sketch', storage);
  assert.equal(selectedBookmarkFace(storage), '/images/characters/icon/cropped/naero_face-cheer.webp');
});

test('로도 책갈피로 고를 수 있고, 얼굴 대신 로 아이콘 한 장이 쓰인다', () => {
  const storage = memory();
  setBookmarkCharacter('law-gavel', storage);
  assert.equal(getBookmarkCharacter(storage), 'law-gavel');
  assert.equal(selectedBookmarkFace(storage), '/images/characters/icon/cropped/law-gavel_icon.webp');
  setBookmarkCharacter('law-sword', storage);
  assert.equal(getBookmarkCharacter(storage), 'law-gavel');
});
