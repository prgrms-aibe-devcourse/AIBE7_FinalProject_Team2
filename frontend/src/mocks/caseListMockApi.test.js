// npm test : API 3 내 체험 상태 목 (FE-58, S-09 리뷰 페이지 진입 확인)
import { test, beforeEach } from 'node:test';
import assert from 'node:assert/strict';
import { getExperience, startExperience } from './caseListMockApi.js';
import { save as saveExperience, storageKey } from './mockExperienceStore.js';

// node에는 localStorage가 없어 테스트마다 빈 저장소를 둔다
beforeEach(() => {
  const data = new Map();
  globalThis.localStorage = { getItem: (key) => data.get(key) ?? null, setItem: (key, value) => data.set(key, String(value)), removeItem: (key) => data.delete(key) };
});

test('체험 기록이 없으면 EXPERIENCE_NOT_FOUND, 기록을 새로 만들지 않는다', async () => {
  await assert.rejects(getExperience(1), { code: 'EXPERIENCE_NOT_FOUND' });
  assert.equal(localStorage.getItem(storageKey), null);
  await assert.rejects(getExperience(2), { code: 'EXPERIENCE_NOT_FOUND' });
});

test('기록이 있으면 지금 상태를 돌려준다', async () => {
  saveExperience({ status: 'COMPLETED', lastReviewedStep: 4 });
  assert.equal((await getExperience(1)).status, 'COMPLETED');
  await startExperience(3);
  assert.equal((await getExperience(3)).status, 'STARTED');
});

test('없는 사건 번호는 CASE_NOT_FOUND', async () => {
  await assert.rejects(getExperience(999999), { code: 'CASE_NOT_FOUND' });
});
