import test from 'node:test';
import assert from 'node:assert/strict';
import { casePhoto, commonPhotoAt, commonPhotos, commonStagePhotos, extraPhotosOf } from './newspaper.js';

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

test('commonPhotoAt: 발단 · 결론 자리는 전용 공용 컷', () => {
  assert.equal(commonPhotoAt(0, 'origin'), commonStagePhotos.origin);
  assert.equal(commonPhotoAt(7, 'conclusion'), commonStagePhotos.conclusion);
});

test('extraPhotosOf: 비어 있는 자리는 발단 · 결론 공용 컷으로 채운다', () => {
  assert.deepEqual(extraPhotosOf(undefined).map((p) => p.src), [commonStagePhotos.origin, commonStagePhotos.conclusion]);
  assert.equal(extraPhotosOf([{ src: '/a.webp' }])[0].src, '/a.webp');
  assert.equal(extraPhotosOf([{ src: '/a.webp' }])[1].src, commonStagePhotos.conclusion);
});
