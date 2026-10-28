// npm test : 성향카드 꾸미기(FE-82). 모르는 값은 기본으로, 잠긴 것은 기본으로 보여 주고, 저장은 고른 칸만 바꾼다
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { DEFAULT_TYPE_DECO, TYPE_BACKGROUNDS, getTypeDeco, normalizeTypeDeco, setTypeDeco, shownTypeDeco } from './typeCardDeco.js';
import { unlockedSets } from './profileDeco.js';

const memory = () => {
  const map = new Map();
  return { getItem: (key) => map.get(key) ?? null, setItem: (key, value) => map.set(key, String(value)) };
};
const open = (unlocked = {}) => unlockedSets({ frames: ['basic'], backgrounds: ['cream'], sashStyles: ['none'], sashColors: [], sashIcons: ['none'], kinds: [], ...unlocked });

test('기본값: 테두리 기본 · 배경 기본(원래 타로카드) · 책갈피 없음', () => {
  assert.equal(DEFAULT_TYPE_DECO.frame, 'basic');
  assert.equal(DEFAULT_TYPE_DECO.background, 'classic');
  assert.equal(DEFAULT_TYPE_DECO.sashStyle, 'none');
  assert.equal(TYPE_BACKGROUNDS[0].id, 'classic');
});

test('normalizeTypeDeco: 모르는 값 · 빠진 값은 기본으로 본다', () => {
  assert.deepEqual(normalizeTypeDeco(null), DEFAULT_TYPE_DECO);
  assert.deepEqual(normalizeTypeDeco({ frame: 'nope', background: 'tarot', sashStyle: 'long', sashColorLong: 'nope' }), { ...DEFAULT_TYPE_DECO, background: 'tarot', sashStyle: 'long' });
});

test('shownTypeDeco: 잠긴 것은 기본으로, 배경 기본은 늘 열려 있다', () => {
  const picked = normalizeTypeDeco({ frame: 'gold', background: 'tarot', sashStyle: 'short', sashColorShort: 'navy' });
  assert.deepEqual(shownTypeDeco(picked, open()), { ...DEFAULT_TYPE_DECO, sashColorShort: 'navy' }); // 열린 책갈피가 없어 책갈피는 없음
  const opened = open({ frames: ['basic', 'gold'], backgrounds: ['cream', 'tarot'], sashStyles: ['none', 'short'], sashColors: ['short:navy'] });
  const shown = shownTypeDeco(picked, opened);
  assert.equal(shown.frame, 'gold');
  assert.equal(shown.background, 'tarot');
  assert.equal(shown.sashStyle, 'short');
  assert.equal(shown.portrait, undefined);
});

test('setTypeDeco: 고른 칸만 바꿔 저장하고, 다시 읽으면 그대로다', () => {
  const storage = memory();
  assert.deepEqual(getTypeDeco(storage), DEFAULT_TYPE_DECO);
  setTypeDeco({ frame: 'wood' }, storage);
  const next = setTypeDeco({ background: 'news' }, storage);
  assert.deepEqual(next, { ...DEFAULT_TYPE_DECO, frame: 'wood', background: 'news' });
  assert.deepEqual(getTypeDeco(storage), next);
});

test('저장소가 깨져 있으면 기본으로 본다', () => {
  assert.deepEqual(getTypeDeco({ getItem: () => '{깨짐' }), DEFAULT_TYPE_DECO);
});

test('성향카드 캐릭터: 내로 · 남불만 받고 나머지는 결과 그대로(null)로 본다', () => {
  assert.equal(normalizeTypeDeco({ character: 'NAMBUL' }).character, 'NAMBUL');
  assert.equal(normalizeTypeDeco({ character: 'LAW' }).character, null);
  assert.equal(normalizeTypeDeco(null).character, null);
});
