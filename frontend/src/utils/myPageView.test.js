// npm test : 마이페이지 변화 흐름 계산
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { trendOrders, trendSlotOrders, recordOf, axisChange, axisSeries, axisTrend, castOf, collectionOf, leanText, imageKinds } from './myPageView.js';

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

const FULL_AXIS = { ...AXIS, label: '사과와 진정성' };
const titled = (title, value) => ({ title, axisScores: { APOLOGY_SINCERITY: value } });

test('axisTrend: 테스트 점수를 맨 앞(0번)에 두고 체험 번호와 사건 이름을 붙인다', () => {
  const points = axisTrend([titled('가', 0.5), titled('나', null), titled('다', 0.75)], AXIS.key, { APOLOGY_SINCERITY: 0.25 });
  assert.deepEqual(points.map(({ order, label, isTest }) => [order, label, isTest]), [[0, '테스트', true], [1, '1', false], [3, '3', false]]);
  assert.equal(points[2].title, '3번째 체험 · 다');
  assert.equal(axisTrend([titled('가', 0.5)], AXIS.key, null).length, 1); // 테스트 전이면 체험만
});

test('axisChange: 처음 → 지금 변경폭과 움직인 쪽, 5%p 안쪽은 그대로로 본다', () => {
  const change = axisChange(axisTrend([titled('가', 0.6)], AXIS.key, { APOLOGY_SINCERITY: 0.2 }), FULL_AXIS);
  assert.equal(change.moved, 40);
  assert.equal(change.toward, '마음신뢰형');
  assert.equal(change.from.isTest, true);
  assert.equal(axisChange(axisTrend([titled('가', 0.8), titled('나', 0.5)], AXIS.key), FULL_AXIS).toward, '행동증명형');
  assert.equal(axisChange(axisTrend([titled('가', 0.5), titled('나', 0.52)], AXIS.key), FULL_AXIS).steady, true);
});

test('axisChange: 현재 판결 성향(최근 3건 평균)이 있으면 지금은 그 값, 없으면 가장 최근 체험', () => {
  const points = axisTrend([titled('가', 0.9), titled('나', 0.3)], AXIS.key, { APOLOGY_SINCERITY: 0.5 });
  assert.equal(axisChange(points, FULL_AXIS, 0.6).moved, 10);
  assert.equal(axisChange(points, FULL_AXIS, 0.6).to.isCurrent, true);
  assert.equal(axisChange(points, FULL_AXIS, null).moved, -20);
});

test('axisChange: 점이 하나뿐이거나 체험 없이 테스트만 있으면 null', () => {
  assert.equal(axisChange(axisTrend([titled('가', 0.5)], AXIS.key), FULL_AXIS), null);
  assert.equal(axisChange(axisTrend([], AXIS.key, { APOLOGY_SINCERITY: 0.5 }), FULL_AXIS), null);
});

test('trendOrders: 테스트(없으면 첫 체험) + 최근 10건, 적으면 있는 만큼', () => {
  assert.deepEqual(trendOrders(3, true), [0, 1, 2, 3]);
  assert.deepEqual(trendOrders(3, false), [1, 2, 3]);
  assert.deepEqual(trendOrders(12, true), [0, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12]);
  assert.deepEqual(trendOrders(12, false), [1, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12]);
  assert.deepEqual(trendOrders(0, true), [0]);
});

test('leanText: 더 기운 쪽 이름과 비중, 반반이면 반반', () => {
  assert.equal(leanText(0.75, FULL_AXIS), '마음신뢰형 75%');
  assert.equal(leanText(0.1, FULL_AXIS), '행동증명형 90%');
  assert.equal(leanText(0.5, FULL_AXIS), '반반');
  assert.equal(leanText(0.585, FULL_AXIS), '마음신뢰형 58%'); // 4축 막대와 같은 반올림(엄격 42% · 관대 58%)
  // 반올림하면 50%지만 반반이 아니면 기운 쪽으로 1%p(서버 유형 글자와 맞춘다). 분수 평균의 부동소수 오차는 반반
  assert.equal(leanText(0.497, FULL_AXIS), '행동증명형 51%');
  assert.equal(leanText(0.503, FULL_AXIS), '마음신뢰형 51%');
  assert.equal(leanText((1 / 3 + 2 / 3) / 2, FULL_AXIS), '반반');
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

test('imageKinds: 사건별이 아니라 종류(폴더)별로 정해진 순서대로 묶는다', () => {
  const kinds = imageKinds([
    '/images/characters/scene/a.webp', '/images/characters/roles/naero_judge.webp', '/images/cases/case-1-origin.webp', '/images/unknown/z.webp',
    '/images/characters/icon/cropped/b.webp', '/images/characters/icon/generated/c.webp',
  ]);
  assert.deepEqual(kinds.map((kind) => kind.title), ['역할 의상', '장면 컷', '얼굴 아이콘 · 생성한 것', '얼굴 아이콘 · 잘라 만든 것', '사건 이미지', 'unknown']);
});

test('imageKinds: 사건 고유 이미지는 사건 이미지 종류 안에서 사건별로, 공용 컷은 따로 나눈다', () => {
  const [cases] = imageKinds(['/images/cases/case-1-origin.webp', '/images/cases/case-1.webp', '/images/cases/case-2-origin.webp', '/images/cases/common-1.webp', '/images/cases/reenact-common_headline.webp'], { caseNames: { 1: '빌린 돈 사건' } });
  assert.deepEqual(cases.groups.map((group) => group.title), ['빌린 돈 사건', '사건 2', '공용 컷']);
  assert.equal(cases.groups[0].items.length, 2);
  assert.equal(cases.groups[2].items.length, 2);
});

test('imageKinds: 같은 경로는 한 장으로 세고, 이름표(labels)가 있으면 파일 이름 대신 쓴다', () => {
  const [kind] = imageKinds(['/images/characters/roles/x.webp', '/images/characters/roles/x.webp'], { labels: { '/images/characters/roles/x.webp': '내로 · 판사' } });
  assert.deepEqual(kind.items, [{ src: '/images/characters/roles/x.webp', label: '내로 · 판사' }]);
});

test('castOf:대본의 역할 의상을 등장 순서대로 한 번씩 모으고, 역할 의상이 아닌 모습은 뺀다', () => {
  const script = {
    acts: {
      1: { look: { naero: 'idle', nambul: 'role:actor' }, steps: [{ look: { naero: 'role:judge' } }] },
      2: { steps: [{ look: { nambul: 'role:actor', law: 'role:judge' } }, { look: { naero: 'role:prosecutor' } }] },
    },
  };
  assert.deepEqual(castOf(script).map((item) => item.label), ['남불 · A씨 역', '내로 · 판사', '내로 · 검사']);
  assert.deepEqual(castOf(undefined), []);
});

test('collectionOf: 일러스트가 없어도 만난 모습이 있으면 묶음을 만든다', () => {
  const scripts = { 1: { acts: { 1: { look: { naero: 'role:defense' }, steps: [] } } } };
  const [group] = collectionOf([{ caseId: 1, title: '사건', status: 'COMPLETED' }], scripts);
  assert.deepEqual(group.cast.map((item) => item.role), ['defense']);
  assert.deepEqual(group.items, []);
});

test('trendSlotOrders: 강조할 체험이 최근 10건 밖이면 순서대로 끼워 넣고, 안이면 그대로', () => {
  assert.deepEqual(trendSlotOrders(12, true, 2), [0, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12]);
  assert.deepEqual(trendSlotOrders(12, false, 2), [1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12]);
  assert.deepEqual(trendSlotOrders(12, true, 5), trendOrders(12, true));
  assert.deepEqual(trendSlotOrders(3, true, undefined), [0, 1, 2, 3]);
});

test('recordOf: 마이페이지 history에서 이 사건의 축 점수 · 유형과 체험 순서(1부터)를 찾는다', () => {
  const scores = { APOLOGY_SINCERITY: 0.5, FAULT_STANDARD: 0.25, PRINCIPLE_RELATION: 0.5, ORDER_OPPORTUNITY: 1 };
  const type = { shareId: 'Qm7xK2pa', name: '예시 유형' };
  const myPage = { history: [{ caseId: 2, axisScores: { APOLOGY_SINCERITY: 1 }, type: null }, { caseId: 3, axisScores: scores, type }] };
  assert.deepEqual(recordOf(myPage, 3), { scores, type, order: 2 });
  assert.deepEqual(recordOf(myPage, '3'), { scores, type, order: 2 }); // 주소의 사건 번호가 문자열이어도 같다
  assert.deepEqual(recordOf(myPage, 2), { scores: { APOLOGY_SINCERITY: 1 }, type: null, order: 1 }); // 네 축이 다 없으면 서버가 null
  assert.deepEqual(recordOf(myPage, 9), {}); // 성향 기록이 없는 사건
  assert.deepEqual(recordOf({}, 3), {});
  assert.equal(recordOf(null, 3), null); // 마이페이지를 못 불러옴
});

test('recordOf: type이 없는 옛 응답(BE-100 이전)이면 null로 둔다', () => {
  const myPage = { history: [{ caseId: 3, axisScores: { APOLOGY_SINCERITY: 1 } }] };
  assert.deepEqual(recordOf(myPage, 3), { scores: { APOLOGY_SINCERITY: 1 }, type: null, order: 1 });
});
