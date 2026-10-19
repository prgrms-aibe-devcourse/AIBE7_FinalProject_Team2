// npm test : 사건 리뷰(독자 엽서) 목 API 계약 (FE-55). 실제 API를 붙일 때 이 규칙을 기준으로 맞춘다
import { test, beforeEach } from 'node:test';
import assert from 'node:assert/strict';
import { COMMENT_MAX, getCaseReviews, postCaseReview, resetCaseReviewMock } from './caseReviewMockApi.js';
import { save as saveExperience } from './mockExperienceStore.js';

// node에는 localStorage가 없어 테스트마다 빈 저장소를 둔다. 기본은 체험을 끝까지 마친 상태
beforeEach(async () => {
  const data = new Map();
  globalThis.localStorage = { getItem: (key) => data.get(key) ?? null, setItem: (key, value) => data.set(key, String(value)), removeItem: (key) => data.delete(key) };
  await resetCaseReviewMock();
  saveExperience({ status: 'COMPLETED', lastReviewedStep: 4 });
});

test('없는 사건 번호는 CASE_NOT_FOUND', async () => {
  await assert.rejects(getCaseReviews(0), { code: 'CASE_NOT_FOUND' });
  await assert.rejects(postCaseReview(-1, { rating: 5, comment: '좋아요' }), { code: 'CASE_NOT_FOUND' });
});

test('체험을 마치지 않았으면 EXPERIENCE_NOT_COMPLETED (코드 이름은 BE 협의 전 임시)', async () => {
  saveExperience({ status: 'AI_REVEALED', lastReviewedStep: 4 });
  await assert.rejects(postCaseReview(1, { rating: 5, comment: '좋아요' }), { code: 'EXPERIENCE_NOT_COMPLETED' });
  assert.equal((await getCaseReviews(1)).mine, null); // 읽기는 체험과 상관없이 된다
});

test('별점은 1 ~ 5 정수, 의견은 공백을 뺀 1 ~ 200자', async () => {
  for (const rating of [0, 6, 3.5, '5', undefined]) {
    await assert.rejects(postCaseReview(1, { rating, comment: '좋아요' }), { code: 'VALIDATION_ERROR' });
  }
  for (const comment of ['', '   ', 'a'.repeat(COMMENT_MAX + 1), undefined]) {
    await assert.rejects(postCaseReview(1, { rating: 4, comment }), { code: 'VALIDATION_ERROR' });
  }
  const saved = await postCaseReview(1, { rating: 4, comment: `  ${'a'.repeat(COMMENT_MAX)}  ` });
  assert.equal(saved.comment.length, COMMENT_MAX); // 앞뒤 공백은 지우고 센다
});

test('한 사건에 한 장만 보낼 수 있다(ALREADY_REVIEWED), 다른 사건은 따로 보낸다', async () => {
  await postCaseReview(1, { rating: 5, comment: '첫 엽서' });
  await assert.rejects(postCaseReview(1, { rating: 1, comment: '다시 보내기' }), { code: 'ALREADY_REVIEWED' });
  await postCaseReview(2, { rating: 3, comment: '다른 사건' });
  assert.equal((await getCaseReviews(1)).mine.comment, '첫 엽서');
});

test('평균 · 개수에는 내 엽서가 들어가고, 다른 독자 목록에는 들어가지 않는다', async () => {
  const before = await getCaseReviews(1);
  assert.equal(before.mine, null);
  await postCaseReview(1, { rating: 1, comment: '아쉬워요' });
  const after = await getCaseReviews(1);
  assert.equal(after.count, before.count + 1);
  assert.equal(after.reviews.length, before.reviews.length);
  const sum = before.averageRating * before.count + 1;
  assert.equal(after.averageRating, Math.round((sum / after.count) * 10) / 10);
});
