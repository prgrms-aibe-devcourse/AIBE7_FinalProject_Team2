import assert from 'node:assert/strict';
import { existsSync } from 'node:fs';
import { test } from 'node:test';
import { PORTRAIT_POOL } from '../../content/portraitPool.js';
import { BACKGROUNDS, FRAMES, SASH_COLORS, SASH_ICONS } from '../../utils/profileDeco.js';
import { GUEST_CARDS, GUEST_TYPE_IMAGES, caseTotalOf, illustrationTotalOf, nextCardIndex } from './guestCards.js';

const has = (list, id) => list.some((item) => item.id === id);

test('샘플 카드는 10종이고 모두 실제 대표 이미지 · 꾸미기 값을 쓴다', () => {
  assert.equal(GUEST_CARDS.length, 10);
  for (const { deco, nickname } of GUEST_CARDS) {
    assert.ok(PORTRAIT_POOL.includes(deco.portrait), `${nickname}: 대표 이미지 후보에 없음 ${deco.portrait}`);
    assert.ok(existsSync(new URL(`../../../public${deco.portrait}`, import.meta.url)), `${nickname}: 이미지 파일 없음`);
    assert.ok(has(FRAMES, deco.frame) && has(BACKGROUNDS, deco.background), `${nickname}: 테두리 · 배경`);
    assert.ok(has(SASH_COLORS.short, deco.sashColorShort) && has(SASH_COLORS.long, deco.sashColorLong), `${nickname}: 책갈피 색`);
    assert.ok(has(SASH_ICONS, deco.sashIconLeft) && has(SASH_ICONS, deco.sashIconRight), `${nickname}: 책갈피 아이콘`);
  }
  assert.equal(new Set(GUEST_CARDS.map(({ deco }) => deco.portrait)).size, 10, '대표 이미지가 서로 다르다');
});

test('다음 카드는 바로 앞과 겹치지 않고 모든 카드가 나올 수 있다', () => {
  const seen = new Set();
  for (let previous = 0; previous < 10; previous += 1) {
    for (let step = 0; step < 9; step += 1) {
      const next = nextCardIndex(previous, 10, () => step / 9);
      assert.notEqual(next, previous);
      assert.ok(next >= 0 && next < 10);
      seen.add(next);
    }
  }
  assert.equal(seen.size, 10);
  assert.equal(nextCardIndex(0, 1), 0);
});

test('성향카드 자리에 비치는 그림은 32장 전부이고 내로 · 남불이 번갈아 순서대로 나온다', () => {
  assert.equal(GUEST_TYPE_IMAGES.length, 32);
  assert.equal(new Set(GUEST_TYPE_IMAGES).size, 32);
  const numbers = GUEST_TYPE_IMAGES.map((src) => Number(/(\d+)\.webp$/.exec(src)[1]));
  assert.deepEqual(numbers.slice(0, 6), [1, 17, 2, 18, 3, 19]);
  assert.deepEqual(numbers.slice(-2), [16, 32]);
  numbers.forEach((n, i) => assert.equal(n > 16, i % 2 === 1, `${n}번은 ${i % 2 === 0 ? '내로' : '남불'} 차례`));
  for (const src of GUEST_TYPE_IMAGES) {
    assert.ok(PORTRAIT_POOL.includes(src), `후보에 없음 ${src}`);
    assert.ok(existsSync(new URL(`../../../public${src}`, import.meta.url)), `파일 없음 ${src}`);
  }
});

test('일러스트 총 개수: 일러스트 API 응답의 total, 없거나 이상하면 null', () => {
  assert.equal(illustrationTotalOf({ total: 212, byKind: {} }), 212);
  assert.equal(illustrationTotalOf({ total: 0 }), 0);
  for (const bad of [null, undefined, {}, { total: '212' }, { total: -1 }, { total: 1.5 }]) assert.equal(illustrationTotalOf(bad), null);
});

test('사건 총 개수: 공개 사건 목록 응답의 summary.total, 없거나 이상하면 null', () => {
  assert.equal(caseTotalOf({ summary: { total: 9 }, cases: [] }), 9);
  assert.equal(caseTotalOf({ summary: { total: 0 } }), 0);
  for (const bad of [null, undefined, {}, { summary: {} }, { summary: { total: '9' } }, { summary: { total: -1 } }]) assert.equal(caseTotalOf(bad), null);
});
