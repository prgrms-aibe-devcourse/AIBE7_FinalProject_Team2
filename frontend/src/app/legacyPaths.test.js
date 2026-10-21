// npm test : 옛 주소 redirect 표(FE-58, BE-82 주소표)
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { legacyPath } from './legacyPaths.js';

test('옛 체험 주소를 새 주소로 옮긴다', () => {
  assert.equal(legacyPath('/cases/1/start'), '/cases/1/impression');
  assert.equal(legacyPath('/cases/1/review'), '/cases/1/trial');
  assert.equal(legacyPath('/cases/1/summary'), '/cases/1/verdict');
  assert.equal(legacyPath('/cases/1/result/ai'), '/cases/1/ai-verdict');
  assert.equal(legacyPath('/cases/1/result/court'), '/cases/1/court-verdict');
  assert.equal(legacyPath('/cases/1/result/compare'), '/cases/1/compare');
  assert.equal(legacyPath('/cases/12/opinion/'), '/cases/12/letters');
});

test('새 주소 · 다른 주소는 그대로 둔다', () => {
  for (const path of ['/cases/1/verdict', '/cases/1/trial', '/cases/1', '/review', '/cases/1/result', '/cases/1/start/x']) {
    assert.equal(legacyPath(path), null);
  }
});
