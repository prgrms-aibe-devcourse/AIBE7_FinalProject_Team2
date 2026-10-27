import { test } from 'node:test';
import assert from 'node:assert/strict';
import { emptySlots, faceIndexAfter, swipeStep } from './newspaperFaces.js';

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
