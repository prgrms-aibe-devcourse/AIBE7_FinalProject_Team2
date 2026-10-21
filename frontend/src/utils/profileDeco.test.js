// npm test : 대표 이미지 페이지의 도전과제 · 해금 · 꾸미기 저장 (FE-68)
import { existsSync } from 'node:fs';
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { PORTRAIT_POOL } from '../content/portraitPool.js';
import { KIND_TITLES } from './myPageView.js';
import {
  ACHIEVEMENTS, BACKGROUNDS, FRAMES, REWARD_TYPES, SASH_COLORS, SASH_ICONS, SASH_STYLES, sashColorKey, achievementProgress, getProfileDeco, kindOfPortrait, portraitLabel, setProfileDeco, shownDeco, unlockedBy, unlockerOf,
} from './profileDeco.js';

const memory = () => {
  const data = new Map();
  return { getItem: (key) => data.get(key) ?? null, setItem: (key, value) => data.set(key, value) };
};
const NOTHING = { completed: 0, collected: 0, attendanceDays: 0, hasType: false };

test('후보는 130장이고 종류별 개수가 정한 대로이며, 모두 실제 파일이다', () => {
  const counts = {};
  PORTRAIT_POOL.forEach((src) => { counts[kindOfPortrait(src)] = (counts[kindOfPortrait(src)] ?? 0) + 1; });
  assert.deepEqual(counts, {
    'characters/roles': 10, 'characters/scene': 16, 'characters/icon/generated': 12, 'characters/mascot': 12, 'characters/law': 9, 'characters/adult': 9,
    'characters/standing': 8, 'characters/cheek': 4, 'characters/cheek-slim': 4, 'characters/deform': 2, 'characters/personality': 32, cases: 12,
  });
  assert.equal(new Set(PORTRAIT_POOL).size, 130);
  PORTRAIT_POOL.forEach((src) => assert.ok(existsSync(new URL(`../../public${src}`, import.meta.url)), `없는 파일: ${src}`));
  assert.ok(!PORTRAIT_POOL.some((src) => /cases\/case-\d+\.webp$/.test(src)), '사건 대표 썸네일은 후보가 아니다');
});

test('모든 종류 · 테두리 · 배경은 처음부터 열려 있거나, 여는 도전과제가 하나 있다', () => {
  const open = unlockedBy(NOTHING);
  Object.keys(KIND_TITLES).filter((kind) => PORTRAIT_POOL.some((src) => kindOfPortrait(src) === kind)).forEach((kind) => {
    assert.ok(open.kinds.has(kind) || unlockerOf('kinds', kind), `열 방법이 없는 종류: ${kind}`);
  });
  FRAMES.forEach(({ id }) => assert.ok(open.frames.has(id) || unlockerOf('frames', id), `열 방법이 없는 테두리: ${id}`));
  BACKGROUNDS.forEach(({ id }) => assert.ok(open.backgrounds.has(id) || unlockerOf('backgrounds', id), `열 방법이 없는 배경: ${id}`));
  Object.entries(SASH_COLORS).forEach(([style, colors]) => colors.forEach(({ id }) => {
    const key = sashColorKey(style, id);
    assert.ok(open.sashColors.has(key) || unlockerOf('sashColors', key), `열 방법이 없는 책갈피 색: ${key}`);
  }));
  SASH_STYLES.forEach(({ id }) => assert.ok(open.sashStyles.has(id) || unlockerOf('sashStyles', id), `열 방법이 없는 책갈피 모양: ${id}`));
  SASH_ICONS.forEach(({ id }) => assert.ok(open.sashIcons.has(id) || unlockerOf('sashIcons', id), `열 방법이 없는 책갈피 아이콘: ${id}`));
});

test('아무것도 안 했으면 역할 의상 · 서 있는 컷 · 기본 테두리 · 크림 배경만 열려 있다', () => {
  const open = unlockedBy(NOTHING);
  assert.deepEqual([...open.kinds], ['characters/roles', 'characters/standing']);
  assert.deepEqual([...open.frames], ['basic']);
  assert.deepEqual([...open.backgrounds], ['cream']);
  assert.deepEqual([...open.sashStyles], ['none', 'short']);
  assert.deepEqual([...open.sashColors], ['short:wine']);
  assert.deepEqual([...open.sashIcons], ['none', 'star']);
  assert.equal(PORTRAIT_POOL.filter((src) => open.kinds.has(kindOfPortrait(src))).length, 18);
});

test('도전과제를 깨면 보상이 열리고, 진행도는 목표에서 멈춘다', () => {
  const stats = { completed: 1, collected: 9, attendanceDays: 12, hasType: true };
  const progress = Object.fromEntries(achievementProgress(stats).map((item) => [item.id, item]));
  assert.equal(progress['first-case'].cleared, true);
  assert.equal(progress['cases-3'].cleared, false);
  assert.equal(progress['cases-3'].value, 1);
  assert.equal(progress['attend-7'].value, 7);
  assert.equal(progress.personality.cleared, true);
  const open = unlockedBy(stats);
  assert.ok(open.kinds.has('characters/scene') && open.kinds.has('characters/personality') && open.kinds.has('cases'));
  assert.ok(!open.kinds.has('characters/mascot'));
  assert.ok(open.frames.has('wood') && open.frames.has('gold') && !open.frames.has('navy'));
  assert.ok(open.backgrounds.has('tarot') && !open.backgrounds.has('linen'));
});

test('도전과제 id는 겹치지 않고, 보상이 하나 이상 있다', () => {
  assert.equal(new Set(ACHIEVEMENTS.map((item) => item.id)).size, ACHIEVEMENTS.length);
  ACHIEVEMENTS.forEach((item) => assert.ok(REWARD_TYPES.some((type) => item[type].length > 0), item.id));
});

test('꾸미기는 고른 것만 바꿔 저장하고, 모르는 값 · 깨진 저장값은 기본으로 본다', () => {
  const storage = memory();
  assert.deepEqual(getProfileDeco(storage), { portrait: null, frame: 'basic', background: 'cream', sashStyle: 'none', sashColorShort: 'wine', sashColorLong: 'wine', sashIconLeft: 'none', sashIconRight: 'none' });
  setProfileDeco({ frame: 'gold' }, storage);
  setProfileDeco({ portrait: '/images/characters/scene/naero_idle-neutral.webp' }, storage);
  assert.deepEqual(getProfileDeco(storage), { portrait: '/images/characters/scene/naero_idle-neutral.webp', frame: 'gold', background: 'cream', sashStyle: 'none', sashColorShort: 'wine', sashColorLong: 'wine', sashIconLeft: 'none', sashIconRight: 'none' });
  setProfileDeco({ background: 'rainbow', portrait: '/images/elsewhere.webp' }, storage);
  assert.deepEqual(getProfileDeco(storage), { portrait: null, frame: 'gold', background: 'cream', sashStyle: 'none', sashColorShort: 'wine', sashColorLong: 'wine', sashIconLeft: 'none', sashIconRight: 'none' });
  storage.setItem('nlnb-profile-deco', '{broken');
  assert.deepEqual(getProfileDeco(storage), { portrait: null, frame: 'basic', background: 'cream', sashStyle: 'none', sashColorShort: 'wine', sashColorLong: 'wine', sashIconLeft: 'none', sashIconRight: 'none' });
  const broken = { getItem: () => { throw new Error('blocked'); }, setItem: () => { throw new Error('blocked'); } };
  assert.doesNotThrow(() => setProfileDeco({ frame: 'wood' }, broken));
  assert.deepEqual(getProfileDeco(broken), { portrait: null, frame: 'basic', background: 'cream', sashStyle: 'none', sashColorShort: 'wine', sashColorLong: 'wine', sashIconLeft: 'none', sashIconRight: 'none' });
});

test('카드 책갈피는 모양마다 색을 따로 저장하고, 모르는 값은 기본으로 본다', () => {
  const storage = memory();
  setProfileDeco({ sashStyle: 'short', sashColorShort: 'navy' }, storage);
  setProfileDeco({ sashStyle: 'long', sashColorLong: 'gold' }, storage);
  setProfileDeco({ frame: 'gold' }, storage); // 다른 것을 바꿔도 책갈피는 그대로
  assert.deepEqual(getProfileDeco(storage), { portrait: null, frame: 'gold', background: 'cream', sashStyle: 'long', sashColorShort: 'navy', sashColorLong: 'gold', sashIconLeft: 'none', sashIconRight: 'none' });
  setProfileDeco({ sashStyle: 'zigzag', sashColorShort: 'rainbow', sashIconLeft: 'rocket' }, storage);
  const deco = getProfileDeco(storage);
  assert.equal(deco.sashStyle, 'none');
  assert.equal(deco.sashColorShort, 'wine');
  assert.equal(deco.sashColorLong, 'gold');
  assert.equal(deco.sashIconLeft, 'none');
});

test('잠긴 꾸미기를 골라 둔 상태면 화면에는 기본으로 보인다', () => {
  const deco = { portrait: '/images/characters/mascot/trio_idle.webp', frame: 'navy', background: 'tarot', sashStyle: 'short', sashColorShort: 'gold', sashColorLong: 'wine', sashIconLeft: 'law', sashIconRight: 'star' };
  const shown = shownDeco(deco, unlockedBy(NOTHING));
  assert.equal(shown.portrait, null);
  assert.equal(shown.frame, 'basic');
  assert.equal(shown.background, 'cream');
  assert.equal(shown.sashStyle, 'short');
  assert.equal(shown.sashColorShort, 'wine', '짧은 책갈피의 금사가 잠겼으면 열린 첫 색(와인)으로');
  assert.equal(shown.sashIconLeft, 'none');
  assert.equal(shown.sashIconRight, 'star');
  assert.deepEqual(shownDeco(deco, unlockedBy({ completed: 99, collected: 99, attendanceDays: 99, hasType: true })), deco);
});

test('모양마다 색 표가 따로라 같은 이름의 색도 따로 열린다', () => {
  const open = unlockedBy({ ...NOTHING, completed: 1 }); // 첫 판결: 얇고 긴 띠 + 긴 띠 와인
  assert.ok(open.sashStyles.has('long') && open.sashColors.has('long:wine'));
  assert.ok(!open.sashColors.has('long:navy') && !open.sashColors.has('short:navy'));
  const longNavy = shownDeco({ ...getProfileDeco(memory()), sashStyle: 'long', sashColorLong: 'navy' }, open);
  assert.equal(longNavy.sashStyle, 'long');
  assert.equal(longNavy.sashColorLong, 'wine');
});

test('얇고 긴 띠가 잠겼으면 책갈피는 없음으로, 아이콘은 짧은 책갈피에만 달린다', () => {
  const base = { ...getProfileDeco(memory()), sashStyle: 'long', sashIconLeft: 'scale', sashIconRight: 'gavel' };
  assert.equal(shownDeco(base, unlockedBy(NOTHING)).sashStyle, 'none');
  const all = unlockedBy({ completed: 99, collected: 99, attendanceDays: 99, hasType: true });
  const long = shownDeco(base, all);
  assert.equal(long.sashStyle, 'long');
  assert.equal(long.sashIconLeft, 'none');
  assert.equal(shownDeco({ ...base, sashStyle: 'short' }, all).sashIconLeft, 'scale');
});

test('localStorage 접근 자체가 막혀도 기본 꾸미기를 돌려준다', () => {
  const original = Object.getOwnPropertyDescriptor(globalThis, 'localStorage');
  Object.defineProperty(globalThis, 'localStorage', { configurable: true, get() { throw new Error('blocked'); } });
  try {
    assert.deepEqual(getProfileDeco(), { portrait: null, frame: 'basic', background: 'cream', sashStyle: 'none', sashColorShort: 'wine', sashColorLong: 'wine', sashIconLeft: 'none', sashIconRight: 'none' });
    assert.doesNotThrow(() => setProfileDeco({ frame: 'wood' }));
  } finally {
    if (original) Object.defineProperty(globalThis, 'localStorage', original);
    else delete globalThis.localStorage;
  }
});

test('portraitLabel: 파일 이름을 읽기 쉬운 이름으로 바꾼다', () => {
  assert.equal(portraitLabel('/images/characters/roles/naero_judge.webp'), '내로 · 판사');
  assert.equal(portraitLabel('/images/characters/icon/generated/nambul_face-cheer.webp'), '남불 · 얼굴 · 응원');
  assert.equal(portraitLabel('/images/characters/mascot/law-gavel_think.webp'), '로(법봉) · 생각');
  assert.equal(portraitLabel('/images/characters/standing/naero.webp'), '내로');
  assert.equal(portraitLabel('/images/characters/scene/nambul_react-happy.webp'), '남불 · 반응 기쁨');
  assert.equal(portraitLabel('/images/characters/scene/naero_cheer-cheer.webp'), '내로 · 응원');
  assert.equal(portraitLabel('/images/characters/adult/law-book_deliver-neutral.webp'), '로(책) · 발표 보통');
  assert.equal(portraitLabel('/images/characters/personality/07.webp'), '성향 07');
  assert.equal(portraitLabel('/images/cases/case-1-origin.webp'), '사건 1 · 발단');
  assert.equal(portraitLabel('/images/cases/reenact-common-2_headline.webp'), '공용 재연 2 · 헤드라인');
  assert.equal(portraitLabel('/images/cases/common-3.webp'), '공용 컷 3');
});
