// npm test : 사전 판단 구간과 실제 판결의 비교(8막 독자 의견란 편집부 의견)
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { comparePreToCourt, editorNote } from './preJudgmentCompare.js';

const prison = (months, extra = {}) => ({ penaltyType: 'PRISON', prisonMonths: months, suspensionMonths: null, reducedTo: null, ...extra });

test('같은 구간이면 SAME, 구간 밖이면 사전 판단이 무겁거나 가볍다', () => {
  assert.equal(comparePreToCourt('실형 5년 이상 ~ 10년 미만', prison(120)), 'PRE_LIGHTER', '10년은 구간 상한 이상이라 사전 판단이 더 가볍다');
  assert.equal(comparePreToCourt('실형 5년 이상 ~ 10년 미만', prison(60)), 'SAME');
  assert.equal(comparePreToCourt('실형 10년 이상 ~ 20년 미만', prison(120)), 'SAME');
  assert.equal(comparePreToCourt('실형 10년 이상 ~ 20년 미만', prison(36)), 'PRE_HEAVIER');
  assert.equal(comparePreToCourt('실형 3년 미만', prison(18)), 'SAME');
  assert.equal(comparePreToCourt('실형 20년 이상', prison(300)), 'SAME');
});

test('형벌 종류가 다르면 종류 순서(집행유예 < 실형 < 무기 < 사형)로 비교한다', () => {
  assert.equal(comparePreToCourt('무기징역', prison(120)), 'PRE_HEAVIER');
  assert.equal(comparePreToCourt('사형', { penaltyType: 'LIFE' }), 'PRE_HEAVIER');
  assert.equal(comparePreToCourt('징역형 집행유예', prison(24, { suspensionMonths: 36 })), 'SAME');
  assert.equal(comparePreToCourt('징역형 집행유예', prison(24)), 'PRE_LIGHTER');
  assert.equal(comparePreToCourt('무기징역', { penaltyType: 'DEATH', reducedTo: 'LIFE' }), 'SAME', '감경되면 선고 형벌로 비교한다');
});

test('읽을 수 없는 이름이면 의견 줄을 만들지 않는다', () => {
  assert.equal(comparePreToCourt('알 수 없는 구간', prison(60)), null);
  assert.equal(editorNote('알 수 없는 구간', prison(60)), null);
  assert.equal(editorNote('실형 5년 이상 ~ 10년 미만', prison(60)), '실제 사건과 비슷하게 판단했어요.');
});
