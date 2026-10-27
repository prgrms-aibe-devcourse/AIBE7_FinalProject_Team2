// npm test : 첫인상(사전 판단)과 내 최종 판결의 비교(6막 독자 의견란 편집부 의견, FE-98) — 서버 preToFinal.direction을 그대로 읽는다. preToCourt(목 계산)는 API 계약이라 계속 점검한다
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { editorNote } from './preJudgmentCompare.js';
import { buildPreToCourt } from '../mocks/comparisonMock.js';
import { mockOverview } from '../mocks/overviewMockData.js';

const prison = (months, extra = {}) => ({ penaltyType: 'PRISON', prisonMonths: months, suspensionMonths: null, reducedTo: null, ...extra });
// 구간 4 = 실형 5 ~ 10년, 5 = 10 ~ 20년, 2 = 3년 미만, 1 = 집행유예, 7 = 무기징역, 8 = 사형
const direction = (rangeOptionId, court) => buildPreToCourt(court, { rangeOptionId }, mockOverview.rangeOptions).direction;

test('최종 판결이 처음 생각보다 무거우면 무겁게 판결했다고 적는다(실제 판결을 잣대로 삼지 않는다)', () => {
  assert.equal(editorNote('HEAVIER'), '처음 생각보다 무겁게 판결했어요.');
  assert.equal(editorNote('LIGHTER'), '처음 생각보다 가볍게 판결했어요.');
  assert.equal(editorNote('SAME'), '처음 생각한 정도로 판결했어요.');
  for (const direction of ['HEAVIER', 'LIGHTER', 'SAME']) assert.doesNotMatch(editorNote(direction), /실제/);
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
