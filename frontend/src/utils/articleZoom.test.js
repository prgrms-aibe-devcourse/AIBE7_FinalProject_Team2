import { test } from 'node:test';
import assert from 'node:assert/strict';
import { flyStart } from './articleZoom.js';

const target = { left: 100, top: 200, width: 600, height: 140, fontSize: 56 };

test('왼쪽 정렬 제목은 왼쪽 위끼리 맞추고 글자 크기 비율로 줄인다', () => {
  const source = { left: 400, top: 900, width: 300, height: 60, fontSize: 28 };
  assert.deepEqual(flyStart(source, target), { x: 300, y: 700, scale: 0.5 });
});

test('가운데 정렬 제목(머리기사 배너)은 가운데끼리 맞춘다', () => {
  const source = { left: 0, top: 0, width: 1000, height: 120, fontSize: 56 };
  const { x, y, scale } = flyStart(source, target, { centered: true });
  assert.equal(scale, 1);
  // 날아가는 글자(폭 600)의 가운데가 원래 제목(폭 1000)의 가운데에 온다
  assert.equal(target.left + x + (target.width * scale) / 2, 500);
  assert.equal(target.top + y + (target.height * scale) / 2, 60);
});
