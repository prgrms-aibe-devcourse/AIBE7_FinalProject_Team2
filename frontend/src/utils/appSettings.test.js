// npm test : 환경 설정(FE-83). 기본값, 모르는 값 무시, 저장 · 불러오기, 초기화 값
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { SETTING_GROUPS, defaultSettings, getSettings, normalizeSettings, saveSettings } from './appSettings.js';

const memory = () => {
  const map = new Map();
  return { getItem: (key) => map.get(key) ?? null, setItem: (key, value) => map.set(key, String(value)) };
};

test('기본값: 저장된 게 없으면 항목 표의 기본값이다', () => {
  const settings = getSettings(memory());
  assert.equal(settings.textSize, 'normal');
  assert.equal(settings.notifyAchievement, true);
  assert.equal(settings.notifyNews, false);
  assert.equal(Object.keys(settings).length, SETTING_GROUPS.flatMap((group) => group.items).length);
  assert.deepEqual(settings, defaultSettings());
});

test('항목 표에 소리 묶음은 없다(지원하지 않는다)', () => {
  assert.deepEqual(SETTING_GROUPS.map((group) => group.id), ['display', 'notice']);
});

test('saveSettings: 저장한 값을 다시 읽으면 그대로다', () => {
  const storage = memory();
  const next = saveSettings({ ...defaultSettings(), textSize: 'large', notifyNews: true }, storage);
  assert.equal(next.textSize, 'large');
  assert.equal(next.notifyNews, true);
  assert.deepEqual(getSettings(storage), next);
});

test('saveSettings: 모르는 항목은 버리고 맞지 않는 값은 기본으로 본다', () => {
  const saved = saveSettings({ nope: true, notifyNews: 'yes', textSize: 'huge' }, memory());
  assert.equal(saved.nope, undefined);
  assert.equal(saved.notifyNews, false);
  assert.equal(saved.textSize, 'normal');
});

test('normalizeSettings: 망가진 저장값은 항목별로 기본으로 본다', () => {
  assert.equal(normalizeSettings({ notifyAchievement: 'x', textSize: 'large' }).notifyAchievement, true);
  assert.equal(normalizeSettings({ notifyAchievement: 'x', textSize: 'large' }).textSize, 'large');
  assert.deepEqual(getSettings({ getItem: () => '{깨짐' }), normalizeSettings(null));
});
