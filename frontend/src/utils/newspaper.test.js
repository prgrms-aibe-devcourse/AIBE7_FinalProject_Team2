import test from 'node:test';
import assert from 'node:assert/strict';
import { casePhoto, commonPhotoAt, commonPhotos, commonStagePhotos, evidencePhotosFromPaper, extraPhotosFromApi, extraPhotosOf, fetchCasePaper } from './newspaper.js';

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

// FE-64 (PR #284 리뷰): 매핑이 뒤바뀌어도 화면에서는 바로 알아채기 어려워 테스트로 고정한다
test('증거조사: 검사 = 결론 사진, 변호인 = 발단 사진 (FE-64)', () => {
  const paper = { extraPhotos: { origin: { imageUrl: '/o.jpg' }, conclusion: { imageUrl: '/c.jpg' } } };
  assert.deepEqual(evidencePhotosFromPaper(paper), { 검사: '/c.jpg', 변호인: '/o.jpg' });
  assert.equal(evidencePhotosFromPaper(null), undefined); // 지면을 못 받음 → 대본의 고정 사진을 그대로 쓴다
  assert.deepEqual(evidencePhotosFromPaper({ extraPhotos: { origin: null, conclusion: null } }), {}); // 받았지만 비어 있음 → 공용 컷
});

test('API 17 extraPhotos → 발단 · 결론 배열 (빈 자리는 undefined로 둬 공용 컷에 맡긴다)', () => {
  assert.deepEqual(extraPhotosFromApi({ origin: null, conclusion: { imageUrl: '/c.jpg', caption: 'c' } }),
    [undefined, { src: '/c.jpg', caption: 'c' }]);
  assert.deepEqual(extraPhotosFromApi(undefined), [undefined, undefined]);
});

test('fetchCasePaper: 지면이 없거나(404) 응답이 늦으면 null을 주고 화면을 막지 않는다 (FE-64)', async () => {
  const paper = { headline: '제목' };
  assert.equal(await fetchCasePaper({ getCasePaper: async () => paper }, 1), paper);
  assert.equal(await fetchCasePaper({ getCasePaper: () => Promise.reject({ code: 'PAPER_NOT_FOUND' }) }, 1), null);
  assert.equal(await fetchCasePaper({ getCasePaper: () => new Promise(() => {}) }, 1, 10), null); // 영영 끝나지 않는 응답
});
