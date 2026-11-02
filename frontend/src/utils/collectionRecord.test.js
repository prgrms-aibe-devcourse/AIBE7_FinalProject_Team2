// npm test : 수집 기록 보내기(FE-132). 목록 밖 항목(400 NOT_COLLECTIBLE)은 빼고 한 번 더 보낸다
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { recordCollection, recordsSettled, rejectedIndexes } from './collectionRecord.js';

const script = { acts: { 1: { look: { naero: 'role:judge', nambul: 'role:ghost' }, steps: [] } } };
const quiet = (run) => async () => {
  const warn = console.warn;
  console.warn = () => {};
  try { await run(); } finally { console.warn = warn; }
};

test('rejectedIndexes: NOT_COLLECTIBLE 사유의 항목 순서만 읽는다', () => {
  const error = { code: 'VALIDATION_ERROR', details: [{ field: 'items[4].itemId', reason: 'NOT_COLLECTIBLE' }, { field: 'items[1].itemId', reason: 'Pattern' }] };
  assert.deepEqual([...rejectedIndexes(error)], [4]);
  assert.equal(rejectedIndexes({ code: 'INVALID_STATE' }).size, 0);
  assert.equal(rejectedIndexes(null).size, 0);
});

test('recordCollection: 사건의 일러스트 3장 + 의상을 한 번에 보낸다', async () => {
  const sent = [];
  const api = { postCollection: async (body) => { sent.push(body); return { added: [], collected: 5, newlyUnlocked: [] }; } };
  const result = await recordCollection(api, 3, script, { backfill: true });
  assert.equal(result.collected, 5);
  assert.equal(sent.length, 1);
  assert.equal(sent[0].caseId, 3);
  assert.equal(sent[0].backfill, true);
  assert.deepEqual(sent[0].items.map((item) => item.itemId), ['case-3-reenact', 'case-3-origin', 'case-3-conclusion', 'naero:judge', 'nambul:ghost']);
});

test('recordCollection: 목록 밖 항목으로 거절되면 그 항목만 빼고 다시 보낸다', quiet(async () => {
  const sent = [];
  const api = {
    postCollection: async (body) => {
      sent.push(body.items.map((item) => item.itemId));
      if (sent.length === 1) throw { code: 'VALIDATION_ERROR', status: 400, details: [{ field: 'items[4].itemId', reason: 'NOT_COLLECTIBLE' }] };
      return { added: [], collected: 4, newlyUnlocked: [] };
    },
  };
  const result = await recordCollection(api, 3, script);
  assert.equal(result.collected, 4);
  assert.deepEqual(sent[1], ['case-3-reenact', 'case-3-origin', 'case-3-conclusion', 'naero:judge']);
}));

test('recordCollection: 그 밖의 실패 · API 없음은 null(흐름을 막지 않는다)', quiet(async () => {
  let calls = 0;
  const api = { postCollection: async () => { calls += 1; throw { code: 'INVALID_STATE', status: 409 }; } };
  assert.equal(await recordCollection(api, 3, script), null);
  assert.equal(calls, 1);
  assert.equal(await recordCollection({}, 3, script), null);
}));

test('recordsSettled: 보내는 중인 기록이 끝날 때까지 기다린다', async () => {
  let release;
  const api = { postCollection: () => new Promise((resolve) => { release = resolve; }) };
  let done = false;
  recordCollection(api, 1, null);
  const waiting = recordsSettled().then(() => { done = true; });
  await new Promise((resolve) => setTimeout(resolve, 0));
  assert.equal(done, false);
  release({ added: [], collected: 3, newlyUnlocked: [] });
  await waiting;
  assert.equal(done, true);
});
