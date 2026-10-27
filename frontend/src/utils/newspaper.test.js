import test from 'node:test';
import assert from 'node:assert/strict';
import { casePhoto, commonCategoryOf, commonPhotoAt, commonPhotos, evidencePhotosFromPaper, extraPhotosFromApi, extraPhotosOf, fetchCasePaper } from './newspaper.js';

test('commonCategoryOf: 범죄 분류 라벨 → 죄명 순으로 공용 컷 분류를 읽고, 모르면 undefined', () => {
  assert.equal(commonCategoryOf({ crimeCategoryLabel: '생명범죄' }), 'life');
  assert.equal(commonCategoryOf({ crimeCategoryLabel: '재산범죄', crimeType: 'INJURY' }), 'property');
  assert.equal(commonCategoryOf({ crimeType: 'ROBBERY' }), 'property');
  assert.equal(commonCategoryOf({ crimeType: 'INJURY' }), 'body');
  assert.equal(commonCategoryOf({ crimeType: 'MURDER' }), 'life');
  assert.equal(commonCategoryOf({}), undefined);
  assert.equal(commonCategoryOf(undefined), undefined);
});

test('commonPhotoAt: 분류 × 자리로 공용 컷을 고른다(자리를 안 주면 대표)', () => {
  assert.equal(commonPhotoAt(0, 'reenact', 'life'), '/images/cases/common-life-reenact.webp');
  assert.equal(commonPhotoAt(0, 'origin', 'body'), commonPhotos.body.origin);
  assert.equal(commonPhotoAt(9, 'conclusion', 'property'), commonPhotos.property.conclusion);
  assert.equal(commonPhotoAt(0, undefined, 'life'), commonPhotos.life.reenact);
  assert.equal(commonPhotoAt(0, 'nope', 'life'), commonPhotos.life.reenact);
});

test('commonPhotoAt: 분류를 모르면 최후의 fallback으로 seed를 분류 세트에 돌려 쓴다', () => {
  assert.equal(commonPhotoAt(0), commonPhotos.property.reenact);
  assert.equal(commonPhotoAt(1), commonPhotos.body.reenact);
  assert.equal(commonPhotoAt(2, 'origin'), commonPhotos.life.origin);
  assert.equal(commonPhotoAt(3), commonPhotos.property.reenact);
  assert.equal(commonPhotoAt(undefined), commonPhotos.property.reenact);
  assert.equal(commonPhotoAt(1, 'reenact', 'unknown'), commonPhotos.body.reenact);
});

test('casePhoto: 서버가 준 썸네일이 우선, 없으면 범죄 분류의 대표 공용 컷, 분류도 모르면 사건 번호 순환', () => {
  assert.equal(casePhoto({ caseId: 5, thumbnailUrl: '/a.webp', crimeType: 'MURDER' }), '/a.webp');
  assert.equal(casePhoto({ caseId: 5, crimeCategoryLabel: '생명범죄' }), commonPhotos.life.reenact);
  assert.equal(casePhoto({ caseId: 4, crimeType: 'INJURY' }), commonPhotos.body.reenact);
  assert.equal(casePhoto({ caseId: 4 }), commonPhotos.body.reenact);
  assert.equal(casePhoto(undefined), commonPhotos.property.reenact);
});

test('extraPhotosOf: 비어 있는 자리는 분류에 맞는 발단 · 결론 공용 컷으로 채운다', () => {
  assert.deepEqual(extraPhotosOf(undefined, 'life').map((p) => p.src), [commonPhotos.life.origin, commonPhotos.life.conclusion]);
  assert.deepEqual(extraPhotosOf(undefined).map((p) => p.src), [commonPhotos.property.origin, commonPhotos.property.conclusion]);
  assert.deepEqual(extraPhotosOf(undefined, undefined, 4).map((p) => p.src), [commonPhotos.body.origin, commonPhotos.body.conclusion]); // 분류를 모르면 casePhoto와 같은 seed(사건 번호)
  assert.equal(extraPhotosOf([{ src: '/a.webp' }], 'body')[0].src, '/a.webp');
  assert.equal(extraPhotosOf([{ src: '/a.webp' }], 'body')[1].src, commonPhotos.body.conclusion);
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
