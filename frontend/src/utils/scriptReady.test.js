import { test } from 'node:test';
import assert from 'node:assert/strict';
import { isScriptReady, scriptStatusOf } from './scriptReady.js';

test('서버 값이 있으면 로컬 대본과 상관없이 그 값을 따른다', () => {
  assert.equal(isScriptReady({ scriptReady: true }, undefined), true);
  assert.equal(isScriptReady({ scriptReady: false }, { title: '로컬 대본' }), false);
});

test('서버 값이 없으면 로컬 대본이 있을 때만 체험 가능이다', () => {
  assert.equal(isScriptReady({}, { title: '로컬 대본' }), true);
  assert.equal(isScriptReady({ scriptReady: null }, undefined), false);
  assert.equal(isScriptReady(undefined, undefined), false);
});

test('scriptStatus가 있으면 READY만 체험 가능이고, 없으면 scriptReady · 로컬 대본으로 본다 (FE-145)', () => {
  assert.equal(isScriptReady({ scriptStatus: 'READY', scriptReady: false }, undefined), true);
  assert.equal(isScriptReady({ scriptStatus: 'MAINTENANCE', scriptReady: true }, { title: 'x' }), false);
  assert.equal(scriptStatusOf({ scriptStatus: 'MAINTENANCE' }), 'MAINTENANCE');
  assert.equal(scriptStatusOf({ scriptReady: false }), 'PREPARING');
  assert.equal(scriptStatusOf({ scriptStatus: '???', scriptReady: true }), 'PREPARING'); // 모르는 값은 막는 쪽
});
