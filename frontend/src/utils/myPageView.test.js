// npm test : 마이페이지 변화 흐름 계산
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { axisSeries, collectionOf, trailLeft, trailSummary } from './myPageView.js';

const AXIS = { key: 'APOLOGY_SINCERITY', strict: { name: '행동증명형' }, lenient: { name: '마음신뢰형' } };
const entry = (value) => ({ axisScores: { APOLOGY_SINCERITY: value } });

test('값이 없는 체험은 건너뛰지만 체험 번호는 유지한다', () => {
  const points = axisSeries([entry(1), entry(null), entry(0.5)], AXIS.key);
  assert.deepEqual(points, [{ order: 1, ratio: 1 }, { order: 3, ratio: 0.5 }]);
});

test('기록이 없거나 모양이 달라도 빈 배열을 돌려준다', () => {
  assert.deepEqual(axisSeries(undefined, AXIS.key), []);
  assert.deepEqual(axisSeries([{}, null], AXIS.key), []);
});

test('첫 값과 마지막 값으로 움직인 쪽과 크기를 말한다', () => {
  assert.equal(trailSummary(axisSeries([entry(0.2), entry(0.5), entry(0.75)], AXIS.key), AXIS), '처음보다 마음신뢰형 쪽으로 55%p 움직였어요');
  assert.equal(trailSummary(axisSeries([entry(0.8), entry(0.4)], AXIS.key), AXIS), '처음보다 행동증명형 쪽으로 40%p 움직였어요');
});

test('5%p보다 작게 움직이면 거의 같다고 하고, 값이 하나뿐이면 말하지 않는다', () => {
  assert.equal(trailSummary(axisSeries([entry(0.5), entry(0.53)], AXIS.key), AXIS), '처음과 거의 같아요');
  assert.equal(trailSummary(axisSeries([entry(0.5)], AXIS.key), AXIS), null);
});

test('점 위치는 막대처럼 왼쪽이 엄격 쪽이다', () => {
  assert.equal(trailLeft(0), 100);
  assert.equal(trailLeft(1), 0);
  assert.equal(trailLeft(0.25), 75);
});

test('collectionOf: 체험을 마친 사건의 일러스트만 모으고 같은 그림은 한 장으로 센다', () => {
  const scripts = {
    1: {
      paper: { photo: 'a.png', extraPhotos: [{ src: 'b.webp' }] },
      evidencePhotos: { 검사: 'a.png', 변호인: 'c.png' },
    },
  };
  const experiences = [
    { caseId: 1, title: '사건', status: 'COMPLETED' },
    { caseId: 2, title: '대본 없음', status: 'COMPLETED' },
    { caseId: 1, title: '진행 중', status: 'IN_PROGRESS' },
  ];
  const [group, ...rest] = collectionOf(experiences, scripts);
  assert.equal(rest.length, 0);
  assert.deepEqual(group.items.map((item) => item.src), ['a.png', 'b.webp', 'c.png']);
  assert.deepEqual(collectionOf([], scripts), []);
});
