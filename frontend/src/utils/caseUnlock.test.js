import test from 'node:test';
import assert from 'node:assert/strict';
import { casesUntilNextStep, isCaseLocked, isLocked } from './caseUnlock.js';

test('isLocked: locked가 true일 때만 잠김이다 (필드가 없으면 열린 사건)', () => {
  assert.equal(isLocked({ locked: true }), true);
  assert.equal(isLocked({ locked: false }), false);
  assert.equal(isLocked({}), false);
  assert.equal(isLocked(undefined), false);
});

test('casesUntilNextStep: 다음 단계까지 남은 사건 수, 마지막 단계 · 값 없음은 0', () => {
  assert.equal(casesUntilNextStep({ completedCases: 0, nextStepAt: 1 }), 1);
  assert.equal(casesUntilNextStep({ completedCases: 1, nextStepAt: 3 }), 2);
  assert.equal(casesUntilNextStep({ completedCases: 5, nextStepAt: null }), 0);
  assert.equal(casesUntilNextStep(undefined), 0);
  assert.equal(casesUntilNextStep({ completedCases: 4, nextStepAt: 3 }), 0, '음수가 되지 않는다');
});

test('isCaseLocked: API 2의 409 CASE_LOCKED만 알아본다', () => {
  assert.equal(isCaseLocked({ code: 'CASE_LOCKED' }), true);
  assert.equal(isCaseLocked({ code: 'CASE_NOT_FOUND' }), false);
  assert.equal(isCaseLocked(null), false);
});
