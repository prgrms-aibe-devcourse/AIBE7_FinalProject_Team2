// npm test : 꾸미기 저장(P-7) · 출석 기록(P-6) 목 API가 서버 계약(BE-96 · BE-97)을 따르는지 확인 (FE-71)
import { test } from 'node:test';
import assert from 'node:assert/strict';

// 목은 저장 기록을 localStorage에 둔다. 테스트마다 비운 저장소를 쓴다
const data = new Map();
Object.defineProperty(globalThis, 'localStorage', {
  configurable: true,
  value: { getItem: (key) => data.get(key) ?? null, setItem: (key, value) => data.set(key, String(value)), removeItem: (key) => data.delete(key) },
});
const { getMyPage } = await import('./personalityMockApi.js');
const { postAttendance, putProfileDeco, setStatsReader } = await import('./profileDecoMockApi.js');

const DEFAULT = {
  portrait: '/images/characters/standing/naero.webp', frame: 'basic', background: 'cream', sashStyle: 'none', sashColorShort: 'wine', sashColorLong: 'wine',
  sashIconLeft: 'none', sashIconRight: 'none', bookmarkCharacter: 'naero', bookmarkFace: 'happy', bookmarkFaceStyle: 'generated', bookmarkColor: 'bronze',
};
const reset = (stats = { completed: 0, hasType: 0 }) => {
  data.clear();
  setStatsReader(() => stats);
};

test('P-5: 저장한 적이 없으면 기본 꾸미기와 도전과제 8개 · 열린 항목이 온다', async () => {
  reset();
  const page = await getMyPage();
  assert.deepEqual(page.profileDeco, DEFAULT);
  assert.equal(page.achievements.length, 8);
  assert.deepEqual(Object.keys(page.unlocked).sort(), ['backgrounds', 'frames', 'kinds', 'sashColors', 'sashIcons', 'sashStyles']);
  assert.equal(typeof page.profile.attendanceDays, 'number');
  const collect = page.achievements.find((item) => item.stat === 'collected');
  assert.deepEqual([collect.status, collect.value, collect.cleared], ['PENDING', null, false]);
});

test('P-6: 같은 날 여러 번 불러도 출석은 하루로 센다', async () => {
  reset();
  const before = (await getMyPage()).profile.attendanceDays;
  assert.equal(await postAttendance(), null);
  await postAttendance();
  assert.equal((await getMyPage()).profile.attendanceDays, before + 1);
});

test('P-7: 열린 것만 고르면 저장되고, 저장한 값이 P-5에 반영된다', async () => {
  reset();
  const deco = { ...DEFAULT, frame: 'gold', bookmarkCharacter: 'nambul', bookmarkFace: 'thinking', bookmarkFaceStyle: 'cropped', bookmarkColor: 'green' };
  const saved = await putProfileDeco(deco);
  assert.deepEqual(saved, deco);
  assert.deepEqual((await getMyPage()).profileDeco, deco);
});

test('P-7: 잠긴 항목은 409 DECO_LOCKED이고 잠긴 필드가 details에 오며, 아무것도 저장하지 않는다', async () => {
  reset();
  const error = await putProfileDeco({ ...DEFAULT, frame: 'wood', background: 'tarot', bookmarkColor: 'green' }).catch((e) => e);
  assert.equal(error.code, 'DECO_LOCKED');
  assert.equal(error.status, 409);
  assert.deepEqual(error.details, [{ field: 'frame', reason: 'DECO_LOCKED' }, { field: 'background', reason: 'DECO_LOCKED' }]);
  assert.deepEqual((await getMyPage()).profileDeco, DEFAULT, '거절되면 저장하지 않는다');
});

test('P-7: 도전과제를 깨면 같은 요청이 통과한다', async () => {
  reset();
  await assert.rejects(putProfileDeco({ ...DEFAULT, frame: 'wood' }), { code: 'DECO_LOCKED' });
  setStatsReader(() => ({ completed: 1, hasType: 0 }));
  assert.equal((await putProfileDeco({ ...DEFAULT, frame: 'wood' })).frame, 'wood');
});

test('P-7: 지금 화면에 쓰이는 칸만 잠김을 본다', async () => {
  reset();
  // 책갈피가 없음이면 잠긴 색 · 아이콘을 들고 있어도 통과한다
  await putProfileDeco({ ...DEFAULT, sashColorLong: 'navy', sashIconLeft: 'law' });
  // 얇고 긴 띠는 첫 판결 전에는 잠겨 있고, 짧은 책갈피에서는 고른 아이콘이 잠겼는지 본다
  const long = await putProfileDeco({ ...DEFAULT, sashStyle: 'long' }).catch((e) => e);
  assert.deepEqual(long.details.map(({ field }) => field), ['sashStyle', 'sashColorLong']);
  const short = await putProfileDeco({ ...DEFAULT, sashStyle: 'short', sashIconLeft: 'law' }).catch((e) => e);
  assert.deepEqual(short.details.map(({ field }) => field), ['sashIconLeft']);
});

test('P-7: 기본 대표 이미지 두 장은 늘 열려 있고, 잠긴 종류는 거절한다', async () => {
  reset();
  assert.equal((await putProfileDeco({ ...DEFAULT, portrait: '/images/characters/standing/nambul.webp' })).portrait, '/images/characters/standing/nambul.webp');
  const error = await putProfileDeco({ ...DEFAULT, portrait: '/images/characters/mascot/trio_idle.webp' }).catch((e) => e);
  assert.equal(error.code, 'DECO_LOCKED');
  assert.deepEqual(error.details, [{ field: 'portrait', reason: 'DECO_LOCKED' }]);
});

test('P-7: 값이 없거나 목록에 없거나 대표 이미지 모양이 틀리면 400 VALIDATION_ERROR이고 잠김보다 먼저 본다', async () => {
  reset();
  const { frame, ...withoutFrame } = DEFAULT;
  const missing = await putProfileDeco(withoutFrame).catch((e) => e);
  assert.equal(missing.code, 'VALIDATION_ERROR');
  assert.deepEqual(missing.details, [{ field: 'frame', reason: 'REQUIRED' }]);
  const odd = await putProfileDeco({ ...DEFAULT, background: 'rainbow', bookmarkColor: 'pink' }).catch((e) => e);
  assert.deepEqual(odd.details, [{ field: 'background', reason: 'INVALID_VALUE' }, { field: 'bookmarkColor', reason: 'INVALID_VALUE' }]);
  for (const portrait of ['/images/../secret.webp', '//evil.example/a.webp', 'https://evil.example/a.webp', '/images/a/b.png', '/images/a\\b.webp', '/images/characters/scene/a.webp?x=1']) {
    const bad = await putProfileDeco({ ...DEFAULT, frame: 'wood', portrait }).catch((e) => e);
    assert.deepEqual([bad.code, bad.details], ['VALIDATION_ERROR', [{ field: 'portrait', reason: 'INVALID_FORMAT' }]], portrait);
  }
});
