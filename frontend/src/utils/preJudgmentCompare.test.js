// npm test : 사전 판단과 실제 판결의 비교(6막 독자 의견란 편집부 의견) — 서버 direction(재판부 기준)을 사용자 관점 문구로 뒤집는다
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { editorNote } from './preJudgmentCompare.js';
import { buildPreToCourt } from '../mocks/comparisonMock.js';
import { mockOverview } from '../mocks/overviewMockData.js';

const prison = (months, extra = {}) => ({ penaltyType: 'PRISON', prisonMonths: months, suspensionMonths: null, reducedTo: null, ...extra });
// 구간 4 = 실형 5 ~ 10년, 5 = 10 ~ 20년, 2 = 3년 미만, 1 = 집행유예, 7 = 무기징역, 8 = 사형
const direction = (rangeOptionId, court) => buildPreToCourt(court, { rangeOptionId }, mockOverview.rangeOptions).direction;

test('재판부가 더 무거우면 사용자는 가볍게 판단한 것이다', () => {
  assert.equal(editorNote('HEAVIER'), '실제 사건보다 가볍게 판단했어요.');
  assert.equal(editorNote('LIGHTER'), '실제 사건보다 무겁게 판단했어요.');
  assert.equal(editorNote('SAME'), '실제 사건과 비슷하게 판단했어요.');
});

test('모르는 값이거나 비어 있으면 의견 줄을 만들지 않는다', () => {
  assert.equal(editorNote(undefined), null);
  assert.equal(editorNote(null), null);
  assert.equal(editorNote('알 수 없음'), null);
});

test('목 preToCourt: 같은 구간이면 SAME, 구간 밖이면 재판부가 무겁거나 가볍다', () => {
  assert.equal(direction(4, prison(60)), 'SAME');
  assert.equal(direction(4, prison(120)), 'HEAVIER', '10년은 구간 상한 이상이라 재판부가 더 무겁다');
  assert.equal(direction(5, prison(36)), 'LIGHTER');
  assert.equal(direction(2, prison(18)), 'SAME');
});

test('목 preToCourt: 형벌 종류가 다르면 종류로 비교하고, 감경은 선고 형벌로 본다', () => {
  assert.equal(direction(7, prison(120)), 'LIGHTER');
  assert.equal(direction(8, { penaltyType: 'LIFE' }), 'LIGHTER');
  assert.equal(direction(1, prison(24, { suspensionMonths: 36 })), 'SAME');
  assert.equal(direction(1, prison(24)), 'HEAVIER');
  assert.equal(direction(7, { penaltyType: 'DEATH', reducedTo: 'LIFE' }), 'SAME');
});
