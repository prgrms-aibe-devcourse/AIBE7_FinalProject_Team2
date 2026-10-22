// npm test : 환경 설정(FE-83). 기본값, 모르는 값 무시, 저장 · 불러오기, 되돌리기
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { SETTING_GROUPS, getSettings, normalizeSettings, resetSettings, setSetting } from './appSettings.js';

const memory = () => {
  const map = new Map();
  return { getItem: (key) => map.get(key) ?? null, setItem: (key, value) => map.set(key, String(value)) };
};

test('기본값: 저장된 게 없으면 항목 표의 기본값이다', () => {
  const settings = getSettings(memory());
  assert.equal(settings.textSize, 'normal');
  assert.equal(settings.sfx, true);
  assert.equal(settings.bgm, false);
  assert.equal(Object.keys(settings).length, SETTING_GROUPS.flatMap((group) => group.items).length);
});

test('setSetting: 고른 항목만 바꿔 저장하고, 다시 읽으면 그대로다', () => {
  const storage = memory();
  setSetting('sfx', false, storage);
  const next = setSetting('textSize', 'large', storage);
  assert.equal(next.sfx, false);
  assert.equal(next.textSize, 'large');
  assert.deepEqual(getSettings(storage), next);
});

test('setSetting: 모르는 항목 · 맞지 않는 값은 무시한다', () => {
  const storage = memory();
  const before = getSettings(storage);
  assert.deepEqual(setSetting('nope', true, storage), before);
  assert.deepEqual(setSetting('sfx', 'yes', storage), before);
  assert.deepEqual(setSetting('textSize', 'huge', storage), before);
});

test('normalizeSettings: 망가진 저장값은 항목별로 기본으로 본다', () => {
  assert.equal(normalizeSettings({ sfx: 'x', textSize: 'large' }).sfx, true);
  assert.equal(normalizeSettings({ sfx: 'x', textSize: 'large' }).textSize, 'large');
  assert.deepEqual(getSettings({ getItem: () => '{깨짐' }), normalizeSettings(null));
});

test('resetSettings: 모두 기본으로 되돌린다', () => {
  const storage = memory();
  setSetting('bgm', true, storage);
  resetSettings(storage);
  assert.equal(getSettings(storage).bgm, false);
});
