import { test } from 'node:test';
import assert from 'node:assert/strict';
import { createFillerDeck, emptySlots, faceIndexAfter, FILL_GAP, FRONT_LIMIT, layoutCases, planFiller, swipeStep, visibleFaces } from './newspaperFaces.js';

const FACES = [undefined, 'MURDER', 'FRAUD', 'INJURY', 'ROBBERY', 'THEFT'];

test('1면(전체)의 다음 면은 살인면, 앞 면은 없다', () => {
  assert.equal(faceIndexAfter(FACES, undefined, 1), 1);
  assert.equal(faceIndexAfter(FACES, undefined, -1), -1);
});

test('마지막 면에서는 더 넘길 수 없다', () => {
  assert.equal(faceIndexAfter(FACES, 'THEFT', 1), -1);
  assert.equal(faceIndexAfter(FACES, 'THEFT', -1), 4);
});

test('목록에 없는 면이면 넘기지 않는다', () => {
  assert.equal(faceIndexAfter(FACES, 'UNKNOWN', 1), -1);
});

test('왼쪽으로 충분히 밀면 다음 면, 오른쪽이면 앞 면', () => {
  assert.equal(swipeStep({ dx: -80, dy: 10, ms: 400 }), 1);
  assert.equal(swipeStep({ dx: 80, dy: -10, ms: 400 }), -1);
});

test('세로 스크롤이나 짧은 손떨림은 넘김이 아니다', () => {
  assert.equal(swipeStep({ dx: -70, dy: 60, ms: 300 }), 0);
  assert.equal(swipeStep({ dx: -20, dy: 0, ms: 50 }), 0);
  assert.equal(swipeStep({ dx: -40, dy: 0, ms: 800 }), 0);
});

test('짧아도 빠르게 튕기면 넘긴다', () => {
  assert.equal(swipeStep({ dx: -30, dy: 2, ms: 120 }), 1);
});

test('다른 사건 줄의 빈 칸은 화면 칸 수마다 마지막 줄만큼', () => {
  assert.deepEqual(emptySlots(1), { six: 5, three: 2, two: 1 });
  assert.deepEqual(emptySlots(4), { six: 2, three: 2, two: 0 });
  assert.deepEqual(emptySlots(6), { six: 0, three: 0, two: 0 });
  assert.deepEqual(emptySlots(7), { six: 5, three: 2, two: 1 });
});

test('좁은 화면에서 채울 칸은 넓은 화면에서 채울 칸보다 많지 않다(뒤쪽 칸만 숨기면 된다)', () => {
  for (let count = 1; count <= 24; count += 1) {
    const { six, three, two } = emptySlots(count);
    assert.ok(three <= six && two <= six, `count ${count}`);
  }
});

const crime = (key) => ({ key, hideWhenEmpty: true, count: (summary) => summary.byCrimeType[key] ?? 0 });
const always = (key) => ({ key, count: () => null });
const FACE_LIST = [always(undefined), crime('MURDER'), crime('FRAUD'), crime('THEFT'), always('INDEX')];
const keys = (list) => list.map(({ key }) => key);
const summaryOf = (byCrimeType) => ({ total: 3, byCrimeType });

test('0건이면 숨기는 면은 건수가 0일 때 빼고, 1면 · 색인 같은 면은 남긴다', () => {
  assert.deepEqual(keys(visibleFaces(FACE_LIST, summaryOf({ MURDER: 3, FRAUD: 0 }), undefined)), [undefined, 'MURDER', 'INDEX']);
});

test('지금 보고 있는 면은 0건이어도 남긴다', () => {
  assert.deepEqual(keys(visibleFaces(FACE_LIST, summaryOf({ MURDER: 3 }), 'THEFT')), [undefined, 'MURDER', 'THEFT', 'INDEX']);
});

test('건수를 아직 모르면 모든 면을 보인다', () => {
  assert.equal(visibleFaces(FACE_LIST, undefined, undefined).length, FACE_LIST.length);
});

const cases = (n) => Array.from({ length: n }, (_, i) => ({ caseId: i + 1 }));

test('1면은 9건만 쓰고 넘치는 기사는 더보기에 두지 않는다', () => {
  const { lead, recommended, rest, more } = layoutCases(cases(12), { front: true });
  assert.equal(1 + recommended.length + rest.length, FRONT_LIMIT);
  assert.equal(lead.caseId, 1);
  assert.deepEqual(more, []);
});

test('유형 면은 9건까지 먼저 보이고 나머지는 더보기로 간다', () => {
  const { recommended, rest, more } = layoutCases(cases(12), { front: false });
  assert.equal(1 + recommended.length + rest.length, FRONT_LIMIT);
  assert.deepEqual(more.map((item) => item.caseId), [10, 11, 12]);
});

test('사건이 적으면 있는 만큼만 놓는다', () => {
  assert.deepEqual(layoutCases(cases(1), { front: false }), { lead: { caseId: 1 }, recommended: [], rest: [], more: [] });
  assert.equal(layoutCases([], { front: true }).lead, undefined);
});

test('남는 높이가 줄 최소 높이보다 작으면 채우지 않는다', () => {
  assert.equal(planFiller({ space: 90, width: 1200 }), null);
  assert.equal(planFiller({ space: 0, width: 1200 }), null);
  assert.equal(planFiller({ space: 400, width: 0 }), null);
});

test('넓은 폭은 3칸, 좁은 폭은 1칸으로 나눈다', () => {
  assert.equal(planFiller({ space: 300, width: 1200 }).columns, 3);
  assert.equal(planFiller({ space: 300, width: 360 }).columns, 1);
  assert.equal(planFiller({ space: 300, width: 2400 }).columns, 4);
});

test('줄 높이는 남는 높이를 빈틈없이 나눈 값이고 줄 최소 높이 이상이다', () => {
  for (const space of [120, 200, 260, 400, 640, 900, 1400]) {
    const plan = planFiller({ space, width: 1200 });
    assert.ok(plan.rowHeight >= 120, `space ${space}`);
    assert.ok(Math.abs(plan.rowHeight * plan.rows + FILL_GAP * (plan.rows - 1) - space) < 1e-6, `space ${space}`);
  }
});

test('줄은 2줄을 넘기지 않는다', () => {
  assert.equal(planFiller({ space: 2000, width: 1200 }).rows, 2);
  assert.equal(planFiller({ space: 250, width: 1200 }).rows, 1);
});

test('광고는 한 번만, 높은 줄에는 사진 지면이 섞인다', () => {
  const tall = planFiller({ space: 900, width: 1200 });
  assert.deepEqual(tall.kinds, ['photo', 'fact', 'ad', 'fact', 'fact', 'photo']); // 3칸 2줄, 사진은 대각선
  assert.equal(tall.kinds.filter((kind) => kind === 'ad').length, 1);
  const short = planFiller({ space: 140, width: 1200 });
  assert.equal(short.kinds.length, 3);
  assert.ok(!short.kinds.includes('photo'));
});

test('덱은 법정 상식 · 사진을 차례로 뽑고 광고는 한 번만 준다', () => {
  const deck = createFillerDeck(4);
  assert.deepEqual(deck.next('fact'), { kind: 'fact', n: 4 });
  assert.deepEqual(deck.next('fact'), { kind: 'fact', n: 5 });
  assert.deepEqual(deck.next('photo'), { kind: 'photo', n: 4 });
  assert.deepEqual(deck.next('ad'), { kind: 'ad' });
  assert.deepEqual(deck.next('ad'), { kind: 'fact', n: 6 }); // 두 번째 광고는 법정 상식으로
});

test('빈 칸이 둘 이상이면 맨 앞이 광고, 하나면 법정 상식', () => {
  assert.deepEqual(createFillerDeck().take(3).map(({ kind }) => kind), ['ad', 'fact', 'fact']);
  assert.deepEqual(createFillerDeck().take(1).map(({ kind }) => kind), ['fact']);
  assert.deepEqual(createFillerDeck().take(0), []);
});

test('fork는 지금까지 뽑은 상태를 이어받고 원본은 바꾸지 않는다', () => {
  const deck = createFillerDeck(0);
  deck.next('ad');
  deck.next('fact');
  const copy = deck.fork();
  assert.deepEqual(copy.next('ad'), { kind: 'fact', n: 1 }); // 광고는 이미 나갔다
  assert.deepEqual(copy.fork().next('fact'), { kind: 'fact', n: 2 });
  assert.deepEqual(deck.next('fact'), { kind: 'fact', n: 1 }); // 원본은 그대로
});
