import test from 'node:test';
import assert from 'node:assert/strict';
import { casePhoto, commonPhotoAt, commonPhotos } from './newspaper.js';

test('commonPhotoAt: seed로 공용 컷을 고정하고, 범위를 넘어도 돌려 쓴다', () => {
  assert.equal(commonPhotoAt(0), commonPhotos[0]);
  assert.equal(commonPhotoAt(1), commonPhotos[1]);
  assert.equal(commonPhotoAt(commonPhotos.length), commonPhotos[0]);
  assert.equal(commonPhotoAt(undefined), commonPhotos[0]);
});

test('casePhoto: 썸네일이 있으면 그것, 없으면 사건 번호로 고정한 공용 컷', () => {
  assert.equal(casePhoto({ caseId: 5, thumbnailUrl: '/a.webp' }), '/a.webp');
  assert.equal(casePhoto({ caseId: 4 }), commonPhotos[1]);
});
