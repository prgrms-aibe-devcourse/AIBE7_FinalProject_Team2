// npm test : 세 판결 비교 목(API 14)의 내 결과 지면 기록 옵션(FE-75)
import { test, beforeEach } from 'node:test';
import assert from 'node:assert/strict';
import { getComparison } from './comparisonMockApi.js';
import { save as saveExperience } from './mockExperienceStore.js';

// node에는 localStorage가 없어 테스트마다 빈 저장소를 둔다. 기본은 체험 중(판결 전)
beforeEach(() => {
  const data = new Map();
  globalThis.localStorage = { getItem: (key) => data.get(key) ?? null, setItem: (key, value) => data.set(key, String(value)), removeItem: (key) => data.delete(key) };
  saveExperience({ status: 'REVIEWED', lastReviewedStep: 4 });
});

test('기본 호출은 체험 상태를 따진다: 마치지 않았으면 INVALID_STATE, 없는 사건은 CASE_NOT_FOUND', async () => {
  await assert.rejects(getComparison(1), { code: 'INVALID_STATE', currentStatus: 'REVIEWED' });
  await assert.rejects(getComparison(5), { code: 'CASE_NOT_FOUND' });
});

test('기록 보기(record)는 지금 체험 상태 · 사건 번호와 상관없이 비교 목을 돌려준다', async () => {
  for (const caseId of [1, 5]) {
    const data = await getComparison(caseId, { record: true });
    assert.equal(typeof data.preToFinal.preJudgment.label, 'string');
    assert.equal(typeof data.preToFinal.finalJudgmentText, 'string');
    assert.ok(data.judgments.USER && data.judgments.AI && data.judgments.COURT);
  }
});
