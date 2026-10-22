// npm test : 유형 코드 → shareId (FE-75 내 결과 지면이 축 점수로 고른 유형을 P-4로 받아 올 때)
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { TYPE_NUMBERS, shareIdOfCode } from './personalityTypes.js';
import { TYPES } from '../mocks/personalityMockData.js';

const ogTypes = JSON.parse(readFileSync(fileURLToPath(new URL('../../og/personality-types.json', import.meta.url)), 'utf8')).types;

test('16유형 모두 서버 시드(og/personality-types.json)의 같은 이름 유형 shareId로 바뀐다', () => {
  assert.equal(Object.keys(TYPE_NUMBERS).length, 16);
  Object.keys(TYPE_NUMBERS).forEach((code) => {
    const seed = ogTypes.find((type) => type.shareId === shareIdOfCode(code));
    assert.ok(seed, `${code} shareId`);
    assert.equal(seed.name, TYPES[code].name, `${code} 이름`);
  });
});

test('모르는 코드 · 코드 없음은 null', () => {
  assert.equal(shareIdOfCode('XXXX'), null);
  assert.equal(shareIdOfCode(null), null);
});
