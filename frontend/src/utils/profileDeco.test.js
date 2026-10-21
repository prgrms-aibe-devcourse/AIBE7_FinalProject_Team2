// npm test : 대표 이미지 페이지의 꾸미기 값 · 서버 응답 해석 · 잠긴 꾸미기 보이기 (FE-68, 서버 연동 FE-71)
// 도전과제 판정은 서버가 한다(BE-96). 여기서는 목 서버(mocks/profileDecoMockApi.js)의 응답을 화면 쪽 표와 맞춰 본다.
import { existsSync } from 'node:fs';
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { PORTRAIT_POOL } from '../content/portraitPool.js';
import { ACHIEVEMENTS as SERVER_ACHIEVEMENTS, FREE } from '../mocks/profileDecoMockData.js';
import { mockAchievements, mockUnlocked } from '../mocks/profileDecoMockApi.js';
import { KIND_TITLES } from './myPageView.js';
import {
  ACHIEVEMENT_TEXT, BACKGROUNDS, DEFAULT_PORTRAITS, FRAMES, REWARD_TYPES, SASH_COLORS, SASH_ICONS, SASH_STYLES, achievementView, kindOfPortrait, normalizeDeco, portraitLabel, sashColorKey, shownDeco, unlockedSets, unlockerIn,
} from './profileDeco.js';

const NAERO = '/images/characters/standing/naero.webp';
const NOTHING = { completed: 0, hasType: 0, attendanceDays: 0, collected: null };
const EVERYTHING = { completed: 99, hasType: 1, attendanceDays: 99, collected: 99 };
const openAfter = (stats) => unlockedSets(mockUnlocked(mockAchievements(stats)));
const DEFAULT_LOOK = { portrait: NAERO, frame: 'basic', background: 'cream', sashStyle: 'none', sashColorShort: 'wine', sashColorLong: 'wine', sashIconLeft: 'none', sashIconRight: 'none' };
const DEFAULT_SERVER = { ...DEFAULT_LOOK, bookmarkCharacter: 'naero', bookmarkFace: 'happy', bookmarkFaceStyle: 'generated', bookmarkColor: 'bronze' };

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

test('모든 종류 · 테두리 · 배경 · 책갈피는 처음부터 열려 있거나, 여는 도전과제가 하나 있다', () => {
  const achievements = mockAchievements(NOTHING);
  const open = openAfter(NOTHING);
  const reachable = (type, id) => open[type].has(id) || unlockerIn(achievements, type, id);
  Object.keys(KIND_TITLES).filter((kind) => PORTRAIT_POOL.some((src) => kindOfPortrait(src) === kind)).forEach((kind) => assert.ok(reachable('kinds', kind), `열 방법이 없는 종류: ${kind}`));
  FRAMES.forEach(({ id }) => assert.ok(reachable('frames', id), `열 방법이 없는 테두리: ${id}`));
  BACKGROUNDS.forEach(({ id }) => assert.ok(reachable('backgrounds', id), `열 방법이 없는 배경: ${id}`));
  Object.entries(SASH_COLORS).forEach(([style, colors]) => colors.forEach(({ id }) => assert.ok(reachable('sashColors', sashColorKey(style, id)), `열 방법이 없는 책갈피 색: ${style}:${id}`)));
  SASH_STYLES.forEach(({ id }) => assert.ok(reachable('sashStyles', id), `열 방법이 없는 책갈피 모양: ${id}`));
  SASH_ICONS.forEach(({ id }) => assert.ok(reachable('sashIcons', id), `열 방법이 없는 책갈피 아이콘: ${id}`));
});

test('서버 표의 보상은 모두 화면이 아는 값이다(그림 · 이름이 없는 id가 내려오지 않는다)', () => {
  const known = {
    kinds: new Set(PORTRAIT_POOL.map(kindOfPortrait)),
    frames: new Set(FRAMES.map(({ id }) => id)),
    backgrounds: new Set(BACKGROUNDS.map(({ id }) => id)),
    sashStyles: new Set(SASH_STYLES.map(({ id }) => id)),
    sashColors: new Set(Object.entries(SASH_COLORS).flatMap(([style, colors]) => colors.map(({ id }) => sashColorKey(style, id)))),
    sashIcons: new Set(SASH_ICONS.map(({ id }) => id)),
  };
  [...SERVER_ACHIEVEMENTS.map((item) => item.rewards), FREE].forEach((rewards) => REWARD_TYPES.forEach((type) => rewards[type].forEach((id) => assert.ok(known[type].has(id), `${type}: ${id}`))));
});

test('도전과제마다 제목 · 문구가 있고, 서버에만 있는 새 id는 id를 제목으로 보여 준다', () => {
  SERVER_ACHIEVEMENTS.forEach((item) => assert.ok(ACHIEVEMENT_TEXT[item.id]?.title && ACHIEVEMENT_TEXT[item.id]?.text, item.id));
  const known = achievementView({ id: 'first-case', stat: 'completed', goal: 1, value: 1, cleared: true, status: 'CLEARED', rewards: {} });
  assert.equal(known.title, '첫 판결');
  assert.equal(known.value, 1, '서버가 준 값은 그대로 둔다');
  const unknown = achievementView({ id: 'brand-new', stat: 'completed', goal: 9, value: 0, cleared: false, status: 'IN_PROGRESS', rewards: {} });
  assert.equal(unknown.title, 'brand-new');
});

test('아무것도 안 했으면 역할 의상 · 서 있는 컷 · 기본 테두리 · 크림 배경만 열려 있다', () => {
  const open = openAfter(NOTHING);
  assert.deepEqual([...open.kinds], ['characters/roles', 'characters/standing']);
  assert.deepEqual([...open.frames], ['basic']);
  assert.deepEqual([...open.backgrounds], ['cream']);
  assert.deepEqual([...open.sashStyles], ['none', 'short']);
  assert.deepEqual([...open.sashColors], ['short:wine']);
  assert.deepEqual([...open.sashIcons], ['none', 'star']);
  assert.equal(PORTRAIT_POOL.filter((src) => open.kinds.has(kindOfPortrait(src))).length, 18);
});

test('서버가 알려 준 열린 항목을 Set으로 바꾸고, 빠진 종류는 빈 Set으로 본다', () => {
  const open = unlockedSets({ kinds: ['cases'], frames: ['wood'] });
  assert.ok(open.kinds.has('cases') && open.frames.has('wood'));
  REWARD_TYPES.filter((type) => type !== 'kinds' && type !== 'frames').forEach((type) => assert.equal(open[type].size, 0));
  REWARD_TYPES.forEach((type) => assert.equal(unlockedSets(undefined)[type].size, 0));
});

test('수집 도전과제는 서버가 셀 수 없어 판정 대기(PENDING)이고, 보상은 열리지 않는다', () => {
  const progress = Object.fromEntries(mockAchievements({ ...NOTHING, completed: 5, attendanceDays: 99, hasType: 1 }).map((item) => [item.id, item]));
  assert.equal(progress['collect-5'].status, 'PENDING');
  assert.equal(progress['collect-5'].value, null);
  assert.equal(progress['collect-5'].cleared, false);
  assert.equal(progress['attend-30'].status, 'CLEARED');
  const open = openAfter({ ...NOTHING, completed: 5, attendanceDays: 99, hasType: 1 });
  assert.ok(!open.kinds.has('cases') && !open.kinds.has('characters/law'), '수집 보상은 BE-83 전까지 잠김');
});

test('도전과제를 깨면 보상이 열리고, 진행도는 목표에서 멈춘다', () => {
  const stats = { completed: 1, hasType: 1, attendanceDays: 12, collected: null };
  const progress = Object.fromEntries(mockAchievements(stats).map((item) => [item.id, item]));
  assert.equal(progress['first-case'].cleared, true);
  assert.equal(progress['cases-3'].cleared, false);
  assert.equal(progress['cases-3'].value, 1);
  assert.equal(progress['attend-7'].value, 7);
  const open = openAfter(stats);
  assert.ok(open.kinds.has('characters/scene') && open.kinds.has('characters/personality'));
  assert.ok(!open.kinds.has('characters/mascot'));
  assert.ok(open.frames.has('wood') && open.frames.has('gold') && !open.frames.has('navy'));
  assert.ok(open.backgrounds.has('tarot') && !open.backgrounds.has('linen'));
});

test('도전과제 id는 겹치지 않고, 보상이 하나 이상 있다', () => {
  assert.equal(new Set(SERVER_ACHIEVEMENTS.map((item) => item.id)).size, SERVER_ACHIEVEMENTS.length);
  SERVER_ACHIEVEMENTS.forEach((item) => assert.ok(REWARD_TYPES.some((type) => item.rewards[type].length > 0), item.id));
});

test('unlockerIn: 잠긴 항목을 여는 도전과제를 서버 응답에서 찾는다', () => {
  const achievements = mockAchievements(NOTHING);
  assert.equal(unlockerIn(achievements, 'frames', 'wood').title, '첫 판결');
  assert.equal(unlockerIn(achievements, 'sashColors', 'long:wine').id, 'first-case');
  assert.equal(unlockerIn(achievements, 'frames', 'basic'), null, '처음부터 열린 것은 여는 도전과제가 없다');
  assert.equal(unlockerIn(undefined, 'frames', 'wood'), null);
});

test('서버가 준 꾸미기: 빠진 값 · 모르는 값은 기본으로 보고, 책갈피 설정도 함께 다룬다', () => {
  assert.deepEqual(normalizeDeco(undefined), DEFAULT_SERVER);
  assert.deepEqual(normalizeDeco(null), DEFAULT_SERVER);
  const saved = { ...DEFAULT_SERVER, frame: 'gold', portrait: '/images/characters/scene/naero_idle-neutral.webp', bookmarkCharacter: 'law-gavel', bookmarkFace: 'thinking', bookmarkFaceStyle: 'cropped', bookmarkColor: 'green' };
  assert.deepEqual(normalizeDeco(saved), saved);
  const odd = normalizeDeco({ ...saved, background: 'rainbow', portrait: '/images/elsewhere.webp', sashStyle: 'zigzag', sashIconLeft: 'rocket', bookmarkCharacter: 'ghost', bookmarkColor: 'pink' });
  assert.equal(odd.background, 'cream');
  assert.equal(odd.portrait, NAERO);
  assert.equal(odd.sashStyle, 'none');
  assert.equal(odd.sashIconLeft, 'none');
  assert.equal(odd.bookmarkCharacter, 'naero');
  assert.equal(odd.bookmarkColor, 'bronze');
  assert.equal(odd.frame, 'gold', '알아볼 수 있는 값은 그대로');
});

test('카드 책갈피는 모양마다 색을 따로 기억하고, 고른 값은 다른 것을 바꿔도 그대로다', () => {
  const deco = normalizeDeco({ sashStyle: 'long', sashColorShort: 'navy', sashColorLong: 'gold', frame: 'gold' });
  assert.deepEqual([deco.sashStyle, deco.sashColorShort, deco.sashColorLong, deco.frame], ['long', 'navy', 'gold', 'gold']);
});

test('잠긴 꾸미기를 골라 둔 상태면 화면에는 기본으로 보인다', () => {
  const deco = { portrait: '/images/characters/mascot/trio_idle.webp', frame: 'navy', background: 'tarot', sashStyle: 'short', sashColorShort: 'gold', sashColorLong: 'wine', sashIconLeft: 'law', sashIconRight: 'star' };
  const shown = shownDeco(deco, openAfter(NOTHING));
  assert.equal(shown.portrait, NAERO, '잠긴 대표 이미지는 기본(내로 스탠딩)으로');
  assert.equal(shown.frame, 'basic');
  assert.equal(shown.background, 'cream');
  assert.equal(shown.sashStyle, 'short');
  assert.equal(shown.sashColorShort, 'wine', '짧은 책갈피의 금사가 잠겼으면 열린 첫 색(와인)으로');
  assert.equal(shown.sashIconLeft, 'none');
  assert.equal(shown.sashIconRight, 'star');
  assert.deepEqual(shownDeco(deco, openAfter(EVERYTHING)), deco);
});

test('모양마다 색 표가 따로라 같은 이름의 색도 따로 열린다', () => {
  const open = openAfter({ ...NOTHING, completed: 1 }); // 첫 판결: 얇고 긴 띠 + 긴 띠 와인
  assert.ok(open.sashStyles.has('long') && open.sashColors.has('long:wine'));
  assert.ok(!open.sashColors.has('long:navy') && !open.sashColors.has('short:navy'));
  const longNavy = shownDeco({ ...DEFAULT_LOOK, sashStyle: 'long', sashColorLong: 'navy' }, open);
  assert.equal(longNavy.sashStyle, 'long');
  assert.equal(longNavy.sashColorLong, 'wine');
});

test('얇고 긴 띠가 잠겼으면 책갈피는 없음으로, 아이콘은 짧은 책갈피에만 달린다', () => {
  const base = { ...DEFAULT_LOOK, sashStyle: 'long', sashIconLeft: 'scale', sashIconRight: 'gavel' };
  assert.equal(shownDeco(base, openAfter(NOTHING)).sashStyle, 'none');
  const all = openAfter(EVERYTHING);
  const long = shownDeco(base, all);
  assert.equal(long.sashStyle, 'long');
  assert.equal(long.sashIconLeft, 'none');
  assert.equal(shownDeco({ ...base, sashStyle: 'short' }, all).sashIconLeft, 'scale');
});

test('기본 제공 대표 이미지는 내로 · 남불 스탠딩이고, 처음은 내로 · 언제나 열려 있다', () => {
  assert.deepEqual(DEFAULT_PORTRAITS, [NAERO, '/images/characters/standing/nambul.webp']);
  assert.equal(normalizeDeco(undefined).portrait, NAERO);
  DEFAULT_PORTRAITS.forEach((src) => assert.ok(PORTRAIT_POOL.includes(src)));
  assert.equal(shownDeco({ ...DEFAULT_LOOK, portrait: DEFAULT_PORTRAITS[1] }, openAfter(NOTHING)).portrait, DEFAULT_PORTRAITS[1]);
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
